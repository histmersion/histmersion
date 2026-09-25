package histdb.server

import histdb.core.*

import java.nio.file.Path

class SupervisorSuite extends munit.FunSuite:

  private val base = AppConfig.load()
  private def rc(home: String = "", data: String = "") = base.runtime.copy(enabled = true, home = home, dataDir = data)
  private def env(m: (String, String)*): String => Option[String] = m.toMap.get

  test("home defaults to the directory above the executable's bin/"):
    val l = Supervisor.layout(rc(), Some(Path.of("/opt/hist-db/bin/histdb-server")), env("HOME" -> "/Users/u"), "Mac OS X")
    assertEquals(l.map(_.home), Right(Path.of("/opt/hist-db")))
    assertEquals(l.map(_.pgBin), Right(Path.of("/opt/hist-db/pg/bin")))
    assertEquals(l.map(_.python), Right(Path.of("/opt/hist-db/converter/python/bin/python3")))

  test("data defaults per platform: Application Support on macOS, XDG on Linux"):
    val exe = Some(Path.of("/b/bin/x"))
    assertEquals(Supervisor.layout(rc(), exe, env("HOME" -> "/Users/u"), "Mac OS X").map(_.data),
      Right(Path.of("/Users/u/Library/Application Support/hist-db")))
    assertEquals(Supervisor.layout(rc(), exe, env("HOME" -> "/home/u", "XDG_DATA_HOME" -> "/xdg"), "Linux").map(_.data),
      Right(Path.of("/xdg/hist-db")))
    assertEquals(Supervisor.layout(rc(), exe, env("HOME" -> "/home/u"), "Linux").map(_.data),
      Right(Path.of("/home/u/.local/share/hist-db")))

  test("explicit home and data dir win; a missing HOME is an actionable error"):
    val l = Supervisor.layout(rc("/h", "/d"), None, env(), "Linux")
    assertEquals(l.map(x => (x.home, x.cluster, x.logs)), Right((Path.of("/h"), Path.of("/d/pg"), Path.of("/d/logs"))))
    assert(Supervisor.layout(rc(), Some(Path.of("/b/bin/x")), env(), "Linux").left.exists(_.contains("HISTDB_DATA_DIR")))

  test("postgres listens on loopback TCP only — no Unix socket (macOS caps its path at 103 bytes)"):
    val o = Supervisor.pgOptions(5433)
    assert(o.contains("-p 5433") && o.contains("listen_addresses=127.0.0.1") && o.contains("unix_socket_directories=''"), o)

  test("the config is pointed at the bundle; explicit user settings are kept"):
    val l = Supervisor.Layout(Path.of("/h"), Path.of("/d"))
    val c = Supervisor.configure(base.copy(runtime = rc()), l)
    assertEquals(c.db.url, s"jdbc:postgresql://127.0.0.1:${base.runtime.pgPort}/histdb")
    assertEquals(c.converter.url, s"http://127.0.0.1:${base.runtime.converterPort}")
    assertEquals(c.backups.dir, "/d/backups")
    assertEquals(c.backups.pgBinDir, "/h/pg/bin")
    assertEquals(c.converter.spoolDir, "/d/uploads")
    assertEquals(c.server.host, "127.0.0.1", "a desktop install is private unless HISTDB_BIND says otherwise")
    assertEquals(Supervisor.configure(base.copy(runtime = rc()), l, Some("0.0.0.0")).server.host, "0.0.0.0")
    val custom = base.copy(runtime = rc(), backups = base.backups.copy(dir = "/mine", pgBinDir = "/opt/pg/bin"))
    assertEquals(Supervisor.configure(custom, l).backups.dir, "/mine")
    assertEquals(Supervisor.configure(custom, l).backups.pgBinDir, "/opt/pg/bin")

  test("a server inside a bundle (pg/bin/pg_ctl beside bin/) enables the runtime by itself"):
    val home = java.nio.file.Files.createTempDirectory("histdb-bundle")
    val pgBin = java.nio.file.Files.createDirectories(home.resolve("pg/bin"))
    val ctl = java.nio.file.Files.writeString(pgBin.resolve("pg_ctl"), "#!/bin/sh\n")
    assert(!Supervisor.bundled(Some(home.resolve("bin/histdb-server"))))
    val _ = ctl.toFile.setExecutable(true)
    assert(Supervisor.bundled(Some(home.resolve("bin/histdb-server"))))
    assert(!Supervisor.bundled(Some(java.nio.file.Path.of("/usr/local/bin/histdb-server"))))

