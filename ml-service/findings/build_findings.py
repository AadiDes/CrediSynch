"""Builds the offline findings artefacts for docs/FINDINGS.md: model quality, the fairness
ablation, and policy-band calibration. Everything here reads only files that
training/train_baf.py writes, so it is fully reproducible - rerun train_baf.py (with and without
--exclude-age) into ml-service/models and ml-service/models_no_age, then run this script.

    py -3.12 findings/build_findings.py

Writes docs/figures/*.png (1600x900) and findings/output/summary.json.
"""
from __future__ import annotations

import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
ML = ROOT / "ml-service"
FIGURES = ROOT / "docs" / "figures"
OUTPUT = Path(__file__).resolve().parent / "output"
FIGSIZE = (16, 9)

# Live policy bands (GET /api/v1/policy/bands against the deployed backend, policy-1.0.0):
# derived from the same cost parameters as backend/src/main/resources/application.yml.
BANDS = {"approve": 0.00250, "step_up": 0.01333, "restricted": 0.48333}


def load_metrics(model_dir: Path) -> dict:
    return json.loads((model_dir / "metrics.json").read_text())


def load_predictions(model_dir: Path) -> pd.DataFrame:
    return pd.read_csv(model_dir / "test_predictions.csv")


def calibration_plot(predictions: pd.DataFrame, out_path: Path) -> list[dict]:
    """Reliability diagram: predicted probability vs observed fraud rate, by decile of score."""
    frame = predictions.copy()
    frame["bin"] = pd.qcut(frame["y_score"], 10, duplicates="drop")
    grouped = frame.groupby("bin", observed=True).agg(
        mean_predicted=("y_score", "mean"), mean_observed=("y_true", "mean"), n=("y_true", "size"))

    fig, ax = plt.subplots(figsize=FIGSIZE)
    ax.plot([0, grouped["mean_predicted"].max()], [0, grouped["mean_predicted"].max()],
            linestyle="--", color="#999999", label="Perfect calibration")
    ax.plot(grouped["mean_predicted"], grouped["mean_observed"], marker="o", color="#f5b301",
            label="Champion model (test split)")
    ax.set_xlabel("Mean predicted fraud probability (decile)")
    ax.set_ylabel("Observed fraud rate")
    ax.set_title("Calibration: predicted vs observed fraud rate, temporal test split")
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_path, dpi=100)
    plt.close(fig)
    return grouped.reset_index().assign(bin=lambda d: d["bin"].astype(str)).to_dict("records")


def fairness_plot(baseline: dict, no_age: dict, out_path: Path) -> None:
    labels = ["Recall @ 5% FPR", "Age FPR ratio (50+ / <50)"]
    baseline_vals = [baseline["recall_at_5pct_fpr"], baseline["fairness_age"]["ratio"]]
    no_age_vals = [no_age["recall_at_5pct_fpr"], no_age["fairness_age"]["ratio"]]

    x = np.arange(len(labels))
    width = 0.35
    fig, ax = plt.subplots(figsize=FIGSIZE)
    ax.bar(x - width / 2, baseline_vals, width, label="Champion (with customer_age)", color="#f5b301")
    ax.bar(x + width / 2, no_age_vals, width, label="Ablation (customer_age excluded)", color="#2fbf71")
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.set_title("Fairness ablation: dropping customer_age as a model input")
    ax.legend()
    for i, (b, n) in enumerate(zip(baseline_vals, no_age_vals)):
        ax.text(i - width / 2, b, f"{b:.3f}", ha="center", va="bottom")
        ax.text(i + width / 2, n, f"{n:.3f}", ha="center", va="bottom")
    fig.tight_layout()
    fig.savefig(out_path, dpi=100)
    plt.close(fig)


def policy_bands_plot(predictions: pd.DataFrame, out_path: Path) -> dict:
    scores = predictions["y_score"].to_numpy()
    labels = predictions["y_true"].to_numpy()
    total_fraud = labels.sum()

    edges = [0, BANDS["approve"], BANDS["step_up"], BANDS["restricted"], 1.0]
    names = ["APPROVE", "STEP_UP", "APPROVE_RESTRICTED", "REVIEW"]
    action_mix = {}
    for lo, hi, name in zip(edges[:-1], edges[1:], names):
        mask = (scores >= lo) & (scores < hi if hi != 1.0 else scores <= hi)
        count = int(mask.sum())
        fraud_in_band = int(labels[mask].sum())
        action_mix[name] = {
            "share_of_applications": count / len(scores),
            "count": count,
            "fraud_captured": fraud_in_band,
            "fraud_captured_share": (fraud_in_band / total_fraud) if total_fraud else 0.0,
        }

    fig, ax = plt.subplots(figsize=FIGSIZE)
    ax.hist(np.clip(scores, 1e-6, None), bins=200, color="#4a5568")
    ax.set_xscale("log")
    ax.set_yscale("log")
    for name, edge in zip(names[1:], edges[1:-1]):
        ax.axvline(edge, color="#e5484d", linestyle="--")
        ax.text(edge, ax.get_ylim()[1], f" {name} >=", rotation=90, va="top", color="#e5484d")
    ax.set_xlabel("Calibrated fraud probability (log scale)")
    ax.set_ylabel("Applications (log scale)")
    ax.set_title("Test-set score distribution against the live policy bands")
    fig.tight_layout()
    fig.savefig(out_path, dpi=100)
    plt.close(fig)
    return action_mix


def main() -> None:
    FIGURES.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)

    baseline_metrics = load_metrics(ML / "models_baseline")
    no_age_metrics = load_metrics(ML / "models_no_age")
    predictions = load_predictions(ML / "models_baseline")

    calibration_bins = calibration_plot(predictions, FIGURES / "calibration.png")
    fairness_plot(baseline_metrics, no_age_metrics, FIGURES / "fairness_ablation.png")
    action_mix = policy_bands_plot(predictions, FIGURES / "policy_bands.png")

    recall_delta = no_age_metrics["recall_at_5pct_fpr"] - baseline_metrics["recall_at_5pct_fpr"]
    summary = {
        "model_quality": {
            "pr_auc": baseline_metrics["pr_auc"],
            "roc_auc": baseline_metrics["roc_auc"],
            "recall_at_5pct_fpr": baseline_metrics["recall_at_5pct_fpr"],
            "score_threshold_at_5pct_fpr": baseline_metrics["score_threshold_at_5pct_fpr"],
            "rows_test": baseline_metrics["rows_test"],
            "fraud_rate_test": baseline_metrics["fraud_rate_test"],
            "split": "temporal (train months < 6, test months >= 6; calibration on months 5)",
        },
        "fairness_ablation": {
            "champion_includes_age": True,
            "baseline": baseline_metrics["fairness_age"] | {"recall_at_5pct_fpr": baseline_metrics["recall_at_5pct_fpr"]},
            "no_age": no_age_metrics["fairness_age"] | {"recall_at_5pct_fpr": no_age_metrics["recall_at_5pct_fpr"]},
            "recall_delta_absolute": recall_delta,
            "recall_delta_relative": recall_delta / baseline_metrics["recall_at_5pct_fpr"],
            "ratio_delta": no_age_metrics["fairness_age"]["ratio"] - baseline_metrics["fairness_age"]["ratio"],
            "decision": "kept customer_age as a model input: dropping it cost "
                        f"{abs(recall_delta / baseline_metrics['recall_at_5pct_fpr']):.1%} relative recall "
                        "at the 5% FPR budget while the disparity only partially closed "
                        f"({baseline_metrics['fairness_age']['ratio']:.2f}x -> {no_age_metrics['fairness_age']['ratio']:.2f}x), "
                        "showing other features proxy for age.",
        },
        "policy_bands": {"thresholds": BANDS, "action_mix": action_mix},
        "calibration_bins": calibration_bins,
    }
    (OUTPUT / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
