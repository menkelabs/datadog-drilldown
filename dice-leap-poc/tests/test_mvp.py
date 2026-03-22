"""MVP: QUBO build, brute optimum, local SA matches or beats greedy baseline."""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path

import pytest

from dice_leap_poc.baseline import brute_force_best, greedy_min_energy
from dice_leap_poc.instance import Instance
from dice_leap_poc.pipeline import run_instance, run_json_file
from dice_leap_poc.qubo import build_bqm

ROOT = Path(__file__).resolve().parents[1]
TOY = ROOT / "sample_data" / "toy_dw_md.json"


def test_load_toy():
    inst = Instance.load_json(TOY)
    assert inst.instance_id == "toy_dw_md"
    assert len(inst.entities) == 4


def test_encoding_version_from_instance():
    inst = replace(Instance.load_json(TOY), encoding_version="2")
    rec = run_instance(inst, strategy_choice="heuristic_only")
    assert rec.encoding_version == "2"


def test_bqm_variable_count():
    inst = Instance.load_json(TOY)
    bqm = build_bqm(inst)
    assert len(bqm.variables) == 4


def test_brute_force_optimal():
    inst = Instance.load_json(TOY)
    bqm = build_bqm(inst)
    e_star, s_star = brute_force_best(bqm)
    assert e_star < float("inf")
    # Known optimum for toy_dw_md: select A and C only (avoid A-B conflict; Deploy_X costly unless paired with A)
    assert pytest.approx(e_star, rel=1e-6) == bqm.energy(s_star)


def test_sa_matches_optimal_with_seed():
    inst = Instance.load_json(TOY)
    rec = run_instance(inst, strategy_choice="qubo", num_reads=4000, seed=42)
    bqm = build_bqm(inst)
    e_star, _ = brute_force_best(bqm)
    assert rec.objective <= e_star + 1e-6
    assert rec.solver_mode == "local_classical"
    assert rec.strategy_choice == "qubo"


def test_qubo_no_worse_than_greedy_baseline():
    inst = Instance.load_json(TOY)
    bqm = build_bqm(inst)
    h = greedy_min_energy(inst, bqm)
    e_h = bqm.energy(h)
    rec = run_instance(inst, strategy_choice="qubo", num_reads=4000, seed=123)
    assert rec.objective <= e_h + 1e-5
    assert rec.vs_baseline_delta >= -1e-5


def test_run_json_file_explicit_qubo():
    rec = run_json_file(TOY, strategy_choice="qubo", seed=7, num_reads=2000)
    assert rec.instance_id == "toy_dw_md"
    assert rec.strategy_choice == "qubo"
    assert rec.encoding_version == "1"
    line = rec.to_json_line()
    assert "vs_baseline_delta" in line
    assert '"encoding_version": "1"' in line
