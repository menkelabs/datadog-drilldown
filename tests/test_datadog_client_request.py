import json
from types import SimpleNamespace
from unittest.mock import Mock

import requests

from dd_rca.config import Config
from dd_rca.datadog_client import DatadogClient, DatadogError


class _Resp:
    def __init__(self, status_code: int, payload=None, text=""):
        self.status_code = status_code
        self._payload = payload if payload is not None else {}
        self.text = text or json.dumps(self._payload)

    def json(self):
        return self._payload


def test_request_retries_on_500_then_succeeds(monkeypatch):
    cfg = Config(api_key="k", app_key="a", site="datadoghq.com", timeout_seconds=0.1, max_retries=2)
    c = DatadogClient(cfg=cfg)
    calls = {"n": 0}

    def fake_request(*args, **kwargs):
        calls["n"] += 1
        if calls["n"] == 1:
            return _Resp(500, {"error": "boom"})
        return _Resp(200, {"ok": True})

    monkeypatch.setattr(c.session, "request", fake_request)
    out = c._request("GET", "https://example/api")
    assert out == {"ok": True}
    assert calls["n"] == 2


def test_request_raises_on_4xx(monkeypatch):
    cfg = Config(api_key="k", app_key="a", site="datadoghq.com", timeout_seconds=0.1, max_retries=0)
    c = DatadogClient(cfg=cfg)

    def fake_request(*args, **kwargs):
        return _Resp(403, {"errors": ["forbidden"]}, text="forbidden")

    monkeypatch.setattr(c.session, "request", fake_request)
    try:
        c._request("GET", "https://example/api")
        assert False, "expected DatadogError"
    except DatadogError as e:
        assert "status=403" in str(e)


def test_request_retries_on_request_exception(monkeypatch):
    cfg = Config(api_key="k", app_key="a", site="datadoghq.com", timeout_seconds=0.1, max_retries=1)
    c = DatadogClient(cfg=cfg)
    calls = {"n": 0}

    def fake_request(*args, **kwargs):
        calls["n"] += 1
        if calls["n"] == 1:
            raise requests.RequestException("network")
        return _Resp(200, {"ok": True})

    monkeypatch.setattr(c.session, "request", fake_request)
    out = c._request("GET", "https://example/api")
    assert out == {"ok": True}
    assert calls["n"] == 2

