# hist-db — thin aliases over install.sh, sbt and docker compose.
# Nothing here hardcodes a port: values come from .env (see .env.example) or the environment.

SHELL := /bin/bash
.DEFAULT_GOAL := help

# Load .env if present, so `make up`/`make health` see the same values compose does.
ifneq (,$(wildcard .env))
include .env
export
endif

HISTDB_PORT       ?= 8000
HISTDB_PUBLIC_URL ?= http://localhost:$(HISTDB_PORT)
HISTDB_BIN_DIR    ?= $(HOME)/bin

# sbt 2's thin client shares one server across invocations and drops the caller's environment;
# `--server -batch` forces a dedicated, env-carrying, non-interactive server per invocation.
SBT ?= sbt --server -batch

COMPOSE     ?= docker compose
COMPOSE_DEV ?= docker compose -f docker-compose.yml -f docker-compose.dev.yml

.PHONY: help install install-dry test native native-server native-cli \
        build up dev-up down restart logs ps health cli-docs docker-build clean

help: ## Show this help
	@echo "hist-db — make targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[1m%-16s\033[0m %s\n", $$1, $$2}'

install: ## Full install: CLI native image + Docker stack + .mcp.json (alias for ./install.sh)
	./install.sh

install-dry: ## Print what `make install` would do, change nothing
	./install.sh --dry-run

test: ## Run the test suite (set HISTDB_TEST_DB_URL for the DB/HTTP suites)
	$(SBT) test

native: native-cli native-server ## Build both GraalVM native images on the host

# sbt 2 writes module output to <root>/target/out/jvm/<scalaVersion>/<name>/, not modules/<m>/target/.
native-cli: ## Build the CLI native image (target/out/jvm/*/histdb-cli/histdb-cli)
	$(SBT) cli/nativeImage
	@find target modules -type f -name histdb-cli -perm -u+x 2>/dev/null | head -1

native-server: ## Build the server native image on the host (target/out/jvm/*/histdb-server/...)
	$(SBT) server/nativeImage
	@find target modules -type f -name histdb-server -perm -u+x 2>/dev/null | head -1

docker-build: ## Build the server container image (native-image inside Docker)
	$(COMPOSE) build server

build: ## Build all container images
	$(COMPOSE) build

up: ## Start the stack in the background
	$(COMPOSE) up -d

dev-up: ## Start Postgres + converter only, with Postgres published (for `sbt server/run`)
	$(COMPOSE_DEV) up -d postgres converter

down: ## Stop the stack (keeps the data volume)
	$(COMPOSE) down

restart: ## Recreate the server container with the current image
	$(COMPOSE) up -d --force-recreate server

logs: ## Follow the server logs
	$(COMPOSE) logs -f server

ps: ## Show stack status
	$(COMPOSE) ps

health: ## Curl the health endpoint
	@curl -fsS "$(HISTDB_PUBLIC_URL)/api/health" && echo

cli-docs: ## Regenerate the CLI reference table in README.md from `histdb --help`
	./tools/gen-cli-docs.sh

clean: ## Remove build output (sbt targets and native images)
	$(SBT) clean
	@find target modules -type f \( -name histdb-cli -o -name histdb-server \) -delete 2>/dev/null || true
