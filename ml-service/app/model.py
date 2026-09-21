"""Loads the trained model and turns one application into a probability plus reason codes.

Reason codes come from LightGBM's own SHAP contributions (`pred_contrib=True`), which is exact
TreeSHAP without pulling in the heavier `shap` package - the same explanation, a smaller image
and a faster hot path.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

from app.schemas import ReasonCode

log = logging.getLogger(__name__)


class FraudModel:
    def __init__(self, model_dir: str):
        self.model_dir = Path(model_dir)
        self.booster = None
        self.calibrator = None
        self.feature_names: list[str] = []
        self.categories: dict[str, list[str]] = {}
        self.model_version = "stub-0.1.0"
        self._pandas = None
        self.load()

    @property
    def loaded(self) -> bool:
        return self.booster is not None

    def load(self) -> None:
        spec_path = self.model_dir / "feature_spec.json"
        model_path = self.model_dir / "model.txt"
        if not (spec_path.exists() and model_path.exists()):
            log.info("No trained model in %s; the deterministic stub stays in use.", self.model_dir)
            return
        try:
            import joblib
            import lightgbm as lgb
            import pandas as pd

            spec = json.loads(spec_path.read_text())
            self.booster = lgb.Booster(model_file=str(model_path))
            self.feature_names = spec["features"]
            self.categories = spec.get("categorical", {})
            self.model_version = spec.get("model_version", "lgbm-baf")
            calibrator_path = self.model_dir / "calibrator.joblib"
            self.calibrator = joblib.load(calibrator_path) if calibrator_path.exists() else None
            self._pandas = pd
            log.info("Loaded model %s with %d features", self.model_version, len(self.feature_names))
        except Exception:  # pragma: no cover - exercised only when artefacts are corrupt
            log.exception("Model artefacts present but unusable; falling back to the stub")
            self.booster = None

    def _frame(self, features: dict):
        pd = self._pandas
        row = {name: features.get(name) for name in self.feature_names}
        frame = pd.DataFrame([row], columns=self.feature_names)
        for column, values in self.categories.items():
            if column in frame.columns:
                frame[column] = pd.Categorical(frame[column].astype(str), categories=values)
        for column in frame.columns:
            if column not in self.categories:
                frame[column] = pd.to_numeric(frame[column], errors="coerce")
        return frame

    def predict(self, features: dict) -> tuple[float, list[ReasonCode]]:
        frame = self._frame(features)
        raw = float(self.booster.predict(frame)[0])
        probability = float(self.calibrator.predict([raw])[0]) if self.calibrator is not None else raw
        probability = min(max(probability, 0.0), 1.0)

        contributions = self.booster.predict(frame, pred_contrib=True)[0]
        pairs = list(zip(self.feature_names, contributions[:-1]))
        pairs.sort(key=lambda item: abs(item[1]), reverse=True)
        reasons = [
            ReasonCode(
                code=name.upper(),
                feature=name,
                contribution=round(float(value), 6),
                direction="INCREASES_RISK" if value > 0 else "DECREASES_RISK",
            )
            for name, value in pairs[:5]
        ]
        return probability, reasons
