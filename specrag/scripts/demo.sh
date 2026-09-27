#!/usr/bin/env bash
# End-to-end demonstration of the SPEC-002 RAG pipeline.
#
# Assumes the service is already running (mvn spring-boot:run). Every step below maps to an
# acceptance criterion, printed alongside the call so the output is self-describing.
set -euo pipefail

BASE="${BASE:-http://localhost:8081}"
CORPUS="${CORPUS:-$(cd "$(dirname "$0")/.." && pwd)}"

say() { printf '\n\033[1m── %s\033[0m\n' "$1"; }
call() { curl -s -w '\n[HTTP %{http_code}]\n' "$@"; }

say "AC-1  Ingest the corpus (full rebuild)"
call -X POST "$BASE/api/ingest" -H 'Content-Type: application/json' \
  -d "{\"path\":\"$CORPUS\"}"

say "AC-5  Re-ingesting the same corpus is idempotent — the count must not double"
call -X POST "$BASE/api/ingest" -H 'Content-Type: application/json' \
  -d "{\"path\":\"$CORPUS\"}" >/dev/null
call "$BASE/api/chunks/count"

say "AC-3  A path outside the allowed root is refused (403)"
call -X POST "$BASE/api/ingest" -H 'Content-Type: application/json' \
  -d '{"path":"/etc"}'

say "AC-2  A path that does not exist is refused (400)"
call -X POST "$BASE/api/ingest" -H 'Content-Type: application/json' \
  -d "{\"path\":\"$CORPUS/does-not-exist\"}"

say "AC-4  An unknown chunker is refused, and the supported ids are named (400)"
call -X POST "$BASE/api/ingest" -H 'Content-Type: application/json' \
  -d "{\"path\":\"$CORPUS\",\"chunker\":\"semantic-magic\"}"

say "AC-12/AC-17  Answer with citations"
call -X POST "$BASE/api/query" -H 'Content-Type: application/json' \
  -d '{"question":"How does ingest stop a path from escaping the allowed root?"}'

say "AC-16  The same question, filtered to markdown only"
call -X POST "$BASE/api/query" -H 'Content-Type: application/json' \
  -d '{"question":"How does ingest stop a path from escaping the allowed root?","sourceType":"markdown"}'

say "AC-13  A question with no relevant context is not answered from thin air"
# The question must be absent from the corpus — and this corpus indexes its own tests, so a
# phrase used in an AC-13 test is NOT off-corpus once ingested. See SPEC-002 §9.5.
call -X POST "$BASE/api/query" -H 'Content-Type: application/json' \
  -d '{"question":"Which varieties of grape ripen earliest in a maritime climate?"}'

say "AC-15  A blank question is rejected, not embedded (400)"
call -X POST "$BASE/api/query" -H 'Content-Type: application/json' -d '{"question":"   "}'

say "NFR-5  The repeated query is served from cache"
call -X POST "$BASE/api/query" -H 'Content-Type: application/json' \
  -d '{"question":"How does ingest stop a path from escaping the allowed root?"}' \
  | python3 -c 'import json,sys; d=json.loads(sys.stdin.readline()); print("cached:", d["cached"], "| elapsedMs:", d["elapsedMs"])'
call "$BASE/api/cache"

say "AC-19/AC-20  Eval harness over the labelled question set"
call -X POST "$BASE/api/eval"

say "TASK-009  The same questions under both retrievers, swept across top-K"
# The point of the sweep is that a single operating point can lie. Reading only the topK=5 row
# would report hybrid as a clear win; the rows either side of it withdraw the claim (SPEC-002 §9.2).
printf '%-6s%-10s%-9s%-9s%-9s%s\n' topK mode P@K R@K MRR answerAcc
for k in 3 5 10; do
  for m in semantic hybrid; do
    curl -s -X POST "$BASE/api/eval?topK=$k&mode=$m" \
      | python3 -c "
import json,sys
s=json.load(sys.stdin)['summary']
print('%-6s%-10s%-9.3f%-9.3f%-9.3f%.3f' % ('$k','$m',
      s['retrievalPrecisionAtK'], s['retrievalRecallAtK'],
      s['meanReciprocalRank'], s['answerAccuracy']))"
  done
done
