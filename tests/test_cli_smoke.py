import os
from pathlib import Path

from dd_rca.models import Report, Symptom


def test_cli_from_logs_writes_reports(monkeypatch, tmp_path: Path):
    import dd_rca.cli as cli

    # avoid env dependency
    monkeypatch.setenv("DD_API_KEY", "k")
    monkeypatch.setenv("DD_APP_KEY", "a")

    # stub client and pipeline
    class FakeClient:
        def __init__(self, cfg):
            self.cfg = cfg

    def fake_analyze_from_logs(**kwargs):
        return Report(
            meta={"seed_type": "logs", "generated_at": "x", "dd_site": "datadoghq.com", "input": {}},
            windows={"incident": {}, "baseline": {}},
            scope={},
            symptoms=[Symptom(type="log_signature", query_or_signature="q")],
            findings={"candidates": []},
            recommendations=[],
        )

    monkeypatch.setattr(cli, "DatadogClient", FakeClient)
    monkeypatch.setattr(cli, "analyze_from_logs", fake_analyze_from_logs)

    out_dir = tmp_path / "out"
    rc = cli.main(["from-logs", "--log-query", "q", "--output-dir", str(out_dir), "--markdown"])
    assert rc == 0
    assert (out_dir / "report.json").exists()
    assert (out_dir / "report.md").exists()

