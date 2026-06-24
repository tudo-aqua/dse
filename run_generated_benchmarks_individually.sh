#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
  echo "Usage: $0 <test-java-file> <log-dir> [parallel-jobs]"
  echo "  <test-java-file>  Path to the Java test class (relative or absolute)"
  echo "  <log-dir>         Directory where per-test log files will be written"
  echo "  [parallel-jobs]   Number of tests to run in parallel (default: 6)"
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

JOBS="${3:-}"
if [[ -z "${JOBS}" || ! "${JOBS}" =~ ^[0-9]+$ ]] || (( JOBS < 1 )); then
  JOBS=6
fi

# TTY detection — disables all ANSI tricks when output is redirected to a file
if [ -t 1 ]; then
  IS_TTY=1
  C_PASS=$'\e[32m'
  C_FAIL=$'\e[31m'
  C_RESET=$'\e[0m'
else
  IS_TTY=0
  C_PASS=''
  C_FAIL=''
  C_RESET=''
fi

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
        *TimeoutException*|*"timed out after"*)  reason="TIMEOUT" ;;
        *AssertionError*DIVERGED*|*AssertionFailedError*DIVERGED*) reason="DIVERGED" ;;
        *AssertionError*BUGGY*|*AssertionFailedError*BUGGY*)       reason="BUGGY" ;;
        *) reason="ERROR: $(printf "%s" "${after_arrow}" | cut -c1-200)" ;;
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

run_single_test() {
  local method="$1"
  local log_file="${LOG_DIR}/${method}.txt"
  local work_subdir="${WORK_DIR}/${method}"
  mkdir -p "${work_subdir}"
  mkdir -p "${LOG_DIR}/reports/${method}"

  # Mark as running (file contains start timestamp)
  printf "%d" "${SECONDS}" > "${RUNNING_DIR}/${method}"

  local test_start=${SECONDS}
  set +e
  (
    cd "${work_subdir}"
    mvn -f "${SCRIPT_DIR}/pom.xml" \
      -DargLine="-Xss128m" \
      -DfailIfNoTests=false \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dsurefire.reportsDirectory="${LOG_DIR}/reports/${method}" \
      -Dtest="${TEST_CLASS}#${method}" \
      surefire:test
  ) > "${log_file}" 2>&1
  local exit_code=$?
  set -e
  local test_elapsed=$((SECONDS - test_start))

  rm -f "${RUNNING_DIR}/${method}"

  # Clear the in-place status line before printing a result (TTY only)
  local pfx=""
  [ "${IS_TTY}" = "1" ] && pfx=$'\r\e[2K'

  local width=${#TOTAL}
  if [ ${exit_code} -eq 0 ]; then
    printf "PASS\t%d\n" "${test_elapsed}" > "${RESULTS_DIR}/${method}"
    local done
    done=$(ls -1 "${RESULTS_DIR}" | wc -l | tr -d ' ')
    printf '%s[%*d/%d] %sPASS%s %-80s (%s)\n' \
      "${pfx}" "${width}" "${done}" "${TOTAL}" \
      "${C_PASS}" "${C_RESET}" "${method}" "$(format_duration ${test_elapsed})"
  else
    local reason
    reason=$(classify_failure "${log_file}")
    printf "FAIL\t%d\t%s\n" "${test_elapsed}" "${reason}" > "${RESULTS_DIR}/${method}"
    touch "${FAILED_DIR}/${method}"
    local done
    done=$(ls -1 "${RESULTS_DIR}" | wc -l | tr -d ' ')
    printf '%s[%*d/%d] %sFAIL%s %-80s (%s, exit %d) — %s\n' \
      "${pfx}" "${width}" "${done}" "${TOTAL}" \
      "${C_FAIL}" "${C_RESET}" "${method}" \
      "$(format_duration ${test_elapsed})" "${exit_code}" "${reason}"
  fi
}

# Redraws the in-place progress bar using \r (no scroll region needed)
print_status_line() {
  [ "${IS_TTY}" = "1" ] || return 0
  set +e

  local cols
  cols=$(tput cols 2>/dev/null || echo 80)

  local done failed running elapsed
  done=$(ls -1 "${RESULTS_DIR}" 2>/dev/null | wc -l | tr -d ' ')
  failed=$(ls -1 "${FAILED_DIR}" 2>/dev/null | wc -l | tr -d ' ')
  running=$(ls -1 "${RUNNING_DIR}" 2>/dev/null | wc -l | tr -d ' ')
  elapsed=$((SECONDS - RUN_START))

  # ETA based on average completion time so far
  local eta_str
  if (( done > 0 && elapsed > 0 )); then
    local avg=$(( elapsed / done ))
    local remaining=$(( TOTAL - done ))
    local eta_secs=$(( avg * remaining / JOBS ))
    eta_str="ETA ~$(format_duration ${eta_secs})"
  else
    eta_str="ETA ..."
  fi

  # Percentage
  local pct=0
  if (( TOTAL > 0 )); then pct=$(( done * 100 / TOTAL )); fi

  # Progress bar width adapts to terminal width
  local bar_width=30
  if (( cols < 80 ));  then bar_width=20; fi
  if (( cols > 120 )); then bar_width=40; fi
  local filled=0
  if (( TOTAL > 0 )); then filled=$(( done * bar_width / TOTAL )); fi
  local bar="" i
  for ((i=0; i<filled; i++));         do bar+="█"; done
  for ((i=filled; i<bar_width; i++)); do bar+="░"; done

  # Color-coded fail count
  local fail_str=""
  if (( failed > 0 )); then
    fail_str=" | ${C_FAIL}${failed} failed${C_RESET}"
  fi

  local content
  content=$(printf '[%s] %d/%d (%d%%)%s | running %d | %s' \
    "${bar}" "${done}" "${TOTAL}" "${pct}" "${fail_str}" "${running}" "${eta_str}")

  # \r to start of line, \e[2K to clear, write without newline so line stays in place
  printf '\r\e[2K%s' "${content}"
  set -e
}

status_updater() {
  set +e
  while true; do
    sleep 1
    [ -f "${LOG_DIR}/_stop" ] && break
    print_status_line
  done
}

export -f run_single_test classify_failure format_duration print_status_line
export LOG_DIR SCRIPT_DIR TEST_CLASS C_PASS C_FAIL C_RESET IS_TTY

TOTAL=${#TESTS[@]}

echo "Running ${TOTAL} tests of ${TEST_CLASS} with ${JOBS} parallel jobs. Logs -> ${LOG_DIR}"
echo "Started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "Pre-compiling..."

mvn -f "${SCRIPT_DIR}/pom.xml" -DskipTests test-compile -q

echo "Compilation done. Starting tests..."
echo "---"

# Always start with clean helper dirs so counters are correct
RESULTS_DIR="${LOG_DIR}/_results"
FAILED_DIR="${LOG_DIR}/_failed"
RUNNING_DIR="${LOG_DIR}/_running"
WORK_DIR="${LOG_DIR}/_work"
rm -rf "${RESULTS_DIR}" "${FAILED_DIR}" "${RUNNING_DIR}"
mkdir -p "${RESULTS_DIR}" "${FAILED_DIR}" "${RUNNING_DIR}" "${WORK_DIR}" "${LOG_DIR}/reports"

export RESULTS_DIR FAILED_DIR RUNNING_DIR WORK_DIR TOTAL JOBS

RUN_START=${SECONDS}
export RUN_START

STATUS_PID=""

cleanup() {
  if [ -n "${STATUS_PID}" ]; then
    kill "${STATUS_PID}" 2>/dev/null || true
    wait "${STATUS_PID}" 2>/dev/null || true
  fi
  # Move to a fresh line so the shell prompt appears cleanly after the status line
  [ "${IS_TTY}" = "1" ] && printf '\n'
}
trap cleanup EXIT

if [ "${IS_TTY}" = "1" ]; then
  status_updater &
  STATUS_PID=$!
fi

printf '%s\n' "${TESTS[@]}" | xargs -P "${JOBS}" -n1 bash -c 'run_single_test "$1"' --

touch "${LOG_DIR}/_stop"
if [ -n "${STATUS_PID}" ]; then
  wait "${STATUS_PID}" 2>/dev/null || true
  STATUS_PID=""
fi

total_elapsed=$((SECONDS - RUN_START))

PASSED=0
FAILED=0
for method in "${TESTS[@]}"; do
  if [ -f "${RESULTS_DIR}/${method}" ]; then
    status=$(cut -f1 "${RESULTS_DIR}/${method}")
    if [ "${status}" = "PASS" ]; then
      PASSED=$((PASSED + 1))
    else
      FAILED=$((FAILED + 1))
    fi
  else
    FAILED=$((FAILED + 1))
  fi
done

echo "---"
printf "Results: %s%d passed%s, %s%d failed%s (of %d total)\n" \
  "${C_PASS}" "${PASSED}" "${C_RESET}" \
  "${C_FAIL}" "${FAILED}" "${C_RESET}" \
  "${TOTAL}"
echo "Total time: $(format_duration ${total_elapsed})"
echo "Logs written to: ${LOG_DIR}"
