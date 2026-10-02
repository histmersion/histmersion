package com.histmersion.core

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
    counts = Some(CorpusCounts(10, 200, 5, 7, 15000, 40)),
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
    assert(md.contains("- reference data: 15000 places · 40 period presets"), md)
    assert(md.contains("Problems:\n- backups: cannot list /x"), md)
    val bare = AboutMarkdown.render(about.copy(build = BuildStamp("1.2.3"), counts = None, lastBackup = None,
      lastBackupAgeSeconds = None, problems = Nil, health = Health("ok", "ok", None, Some("ok"))))
    assert(bare.contains("- commit: unknown"), bare)
    assert(bare.contains("- converter: ok\n"), bare)
    assert(bare.contains("- last backup: none"), bare)
    assert(!bare.contains("corpus:") && !bare.contains("Problems"), bare)
