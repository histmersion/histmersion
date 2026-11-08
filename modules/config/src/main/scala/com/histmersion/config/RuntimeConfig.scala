package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The native bundle: when `enabled`, the server itself runs the bundled PostgreSQL and OCR converter as child
  * processes (`com.histmersion.server.Supervisor`). `home` is the bundle root holding `pg/` and `converter/` (empty =
  * the directory above the running executable's `bin/`); `dataDir` holds the cluster, logs and backups (empty = the
  * platform's per-user data directory).
  */
final case class RuntimeConfig(
    enabled: RuntimeConfig.Enabled,
    home: RuntimeConfig.Home,
    dataDir: RuntimeConfig.DataDir,
    pgPort: RuntimeConfig.PgPort,
    converterPort: RuntimeConfig.ConverterPort,
    postgres: PgTuning
) derives ConfigReader

object RuntimeConfig:
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
  type Home = Home.T
  object Home extends RefinedType[String, Pure]
  type DataDir = DataDir.T
  object DataDir extends RefinedType[String, Pure]
  type PgPort = PgPort.T
  object PgPort extends RefinedType[Int, Pure]
  type ConverterPort = ConverterPort.T
  object ConverterPort extends RefinedType[Int, Pure]
