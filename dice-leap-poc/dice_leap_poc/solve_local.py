"""Local classical sampling (Phase 1 — no Leap)."""

from __future__ import annotations

import time
from typing import Any

import dimod
import neal


def solve_local_sa(
    bqm: dimod.BinaryQuadraticModel,
    *,
    num_reads: int = 2000,
    seed: int | None = 42,
) -> tuple[dict, float, float]:
    """
    Simulated annealing via dwave-neal. Returns (best_sample dict, best_energy, runtime_ms).
    """
    sampler = neal.SimulatedAnnealingSampler()
    t0 = time.perf_counter()
    # neal accepts seed in sample() on recent versions
    kwargs: dict[str, Any] = {"num_reads": num_reads}
    if seed is not None:
        kwargs["seed"] = seed
    sampleset = sampler.sample(bqm, **kwargs)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    best = sampleset.first
    return dict(best.sample), float(best.energy), elapsed_ms
