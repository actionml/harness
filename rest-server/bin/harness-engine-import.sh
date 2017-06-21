#!/usr/bin/env bash

. "${HARNESS_HOME}/bin/harness-env"

while [ -n "$1" ]
do
    case "$1" in
        -i) FILENAME=$2
            shift ;;
        *) RESOURCE_ID=$1 ;;
    esac
shift
done


if [ -z "$RESOURCE_ID" ]; then
    echo -e "${RED}Engine resource id not specified!${NC}"
    echo -e "Expected command: ${CYAN}harness import <some-resource-id> [-i <some-directory> | -i <some-file>]${NC}"
    exit 1
fi

if [ ! -z ${FILENAME} ]; then
    echo -e "${RED}Engine events JSON file [${FILENAME}] not found!${NC}"
    echo -e "Expected command: ${CYAN}harness import <some-resource-id> [-i <some-directory> | -i <some-file>]${NC}"
    echo -e "Verify that the correct file or directory path is specified."
    exit 1
fi

PYTHON_ARGS="import"
if [ -n "$RESOURCE_ID" ]; then PYTHON_ARGS="${PYTHON_ARGS} ${RESOURCE_ID}"; fi
if [ -n "$FILENAME" ]; then PYTHON_ARGS="${PYTHON_ARGS} -c ${FILENAME}"; fi


echo -e "${CYAN}Run ${HARNESS_HOME}/bin/engine.py ${PYTHON_ARGS}${NC}"

${HARNESS_HOME}/bin/engine.py ${PYTHON_ARGS}
