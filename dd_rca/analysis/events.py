from __future__ import annotations

from typing import Any, Dict, List

from ..models import EventItem


def parse_events(resp: Dict[str, Any], limit: int = 20) -> List[EventItem]:
    events = resp.get("events") or []
    out: List[EventItem] = []
    for e in events:
        if not isinstance(e, dict):
            continue
        ts = e.get("date_happened")
        if ts is None:
            continue
        # date_happened is epoch seconds
        try:
            ts_iso = __import__("datetime").datetime.fromtimestamp(int(ts), tz=__import__("datetime").UTC).isoformat()
        except Exception:
            ts_iso = str(ts)
        out.append(
            EventItem(
                ts=ts_iso,
                title=str(e.get("title") or ""),
                text=str(e.get("text") or "")[:1500],
                tags=[t for t in (e.get("tags") or []) if isinstance(t, str)],
                url=e.get("url"),
            )
        )
    out.sort(key=lambda x: x.ts)
    return out[: int(limit)]

