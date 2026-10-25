package com.histmersion.api


class AboutSuite extends munit.FunSuite:

  test("#718: a converter text maps to ok / busy / down, and wording never seen is unknown, not an error"):
    assertEquals(ServiceState.of(Some("ok")), ServiceState.Ok)
    assertEquals(ServiceState.of(Some(" OK ")), ServiceState.Ok)
    assertEquals(ServiceState.of(Some("busy: 2 jobs converting")), ServiceState.Busy)
    assertEquals(ServiceState.of(Some("converter busy")), ServiceState.Busy)
    assertEquals(ServiceState.of(Some("degraded: no answer from the converter within 2000 ms")), ServiceState.Down)
    assertEquals(ServiceState.of(Some("converter at http://x is unreachable (ConnectException: refused)")), ServiceState.Down)
    assertEquals(ServiceState.of(Some("error: relation does not exist")), ServiceState.Down)
    assertEquals(ServiceState.of(Some("warming up")), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some("unknown")), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some("  ")), ServiceState.Unknown)
    assertEquals(ServiceState.of(None), ServiceState.Unknown)

  test("job counts fold the status rows; a status that is no job state is dropped"):
    val c = JobCounts.of(List("queued" -> 2, "running" -> 1, "done" -> 40, "failed" -> 3, "weird" -> 9))
    assertEquals(c, JobCounts(2, 1, 40, 3))
    assertEquals(JobState.values.toList.map(c.of), List(2, 1, 40, 3))
    assertEquals(JobCounts.of(Nil), JobCounts())

  test("ages are coarse and never negative"):
    assertEquals(AboutMarkdown.age(-5), "0 s")
    assertEquals(AboutMarkdown.age(59), "59 s")
    assertEquals(AboutMarkdown.age(600), "10 min")
    assertEquals(AboutMarkdown.age(7300), "2 h")
    assertEquals(AboutMarkdown.age(3 * 86400 + 5), "3 d")

  private val about = About(
    name = "histmersion",
    build = BuildStamp("1.2.3", Some("abc123def456"), Some("2026-10-01T10:00:00+02:00"), dirty = true),
    health = Health("degraded", "ok", Some("28"), Some("busy: 1 job")),
    jobs = JobCounts(1, 2, 3, 4),
    counts = Some(CorpusCounts(10, 200, 5, 7, 15000, 40, 12)),
    lastBackup = Some(BackupInfo("histmersion-20261001.dump", 2_500_000, "2026-10-01T08:00:00Z")),
    lastBackupAgeSeconds = Some(7200),
    problems = List("backups: cannot list /x"))

  test("histmersion about renders every part, and says what could not be read"):
    val md = AboutMarkdown.render(about)
    assert(md.startsWith("# histmersion 1.2.3"), md)
    assert(md.contains("- commit: abc123def456 (with uncommitted changes)"), md)
    assert(md.contains("- schema version: 28"), md)
    assert(md.contains("- converter: busy (busy: 1 job)"), md)
    assert(md.contains("- jobs: 1 queued · 2 running · 3 done · 4 failed"), md)
    assert(md.contains("- last backup: histmersion-20261001.dump · 2.5 MB · 2 h ago"), md)
    assert(md.contains("- corpus: 10 sources · 200 findings · 5 articles · 7 events"), md)
    assert(md.contains("- reference data: 15000 places · 40 periods · 12 presets"), md)
    assert(md.contains("Problems:\n- backups: cannot list /x"), md)
    val bare = AboutMarkdown.render(about.copy(build = BuildStamp("1.2.3"), counts = None, lastBackup = None,
      lastBackupAgeSeconds = None, problems = Nil, health = Health("ok", "ok", None, Some("ok"))))
    assert(bare.contains("- commit: unknown"), bare)
    assert(bare.contains("- converter: ok\n"), bare)
    assert(bare.contains("- last backup: none"), bare)
    assert(!bare.contains("corpus:") && !bare.contains("Problems"), bare)

  test("the source link points at the running commit, or at the repository when the commit is unknown"):
    val repo = "https://github.com/histmersion/histmersion"
    assertEquals(CorrespondingSource.url(repo, Some("abc123def456")), s"$repo/tree/abc123def456")
    assertEquals(CorrespondingSource.url(s"$repo/", Some(" abc123 ")), s"$repo/tree/abc123")
    assertEquals(CorrespondingSource.url(s"$repo.git", Some("abc123")), s"$repo/tree/abc123")
    assertEquals(CorrespondingSource.url(repo, None), repo)
    assertEquals(CorrespondingSource.url(repo, Some("")), repo)

  test("histmersion about names the licence and links the source; an older server says unknown"):
    val src = about.copy(build = about.build.copy(licence = Some("AGPL-3.0-or-later"),
      sourceUrl = Some("https://example.org/h/tree/abc123def456")))
    val md = AboutMarkdown.render(src)
    assert(md.contains("- licence: AGPL-3.0-or-later\n- source: https://example.org/h/tree/abc123def456"), md)
    val old = AboutMarkdown.render(about)
    assert(old.contains("- licence: unknown\n- source: unknown"), old)

  test("histmersion about says which config file the server read"):
    val read = AboutMarkdown.render(about.copy(configFile = ConfigFileInfo(Some("/c/histmersion.conf"), loaded = true)))
    assert(read.contains("- config file: /c/histmersion.conf (read)"), read)
    val none = AboutMarkdown.render(about.copy(configFile = ConfigFileInfo(Some("/c/histmersion.conf"))))
    assert(none.contains("- config file: /c/histmersion.conf (not found: defaults and environment only)"), none)

  test("an About without configFile (an older server) still decodes"):
    import io.circe.syntax.*
    val json = about.asJson.mapObject(_.remove("configFile"))
    assertEquals(json.as[About].map(_.configFile), Right(ConfigFileInfo()))

  test("every count agrees with its noun: 1 article, not 1 articles"):
    val one = AboutMarkdown.render(about.copy(counts = about.counts.map(_.copy(sources = 1, findings = 0,
      articles = 1, events = 1, places = 1, periods = 1, presets = 1))))
    assert(one.contains("- corpus: 1 source · 0 findings · 1 article · 1 event"), one)
    assert(one.contains("- reference data: 1 place · 1 period · 1 preset"), one)
    assertEquals(Plural.count(2, "entry", "entries"), "2 entries")
    assertEquals(Plural.count(1, "entry", "entries"), "1 entry")

  test("About renders the config file line"):
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"), loaded = true)), "/c/h.conf (read)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"))),
      "/c/h.conf (not found: defaults and environment only)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some("/c/h.conf"), loaded = true, List("histmersion.x"))),
      "/c/h.conf (read; ignored unknown key: histmersion.x)")
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo()), "none (no location: HOME is unset)")
