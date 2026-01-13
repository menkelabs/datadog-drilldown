from dd_rca.analysis.apm import dependency_key, normalize_span, percentile


def test_percentile_basic():
    assert percentile([1, 2, 3, 4, 5], 50) == 3
    assert percentile([1, 2, 3, 4, 5], 0) == 1
    assert percentile([1, 2, 3, 4, 5], 100) == 5


def test_normalize_span_duration_infers_ns_to_ms():
    item = {
        "attributes": {
            "duration": 50_000_000,  # 50ms in ns
            "service": "api",
            "resource": "GET /health",
            "span.kind": "server",
            "error": 1,
            "trace_id": "t1",
        }
    }
    s = normalize_span(item)
    assert s.duration_ms == 50.0
    assert s.error is True
    assert s.service == "api"


def test_dependency_key_prefers_peer_service():
    s = normalize_span({"attributes": {"peer.service": "postgres", "span.type": "db"}})
    assert dependency_key(s) == "peer_service:postgres"

