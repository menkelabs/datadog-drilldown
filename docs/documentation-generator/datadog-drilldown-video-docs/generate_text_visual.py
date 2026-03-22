#!/usr/bin/env python3
"""
Build a clean 1280x720 MP4: solid background + staggered centered text (no terminal / shell).

Reads visual-text/<stem>.txt — one display line per file line (empty lines skipped).
Uses ffmpeg drawtext; no VHS, no prompts, no screen grabs.

Usage:
  python generate_text_visual.py --stem 01-welcome-scope --audio audio/01-welcome-scope.mp3 \\
      --out text-rendered/01-welcome-scope.mp4
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEMOS_DIR = Path(__file__).resolve().parent
VISUAL_TEXT_DIR = DEMOS_DIR / "visual-text"
TEXT_RENDERED_DIR = DEMOS_DIR / "text-rendered"

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/ubuntu/Ubuntu-R.ttf",
]


def find_font() -> str:
    for f in FONT_CANDIDATES:
        if Path(f).is_file():
            return f
    return "DejaVu Sans"


def ffprobe_duration(audio: Path) -> float:
    r = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "json",
            str(audio),
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    return float(json.loads(r.stdout)["format"]["duration"])


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--stem", required=True)
    p.add_argument("--audio", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--bg", default="0x1a237e", help="hex color without #")
    args = p.parse_args()

    lines_path = VISUAL_TEXT_DIR / f"{args.stem}.txt"
    if not lines_path.is_file():
        print(f"ERROR: missing {lines_path}", file=sys.stderr)
        sys.exit(1)

    raw_lines = [
        ln.strip() for ln in lines_path.read_text(encoding="utf-8").splitlines() if ln.strip()
    ]
    if not raw_lines:
        print(f"ERROR: no lines in {lines_path}", file=sys.stderr)
        sys.exit(1)

    if not shutil.which("ffmpeg"):
        print("ERROR: ffmpeg not found", file=sys.stderr)
        sys.exit(1)

    duration = ffprobe_duration(args.audio)
    fontfile = find_font()
    use_fontfile = Path(fontfile).is_file()

    TEXT_RENDERED_DIR.mkdir(parents=True, exist_ok=True)
    args.out.parent.mkdir(parents=True, exist_ok=True)

    tmp = Path(tempfile.mkdtemp(prefix="textviz-"))
    try:
        text_files: list[Path] = []
        for i, line in enumerate(raw_lines):
            fp = tmp / f"line{i}.txt"
            fp.write_text(line.replace("\r", ""), encoding="utf-8")
            text_files.append(fp)

        n = len(raw_lines)
        window = min(duration * 0.35, max(2.5, n * 1.2))
        step = window / max(n, 1)

        fontsize0 = 46 if n <= 4 else 38
        fontsize = 38 if n <= 4 else 32
        line_h = 76 if n <= 5 else 58
        y0 = 120

        simple_chain: list[str] = []
        for i, tf in enumerate(text_files):
            fs = fontsize0 if i == 0 else fontsize
            t_start = round(i * step, 2)
            font_opt = f"fontfile={fontfile}:" if use_fontfile else ""
            seg = (
                f"drawtext={font_opt}textfile={tf.as_posix()}:reload=1:"
                f"fontsize={fs}:fontcolor=white:"
                f"box=1:boxcolor=black@0.35:boxborderw=16:"
                f"x=(w-text_w)/2:y={y0 + i * line_h}:"
                f"enable='gte(t\\,{t_start})'"
            )
            simple_chain.append(seg)
        vf = ",".join(simple_chain)

        bg = args.bg.removeprefix("#")
        cmd = [
            "ffmpeg",
            "-y",
            "-f",
            "lavfi",
            "-i",
            f"color=c={bg}:s=1280x720:r=30:d={duration}",
            "-i",
            str(args.audio),
            "-vf",
            vf,
            "-map",
            "0:v:0",
            "-map",
            "1:a:0",
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-t",
            str(duration),
            "-movflags",
            "+faststart",
            str(args.out),
        ]
        subprocess.run(cmd, check=True, capture_output=True)
        print(f"  ✓ text visual → {args.out} ({duration:.1f}s, {n} lines)")
    except subprocess.CalledProcessError as e:
        err = e.stderr
        if isinstance(err, bytes):
            err = err.decode("utf-8", errors="replace")
        print(err or str(e), file=sys.stderr)
        sys.exit(1)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
