# 0011. Test plugins prove one property each, with no network

- Status: accepted
- Date: 2026-11-21

## Context

The relink, dependency resolution, SPI checks and AI pipeline need real third-party plugin jars to test against: built
against plugin-api alone, packaged on their own, never on the server's class path.

## Decision

Each fixture under `modules/plugin-fixtures/` is a test fixture, not a product, and is built so that its registering
proves one property:

- **`hello`**, the smallest source-fetcher plugin. Its greeter is chosen by name from the config, so native-image cannot
  see the class statically. The reflective lookup works in a relinked binary only because the jar carries its own
  `META-INF/native-image/com.histmersion/plugin-hello/reachability-metadata.json`. A registered `hello` therefore proves
  that a plugin's own reachability metadata reaches the image.
- **`hello-deps`** depends on **`hello-lib`**, a library no Histmersion module depends on, and is published with a POM
  that lists it. It registers only when that library's class loads, so a registered `hello-deps` proves the dependency
  was resolved from the POM, installed and linked with it.
- **`ForeignSpiPlugin`** is built for a foreign SPI generation and is deliberately absent from the jar's services file;
  the tests write a jar whose services file names it, to see it refused before it is installed or linked.
- **`hello-ai`** is the smallest AI provider. It calls no model and opens no socket: its replies are scripted in its
  config, so the suites can run a real pipeline through the host's tool loop. Each turn answers with the first reply
  whose `when` occurs in the step's answer schema; a reply with a `tool` calls that tool once first, then answers; the
  model `limited` reports its plan's usage limit, so a chain can be seen to move on.

## Consequences

- The relink tests and `scripts/bundle/smoke.sh` can check those properties on a real binary without network access.

## Code

`modules/plugin-fixtures/hello/`, `hello-deps/`, `hello-lib/`, `hello-ai/`; `scripts/bundle/smoke.sh`.
