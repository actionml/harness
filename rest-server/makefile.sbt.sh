HARNESS_ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SBT_INSTALL ?= yes
SBT_URL ?= https://git.io/sbt

ifeq ($(SBT_INSTALL),yes)
SBT ?= $(HARNESS_ROOT)/sbt/sbt
endif

export HARNESS_ROOT
export SBT


.sbt: sbt
sbt:
ifeq ($(SBT_INSTALL),yes)
    @ SBT_DIR="$$(dirname $(SBT))" && mkdir -p $$SBT_DIR && cd $$SBT_DIR && \
    [ -x sbt ] || ( echo "Installing sbt extras locally (from $(SBT_URL))"; \
      which curl &> /dev/null && ( curl \-#SL -o sbt \
        https://git.io/sbt && chmod 0755 sbt || exit 1; ) || \
      ( which wget &>/dev/null && wget -O sbt https://git.io/sbt && chmod 0755 sbt; ) \
    )
endif
