package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The native bundle: when `enabled`, the server itself runs the bundled PostgreSQL and OCR converter as child
  * processes (`com.histmersion.server.Supervisor`). `home` is the bundle root holding `pg/` and `converter/` (empty =
  * the directory above the running executable's `bin/`); `dataDir` holds the cluster, logs and backups (empty = the
  * platform's per-user data directory).
  */
final case class RuntimeConfig(
    enabled: Boolean,
    home: String,
    dataDir: String,
    pgPort: Int,
    converterPort: Int,
    postgres: PgTuning
) derives ConfigReader
