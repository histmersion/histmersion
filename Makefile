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

# A THROWAWAY Postgres for the DB-backed suites — never the live corpus (see TestDb).
TEST_DB_NAME  ?= histdb-testdb
TEST_DB_PORT  ?= 55432
TEST_DB_IMAGE ?= $(shell sed -n 's/^ *image: *\(postgis\/postgis:[^ ]*\).*/\1/p' docker-compose.yml | head -1)
TEST_DB_URL   ?= jdbc:postgresql://127.0.0.1:$(TEST_DB_PORT)/histdb_test

.PHONY: help install install-dry test native native-server native-cli \
        build up dev-up down restart logs ps health cli-docs docker-build clean \
        test-db test-db-down test-it bundle

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

BUNDLE_DIR ?= dist/hist-db

bundle: ## Build the native bundle in dist/hist-db (server + CLI, PostgreSQL 18 + PostGIS, OCR converter)
	scripts/bundle/postgres.sh $(BUNDLE_DIR)
	scripts/bundle/converter.sh $(BUNDLE_DIR)
	scripts/bundle/server.sh $(BUNDLE_DIR)

test-db: ## Start a throwaway Postgres (histdb_test on :55432) for the DB-backed suites
	@docker rm -f $(TEST_DB_NAME) >/dev/null 2>&1 || true
	docker run -d --rm --name $(TEST_DB_NAME) -e POSTGRES_USER=histdb -e POSTGRES_PASSWORD=histdb \
	  -e POSTGRES_DB=histdb_test -p 127.0.0.1:$(TEST_DB_PORT):5432 $(TEST_DB_IMAGE) >/dev/null
	@for i in $$(seq 1 90); do \
	  docker logs $(TEST_DB_NAME) 2>&1 | grep -q 'PostgreSQL init process complete' && \
	  docker exec $(TEST_DB_NAME) pg_isready -U histdb -h 127.0.0.1 >/dev/null 2>&1 && exit 0; sleep 1; done; \
	  echo 'test database did not become ready' >&2; exit 1
	@echo "HISTDB_TEST_DB_URL=$(TEST_DB_URL)"

test-db-down: ## Remove the throwaway test Postgres
	docker rm -f $(TEST_DB_NAME) >/dev/null 2>&1 || true

test-it: test-db ## Every suite, DB-backed ones included, against the throwaway database
	HISTDB_TEST_DB_URL=$(TEST_DB_URL) sbt --server -batch 'testOnly *'; rc=$$?; $(MAKE) test-db-down; exit $$rc

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
