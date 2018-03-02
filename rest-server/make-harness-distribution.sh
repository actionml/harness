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
DISTDIR="${FWDIR}/dist"

VERSION=$(grep ^version ${FWDIR}/build.sbt | grep -o '".*"' | sed 's/"//g')

echo "Building binary distribution for Harness $VERSION..."

cd ${FWDIR}
set -x
sbt/sbt "${JAVA_PROPS[@]}" server/clean
sbt/sbt "${JAVA_PROPS[@]}" server/universal:stage
set +x

cd ${FWDIR}
rm -rf ${DISTDIR}
mkdir -p ${DISTDIR}/bin
mkdir -p ${DISTDIR}/conf
mkdir -p ${DISTDIR}/logs
mkdir -p ${DISTDIR}/lib
mkdir -p ${DISTDIR}/project

mkdir -p ${DISTDIR}/sbt

cp ${FWDIR}/bin/* ${DISTDIR}/bin || :
cp ${FWDIR}/conf/logback.xml ${DISTDIR}/conf
cp ${FWDIR}/server/src/main/resources/*.conf ${DISTDIR}/conf
cp ${FWDIR}/keystore.jks ${DISTDIR}
cp ${FWDIR}/project/build.properties ${DISTDIR}/project
cp ${FWDIR}/sbt/sbt ${DISTDIR}/sbt
cp ${FWDIR}/server/target/universal/stage/lib/* ${DISTDIR}/lib
cp ${FWDIR}/server/target/universal/stage/bin/server ${DISTDIR}/bin/main

touch ${DISTDIR}/RELEASE

TARNAME="Harness-$VERSION.tar.gz"
TARDIR="Harness-$VERSION"
cp -r ${DISTDIR} ${TARDIR}

tar zcvf ${TARNAME} ${TARDIR}
rm -rf ${TARDIR}
rm -rf ${DISTDIR}

echo -e "\033[0;32mHarness binary distribution created at $TARNAME\033[0m"