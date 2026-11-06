package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Home = Home.T
  object Home extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataDir = DataDir.T
  object DataDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PgPort = PgPort.T
  object PgPort extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConverterPort = ConverterPort.T
  object ConverterPort extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
