#!/usr/bin/env bash

# harness common env initialization script (meant to be sourced)

# Reset
export NC='\033[0m'           # Text Reset

# Regular Colors
export RED='\033[0;31m'          # Red--error
export GREEN='\033[0;32m'        # Green
export YELLOW='\033[0;33m'       # Yellow--warning
export BLUE='\033[0;34m'         # Blue
export PURPLE='\033[0;35m'       # Purple
export CYAN='\033[0;36m'         # Cyan--hints and info messages
export WHITE='\033[0;37m'        # White


## BSD/MacOS compatible readlink -f
#
_readlink_f() {
  target=$1
  cd $(dirname $target)
  target=$(basename $target)

  while [ -L "$target" ]; do
      target=$(readlink $target)
      cd $(dirname $target)
      target=$(basename $target)
  done

  physical_directory=$(pwd -P)
  echo $physical_directory/$(basename $target)
}


# export HARNESS_HOME if not set
if [ -z "${HARNESS_HOME}" ]; then
  if [ -z "$(which readlink 2>/dev/null)" ]; then
    echo -e "${RED}readlink command must be present on your system!${NC}"
    exit 1
  fi

  bindir=$(dirname `_readlink_f $0`)
  # trim the last path element (which is supposed to be /bin(/))
  export HARNESS_HOME=${bindir%/*}
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
  ps -e -w -w -o pid,command | grep "\bjava .*${service_class}" | sed 's/^ *\([0-9]*\) .*/\1/'
}
