"""Load decision instances from JSON (Phase 1 schema)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class Entity:
    id: str
    cost: float
    signal: float


@dataclass
class Instance:
    instance_id: str
    entities: list[Entity]
    conflicts: list[tuple[str, str]]
    dependencies: list[tuple[str, str]]
    conflict_penalty: float = 5.0
    dependency_penalty: float = 10.0
    tier: str | None = None
    description: str | None = None
    encoding_version: str | None = None

    @staticmethod
    def from_dict(d: dict[str, Any]) -> Instance:
        entities = [
            Entity(id=e["id"], cost=float(e["cost"]), signal=float(e["signal"]))
            for e in d["entities"]
        ]
        conflicts = [(a, b) for a, b in d.get("conflicts", [])]
        dependencies = [(dep, req) for dep, req in d.get("dependencies", [])]
        return Instance(
            instance_id=d["instance_id"],
            entities=entities,
            conflicts=conflicts,
            dependencies=dependencies,
            conflict_penalty=float(d.get("conflict_penalty", 5.0)),
            dependency_penalty=float(d.get("dependency_penalty", 10.0)),
            tier=d.get("tier"),
            description=d.get("description"),
            encoding_version=d.get("encoding_version"),
        )

    @staticmethod
    def load_json(path: Path | str) -> Instance:
        p = Path(path)
        with p.open(encoding="utf-8") as f:
            return Instance.from_dict(json.load(f))
