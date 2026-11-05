package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings derivation. Defaults + env
  * overrides live in `reference.conf`; nothing is hardcoded elsewhere.
  */
/** `requestTimeoutSeconds` bounds one HTTP request end to end. Netty's default is 20 s, which is shorter than a
  * `pg_dump` of the live corpus (~40 s for 860 MB) — the request was answered 503 and the finished dump was never moved
  * into place. Long operations (backup, restore, seed, ingest of a large file) need this to be at least
  * `backups.timeout-seconds`. `shutdownGraceSeconds` is how long a stop waits for the open connections to close — netty
  * counts an idle keep-alive connection as open, so every stop with a client still connected waited the whole time; the
  * test suites set it to 0.
  */
/** `sourceUrl`: the repository the About window and `histmersion about` link as this program's source. */
final case class ServerConfig(
    host: String,
    port: Int,
    publicUrl: String,
    requestTimeoutSeconds: Int,
    shutdownGraceSeconds: Int,
    sourceUrl: String,
    restartExitCode: Int = 75
) derives ConfigReader
