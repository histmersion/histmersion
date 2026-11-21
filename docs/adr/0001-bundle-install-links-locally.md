# 0001. The bundle links its server on the installing machine, with a prebuilt fallback

- Status: accepted
- Date: 2026-11-21

## Context

The server is a GraalVM native image. A user's own plugins must be linked into that image, and a binary built by a
different JDK's native-image fails at start-up, so a relink must use exactly the GraalVM version the bundle was built
with. Linking needs GraalVM, a C toolchain (Xcode command line tools or `cc`), about 16 GB of memory, disk and about two
minutes; not every machine has them. An install must never leave the user without a working server.

## Decision

- **The bundle carries a relink kit** (`kit/`): the server's Sloth-patched jars in class-path order, the one list of
  native-image arguments, `kit.json` and `SHA256SUMS`, and the scripts that use it. It also carries the licence and the
  third-party notices, and the first-run seed data (the gazetteer and the period presets) as data: never the scripts that
  regenerate it, and never the external extracts, only their pins.
- **The bundle is built by the exact pinned GraalVM CE**, the first version in `scripts/bundle/graalvm-pins.txt`. The
  kit records that version in `kit/graalvm-version` at build time.
- **An install links the server locally by default** (`install-build.sh`, mode `local`), from the kit and the plugins
  of the data directory. `--prebuilt` takes the bundle's binary as shipped; `--jvm` runs the kit on a JDK instead
  (JVM mode, `server-jvm.sh`).
- **Every failure falls back to the bundle's prebuilt binary**, which stays in place until a new binary has passed its
  own `--self-check`. Failures include too little memory, no C toolchain, too little disk, the GraalVM download or its
  checksum, native-image itself, or the self-check. The install goes on and says so. An install never ends without a
  server binary.
- **Nothing is linked twice.** A binary linked on a machine carries a build record (`bin/histmersion-server.build`):
  the mode, a fingerprint of the kit (its `SHA256SUMS`), a fingerprint of the plugin set (the jars, the POMs beside them,
  and the resolved dependency jars in `lib/`), the machine's id, and its origin. When the record matches, an install
  keeps that binary. This covers the bundle's own binary on the machine that built it, and the previous install's
  binary on a reinstall of the same bundle with the same plugins. The machine id is a hash of the hardware UUID (macOS)
  or the machine-id (Linux), so the record holds nothing personal. A record without a machine id never matches.
- **A relink is a sequence of steps that each can stop it**, and nothing in `bin/` changes until the new binary passed
  its self-check. The steps are: the lock, the kit's checksums, the toolchain, provenance (ADR 0003), the JVM
  pre-flight, Sloth, native-image, the self-check, signing, the swap and the restart. One relink runs at a time: the
  lock is a directory created atomically and holding its owner's pid, and a lock whose owner is gone is taken over. The
  pre-flight runs the server's own `--self-check` on the JVM with the user's config, so a config that does not decode
  stops the relink before the build, not after it. native-image runs in the work directory, because it leaves its
  reports (build artifacts, crash notes) in its working directory.
- **A binary's records travel with it.** On a swap the current binary becomes `.prev` together with its plugin-set
  record, its signed checksum and its build record. A record the previous binary did not have goes along too, and is
  never left beside a binary it does not describe. The bundle's own binary becomes `.prev` when an earlier install's
  binary is reused, so `histmersion plugin rollback` can still reach it.
- **Plugins' dependencies come from the POM beside each jar.** A jar without a POM has no dependencies of its own. They
  are resolved into `<data>/plugins/lib` by the kit's resolver. What the server already ships is not installed again,
  and a plugin that needs a newer version of a shipped library is refused (exit 2).
- **GraalVM is provisioned into the data directory**, not the install, because an install replaces the install
  directory wholesale. It is downloaded with curl (so macOS sets no quarantine attribute) from the official release and
  checked against the release's own checksum. GraalVM CE is GPLv2 with the Classpath Exception. `toolchain preflight`
  checks everything but GraalVM before an installer downloads its ~340 MB.
- **Finding a GraalVM home** tries, best first: the explicit one, the provisioned one, `GRAALVM_HOME`/`JAVA_HOME`, then
  sdkman's candidates. sdkman names a GraalVM CE build by its release (`25.5.5.0+3-graalce`), while the pins' URLs use
  its tag (`graal-25.5.5.0.3`), so each candidate is matched by its `release` file.
- **Memory** is the total on macOS, which has no trustworthy "available" figure, and `MemAvailable` on Linux.
- **The installers stop a running instance first**, because it holds the database cluster open. The previous install
  is moved aside and kept until the build step, so its binary can be reused.
- **JVM mode** finds plugins at each start, so adding one needs a restart instead of a relink. The launcher passes its
  own path as argv[0], so `pgrep -f <install>/bin/histmersion-server` finds the process in both modes.
- **Uninstalling keeps the data** (database, backups) unless `--purge` is given.

## Consequences

- A user with a capable machine gets their plugins in the native binary with no extra step; anyone else still gets a
  working server, and the installer says why.
- The kit, the build record and the fallbacks are tested against fakes by `scripts/bundle/tests/kit-scripts-test.sh`
  and `relink-test.sh`, and for real by `scripts/bundle/smoke.sh`.
- Using the total memory on macOS may let a relink start on a machine whose memory is mostly in use.

## Code

`scripts/bundle/install-build.sh`, `install-macos.sh`, `install-linux.sh`, `relink.sh`, `kit-common.sh`
(`hk_resolve`, `hk_pinned_version`, `hk_graalvm_candidates`, `hk_mem_bytes`, `hk_lock`, `hk_write_build_record`,
`hk_plugins_fingerprint`, `hk_machine_id`), `server.sh`, `server-jvm.sh`, `toolchain.sh`, `uninstall-macos.sh`,
`uninstall-linux.sh`, `tests/kit-scripts-test.sh`.
