#!/usr/bin/env bash
#
# Pulls raw JSON from The Blue Alliance and FRC Nexus for a single event.
# Intended to capture responses that can later be curated into test fixtures.
#
# Each fetch run writes into a new timestamped subdirectory under the event,
# so running the script repeatedly accumulates snapshots over time rather
# than overwriting the previous pull.
#
# Output layout:
#   scripts/fixtures/<event_key>/<YYYY-MM-DDTHH-MM-SSZ>/*.json
#
# Usage:
#   TBA_KEY=... ./scripts/fetch-fixtures.sh list [--team N] [--year YYYY]
#   TBA_KEY=... [NEXUS_KEY=...] ./scripts/fetch-fixtures.sh fetch <event_key>

set -euo pipefail

TBA_BASE="https://www.thebluealliance.com/api/v3"
NEXUS_BASE="https://frc.nexus/api/v1"
OUT_ROOT="scripts/fixtures"

die() {
  echo "error: $*" >&2
  exit 1
}

usage() {
  cat >&2 <<EOF
Usage:
  TBA_KEY=... $0 list [--team N] [--year YYYY]
  TBA_KEY=... [NEXUS_KEY=...] $0 fetch <event_key>
EOF
  exit 2
}

require_tools() {
  command -v curl >/dev/null 2>&1 || die "curl is required"
  command -v jq   >/dev/null 2>&1 || die "jq is required (brew install jq)"
}

require_tba_key() {
  [[ -n "${TBA_KEY:-}" ]] || die "TBA_KEY env var is required"
}

# ---------- list ----------

cmd_list() {
  local team="" year
  year="$(date +%Y)"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --team) team="${2:-}"; [[ -n "$team" ]] || die "--team needs a value"; shift 2 ;;
      --year) year="${2:-}"; [[ -n "$year" ]] || die "--year needs a value"; shift 2 ;;
      -h|--help) usage ;;
      *) die "unknown arg: $1" ;;
    esac
  done

  local path
  if [[ -n "$team" ]]; then
    path="/team/frc${team}/events/${year}/simple"
  else
    path="/events/${year}/simple"
  fi

  local body
  body="$(curl -fsS -H "X-TBA-Auth-Key: ${TBA_KEY}" "${TBA_BASE}${path}")" \
    || die "TBA request failed: ${path}"

  if [[ "$(jq 'length' <<<"$body")" == "0" ]]; then
    echo "(no events returned for year ${year}${team:+ / team ${team}})" >&2
    return 0
  fi

  jq -r '
    sort_by(.start_date) | .[] |
    [
      .key,
      .name,
      (.start_date + ".." + .end_date),
      ([.city, .state_prov, .country] | map(select(. != null and . != "")) | join(", "))
    ] | @tsv
  ' <<<"$body" | column -t -s $'\t'
}

# ---------- fetch ----------

# fetch_endpoint <url> <auth_header_name> <auth_key> <out_path> <label>
fetch_endpoint() {
  local url="$1" header="$2" key="$3" out="$4" label="$5"

  local tmp http_code bytes
  tmp="$(mktemp)"
  http_code="$(curl -sS -o "$tmp" -w "%{http_code}" \
    -H "${header}: ${key}" \
    -H "User-Agent: PitWatch-fixture-puller" \
    "$url" || echo "000")"
  bytes="$(wc -c <"$tmp" | tr -d ' ')"

  if jq . "$tmp" >"$out" 2>/dev/null; then
    rm -f "$tmp"
  else
    # Non-JSON body (e.g. HTML error page) — preserve it verbatim so the failure is visible.
    mv "$tmp" "$out"
  fi

  printf "  %-14s %s  %7s bytes  -> %s\n" "$label" "$http_code" "$bytes" "$out"
}

cmd_fetch() {
  [[ $# -eq 1 ]] || usage
  local event_key="$1"
  local timestamp
  timestamp="$(date -u +"%Y-%m-%dT%H-%M-%SZ")"
  local out_dir="${OUT_ROOT}/${event_key}/${timestamp}"
  mkdir -p "$out_dir"

  echo "TBA -> ${out_dir}"
  fetch_endpoint "${TBA_BASE}/event/${event_key}"          "X-TBA-Auth-Key" "${TBA_KEY}" "${out_dir}/tba_event.json"    "event"
  fetch_endpoint "${TBA_BASE}/event/${event_key}/matches"  "X-TBA-Auth-Key" "${TBA_KEY}" "${out_dir}/tba_matches.json"  "matches"
  fetch_endpoint "${TBA_BASE}/event/${event_key}/rankings" "X-TBA-Auth-Key" "${TBA_KEY}" "${out_dir}/tba_rankings.json" "rankings"
  fetch_endpoint "${TBA_BASE}/event/${event_key}/oprs"     "X-TBA-Auth-Key" "${TBA_KEY}" "${out_dir}/tba_oprs.json"     "oprs"
  fetch_endpoint "${TBA_BASE}/event/${event_key}/teams"    "X-TBA-Auth-Key" "${TBA_KEY}" "${out_dir}/tba_teams.json"    "teams"

  if [[ -n "${NEXUS_KEY:-}" ]]; then
    echo "Nexus -> ${out_dir}"
    fetch_endpoint "${NEXUS_BASE}/event/${event_key}"     "Nexus-Api-Key" "${NEXUS_KEY}" "${out_dir}/nexus_event.json" "nexus event"
    fetch_endpoint "${NEXUS_BASE}/event/${event_key}/map" "Nexus-Api-Key" "${NEXUS_KEY}" "${out_dir}/nexus_map.json"   "nexus map"
  else
    echo "NEXUS_KEY unset — skipping Nexus endpoints"
  fi

  echo "done."
}

# ---------- dispatch ----------

main() {
  require_tools
  [[ $# -ge 1 ]] || usage

  local sub="$1"; shift
  case "$sub" in
    list)      require_tba_key; cmd_list  "$@" ;;
    fetch)     require_tba_key; cmd_fetch "$@" ;;
    -h|--help) usage ;;
    *)         die "unknown subcommand: $sub" ;;
  esac
}

main "$@"
