package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `pool.max` sizes the HikariCP pool behind every [[com.histmersion.db.Db]] DataSource. Pooling is not a
  * micro-optimisation here: without it every repo call opened a fresh TCP connection and did the whole startup
  * handshake.
  */
final case class DbPoolConfig(max: DbPoolConfig.Max) derives ConfigReader

object DbPoolConfig:
  type Max = Max.T
  object Max extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
