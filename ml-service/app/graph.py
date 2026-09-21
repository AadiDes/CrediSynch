"""Ring detection: the graph algorithm ADR 0002 puts in Python (NetworkX), not Java.

The decision API already knows which applications are *connected* (a single indexed SQL query
over entity_links, recursively followed - see RingDetectionService). What it sends here is the raw
edge evidence for that connected neighbourhood; this module is the one place that turns "these
applications share identifiers" into a graph and reports its shape (size, density) the way
docs/FINDINGS.md finding (d) describes: connected-components clustering over shared-hash edges.
"""
from __future__ import annotations

import itertools

import networkx as nx

from app.schemas import RingCluster, RingDetectionRequest, RingDetectionResponse

ALGORITHM = "CONNECTED_COMPONENTS"


def detect_rings(request: RingDetectionRequest) -> RingDetectionResponse:
    graph = nx.Graph()
    by_hash: dict[str, set[str]] = {}
    for link in request.entity_links:
        graph.add_node(link.application_id)
        by_hash.setdefault(link.entity_hash, set()).add(link.application_id)

    # Every pair of applications sharing a given identifier is an edge - a shared device/phone/
    # email/address/bank account between two applications is direct evidence, not an inference.
    for members in by_hash.values():
        for a, b in itertools.combinations(sorted(members), 2):
            graph.add_edge(a, b)

    clusters: list[RingCluster] = []
    for component in nx.connected_components(graph):
        if len(component) < request.min_cluster_size:
            continue
        subgraph = graph.subgraph(component)
        clusters.append(
            RingCluster(
                members=sorted(component),
                size=len(component),
                density=round(nx.density(subgraph), 6) if len(component) > 1 else 0.0,
            )
        )

    clusters.sort(key=lambda c: c.size, reverse=True)
    return RingDetectionResponse(clusters=clusters, algorithm=ALGORITHM)
