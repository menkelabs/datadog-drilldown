from dd_rca.config import Config
from dd_rca.datadog_client import DatadogClient


def test_search_logs_paginates_with_cursor(monkeypatch):
    cfg = Config(api_key="k", app_key="a", site="datadoghq.com", timeout_seconds=0.1, max_retries=0)
    c = DatadogClient(cfg=cfg)

    calls = {"bodies": []}

    def fake_request(method, url, *, params=None, json_body=None):
        calls["bodies"].append(json_body)
        if len(calls["bodies"]) == 1:
            return {
                "data": [{"id": "1"}, {"id": "2"}],
                "meta": {"page": {"after": "cursor1"}},
            }
        return {
            "data": [{"id": "3"}],
            "meta": {"page": {}},
        }

    monkeypatch.setattr(c, "_request", fake_request)
    logs = c.search_logs(query="q", start_iso="2025-01-01T00:00:00Z", end_iso="2025-01-01T01:00:00Z", limit=100)
    assert [x["id"] for x in logs] == ["1", "2", "3"]
    assert calls["bodies"][0]["page"].get("cursor") is None
    assert calls["bodies"][1]["page"]["cursor"] == "cursor1"


def test_search_spans_paginates_with_cursor(monkeypatch):
    cfg = Config(api_key="k", app_key="a", site="datadoghq.com", timeout_seconds=0.1, max_retries=0)
    c = DatadogClient(cfg=cfg)
    calls = {"bodies": []}

    def fake_request(method, url, *, params=None, json_body=None):
        calls["bodies"].append(json_body)
        if len(calls["bodies"]) == 1:
            return {"data": [{"id": "s1"}], "meta": {"page": {"after": "a"}}}
        return {"data": [{"id": "s2"}], "meta": {}}

    monkeypatch.setattr(c, "_request", fake_request)
    spans = c.search_spans(query="service:api env:prod", start_iso="2025-01-01T00:00:00Z", end_iso="2025-01-01T01:00:00Z", limit=100)
    assert [x["id"] for x in spans] == ["s1", "s2"]
    assert calls["bodies"][1]["page"]["cursor"] == "a"

