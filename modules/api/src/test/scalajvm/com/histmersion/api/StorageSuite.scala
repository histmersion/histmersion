package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.maintenance.{Storage, StorageMarkdown}
class StorageSuite extends munit.FunSuite:

  private val GB = 1000L * 1000 * 1000
  private val vol = "/Volumes/Data (/dev/disk3s5)"

  private def e(key: String, path: Option[String], bytes: Long, shrink: Option[String] = None) =
    Storage.Entry(key, key, "caches", path, (Some(bytes)).map(com.histmersion.api.ByteCount.apply), (Some(1L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply), volume = Some(vol), shrink = shrink)

  test("paths nest by containment — the innermost parent, either separator, not a mere prefix"):
    assert(Storage.under("/d/pg", "/d"))
    assert(Storage.under("/d", "/d/"))
    assert(Storage.under("C:\\d\\x", "C:/d"))
    assert(!Storage.under("/data2", "/data"))
    val nested = Storage.nest(
      List(
        e("install", Some("/h"), 10),
        e("kit", Some("/h/kit"), 2),
        e("deep", Some("/h/kit/lib"), 1),
        e("logs", Some("/d/logs"), 3),
        Storage.Entry("database", "db", "database", None, (Some(5L)).map(com.histmersion.api.ByteCount.apply), within = Some("cluster"))
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
      List(e("install", Some("/h"), 10 * GB), e("kit", Some("/h/kit"), 2 * GB), e("logs", Some("/d/logs"), GB))
    )
    assertEquals(Storage.total(es), com.histmersion.api.ByteCount(11 * GB))
    // the same path twice: the later one is within the earlier
    val twice = Storage.nest(List(e("a", Some("/x"), 5), e("b", Some("/x"), 5)))
    assertEquals(twice.map(_.within), List(None, Some("a")))
    assertEquals(Storage.total(twice), com.histmersion.api.ByteCount(5L))

  test("shown: children follow their parent, missing locations are left out"):
    val es = Storage.nest(
      List(
        Storage.Entry("database", "db", "database", None, (Some(5L)).map(com.histmersion.api.ByteCount.apply), within = Some("cluster")),
        e("cluster", Some("/d/pg"), 6),
        e("gone", Some("/nope"), 0).modify(_.exists).setTo(false).modify(_.bytes).setTo((None: _root_.scala.Option[com.histmersion.api.ByteCount])),
        e("logs", Some("/d/logs"), 1)
      )
    )
    assertEquals(Storage.shown(es).map((x, d) => x.key -> d), List("cluster" -> 0, "database" -> 1, "logs" -> 0))

  test("low free space: a hint naming the three biggest that can shrink, each with how"):
    val r = Storage.Report(
      entries = Storage.nest(
        List(
          e("backups", Some("/d/backups"), 30 * GB, Storage.Advice.shrink("backups", 5)),
          e("basemap", Some("/c/basemap"), 3 * GB, Storage.Advice.shrink("basemap", 5)),
          e("originals", Some("/d/uploads"), 40 * GB), // nothing to do about it: not named
          e("gazetteer", Some("/c/gazetteer"), 1 * GB, Storage.Advice.shrink("gazetteer", 5)),
          e("logs", Some("/d/logs"), GB / 2, Storage.Advice.shrink("logs", 5))
        )
      ),
      volumes = List(Storage.Volume(vol, com.histmersion.api.ByteCount(500 * GB), com.histmersion.api.ByteCount(4 * GB)), Storage.Volume("/other", com.histmersion.api.ByteCount(100 * GB), com.histmersion.api.ByteCount(50 * GB)))
    )
    val hints = Storage.hints(r)
    assertEquals(hints.size, 1, hints)
    val h = hints.head
    assert(
      h.startsWith(
        s"Low disk space on $vol: 4.0 GB free of 500.0 GB. Biggest here: backups 30.0 GB (lower backups.keep (now 5)"
      ),
      h
    )
    assert(h.contains("basemap 3.0 GB (histmersion basemap rm"), h)
    assert(h.contains("gazetteer 1.0 GB"), h)
    assert(!h.contains("logs") && !h.contains("originals"), h)
    assertEquals(Storage.hints(r.modify(_.lowFreeBytes).setTo(com.histmersion.api.ByteCount(GB))), Nil)
    assertEquals(Storage.hints(r.modify(_.entries).setTo(Nil)), List(s"Low disk space on $vol: 4.0 GB free of 500.0 GB."))

  test("every location with something to do about it says what"):
    assert(Storage.Advice.shrink("backups", 0).exists(_.contains("now 0")))
    assert(Storage.Advice.shrink("toolchain", 5).exists(_.contains("histmersion-toolchain remove")))
    assert(Storage.Advice.shrink("database", 5).exists(_.contains("maintenance vacuum")))
    assert(Storage.Advice.shrink("cache:thumbs", 5).isDefined)
    assertEquals(Storage.Advice.shrink("originals", 5), None)
    assertEquals(Storage.Advice.shrink("plugins", 5), None)

  test("sizes: at least for a capped walk, a dash for nothing measured, not there for a missing path"):
    assertEquals(Storage.size(e("a", Some("/a"), 2 * GB)), "2.0 GB")
    assertEquals(Storage.size(e("a", Some("/a"), 2 * GB).modify(_.partial).setTo(true)), "at least 2.0 GB")
    assertEquals(Storage.size(e("a", Some("/a"), 0).modify(_.bytes).setTo((None: _root_.scala.Option[com.histmersion.api.ByteCount]))), "—")
    assertEquals(Storage.size(e("a", Some("/a"), 0).modify(_.bytes).setTo((None: _root_.scala.Option[com.histmersion.api.ByteCount])).modify(_.exists).setTo(false)), "not there")

  test("the CLI's own locations join the report; one the server reports already is not listed twice"):
    val server = Storage.Report(
      entries = List(e("install", Some("/h"), 10 * GB)),
      volumes = List(Storage.Volume(vol, com.histmersion.api.ByteCount(500 * GB), com.histmersion.api.ByteCount(400 * GB)))
    )
    val local = List(e("install", Some("/h"), 10 * GB), e("calibre-originals", Some("/o"), 2 * GB))
    val merged = Storage.addLocal(
      server,
      local,
      List(Storage.Volume(vol, com.histmersion.api.ByteCount(500 * GB), com.histmersion.api.ByteCount(400 * GB)), Storage.Volume("/usb", com.histmersion.api.ByteCount(10 * GB), com.histmersion.api.ByteCount(1 * GB)))
    )
    assertEquals(merged.entries.map(x => x.key -> x.host), List("install" -> "server", "calibre-originals" -> "cli"))
    assertEquals(merged.volumes.map(_.id), List(vol, "/usb"))
    assertEquals(Storage.addLocal(server, local.take(1), Nil), server)

  private val report = Storage.Report(
    measuredAt = Some("2026-10-19T10:00:00Z"),
    tookMillis = (Some(420)).map(com.histmersion.api.maintenance.Storage.Report.DurationMillis.apply),
    ageSeconds = (Some(12)).map(com.histmersion.api.maintenance.Storage.Report.AgeSeconds.apply),
    entries = Storage.nest(
      List(
        Storage.Entry(
          "database",
          "database (Postgres)",
          "database",
          None,
          (Some(9 * GB)).map(com.histmersion.api.ByteCount.apply),
          within = Some("database-cluster"),
          detail = Some("histmersion")
        ),
        Storage.Entry(
          "database-cluster",
          "database files (bundled Postgres)",
          "database",
          Some("/d/pg"),
          (Some(10 * GB)).map(com.histmersion.api.ByteCount.apply),
          (Some(3000L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          volume = Some(vol)
        ),
        Storage.Entry(
          "backups",
          "backups",
          "backups",
          Some("/d/backups"),
          (Some(28 * GB)).map(com.histmersion.api.ByteCount.apply),
          (Some(5L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          volume = Some(vol),
          shrink = Storage.Advice.shrink("backups", 5)
        ),
        Storage.Entry("toolchain", "GraalVM toolchain", "install", Some("/d/toolchain"), None, None, exists = false),
        Storage.Entry(
          "basemap",
          "terrain base map (cache)",
          "caches",
          Some("/c/basemap"),
          (Some(600_000_000L)).map(com.histmersion.api.ByteCount.apply),
          (Some(3L)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          partial = true,
          detail = Some("vector 410.0 MB · terrain 190.0 MB"),
          volume = Some(vol)
        )
      )
    ),
    tables = List(TableSize("sources", 5 * GB), TableSize("assets", 2 * GB)),
    volumes = List(Storage.Volume(vol, com.histmersion.api.ByteCount(994 * GB), com.histmersion.api.ByteCount(120 * GB))),
    backups = Some(BackupsUsage(5, 28 * GB, Some("histmersion-20261019T212919Z.dump"), 5))
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
    val low = Storage.withHints(report.modify(_.volumes).setTo(List(Storage.Volume(vol, com.histmersion.api.ByteCount(994 * GB), com.histmersion.api.ByteCount(5 * GB)))))
    val lmd = StorageMarkdown.render(low).mkString("\n")
    assert(lmd.contains("5.0 GB free of 994.0 GB — LOW"), lmd)
    assert(lmd.contains("- Low disk space on"), lmd)

  test("histmersion about: measuring, and nothing at all from an older server"):
    assertEquals(StorageMarkdown.render(Storage.Report()), Nil)
    assertEquals(
      StorageMarkdown.render(Storage.Report(measuring = true)),
      List("", "## Storage (measuring…)", "", "- measuring…")
    )
    val about = About(
      "histmersion",
      BuildStamp("1"),
      Health("ok", "ok", None, None),
      JobCounts(),
      None,
      None,
      None,
      storage = report
    )
    assert(AboutMarkdown.render(about).contains("## Storage — "), AboutMarkdown.render(about))

  test("an About without storage (an older server) still decodes; one with it round-trips"):
    import io.circe.syntax.*
    val about = About(
      "histmersion",
      BuildStamp("1"),
      Health("ok", "ok", None, None),
      JobCounts(),
      None,
      None,
      None,
      storage = report
    )
    assertEquals(about.asJson.mapObject(_.remove("storage")).as[About].map(_.storage), Right(Storage.Report()))
    assertEquals(about.asJson.as[About].map(_.storage), Right(report))
