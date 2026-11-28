package com.histmersion
package db
import com.histmersion.util.problem.Text.sentences

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.worklists.QueueRows

import com.histmersion.util.database.Transactor

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{GeoPoint, RegisteredPlanMatches, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.data.exchange.TestExchange
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.api.library.Confidence
import com.histmersion.api.exchange.MergeDecision.Kind as DecisionKind
import io.scalaland.chimney.dsl.*
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.data.exchange.{BundleExport, BundleScope}
import com.histmersion.domain.automation.goals.GoalRepo
import com.histmersion.util.data.exchange.{BundleImport, BundleRepo, ImportOptions, MergeState}
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Queue
import com.histmersion.api.catalogue.{EventPut, Period}
import com.histmersion.api.exchange.{
  MatchCounts,
  MergeApplyResult,
  MergeIssue,
  MergePlan,
  MergeResolution,
  MergeResolutions
}
import com.histmersion.api.library.{Edge, Finding, Source}
import com.histmersion.api.research.Article
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeIssue as Issue
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.util.storage.ContentDirectory
import com.histmersion.util.data.exchange.BundleImporter
import com.histmersion.util.storage.ByteSource
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.domain.library.sources.Citekeys
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.util.storage.ContentStore
import com.histmersion.util.data.exchange.MergeRow
import com.histmersion.domain.research.Slug
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** `merge plan` against real Postgres (docs/research-merge-import.md §8, milestone M3): the crafted pair of
  * collections, "work" and "home", built through the repositories in two databases of their own; work's bundle planned
  * in home must give EXACTLY the expected issue list in one run. Then the import's refusal, the id map (`uid_aliases`)
  * and the ISBN key. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MergeSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val merge = cfg.merge.transformInto[MergeSettings]
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val admin = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private lazy val chunkChars = cfg.limits.chunkChars
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val importChunkChars = ImportOptions.ChunkChars(chunkChars.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val importToleranceKm = ImportOptions.ToleranceKm(cfg.places.pointToleranceKm.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sha256Of(bundle: Path): Sha256 =
    Sha256.assume(BundleFormat.sha256Hex(Files.readAllBytes(bundle)).value)
  private val tmp = Files.createTempDirectory("histmersion-merge-suite")

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try {
      val st = c.createStatement();
      try { val _ = st.execute(stmt) }
      finally st.close()
    } finally c.close()

  private def fresh(n: String): DataSource =
    val ds = scratch(n)
    ds

  // every database a test makes: a copy of one migrated once (#3765)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val scratch = ScratchDatabases("histmersion_merge", cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  private def exportOf(ds: DataSource, originals: Path): Path =
    val out = java.io.ByteArrayOutputStream()
    BundleExport
      .write(ds, out, None, ContentDirectory(originals), TestExchange.kinds)
      .fold(e => fail(e), identity)
    val f = Files.createTempFile(tmp, "bundle-", ".hmx")
    Files.write(f, out.toByteArray)

  private def planIn(
      ds: DataSource,
      bundle: Path,
      originals: Path,
      res: MergeResolutions = MergeResolutions()
  ): MergePlan =
    MergeState
      .planFile(
        ds,
        bundle,
        res,
        merge,
        ContentDirectory(originals),
        importChunkChars,
        TestExchange.kinds,
        TestExchange.rules
      )
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  private def importInto(
      ds: DataSource,
      bundle: Path,
      originals: Path,
      res: MergeResolutions = MergeResolutions(),
      dryRun: ImportOptions.DryRun = ImportOptions.DryRun(false)
  ): Bundle.ImportResult =
    BundleImporter
      .run(
        ds,
        ByteSource.file(bundle),
        sha256Of(bundle),
        BundleImport.Options(dryRun, importChunkChars, importToleranceKm),
        ContentDirectory(originals),
        res,
        merge,
        TestExchange.kinds,
        TestExchange.rules,
        TestCreators.clock
      )
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def count(ds: DataSource, q: String): Int = connect(ds)(sql"${SqlLiteral(q)}".query[Int].run().head)

  // ── the crafted pair ───────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def src(
      ck: String,
      title: String,
      authors: List[String],
      year: Int,
      doi: Option[String] = None,
      body: Option[Source.OcrMarkdown] = None,
      status: Source.Status = Source.Status.Current,
      notes: Option[Source.Notes] = None,
      venue: Option[String] = None,
      summary: Option[Source.Summary] = None,
      findings: List[(Finding.Claim, List[Finding.Tags])] = Nil,
      edges: List[(Citekey, Edge.Kind)] = Nil
  ): Source =
    Source(
      Citekey(ck),
      api.library.Title(title),
      (authors).map(api.library.AuthorName.apply),
      (doi).map(Doi.apply),
      (Some(year)).map(Year.apply),
      (venue).map(Source.Venue.apply),
      status,
      Nil,
      Nil,
      Nil,
      None,
      summary,
      None,
      None,
      None,
      None,
      ocrMarkdown = body,
      notes = notes,
      findings = findings.zipWithIndex.map { case ((claim, tags), i) =>
        Finding(
          Finding.Id(s"$ck#${i + 1}"),
          Citekey(ck),
          claim,
          Some(Finding.Region("Anatolia")),
          Some(GeoPoint(Longitude(34.6), Latitude(40.0))),
          Some(Period(Year(-1400), Year(-1200))),
          Some(Finding.PeriodName("LBA")),
          tags,
          Some(Confidence.High)
        )
      },
      edges = edges.map((dst, kind) => Edge(Citekey(ck), dst, kind))
    )

  private def png(n: Byte): Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10, n, n, n)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def asset(name: NewFigure.Name, n: Byte) =
    NewFigure(name, NewFigure.Mime("image/png"), NewFigure.Bytes(png(n)), None, None)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def attest(
      ds: DataSource,
      ck: Citekey,
      quote: Attestation.Quote,
      target: Attestation.TargetId
  ): Unit =
    val _ = AttestationRows.create(
      ds,
      Attestation.Put(
        ck,
        Attestation.TargetType("place"),
        target,
        quote = Some(quote),
        charStart = Some(Attestation.CharStart(0)),
        charEnd = Some(Attestation.CharEnd(quote.value.length))
      )
    )

  private def put(ds: DataSource, s: Source): Unit = SourceFixtures.upsert(ds, s, chunkChars)

  private val olsen: String =
    val rnd = scala.util.Random(2015)
    (1 to 3000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(7)).mkString).mkString(" ")
  private val olsenAgain: String =
    olsen.split(" ").zipWithIndex.map((w, i) => if i % 50 == 0 then w.toUpperCase.reverse else w).mkString(" ")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val kuhrtSha = Sha256.assume("9f2e" + "0" * 60)
  private val Isbn = "9780198150428"

  /** work, home and its spool; home has already imported work once (the shared history). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def craft(tag: String): (DataSource, DataSource, Path, Path) =
    val (work, home) = (fresh(s"${tag}w"), fresh(s"${tag}h"))
    val (sw, sh) = (spool(s"${tag}w"), spool(s"${tag}h"))
    Transactor(work).write(BundleRepo.setLabel(Some(InstanceInfo.Label("work"))))
    Transactor(home).write(BundleRepo.setLabel(Some(InstanceInfo.Label("home"))))
    // ── the shared history ──
    put(
      work,
      src("zzshared2001_alpha", "Alpha", List("Doe, John"), 2001, summary = Some(Source.Summary("as imported")))
    )
    transact(work)(
      GoalRepo.put(domain.automation.Goal.Slug("zz_shared_goal"), Goal.Put(Goal.Title("Shared goal")))
    )
    // two articles both sides will edit: in different places (a clean three-way merge), in one place
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("zz_shared_art"),
        Article.Title("Shared"),
        Article.KindValue("concept"),
        Article.Body("Intro.\n\nMiddle.\n\nEnd.")
      )
    )
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("zz_shared_conf"),
        Article.Title("Conflict"),
        Article.KindValue("concept"),
        Article.Body("One.\n\nTwo.\n\nThree.")
      )
    )
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied.applied, first.problems.mkString("\n"))
    // …then both sides go on
    val _ =
      SourceFixtures.patch(
        work,
        domain.library.Citekey("zzshared2001_alpha"),
        Source.Patch(summary = Some(Source.Summary("edited at work"))),
        chunkChars
      )
    val _ =
      SourceFixtures.patch(
        home,
        domain.library.Citekey("zzshared2001_alpha"),
        Source.Patch(summary = Some(Source.Summary("edited at home"))),
        chunkChars
      )
    val _ = Transactor(work).write(GoalRepo.deleteIn(domain.automation.Goal.Slug("zz_shared_goal")))
    val _ = Transactor(home).write(
      GoalRepo.patchIn(
        domain.automation.Goal.Slug("zz_shared_goal"),
        Goal.Patch(notes = Some(Goal.Notes("edited at home")))
      )
    )
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("zz_shared_art"),
        Article.Title("Shared"),
        Article.KindValue("concept"),
        Article.Body("Intro (work).\n\nMiddle.\n\nEnd.")
      )
    )
    val _ = ArticleFixtures.put(
      home,
      Article(
        Article.Slug("zz_shared_art"),
        Article.Title("Shared"),
        Article.KindValue("concept"),
        Article.Body("Intro.\n\nMiddle.\n\nEnd (home).")
      )
    )
    val _ =
      ArticleFixtures.put(
        work,
        Article(
          Article.Slug("zz_shared_conf"),
          Article.Title("Conflict"),
          Article.KindValue("concept"),
          Article.Body("One.\n\nTwo at work.\n\nThree.")
        )
      )
    val _ =
      ArticleFixtures.put(
        home,
        Article(
          Article.Slug("zz_shared_conf"),
          Article.Title("Conflict"),
          Article.KindValue("concept"),
          Article.Body("One.\n\nTwo at home.\n\nThree.")
        )
      )

    // ── work ──
    put(
      work,
      src(
        "guterbock1997_perspectives",
        "Perspectives on Hittite Civilization: Selected Writings",
        List("H. G. Güterbock", "Harry A. Hoffner"),
        1997,
        doi = Some("10.5555/oip26"),
        venue = Some("Oriental Institute"),
        notes = Some(Source.Notes("work's notes")),
        // the same claim as home's #1 (other word order), a near duplicate of it, and one home lacks
        findings = List(
          Finding.Claim("the storm god of Hatti is Tarhunna") -> List(Finding.Tags("work")),
          Finding.Claim("Tarhunna the storm god ruled Hatti and Arinna") -> Nil,
          Finding.Claim("Hattusa burned around 1180 BC") -> Nil
        ),
        edges =
          List(Citekey("zznew2020_only") -> Edge.Kind.References, Citekey("smith1900_unknown") -> Edge.Kind.References)
      )
    )
    // one figure as home's (the same bytes), one new, one whose name home uses for another figure
    val _ = AssetFixtures.add(
      work,
      domain.library.Citekey("guterbock1997_perspectives"),
      List(
        asset(NewFigure.Name("fig1.png"), 1),
        asset(NewFigure.Name("fig2.png"), 2),
        asset(NewFigure.Name("fig3.png"), 4)
      )
    )
    put(
      work,
      src(
        "kuhrt1995_near",
        "The Ancient Near East c. 3000-330 BC",
        List("Amélie Kuhrt"),
        1995,
        doi = Some("10.4324/9780203431566"),
        body = Some(Source.OcrMarkdown("Kuhrt body, work's OCR"))
      )
    )
    SourceFixtures.setSha256(work, domain.library.Citekey("kuhrt1995_near"), kuhrtSha)
    // one passage home's body has too (it moves), one only work's has (it cannot)
    attest(work, Citekey("kuhrt1995_near"), Attestation.Quote("Kuhrt body"), Attestation.TargetId("zz_found"))
    attest(work, Citekey("kuhrt1995_near"), Attestation.Quote("work's OCR"), Attestation.TargetId("zz_lost"))
    put(
      work,
      src(
        "bryce2005_kingdom",
        "The Kingdom of the Hittites",
        List("Trevor Bryce"),
        2006,
        doi = Some("10.1093/acprof:oso/9780199279081.001.0001"),
        status = Source.Status.Contested,
        edges = List(Citekey("guterbock1997_perspectives") -> Edge.Kind.References)
      )
    )
    put(
      work,
      src(
        "page2024_history",
        "History and the Homeric Iliad",
        List("Denys Page"),
        2024,
        venue = Some("Sather Classical Lectures")
      )
    )
    put(work, src("smith2019_ritual", "Ritual and Kingship in Early Mesopotamia", List("M. Smith"), 2019))
    put(
      work,
      src("latacz2023_troy", "Troy and Homer: Towards a Solution of an Old Mystery", List("Latacz, Joachim"), 2004)
    )
    put(
      work,
      src(
        "harrison1903_old",
        "Prolegomena to the Study of Greek Religion",
        List("Harrison, Jane Ellen, 1850-1928"),
        1903
      )
    )
    val _ = SourceFixtures.rename(
      work,
      domain.library.Citekey("harrison1903_old"),
      Citekeys.RenameToNewRaw("harrison1903_prolegomena")
    )
    put(
      work,
      src(
        "olsen2015_penelope",
        "Penelope's Weaving",
        List("Barbara Olsen"),
        2015,
        body = Some(Source.OcrMarkdown(olsen))
      )
    )
    put(work, src("zznew2020_only", "A Book Only Work Has", List("Roe, Richard"), 2020))
    put(work, src("west1997_east", "The East Face of Helicon", List("M. L. West"), 1997))
    exec(
      work,
      s"INSERT INTO source_identifiers (source_uid, scheme, value) SELECT uid, 'isbn', '$Isbn' FROM sources WHERE citekey = 'west1997_east'"
    )
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("bronze_age_collapse"),
        Article.Title("Bronze Age collapse"),
        Article.KindValue("concept"),
        Article.Body("Work's account of the collapse, citing [[kuhrt1995_near]].")
      )
    )
    val _ = ArticleFixtures.put(
      work,
      Article(
        Article.Slug("zz_same_text"),
        Article.Title("Same"),
        Article.KindValue("concept"),
        Article.Body("Identical on both sides.")
      )
    )
    EventFixtures.put(
      work,
      BEventRow.Id("zz_siege"),
      EventPut(
        EventSummary.Label("Siege of Troy (work)"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190)))
      )
    )
    // the same event (dated alike) created on both sides
    EventFixtures.put(
      work,
      BEventRow.Id("zz_ev_same"),
      EventPut(
        EventSummary.Label("Fall of Hattusa (work)"),
        EventSummary.Kind("destruction"),
        TimeSpan(Some(Year(-1190)), None, None, Some(Year(-1180)))
      )
    )
    transact(work)(
      GoalRepo.put(
        domain.automation.Goal.Slug("zz_goal_both"),
        Goal.Put(Goal.Title("Goal created at work"), status = Goal.StatusValue("seeding"))
      )
    )
    val _ = QueueRows.add(work, Queue.Kind.Tasks, Queue.QueueAdd(Queue.QueueItem.Text("Find the  Kuhrt reprint")))

    // ── home ──
    put(
      home,
      src(
        "guterbock1997_hittite",
        "Perspectives on Hittite civilization",
        List("Hoffner, Harry A.", "Güterbock, Hans G."),
        1997,
        venue = Some("Oriental Institute"),
        notes = Some(Source.Notes("home's notes")),
        findings = List(
          Finding.Claim("Tarhunna is the storm god of Hatti") -> List(Finding.Tags("home")),
          Finding.Claim("The storm god Tarhunna ruled Hatti") -> Nil
        ),
        edges = List(Citekey("page1959_history") -> Edge.Kind.References)
      )
    )
    val _ = AssetFixtures.add(
      home,
      domain.library.Citekey("guterbock1997_hittite"),
      List(asset(NewFigure.Name("fig1.png"), 1), asset(NewFigure.Name("fig3.png"), 3))
    )
    put(
      home,
      src(
        "kuhrt1995_ancient",
        "The Ancient Near East, c. 3000–330 BC",
        List("Kuhrt, Amelie"),
        1995,
        doi = Some("10.4324/9781315682334"),
        body = Some(Source.OcrMarkdown("Preface. Kuhrt body, home's OCR"))
      )
    )
    SourceFixtures.setSha256(home, domain.library.Citekey("kuhrt1995_ancient"), kuhrtSha)
    // home's own attestation: should the merge take work's body, it could not move
    attest(home, Citekey("kuhrt1995_ancient"), Attestation.Quote("home's OCR"), Attestation.TargetId("zz_home"))
    put(
      home,
      src(
        "bryce2005_kingdom_hittites",
        "The kingdom of the Hittites",
        List("Bryce, Trevor"),
        2005,
        doi = Some("https://doi.org/10.1093/acprof:oso/9780199279081.001.0001")
      )
    )
    put(
      home,
      src(
        "page1959_history",
        "History and the Homeric Iliad",
        List("Page, Denys"),
        1959,
        venue = Some("Sather Classical Lectures")
      )
    )
    put(home, src("smith2019_ritual", "Ritual Economies of the Aegean", List("Smith, J."), 2019))
    put(
      home,
      src("latacz2023_troy", "Troy and Homer: Towards a Solution of an Old Mystery", List("Joachim Latacz"), 2004)
    )
    val _ = SourceFixtures.rename(
      home,
      domain.library.Citekey("latacz2023_troy"),
      Citekeys.RenameToNewRaw("latacz2004_troy")
    )
    put(
      home,
      src("harrison1903_prolegomena_greek", "Prolegomena to the study of Greek religion", List("Jane Harrison"), 1903)
    )
    put(
      home,
      src("harrison1903_old", "Themis: A Study of the Social Origins of Greek Religion", List("Jane Harrison"), 1912)
    )
    val _ = SourceFixtures.rename(
      home,
      domain.library.Citekey("harrison1903_old"),
      Citekeys.RenameToNewRaw("harrison1912_themis")
    )
    put(
      home,
      src(
        "olsen2015_penelope_weaving",
        "Penelope’s weaving",
        List("Olsen, Barbara"),
        2015,
        body = Some(Source.OcrMarkdown(olsenAgain))
      )
    )
    put(home, src("west1997_face", "The East Face of Helicon", List("West, Martin L."), 1997))
    exec(
      home,
      s"INSERT INTO source_identifiers (source_uid, scheme, value) SELECT uid, 'isbn', '$Isbn' FROM sources WHERE citekey = 'west1997_face'"
    )
    val _ = ArticleFixtures.put(
      home,
      Article(
        Article.Slug("bronze_age_collapse"),
        Article.Title("Bronze Age collapse"),
        Article.KindValue("concept"),
        Article.Body("Home's account of the collapse.")
      )
    )
    val _ = ArticleFixtures.put(
      home,
      Article(
        Article.Slug("zz_same_text"),
        Article.Title("Same"),
        Article.KindValue("concept"),
        Article.Body("Identical on both sides.")
      )
    )
    EventFixtures.put(
      home,
      BEventRow.Id("zz_siege"),
      EventPut(
        EventSummary.Label("Siege of Troy (home)"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1210)), None, None, Some(Year(-1180)))
      )
    )
    EventFixtures.put(
      home,
      BEventRow.Id("zz_ev_same"),
      EventPut(
        EventSummary.Label("Fall of Hattusa (home)"),
        EventSummary.Kind("destruction"),
        TimeSpan(Some(Year(-1190)), None, None, Some(Year(-1180)))
      )
    )
    transact(home)(
      GoalRepo.put(
        domain.automation.Goal.Slug("zz_goal_both"),
        Goal.Put(Goal.Title("Goal created at home"), notes = Some(Goal.Notes("home's plan")))
      )
    )
    val task = QueueRows.add(home, Queue.Kind.Tasks, Queue.QueueAdd(Queue.QueueItem.Text("Find the Kuhrt reprint")))
    val _ = QueueRows.patch(home, task.id, Queue.QueuePatch(status = Some(Queue.QueueItem.Status(Queue.Done))))
    (work, home, sw, sh)

  /** kind, id letter, left label, right label, severity, conditional on. */
  private type Shape = (Issue.Kind, String, Option[String], Option[String], Issue.Severity, Option[DecisionKind])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def shape(p: MergePlan): Set[Shape] =
    p.issues
      .map(i =>
        (
          i.kind,
          i.id.value.takeWhile(_ != '-'),
          i.left.map(_.label.value),
          i.right.map(_.label.value),
          i.severity,
          i.condition.map(_.when)
        )
      )
      .toSet

  import com.histmersion.api.exchange.MergeIssue.Kind.*
  import com.histmersion.api.exchange.MergeIssue.Severity.*
  private val expected: Set[Shape] = Set(
    (NotesDiffers, "S", Some("guterbock1997_hittite"), Some("guterbock1997_perspectives"), Blocking, None),
    (DoiDiffers, "S", Some("kuhrt1995_ancient"), Some("kuhrt1995_near"), Blocking, None),
    (BodyDiffers, "O", Some("kuhrt1995_ancient"), Some("kuhrt1995_near"), Default, None),
    (StatusDiffers, "S", Some("bryce2005_kingdom_hittites"), Some("bryce2005_kingdom"), Blocking, None),
    (SameWork, "S", Some("page1959_history"), Some("page2024_history"), Blocking, None),
    (YearDiffers, "S", Some("page1959_history"), Some("page2024_history"), Blocking, Some(DecisionKind.Map)),
    (CitekeyCollision, "S", Some("smith2019_ritual"), Some("smith2019_ritual"), Blocking, None),
    (AliasConflict, "L", Some("harrison1912_themis"), Some("harrison1903_prolegomena"), Blocking, None),
    (BodyDiffers, "O", Some("olsen2015_penelope_weaving"), Some("olsen2015_penelope"), Default, None),
    (ChangedBothSides, "S", Some("zzshared2001_alpha"), Some("zzshared2001_alpha"), Blocking, None),
    (DeletedThereChangedHere, "G", Some("zz_shared_goal"), None, Blocking, None),
    (ArticleDiverged, "A", Some("bronze_age_collapse"), Some("bronze_age_collapse"), Blocking, None),
    (ArticleDiverged, "A", Some("zz_same_text"), Some("zz_same_text"), Default, None),
    (LabelTaken, "E", Some("zz_siege"), Some("zz_siege"), Blocking, None),
    // M4: inside the merged pairs, and the articles both sides edited
    (FindingNearDuplicate, "F", Some("guterbock1997_hittite#2"), Some("guterbock1997_perspectives#2"), Default, None),
    (AttestationUnanchored, "T", Some("kuhrt1995_ancient"), Some("place zz_lost"), Blocking, None),
    (AttestationUnanchored, "T", Some("place zz_home"), Some("kuhrt1995_near"), Blocking, Some(DecisionKind.KeepRight)),
    (ChangedBothSides, "A", Some("zz_shared_conf"), Some("zz_shared_conf"), Blocking, None)
  )

  test("the crafted pair: work's bundle planned at home gives exactly the expected issues, in one run"):
    val (work, home, sw, sh) = craft("fx")
    val bundle = exportOf(work, sw)
    val before = exportOf(home, sh)
    val p = planIn(home, bundle, sh)
    assertEquals(shape(p) -- expected, Set.empty, "unexpected issues")
    assertEquals(expected -- shape(p), Set.empty, "missing issues")
    assertEquals(p.issues.size, expected.size, p.issues.mkString("\n"))
    assertEquals(
      p.matches,
      MatchCounts(
        sources = MatchCounts.Sources(11),
        byUid = MatchCounts.ByUid(1),
        byStrongKey = MatchCounts.ByStrongKey(3),
        byScore = MatchCounts.ByScore(4),
        undecided = MatchCounts.Undecided(1),
        unmatched = MatchCounts.Unmatched(2)
      )
    )
    assertEquals(p.problems, Nil)
    assertEquals(p.from.label, Some("work"))
    assertEquals(p.into.label, Some("home"))
    // the ISBN and the near-identical OCR are what matched them
    val matched = RegisteredPlanMatches.kind.all(p.sources)
    assert(
      matched.exists(m =>
        m.rightCitekey.value == "west1997_east" && m.why.contains(MergeIssue.Rationale("ISBN equal (strong key)"))
      ),
      matched.toString
    )
    assert(
      matched.exists(m =>
        m.rightCitekey.value == "olsen2015_penelope" && m.why.exists(w => s"$w".contains("OCR bodies alike"))
      ),
      matched.find(_.rightCitekey.value == "olsen2015_penelope").toString
    )
    // deterministic, and pure: planning twice gives the same plan and writes nothing
    assertEquals(planIn(home, bundle, sh), p)
    assert(
      java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), Files.readAllBytes(before)),
      "planning wrote something"
    )

  test("the import refuses while issues are open or merges are needed — every one listed, nothing written"):
    val (work, home, sw, sh) = craft("im")
    val bundle = exportOf(work, sw)
    val before = Files.readAllBytes(exportOf(home, sh))
    val r = importInto(home, bundle, sh)
    assert(!r.applied.applied)
    val p = planIn(home, bundle, sh)
    assertEquals(r.planHash, Some(p.planHash))
    p.open.foreach(i =>
      assert(
        r.problems.exists(_.sentence.startsWith(i.id.value)),
        s"${i.id} not reported:\n${r.problems.mkString("\n")}"
      )
    )
    assert(
      r.problems.exists(x => x.sentence.contains("guterbock1997_perspectives") && x.sentence.contains("merge apply")),
      r.problems.mkString("\n")
    )
    // decided the importable way, the merges still need merge apply
    val res = MergeResolutions(
      p.open
        .filter(i => i.options.contains(DecisionKind.KeepLeft))
        .map(i => MergeResolution(MergeResolution.Issue(i.id.value), MergeResolution.Decision("keep-left")))
    )
    val r2 = importInto(home, bundle, sh, res)
    assert(!r2.applied.applied)
    assert(
      r2.problems.forall(x => x.sentence.contains("merge apply") || x.sentence.contains("open")),
      r2.problems.mkString("\n")
    )
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), before), "a refused import wrote something")

  test("uid_aliases: a bundle uid redirected here lands on the surviving row — its label wins, nothing is inserted"):
    val (a, b) = (fresh("ua"), fresh("ub"))
    val (sa, sb) = (spool("ua"), spool("ub"))
    put(a, src("zzx2001_there", "There", List("Doe, John"), 2001, summary = Some(Source.Summary("v1"))))
    put(b, src("zzx2001_here", "Here", List("Doe, John"), 2001, summary = Some(Source.Summary("ours"))))
    val uidThere = connect(a)(sql"SELECT uid FROM sources WHERE citekey = 'zzx2001_there'".query[UUID].run().head)
    val uidHere = connect(b)(sql"SELECT uid FROM sources WHERE citekey = 'zzx2001_here'".query[UUID].run().head)
    // what merge apply (M5) will leave behind: a redirect, and a ledger row whose local rev is ours now
    val localRev =
      connect(b)(
        TestExchange.kinds
          .entities(BundleScope.Uids(List(uidHere)), ContentStore.none)
          .revs(uidHere)
          .rev
      )
    exec(
      b,
      s"INSERT INTO merges (id, bundle_id, bundle_sha256, from_instance) VALUES ('$uidHere', '$uidHere', 'x', '$uidThere')"
    )
    exec(
      b,
      s"INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES ('$uidThere', '$uidHere', 'source', '$uidHere')"
    )
    exec(
      b,
      s"INSERT INTO merge_ledger (foreign_uid, kind, merge_id, foreign_rev, local_uid, local_rev_after, decision) " +
        s"VALUES ('$uidThere', 'source', '$uidHere', 'old', '$uidHere', '$localRev', 'Merged')"
    )
    // it changes over there; ours has not changed since the merge → fast-forward onto OUR row
    val _ =
      SourceFixtures.patch(
        a,
        domain.library.Citekey("zzx2001_there"),
        Source.Patch(summary = Some(Source.Summary("v2 from there"))),
        chunkChars
      )
    val bundle = exportOf(a, sa)
    val p = planIn(b, bundle, sb)
    assertEquals(p.issues, Nil)
    assertEquals(p.matches.byLedger, 1)
    val r = importInto(b, bundle, sb)
    assert(r.applied.applied, r.problems.mkString("\n"))
    assertEquals(r.counts.find(_.kind == Bundle.Kind.Source).map(_.updated), Some(1), r.toString)
    assertEquals(count(b, "SELECT count(*)::int FROM sources"), 1)
    assertEquals(
      SourceFixtures.load(b, domain.library.Citekey("zzx2001_here")).flatMap(_.summary),
      Some("v2 from there")
    )
    assertEquals(count(b, s"SELECT count(*)::int FROM sources WHERE uid = '$uidHere' AND citekey = 'zzx2001_here'"), 1)
    assertEquals(
      count(b, s"SELECT count(*)::int FROM merge_ledger WHERE foreign_uid = '$uidThere' AND local_uid = '$uidHere'"),
      1
    )
    // and again: nothing to do
    val again = importInto(b, bundle, sb)
    assertEquals(again.changed, 0, again.toString)
    assertEquals(again.issues, Nil)

  test("a bundle with an open issue can still be imported once it is decided the importable way"):
    val (a, b) = (fresh("da"), fresh("db"))
    val (sa, sb) = (spool("da"), spool("db"))
    put(a, src("zzy2001_new", "A new one", List("Roe, Richard"), 2001))
    EventFixtures.put(
      a,
      BEventRow.Id("zz_both"),
      EventPut(
        EventSummary.Label("Event at a"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190)))
      )
    )
    EventFixtures.put(
      b,
      BEventRow.Id("zz_both"),
      EventPut(
        EventSummary.Label("Event at b"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1300)), None, None, Some(Year(-1290)))
      )
    )
    val bundle = exportOf(a, sa)
    val p = planIn(b, bundle, sb)
    val taken = p.open.find(_.kind == Issue.Kind.LabelTaken).getOrElse(fail(p.issues.toString))
    assertEquals(p.open.size, 1, p.issues.mkString("\n"))
    val r = importInto(
      b,
      bundle,
      sb,
      MergeResolutions(
        List(MergeResolution(MergeResolution.Issue(taken.id.value), MergeResolution.Decision("keep-left")))
      )
    )
    assert(r.applied.applied, r.problems.mkString("\n"))
    assertEquals(count(b, "SELECT count(*)::int FROM sources WHERE citekey = 'zzy2001_new'"), 1)
    assertEquals(connect(b)(sql"SELECT label FROM events WHERE id = 'zz_both'".query[String].run().head), "Event at b")

  // ── merge apply (M5) ────────────────────────────────────────────────────────────────────────

  private val backupsTaken = java.util.concurrent.atomic.AtomicInteger(0)

  /** `merge apply` with a stub backup (a test takes no `pg_dump`); it counts the backups asked for. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def applyIn(
      ds: DataSource,
      bundle: Path,
      originals: Path,
      res: MergeResolutions,
      hash: MergePlan.PlanHash
  ): MergeApplyResult =
    BundleImporter
      .apply(
        ds,
        ByteSource.file(bundle),
        sha256Of(bundle),
        BundleImport
          .Options(dryRun = ImportOptions.DryRun(false), importChunkChars, importToleranceKm),
        ContentDirectory(originals),
        res,
        merge,
        MergeRow.PlanHash(hash.value),
        () => { val _ = backupsTaken.incrementAndGet(); Right("stub.dump") },
        TestExchange.kinds,
        TestExchange.rules,
        TestExchange.followUps,
        TestCreators.clock
      )
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  private val confText = "One.\n\nTwo, as merged by hand.\n\nThree."

  /** A decision for every kind of issue the crafted pair raises — each one exercising a decision `merge apply` carries
    * out.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def decide(i: MergeIssue): MergeResolution =
    val d = i.kind match
      case SameWork                                            => s"map:${i.left.get.label}"
      case YearDiffers                                         => "keep-left"
      case CitekeyCollision                                    => i.suggested.get.wire.value
      case AliasConflict                                       => "keep-left"
      case DoiDiffers                                          => "keep-left"
      case StatusDiffers                                       => "keep-right"
      case NotesDiffers                                        => "concat"
      case ChangedBothSides if i.entity == Bundle.Kind.Article => "file:merged.md"
      case ChangedBothSides                                    => "keep-right"
      case DeletedThereChangedHere                             => "keep-left"
      case ArticleDiverged                                     => "both"
      case LabelTaken                                          => "rename-right:zz_siege_work"
      case AttestationUnanchored                               => "drop"
      case _ => i.suggested.fold(DecisionKind.wireOf(i.options.head).value)(_.wire.value)
    MergeResolution(
      MergeResolution.Issue(i.id.value),
      MergeResolution.Decision(d),
      Option.when(d.startsWith("file:"))(MergeResolution.Text(confText))
    )

  /** Plan, decide what is open, plan again — until nothing is open (a decision can make a conditional issue
    * unconditional).
    */
  private def decideAll(ds: DataSource, bundle: Path, originals: Path): (MergeResolutions, MergePlan) =
    @annotation.tailrec
    def loop(res: MergeResolutions, n: Int): (MergeResolutions, MergePlan) =
      val p = planIn(ds, bundle, originals, res)
      assertEquals(p.resolutionErrors, Nil)
      if p.open.isEmpty || n == 0 then (res, p)
      else loop(res.copy(resolve = res.resolve ++ p.open.map(decide)), n - 1)
    loop(MergeResolutions(), 5)

  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def one[A](ds: DataSource, q: String)(using DbCodec[A]): A =
    connect(ds)(sql"${SqlLiteral(q)}".query[A].run().head)
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def uidOf(ds: DataSource, table: String, col: String, label: String): UUID =
    connect(ds)(
      sql"SELECT uid FROM ${SqlLiteral(table)} WHERE ${SqlLiteral(col)} = $label".query[UUID].run().head
    )

  test(
    "merge apply: the crafted pair, every issue decided — one transaction; re-planned, 0 changes; applied again, a no-op"
  ):
    val (work, home, sw, sh) = craft("ap")
    val bundle = exportOf(work, sw)
    val (res, p) = decideAll(home, bundle, sh)
    assertEquals(p.open, Nil, p.issues.mkString("\n"))
    assert(p.changes.value > 0)
    // a stale plan is refused, and nothing is written — not even a backup
    val before = Files.readAllBytes(exportOf(home, sh))
    val stale = applyIn(home, bundle, sh, res, MergePlan.PlanHash("0badc0de"))
    assert(!stale.applied.applied)
    assert(stale.problems.exists(_.sentence.contains("not 0badc0de")), stale.problems.mkString("\n"))
    assertEquals(backupsTaken.get, 0)
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), before), "a refused apply wrote something")

    val uidW = uidOf(work, "sources", "citekey", "guterbock1997_perspectives")
    val uidH = uidOf(home, "sources", "citekey", "guterbock1997_hittite")
    val r = applyIn(home, bundle, sh, res, p.planHash)
    assert(r.applied.applied, r.problems.mkString("\n"))
    assertEquals(r.backup, Some("stub.dump"))
    assertEquals(backupsTaken.get, 1)
    assertEquals(r.replanChanges, Some(0), "planning the same bundle again after the apply")

    // sources: folded, renamed, new — and the redirects and old citekeys a merge leaves
    assertEquals(count(home, "SELECT count(*)::int FROM sources"), 11 + 2) // + zznew2020_only, smith2019_ritualkingship
    assertEquals(count(home, s"SELECT count(*)::int FROM uid_aliases WHERE alias_uid = '$uidW' AND uid = '$uidH'"), 1)
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("guterbock1997_hittite")).flatMap(_.notes),
      Some("home's notes\n\nwork's notes")
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("guterbock1997_hittite")).map(_.doi),
      Some(Some("10.5555/oip26"))
    )
    assertEquals(
      count(
        home,
        "SELECT count(*)::int FROM source_aliases WHERE alias = 'guterbock1997_perspectives' AND citekey = 'guterbock1997_hittite'"
      ),
      1
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("bryce2005_kingdom_hittites")).map(_.status),
      Some(Source.Status.Contested)
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("page1959_history")).flatMap(_.year),
      Some(1959L)
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("smith2019_ritualkingship")).map(_.title),
      Some("Ritual and Kingship in Early Mesopotamia")
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("smith2019_ritual")).map(_.title),
      Some("Ritual Economies of the Aegean")
    )
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("zzshared2001_alpha")).flatMap(_.summary),
      Some("edited at work")
    )
    // the kept body stays (keep-left by default); the ISBN and MinHash pairs folded too
    assertEquals(
      SourceFixtures.load(home, domain.library.Citekey("kuhrt1995_ancient")).flatMap(_.ocrMarkdown),
      Some("Preface. Kuhrt body, home's OCR")
    )
    assertEquals(
      count(
        home,
        "SELECT count(*)::int FROM sources WHERE citekey IN ('west1997_east', 'olsen2015_penelope', 'kuhrt1995_near')"
      ),
      0
    )

    // findings: the same claim is one (tags unioned), the near duplicate kept beside it, the new one
    // numbered after home's last
    val gf = SourceFixtures.load(home, domain.library.Citekey("guterbock1997_hittite")).get.findings
    assertEquals(
      gf.map(_.id.value).sorted,
      List("guterbock1997_hittite#1", "guterbock1997_hittite#2", "guterbock1997_hittite#3", "guterbock1997_hittite#4")
    )
    assertEquals(
      gf.find(_.id.value == "guterbock1997_hittite#1").map(_.tags.map(_.value).sorted),
      Some(List("home", "work"))
    )
    assert(gf.exists(_.claim.value == "Hattusa burned around 1180 BC"))
    // figures: the same bytes are one, a taken name gets work's label
    assertEquals(
      AssetFixtures
        .list(home, domain.library.Citekey("guterbock1997_hittite"))
        .map(_.name.value)
        .toList
        .sorted,
      List("fig1.png", "fig2.png", "fig3-work.png", "fig3.png")
    )
    // edges: re-pointed through the merge, ours kept, the parked one carried
    val edges = connect(home)(sql"SELECT src, dst FROM edges ORDER BY src, dst".query[(String, String)].run().toSet)
    assert(edges(("guterbock1997_hittite", "zznew2020_only")), edges.toString)
    assert(edges(("guterbock1997_hittite", "page1959_history")), edges.toString)
    assert(edges(("bryce2005_kingdom_hittites", "guterbock1997_hittite")), edges.toString)
    assertEquals(
      count(
        home,
        "SELECT count(*)::int FROM unresolved_edges WHERE src = 'guterbock1997_hittite' AND dst = 'smith1900_unknown'"
      ),
      1
    )
    // attestations: the one whose quote home's body has moved to it; the other dropped, for good
    val (start, end) =
      one[(Int, Int)](home, "SELECT char_start, char_end FROM attestations WHERE target_id = 'zz_found'")
    assertEquals("Preface. Kuhrt body, home's OCR".substring(start, end), "Kuhrt body")
    assertEquals(count(home, "SELECT count(*)::int FROM attestations WHERE target_id = 'zz_lost'"), 0)
    val lost = connect(work)(sql"SELECT uid FROM attestations WHERE target_id = 'zz_lost'".query[UUID].run().head)
    assertEquals(count(home, s"SELECT count(*)::int FROM tombstones WHERE uid = '$lost'"), 1)

    // articles: the clean three-way merge, the merged text sent as a file, the other side's beside ours
    assertEquals(
      ArticleFixtures.load(home, Slug("zz_shared_art")).map(_.body),
      Some("Intro (work).\n\nMiddle.\n\nEnd (home).")
    )
    assertEquals(ArticleFixtures.load(home, Slug("zz_shared_conf")).map(_.body), Some(confText))
    val copy =
      ArticleFixtures.load(home, Slug("bronze_age_collapse-work")).getOrElse(fail("no bronze_age_collapse-work"))
    // its `[[citekey]]` follows the merge: work's kuhrt1995_near is home's kuhrt1995_ancient
    assertEquals(copy.body, "Work's account of the collapse, citing [[kuhrt1995_ancient]].")
    assertEquals(
      ArticleFixtures.load(home, Slug("bronze_age_collapse")).map(_.body),
      Some("Home's account of the collapse.")
    )
    // the texts the merge replaced are versions now
    assert(
      count(
        home,
        "SELECT count(*)::int FROM article_versions WHERE slug = 'zz_shared_art' AND body = 'Intro.\n\nMiddle.\n\nEnd (home).'"
      ) >= 1
    )
    // events, goals, queue: one each where both sides had one
    assertEquals(count(home, "SELECT count(*)::int FROM events WHERE id LIKE 'zz_ev_same%'"), 1)
    assertEquals(one[String](home, "SELECT label FROM events WHERE id = 'zz_ev_same'"), "Fall of Hattusa (home)")
    assertEquals(one[String](home, "SELECT label FROM events WHERE id = 'zz_siege_work'"), "Siege of Troy (work)")
    assertEquals(one[String](home, "SELECT status FROM goals WHERE slug = 'zz_goal_both'"), "seeding")
    assertEquals(
      count(
        home,
        "SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE 'Find the%Kuhrt reprint' AND status = 'done'"
      ),
      1
    )
    assertEquals(
      count(home, "SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE 'Find the%Kuhrt reprint'"),
      1
    )
    // the reviews, the log entry, the merge with its plan and backup, the remembered decisions
    assertEquals(r.reviews.sorted, List("bronze_age_collapse-work", "zz_shared_art", "zz_shared_conf"))
    r.reviews.foreach(slug =>
      assertEquals(
        count(
          home,
          s"SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE '/review-article $slug %'"
        ),
        1
      )
    )
    assert(r.logEntry.isDefined)
    assertEquals(
      one[(String, String)](home, s"SELECT plan_hash, backup FROM merges WHERE id = '${r.mergeId.get}'"),
      (p.planHash, "stub.dump")
    )
    assertEquals(count(home, "SELECT count(*)::int FROM merge_decisions"), res.resolve.size)

    // planning again: nothing open, nothing to change; applying again: a no-op, no backup
    val again = planIn(home, bundle, sh, res)
    assertEquals(again.open, Nil)
    assertEquals(again.resolutionErrors, Nil, "a resolution the apply carried out is not stale")
    assertEquals(again.changes, 0, again.issues.filter(_.decision.isDefined).mkString("\n"))
    val snapshot = Files.readAllBytes(exportOf(home, sh))
    val noop = applyIn(home, bundle, sh, res, again.planHash)
    assert(noop.applied.applied, noop.problems.mkString("\n"))
    assertEquals(noop.mergeId, None)
    assertEquals(backupsTaken.get, 1)
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), snapshot), "a second apply wrote something")
    // …and without the resolutions: the decisions are remembered
    val bare = planIn(home, bundle, sh)
    assertEquals(bare.open, Nil, bare.open.mkString("\n"))
    assertEquals(bare.changes, 0)

  test("merge apply refuses while an issue is open, and never takes the backup"):
    val (work, home, sw, sh) = craft("ao")
    val bundle = exportOf(work, sw)
    val p = planIn(home, bundle, sh)
    val taken = backupsTaken.get
    val r = applyIn(home, bundle, sh, MergeResolutions(), p.planHash)
    assert(!r.applied.applied)
    p.open.foreach(i => assert(r.problems.exists(_.sentence.startsWith(i.id.value)), s"${i.id} not reported"))
    assertEquals(backupsTaken.get, taken)

  test(
    "property: random pairs of collections — applied, every reference holds; planned again, nothing; applied again, a no-op"
  ):
    val rnd = scala.util.Random(20261004L)
    val claims = List(
      "Tarhunna ruled the storm",
      "Hattusa burned in fire",
      "Troy fell to the Achaeans",
      "Ugarit traded with Cyprus"
    )
    (1 to 4).foreach { round =>
      val (a, b) = (fresh(s"p${round}a"), fresh(s"p${round}b"))
      val (sa, sb) = (spool(s"p${round}a"), spool(s"p${round}b"))
      Transactor(a).write(BundleRepo.setLabel(Some(InstanceInfo.Label("work"))))
      // six works; each side holds some of them under its own citekeys, a DOI or not, a year off by
      // more than one now and then (an edition question), findings from one pool, edges among its own
      def side(ds: DataSource, tag: String): Unit =
        val held = (0 until 6).filter(_ => rnd.nextDouble() < 0.7).toList
        val cks = held.map(i => s"zzw$i${tag}2000_t")
        held.zip(cks).foreach { (i, ck) =>
          val year = if rnd.nextDouble() < 0.15 then 2010 else 2000
          put(
            ds,
            src(
              ck,
              s"${List("Kingship", "Ritual", "Trade", "Warfare", "Writing", "Burial")(i)} in the Bronze Age",
              List(s"Author$i, Ann"),
              year,
              doi = Option.when(rnd.nextBoolean())(s"10.9999/w$i"),
              findings = rnd.shuffle(claims).take(rnd.nextInt(3)).map(c => Finding.Claim(c) -> List(Finding.Tags(tag))),
              edges =
                cks.filter(_ != ck).filter(_ => rnd.nextDouble() < 0.3).map(c => Citekey(c) -> Edge.Kind.References)
            )
          )
        }
      side(a, "a")
      side(b, "b")
      val bundle = exportOf(a, sa)
      @annotation.tailrec
      def loop(res: MergeResolutions, n: Int): (MergeResolutions, MergePlan) =
        val p = planIn(b, bundle, sb, res)
        if p.open.isEmpty || n == 0 then (res, p)
        else
          loop(
            res.copy(resolve = res.resolve ++ p.open.map { i =>
              val wire = i.kind match
                case SameWork => s"map:${i.left.get.label}"
                case _        => i.suggested.fold(DecisionKind.wireOf(i.options.head).value)(_.wire.value)
              MergeResolution(MergeResolution.Issue(i.id.value), MergeResolution.Decision(wire))
            }),
            n - 1
          )
      val (res, p) = loop(MergeResolutions(), 5)
      assertEquals(p.open, Nil, s"round $round")
      val r = applyIn(b, bundle, sb, res, p.planHash)
      assert(r.applied.applied, s"round $round: ${r.problems.mkString("\n")}")
      assertEquals(r.replanChanges, Some(0), s"round $round")
      assertEquals(
        count(
          b,
          "SELECT count(*)::int FROM edges e WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst)"
        ),
        0
      )
      assertEquals(
        count(
          b,
          "SELECT count(*)::int FROM findings f WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = f.citekey)"
        ),
        0
      )
      assertEquals(
        count(
          b,
          "SELECT count(*)::int FROM uid_aliases u WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.uid = u.uid) " +
            "AND NOT EXISTS (SELECT 1 FROM findings f WHERE f.uid = u.uid)"
        ),
        0
      )
      val again = planIn(b, bundle, sb, res)
      assertEquals(again.changes, 0, s"round $round")
      val snapshot = Files.readAllBytes(exportOf(b, sb))
      val noop = applyIn(b, bundle, sb, res, again.planHash)
      assert(noop.applied.applied && noop.mergeId.isEmpty, s"round $round: ${noop.problems.mkString("\n")}")
      assert(
        java.util.Arrays.equals(Files.readAllBytes(exportOf(b, sb)), snapshot),
        s"round $round: a second apply wrote something"
      )
    }

  test("renames over there are followed: an article slug (its old one kept as an alias), an event id, a goal slug"):
    val (a, b) = (fresh("rna"), fresh("rnb"))
    val (sa, sb) = (spool("rna"), spool("rnb"))
    val _ = ArticleFixtures.put(
      a,
      Article(Article.Slug("zz_old_slug"), Article.Title("Old"), Article.KindValue("concept"), Article.Body("Text."))
    )
    val _ = ArticleFixtures.put(
      a,
      Article(
        Article.Slug("zz_linker"),
        Article.Title("Linker"),
        Article.KindValue("concept"),
        Article.Body("See [[zz_old_slug]].")
      )
    )
    EventFixtures.put(
      a,
      BEventRow.Id("zz_old_event"),
      EventPut(
        EventSummary.Label("An event"),
        EventSummary.Kind("battle"),
        TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190)))
      )
    )
    transact(a)(
      GoalRepo.put(domain.automation.Goal.Slug("zz_old_goal"), Goal.Put(Goal.Title("A goal")))
    )
    val first = importInto(b, exportOf(a, sa), sb)
    assert(first.applied.applied, first.problems.mkString("\n"))
    transact(a) {
      ArticleFixtures
        .renameIn(Slug("zz_old_slug"), Slug("zz_new_slug"))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
      EventFixtures
        .renameIn(BEventRow.Id("zz_old_event"), BEventRow.Id("zz_new_event"))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
      GoalRepo
        .renameIn(
          domain.automation.Goal.Slug("zz_old_goal"),
          domain.automation.Goal.Slug("zz_new_goal")
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    }
    // the rename rewrote the link in the other article over there
    assertEquals(ArticleFixtures.load(a, Slug("zz_linker")).map(_.body), Some("See [[zz_new_slug]]."))
    val r = importInto(b, exportOf(a, sa), sb)
    assert(r.applied.applied, r.problems.mkString("\n"))
    assertEquals(ArticleFixtures.load(b, Slug("zz_new_slug")).map(_.body), Some("Text."))
    assertEquals(ArticleFixtures.view(b, Slug("zz_old_slug")).map(_.article.slug), Some("zz_new_slug"))
    assertEquals(ArticleFixtures.load(b, Slug("zz_linker")).map(_.body), Some("See [[zz_new_slug]]."))
    assertEquals(count(b, "SELECT count(*)::int FROM events WHERE id = 'zz_new_event'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM goals WHERE slug = 'zz_new_goal'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM events WHERE id = 'zz_old_event'"), 0)
    // and the same bundle again changes nothing
    assertEquals(importInto(b, exportOf(a, sa), sb).changed, 0)
