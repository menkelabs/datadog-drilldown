"""Build a binary QUBO / BQM from an Instance (dwave.md-style diagonals + penalties)."""

from __future__ import annotations

import dimod

from dice_leap_poc.instance import Instance


def instance_to_qubo_dict(inst: Instance) -> dict[tuple[str, str], float]:
    """
    Minimize x^T Q x (Q upper-triangular in tuple keys for dimod.from_qubo).
    Diagonal: Q_ii = cost_i - signal_i (minimize cost, maximize signal).
    Conflict (i,j): +penalty * x_i * x_j (discourage both selected).
    Dependency (dep, req): penalty * x_dep * (1 - x_req) = P*x_dep - P*x_dep*x_req.
    """
    Q: dict[tuple[str, str], float] = {}
    ids = {e.id for e in inst.entities}

    for e in inst.entities:
        Q[(e.id, e.id)] = Q.get((e.id, e.id), 0.0) + (e.cost - e.signal)

    for a, b in inst.conflicts:
        if a not in ids or b not in ids:
            raise ValueError(f"Conflict references unknown entity: {(a, b)}")
        u, v = sorted((a, b))
        Q[(u, v)] = Q.get((u, v), 0.0) + inst.conflict_penalty

    for dep, req in inst.dependencies:
        if dep not in ids or req not in ids:
            raise ValueError(f"Dependency references unknown entity: {(dep, req)}")
        P = inst.dependency_penalty
        Q[(dep, dep)] = Q.get((dep, dep), 0.0) + P
        u, v = sorted((dep, req))
        Q[(u, v)] = Q.get((u, v), 0.0) - P

    return Q


def build_bqm(inst: Instance) -> dimod.BinaryQuadraticModel:
    Q = instance_to_qubo_dict(inst)
    return dimod.BinaryQuadraticModel.from_qubo(Q, offset=0.0)


def assignment_to_selected(bqm: dimod.BinaryQuadraticModel, sample: dict) -> list[str]:
    """Variables with value 1, sorted for stable output."""
    return sorted(v for v in bqm.variables if sample.get(v, 0) == 1)
