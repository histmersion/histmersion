package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.Storage

/** `histmersion about`'s own locations: added only when the server is on this machine, never twice. */
class LocalStorageSuite extends munit.FunSuite:

  private val about = About(
    "histmersion",
    BuildStamp("1"),
    Health("ok", "ok", None, None),
    JobCounts(),
    None,
    None,
    None,
    storage =
      Storage.Report(entries = List(Storage.Entry("backups", "backups", "backups", Some("/srv/backups"), (Some(10L)).map(com.histmersion.api.ByteCount.apply))))
  )

  private def config(originals: Path, install: Path): AppConfig =
    val base = AppConfig.load()
    base.modify(_.cli).setTo(base.cli.modify(
        _.calibre).setTo(base.cli.calibre.modify(_.originalsDir).setTo(originals.toString)).modify(_.plugins).setTo(base.cli.plugins.modify(_.installDir).setTo(install.toString)
      )
    )

  test("a server on another machine: nothing of this machine is mixed in"):
    val d = Files.createTempDirectory("local-storage")
    val a = LocalStorage.add(about, "https://histmersion.example.org", config(d, d), _ => None, "Linux", None)
    assertEquals(a, about)

  test("on this machine: the originals folder and the install, measured, marked as this machine's"):
    val d = Files.createTempDirectory("local-storage")
    Files.createDirectories(d.resolve("originals/smith2001"))
    Files.write(d.resolve("originals/smith2001/paper.pdf"), Array.fill[Byte](1234)(1))
    Files.createDirectories(d.resolve("install/bin"))
    Files.write(d.resolve("install/bin/histmersion-relink"), Array.fill[Byte](100)(1))
    val env = Map("HOME" -> d.toString, "HISTMERSION_DATA_DIR" -> d.resolve("data").toString).get
    val cfg = config(d.resolve("originals"), d.resolve("install"))
    val a = LocalStorage.add(
      about,
      "http://127.0.0.1:8000",
      cfg.modify(_.runtime).setTo(cfg.runtime.modify(_.dataDir).setTo(d.resolve("data").toString)),
      env,
      "Linux",
      None
    )
    val local = a.storage.entries.filter(_.host == "cli")
    assertEquals(local.map(e => (e.key, e.bytes)), List("calibre-originals" -> Some(com.histmersion.api.ByteCount(1234L)), "install" -> Some(com.histmersion.api.ByteCount(100L))))
    assert(a.storage.volumes.nonEmpty)
    val md = AboutMarkdown.render(a)
    assert(md.contains("- originals folder (cli.calibre.originals-dir) [on this machine]: 1.2 kB · 1 file"), md)
    // the server reports the install already: not listed twice
    val known = about.modify(_.storage).setTo(about.storage.modify(_.entries).setTo(about.storage.entries :+
          Storage.Entry(
            "install",
            "install",
            "install",
            Some(d.resolve("install").toAbsolutePath.normalize.toString),
            (Some(100L)).map(com.histmersion.api.ByteCount.apply)
          )
      )
    )
    val again = LocalStorage.add(known, "http://localhost:8000", cfg, env, "Linux", None)
    assertEquals(again.storage.entries.filter(_.host == "cli").map(_.key), List("calibre-originals"))
