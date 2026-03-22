"""D-Wave Leap hybrid sampler (Phase 2 — optional `dwave-system`)."""

from __future__ import annotations

import time

import dimod


def solve_leap_hybrid(
    bqm: dimod.BinaryQuadraticModel,
    *,
    time_limit_s: float | None = None,
) -> tuple[dict[str, int], float, float]:
    """
    Hybrid BQM solver via Leap. Returns (best_sample dict, best_energy, runtime_ms).

    Requires network credentials (e.g. ``DWAVE_API_TOKEN``) and optional extra:
    ``pip install 'dice-leap-poc[leap]'`` (installs ``dwave-system``).

    ``time_limit_s``: wall-clock limit in seconds passed to the sampler; if ``None``,
    uses ``LeapHybridSampler.min_time_limit(bqm)`` (solver-enforced minimum).
    """
    try:
        from dwave.system import LeapHybridSampler
    except ImportError as e:
        raise ImportError(
            "Leap hybrid solver requires optional dependencies. Install with: "
            "pip install 'dice-leap-poc[leap]' (or pip install dwave-system)"
        ) from e

    sampler = LeapHybridSampler()
    if time_limit_s is None:
        time_limit_s = float(sampler.min_time_limit(bqm))
    # Ocean accepts integer seconds for time_limit in practice
    tl = max(1, int(round(time_limit_s)))

    t0 = time.perf_counter()
    sampleset = sampler.sample(bqm, time_limit=tl)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    best = sampleset.first
    return dict(best.sample), float(best.energy), elapsed_ms
