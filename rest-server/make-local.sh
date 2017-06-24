#!/usr/bin/env bash

set -e

usage ()
{
    echo "Usage: $0 [-h|--help]"
    echo ""
    echo "  -h|--help    Show usage"
}

JAVA_PROPS=()

for i in "$@"
do
case ${i} in
    -h|--help)
    usage
    shift
    exit
    ;;
    -D*)
    JAVA_PROPS+=("$i")
    shift
    ;;
    *)
    usage
    exit 1
    ;;
esac
done

FWDIR="$(cd `dirname $0`; pwd)"

VERSION=$(grep ^version ${FWDIR}/build.sbt | grep -o '".*"' | sed 's/"//g')

echo "Building local Harness $VERSION..."

cd ${FWDIR}
set -x
sbt/sbt "${JAVA_PROPS[@]}" server/clean
sbt/sbt "${JAVA_PROPS[@]}" server/universal:stage
cp server/target/universal/stage/bin/server bin/main
cp ${FWDIR}/server/target/universal/stage/bin/server ${FWDIR}/bin/main

mkdir ${FWDIR}/logs
