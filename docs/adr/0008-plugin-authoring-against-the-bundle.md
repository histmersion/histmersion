# 0008. Plugins are authored against the bundle's own Maven repository

- Status: accepted
- Date: 2026-11-21

## Context

A plugin author compiles against the plugin API and against the libraries the server provides, at the versions it
ships. The author should not have to name the install's path, and a plugin must not bundle what the server already
provides.

## Decision

- The bundle ships a Maven repository (`maven/`): the plugin API (jar, sources, POM) and the plugin platform's POM,
  which lists the libraries the server provides. A plugin depends on both compile-only.
- The installers copy the plugin API, platform and testkit into `~/.m2/repository` (only missing or changed files), so
  a plugin project finds them as `m2Local` without naming the install.
- `plugin-template-check.sh` builds every kind of plugin `histmersion plugin new` writes, as an author would: the
  bundle's CLI writes the project, `scala-cli test` builds and tests it, and `scala-cli --power publish` writes the jar
  and the POM. It fails when any step fails or the compiler warns (the JVM's own `WARNING` lines about
  `sun.misc.Unsafe` do not count). It also fails when the jar holds anything but the plugin's own classes, services
  file, defaults and native-image metadata, or when the POM lists the API, the platform or the testkit.
- It runs under a temporary `HOME`, which also proves the template names no path of the building machine; coursier's
  `m2Local` follows the JVM's `user.home`, not `HOME`, so scala-cli gets `-Duser.home` too, and `--power` must precede
  `-D` or scala-cli takes the subcommand's options for its own. Tests run from another directory than the project's, so
  recordings must be found by the project's path.

## Consequences

- A plugin built from the template resolves only from the bundle and the user's own repositories.
- The check needs scala-cli and a JDK 17+; the smoke test runs it when scala-cli is on PATH.

## Code

`scripts/bundle/server.sh`, `install-macos.sh`, `install-linux.sh`, `plugin-template-check.sh`, `smoke.sh`, the CLI's
`plugin new` (`PluginTemplate`).
