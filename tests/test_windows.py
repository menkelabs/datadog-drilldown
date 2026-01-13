from dd_rca.windows import windows_ending_at


def test_windows_ending_at_epoch_seconds():
    w = windows_ending_at(anchor_ts="1700000000", window_minutes=10, baseline_minutes=10)
    assert w.incident.end_epoch == 1700000000
    assert w.incident.start_epoch == 1700000000 - 600
    assert w.baseline.end_epoch == w.incident.start_epoch


def test_windows_ending_at_iso():
    w = windows_ending_at(anchor_ts="2025-01-01T00:00:00Z", window_minutes=5, baseline_minutes=5)
    assert w.incident.end.isoformat().startswith("2025-01-01T00:00:00")

