# harness common env initialization script (meant to be sourced)

# Reset
export NC='\033[0m'           # Text Reset

# Regular Colors
export RED='\033[0;31m'          # Red--error
export GREEN='\033[0;32m'        # Green
export YELLOW='\033[0;33m'       # Yellow
export BLUE='\033[0;34m'         # Blue
export PURPLE='\033[0;35m'       # Purple
export CYAN='\033[0;36m'         # Cyan--hints and info messages
export WHITE='\033[0;37m'        # White


# export HARNESS_HOME if not set
if [ -z "${HARNESS_HOME}" ]; then
  source_path=$(readlink -f "$0" 2>/dev/null)

  if [ $? -eq 0 -a -n "${source_path}" ]; then
    # readlink has -f and suceeded
    export HARNESS_HOME=$(dirname "${source_path%/*}")
  else
    # try recursively locate while we get links
    target_dir=$(cd `dirname "$0"`; pwd)
    while [ -L "${target_dir}" ]; do
      target_dir=$(ls -l "${target_dir}" | sed 's/.* -> //')
      target_dir="$(cd `dirname "${target_dir}"`; pwd)"
    done
    export HARNESS_HOME="${target_dir}"
  fi
fi

# source the harness-env file
. "${HARNESS_ENVFILE:-${HARNESS_HOME}/bin/harness-env}"

if [ "${HARNESS_AUTH_ENABLED}" != "true" ]; then
  USER_ARGS=""
elif [ ! -z "$ADMIN_USER_ID" ] && [ ! -z "$ADMIN_USER_SECRET_LOCATION" ] && [ "${HARNESS_AUTH_ENABLED}" = "true" ]; then
  USER_ARGS=" --client_user_id ${ADMIN_USER_ID} --client_user_secret_location ${ADMIN_USER_SECRET_LOCATION} "
else
  echo -e "${RED}Admin ID and Auth mis-configured see bin/harness-env. If you want auth enabled all these must be set ${NC}"
  echo -e "${RED}ADMIN_USER_ID, ADMIN_USER_SECRET_LOCATION, HARNESS_AUTH_ENABLED${NC}"
  exit 1
fi
export USER_ARGS


## Check system compatibilty
#
( which ps >& /dev/null && which grep >& /dev/null && which sed >& /dev/null ) || fail=yes
( ps -w &> /dev/null ) || fail=yes
if [ "$fail" = yes ]; then
  >&2 echo "System utilities required, please install:"
  >&2 echo "==> ps (gnu compatible with -w option), sed, grep!"
  exit 1
fi


## Print a status line "message .(...) value"
#
status_line() {
  local char="." total=64
  len=$((total - ${#1} - ${#2}))
  padding=$(printf "%-${len}s" "${char}")
  padding=${padding// /${char}}
  printf "%s ${padding} %s\n" "${1}" "${2}"
}


## Locate java of a class
#
java_pid() {
  local pid service_class="$1"
  ps -e ww -o pid,command | grep "\bjava .*${service_class}" | sed 's/^ *\([0-9]*\) .*/\1/'
}


## Check if harness is running
harness_running() {
  if [ "$1" = "-v" ]; then
    "${HARNESS_HOME}"/bin/commands.py status "${USER_ARGS}"
  else
    "${HARNESS_HOME}"/bin/commands.py status "${USER_ARGS}" &> /dev/null
  fi
}


## Wait for harness is running
waitfor_harness() {
  local checks=3 delay=2 timeout=${1:-30}

  while ( ! harness_running || [ "${checks}" -gt 0 ] ); do
    [ "${timeout}" -le 0 ] && break
    sleep $delay
    checks=$((checks - 1))
    timeout=$((timeout - delay))
  done

  harness_running
}
