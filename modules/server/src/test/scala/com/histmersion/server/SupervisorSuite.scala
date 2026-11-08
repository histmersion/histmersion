package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Path
import com.histmersion.config.BackupsConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.UserPaths
import com.histmersion.config.PgTuning
import com.histmersion.config.ServerConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.InitConfig

class SupervisorSuite extends munit.FunSuite:

  // independent of the environment the suite runs in (CI sets HISTMERSION_PG_BIN_DIR, HISTMERSION_BIND, …)
  private val base =
    val c = AppConfig.load()
    c.modify(_.backups)
      .setTo(
        c.backups.modify(_.dir).setTo(BackupsConfig.Dir("backups")).modify(_.pgBinDir).setTo(BackupsConfig.PgBinDir(""))
      )
  private def rc(home: String = "", data: String = "") = base.runtime
    .modify(_.enabled)
    .setTo(RuntimeConfig.Enabled(true))
    .modify(_.home)
    .setTo(RuntimeConfig.Home(home))
    .modify(_.dataDir)
    .setTo(RuntimeConfig.DataDir(data))
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
        .exists(_.value.contains("HISTMERSION_DATA_DIR"))
    )

  test("postgres listens on loopback TCP only — no Unix socket (macOS caps its path at 103 bytes)"):
    val o = Supervisor.pgOptions(RuntimeConfig.PgPort(5433)).value
    assert(
      o.contains("-p 5433") && o.contains("listen_addresses=127.0.0.1") && o.contains("unix_socket_directories=''"),
      o
    )

  test("postgres starts with memory sized from the machine's RAM, or as configured"):
    val GB = 1024L * 1024L * 1024L
    val auto = PgTuning(PgTuning.SharedBuffers("auto"), PgTuning.EffectiveCacheSize("auto"), PgTuning.WorkMem("16MB"))
    assertEquals(
      Supervisor.pgSettings(auto, Some(ByteCount.applyUnsafe(48 * GB))),
      List("shared_buffers" -> "2048MB", "effective_cache_size" -> "24576MB", "work_mem" -> "16MB")
    )
    // RAM/16, never under initdb's 128 MB
    assertEquals(Supervisor.pgSettings(auto, Some(ByteCount.applyUnsafe(8 * GB))).head, "shared_buffers" -> "512MB")
    assertEquals(Supervisor.pgSettings(auto, Some(ByteCount.applyUnsafe(1 * GB))).head, "shared_buffers" -> "128MB")
    // the RAM unknown: `auto` is left to Postgres, an explicit size still applies
    assertEquals(Supervisor.pgSettings(auto, None), List("work_mem" -> "16MB"))
    assertEquals(
      Supervisor.pgSettings(
        PgTuning(PgTuning.SharedBuffers("1GB"), PgTuning.EffectiveCacheSize(""), PgTuning.WorkMem("")),
        None
      ),
      List("shared_buffers" -> "1GB")
    )
    // anything that is not a size never reaches the -o string
    assertEquals(
      Supervisor.pgSettings(
        PgTuning(
          PgTuning.SharedBuffers("1GB -c fsync=off"),
          PgTuning.EffectiveCacheSize("lots"),
          PgTuning.WorkMem("4 MB")
        ),
        Some(ByteCount.applyUnsafe(GB))
      ),
      List("work_mem" -> "4MB")
    )
    val o =
      Supervisor.pgOptions(RuntimeConfig.PgPort(5433), List("shared_buffers" -> "2048MB", "work_mem" -> "16MB")).value
    assert(o.endsWith(" -c shared_buffers=2048MB -c work_mem=16MB"), o)
    assertEquals(base.runtime.postgres, auto, "reference.conf: auto, auto, 16MB")

  test("the config is pointed at the bundle; explicit user settings are kept"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    val c = Supervisor.configure(base.modify(_.runtime).setTo(rc()), l)
    assertEquals(c.db.url, s"jdbc:postgresql://127.0.0.1:${base.runtime.pgPort}/histmersion")
    assertEquals(c.converter.url, s"http://127.0.0.1:${base.runtime.converterPort}")
    assertEquals(c.backups.dir, "/d/backups")
    assertEquals(c.backups.pgBinDir, "/h/pg/bin")
    assertEquals(c.converter.spoolDir, "/d/uploads")
    assertEquals(c.server.host, "127.0.0.1", "a desktop install is private unless HISTMERSION_BIND says otherwise")
    assertEquals(
      Supervisor.configure(base.modify(_.runtime).setTo(rc()), l, Some(ServerConfig.Host("0.0.0.0"))).server.host,
      "0.0.0.0"
    )
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
