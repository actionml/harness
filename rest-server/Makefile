JAVA_PROPS ?=
VERSION := $(shell grep ^version build.sbt | grep -o '".*"' | sed 's/"//g')
DIST := dist/
.DEFAULT_GOAL := build

include ../makefile.sbt.sh

# Fallback to system-wide sbt binary
ifeq (,$(wildcard $(SBT)))
SBT ?= sbt
endif


.PHONY: clean build dist clean_dist

build: sbt
    $(SBT) $(JAVA_PROPS) -batch server/universal:stage

clean: sbt
    $(SBT) $(JAVA_PROPS) -batch server/clean

clean_dist: 
	rm -r $(DIST)
	echo "Cleanging old artifacts for Harness-"$(VERSION)
	rm Harness-$(VERSION).tar.gz

## Make rest server dist/rest-server
#
dist: clean build clean_dist
    mkdir -p $(DIST) && cd $(DIST) && rm -rf ./* && mkdir bin conf project lib
    cp bin/* $(DIST)/bin/
    cp conf/* $(DIST)/conf/
    cp project/build.properties $(DIST)/project/
    cp server/target/universal/stage/lib/* $(DIST)/lib/
    cp server/target/universal/stage/bin/server $(DIST)/bin/main
    echo $(VERSION) > $(DIST)/RELEASE
