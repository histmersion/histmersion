package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.{Storage, StorageMarkdown}
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StorageSuite extends munit.FunSuite:

  private val GB = 1000L * 1000 * 1000
  private val vol = Storage.Volume.Id("/Volumes/Data (/dev/disk3s5)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val entryVolume = Storage.Entry.Volume(vol.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def e(
      key: Storage.Entry.Key,
      path: Option[Storage.Entry.Path],
      bytes: ByteCount,
      shrink: Option[Storage.Entry.Shrink] = None
  ) =
    Storage.Entry(
      key,
      Storage.Entry.Label(key.value),
      Storage.Entry.Group("caches"),
      path,
      Some(bytes),
      Some(Storage.FileCount(1L)),
      volume = Some(entryVolume),
      shrink = shrink
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def shrinkOf(key: Storage.Entry.Key, keep: Storage.Advice.Keep): Option[Storage.Entry.Shrink] =
    Storage.Advice.shrink(key, keep).map(h => Storage.Entry.Shrink(h.value))

  test("paths nest by containment — the innermost parent, either separator, not a mere prefix"):
    assert(Storage.under(Storage.Entry.Path("/d/pg"), Storage.Entry.Path("/d")))
    assert(Storage.under(Storage.Entry.Path("/d"), Storage.Entry.Path("/d/")))
    assert(Storage.under(Storage.Entry.Path("C:\\d\\x"), Storage.Entry.Path("C:/d")))
    assert(!Storage.under(Storage.Entry.Path("/data2"), Storage.Entry.Path("/data")))
    val nested = Storage.nest(
      List(
        e(Storage.Entry.Key("install"), Some(Storage.Entry.Path("/h")), ByteCount(10)),
        e(Storage.Entry.Key("kit"), Some(Storage.Entry.Path("/h/kit")), ByteCount(2)),
        e(Storage.Entry.Key("deep"), Some(Storage.Entry.Path("/h/kit/lib")), ByteCount(1)),
        e(Storage.Entry.Key("logs"), Some(Storage.Entry.Path("/d/logs")), ByteCount(3)),
        Storage.Entry(
          Storage.Entry.Key("database"),
          Storage.Entry.Label("db"),
          Storage.Entry.Group("database"),
          None,
          (Some(5L)).map(ByteCount.apply),
          within = Some(Storage.Entry.Key("cluster"))
        )
      )
    )
    assertEquals(
      nested.map(x => x.key -> x.within).toMap,
      Map(
        "install" -> None,
        "kit" -> Some("install"),
        "deep" -> Some("kit"),
        "logs" -> None,
        "database" -> Some("cluster")
      )
    )

  test("the total counts every byte once: what is inside another entry is not added again"):
    val es = Storage.nest(
      List(
        e(Storage.Entry.Key("install"), Some(Storage.Entry.Path("/h")), ByteCount(10 * GB)),
        e(Storage.Entry.Key("kit"), Some(Storage.Entry.Path("/h/kit")), ByteCount(2 * GB)),
        e(Storage.Entry.Key("logs"), Some(Storage.Entry.Path("/d/logs")), ByteCount(GB))
      )
    )
    assertEquals(Storage.total(es), ByteCount(11 * GB))
    // the same path twice: the later one is within the earlier
    val twice = Storage.nest(
      List(
        e(Storage.Entry.Key("a"), Some(Storage.Entry.Path("/x")), ByteCount(5)),
        e(Storage.Entry.Key("b"), Some(Storage.Entry.Path("/x")), ByteCount(5))
      )
    )
    assertEquals(twice.map(_.within), List(None, Some("a")))
    assertEquals(Storage.total(twice), ByteCount(5L))

  test("shown: children follow their parent, missing locations are left out"):
    val es = Storage.nest(
      List(
        Storage.Entry(
          Storage.Entry.Key("database"),
          Storage.Entry.Label("db"),
          Storage.Entry.Group("database"),
          None,
          (Some(5L)).map(ByteCount.apply),
          within = Some(Storage.Entry.Key("cluster"))
        ),
        e(Storage.Entry.Key("cluster"), Some(Storage.Entry.Path("/d/pg")), ByteCount(6)),
        e(Storage.Entry.Key("gone"), Some(Storage.Entry.Path("/nope")), ByteCount(0))
          .modify(_.exists)
          .setTo(Storage.Entry.Exists.Missing)
          .modify(_.bytes)
          .setTo((None: Option[ByteCount])),
        e(Storage.Entry.Key("logs"), Some(Storage.Entry.Path("/d/logs")), ByteCount(1))
      )
    )
    assertEquals(Storage.shown(es).map((x, d) => x.key -> d), List("cluster" -> 0, "database" -> 1, "logs" -> 0))

  test("low free space: a hint naming the three biggest that can shrink, each with how"):
    val r = Storage.Report(
      entries = Storage.nest(
        List(
          e(
            Storage.Entry.Key("backups"),
            Some(Storage.Entry.Path("/d/backups")),
            ByteCount(30 * GB),
            shrinkOf(Storage.Entry.Key("backups"), Storage.Advice.Keep(5))
          ),
          e(
            Storage.Entry.Key("basemap"),
            Some(Storage.Entry.Path("/c/basemap")),
            ByteCount(3 * GB),
            shrinkOf(Storage.Entry.Key("basemap"), Storage.Advice.Keep(5))
          ),
          e(
            Storage.Entry.Key("originals"),
            Some(Storage.Entry.Path("/d/uploads")),
            ByteCount(40 * GB)
          ), // nothing to do about it: not named
          e(
            Storage.Entry.Key("gazetteer"),
            Some(Storage.Entry.Path("/c/gazetteer")),
            ByteCount(1 * GB),
            shrinkOf(Storage.Entry.Key("gazetteer"), Storage.Advice.Keep(5))
          ),
          e(
            Storage.Entry.Key("logs"),
            Some(Storage.Entry.Path("/d/logs")),
            ByteCount(GB / 2),
            shrinkOf(Storage.Entry.Key("logs"), Storage.Advice.Keep(5))
          )
        )
      ),
      volumes = List(
        Storage.Volume(vol, ByteCount(500 * GB), ByteCount(4 * GB)),
        Storage.Volume(Storage.Volume.Id("/other"), ByteCount(100 * GB), ByteCount(50 * GB))
      )
    )
    val hints = Storage.hints(r)
    assertEquals(hints.size, 1, hints)
    val h = hints.head
    assert(
      h.value.startsWith(
        s"Low disk space on $vol: 4.0 GB free of 500.0 GB. Biggest here: backups 30.0 GB (lower backups.keep (now 5)"
      ),
      h
    )
    assert(h.value.contains("basemap 3.0 GB (histmersion basemap rm"), h)
    assert(h.value.contains("gazetteer 1.0 GB"), h)
    assert(!h.value.contains("logs") && !h.value.contains("originals"), h)
    assertEquals(Storage.hints(r.modify(_.lowFreeBytes).setTo(ByteCount(GB))), Nil)
    assertEquals(
      Storage.hints(r.modify(_.entries).setTo(Nil)),
      List(s"Low disk space on $vol: 4.0 GB free of 500.0 GB.")
    )

  test("every location with something to do about it says what"):
    assert(
      Storage.Advice.shrink(Storage.Entry.Key("backups"), Storage.Advice.Keep(0)).exists(_.value.contains("now 0"))
    )
    assert(
      Storage.Advice
        .shrink(Storage.Entry.Key("toolchain"), Storage.Advice.Keep(5))
        .exists(_.value.contains("histmersion-toolchain remove"))
    )
    assert(
      Storage.Advice
        .shrink(Storage.Entry.Key("database"), Storage.Advice.Keep(5))
        .exists(_.value.contains("maintenance vacuum"))
    )
    assert(Storage.Advice.shrink(Storage.Entry.Key("cache:thumbs"), Storage.Advice.Keep(5)).isDefined)
    assertEquals(Storage.Advice.shrink(Storage.Entry.Key("originals"), Storage.Advice.Keep(5)), None)
    assertEquals(Storage.Advice.shrink(Storage.Entry.Key("plugins"), Storage.Advice.Keep(5)), None)

  test("sizes: at least for a capped walk, a dash for nothing measured, not there for a missing path"):
    assertEquals(Storage.size(e(Storage.Entry.Key("a"), Some(Storage.Entry.Path("/a")), ByteCount(2 * GB))), "2.0 GB")
    assertEquals(
      Storage.size(
        e(Storage.Entry.Key("a"), Some(Storage.Entry.Path("/a")), ByteCount(2 * GB))
          .modify(_.partial)
          .setTo(Storage.Entry.Partial.Partial)
      ),
      "at least 2.0 GB"
    )
    assertEquals(
      Storage.size(
        e(Storage.Entry.Key("a"), Some(Storage.Entry.Path("/a")), ByteCount(0))
          .modify(_.bytes)
          .setTo((None: Option[ByteCount]))
      ),
      "—"
    )
    assertEquals(
      Storage.size(
        e(Storage.Entry.Key("a"), Some(Storage.Entry.Path("/a")), ByteCount(0))
          .modify(_.bytes)
          .setTo((None: Option[ByteCount]))
          .modify(_.exists)
          .setTo(Storage.Entry.Exists.Missing)
      ),
      "not there"
    )

  test("the CLI's own locations join the report; one the server reports already is not listed twice"):
    val server = Storage.Report(
      entries = List(e(Storage.Entry.Key("install"), Some(Storage.Entry.Path("/h")), ByteCount(10 * GB))),
      volumes = List(Storage.Volume(vol, ByteCount(500 * GB), ByteCount(400 * GB)))
    )
    val local = List(
      e(Storage.Entry.Key("install"), Some(Storage.Entry.Path("/h")), ByteCount(10 * GB)),
      e(Storage.Entry.Key("calibre-originals"), Some(Storage.Entry.Path("/o")), ByteCount(2 * GB))
    )
    val merged = Storage.addLocal(
      server,
      local,
      List(
        Storage.Volume(vol, ByteCount(500 * GB), ByteCount(400 * GB)),
        Storage.Volume(Storage.Volume.Id("/usb"), ByteCount(10 * GB), ByteCount(1 * GB))
      )
    )
    assertEquals(merged.entries.map(x => x.key -> x.host), List("install" -> "server", "calibre-originals" -> "cli"))
    assertEquals(merged.volumes.map(_.id), List(vol, Storage.Volume.Id("/usb")))
    assertEquals(Storage.addLocal(server, local.take(1), Nil), server)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val report = Storage.Report(
    measuredAt = Some(Storage.Report.MeasuredAt("2026-10-19T10:00:00Z")),
    tookMillis = (Some(420)).map(Storage.Report.DurationMillis.apply),
    ageSeconds = (Some(12)).map(Storage.Report.AgeSeconds.apply),
    entries = Storage.nest(
      List(
        Storage.Entry(
          Storage.Entry.Key("database"),
          Storage.Entry.Label("database (Postgres)"),
          Storage.Entry.Group("database"),
          None,
          (Some(9 * GB)).map(ByteCount.apply),
          within = Some(Storage.Entry.Key("database-cluster")),
          detail = Some(Storage.Entry.Detail("histmersion"))
        ),
        Storage.Entry(
          Storage.Entry.Key("database-cluster"),
          Storage.Entry.Label("database files (bundled Postgres)"),
          Storage.Entry.Group("database"),
          Some(Storage.Entry.Path("/d/pg")),
          (Some(10 * GB)).map(ByteCount.apply),
          (Some(3000L)).map(Storage.FileCount.apply),
          volume = Some(entryVolume)
        ),
        Storage.Entry(
          Storage.Entry.Key("backups"),
          Storage.Entry.Label("backups"),
          Storage.Entry.Group("backups"),
          Some(Storage.Entry.Path("/d/backups")),
          (Some(28 * GB)).map(ByteCount.apply),
          (Some(5L)).map(Storage.FileCount.apply),
          volume = Some(entryVolume),
          shrink = shrinkOf(Storage.Entry.Key("backups"), Storage.Advice.Keep(5))
        ),
        Storage.Entry(
          Storage.Entry.Key("toolchain"),
          Storage.Entry.Label("GraalVM toolchain"),
          Storage.Entry.Group("install"),
          Some(Storage.Entry.Path("/d/toolchain")),
          None,
          None,
          exists = Storage.Entry.Exists.Missing
        ),
        Storage.Entry(
          Storage.Entry.Key("basemap"),
          Storage.Entry.Label("terrain base map (cache)"),
          Storage.Entry.Group("caches"),
          Some(Storage.Entry.Path("/c/basemap")),
          (Some(600_000_000L)).map(ByteCount.apply),
          (Some(3L)).map(Storage.FileCount.apply),
          partial = Storage.Entry.Partial.Partial,
          detail = Some(Storage.Entry.Detail("vector 410.0 MB · terrain 190.0 MB")),
          volume = Some(entryVolume)
        )
      )
    ),
    tables = List(
      TableSize(TableSize.Name("sources"), ByteCount(5 * GB)),
      TableSize(TableSize.Name("assets"), ByteCount(2 * GB))
    ),
    volumes = List(Storage.Volume(vol, ByteCount(994 * GB), ByteCount(120 * GB))),
    backups = Some(
      BackupsUsage(
        BackupsUsage.Count(5),
        ByteCount(28 * GB),
        Some(BackupsUsage.Newest("histmersion-20261019T212919Z.dump")),
        BackupsUsage.Keep(5)
      )
    )
  )

  test("histmersion about: the Storage section — total, nesting, backups, tables, free space"):
    val md = StorageMarkdown.render(report).mkString("\n")
    assert(md.contains("## Storage — 38.6 GB in all (measured 12 s ago, took 420 ms)"), md)
    assert(
      md.contains(
        "- database files (bundled Postgres): 10.0 GB · 3000 files · /d/pg\n" +
          "  - database (Postgres): 9.0 GB · largest tables: sources 5.0 GB, assets 2.0 GB · histmersion"
      ),
      md
    )
    assert(
      md.contains(
        "- backups: 28.0 GB · 5 files · 5 archives, newest histmersion-20261019T212919Z.dump, keeps 5 · /d/backups"
      ),
      md
    )
    assert(
      md.contains(
        "- terrain base map (cache): at least 600.0 MB · 3 files · vector 410.0 MB · terrain 190.0 MB · /c/basemap"
      ),
      md
    )
    assert(!md.contains("toolchain"), md)
    assert(md.contains(s"- free space: $vol: 120.0 GB free of 994.0 GB"), md)
    assert(!md.contains("LOW") && !md.contains("Low disk space"), md)
    val low = Storage.withHints(
      report.modify(_.volumes).setTo(List(Storage.Volume(vol, ByteCount(994 * GB), ByteCount(5 * GB))))
    )
    val lmd = StorageMarkdown.render(low).mkString("\n")
    assert(lmd.contains("5.0 GB free of 994.0 GB — LOW"), lmd)
    assert(lmd.contains("- Low disk space on"), lmd)

  test("histmersion about: measuring, and nothing at all from an older server"):
    assertEquals(StorageMarkdown.render(Storage.Report()), Nil)
    assertEquals(
      StorageMarkdown.render(Storage.Report(measuring = Storage.Report.Measuring.Measuring)),
      List("", "## Storage (measuring…)", "", "- measuring…")
    )
    val about = About(
      About.Name("histmersion"),
      BuildStamp(BuildStamp.Version("1")),
      Health(Health.Status("ok"), Health.Db("ok"), None, None),
      JobCounts(),
      None,
      None,
      None,
      storage = report
    )
    assert(AboutMarkdown.render(about).contains("## Storage — "), AboutMarkdown.render(about))

  test("an About without storage (an older server) still decodes; one with it round-trips"):
    import com.histmersion.util.json.RawJson.*
    val about = About(
      About.Name("histmersion"),
      BuildStamp(BuildStamp.Version("1")),
      Health(Health.Status("ok"), Health.Db("ok"), None, None),
      JobCounts(),
      None,
      None,
      None,
      storage = report
    )
    assertEquals(about.asJson.mapObject(_.remove("storage")).as[About].map(_.storage), Right(Storage.Report()))
    assertEquals(about.asJson.as[About].map(_.storage), Right(report))
