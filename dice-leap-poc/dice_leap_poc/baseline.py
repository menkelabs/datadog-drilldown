"""Greedy heuristic baseline on the same QUBO (for vs_baseline_delta)."""

from __future__ import annotations

import dimod

from dice_leap_poc.instance import Instance


def greedy_min_energy(inst: Instance, bqm: dimod.BinaryQuadraticModel) -> dict:
    """
    Greedily turn on variables in order of ascending diagonal contribution (cost-signal),
    skipping if a conflict or dependency would be violated.
    """
    diag = {e.id: e.cost - e.signal for e in inst.entities}
    order = sorted(diag.keys(), key=lambda x: diag[x])

    conflict_pairs = {tuple(sorted((a, b))) for a, b in inst.conflicts}
    deps = list(inst.dependencies)
    selected: set[str] = set()
    assignment = {v: 0 for v in bqm.variables}

    def can_add(v: str) -> bool:
        for dep, req in deps:
            if v == dep and req not in selected:
                return False
        trial = selected | {v}
        for a, b in conflict_pairs:
            if a in trial and b in trial:
                return False
        return True

    for v in order:
        if not can_add(v):
            continue
        selected.add(v)
        assignment[v] = 1

    return assignment


def brute_force_best(bqm: dimod.BinaryQuadraticModel) -> tuple[float, dict]:
    """Exact minimum for small models (tests)."""
    vars_list = list(bqm.variables)
    n = len(vars_list)
    best_e = float("inf")
    best_s: dict = {}
    for mask in range(1 << n):
        s = {vars_list[i]: (mask >> i) & 1 for i in range(n)}
        e = bqm.energy(s)
        if e < best_e:
            best_e, best_s = e, s
    return best_e, best_s
