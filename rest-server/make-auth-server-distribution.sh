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
DISTDIR="auth-server/${FWDIR}/dist"

VERSION=$(grep ^version ${FWDIR}/build.sbt | grep -o '".*"' | sed 's/"//g')

echo "Building binary distribution for Auth Server $VERSION..."

cd ${FWDIR}
set -x
sbt/sbt "${JAVA_PROPS[@]}" authServer/clean
sbt/sbt "${JAVA_PROPS[@]}" authServer/universal:stage
set +x

cd ${FWDIR}
rm -rf ${DISTDIR}
mkdir -p ${DISTDIR}/bin
mkdir -p ${DISTDIR}/conf
mkdir -p ${DISTDIR}/logs
mkdir -p ${DISTDIR}/lib
mkdir -p ${DISTDIR}/project

mkdir -p ${DISTDIR}/sbt

cp ${FWDIR}/auth-server/bin/* ${DISTDIR}/bin || :
cp ${FWDIR}/auth-server/src/main/resources/*.conf ${DISTDIR}/conf
cp ${FWDIR}/auth-server/src/main/resources/*.xml ${DISTDIR}/conf
cp ${FWDIR}/project/build.properties ${DISTDIR}/project
cp ${FWDIR}/sbt/sbt ${DISTDIR}/sbt
cp ${FWDIR}/auth-server/target/universal/stage/lib/* ${DISTDIR}/lib
cp ${FWDIR}/auth-server/target/universal/stage/bin/authserver ${DISTDIR}/bin/main

touch ${DISTDIR}/RELEASE

TARNAME="AuthServer-$VERSION.tar.gz"
TARDIR="AuthServer-$VERSION"
cp -r ${DISTDIR} ${TARDIR}

tar zcvf ${TARNAME} ${TARDIR}
rm -rf ${TARDIR}
rm -rf ${DISTDIR}

echo -e "\033[0;32mAuth server binary distribution created at $TARNAME\033[0m"