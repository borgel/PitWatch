# PitWatch Android: build, test and install the (sideloaded) debug app.
#
#   make build                      # debug APK against the real TBA / FRC Nexus APIs
#   make install DEVICE=<serial>    # build, install and launch on one device (see `make devices`)
#   make test                       # JVM unit tests (:core + :app)
#
# Installs use `adb install` on a single device, never Gradle's installDebug (that one installs on
# every connected device). With one device attached DEVICE may be left out; ANDROID_SERIAL works too.

GRADLE  := cd android && ./gradlew
ADB     ?= adb
DEVICE  ?= $(ANDROID_SERIAL)
APK     := android/app/build/outputs/apk/debug/app-debug.apk
PACKAGE := com.pitwatch.app

# The local fake event (scripts/fake-api.py), as seen from an emulator (10.0.2.2 = this machine).
FAKE_PORT  ?= 8765
FAKE_HOST  ?= 10.0.2.2
FAKE_PROPS := -Ppitwatch.tbaBaseUrl=http://$(FAKE_HOST):$(FAKE_PORT)/api/v3 -Ppitwatch.nexusBaseUrl=http://$(FAKE_HOST):$(FAKE_PORT)/api/v1
FAKE_SNAPSHOTS := scripts/fixtures/2026cancmp/2026-04-10T22-05-28Z scripts/fixtures/2026cancmp/2026-04-11T00-51-22Z

ADB_TARGET = $(ADB) $(if $(DEVICE),-s $(DEVICE))

.DEFAULT_GOAL := help
.PHONY: help build test install run uninstall devices fake-build fake-install fake-api clean check-device

help: ## List the targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  \033[1m%-13s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Pick a device with DEVICE=<serial> (from 'make devices') when more than one is attached."

build: ## Build the debug APK (real APIs)
	$(GRADLE) :app:assembleDebug
	@echo "APK: $(APK)"

test: ## Run the JVM unit tests
	$(GRADLE) :core:test :app:testDebugUnitTest

install: build check-device ## Build, install and launch on DEVICE
	$(ADB_TARGET) install -r $(APK)
	@$(MAKE) --no-print-directory run

run: check-device ## Launch the installed app on DEVICE
	$(ADB_TARGET) shell am start -n $(PACKAGE)/.MainActivity

uninstall: check-device ## Remove the app (and its data) from DEVICE
	$(ADB_TARGET) uninstall $(PACKAGE)

devices: ## List attached devices and emulators
	$(ADB) devices -l

fake-build: ## Build the debug APK against the local fake API (emulator only)
	$(GRADLE) :app:assembleDebug $(FAKE_PROPS)
	@echo "APK (fake API at $(FAKE_HOST):$(FAKE_PORT)): $(APK)"

fake-install: fake-build check-device ## Fake-API build, install and launch on DEVICE
	$(ADB_TARGET) install -r $(APK)
	@$(MAKE) --no-print-directory run

fake-api: ## Serve the captured event, shifted to now (Ctrl-C to stop)
	python3 scripts/fake-api.py --port $(FAKE_PORT) --switch-after 120 $(FAKE_SNAPSHOTS)

clean: ## Remove build outputs
	$(GRADLE) clean

# Refuses to guess when several devices are attached and none was picked.
check-device:
	@if [ -z "$(DEVICE)" ]; then \
		count=$$($(ADB) devices | awk 'NR > 1 && $$2 == "device"' | wc -l | tr -d ' '); \
		if [ "$$count" = "0" ]; then echo "No device attached (make devices)." >&2; exit 1; fi; \
		if [ "$$count" != "1" ]; then \
			echo "$$count devices attached; pick one with DEVICE=<serial>:" >&2; \
			$(ADB) devices -l | awk 'NR > 1 && NF' >&2; exit 1; \
		fi; \
	fi
