package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{ServiceState, TableSize}
import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.instance.{About, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.Storage

/** The About window's Storage and Disk space sections, in their pure part. */
class AboutStorageSuite extends munit.FunSuite:

  private val GB = 1000L * 1000 * 1000
  private val vol = "/System/Volumes/Data (/dev/disk3s5)"

  private val about =
    About(com.histmersion.api.instance.About.Name("histmersion"), BuildStamp(com.histmersion.api.instance.BuildStamp.Version("1.2.3")), Health(com.histmersion.api.instance.Health.Status("ok"), com.histmersion.api.instance.Health.Db("ok"), Some(com.histmersion.api.instance.Health.SchemaVersion("97")), Some(com.histmersion.api.instance.Health.Converter("ok"))), JobCounts(), None, None, None)

  private val report = Storage.Report(
    ageSeconds = (Some(30)).map(com.histmersion.api.maintenance.Storage.Report.AgeSeconds.apply),
    entries = Storage.nest(
      List(
        Storage.Entry(
          com.histmersion.api.maintenance.Storage.Entry.Key("database"),
          com.histmersion.api.maintenance.Storage.Entry.Label("database (Postgres)"),
          com.histmersion.api.maintenance.Storage.Entry.Group("database"),
          None,
          (Some(9 * GB)).map(com.histmersion.api.ByteCount.apply),
          within = Some(com.histmersion.api.maintenance.Storage.Entry.Key("database-cluster")),
          detail = Some(com.histmersion.api.maintenance.Storage.Entry.Detail("histmersion"))
        ),
        Storage.Entry(
          com.histmersion.api.maintenance.Storage.Entry.Key("database-cluster"),
          com.histmersion.api.maintenance.Storage.Entry.Label("database files (bundled Postgres)"),
          com.histmersion.api.maintenance.Storage.Entry.Group("database"),
          Some(com.histmersion.api.maintenance.Storage.Entry.Path("/d/pg")),
          (Some(10 * GB)).map(com.histmersion.api.ByteCount.apply),
          (Some(3000L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          volume = Some(Storage.Entry.Volume(vol))
        ),
        Storage.Entry(
          com.histmersion.api.maintenance.Storage.Entry.Key("backups"),
          com.histmersion.api.maintenance.Storage.Entry.Label("backups"),
          com.histmersion.api.maintenance.Storage.Entry.Group("backups"),
          Some(com.histmersion.api.maintenance.Storage.Entry.Path("/d/backups")),
          (Some(28 * GB)).map(com.histmersion.api.ByteCount.apply),
          (Some(5L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          volume = Some(Storage.Entry.Volume(vol)),
          shrink = Storage.Advice.shrink(com.histmersion.api.maintenance.Storage.Entry.Key("backups"), com.histmersion.api.maintenance.Storage.Advice.Keep(5)).map(h => Storage.Entry.Shrink(h.value))
        ),
        Storage.Entry(com.histmersion.api.maintenance.Storage.Entry.Key("plugins"), com.histmersion.api.maintenance.Storage.Entry.Label("plugins"), com.histmersion.api.maintenance.Storage.Entry.Group("install"), Some(com.histmersion.api.maintenance.Storage.Entry.Path("/d/plugins")), None, None, exists = com.histmersion.api.maintenance.Storage.Entry.Exists(false)),
        Storage.Entry(
          com.histmersion.api.maintenance.Storage.Entry.Key("basemap"),
          com.histmersion.api.maintenance.Storage.Entry.Label("terrain base map (cache)"),
          com.histmersion.api.maintenance.Storage.Entry.Group("caches"),
          Some(com.histmersion.api.maintenance.Storage.Entry.Path("/c/basemap")),
          (Some(600_000_000L)).map(com.histmersion.api.ByteCount.apply),
          (Some(3L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          partial = com.histmersion.api.maintenance.Storage.Entry.Partial(true),
          volume = Some(Storage.Entry.Volume(vol))
        ),
        Storage.Entry(
          com.histmersion.api.maintenance.Storage.Entry.Key("calibre-originals"),
          com.histmersion.api.maintenance.Storage.Entry.Label("originals folder (cli.calibre.originals-dir)"),
          com.histmersion.api.maintenance.Storage.Entry.Group("corpus"),
          Some(com.histmersion.api.maintenance.Storage.Entry.Path("/o")),
          (Some(GB)).map(com.histmersion.api.ByteCount.apply),
          (Some(10L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          host = com.histmersion.api.maintenance.Storage.Entry.Host("cli")
        )
      )
    ),
    tables = List(TableSize(com.histmersion.api.TableSize.Name("sources"), com.histmersion.api.ByteCount(5 * GB))),
    volumes = List(Storage.Volume(Storage.Volume.Id(vol), com.histmersion.api.ByteCount(994 * GB), com.histmersion.api.ByteCount(5 * GB))),
    backups = Some(BackupsUsage(com.histmersion.api.backup.BackupsUsage.Count(5), com.histmersion.api.ByteCount(28 * GB), Some(com.histmersion.api.backup.BackupsUsage.Newest("histmersion-1.dump")), com.histmersion.api.backup.BackupsUsage.Keep(5)))
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
    val roomy = AboutView.storage(report.modify(_.volumes).setTo(List(Storage.Volume(Storage.Volume.Id(vol), com.histmersion.api.ByteCount(994 * GB), com.histmersion.api.ByteCount(500 * GB)))))
    assertEquals(roomy.last.rows.map(_.state), List(Some(ServiceState.Ok)))

  test("measuring: the window says so; an older server's About has no Storage section"):
    assertEquals(AboutView.storage(Storage.Report()), Nil)
    val m = AboutView.storage(Storage.Report(measuring = com.histmersion.api.maintenance.Storage.Report.Measuring(true)))
    assertEquals(m.map(_.title), List("Storage"))
    assertEquals(m.head.rows, List(AboutView.Row("", "measuring…")))
    val again = AboutView.storage(report.modify(_.measuring).setTo(Storage.Report.Measuring(true)))
    assertEquals(again.head.rows.head.detail, Some("measured 30 s ago · measuring…"))
