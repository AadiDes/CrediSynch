"""Train the CrediSynch fraud model on the Bank Account Fraud (BAF) suite.

Run once, locally:

    py -3.12 -m pip install -r requirements-ml.txt
    py -3.12 training/train_baf.py --data C:\\data\\baf\\Base.csv --out models

Evaluation follows the way a bank actually reads a fraud model, not the way a Kaggle
leaderboard does:
  * a temporal split (train on the earlier months, test on the later ones), because fraud drifts;
  * recall at a fixed 5% false-positive budget, because accuracy is meaningless at ~1% prevalence;
  * a false-positive-rate ratio across age groups, because friction should not land unevenly.
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import joblib
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.isotonic import IsotonicRegression
from sklearn.metrics import average_precision_score, roc_auc_score, roc_curve

TARGET = "fraud_bool"
DROP_COLUMNS = [TARGET, "month"]
CATEGORICAL = ["payment_type", "employment_status", "housing_status", "source", "device_os"]


def load(data_path: Path) -> pd.DataFrame:
    frame = pd.read_csv(data_path)
    if TARGET not in frame.columns:
        raise SystemExit(f"{data_path} does not look like a BAF file: no '{TARGET}' column")
    return frame


def split_by_month(frame: pd.DataFrame, holdout_from: int) -> tuple[pd.DataFrame, pd.DataFrame]:
    train = frame[frame["month"] < holdout_from]
    test = frame[frame["month"] >= holdout_from]
    if train.empty or test.empty:
        raise SystemExit("Temporal split produced an empty side; check the 'month' column")
    return train, test


def prepare(frame: pd.DataFrame, categories: dict[str, list[str]] | None = None, extra_drop: list[str] | None = None):
    drop = DROP_COLUMNS + (extra_drop or [])
    features = frame.drop(columns=[c for c in drop if c in frame.columns])
    resolved: dict[str, list[str]] = {}
    for column in CATEGORICAL:
        if column not in features.columns:
            continue
        values = categories[column] if categories else sorted(features[column].astype(str).unique())
        features[column] = pd.Categorical(features[column].astype(str), categories=values)
        resolved[column] = list(values)
    return features, resolved


def recall_at_fpr(y_true, y_score, target_fpr: float) -> tuple[float, float]:
    fpr, tpr, thresholds = roc_curve(y_true, y_score)
    index = int(np.searchsorted(fpr, target_fpr, side="right") - 1)
    index = max(index, 0)
    return float(tpr[index]), float(thresholds[index])


def fpr_ratio_by_age(y_true, y_score, ages, threshold: float, cut: int = 50) -> dict:
    flagged = y_score >= threshold
    legitimate = y_true == 0
    older = ages >= cut
    def fpr(mask):
        denominator = int((legitimate & mask).sum())
        return float((flagged & legitimate & mask).sum() / denominator) if denominator else float("nan")
    fpr_older, fpr_younger = fpr(older), fpr(~older)
    ratio = fpr_older / fpr_younger if fpr_younger else float("nan")
    return {"fpr_age_50_plus": fpr_older, "fpr_under_50": fpr_younger, "ratio": ratio}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--out", default=Path("models"), type=Path)
    parser.add_argument("--holdout-from-month", default=6, type=int)
    parser.add_argument("--calibration-month", default=5, type=int)
    parser.add_argument("--rounds", default=400, type=int)
    parser.add_argument("--exclude-age", action="store_true",
                        help="Fairness ablation (docs/FINDINGS.md): drop customer_age as a model "
                             "input. The raw column is still read from the test split for the "
                             "post-hoc audit - age is never a feature, only ever an audit input.")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    extra_drop = ["customer_age"] if args.exclude_age else []

    started = time.perf_counter()
    frame = load(args.data)
    print(f"loaded {len(frame):,} rows, fraud rate {frame[TARGET].mean():.4%}")

    train_all, test = split_by_month(frame, args.holdout_from_month)
    fit = train_all[train_all["month"] < args.calibration_month]
    calib = train_all[train_all["month"] >= args.calibration_month]

    x_fit, categories = prepare(fit, extra_drop=extra_drop)
    x_calib, _ = prepare(calib, categories, extra_drop=extra_drop)
    x_test, _ = prepare(test, categories, extra_drop=extra_drop)

    model = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=args.rounds,
        learning_rate=0.05,
        num_leaves=63,
        min_child_samples=100,
        subsample=0.9,
        subsample_freq=1,
        colsample_bytree=0.8,
        is_unbalance=True,
        n_jobs=-1,
        random_state=42,
    )
    model.fit(x_fit, fit[TARGET], categorical_feature=[c for c in CATEGORICAL if c in x_fit.columns])

    # Isotonic calibration on a later month: the policy thresholds are probabilities,
    # so an uncalibrated score would silently shift every band.
    raw_calib = model.predict_proba(x_calib)[:, 1]
    calibrator = IsotonicRegression(out_of_bounds="clip").fit(raw_calib, calib[TARGET])

    raw_test = model.predict_proba(x_test)[:, 1]
    calibrated_test = calibrator.predict(raw_test)

    recall5, threshold5 = recall_at_fpr(test[TARGET], calibrated_test, 0.05)
    metrics = {
        "trained_at": pd.Timestamp.utcnow().isoformat(),
        "rows_fit": int(len(fit)),
        "rows_calibration": int(len(calib)),
        "rows_test": int(len(test)),
        "fraud_rate_test": float(test[TARGET].mean()),
        "pr_auc": float(average_precision_score(test[TARGET], calibrated_test)),
        "roc_auc": float(roc_auc_score(test[TARGET], calibrated_test)),
        "recall_at_5pct_fpr": recall5,
        "score_threshold_at_5pct_fpr": threshold5,
        "mean_predicted_probability": float(calibrated_test.mean()),
        "fairness_age": fpr_ratio_by_age(
            test[TARGET].to_numpy(), calibrated_test, test["customer_age"].to_numpy(), threshold5),
        "training_seconds": round(time.perf_counter() - started, 1),
    }

    model.booster_.save_model(str(args.out / "model.txt"))
    joblib.dump(calibrator, args.out / "calibrator.joblib")
    (args.out / "feature_spec.json").write_text(json.dumps({
        "model_version": f"lgbm-baf-{pd.Timestamp.utcnow():%Y%m%d%H%M}",
        "features": list(x_fit.columns),
        "categorical": categories,
        "excludes_age": args.exclude_age,
    }, indent=2))
    (args.out / "metrics.json").write_text(json.dumps(metrics, indent=2))

    # For docs/FINDINGS.md's calibration plot and policy-band analysis, without needing to
    # reload the 200MB+ source CSV or retrain just to look at the test split again.
    pd.DataFrame({
        "y_true": test[TARGET].to_numpy(),
        "y_score": calibrated_test,
        "customer_age": test["customer_age"].to_numpy(),
        "month": test["month"].to_numpy(),
    }).to_csv(args.out / "test_predictions.csv", index=False)

    print(json.dumps(metrics, indent=2))
    print(f"\nartifacts written to {args.out.resolve()}")


if __name__ == "__main__":
    main()
