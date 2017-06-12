#!/usr/bin/env bash

. 'it/header.sh'

echo ""
echo " > Up docker containers"
echo ""
docker-compose down
docker-compose up -d --build

EXPECT_EVENTS_FILE='it/send-events/expect-events'
OUT_EVENTS_FILE='it/send-events/out-events'

EXPECT_QUERIES_FILE='it/send-events/expect-queries'
OUT_QUERIES_FILE='it/send-events/out-queries'

echo ""
echo " > Removing old result files"
echo ""
if [ -f ${OUT_EVENTS_FILE} ]; then
    rm ${OUT_EVENTS_FILE}
fi

if [ -f ${OUT_QUERIES_FILE} ]; then
    rm ${OUT_QUERIES_FILE}
fi

echo ""
echo " > Checking for needed files"
echo ""
if [ ! -f ${EXPECT_EVENTS_FILE} ]; then
    echo "File not found: ${EXPECT_EVENTS_FILE}"
    exit 1
fi

if [ ! -f ${EXPECT_QUERIES_FILE} ]; then
    echo "File not found: ${EXPECT_QUERIES_FILE}"
    exit 1
fi

echo ""
echo " > Starting harness server"
echo ""
docker exec harness_router_1 /app/bin/harness start
sleep 3
echo ""
echo " > Check status server"
echo ""
docker exec harness_router_1 /app/bin/harness status
echo ""
echo " > Add new engine"
echo ""
docker exec harness_router_1 /app/bin/harness add -c /data/engine.json
echo ""
echo " > Sending events"
echo ""
docker exec harness_java_client_1 /app/it_send_events.sh TEST-ENGINE-ID /data/test-ds-1.json | sed -f 'it/clean' | tee -a ${OUT_EVENTS_FILE}

sleep 3
echo ""
echo " > Sending queries"
echo ""
docker exec harness_java_client_1 /app/it_send_queries.sh TEST-ENGINE-ID /data/queries-for-test-ds-1.json | sed -f 'it/clean' | tee -a ${OUT_QUERIES_FILE}

echo ""
echo " > Stopping harness server"
echo ""
docker exec harness_router_1 /app/bin/harness stop

DIFF_EVENTS_RESULT=`diff ${OUT_EVENTS_FILE} ${EXPECT_EVENTS_FILE}`

if [ -z ${DIFF_EVENTS_RESULT} ]; then
    echo -e "${GLINE}"
    echo -e "${GREEN}Send events tests success passed!${NC}"
    echo -e "${GLINE}"
else
    echo ${DIFF_EVENTS_RESULT}
    echo -e "${RLINE}"
    echo -e "${RED}Send events tests failure!${NC}"
    echo -e "${RLINE}"
fi

DIFF_QUERIES_RESULT=`diff ${OUT_QUERIES_FILE} ${EXPECT_QUERIES_FILE}`

if [ -z ${DIFF_QUERIES_RESULT} ]; then
    echo -e "${GLINE}"
    echo -e "${GREEN}Send queries tests success passed!${NC}"
    echo -e "${GLINE}"
else
    echo ${DIFF_QUERIES_RESULT}
    echo -e "${RLINE}"
    echo -e "${RED}Send queries tests failure!${NC}"
    echo -e "${RLINE}"
fi

echo ""
echo " > Down docker containers"
echo ""
docker-compose down



