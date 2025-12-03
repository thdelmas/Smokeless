# Makefile for building and running PreuJust on connected devices

APP_ID := com.preujust/.MainActivity

# Device IDs
SAMSUNG_ID := 616ecbcf
PIXEL4A_ID := 0B201JECB13875
PIXEL9A_ID := 59101JEBF02652

# Default target
.PHONY: all
all: help

# === Android build & run ===

.PHONY: install
install: git-sync
	./gradlew installDebug

.PHONY: run-pixel4a
run-pixel4a: install
	adb -s $(PIXEL4A_ID) shell am start -n $(APP_ID)

.PHONY: run-pixel9a
run-pixel9a: install
	adb -s $(PIXEL9A_ID) shell am start -n $(APP_ID)

.PHONY: run-samsung
run-samsung: install
	adb -s $(SAMSUNG_ID) shell am start -n $(APP_ID)

.PHONY: devices
devices:
	adb devices

.PHONY: logs
logs:
	adb logcat --pid=$$(adb shell pidof com.preujust)

# === Git utilities ===

.PHONY: git-sync
git-sync:
	@if [ -z "$(BRANCH)" ]; then \
		echo "Usage: make git-sync BRANCH=<git-branch>";\
	else \
		git checkout main && \
		git pull && \
		git checkout $(BRANCH) && \
		git pull; \
	fi

# === Help ===

.PHONY: help
help:
	@echo "Available commands:"
	@echo "  make install             - Build and install the debug APK"
	@echo "  make run-pixel4a         - Install and run on Pixel 4a (ID: $(PIXEL4A_ID))"
	@echo "  make run-pixel9a         - Install and run on Pixel 9a (ID: $(PIXEL9A_ID))"
	@echo "  make run-samsung         - Install and run on Samsung (ID: $(SAMSUNG_ID))"
	@echo "  make devices             - List connected ADB devices"
	@echo "  make logs                - Show logs for the running app"
	@echo "  make git-sync BRANCH=x  - Checkout main, pull, then checkout and pull branch x"

