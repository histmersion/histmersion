package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Memory settings the bundled Postgres is started with (`-c`, so an existing cluster gets them on its next start;
  * nothing is written to its `postgresql.conf`). Each is a Postgres size (`"2GB"`, `"512MB"`), `"auto"` (sized from the
  * machine's RAM, see `Supervisor.pgSettings`) or `""` (Postgres' own default). initdb's defaults — 128 MB of shared
  * buffers, 4 MB per sort — are sized for a machine with no RAM to spare; this one holds a 15 GB gazetteer it searches
  * as you type.
  */
final case class PgTuning(sharedBuffers: String, effectiveCacheSize: String, workMem: String) derives ConfigReader
