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

TOTAL=${#TESTS[@]}
PASSED=0
FAILED=0

echo "Running ${TOTAL} tests of ${TEST_CLASS} individually. Logs -> ${LOG_DIR}"
echo "---"

for method in "${TESTS[@]}"; do
  log_file="${LOG_DIR}/${method}.txt"
  printf "%-90s " "${method}"

  set +e
  mvn -f "${SCRIPT_DIR}/pom.xml" \
    -DargLine="-Xss128m" \
    -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest="${TEST_CLASS}#${method}" \
    test > "${log_file}" 2>&1
  exit_code=$?
  set -e

  if [ ${exit_code} -eq 0 ]; then
    echo "PASS"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL (exit ${exit_code})"
    FAILED=$((FAILED + 1))
  fi
done

echo "---"
echo "Results: ${PASSED} passed, ${FAILED} failed (of ${TOTAL} total)"
echo "Logs written to: ${LOG_DIR}"
