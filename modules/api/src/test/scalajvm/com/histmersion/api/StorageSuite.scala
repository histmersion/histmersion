package com.histmersion.api

class StorageSuite extends munit.FunSuite:

  private val GB = 1000L * 1000 * 1000
  private val vol = "/Volumes/Data (/dev/disk3s5)"

  private def e(key: String, path: Option[String], bytes: Long, shrink: Option[String] = None) =
    StorageEntry(key, key, "caches", path, Some(bytes), Some(1L), volume = Some(vol), shrink = shrink)

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
        StorageEntry("database", "db", "database", None, Some(5L), within = Some("cluster"))
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
    assertEquals(Storage.total(es), 11 * GB)
    // the same path twice: the later one is within the earlier
    val twice = Storage.nest(List(e("a", Some("/x"), 5), e("b", Some("/x"), 5)))
    assertEquals(twice.map(_.within), List(None, Some("a")))
    assertEquals(Storage.total(twice), 5L)

  test("shown: children follow their parent, missing locations are left out"):
    val es = Storage.nest(
      List(
        StorageEntry("database", "db", "database", None, Some(5L), within = Some("cluster")),
        e("cluster", Some("/d/pg"), 6),
        e("gone", Some("/nope"), 0).copy(exists = false, bytes = None),
        e("logs", Some("/d/logs"), 1)
      )
    )
    assertEquals(Storage.shown(es).map((x, d) => x.key -> d), List("cluster" -> 0, "database" -> 1, "logs" -> 0))

  test("low free space: a hint naming the three biggest that can shrink, each with how"):
    val r = StorageReport(
      entries = Storage.nest(
        List(
          e("backups", Some("/d/backups"), 30 * GB, StorageAdvice.shrink("backups", 5)),
          e("basemap", Some("/c/basemap"), 3 * GB, StorageAdvice.shrink("basemap", 5)),
          e("originals", Some("/d/uploads"), 40 * GB), // nothing to do about it: not named
          e("gazetteer", Some("/c/gazetteer"), 1 * GB, StorageAdvice.shrink("gazetteer", 5)),
          e("logs", Some("/d/logs"), GB / 2, StorageAdvice.shrink("logs", 5))
        )
      ),
      volumes = List(StorageVolume(vol, 500 * GB, 4 * GB), StorageVolume("/other", 100 * GB, 50 * GB))
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
    assertEquals(Storage.hints(r.copy(lowFreeBytes = GB)), Nil)
    assertEquals(Storage.hints(r.copy(entries = Nil)), List(s"Low disk space on $vol: 4.0 GB free of 500.0 GB."))

  test("every location with something to do about it says what"):
    assert(StorageAdvice.shrink("backups", 0).exists(_.contains("now 0")))
    assert(StorageAdvice.shrink("toolchain", 5).exists(_.contains("histmersion-toolchain remove")))
    assert(StorageAdvice.shrink("database", 5).exists(_.contains("maintenance vacuum")))
    assert(StorageAdvice.shrink("cache:thumbs", 5).isDefined)
    assertEquals(StorageAdvice.shrink("originals", 5), None)
    assertEquals(StorageAdvice.shrink("plugins", 5), None)

  test("sizes: at least for a capped walk, a dash for nothing measured, not there for a missing path"):
    assertEquals(Storage.size(e("a", Some("/a"), 2 * GB)), "2.0 GB")
    assertEquals(Storage.size(e("a", Some("/a"), 2 * GB).copy(partial = true)), "at least 2.0 GB")
    assertEquals(Storage.size(e("a", Some("/a"), 0).copy(bytes = None)), "—")
    assertEquals(Storage.size(e("a", Some("/a"), 0).copy(bytes = None, exists = false)), "not there")

  test("the CLI's own locations join the report; one the server reports already is not listed twice"):
    val server = StorageReport(
      entries = List(e("install", Some("/h"), 10 * GB)),
      volumes = List(StorageVolume(vol, 500 * GB, 400 * GB))
    )
    val local = List(e("install", Some("/h"), 10 * GB), e("calibre-originals", Some("/o"), 2 * GB))
    val merged = Storage.addLocal(
      server,
      local,
      List(StorageVolume(vol, 500 * GB, 400 * GB), StorageVolume("/usb", 10 * GB, 1 * GB))
    )
    assertEquals(merged.entries.map(x => x.key -> x.host), List("install" -> "server", "calibre-originals" -> "cli"))
    assertEquals(merged.volumes.map(_.id), List(vol, "/usb"))
    assertEquals(Storage.addLocal(server, local.take(1), Nil), server)

  private val report = StorageReport(
    measuredAt = Some("2026-10-19T10:00:00Z"),
    tookMillis = Some(420),
    ageSeconds = Some(12),
    entries = Storage.nest(
      List(
        StorageEntry(
          "database",
          "database (Postgres)",
          "database",
          None,
          Some(9 * GB),
          within = Some("database-cluster"),
          detail = Some("histmersion")
        ),
        StorageEntry(
          "database-cluster",
          "database files (bundled Postgres)",
          "database",
          Some("/d/pg"),
          Some(10 * GB),
          Some(3000L),
          volume = Some(vol)
        ),
        StorageEntry(
          "backups",
          "backups",
          "backups",
          Some("/d/backups"),
          Some(28 * GB),
          Some(5L),
          volume = Some(vol),
          shrink = StorageAdvice.shrink("backups", 5)
        ),
        StorageEntry("toolchain", "GraalVM toolchain", "install", Some("/d/toolchain"), None, None, exists = false),
        StorageEntry(
          "basemap",
          "terrain base map (cache)",
          "caches",
          Some("/c/basemap"),
          Some(600_000_000L),
          Some(3L),
          partial = true,
          detail = Some("vector 410.0 MB · terrain 190.0 MB"),
          volume = Some(vol)
        )
      )
    ),
    tables = List(TableSize("sources", 5 * GB), TableSize("assets", 2 * GB)),
    volumes = List(StorageVolume(vol, 994 * GB, 120 * GB)),
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
    val low = Storage.withHints(report.copy(volumes = List(StorageVolume(vol, 994 * GB, 5 * GB))))
    val lmd = StorageMarkdown.render(low).mkString("\n")
    assert(lmd.contains("5.0 GB free of 994.0 GB — LOW"), lmd)
    assert(lmd.contains("- Low disk space on"), lmd)

  test("histmersion about: measuring, and nothing at all from an older server"):
    assertEquals(StorageMarkdown.render(StorageReport()), Nil)
    assertEquals(
      StorageMarkdown.render(StorageReport(measuring = true)),
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
    assertEquals(about.asJson.mapObject(_.remove("storage")).as[About].map(_.storage), Right(StorageReport()))
    assertEquals(about.asJson.as[About].map(_.storage), Right(report))
