from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


def _tags_to_map(tags: List[str]) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for t in tags or []:
        if not isinstance(t, str):
            continue
        if ":" in t:
            k, v = t.split(":", 1)
            k = k.strip()
            v = v.strip()
            if k and v and k not in out:
                out[k] = v
    return out


@dataclass
class Scope:
    service: Optional[str] = None
    env: Optional[str] = None
    region: Optional[str] = None
    cluster: Optional[str] = None
    hosts: List[str] = field(default_factory=list)
    pods: List[str] = field(default_factory=list)
    tags: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "service": self.service,
            "environment": self.env,
            "region": self.region,
            "cluster": self.cluster,
            "hosts": self.hosts,
            "pods": self.pods,
            "tag_filters": {k: v for k, v in sorted(self.tags.items())},
        }

    def to_event_tag_query(self) -> Optional[str]:
        # events API expects comma-separated tags
        tags = []
        if self.service:
            tags.append(f"service:{self.service}")
        if self.env:
            tags.append(f"env:{self.env}")
        if self.region:
            tags.append(f"region:{self.region}")
        if self.cluster:
            tags.append(f"cluster:{self.cluster}")
        return ",".join(tags) if tags else None


def scope_from_monitor(monitor: Dict[str, Any]) -> Scope:
    tags = monitor.get("tags") or []
    tm = _tags_to_map(tags if isinstance(tags, list) else [])
    service = tm.get("service") or tm.get("svc")
    env = tm.get("env") or tm.get("environment")
    region = tm.get("region") or tm.get("aws_region")
    cluster = tm.get("cluster") or tm.get("kube_cluster_name")
    return Scope(service=service, env=env, region=region, cluster=cluster, tags=tm)


def scope_from_logs(logs: List[Dict[str, Any]]) -> Scope:
    # best-effort: pick most common values in incident logs
    service_counts: Dict[str, int] = {}
    env_counts: Dict[str, int] = {}
    region_counts: Dict[str, int] = {}
    cluster_counts: Dict[str, int] = {}
    host_counts: Dict[str, int] = {}

    for item in logs or []:
        attrs = (item.get("attributes") or {}) if isinstance(item, dict) else {}
        service = attrs.get("service")
        if isinstance(service, str) and service:
            service_counts[service] = service_counts.get(service, 0) + 1
        host = attrs.get("host")
        if isinstance(host, str) and host:
            host_counts[host] = host_counts.get(host, 0) + 1

        # Datadog logs often include "ddtags" like "env:prod,service:api"
        ddtags = attrs.get("ddtags")
        if isinstance(ddtags, str) and ddtags:
            tm = _tags_to_map([t.strip() for t in ddtags.split(",") if t.strip()])
            if tm.get("env"):
                env_counts[tm["env"]] = env_counts.get(tm["env"], 0) + 1
            if tm.get("region"):
                region_counts[tm["region"]] = region_counts.get(tm["region"], 0) + 1
            if tm.get("cluster"):
                cluster_counts[tm["cluster"]] = cluster_counts.get(tm["cluster"], 0) + 1

    def top(d: Dict[str, int]) -> Optional[str]:
        if not d:
            return None
        return sorted(d.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]

    top_hosts = [h for h, _ in sorted(host_counts.items(), key=lambda kv: (-kv[1], kv[0]))[:5]]
    return Scope(
        service=top(service_counts),
        env=top(env_counts),
        region=top(region_counts),
        cluster=top(cluster_counts),
        hosts=top_hosts,
    )

