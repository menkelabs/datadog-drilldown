from dd_rca.analysis.logs import cluster_logs, merge_baseline_counts, normalize_message


def test_normalize_message_stabilizes_numbers_and_uuids():
    m1 = "User 123 failed request id 550e8400-e29b-41d4-a716-446655440000"
    m2 = "User 999 failed request id 550e8400-e29b-41d4-a716-446655440000"
    assert normalize_message(m1) == normalize_message(m2)


def test_merge_baseline_counts_sets_counts():
    incident = [
        {"attributes": {"message": "error code 500 user 1", "timestamp": "2025-01-01T00:00:00Z"}},
        {"attributes": {"message": "error code 500 user 2", "timestamp": "2025-01-01T00:01:00Z"}},
    ]
    baseline = [{"attributes": {"message": "error code 500 user 9", "timestamp": "2024-12-31T23:00:00Z"}}]
    ic = cluster_logs(incident)
    merged = merge_baseline_counts(ic, baseline)
    assert len(merged) == 1
    c = next(iter(merged.values()))
    assert c.count_incident == 2
    assert c.count_baseline == 1

