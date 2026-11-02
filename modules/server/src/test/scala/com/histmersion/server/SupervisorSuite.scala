package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Path

class SupervisorSuite extends munit.FunSuite:

  // independent of the environment the suite runs in (CI sets HISTMERSION_PG_BIN_DIR, HISTMERSION_BIND, …)
  private val base =
    val c = AppConfig.load()
    c.copy(backups = c.backups.copy(dir = "backups", pgBinDir = ""))
  private def rc(home: String = "", data: String = "") = base.runtime.copy(enabled = true, home = home, dataDir = data)
  private def env(m: (String, String)*): String => Option[String] = m.toMap.get

  test("home defaults to the directory above the executable's bin/"):
    val l = Supervisor.layout(
      rc(),
      Some(Path.of("/opt/histmersion/bin/histmersion-server")),
      env("HOME" -> "/Users/u"),
      "Mac OS X"
    )
    assertEquals(l.map(_.home), Right(Path.of("/opt/histmersion")))
    assertEquals(l.map(_.pgBin), Right(Path.of("/opt/histmersion/pg/bin")))
    assertEquals(l.map(_.python), Right(Path.of("/opt/histmersion/converter/python/bin/python3")))

  test("data defaults per platform: Application Support on macOS, XDG on Linux"):
    val exe = Some(Path.of("/b/bin/x"))
    assertEquals(
      Supervisor.layout(rc(), exe, env("HOME" -> "/Users/u"), "Mac OS X").map(_.data),
      Right(Path.of("/Users/u/Library/Application Support/Histmersion"))
    )
    assertEquals(
      Supervisor.layout(rc(), exe, env("HOME" -> "/home/u", "XDG_DATA_HOME" -> "/xdg"), "Linux").map(_.data),
      Right(Path.of("/xdg/histmersion"))
    )
    assertEquals(
      Supervisor.layout(rc(), exe, env("HOME" -> "/home/u"), "Linux").map(_.data),
      Right(Path.of("/home/u/.local/share/histmersion"))
    )

  test("explicit home and data dir win; a missing HOME is an actionable error"):
    val l = Supervisor.layout(rc("/h", "/d"), None, env(), "Linux")
    assertEquals(l.map(x => (x.home, x.cluster, x.logs)), Right((Path.of("/h"), Path.of("/d/pg"), Path.of("/d/logs"))))
    assert(
      Supervisor.layout(rc(), Some(Path.of("/b/bin/x")), env(), "Linux").left.exists(_.contains("HISTMERSION_DATA_DIR"))
    )

  test("postgres listens on loopback TCP only — no Unix socket (macOS caps its path at 103 bytes)"):
    val o = Supervisor.pgOptions(5433)
    assert(
      o.contains("-p 5433") && o.contains("listen_addresses=127.0.0.1") && o.contains("unix_socket_directories=''"),
      o
    )

  test("postgres starts with memory sized from the machine's RAM, or as configured"):
    val GB = 1024L * 1024L * 1024L
    val auto = PgTuning("auto", "auto", "16MB")
    assertEquals(
      Supervisor.pgSettings(auto, Some(48 * GB)),
      List("shared_buffers" -> "2048MB", "effective_cache_size" -> "24576MB", "work_mem" -> "16MB")
    )
    // RAM/16, never under initdb's 128 MB
    assertEquals(Supervisor.pgSettings(auto, Some(8 * GB)).head, "shared_buffers" -> "512MB")
    assertEquals(Supervisor.pgSettings(auto, Some(1 * GB)).head, "shared_buffers" -> "128MB")
    // the RAM unknown: `auto` is left to Postgres, an explicit size still applies
    assertEquals(Supervisor.pgSettings(auto, None), List("work_mem" -> "16MB"))
    assertEquals(Supervisor.pgSettings(PgTuning("1GB", "", ""), None), List("shared_buffers" -> "1GB"))
    // anything that is not a size never reaches the -o string
    assertEquals(
      Supervisor.pgSettings(PgTuning("1GB -c fsync=off", "lots", "4 MB"), Some(GB)),
      List("work_mem" -> "4MB")
    )
    val o = Supervisor.pgOptions(5433, List("shared_buffers" -> "2048MB", "work_mem" -> "16MB"))
    assert(o.endsWith(" -c shared_buffers=2048MB -c work_mem=16MB"), o)
    assertEquals(base.runtime.postgres, auto, "reference.conf: auto, auto, 16MB")

  test("the config is pointed at the bundle; explicit user settings are kept"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    val c = Supervisor.configure(base.copy(runtime = rc()), l)
    assertEquals(c.db.url, s"jdbc:postgresql://127.0.0.1:${base.runtime.pgPort}/histmersion")
    assertEquals(c.converter.url, s"http://127.0.0.1:${base.runtime.converterPort}")
    assertEquals(c.backups.dir, "/d/backups")
    assertEquals(c.backups.pgBinDir, "/h/pg/bin")
    assertEquals(c.converter.spoolDir, "/d/uploads")
    assertEquals(c.server.host, "127.0.0.1", "a desktop install is private unless HISTMERSION_BIND says otherwise")
    assertEquals(Supervisor.configure(base.copy(runtime = rc()), l, Some("0.0.0.0")).server.host, "0.0.0.0")
    val custom = base.copy(runtime = rc(), backups = base.backups.copy(dir = "/mine", pgBinDir = "/opt/pg/bin"))
    assertEquals(Supervisor.configure(custom, l).backups.dir, "/mine")
    assertEquals(Supervisor.configure(custom, l).backups.pgBinDir, "/opt/pg/bin")

  test("a bundled server builds bundles under <data>/bundles unless told otherwise"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    assertEquals(Supervisor.configure(base.copy(runtime = rc()), l).bundles.dir, "/d/bundles")
    val custom = base.copy(runtime = rc(), bundles = base.bundles.copy(dir = "/srv/bundles"))
    assertEquals(Supervisor.configure(custom, l).bundles.dir, "/srv/bundles")

  test("a bundled server reads its first-run data from the bundle's db/ unless told otherwise (#720)"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    assertEquals(Supervisor.configure(base.copy(runtime = rc()), l).init.dataDir, "/h/db")
    val custom = base.copy(runtime = rc(), init = base.init.copy(dataDir = "/srv/seed"))
    assertEquals(Supervisor.configure(custom, l).init.dataDir, "/srv/seed")

  test("a server inside a bundle (pg/bin/pg_ctl beside bin/) enables the runtime by itself"):
    val home = java.nio.file.Files.createTempDirectory("histmersion-bundle")
    val pgBin = java.nio.file.Files.createDirectories(home.resolve("pg/bin"))
    val ctl = java.nio.file.Files.writeString(pgBin.resolve("pg_ctl"), "#!/bin/sh\n")
    assert(!Supervisor.bundled(Some(home.resolve("bin/histmersion-server"))))
    val _ = ctl.toFile.setExecutable(true)
    assert(Supervisor.bundled(Some(home.resolve("bin/histmersion-server"))))
    assert(!Supervisor.bundled(Some(java.nio.file.Path.of("/usr/local/bin/histmersion-server"))))
