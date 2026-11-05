package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries are looked up on
  * PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are installed off PATH (homebrew's libpq
  * is keg-only).
  */
/** @param keep how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all. */
final case class BackupsConfig(dir: String, pgBinDir: String, timeoutSeconds: Int, requestWaitSeconds: Int, keep: Int)
    derives ConfigReader
