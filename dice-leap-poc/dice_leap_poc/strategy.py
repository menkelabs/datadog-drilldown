"""Optimization strategy layer: tier labels + dwave.md-style rollover thresholds."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from dice_leap_poc.instance import Instance

StrategyChoice = Literal["heuristic_only", "qubo"]


@dataclass(frozen=True)
class RolloverConfig:
    """
    When instance has no explicit `tier`, use candidate/edge counts (dwave.md triggers).
    Above these → QUBO path; at or below → heuristic-only.
    """

    max_candidates_for_heuristic: int = 12
    max_constraint_edges_for_heuristic: int = 8


def constraint_edge_count(inst: Instance) -> int:
    """Undirected conflict pairs + dependency arcs (each counts as one edge)."""
    return len(inst.conflicts) + len(inst.dependencies)


def plan_strategy(
    inst: Instance,
    cfg: RolloverConfig | None = None,
) -> tuple[StrategyChoice, str]:
    """
    Returns (strategy_choice, strategy_reason) for logging and SolveRecord.
    Explicit JSON `tier` wins when set to simple/complex; otherwise metrics-based rollover.
    """
    cfg = cfg or RolloverConfig()
    n = len(inst.entities)
    edges = constraint_edge_count(inst)

    if inst.tier == "simple":
        return "heuristic_only", "fixture_tier_simple"
    if inst.tier == "complex":
        return "qubo", "fixture_tier_complex"

    if n > cfg.max_candidates_for_heuristic or edges > cfg.max_constraint_edges_for_heuristic:
        return "qubo", f"rollover_metrics(n={n},edges={edges})"

    return "heuristic_only", f"below_rollover(n={n},edges={edges})"
