# Hex Casting deploy Makefile
#
# Target instance: christianmods_w_hexcasting (MC 1.21.1 / NeoForge 21.1.x)
# Builds the NeoForge jar from the `devel/port-1.21` branch and copies it
# — plus the two hard runtime dependencies (kotlin-for-forge, patchouli) —
# into the pack's mods directory.
#
# Usage:
#   make            # build + deploy
#   make build      # gradle build only
#   make deploy     # copy existing jar without rebuilding
#   make clean      # gradle clean
#   make run        # launch dev client (runClient)
#   make verify     # print target jar path, modpack dir, existing hex jars
#   make smoketest  # boot dedicated server with only hex jar, check for errors
#
# Override paths on the command line if needed, e.g.
#   make deploy INSTANCE=/d/otherpack

# mingw-make on Windows defaults to cmd.exe-style shells, which don't understand
# "./gradlew". Force bash (via env SHELL) for every recipe so the unix-style paths
# resolve. Override with `make SHELL=/bin/sh` if you prefer a POSIX shell.
SHELL := /bin/bash.exe

INSTANCE ?= E:/CurseForge/Install/Instances/christianmods_w_hexcasting
MODS_DIR ?= $(INSTANCE)/mods
JAR_DIR  := Neoforge/build/libs
GRADLEW  := sh ./gradlew

# Artifact pattern: archivesName = hexcasting-neoforge-<mc>-<ver>.jar.
# Excludes -dev, -sources, -shadow, -all variants produced by loom/shadow.
JAR_PATTERN := hexcasting-neoforge-*.jar

.PHONY: all build deploy clean run verify help smoketest smoketest-clean gametest

all: deploy

help:
	@echo "make build           -- gradle :Neoforge:build"
	@echo "make deploy          -- build then copy jar to $(MODS_DIR)"
	@echo "make clean           -- gradle clean"
	@echo "make run             -- :Neoforge:runClient (dev)"
	@echo "make verify          -- show resolved paths and existing hex jars in target"
	@echo "make smoketest       -- boot dedicated server with only hex jar, check for errors"
	@echo "make smoketest-clean -- wipe build/smoketest (forces fresh server install)"
	@echo "make gametest        -- run hex @GameTest assertions (feeble_mind attribute, staff use, amethyst cluster, recipe)"

build:
	$(GRADLEW) :Neoforge:build

clean:
	$(GRADLEW) clean

run:
	$(GRADLEW) :Neoforge:runClient

verify:
	@echo "JAR_DIR  = $(JAR_DIR)"
	@echo "MODS_DIR = $(MODS_DIR)"
	@echo "-- candidate build artifacts --"
	@ls -1 $(JAR_DIR)/$(JAR_PATTERN) 2>/dev/null || echo "  (none — run 'make build' first)"
	@echo "-- hexcasting jars currently in target --"
	@ls -1 "$(MODS_DIR)"/hexcasting-*.jar 2>/dev/null || echo "  (none)"

deploy: build
	@test -d "$(MODS_DIR)" || { echo "ERROR: target dir missing: $(MODS_DIR)"; exit 1; }
	@jar=$$(ls -1 $(JAR_DIR)/$(JAR_PATTERN) 2>/dev/null \
	        | grep -Ev -- '-(dev|sources|dev-shadow|shadow|all|javadoc)\.jar$$' \
	        | head -n1); \
	if [ -z "$$jar" ]; then \
	    echo "ERROR: no distributable jar in $(JAR_DIR)"; \
	    ls -1 $(JAR_DIR) 2>/dev/null; \
	    exit 1; \
	fi; \
	rm -f "$(MODS_DIR)"/hexcasting-*.jar; \
	cp "$$jar" "$(MODS_DIR)/"; \
	echo "Deployed $$(basename $$jar) -> $(MODS_DIR)"; \
	bash scripts/install_deps.sh "$(MODS_DIR)"

# Boot a dedicated server with only the hex jar, wait for "Done (", /stop,
# verify no mod-loading exceptions. Installs NeoForge server into
# build/smoketest/ on first run and reuses it thereafter.
smoketest: build
	@bash scripts/smoketest.sh

smoketest-clean:
	@rm -rf build/smoketest
	@echo "build/smoketest removed"

# Run the @GameTest assertions in HexGameTests. Each test spawns a mock
# player / block setup, asserts gameplay invariants (feeble_mind attribute
# registered, staff.use doesn't throw, amethyst cluster break survives,
# amethyst_pillar recipe matches), and the gradle run returns non-zero if
# any assertion fires.
gametest:
	@bash scripts/gametest.sh
