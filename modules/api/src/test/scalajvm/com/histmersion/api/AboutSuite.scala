package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, CorpusCounts, Health}
import com.histmersion.api.jobs.{JobCounts, JobState}
import com.histmersion.api.library.CorrespondingSource
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.backup.Backup
class AboutSuite extends munit.FunSuite:

  test("#718: a converter text maps to ok / busy / down, and wording never seen is unknown, not an error"):
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("ok"))), ServiceState.Ok)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire(" OK "))), ServiceState.Ok)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("busy: 2 jobs converting"))), ServiceState.Busy)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("converter busy"))), ServiceState.Busy)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("degraded: no answer from the converter within 2000 ms"))), ServiceState.Down)
    assertEquals(
      ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("converter at http://x is unreachable (ConnectException: refused)"))),
      ServiceState.Down
    )
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("error: relation does not exist"))), ServiceState.Down)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("warming up"))), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("unknown"))), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some(com.histmersion.api.ServiceState.Wire("  "))), ServiceState.Unknown)
    assertEquals(ServiceState.of(None), ServiceState.Unknown)

  test("job counts fold the status rows; a status that is no job state is dropped"):
    val c = JobCounts.of(
      List(
        JobState.Wire("queued") -> JobCounts.Count(2),
        JobState.Wire("running") -> JobCounts.Count(1),
        JobState.Wire("done") -> JobCounts.Count(40),
        JobState.Wire("failed") -> JobCounts.Count(3),
        JobState.Wire("weird") -> JobCounts.Count(9),
        JobState.Wire("paused") -> JobCounts.Count(1),
        JobState.Wire("cancelled") -> JobCounts.Count(4)
      )
    )
    assertEquals(c, JobCounts(com.histmersion.api.jobs.JobCounts.Queued(2), com.histmersion.api.jobs.JobCounts.Running(1), com.histmersion.api.jobs.JobCounts.Done(40), com.histmersion.api.jobs.JobCounts.Failed(3), com.histmersion.api.jobs.JobCounts.Paused(1), com.histmersion.api.jobs.JobCounts.Cancelled(4)))
    assertEquals(JobState.values.toList.map(c.of), List(2, 1, 40, 3, 1, 4))
    assertEquals(JobCounts.of(Nil), JobCounts())

  test("ages are coarse and never negative"):
    assertEquals(AboutMarkdown.age(-5), "0 s")
    assertEquals(AboutMarkdown.age(59), "59 s")
    assertEquals(AboutMarkdown.age(600), "10 min")
    assertEquals(AboutMarkdown.age(7300), "2 h")
    assertEquals(AboutMarkdown.age(3 * 86400 + 5), "3 d")

  private val about = About(
    name = com.histmersion.api.instance.About.Name("histmersion"),
    build = BuildStamp(com.histmersion.api.instance.BuildStamp.Version("1.2.3"), Some(com.histmersion.api.instance.BuildStamp.GitCommit("abc123def456")), Some(com.histmersion.api.instance.BuildStamp.CommitDate("2026-10-01T10:00:00+02:00")), dirty = com.histmersion.api.instance.BuildStamp.Dirty(true)),
    health = Health(com.histmersion.api.instance.Health.Status("degraded"), com.histmersion.api.instance.Health.Db("ok"), Some(com.histmersion.api.instance.Health.SchemaVersion("28")), Some(com.histmersion.api.instance.Health.Converter("busy: 1 job"))),
    jobs = JobCounts(com.histmersion.api.jobs.JobCounts.Queued(1), com.histmersion.api.jobs.JobCounts.Running(2), com.histmersion.api.jobs.JobCounts.Done(3), com.histmersion.api.jobs.JobCounts.Failed(4)),
    counts = Some(CorpusCounts(com.histmersion.api.instance.CorpusCounts.Sources(10), com.histmersion.api.instance.CorpusCounts.Findings(200), com.histmersion.api.instance.CorpusCounts.Articles(5), com.histmersion.api.instance.CorpusCounts.Events(7), com.histmersion.api.instance.CorpusCounts.Places(15000), com.histmersion.api.instance.CorpusCounts.Periods(40), com.histmersion.api.instance.CorpusCounts.Presets(12))),
    lastBackup = Some(Backup.Info(com.histmersion.api.backup.Backup.Info.File("histmersion-20261001.dump"), ByteCount(2_500_000), com.histmersion.api.backup.Backup.Info.CreatedAt("2026-10-01T08:00:00Z"))),
    lastBackupAgeSeconds = Some(com.histmersion.api.instance.About.LastBackupAgeSeconds(7200)),
    problems = List(com.histmersion.api.instance.About.Problems("backups: cannot list /x"))
  )

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
    val bare = AboutMarkdown.render(
      about.modify(
        _.build).setTo(BuildStamp(BuildStamp.Version("1.2.3"))).modify(_.counts).setTo(None).modify(_.lastBackup).setTo(None).modify(_.lastBackupAgeSeconds).setTo(None).modify(_.problems).setTo(Nil).modify(_.health).setTo(Health(Health.Status("ok"), Health.Db("ok"), None, Some(Health.Converter("ok")))
      )
    )
    assert(bare.contains("- commit: unknown"), bare)
    assert(bare.contains("- converter: ok\n"), bare)
    assert(bare.contains("- last backup: none"), bare)
    assert(!bare.contains("corpus:") && !bare.contains("Problems"), bare)

  test("the source link points at the running commit, or at the repository when the commit is unknown"):
    val repo = CorrespondingSource.Repository("https://github.com/histmersion/histmersion")
    assertEquals(CorrespondingSource.url(repo, Some(com.histmersion.api.library.CorrespondingSource.Commit("abc123def456"))), s"$repo/tree/abc123def456")
    assertEquals(CorrespondingSource.url(CorrespondingSource.Repository(s"$repo/"), Some(com.histmersion.api.library.CorrespondingSource.Commit(" abc123 "))), s"$repo/tree/abc123")
    assertEquals(CorrespondingSource.url(CorrespondingSource.Repository(s"$repo.git"), Some(com.histmersion.api.library.CorrespondingSource.Commit("abc123"))), s"$repo/tree/abc123")
    assertEquals(CorrespondingSource.url(repo, None), repo)
    assertEquals(CorrespondingSource.url(repo, Some(com.histmersion.api.library.CorrespondingSource.Commit(""))), repo)

  test("histmersion about names the licence and links the source; an older server says unknown"):
    val src = about.modify(_.build).setTo(about.build.modify(_.licence).setTo(Some(BuildStamp.Licence("AGPL-3.0-or-later"))).modify(_.sourceUrl).setTo(Some(BuildStamp.SourceUrl("https://example.org/h/tree/abc123def456")))
    )
    val md = AboutMarkdown.render(src)
    assert(md.contains("- licence: AGPL-3.0-or-later\n- source: https://example.org/h/tree/abc123def456"), md)
    val old = AboutMarkdown.render(about)
    assert(old.contains("- licence: unknown\n- source: unknown"), old)

  test("histmersion about says which config file the server read"):
    val read = AboutMarkdown.render(about.modify(_.configFile).setTo(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf")), loaded = ConfigFileInfo.Loaded(true))))
    assert(read.contains("- config file: /c/histmersion.conf (read)"), read)
    val none = AboutMarkdown.render(about.modify(_.configFile).setTo(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf")))))
    assert(none.contains("- config file: /c/histmersion.conf (not found: defaults and environment only)"), none)

  test("an About without configFile (an older server) still decodes"):
    import io.circe.syntax.*
    val json = about.asJson.mapObject(_.remove("configFile"))
    assertEquals(json.as[About].map(_.configFile), Right(ConfigFileInfo()))

  test("every count agrees with its noun: 1 article, not 1 articles"):
    val one = AboutMarkdown.render(
      about.modify(_.counts).setTo(about.counts.map(
          _.modify(_.sources).setTo(CorpusCounts.Sources(1)).modify(_.findings).setTo(CorpusCounts.Findings(0)).modify(_.articles).setTo(CorpusCounts.Articles(1)).modify(_.events).setTo(CorpusCounts.Events(1)).modify(_.places).setTo(CorpusCounts.Places(1)).modify(_.periods).setTo(CorpusCounts.Periods(1)).modify(_.presets).setTo(CorpusCounts.Presets(1))
        )
      )
    )
    assert(one.contains("- corpus: 1 source · 0 findings · 1 article · 1 event"), one)
    assert(one.contains("- reference data: 1 place · 1 period · 1 preset"), one)
    assertEquals(Plural.count(com.histmersion.api.Plural.CountN(2), com.histmersion.api.Plural.CountSingular("entry"), com.histmersion.api.Plural.CountPlural("entries")), "2 entries")
    assertEquals(Plural.count(com.histmersion.api.Plural.CountN(1), com.histmersion.api.Plural.CountSingular("entry"), com.histmersion.api.Plural.CountPlural("entries")), "1 entry")

  test("About renders the config file line"):
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo(Some(com.histmersion.api.settings.ConfigFileInfo.Path("/c/h.conf")), loaded = com.histmersion.api.settings.ConfigFileInfo.Loaded(true))), "/c/h.conf (read)")
    assertEquals(
      ConfigFileInfo.describe(ConfigFileInfo(Some(com.histmersion.api.settings.ConfigFileInfo.Path("/c/h.conf")))),
      "/c/h.conf (not found: defaults and environment only)"
    )
    assertEquals(
      ConfigFileInfo.describe(ConfigFileInfo(Some(com.histmersion.api.settings.ConfigFileInfo.Path("/c/h.conf")), loaded = com.histmersion.api.settings.ConfigFileInfo.Loaded(true), List(com.histmersion.api.settings.ConfigFileInfo.UnknownKeys("histmersion.x")))),
      "/c/h.conf (read; ignored unknown key: histmersion.x)"
    )
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo()), "none (no location: HOME is unset)")
