"""Summarize existing OldPoint capture logs without treating window means as frame samples."""

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re


NUMBER = r"(\d+(?:\.\d+)?)"
RECORD = re.compile(
    r"\[OldPoint\] (slow )?(indoor|outdoor) capture ms: "
    + r"native=" + NUMBER + r", walls=" + NUMBER
    + r", openings=" + NUMBER + r", roofs=" + NUMBER + r", total=" + NUMBER
)
WINDOW = re.compile(r", max=" + NUMBER + r", samples=(\d+)")
STAGES = ("native", "walls", "openings", "roofs", "total")


def summarize(path, since):
    groups = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        # PZ log timestamps use DD-MM-YY, independently of the host locale.
        stamp = re.match(r"\[(\d{2})-(\d{2})-(\d{2}) ([\d:.]+)\]", line)
        if not stamp:
            continue
        day, month, year, clock = stamp.groups()
        date = f"20{year}-{month}-{day}"
        if date < since:
            continue
        match = RECORD.search(line)
        if not match:
            continue
        slow, mode, *values = match.groups()
        key = (date, mode)
        group = groups.setdefault(key, {"windows": [], "slow_records": []})
        record = {"line": line_number, "time": f"{date} {clock}", "ms": dict(zip(STAGES, map(float, values)))}
        if slow:
            group["slow_records"].append(record)
        else:
            window = WINDOW.search(line)
            if window:
                record.update(max_ms=float(window[1]), samples=int(window[2]))
                group["windows"].append(record)
    results = []
    for (date, mode), group in sorted(groups.items()):
        windows = group["windows"]
        samples = sum(record["samples"] for record in windows)
        results.append({
            "log": str(path.resolve()), "date": date, "mode": mode,
            "windows": len(windows), "window_samples": samples,
            "weighted_window_mean_ms": {
                stage: sum(record["ms"][stage] * record["samples"] for record in windows) / samples
                for stage in STAGES
            } if samples else None,
            "window_total_mean_range_ms": [min(record["ms"]["total"] for record in windows), max(record["ms"]["total"] for record in windows)] if windows else None,
            "maximum_reported_ms": max([record["max_ms"] for record in windows] + [record["ms"]["total"] for record in group["slow_records"]], default=None),
            "slow_records_count": len(group["slow_records"]),
            "slow_records": group["slow_records"],
        })
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--logs", type=Path, default=Path.home() / "Zomboid" / "Logs")
    parser.add_argument("--since", required=True, help="First date to include, YYYY-MM-DD")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    sessions = [result for path in sorted(args.logs.rglob("*DebugLog.txt")) for result in summarize(path, args.since)]
    result = {
        "generated_at": datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds"),
        "since": args.since,
        "limitations": [
            "Window means are weighted by samples; frame percentiles cannot be recovered.",
            "Slow records are throttled to one per second per mode and may overlap window samples.",
            "Final partial windows are not included in weighted means.",
            "Capture timing excludes tree capture, rule packing, GPU upload and drawing.",
            "The hardcoded loaded-build log message does not identify the exact deployed source.",
        ],
        "sessions": sessions,
    }
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
    for session in sessions:
        mean = session["weighted_window_mean_ms"]
        print(f"{Path(session['log']).name} {session['date']} {session['mode']}: "
              f"windows={session['windows']}, samples={session['window_samples']}, "
              f"mean_ms={None if mean is None else round(mean['total'], 4)}, "
              f"max_ms={session['maximum_reported_ms']}, slow_records={session['slow_records_count']}")


if __name__ == "__main__":
    main()
