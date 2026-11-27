package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** `pool.max` sizes the HikariCP pool behind every [[com.histmersion.db.Db]] DataSource. Pooling is not a
  * micro-optimisation here: without it every repo call opened a fresh TCP connection and did the whole startup
  * handshake.
  */
@movesTo(FeaturePlace.UtilDatabase)
final case class DbPoolConfig(max: DbPoolConfig.Max) derives ConfigReader

object DbPoolConfig:
  type Max = Max.T
  object Max extends Newtype[Int, Pure]:
    /** The most connections the read-only query pool holds. */
    private val QueryMost = 4

    extension (m: Max)
      /** The application pool's size: this, and never under one connection. */
      def application: Max = Max(math.max(1, m.value))

      /** The read-only query pool's (`POST /api/query`): the application pool's, at most 4. */
      def query: Max = Max(math.min(QueryMost, m.application.value))

      /** The connections the server's pools hold at most: two of each while a settings reload swaps them (the old ones
        * close once idle), each application pool beside its query pool.
        */
      def peak: Int = 2 * (m.application.value + m.query.value)

    /** The largest pool whose [[peak]] fits in `room` connections, when even one connection does. */
    def most(room: Int): Option[Max] =
      val whole = (room - 2 * QueryMost) / 2
      (if whole >= QueryMost then Some(whole) else Some(math.min(QueryMost - 1, room / 4)).filter(_ >= 1)).map(Max(_))
