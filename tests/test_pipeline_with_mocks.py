from types import SimpleNamespace

import pytest

from dd_rca.datadog_client import DatadogError
from dd_rca.pipeline import analyze_from_logs, analyze_from_monitor, analyze_from_service


class FakeClient:
    def __init__(self):
        self.cfg = SimpleNamespace(site="datadoghq.com")

    def get_monitor(self, monitor_id: int):
        return {
            "id": monitor_id,
            "name": "High latency",
            "type": "metric alert",
            "query": "avg:system.load.1{service:api,env:prod} > 10",
            "tags": ["service:api", "env:prod"],
        }

    def query_metrics(self, query: str, *, start: int, end: int):
        # return deterministic values based on time range
        # baseline: smaller values, incident: larger values
        val = 1.0 if (end - start) > 0 and end < 2_000_000_000 else 2.0
        return {"series": [{"pointlist": [[start * 1000, val], [end * 1000, val * 2]]}]}

    def search_logs(self, *, query: str, start_iso: str, end_iso: str, limit: int = 1000, max_pages: int = 2):
        # include service + env inference for APM
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
        # return a mix of server and client spans
        return [
            {
                "attributes": {
                    "timestamp": start_iso,
                    "service": "api",
                    "resource": "GET /users",
                    "span.kind": "server",
                    "duration": 200_000_000,  # 200ms
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
                    "duration": 300_000_000,  # 300ms
                    "error": 1,
                    "trace_id": "t1",
                }
            },
        ]


def test_analyze_from_logs_includes_apm_and_candidates():
    c = FakeClient()
    report = analyze_from_logs(client=c, log_query="service:api", anchor_ts="1700000000", window_minutes=10, baseline_minutes=10)
    d = report.to_dict()
    assert d["meta"]["seed_type"] == "logs"
    assert "apm" in d["findings"]
    assert d["findings"]["apm"]["enabled"] is True
    # should include some candidates (logs + apm)
    assert len(d["findings"]["candidates"]) >= 1


def test_analyze_from_monitor_includes_monitor_and_apm():
    c = FakeClient()
    report = analyze_from_monitor(client=c, monitor_id=123, trigger_ts="1700000000", window_minutes=10, baseline_minutes=10)
    d = report.to_dict()
    assert d["meta"]["seed_type"] == "monitor"
    assert d["findings"]["monitor"]["id"] == 123
    assert d["findings"]["apm"]["enabled"] is True


def test_analyze_from_service_runs_latency_mode():
    c = FakeClient()
    report = analyze_from_service(
        client=c,
        service="api",
        env="prod",
        start="2025-01-01T00:00:00Z",
        end="2025-01-01T00:10:00Z",
        mode="latency",
    )
    d = report.to_dict()
    assert d["meta"]["seed_type"] == "service"
    assert d["findings"]["service"]["mode"] == "latency"


def test_analyze_apm_gracefully_handles_errors():
    class NoApmClient(FakeClient):
        def search_spans(self, **kwargs):
            raise DatadogError("no apm")

    c = NoApmClient()
    report = analyze_from_logs(client=c, log_query="service:api", anchor_ts="1700000000", window_minutes=10, baseline_minutes=10)
    d = report.to_dict()
    assert d["findings"]["apm"]["enabled"] is True
    assert "error" in d["findings"]["apm"]

