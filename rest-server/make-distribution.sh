#!/usr/bin/env bash

set -e

usage ()
{
    echo "Usage: $0 [-h|--help]"
    echo ""
    echo "  -h|--help    Show usage"
    echo ""
    echo "  --with-rpm   Build distribution for RPM package"
    echo "  --with-deb   Build distribution for DEB package"
}

JAVA_PROPS=()

for i in "$@"
do
case $i in
    -h|--help)
    usage
    shift
    exit
    ;;
    -D*)
    JAVA_PROPS+=("$i")
    shift
    ;;
    --with-rpm)
    RPM_BUILD=true
    shift
    ;;
    --with-deb)
    DEB_BUILD=true
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

echo "Building binary distribution for ActionML PioKappa $VERSION..."

cd ${FWDIR}
set -x
sbt/sbt "${JAVA_PROPS[@]}" clean
sbt/sbt "${JAVA_PROPS[@]}" universal:stage
set +x

cd ${FWDIR}
rm -rf ${DISTDIR}
mkdir -p ${DISTDIR}/bin
mkdir -p ${DISTDIR}/conf
mkdir -p ${DISTDIR}/lib
mkdir -p ${DISTDIR}/project

mkdir -p ${DISTDIR}/sbt

cp ${FWDIR}/bin/* ${DISTDIR}/bin || :
cp ${FWDIR}/conf/* ${DISTDIR}/conf
cp ${FWDIR}/project/build.properties ${DISTDIR}/project
cp ${FWDIR}/sbt/sbt ${DISTDIR}/sbt
cp ${FWDIR}/target/universal/stage/lib/* ${DISTDIR}/lib
cp ${FWDIR}/target/universal/stage/bin/main ${DISTDIR}/bin

touch ${DISTDIR}/RELEASE

TARNAME="ActionML-$VERSION.tar.gz"
TARDIR="ActionML-$VERSION"
cp -r ${DISTDIR} ${TARDIR}

tar zcvf ${TARNAME} ${TARDIR}
rm -rf ${TARDIR}

echo -e "\033[0;32mActionML binary distribution created at $TARNAME\033[0m"