package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.ByteCount
import com.histmersion.config.UserPaths
import com.histmersion.lint.{LintRule, tolerated}

/** `histmersion about`'s own locations: added only when the server is on this machine, never twice. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class LocalStorageSuite extends munit.FunSuite:

  private val about = About(
    About.Name("histmersion"),
    BuildStamp(BuildStamp.Version("1")),
    Health(Health.Status("ok"), Health.Db("ok"), None, None),
    JobCounts(),
    None,
    None,
    None,
    storage = Storage.Report(entries =
      List(
        Storage.Entry(
          Storage.Entry.Key("backups"),
          Storage.Entry.Label("backups"),
          Storage.Entry.Group("backups"),
          Some(Storage.Entry.Path("/srv/backups")),
          (Some(10L)).map(ByteCount.apply)
        )
      )
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def config(originals: Path, install: Path): AppConfig =
    val base = AppConfig.load()
    base
      .modify(_.cli)
      .setTo(
        base.cli
          .modify(_.calibre)
          .setTo(base.cli.calibre.modify(_.originalsDir).setTo(CalibreCliConfig.OriginalsDir(originals.toString)))
          .modify(_.plugins)
          .setTo(base.cli.plugins.modify(_.installDir).setTo(CliPluginsConfig.InstallDir(install.toString)))
      )

  test("a server on another machine: nothing of this machine is mixed in"):
    val d = Files.createTempDirectory("local-storage")
    val a = LocalStorage.add(
      about,
      Client.ServerUrl("https://histmersion.example.org"),
      config(d, d),
      _ => None,
      UserPaths.OsName("Linux"),
      None
    )
    assertEquals(a, about)

  test("on this machine: the originals folder and the install, measured, marked as this machine's"):
    val d = Files.createTempDirectory("local-storage")
    Files.createDirectories(d.resolve("originals/smith2001"))
    Files.write(d.resolve("originals/smith2001/paper.pdf"), Array.fill[Byte](1234)(1))
    Files.createDirectories(d.resolve("install/bin"))
    Files.write(d.resolve("install/bin/histmersion-relink"), Array.fill[Byte](100)(1))
    val env = Map(
      Environment.VariablesKey("HOME") -> Environment.VariablesValue(d.toString),
      Environment.VariablesKey("HISTMERSION_DATA_DIR") -> Environment.VariablesValue(d.resolve("data").toString)
    ).get
    val cfg = config(d.resolve("originals"), d.resolve("install"))
    val a = LocalStorage.add(
      about,
      Client.ServerUrl("http://127.0.0.1:8000"),
      cfg
        .modify(_.runtime)
        .setTo(cfg.runtime.modify(_.dataDir).setTo(RuntimeConfig.DataDir(d.resolve("data").toString))),
      env,
      UserPaths.OsName("Linux"),
      None
    )
    val local = a.storage.entries.filter(_.host == Storage.Entry.Host("cli"))
    assertEquals(
      local.map(e => (e.key, e.bytes)),
      List(
        Storage.Entry.Key("calibre-originals") -> Some(ByteCount(1234L)),
        Storage.Entry.Key("install") -> Some(ByteCount(100L))
      )
    )
    assert(a.storage.volumes.nonEmpty)
    val md = AboutMarkdown.render(a)
    assert(md.contains("- originals folder (cli.calibre.originals-dir) [on this machine]: 1.2 kB · 1 file"), md)
    // the server reports the install already: not listed twice
    val known = about
      .modify(_.storage)
      .setTo(
        about.storage
          .modify(_.entries)
          .setTo(
            about.storage.entries :+
              Storage.Entry(
                Storage.Entry.Key("install"),
                Storage.Entry.Label("install"),
                Storage.Entry.Group("install"),
                Some(Storage.Entry.Path(d.resolve("install").toAbsolutePath.normalize.toString)),
                (Some(100L)).map(ByteCount.apply)
              )
          )
      )
    val again =
      LocalStorage.add(known, Client.ServerUrl("http://localhost:8000"), cfg, env, UserPaths.OsName("Linux"), None)
    assertEquals(
      again.storage.entries.filter(_.host == Storage.Entry.Host("cli")).map(_.key),
      List(Storage.Entry.Key("calibre-originals"))
    )
