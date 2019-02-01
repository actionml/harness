.DEFAULT_GOAL := build
.PHONY: sbt clean clean-dist build dist

HARNESS_ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SBT_URL ?= https://git.io/sbt
SBT ?= $(HARNESS_ROOT)/sbt/sbt

RESTSRV_DIR := $(HARNESS_ROOT)/rest-server
PYTHON_SDK_DIR := $(HARNESS_ROOT)/python-sdk
DIST ?= ./dist

VERSION := $(shell grep ^version $(RESTSRV_DIR)/build.sbt | grep -o '".*"' | sed 's/"//g')
# Enforce version of Scala set in build.sbt (specificly passed to sbt later!)
SCALA_VERSION := $(shell grep ^scalaVersion $(RESTSRV_DIR)/build.sbt | grep -o '".*"' | sed 's/"//g')

# Install sbtx locally
sbt:
ifeq ($(SBT),$(HARNESS_ROOT)/sbt/sbt)
	@ SBT_DIR="$$(dirname $(SBT))" && mkdir -p $$SBT_DIR && cd $$SBT_DIR && \
	[ -x sbt ] || ( echo "Installing sbt extras locally (from $(SBT_URL))"; \
		which curl &> /dev/null && ( curl \-#SL -o sbt \
			https://git.io/sbt && chmod 0755 sbt || exit 1; ) || \
			( which wget &>/dev/null && wget -O sbt https://git.io/sbt && chmod 0755 sbt; ) \
	)
endif


build: sbt
	cd $(RESTSRV_DIR); \
		$(SBT) ++$(SCALA_VERSION) -batch server/universal:stage

clean: sbt
	cd $(RESTSRV_DIR); \
		$(SBT) ++$(SCALA_VERSION) -batch server/clean

dist: clean clean-dist build wheel
	mkdir -p $(DIST) && cd $(DIST) && mkdir bin conf logs project lib
	cp $(RESTSRV_DIR)/bin/* $(DIST)/bin/
	cp $(RESTSRV_DIR)/conf/* $(DIST)/conf/
	cp $(RESTSRV_DIR)/project/build.properties $(DIST)/project/
	cp $(RESTSRV_DIR)/server/target/universal/stage/lib/* $(DIST)/lib/
	cp $(RESTSRV_DIR)/server/target/universal/stage/bin/server $(DIST)/bin/main
	## clean up default SSL files, SSL initialization has to be fixed
	#  we can not ship without jks which unfortunate.
	# rm -f $(DIST)/conf/harness.jks $(DIST)/conf/harness.pem
	echo $(VERSION) > $(DIST)/RELEASE

wheel:
	mkdir -p $(DIST)
	pip wheel --wheel-dir=$(DIST)/wheel $(PYTHON_SDK_DIR)

clean-dist:
	rm -rf $(DIST)
