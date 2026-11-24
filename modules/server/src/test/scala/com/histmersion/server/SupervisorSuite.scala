package com.histmersion
package server

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Path
import com.histmersion.util.database.InstanceLock
import com.histmersion.config.BackupsConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.UserPaths
import com.histmersion.config.PgTuning
import com.histmersion.config.ServerConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.InitConfig
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SupervisorSuite extends munit.FunSuite:

  // independent of the environment the suite runs in (CI sets HISTMERSION_PG_BIN_DIR, HISTMERSION_BIND, …)
  private val base =
    val c = AppConfig.load()
    c.modify(_.backups)
      .setTo(
        c.backups.modify(_.dir).setTo(BackupsConfig.Dir("backups")).modify(_.pgBinDir).setTo(BackupsConfig.PgBinDir(""))
      )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def rc(home: String = "", data: String = "") = base.runtime
    .modify(_.enabled)
    .setTo(RuntimeConfig.Enabled(true))
    .modify(_.home)
    .setTo(RuntimeConfig.Home(home))
    .modify(_.dataDir)
    .setTo(RuntimeConfig.DataDir(data))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def env(m: (String, String)*): UserPaths.Lookup =
    m.map((k, v) => Environment.VariablesKey(k) -> Environment.VariablesValue(v)).toMap.get

  test("home defaults to the directory above the executable's bin/"):
    val l = Supervisor.layout(
      rc(),
      Some(Path.of("/opt/histmersion/bin/histmersion-server")),
      env("HOME" -> "/Users/u"),
      UserPaths.OsName("Mac OS X")
    )
    assertEquals(l.map(_.home), Right(Path.of("/opt/histmersion")))
    assertEquals(l.map(_.pgBin), Right(Path.of("/opt/histmersion/pg/bin")))
    assertEquals(l.map(_.python), Right(Path.of("/opt/histmersion/converter/python/bin/python3")))

  test("data defaults per platform: Application Support on macOS, XDG on Linux"):
    val exe = Some(Path.of("/b/bin/x"))
    assertEquals(
      Supervisor.layout(rc(), exe, env("HOME" -> "/Users/u"), UserPaths.OsName("Mac OS X")).map(_.data),
      Right(Path.of("/Users/u/Library/Application Support/Histmersion"))
    )
    assertEquals(
      Supervisor
        .layout(rc(), exe, env("HOME" -> "/home/u", "XDG_DATA_HOME" -> "/xdg"), UserPaths.OsName("Linux"))
        .map(_.data),
      Right(Path.of("/xdg/histmersion"))
    )
    assertEquals(
      Supervisor.layout(rc(), exe, env("HOME" -> "/home/u"), UserPaths.OsName("Linux")).map(_.data),
      Right(Path.of("/home/u/.local/share/histmersion"))
    )

  test("explicit home and data dir win; a missing HOME is an actionable error"):
    val l = Supervisor.layout(rc("/h", "/d"), None, env(), UserPaths.OsName("Linux"))
    assertEquals(l.map(x => (x.home, x.cluster, x.logs)), Right((Path.of("/h"), Path.of("/d/pg"), Path.of("/d/logs"))))
    assert(
      Supervisor
        .layout(rc(), Some(Path.of("/b/bin/x")), env(), UserPaths.OsName("Linux"))
        .left
        .exists(_.sentence.contains("HISTMERSION_DATA_DIR"))
    )

  test("postgres listens on loopback TCP only — no Unix socket (macOS caps its path at 103 bytes)"):
    val o = Supervisor.pgOptions(RuntimeConfig.PgPort(5433)).value
    assert(
      o.contains("-p 5433") && o.contains("listen_addresses=127.0.0.1") && o.contains("unix_socket_directories=''"),
      o
    )

  test("postgres starts with memory sized from the machine's RAM, or as configured, and its connections"):
    val auto = PgTuning(
      PgSize.Auto,
      PgSize.Auto,
      PgSize.Exactly(PgBytes(16777216L)), // 16 MB
      PgSize.Exactly(PgBytes(67108864L)), // 64 MB
      PgTuning.Connections(100)
    )
    // the sizes the start before typed sizes passed on a 48 GB machine (2048MB, 24576MB, 16MB), each in its largest
    // whole unit, and initdb's own maintenance_work_mem and max_connections (64MB and 100: `postgres -C` on a fresh
    // cluster)
    assertEquals(
      Supervisor.pgSettings(auto, Some(ByteCount(51539607552L))),
      List(
        "shared_buffers" -> "2GB",
        "effective_cache_size" -> "24GB",
        "work_mem" -> "16MB",
        "maintenance_work_mem" -> "64MB",
        "max_connections" -> "100"
      )
    )
    // RAM/16, never under initdb's 128 MB
    assertEquals(Supervisor.pgSettings(auto, Some(ByteCount(8589934592L))).head, "shared_buffers" -> "512MB")
    assertEquals(Supervisor.pgSettings(auto, Some(ByteCount(1073741824L))).head, "shared_buffers" -> "128MB")
    // the RAM unknown: `auto` is left to Postgres, an explicit size still applies
    assertEquals(
      Supervisor.pgSettings(auto, None),
      List("work_mem" -> "16MB", "maintenance_work_mem" -> "64MB", "max_connections" -> "100")
    )
    assertEquals(
      Supervisor.pgSettings(
        PgTuning(
          PgSize.Exactly(PgBytes(1073741824L)),
          PgSize.Unset,
          PgSize.Unset,
          PgSize.Unset,
          PgTuning.Connections(40)
        ),
        None
      ),
      List("shared_buffers" -> "1GB", "max_connections" -> "40")
    )
    val o =
      Supervisor.pgOptions(RuntimeConfig.PgPort(5433), List("shared_buffers" -> "2048MB", "work_mem" -> "16MB")).value
    assert(o.endsWith(" -c shared_buffers=2048MB -c work_mem=16MB"), o)
    assertEquals(base.runtime.postgres, auto, "reference.conf: auto, auto, 16MB, 64MB, 100")

  test("a pool the bundled Postgres' connections cannot hold stops the start before anything starts, and a reload"):
    val crowded = base
      .modify(_.runtime)
      .setTo(rc("/nonexistent/home", "/nonexistent/data"))
      .modify(_.db)
      .setTo(base.db.modify(_.pool).setTo(DbPoolConfig(DbPoolConfig.Max(44))))
    val paths = UserPaths(Environment.empty, UserPaths.OsName("Linux"))
    val said =
      "histmersion.db.pool.max is 44, more than the 100 connections of histmersion.runtime.postgres.max-connections " +
        "leave room for: at most 43"
    // the check is the start's first step: no directory is made, no Postgres run
    val e = intercept[IllegalStateException](
      Supervisor.start(crowded, paths, None, InstanceLock.Holds(InstanceLock.Holder("histmersion-server test")))
    )
    assert(e.getMessage.startsWith(said), e.getMessage)
    assert(!java.nio.file.Files.exists(Path.of("/nonexistent")))
    // a reload's pool, against the connections the running Postgres was started with
    val source = Supervisor.reloadSource(base.modify(_.runtime).setTo(rc("/h", "/d")), () => fail("not read"), paths)
    assert(source.prepare(crowded).left.exists(_.sentence.startsWith(said)), s"${source.prepare(crowded)}")

  test("the config is pointed at the bundle; explicit user settings are kept"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    val c = Supervisor.configure(base.modify(_.runtime).setTo(rc()), l)
    assertEquals(c.db.url, s"jdbc:postgresql://127.0.0.1:${base.runtime.pgPort}/histmersion")
    assertEquals(c.converter.url, s"http://127.0.0.1:${base.runtime.converterPort}")
    assertEquals(c.backups.dir, "/d/backups")
    assertEquals(c.backups.pgBinDir, "/h/pg/bin")
    assertEquals(c.converter.spoolDir, "/d/uploads")
    // the addresses the server listens on are the settings': loopback by default, else the config file's or
    // HISTMERSION_BIND's list — the bundle no longer replaces them
    def bundled(env: Map[String, String], text: String): Vector[String] =
      val read = AppConfig
        .loadWithUserText(
          org.ekrich.config.ConfigFactory.parseMap(scala.jdk.CollectionConverters.MapHasAsJava(env).asJava),
          text
        )
        .fold(e => fail(s"$e"), _.config)
      Supervisor.configure(read.modify(_.runtime).setTo(rc()), l).server.host.toVector.map(h => s"$h")
    assertEquals(bundled(Map.empty, ""), Vector("127.0.0.1"), "a desktop install is private unless told otherwise")
    assertEquals(
      bundled(Map.empty, """histmersion.server.host = ["127.0.0.1", "100.101.102.103"]"""),
      Vector("127.0.0.1", "100.101.102.103")
    )
    assertEquals(
      bundled(Map("HISTMERSION_BIND" -> "127.0.0.1, 100.101.102.103"), ""),
      Vector("127.0.0.1", "100.101.102.103")
    )
    assertEquals(bundled(Map("HISTMERSION_BIND" -> "0.0.0.0"), ""), Vector("0.0.0.0"))
    val custom = base
      .modify(_.runtime)
      .setTo(rc())
      .modify(_.backups)
      .setTo(
        base.backups
          .modify(_.dir)
          .setTo(BackupsConfig.Dir("/mine"))
          .modify(_.pgBinDir)
          .setTo(BackupsConfig.PgBinDir("/opt/pg/bin"))
      )
    assertEquals(Supervisor.configure(custom, l).backups.dir, "/mine")
    assertEquals(Supervisor.configure(custom, l).backups.pgBinDir, "/opt/pg/bin")

  test("a bundled server builds bundles under <data>/bundles unless told otherwise"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    assertEquals(Supervisor.configure(base.modify(_.runtime).setTo(rc()), l).bundles.dir, "/d/bundles")
    val custom = base
      .modify(_.runtime)
      .setTo(rc())
      .modify(_.bundles)
      .setTo(base.bundles.modify(_.dir).setTo(BundlesConfig.Dir("/srv/bundles")))
    assertEquals(Supervisor.configure(custom, l).bundles.dir, "/srv/bundles")

  test("a bundled server reads its first-run data from the bundle's db/ unless told otherwise (#720)"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    assertEquals(Supervisor.configure(base.modify(_.runtime).setTo(rc()), l).init.dataDir, "/h/db")
    val custom = base
      .modify(_.runtime)
      .setTo(rc())
      .modify(_.init)
      .setTo(base.init.modify(_.dataDir).setTo(InitConfig.DataDir("/srv/seed")))
    assertEquals(Supervisor.configure(custom, l).init.dataDir, "/srv/seed")

  test("a server inside a bundle (pg/bin/pg_ctl beside bin/) enables the runtime by itself"):
    val home = java.nio.file.Files.createTempDirectory("histmersion-bundle")
    val pgBin = java.nio.file.Files.createDirectories(home.resolve("pg/bin"))
    val ctl = java.nio.file.Files.writeString(pgBin.resolve("pg_ctl"), "#!/bin/sh\n")
    assert(!Supervisor.bundled(Some(home.resolve("bin/histmersion-server"))))
    val _ = ctl.toFile.setExecutable(true)
    assert(Supervisor.bundled(Some(home.resolve("bin/histmersion-server"))))
    assert(!Supervisor.bundled(Some(java.nio.file.Path.of("/usr/local/bin/histmersion-server"))))
