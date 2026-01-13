from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Optional


def _parse_ts(ts: Optional[str]) -> datetime:
    if ts is None or str(ts).strip() == "":
        return datetime.now(tz=UTC)
    s = str(ts).strip()
    # epoch seconds or milliseconds
    if s.isdigit():
        v = int(s)
        if v > 10_000_000_000:  # ms
            return datetime.fromtimestamp(v / 1000.0, tz=UTC)
        return datetime.fromtimestamp(v, tz=UTC)
    # ISO-ish
    s = s.replace("Z", "+00:00")
    dt = datetime.fromisoformat(s)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


@dataclass(frozen=True)
class TimeWindow:
    start: datetime
    end: datetime

    @property
    def start_epoch(self) -> int:
        return int(self.start.timestamp())

    @property
    def end_epoch(self) -> int:
        return int(self.end.timestamp())

    def to_dict(self) -> dict:
        return {
            "start": self.start.isoformat(),
            "end": self.end.isoformat(),
            "start_epoch": self.start_epoch,
            "end_epoch": self.end_epoch,
        }


@dataclass(frozen=True)
class Windows:
    incident: TimeWindow
    baseline: TimeWindow
    anchor: datetime

    def to_dict(self) -> dict:
        return {
            "anchor": self.anchor.isoformat(),
            "incident": self.incident.to_dict(),
            "baseline": self.baseline.to_dict(),
        }


def windows_ending_at(
    *,
    anchor_ts: Optional[str],
    window_minutes: int,
    baseline_minutes: Optional[int] = None,
) -> Windows:
    anchor = _parse_ts(anchor_ts)
    win = timedelta(minutes=int(window_minutes))
    base = timedelta(minutes=int(baseline_minutes if baseline_minutes is not None else window_minutes))
    incident_end = anchor
    incident_start = incident_end - win
    baseline_end = incident_start
    baseline_start = baseline_end - base
    return Windows(
        anchor=anchor,
        incident=TimeWindow(start=incident_start, end=incident_end),
        baseline=TimeWindow(start=baseline_start, end=baseline_end),
    )

