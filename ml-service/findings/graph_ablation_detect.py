"""Ring-detection precision/recall (docs/FINDINGS.md): a minimal connected-components detector
over entity_links, evaluated against the known ground truth from graph_ablation.py's injection
(which application belongs to which synthetic ring, or to noise).

Needs entity_links exported from the database as CSV (application_id,entity_type,entity_hash),
since this script has no direct DB access - see the docstring in graph_ablation.py's SSM/psql
command for how that export was produced.

    py -3.12 findings/graph_ablation_detect.py <entity_links.csv>
"""
from __future__ import annotations

import itertools
import json
import sys
from collections import defaultdict
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent / "output"


class UnionFind:
    def __init__(self):
        self.parent: dict[str, str] = {}

    def find(self, x: str) -> str:
        self.parent.setdefault(x, x)
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: str, b: str) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[ra] = rb


def detect_clusters(links_csv: Path, known_applications: set[str]) -> dict[str, str]:
    """Two applications are in the same detected cluster iff they share any entity hash."""
    uf = UnionFind()
    by_hash: dict[str, list[str]] = defaultdict(list)
    for line in links_csv.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        app_id, _entity_type, entity_hash = line.split(",", 2)
        if app_id not in known_applications:
            continue
        by_hash[entity_hash].append(app_id)
        uf.find(app_id)
    for members in by_hash.values():
        for a, b in itertools.pairwise(members):
            uf.union(a, b)
    return {app_id: uf.find(app_id) for app_id in known_applications}


def pairwise_precision_recall(ground_truth: dict[str, str], detected: dict[str, str]) -> dict:
    apps = list(ground_truth)
    true_positive = false_positive = false_negative = 0
    for a, b in itertools.combinations(apps, 2):
        same_true_ring = ground_truth[a] == ground_truth[b] and ground_truth[a] != "noise"
        same_cluster = detected[a] == detected[b]
        if same_cluster and same_true_ring:
            true_positive += 1
        elif same_cluster and not same_true_ring:
            false_positive += 1
        elif not same_cluster and same_true_ring:
            false_negative += 1
    precision = true_positive / (true_positive + false_positive) if (true_positive + false_positive) else 1.0
    recall = true_positive / (true_positive + false_negative) if (true_positive + false_negative) else 1.0
    return {
        "pairwise_true_positive": true_positive, "pairwise_false_positive": false_positive,
        "pairwise_false_negative": false_negative, "precision": precision, "recall": recall,
        "f1": (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0,
    }


def main() -> None:
    links_csv = Path(sys.argv[1]) if len(sys.argv) > 1 else OUTPUT / "entity_links_export.csv"
    injection = json.loads((OUTPUT / "graph_ablation_injection.json").read_text())
    ground_truth = injection["ground_truth"]

    detected = detect_clusters(links_csv, set(ground_truth))
    metrics = pairwise_precision_recall(ground_truth, detected)

    detected_ring_count = len({cluster for app, cluster in detected.items() if ground_truth[app] != "noise"})
    result = {
        "ring_detection": metrics,
        "true_ring_count": len({v for v in ground_truth.values() if v != "noise"}),
        "detected_cluster_count_among_ring_members": detected_ring_count,
    }
    (OUTPUT / "graph_ablation_detection.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
