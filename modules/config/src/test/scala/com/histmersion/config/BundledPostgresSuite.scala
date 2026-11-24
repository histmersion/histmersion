package com.histmersion
package config

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.config.testsupport.ComparedByValue.given
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*
import com.histmersion.lint.{LintRule, tolerated}

/** The bundled Postgres' settings (`runtime.postgres`): sizes ([[PgSize]], [[PgBytes]]), what `auto` comes to
  * ([[PgParameter]]), the defaults, the refusal of what is no size, and the pool against the connections
  * ([[PgTuning.admits]]).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class BundledPostgresSuite extends munit.FunSuite:

  /** A size as the settings write it, read and written back as Postgres reads it. */
  private def shown(s: Option[PgSize]): Option[PgSize.Written] = s.collect { case PgSize.Exactly(b) => b.written }

  private def load(text: String, env: Map[String, String] = Map.empty) =
    AppConfig.loadWithUserText(ConfigFactory.parseMap(env.asJava), text).map(_.config)

  private def tuning(connections: PgTuning.Connections) =
    PgTuning(PgSize.Auto, PgSize.Auto, PgSize.Unset, PgSize.Unset, connections)

  private def most(refused: Either[Problem, Unit]): Option[Problem.Count] = refused.left.toOption.collect {
    case Problem.Config.Setting(_, f: Problem.Config.Fault.PoolOverConnections) => f.most
  }

  test("a size is a whole number of a unit Postgres reads, auto, or empty; a space between number and unit is fine"):
    assertEquals(PgSize.Written("2GB").size, Some(PgSize.Exactly(PgBytes(2147483648L))))
    assertEquals(PgSize.Written("512MB").size, Some(PgSize.Exactly(PgBytes(536870912L))))
    assertEquals(PgSize.Written("64kB").size, Some(PgSize.Exactly(PgBytes(65536L))))
    assertEquals(PgSize.Written("1TB").size, Some(PgSize.Exactly(PgBytes(1099511627776L))))
    assertEquals(PgSize.Written(" 4 MB ").size, Some(PgSize.Exactly(PgBytes(4194304L))))
    assertEquals(PgSize.Written("auto").size, Some(PgSize.Auto))
    assertEquals(PgSize.Written("AUTO").size, Some(PgSize.Auto))
    assertEquals(PgSize.Written("").size, Some(PgSize.Unset))
    assertEquals(PgSize.Written("  ").size, Some(PgSize.Unset))

  test("anything else is no size: no unit, a unit Postgres does not read, a fraction, nothing, too much, words"):
    List(
      PgSize.Written("512"),
      PgSize.Written("2 GiB"),
      PgSize.Written("2gb"),
      PgSize.Written("1.5GB"),
      PgSize.Written("0MB"),
      PgSize.Written("-1MB"),
      PgSize.Written("99999999999TB"),
      PgSize.Written("lots"),
      PgSize.Written("1GB -c fsync=off"),
      PgSize.Written("1GB;")
    ).foreach(raw => assertEquals(raw.size, None, s"$raw"))

  test("a size is written in the largest unit that holds it whole, as Postgres reads it"):
    assertEquals(PgBytes(2147483648L).written, PgSize.Written("2GB"))
    assertEquals(PgBytes(1610612736L).written, PgSize.Written("1536MB"))
    assertEquals(PgBytes(65536L).written, PgSize.Written("64kB"))
    assertEquals(PgBytes(3298534883328L).written, PgSize.Written("3TB"))
    // what the settings write reads back as the same words
    assertEquals(shown(PgSize.Written("2GB").size), Some(PgSize.Written("2GB")))
    assertEquals(shown(PgSize.Written("1536MB").size), Some(PgSize.Written("1536MB")))
    assertEquals(shown(PgSize.Written("64kB").size), Some(PgSize.Written("64kB")))
    // and the same size in another unit is written in the largest one
    assertEquals(shown(PgSize.Written("2048MB").size), Some(PgSize.Written("2GB")))

  test("auto: shared buffers RAM/16 within 128MB…2GB, cache RAM/2 from 512MB, work 16MB, maintenance RAM/32 64MB…1GB"):
    val ram48 = ByteCount(51539607552L)
    val ram8 = ByteCount(8589934592L)
    val ram1 = ByteCount(1073741824L)
    assertEquals(PgParameter.SharedBuffers.auto(ram48).written, PgSize.Written("2GB"))
    assertEquals(PgParameter.SharedBuffers.auto(ram8).written, PgSize.Written("512MB"))
    assertEquals(PgParameter.SharedBuffers.auto(ram1).written, PgSize.Written("128MB"))
    assertEquals(PgParameter.EffectiveCacheSize.auto(ram48).written, PgSize.Written("24GB"))
    assertEquals(PgParameter.EffectiveCacheSize.auto(ByteCount(536870912L)).written, PgSize.Written("512MB"))
    assertEquals(PgParameter.WorkMem.auto(ram48).written, PgSize.Written("16MB"))
    assertEquals(PgParameter.WorkMem.auto(ram1).written, PgSize.Written("16MB"))
    assertEquals(PgParameter.MaintenanceWorkMem.auto(ram48).written, PgSize.Written("1GB"))
    assertEquals(PgParameter.MaintenanceWorkMem.auto(ram8).written, PgSize.Written("256MB"))
    assertEquals(PgParameter.MaintenanceWorkMem.auto(ram1).written, PgSize.Written("64MB"))
    // whole megabytes, as auto always rounded: 48 GB and one byte is still 24 GB of cache
    assertEquals(PgParameter.EffectiveCacheSize.auto(ByteCount(51539607553L)).written, PgSize.Written("24GB"))

  test("each parameter's name to Postgres and its setting"):
    assertEquals(
      PgParameter.values.toList.map(p => (p.name, s"${p.key}")),
      List(
        ("shared_buffers", "histmersion.runtime.postgres.shared-buffers"),
        ("effective_cache_size", "histmersion.runtime.postgres.effective-cache-size"),
        ("work_mem", "histmersion.runtime.postgres.work-mem"),
        ("maintenance_work_mem", "histmersion.runtime.postgres.maintenance-work-mem")
      )
    )

  test("the defaults are today's: auto, auto, 16MB, and Postgres' own 64MB and 100 connections"):
    val t = load("").fold(p => fail(s"$p"), _.runtime.postgres)
    assertEquals(
      t,
      PgTuning(
        PgSize.Auto,
        PgSize.Auto,
        PgSize.Exactly(PgBytes(16777216L)),
        PgSize.Exactly(PgBytes(67108864L)),
        PgTuning.Connections(100)
      )
    )
    // what is set to auto, and what it comes to: nothing while the memory is unknown
    assertEquals(t.automatic(None), Nil)
    assertEquals(
      t.automatic(Some(ByteCount(51539607552L))).map((p, b) => p -> b.written),
      List(PgParameter.SharedBuffers -> PgSize.Written("2GB"), PgParameter.EffectiveCacheSize -> PgSize.Written("24GB"))
    )

  test("every size is settable in the file and by the environment, max-connections too"):
    val t = load(
      """histmersion.runtime.postgres { shared-buffers = "1GB", effective-cache-size = "", maintenance-work-mem = auto,
        |  max-connections = 40 }""".stripMargin,
      Map("HISTMERSION_PG_WORK_MEM" -> "8MB", "HISTMERSION_PG_MAX_CONNECTIONS" -> "60")
    ).fold(p => fail(s"$p"), _.runtime.postgres)
    assertEquals(
      t,
      PgTuning(
        PgSize.Exactly(PgBytes(1073741824L)),
        PgSize.Unset,
        PgSize.Exactly(PgBytes(8388608L)),
        PgSize.Auto,
        PgTuning.Connections(60)
      )
    )

  test("a value that is no size stops the settings from loading, each one said of its key"):
    val bad = load("""histmersion.runtime.postgres { shared-buffers = "2 GiB", work-mem = "4096" }""")
    assertEquals(
      bad.swap.map(_.toVector),
      Right(
        Vector(
          Problem.Config.Setting(
            Problem.SettingKey("histmersion.runtime.postgres.shared-buffers"),
            Problem.Config.Fault.NotSize(Problem.Raw("2 GiB"))
          ),
          Problem.Config.Setting(
            Problem.SettingKey("histmersion.runtime.postgres.work-mem"),
            Problem.Config.Fault.NotSize(Problem.Raw("4096"))
          )
        )
      )
    )
    assertEquals(
      bad.swap.toOption.map(_.toVector.head.sentence),
      Some(
        "histmersion.runtime.postgres.shared-buffers is not a size: '2 GiB' (a whole number of kB, MB, GB or TB, such " +
          "as 512MB or 2GB; auto; or \"\" for Postgres' own default)"
      )
    )
    // the environment's value too
    assert(load("", Map("HISTMERSION_PG_MAINTENANCE_WORK_MEM" -> "lots")).isLeft)
    // and a number of connections Postgres does not take
    assert(load("histmersion.runtime.postgres.max-connections = 0").isLeft)
    assert(load("histmersion.runtime.postgres.max-connections = 262144").isLeft)

  test("the pool fits when twice the application and query pools and 5 more fit in max-connections"):
    // the defaults: 2 × (8 + 4) + 5 = 29 of 100
    assertEquals(tuning(PgTuning.Connections(100)).admits(DbPoolConfig(DbPoolConfig.Max(8))), Right(()))
    // 2 × (43 + 4) + 5 = 99 fits, 44 does not
    assertEquals(tuning(PgTuning.Connections(100)).admits(DbPoolConfig(DbPoolConfig.Max(43))), Right(()))
    val refused = tuning(PgTuning.Connections(100)).admits(DbPoolConfig(DbPoolConfig.Max(44)))
    assertEquals(
      refused,
      Left(
        Problem.Config.Setting(
          Problem.SettingKey("histmersion.db.pool.max"),
          Problem.Config.Fault.PoolOverConnections(
            Problem.Count(44L),
            Problem.SettingKey("histmersion.runtime.postgres.max-connections"),
            Problem.Count(100L),
            Problem.Count(43L),
            Problem.Count(5L)
          )
        )
      )
    )
    assertEquals(
      refused.left.map(_.sentence),
      Left(
        "histmersion.db.pool.max is 44, more than the 100 connections of histmersion.runtime.postgres.max-connections " +
          "leave room for: at most 43 (a settings reload holds the old pools beside the new ones, each beside its " +
          "read-only query pool, and 5 stay free for backups and maintenance) — lower it, or raise the connections " +
          "and restart"
      )
    )
    // a pool under the query pool's 4 has a query pool as small: 4 × 3 + 5 = 17
    assertEquals(tuning(PgTuning.Connections(17)).admits(DbPoolConfig(DbPoolConfig.Max(3))), Right(()))
    assertEquals(
      most(tuning(PgTuning.Connections(16)).admits(DbPoolConfig(DbPoolConfig.Max(3)))),
      Some(Problem.Count(2L))
    )
    // too few connections for any pool at all
    assertEquals(
      most(tuning(PgTuning.Connections(8)).admits(DbPoolConfig(DbPoolConfig.Max(1)))),
      Some(Problem.Count(0L))
    )
    // a pool of 0 is the pool of 1 the database layer opens
    assertEquals(tuning(PgTuning.Connections(9)).admits(DbPoolConfig(DbPoolConfig.Max(0))), Right(()))

  test("the most a pool may be is the largest whose peak fits"):
    // every pool size up to 346 is the most for some room up to 700
    val pools = (0 to 700).flatMap(DbPoolConfig.Max.most).distinct
    assertEquals(pools.size, 346)
    (0 to 300).foreach { room =>
      val fitting = pools.filter(_.peak <= room)
      assertEquals(DbPoolConfig.Max.most(room), fitting.maxOption, s"room $room")
    }
