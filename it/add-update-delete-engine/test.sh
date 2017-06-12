#!/usr/bin/env bash

. 'it/header.sh'

docker-compose down
docker-compose up -d --build

EXPECT_FILE="it/add-update-delete-engine/expect"
OUT_FILE="it/add-update-delete-engine/out"

echo "Removing old result files"
if [ -f ${OUT_FILE} ]; then
    rm ${OUT_FILE}
fi

echo "Checking for needed files"
if [ ! -f ${EXPECT_FILE} ]; then
    echo "File not found: ${EXPECT_FILE}"
    exit 1
fi

docker exec harness_router_1 /app/bin/harness start | sed -f 'it/clean' >> ${OUT_FILE}
sleep 3
docker exec harness_router_1 /app/bin/harness add -c /data/engine.json | sed -f 'it/clean' >> ${OUT_FILE}
docker exec harness_router_1 /app/bin/harness update -c /data/engine.json | sed -f 'it/clean' >> ${OUT_FILE}
docker exec harness_router_1 /app/bin/harness delete -c /data/engine.json | sed -f 'it/clean' >> ${OUT_FILE}
docker exec harness_router_1 /app/bin/harness stop | sed -f 'it/clean' >> ${OUT_FILE}

DIFF_RESULT=`diff ${OUT_FILE} ${EXPECT_FILE}`

if [ -z ${DIFF_RESULT} ]; then
    echo -e "${GLINE}"
    echo -e "${GREEN}All tests success passed!${NC}"
    echo -e "${GLINE}"
else
    echo ${DIFF_RESULT}
    echo -e "${RLINE}"
    echo -e "${RED}Something went wrong!${NC}"
    echo -e "${RLINE}"
fi

docker-compose down



