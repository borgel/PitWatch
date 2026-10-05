#!/usr/bin/env python3
"""Serve captured TBA + Nexus snapshots as a local fake API, shifted to "now".

Every timestamp is moved so the snapshot's Nexus dataAsOfTime equals the moment it is served, and the
event's dates are rewritten to span today, so the Android app sees a live event. With several snapshot
dirs, the server moves to the next one every --switch-after seconds (replaying an event's progress).

Usage: python3 scripts/fake-api.py [--port 8765] [--switch-after 300] DIR [DIR ...]
Point the debug build at it with -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3
and -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1 (10.0.2.2 = host, from the emulator).
"""
import argparse
import datetime as dt
import json
import pathlib
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TBA_SECONDS = {"time", "predicted_time", "actual_time", "post_result_time"}
STARTED = time.time()


def load(directory):
    d = pathlib.Path(directory)
    return {f.stem: json.loads(f.read_text()) for f in d.glob("*.json")}


def shift(value, delta_ms):
    """Shift TBA second-timestamps and every Nexus millisecond field named *Time."""
    if isinstance(value, list):
        return [shift(v, delta_ms) for v in value]
    if not isinstance(value, dict):
        return value
    out = {}
    for k, v in value.items():
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            if k in TBA_SECONDS:
                v = int(v + delta_ms / 1000)
            elif k.endswith("Time"):
                v = int(v + delta_ms)
        out[k] = shift(v, delta_ms)
    return out


def current(snapshots, switch_after):
    index = min(len(snapshots) - 1, int((time.time() - STARTED) // switch_after))
    snap = snapshots[index]
    delta_ms = int(time.time() * 1000) - snap["nexus_event"]["dataAsOfTime"]
    today = dt.date.today()
    event = dict(snap["tba_event"], start_date=str(today - dt.timedelta(days=1)), end_date=str(today + dt.timedelta(days=2)), year=today.year)
    return snap, delta_ms, event


class Handler(BaseHTTPRequestHandler):
    snapshots = []
    switch_after = 300

    def do_GET(self):
        snap, delta, event = current(self.snapshots, self.switch_after)
        key = event["key"]
        routes = {
            rf"/api/v3/team/frc\d+/events/\d+": [event],
            rf"/api/v3/team/frc(\d+)": None,
            rf"/api/v3/event/{key}": event,
            rf"/api/v3/event/{key}/matches": shift(snap["tba_matches"], delta),
            rf"/api/v3/event/{key}/rankings": snap["tba_rankings"],
            rf"/api/v3/event/{key}/oprs": snap["tba_oprs"],
            rf"/api/v1/event/{key}": shift(snap["nexus_event"], delta),
            rf"/api/v1/event/{key}/map": snap["nexus_map"],
        }
        for pattern, body in routes.items():
            match = re.fullmatch(pattern, self.path)
            if not match:
                continue
            if body is None:  # team validation: echo any team number
                body = {"key": f"frc{match.group(1)}", "team_number": int(match.group(1)), "nickname": "Fake Team"}
            data = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_error(404)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dirs", nargs="+")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--switch-after", type=int, default=300)
    args = parser.parse_args()
    Handler.snapshots = [load(d) for d in args.dirs]
    Handler.switch_after = args.switch_after
    print(f"Serving {len(args.dirs)} snapshot(s) on http://0.0.0.0:{args.port}")
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
