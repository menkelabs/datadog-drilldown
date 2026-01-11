import json
from pathlib import Path
from types import SimpleNamespace

import dd_rca.pipeline as p


class GoldenClient:
    def __init__(self):
        self.cfg = SimpleNamespace(site="datadoghq.com")

    def search_logs(self, *, query: str, start_iso: str, end_iso: str, limit: int = 1000, max_pages: int = 2):
        # baseline window (end before anchor): empty
        if end_iso < "2023-11-14T22:13:20+00:00":
            return []
        return [
            {
                "attributes": {
                    "timestamp": start_iso,
                    "service": "api",
                    "ddtags": "env:prod,service:api",
                    "message": "TimeoutError: db timeout after 5000ms",
                    "error.type": "TimeoutError",
                    "error.message": "db timeout after 5000ms",
                    "error.stack": "stack\nline1\nline2\n",
                }
            }
        ]

    def search_events(self, *, start: int, end: int, tags=None):
        return {"events": [{"date_happened": start + 10, "title": "deploy", "text": "v1.2.3", "tags": ["env:prod"]}]}

    def search_spans(self, *, query: str, start_iso: str, end_iso: str, limit: int = 1000, max_pages: int = 2):
        # baseline window: empty
        if end_iso < "2023-11-14T22:13:20+00:00":
            return []
        return [
            {
                "attributes": {
                    "timestamp": start_iso,
                    "service": "api",
                    "resource": "GET /users",
                    "span.kind": "server",
                    "duration": 200_000_000,
                    "error": 0,
                    "trace_id": "t1",
                }
            },
            {
                "attributes": {
                    "timestamp": start_iso,
                    "service": "api",
                    "name": "postgres.query",
                    "span.kind": "client",
                    "span.type": "db",
                    "peer.service": "postgres",
                    "duration": 300_000_000,
                    "error": 1,
                    "trace_id": "t1",
                }
            },
        ]


def test_golden_report_fixture_matches_exact_output(monkeypatch):
    # freeze generated_at for deterministic snapshot
    monkeypatch.setattr(p, "_now_iso", lambda: "2026-01-10T00:00:00+00:00")

    client = GoldenClient()
    report = p.analyze_from_logs(client=client, log_query="service:api", anchor_ts="1700000000", window_minutes=10, baseline_minutes=10)
    got = report.to_dict()

    fixture_path = Path(__file__).parent / "fixtures" / "report_logs_golden.json"
    expected = json.loads(fixture_path.read_text(encoding="utf-8"))

    assert got == expected

