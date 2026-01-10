from __future__ import annotations

import json
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

import requests

from .config import Config


class DatadogError(RuntimeError):
    pass


def _base_urls(site: str) -> Tuple[str, str]:
    site = site.strip()
    if site.startswith("http://") or site.startswith("https://"):
        # user supplied full base; best-effort
        base = site.rstrip("/")
        return base + "/api/v1", base + "/api/v2"
    return f"https://api.{site}/api/v1", f"https://api.{site}/api/v2"


@dataclass
class DatadogClient:
    cfg: Config
    session: requests.Session = requests.Session()

    def _headers(self) -> Dict[str, str]:
        return {
            "DD-API-KEY": self.cfg.api_key,
            "DD-APPLICATION-KEY": self.cfg.app_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _request(
        self,
        method: str,
        url: str,
        *,
        params: Optional[dict] = None,
        json_body: Optional[dict] = None,
    ) -> Any:
        last_err = None
        for attempt in range(self.cfg.max_retries + 1):
            try:
                resp = self.session.request(
                    method=method,
                    url=url,
                    params=params,
                    data=None if json_body is None else json.dumps(json_body),
                    headers=self._headers(),
                    timeout=self.cfg.timeout_seconds,
                )
                if resp.status_code in (429, 500, 502, 503, 504):
                    raise DatadogError(f"retryable status={resp.status_code} body={resp.text[:500]}")
                if resp.status_code >= 400:
                    raise DatadogError(f"status={resp.status_code} body={resp.text[:1000]}")
                return resp.json()
            except (requests.RequestException, DatadogError) as e:
                last_err = e
                if attempt >= self.cfg.max_retries:
                    break
                sleep_s = min(8.0, 0.5 * (2**attempt))
                time.sleep(sleep_s)
        raise DatadogError(f"request failed: {method} {url}: {last_err}")

    def get_monitor(self, monitor_id: int) -> Dict[str, Any]:
        v1, _ = _base_urls(self.cfg.site)
        return self._request("GET", f"{v1}/monitor/{int(monitor_id)}")

    def query_metrics(self, query: str, *, start: int, end: int) -> Dict[str, Any]:
        v1, _ = _base_urls(self.cfg.site)
        return self._request("GET", f"{v1}/query", params={"from": start, "to": end, "query": query})

    def search_events(self, *, start: int, end: int, tags: Optional[str] = None) -> Dict[str, Any]:
        v1, _ = _base_urls(self.cfg.site)
        params: Dict[str, Any] = {"start": start, "end": end}
        if tags:
            params["tags"] = tags
        return self._request("GET", f"{v1}/events", params=params)

    def search_logs(
        self,
        *,
        query: str,
        start_iso: str,
        end_iso: str,
        limit: int = 1000,
        max_pages: int = 2,
    ) -> List[Dict[str, Any]]:
        _, v2 = _base_urls(self.cfg.site)
        url = f"{v2}/logs/events/search"
        logs: List[Dict[str, Any]] = []
        cursor: Optional[str] = None
        for _ in range(max_pages):
            body: Dict[str, Any] = {
                "filter": {"from": start_iso, "to": end_iso, "query": query},
                "sort": "timestamp",
                "page": {"limit": min(int(limit), 1000)},
            }
            if cursor:
                body["page"]["cursor"] = cursor
            resp = self._request("POST", url, json_body=body)
            data = resp.get("data") or []
            logs.extend(data)
            meta = resp.get("meta") or {}
            after = ((meta.get("page") or {}).get("after")) if isinstance(meta, dict) else None
            if not after:
                break
            cursor = after
        return logs

