package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{ServiceState, TableSize}
import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.instance.{About, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}

/** The About window's Storage and Disk space sections, in their pure part. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AboutStorageSuite extends munit.FunSuite:

  private val GB = 1000L * 1000 * 1000
  private val vol = "/System/Volumes/Data (/dev/disk3s5)"

  private val about =
    About(
      About.Name("histmersion"),
      BuildStamp(BuildStamp.Version("1.2.3")),
      Health(Health.Status("ok"), Health.Db("ok"), Some(Health.SchemaVersion("97")), Some(Health.Converter("ok"))),
      JobCounts(),
      None,
      None,
      None
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val report = Storage.Report(
    ageSeconds = (Some(30)).map(Storage.Report.AgeSeconds.apply),
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
          volume = Some(Storage.Entry.Volume(vol))
        ),
        Storage.Entry(
          Storage.Entry.Key("backups"),
          Storage.Entry.Label("backups"),
          Storage.Entry.Group("backups"),
          Some(Storage.Entry.Path("/d/backups")),
          (Some(28 * GB)).map(ByteCount.apply),
          (Some(5L)).map(Storage.FileCount.apply),
          volume = Some(Storage.Entry.Volume(vol)),
          shrink = Storage.Advice
            .shrink(Storage.Entry.Key("backups"), Storage.Advice.Keep(5))
            .map(h => Storage.Entry.Shrink(h.value))
        ),
        Storage.Entry(
          Storage.Entry.Key("plugins"),
          Storage.Entry.Label("plugins"),
          Storage.Entry.Group("install"),
          Some(Storage.Entry.Path("/d/plugins")),
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
          volume = Some(Storage.Entry.Volume(vol))
        ),
        Storage.Entry(
          Storage.Entry.Key("calibre-originals"),
          Storage.Entry.Label("originals folder (cli.calibre.originals-dir)"),
          Storage.Entry.Group("corpus"),
          Some(Storage.Entry.Path("/o")),
          (Some(GB)).map(ByteCount.apply),
          (Some(10L)).map(Storage.FileCount.apply),
          host = Storage.Entry.Host("cli")
        )
      )
    ),
    tables = List(TableSize(TableSize.Name("sources"), ByteCount(5 * GB))),
    volumes = List(Storage.Volume(Storage.Volume.Id(vol), ByteCount(994 * GB), ByteCount(5 * GB))),
    backups = Some(
      BackupsUsage(
        BackupsUsage.Count(5),
        ByteCount(28 * GB),
        Some(BackupsUsage.Newest("histmersion-1.dump")),
        BackupsUsage.Keep(5)
      )
    )
  )

  test("Storage: the total, each location nested under the one it is inside, missing ones left out"):
    val s = AboutView.sections(about.modify(_.storage).setTo(Storage.withHints(report)))
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Storage", "Disk space"))
    val rows = s.find(_.title == "Storage").get.rows
    assertEquals(
      rows.map(r => (r.label, r.depth)),
      List(
        "in all" -> 0,
        "database files (bundled Postgres)" -> 0,
        "database (Postgres)" -> 1,
        "backups" -> 0,
        "terrain base map (cache)" -> 0,
        "originals folder (cli.calibre.originals-dir) (this machine)" -> 0
      )
    )
    assertEquals((rows.head.value, rows.head.detail), ("39.6 GB", Some("measured 30 s ago")))
    def row(l: String) = rows.find(_.label == l).get
    assertEquals(row("database (Postgres)").detail, Some("largest tables: sources 5.0 GB · histmersion"))
    assertEquals(row("backups").detail, Some("5 files · 5 archives, newest histmersion-1.dump, keeps 5 · /d/backups"))
    assertEquals(
      (row("terrain base map (cache)").value, row("terrain base map (cache)").state),
      ("at least 600.0 MB", Some(ServiceState.Busy))
    )

  test("Disk space: each volume with a badge, and what to delete when it runs low"):
    val s = AboutView.sections(about.modify(_.storage).setTo(Storage.withHints(report)))
    val rows = s.find(_.title == "Disk space").get.rows
    assertEquals((rows.head.label, rows.head.value, rows.head.state), (vol, "5.0 GB free", Some(ServiceState.Down)))
    assert(rows(1).value.startsWith(s"Low disk space on $vol"), rows)
    assert(rows(1).value.contains("backups 28.0 GB (lower backups.keep (now 5)"), rows)
    val roomy = AboutView.storage(
      report
        .modify(_.volumes)
        .setTo(List(Storage.Volume(Storage.Volume.Id(vol), ByteCount(994 * GB), ByteCount(500 * GB))))
    )
    assertEquals(roomy.last.rows.map(_.state), List(Some(ServiceState.Ok)))

  test("measuring: the window says so; an older server's About has no Storage section"):
    assertEquals(AboutView.storage(Storage.Report()), Nil)
    val m = AboutView.storage(Storage.Report(measuring = Storage.Report.Measuring.Measuring))
    assertEquals(m.map(_.title), List("Storage"))
    assertEquals(m.head.rows, List(AboutView.Row("", "measuring…")))
    val again = AboutView.storage(report.modify(_.measuring).setTo(Storage.Report.Measuring.Measuring))
    assertEquals(again.head.rows.head.detail, Some("measured 30 s ago · measuring…"))
