"""Milestone 1 acceptance: simple tier heuristic; complex tier QUBO + superiority margin."""

from __future__ import annotations

from pathlib import Path

import pytest

from dice_leap_poc.batch import iter_fixture_paths, run_fixtures, tier_aggregates
from dice_leap_poc.instance import Entity, Instance
from dice_leap_poc.pipeline import run_instance, run_json_file
from dice_leap_poc.strategy import RolloverConfig, plan_strategy

ROOT = Path(__file__).resolve().parents[1]
TOY = ROOT / "sample_data" / "toy_dw_md.json"
COMPLEX = ROOT / "sample_data" / "complex_dw_md.json"
BELOW_ROLLOVER = ROOT / "sample_data" / "below_rollover_n12_e8.json"
ROLLOVER_N13 = ROOT / "sample_data" / "rollover_n13.json"
ROLLOVER_EDGES9 = ROOT / "sample_data" / "rollover_edges9.json"

# Tuned with synthetic complex fixture (greedy vs brute margin ~2.03); SA uses 10k reads.
COMPLEX_MIN_MARGIN = 1.5
ROLLOVER_N13_MIN_MARGIN = 1.5
SIMPLE_MAX_DELTA = 1e-9


def test_plan_strategy_fixture_tiers():
    simple = Instance.load_json(TOY)
    assert plan_strategy(simple) == ("heuristic_only", "fixture_tier_simple")
    complex_inst = Instance.load_json(COMPLEX)
    assert plan_strategy(complex_inst) == ("qubo", "fixture_tier_complex")


def test_plan_strategy_metrics_rollover_no_tier():
    """dwave.md-style: >12 candidates → QUBO path when tier absent."""
    entities = [Entity(id=f"E{i}", cost=1.0, signal=1.0) for i in range(13)]
    inst = Instance(
        instance_id="metrics_rollover",
        entities=entities,
        conflicts=[],
        dependencies=[],
        tier=None,
    )
    choice, reason = plan_strategy(inst)
    assert choice == "qubo"
    assert "rollover_metrics" in reason


def test_plan_strategy_below_rollover_no_tier():
    entities = [Entity(id=f"E{i}", cost=1.0, signal=1.0) for i in range(10)]
    inst = Instance(
        instance_id="below",
        entities=entities,
        conflicts=[("E0", "E1")],
        dependencies=[],
        tier=None,
    )
    choice, reason = plan_strategy(inst)
    assert choice == "heuristic_only"
    assert "below_rollover" in reason


def test_explicit_solver_mode_local_classical():
    inst = Instance.load_json(TOY)
    rec = run_instance(
        inst,
        strategy_choice="qubo",
        solver_mode="local_classical",
        num_reads=800,
        seed=1,
    )
    assert rec.solver_mode == "local_classical"
    assert rec.strategy_choice == "qubo"


def test_simple_tier_auto_heuristic_json():
    rec = run_json_file(TOY, num_reads=2000, seed=1)
    assert rec.strategy_choice == "heuristic_only"
    assert rec.strategy_reason == "fixture_tier_simple"
    assert rec.vs_baseline_delta == pytest.approx(0.0, abs=SIMPLE_MAX_DELTA)
    assert rec.objective == pytest.approx(rec.baseline_objective, rel=0, abs=1e-9)


def test_complex_tier_auto_qubo_superiority():
    rec = run_json_file(COMPLEX, num_reads=10000, seed=42)
    assert rec.strategy_choice == "qubo"
    assert rec.strategy_reason == "fixture_tier_complex"
    assert rec.vs_baseline_delta >= COMPLEX_MIN_MARGIN


def test_custom_rollover_threshold():
    """Tighter candidate cap forces heuristic on a 13-node untiered graph."""
    entities = [Entity(id=f"E{i}", cost=1.0, signal=1.0) for i in range(13)]
    inst = Instance("t", entities, [], [], tier=None)
    cfg = RolloverConfig(max_candidates_for_heuristic=20, max_constraint_edges_for_heuristic=8)
    assert plan_strategy(inst, cfg) == ("heuristic_only", "below_rollover(n=13,edges=0)")


def test_batch_sample_fixtures_smoke(tmp_path):
    paths = [TOY, COMPLEX]
    out = tmp_path / "b.jsonl"
    records = run_fixtures(paths, append_jsonl=out, num_reads=8000, seed=42)
    assert len(records) == 2
    assert out.read_text().count("\n") == 2
    agg = tier_aggregates(records)
    assert "simple" in agg and "complex" in agg


def test_iter_fixture_paths_includes_toy():
    found = {p.name for p in iter_fixture_paths(ROOT / "sample_data")}
    assert "toy_dw_md.json" in found
    assert "complex_dw_md.json" in found
    assert "below_rollover_n12_e8.json" in found
    assert "rollover_n13.json" in found
    assert "rollover_edges9.json" in found


def test_rollover_dataset_metrics_boundaries():
    """Simulate dwave-style triggers: just below ceiling = heuristic; above = qubo."""
    below = Instance.load_json(BELOW_ROLLOVER)
    c_b, r_b = plan_strategy(below)
    assert c_b == "heuristic_only"
    assert "below_rollover" in r_b
    assert "n=12" in r_b and "edges=8" in r_b

    n13 = Instance.load_json(ROLLOVER_N13)
    c_n, r_n = plan_strategy(n13)
    assert c_n == "qubo"
    assert "rollover_metrics" in r_n
    assert "n=13" in r_n

    e9 = Instance.load_json(ROLLOVER_EDGES9)
    c_e, r_e = plan_strategy(e9)
    assert c_e == "qubo"
    assert "rollover_metrics" in r_e
    assert "edges=9" in r_e


def test_rollover_n13_qubo_superiority_metrics_path():
    """Metrics rollover (n>12): SA/QUBO path measurably beats greedy — same bar as tier=complex."""
    inst = Instance.load_json(ROLLOVER_N13)
    rec = run_instance(inst, num_reads=12000, seed=42)
    assert rec.strategy_choice == "qubo"
    assert "rollover_metrics" in rec.strategy_reason
    assert rec.encoding_version == "1"
    assert rec.vs_baseline_delta >= ROLLOVER_N13_MIN_MARGIN
