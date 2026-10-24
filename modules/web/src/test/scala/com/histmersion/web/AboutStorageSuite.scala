package com.histmersion.web

import com.histmersion.api.*

/** The About window's Storage and Disk space sections, in their pure part. */
class AboutStorageSuite extends munit.FunSuite:

  private val GB  = 1000L * 1000 * 1000
  private val vol = "/System/Volumes/Data (/dev/disk3s5)"

  private val about = About("histmersion", BuildStamp("1.2.3"), Health("ok", "ok", Some("97"), Some("ok")),
    JobCounts(), None, None, None)

  private val report = StorageReport(
    ageSeconds = Some(30),
    entries = Storage.nest(List(
      StorageEntry("database", "database (Postgres)", "database", None, Some(9 * GB), within = Some("database-cluster"),
        detail = Some("histmersion")),
      StorageEntry("database-cluster", "database files (bundled Postgres)", "database", Some("/d/pg"), Some(10 * GB),
        Some(3000L), volume = Some(vol)),
      StorageEntry("backups", "backups", "backups", Some("/d/backups"), Some(28 * GB), Some(5L), volume = Some(vol),
        shrink = StorageAdvice.shrink("backups", 5)),
      StorageEntry("plugins", "plugins", "install", Some("/d/plugins"), None, None, exists = false),
      StorageEntry("basemap", "terrain base map (cache)", "caches", Some("/c/basemap"), Some(600_000_000L), Some(3L),
        partial = true, volume = Some(vol)),
      StorageEntry("calibre-originals", "originals folder (cli.calibre.originals-dir)", "corpus", Some("/o"),
        Some(GB), Some(10L), host = "cli"))),
    tables = List(TableSize("sources", 5 * GB)),
    volumes = List(StorageVolume(vol, 994 * GB, 5 * GB)),
    backups = Some(BackupsUsage(5, 28 * GB, Some("histmersion-1.dump"), 5)))

  test("Storage: the total, each location nested under the one it is inside, missing ones left out"):
    val s = AboutView.sections(about.copy(storage = Storage.withHints(report)))
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Storage", "Disk space"))
    val rows = s.find(_.title == "Storage").get.rows
    assertEquals(rows.map(r => (r.label, r.depth)), List(
      "in all" -> 0, "database files (bundled Postgres)" -> 0, "database (Postgres)" -> 1, "backups" -> 0,
      "terrain base map (cache)" -> 0, "originals folder (cli.calibre.originals-dir) (this machine)" -> 0))
    assertEquals((rows.head.value, rows.head.detail), ("39.6 GB", Some("measured 30 s ago")))
    def row(l: String) = rows.find(_.label == l).get
    assertEquals(row("database (Postgres)").detail, Some("largest tables: sources 5.0 GB · histmersion"))
    assertEquals(row("backups").detail, Some("5 files · 5 archives, newest histmersion-1.dump, keeps 5 · /d/backups"))
    assertEquals((row("terrain base map (cache)").value, row("terrain base map (cache)").state),
      ("at least 600.0 MB", Some(ServiceState.Busy)))

  test("Disk space: each volume with a badge, and what to delete when it runs low"):
    val s = AboutView.sections(about.copy(storage = Storage.withHints(report)))
    val rows = s.find(_.title == "Disk space").get.rows
    assertEquals((rows.head.label, rows.head.value, rows.head.state), (vol, "5.0 GB free", Some(ServiceState.Down)))
    assert(rows(1).value.startsWith(s"Low disk space on $vol"), rows)
    assert(rows(1).value.contains("backups 28.0 GB (lower backups.keep (now 5)"), rows)
    val roomy = AboutView.storage(report.copy(volumes = List(StorageVolume(vol, 994 * GB, 500 * GB))))
    assertEquals(roomy.last.rows.map(_.state), List(Some(ServiceState.Ok)))

  test("measuring: the window says so; an older server's About has no Storage section"):
    assertEquals(AboutView.storage(StorageReport()), Nil)
    val m = AboutView.storage(StorageReport(measuring = true))
    assertEquals(m.map(_.title), List("Storage"))
    assertEquals(m.head.rows, List(AboutView.Row("", "measuring…")))
    val again = AboutView.storage(report.copy(measuring = true))
    assertEquals(again.head.rows.head.detail, Some("measured 30 s ago · measuring…"))
