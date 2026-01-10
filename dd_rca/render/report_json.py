from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict


def write_report_json(report_dict: Dict[str, Any], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report_dict, indent=2, sort_keys=False) + "\n", encoding="utf-8")

