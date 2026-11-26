#!/usr/bin/env bash
# End-to-end API smoke test against a running stack:
# health -> login -> CSV preview + import -> categorised transactions -> NL question (SQL + rows)
# -> dashboard / forecast / anomalies / recurring / goal suggestion.
set -euo pipefail
BASE="${BASE_URL:-http://localhost:20800}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CSV="$ROOT/docs/samples/sample_statement.csv"
jq_py() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

echo "1. health"
curl -fsS "$BASE/actuator/health" | jq_py 'd["status"]'

echo "2. login as demo user"
TOKEN=$(curl -fsS -H 'Content-Type: application/json' \
  -d '{"email":"demo@finpilot.dev","password":"DemoPass123!"}' "$BASE/api/v1/auth/login" | jq_py 'd["accessToken"]')
AUTH="Authorization: Bearer $TOKEN"

echo "3. preview sample statement"
curl -fsS -H "$AUTH" -F "file=@$CSV" "$BASE/api/v1/imports/preview" \
  | jq_py '"headers=%s suggested=%s" % (d["headers"], d["suggestedMapping"])'

echo "4. import with mapping"
MAPPING='{"date":"Txn Date","time":"Time","description":"Narration","debit":"Withdrawal Amt","credit":"Deposit Amt","dateFormat":"dd/MM/yyyy"}'
curl -fsS -H "$AUTH" -F "file=@$CSV" -F "mapping=$MAPPING;type=application/json" "$BASE/api/v1/imports" \
  | jq_py '"imported=%d duplicates=%d failed=%d" % (d["imported"], d["duplicates"], d["failed"])'

echo "5. categorised transactions"
curl -fsS -H "$AUTH" "$BASE/api/v1/transactions?size=5" \
  | jq_py '"\n".join("  %s | %-28s | %10.2f | %-13s (%s %.2f)" % (t["postedAt"][:10], t["merchant"][:28], t["amount"], t["category"], t["categorySource"], t["categoryConfidence"] or 0) for t in d["content"]) + "\n  total=%d" % d["totalElements"]'

echo "6. natural-language question"
curl -fsS -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"question":"How much did I spend on food last month vs this month?"}' "$BASE/api/v1/assistant/query" \
  | jq_py '"SQL:\n" + d["sql"] + "\nrows=%s chart=%s\nsummary=%s provider=%s" % (d["rows"], d["chart"]["type"], d["summary"], d["provider"])'

echo "7. dashboard / forecast / anomalies / recurring"
curl -fsS -H "$AUTH" "$BASE/api/v1/dashboard" | jq_py '"income=%.0f expense=%.0f txns=%d flagged=%d" % (d["totals"]["income"], d["totals"]["expense"], d["transactionCount"], d["flaggedAnomalies"])'
curl -fsS -H "$AUTH" "$BASE/api/v1/forecast" | jq_py '"forecast categories=%d next-month total=%.0f [%.0f..%.0f]" % (len(d["categories"]), d["total"]["forecast"][0]["mean"], d["total"]["forecast"][0]["lower"], d["total"]["forecast"][0]["upper"])'
curl -fsS -H "$AUTH" -X POST "$BASE/api/v1/anomalies/scan" | jq_py '"anomaly scan: %s" % d'
curl -fsS -H "$AUTH" "$BASE/api/v1/anomalies" | jq_py '"top anomaly: %s" % ((d[0]["merchant"], d[0]["amount"], d[0]["anomaly"]["reasons"]) if d else None)'
curl -fsS -H "$AUTH" "$BASE/api/v1/recurring" | jq_py '"recurring: %s" % [(r["merchant"], r["cadence"], r["averageAmount"]) for r in d[:5]]'
GOAL=$(curl -fsS -H "$AUTH" "$BASE/api/v1/goals" | jq_py 'd[0]["id"]')
curl -fsS -H "$AUTH" "$BASE/api/v1/goals/$GOAL/suggestion" | jq_py '"goal advice: " + d["advice"]'
echo "SMOKE OK"
