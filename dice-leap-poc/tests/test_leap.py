"""Leap hybrid path: optional dep + token; CI stays local-only."""

from __future__ import annotations

import importlib.util
from pathlib import Path

import pytest

from dice_leap_poc.instance import Instance
from dice_leap_poc.pipeline import run_instance

ROOT = Path(__file__).resolve().parents[1]
TOY = ROOT / "sample_data" / "toy_dw_md.json"


def _dwave_system_installed() -> bool:
    return importlib.util.find_spec("dwave.system") is not None


def test_leap_hybrid_raises_import_error_when_dwave_system_missing():
    """Default CI / venv without ``[leap]`` should get a clear ImportError."""
    if _dwave_system_installed():
        pytest.skip("dwave-system installed — this check targets local/CI without [leap]")
    inst = Instance.load_json(TOY)
    with pytest.raises(ImportError, match="Leap hybrid solver requires"):
        run_instance(
            inst,
            strategy_choice="qubo",
            solver_mode="leap_hybrid",
            num_reads=100,
            seed=0,
        )


@pytest.mark.leap
def test_leap_hybrid_smoke_toy_when_token_present():
    """Hit Leap only when optional deps + ``DWAVE_API_TOKEN`` are available."""
    pytest.importorskip("dwave.system", reason="pip install 'dice-leap-poc[leap]'")
    import os

    if not os.environ.get("DWAVE_API_TOKEN"):
        pytest.skip("DWAVE_API_TOKEN not set")

    inst = Instance.load_json(TOY)
    rec = run_instance(
        inst,
        strategy_choice="qubo",
        solver_mode="leap_hybrid",
        leap_time_limit_s=5.0,
    )
    assert rec.solver_mode == "leap_hybrid"
    assert rec.strategy_choice == "qubo"
    assert rec.n_vars == 4
    assert rec.runtime_ms >= 0
