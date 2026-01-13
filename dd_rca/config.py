from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    api_key: str
    app_key: str
    site: str = "datadoghq.com"
    timeout_seconds: float = 20.0
    max_retries: int = 4

    @staticmethod
    def from_env() -> "Config":
        api_key = os.environ.get("DD_API_KEY", "").strip()
        app_key = os.environ.get("DD_APP_KEY", "").strip()
        site = os.environ.get("DD_SITE", "datadoghq.com").strip()
        if not api_key:
            raise ValueError("Missing DD_API_KEY")
        if not app_key:
            raise ValueError("Missing DD_APP_KEY")
        if not site:
            site = "datadoghq.com"
        return Config(api_key=api_key, app_key=app_key, site=site)

