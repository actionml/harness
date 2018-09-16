JAVA_PROPS ?=
DIST := dist/
BUILD_ROOT := $(abspath ../)
VERSION := $(shell grep ^version $(BUILD_ROOT)/build.sbt | grep -o '".*"' | sed 's/"//g')
.DEFAULT_GOAL := build

# include harness top level makefile
include ../../makefile.sbt

# Fallback to system-wide sbt binary
ifeq (,$(wildcard $(SBT)))
SBT ?= sbt
endif


.PHONY: clean build dist

build: sbt
	cd $(BUILD_ROOT) && \
		$(SBT) $(JAVA_PROPS) -batch authServer/universal:stage

clean: sbt
	cd $(BUILD_ROOT) && \
	$(SBT) $(JAVA_PROPS) -batch authServer/clean

dist:
	mkdir -p $(DIST) && cd $(DIST) && rm -rf ./* && mkdir bin conf project lib
	cp bin/* $(DIST)/bin
	cp src/main/resources/*.conf $(DIST)/conf
	cp src/main/resources/*.xml $(DIST)/conf
	cp $(BUILD_ROOT)/conf/harness.jks $(DIST)/
	cp $(BUILD_ROOT)/project/build.properties $(DIST)/project
	cp target/universal/stage/lib/* $(DIST)/lib
	cp target/universal/stage/bin/authserver $(DIST)/bin/main
	echo $(VERSION) > $(DIST)/RELEASE
