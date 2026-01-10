from __future__ import annotations

import argparse
from pathlib import Path
from typing import Optional

from .config import Config
from .datadog_client import DatadogClient
from .pipeline import analyze_from_logs, analyze_from_monitor
from .render.report_json import write_report_json
from .render.report_md import render_report_md


def _add_common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--site", default=None, help="Datadog site (default: env DD_SITE or datadoghq.com)")
    p.add_argument(
        "--output-dir",
        default="dd-rca-out",
        help="Output directory for report.json/markdown (default: dd-rca-out)",
    )
    p.add_argument("--markdown", action="store_true", help="Also render report.md")


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="dd-rca", description="Seeded RCA reports from Datadog monitors/logs")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_mon = sub.add_parser("from-monitor", help="Analyze starting from a Datadog monitor")
    _add_common(p_mon)
    p_mon.add_argument("--monitor-id", type=int, required=True)
    p_mon.add_argument("--trigger-ts", default=None, help="Anchor timestamp (ISO or epoch). Default: now")
    p_mon.add_argument("--window-minutes", type=int, default=60)
    p_mon.add_argument("--baseline-minutes", type=int, default=60)

    p_logs = sub.add_parser("from-logs", help="Analyze starting from a Datadog logs query")
    _add_common(p_logs)
    p_logs.add_argument("--log-query", required=True)
    p_logs.add_argument("--anchor-ts", default=None, help="Anchor timestamp (ISO or epoch). Default: now")
    p_logs.add_argument("--window-minutes", type=int, default=30)
    p_logs.add_argument("--baseline-minutes", type=int, default=30)

    args = parser.parse_args(argv)

    cfg = Config.from_env()
    if args.site:
        cfg = Config(api_key=cfg.api_key, app_key=cfg.app_key, site=str(args.site))
    client = DatadogClient(cfg=cfg)

    out_dir = Path(str(args.output_dir))

    if args.cmd == "from-monitor":
        report = analyze_from_monitor(
            client=client,
            monitor_id=int(args.monitor_id),
            trigger_ts=args.trigger_ts,
            window_minutes=int(args.window_minutes),
            baseline_minutes=int(args.baseline_minutes),
        )
        report_dict = report.to_dict()
        write_report_json(report_dict, out_dir / "report.json")
        if args.markdown:
            (out_dir / "report.md").write_text(render_report_md(report_dict), encoding="utf-8")
        return 0

    if args.cmd == "from-logs":
        report = analyze_from_logs(
            client=client,
            log_query=str(args.log_query),
            anchor_ts=args.anchor_ts,
            window_minutes=int(args.window_minutes),
            baseline_minutes=int(args.baseline_minutes),
        )
        report_dict = report.to_dict()
        write_report_json(report_dict, out_dir / "report.json")
        if args.markdown:
            (out_dir / "report.md").write_text(render_report_md(report_dict), encoding="utf-8")
        return 0

    parser.error("Unknown command")
    return 2

