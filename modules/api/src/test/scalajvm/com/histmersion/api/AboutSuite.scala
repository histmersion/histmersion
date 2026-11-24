package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, CorpusCounts, Health}
import com.histmersion.api.jobs.{JobCounts, JobState}
import com.histmersion.api.library.CorrespondingSource
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.backup.Backup
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AboutSuite extends munit.FunSuite:

  test("#718: a converter text maps to ok / busy / down, and wording never seen is unknown, not an error"):
    assertEquals(ServiceState.of(Some(ServiceState.Wire("ok"))), ServiceState.Ok)
    assertEquals(ServiceState.of(Some(ServiceState.Wire(" OK "))), ServiceState.Ok)
    assertEquals(ServiceState.of(Some(ServiceState.Wire("busy: 2 jobs converting"))), ServiceState.Busy)
    assertEquals(ServiceState.of(Some(ServiceState.Wire("converter busy"))), ServiceState.Busy)
    assertEquals(
      ServiceState.of(Some(ServiceState.Wire("degraded: no answer from the converter within 2000 ms"))),
      ServiceState.Down
    )
    assertEquals(
      ServiceState.of(Some(ServiceState.Wire("converter at http://x is unreachable (ConnectException: refused)"))),
      ServiceState.Down
    )
    assertEquals(ServiceState.of(Some(ServiceState.Wire("error: relation does not exist"))), ServiceState.Down)
    assertEquals(ServiceState.of(Some(ServiceState.Wire("warming up"))), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some(ServiceState.Wire("unknown"))), ServiceState.Unknown)
    assertEquals(ServiceState.of(Some(ServiceState.Wire("  "))), ServiceState.Unknown)
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
    assertEquals(
      c,
      JobCounts(
        JobCounts.Queued(2),
        JobCounts.Running(1),
        JobCounts.Done(40),
        JobCounts.Failed(3),
        JobCounts.Paused(1),
        JobCounts.Cancelled(4)
      )
    )
    assertEquals(JobState.values.toList.map(c.of), List(2, 1, 40, 3, 1, 4))
    assertEquals(JobCounts.of(Nil), JobCounts())

  test("ages are coarse and never negative"):
    assertEquals(AboutMarkdown.age(-5), "0 s")
    assertEquals(AboutMarkdown.age(59), "59 s")
    assertEquals(AboutMarkdown.age(600), "10 min")
    assertEquals(AboutMarkdown.age(7300), "2 h")
    assertEquals(AboutMarkdown.age(3 * 86400 + 5), "3 d")

  private val about = About(
    name = About.Name("histmersion"),
    build = BuildStamp(
      BuildStamp.Version("1.2.3"),
      Some(BuildStamp.GitCommit("abc123def456")),
      Some(BuildStamp.CommitDate("2026-10-01T10:00:00+02:00")),
      dirty = BuildStamp.Dirty(true)
    ),
    health = Health(
      Health.Status("degraded"),
      Health.Db("ok"),
      Some(Health.SchemaVersion("28")),
      Some(Health.Converter("busy: 1 job"))
    ),
    jobs = JobCounts(JobCounts.Queued(1), JobCounts.Running(2), JobCounts.Done(3), JobCounts.Failed(4)),
    counts = Some(
      CorpusCounts(
        CorpusCounts.Sources(10),
        CorpusCounts.Findings(200),
        CorpusCounts.Articles(5),
        CorpusCounts.Events(7),
        CorpusCounts.Places(15000),
        CorpusCounts.Periods(40),
        CorpusCounts.Presets(12)
      )
    ),
    lastBackup = Some(
      Backup.Info(
        Backup.Info.File("histmersion-20261001.dump"),
        ByteCount(2_500_000),
        Backup.Info.CreatedAt("2026-10-01T08:00:00Z")
      )
    ),
    lastBackupAgeSeconds = Some(About.LastBackupAgeSeconds(7200)),
    problems = List(About.Problems("backups: cannot list /x"))
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
      about
        .modify(_.build)
        .setTo(BuildStamp(BuildStamp.Version("1.2.3")))
        .modify(_.counts)
        .setTo(None)
        .modify(_.lastBackup)
        .setTo(None)
        .modify(_.lastBackupAgeSeconds)
        .setTo(None)
        .modify(_.problems)
        .setTo(Nil)
        .modify(_.health)
        .setTo(Health(Health.Status("ok"), Health.Db("ok"), None, Some(Health.Converter("ok"))))
    )
    assert(bare.contains("- commit: unknown"), bare)
    assert(bare.contains("- converter: ok\n"), bare)
    assert(bare.contains("- last backup: none"), bare)
    assert(!bare.contains("corpus:") && !bare.contains("Problems"), bare)

  test("the source link points at the running commit, or at the repository when the commit is unknown"):
    val repo = CorrespondingSource.Repository("https://github.com/histmersion/histmersion")
    assertEquals(
      CorrespondingSource.url(repo, Some(CorrespondingSource.Commit("abc123def456"))),
      s"$repo/tree/abc123def456"
    )
    assertEquals(
      CorrespondingSource.url(CorrespondingSource.Repository(s"$repo/"), Some(CorrespondingSource.Commit(" abc123 "))),
      s"$repo/tree/abc123"
    )
    assertEquals(
      CorrespondingSource.url(CorrespondingSource.Repository(s"$repo.git"), Some(CorrespondingSource.Commit("abc123"))),
      s"$repo/tree/abc123"
    )
    assertEquals(CorrespondingSource.url(repo, None).value, repo.value)
    assertEquals(CorrespondingSource.url(repo, Some(CorrespondingSource.Commit(""))).value, repo.value)

  test("histmersion about names the licence and links the source; an older server says unknown"):
    val src = about
      .modify(_.build)
      .setTo(
        about.build
          .modify(_.licence)
          .setTo(Some(BuildStamp.Licence("AGPL-3.0-or-later")))
          .modify(_.sourceUrl)
          .setTo(Some(BuildStamp.SourceUrl("https://example.org/h/tree/abc123def456")))
      )
    val md = AboutMarkdown.render(src)
    assert(md.contains("- licence: AGPL-3.0-or-later\n- source: https://example.org/h/tree/abc123def456"), md)
    val old = AboutMarkdown.render(about)
    assert(old.contains("- licence: unknown\n- source: unknown"), old)

  test("histmersion about says which config file the server read"):
    val read = AboutMarkdown.render(
      about
        .modify(_.configFile)
        .setTo(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf")), loaded = ConfigFileInfo.Loaded(true)))
    )
    assert(read.contains("- config file: /c/histmersion.conf (read)"), read)
    val none = AboutMarkdown.render(
      about.modify(_.configFile).setTo(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf"))))
    )
    assert(none.contains("- config file: /c/histmersion.conf (not found: defaults and environment only)"), none)

  test("an About without configFile (an older server) still decodes"):
    import com.histmersion.util.json.RawJson.*
    val json = about.asJson.mapObject(_.remove("configFile"))
    assertEquals(json.as[About].map(_.configFile), Right(ConfigFileInfo()))

  test("every count agrees with its noun: 1 article, not 1 articles"):
    val one = AboutMarkdown.render(
      about
        .modify(_.counts)
        .setTo(
          about.counts.map(
            _.modify(_.sources)
              .setTo(CorpusCounts.Sources(1))
              .modify(_.findings)
              .setTo(CorpusCounts.Findings(0))
              .modify(_.articles)
              .setTo(CorpusCounts.Articles(1))
              .modify(_.events)
              .setTo(CorpusCounts.Events(1))
              .modify(_.places)
              .setTo(CorpusCounts.Places(1))
              .modify(_.periods)
              .setTo(CorpusCounts.Periods(1))
              .modify(_.presets)
              .setTo(CorpusCounts.Presets(1))
          )
        )
    )
    assert(one.contains("- corpus: 1 source · 0 findings · 1 article · 1 event"), one)
    assert(one.contains("- reference data: 1 place · 1 period · 1 preset"), one)
    assertEquals(
      Plural.count(Plural.CountN(2), Plural.CountSingular("entry"), Plural.CountPlural("entries")),
      "2 entries"
    )
    assertEquals(
      Plural.count(Plural.CountN(1), Plural.CountSingular("entry"), Plural.CountPlural("entries")),
      "1 entry"
    )

  test("About renders the config file line"):
    assertEquals(
      ConfigFileInfo.describe(
        ConfigFileInfo(Some(ConfigFileInfo.Path("/c/h.conf")), loaded = ConfigFileInfo.Loaded(true))
      ),
      "/c/h.conf (read)"
    )
    assertEquals(
      ConfigFileInfo.describe(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/h.conf")))),
      "/c/h.conf (not found: defaults and environment only)"
    )
    assertEquals(
      ConfigFileInfo.describe(
        ConfigFileInfo(
          Some(ConfigFileInfo.Path("/c/h.conf")),
          loaded = ConfigFileInfo.Loaded(true),
          List(ConfigFileInfo.UnknownKeys("histmersion.x"))
        )
      ),
      "/c/h.conf (read; ignored unknown key: histmersion.x)"
    )
    assertEquals(ConfigFileInfo.describe(ConfigFileInfo()), "none (no location: HOME is unset)")
