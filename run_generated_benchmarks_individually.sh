#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -ne 2 ]; then
  echo "Usage: $0 <test-java-file> <log-dir>"
  echo "  <test-java-file>  Path to the Java test class (relative or absolute)"
  echo "  <log-dir>         Directory where per-test log files will be written"
  exit 1
fi

TEST_FILE="$1"
if [ ! -f "${TEST_FILE}" ]; then
  echo "Error: test file not found: ${TEST_FILE}"
  exit 1
fi

TEST_CLASS="$(basename "${TEST_FILE}" .java)"

LOG_DIR="$2"
if [[ "${LOG_DIR}" != /* ]]; then
  LOG_DIR="${SCRIPT_DIR}/${LOG_DIR}"
fi
mkdir -p "${LOG_DIR}"

TESTS=()
while IFS= read -r line; do
  TESTS+=("${line}")
done < <(awk '
  /@Test([^A-Za-z_]|$)/ { want=1; next }
  want {
    if (match($0, /void[[:space:]]+[A-Za-z_][A-Za-z0-9_]*/)) {
      name = substr($0, RSTART + 5, RLENGTH - 5)
      gsub(/^[[:space:]]+/, "", name)
      print name
      want = 0
    } else if (/[{};]/) { want = 0 }
  }
' "${TEST_FILE}")

if [ ${#TESTS[@]} -eq 0 ]; then
  echo "Error: no @Test methods found in ${TEST_FILE}"
  exit 1
fi

classify_failure() {
  local log="$1"
  local reason=""
  set +e
  if grep -q "COMPILATION ERROR" "${log}" 2>/dev/null; then
    reason="COMPILE_ERROR"
  elif grep -q "No tests were executed" "${log}" 2>/dev/null; then
    reason="NO_TESTS"
  else
    local surefire_line
    surefire_line=$(grep -E "^\[ERROR\][[:space:]]+[A-Za-z].*»" "${log}" 2>/dev/null | head -1)
    if [ -n "${surefire_line}" ]; then
      local after_arrow
      after_arrow=$(printf "%s" "${surefire_line}" | sed 's/.*» //')
      case "${after_arrow}" in
        *TimeoutException*|*timed out after*)  reason="TIMEOUT" ;;
        *AssertionError*DIVERGED*|*AssertionFailedError*DIVERGED*) reason="DIVERGED" ;;
        *AssertionError*BUGGY*|*AssertionFailedError*BUGGY*)       reason="BUGGY" ;;
        *) reason="ERROR: $(printf "%s" "${after_arrow}" | cut -c1-80)" ;;
      esac
    else
      reason="UNKNOWN"
    fi
  fi
  set -e
  printf "%s" "${reason}"
}

format_duration() {
  local secs=$1
  local h=$((secs / 3600))
  local m=$(((secs % 3600) / 60))
  local s=$((secs % 60))
  if [ ${h} -gt 0 ]; then
    printf "%dh %02dm %02ds" ${h} ${m} ${s}
  elif [ ${m} -gt 0 ]; then
    printf "%dm %02ds" ${m} ${s}
  else
    printf "%ds" ${s}
  fi
}

TOTAL=${#TESTS[@]}
PASSED=0
FAILED=0
INDEX=0

echo "Running ${TOTAL} tests of ${TEST_CLASS} individually. Logs -> ${LOG_DIR}"
echo "Started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "---"

run_start=${SECONDS}

for method in "${TESTS[@]}"; do
  INDEX=$((INDEX + 1))
  log_file="${LOG_DIR}/${method}.txt"
  printf "[%${#TOTAL}d/%d] %-90s " "${INDEX}" "${TOTAL}" "${method}"

  test_start=${SECONDS}
  set +e
  mvn -f "${SCRIPT_DIR}/pom.xml" \
    -DargLine="-Xss128m" \
    -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest="${TEST_CLASS}#${method}" \
    test > "${log_file}" 2>&1
  exit_code=$?
  set -e
  test_elapsed=$((SECONDS - test_start))

  if [ ${exit_code} -eq 0 ]; then
    echo "PASS ($(format_duration ${test_elapsed}))"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL (exit ${exit_code}, $(format_duration ${test_elapsed})) — $(classify_failure "${log_file}")"
    FAILED=$((FAILED + 1))
  fi
done

total_elapsed=$((SECONDS - run_start))

echo "---"
echo "Results: ${PASSED} passed, ${FAILED} failed (of ${TOTAL} total)"
echo "Total time: $(format_duration ${total_elapsed})"
echo "Logs written to: ${LOG_DIR}"
