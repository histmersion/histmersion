package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource

/** `merge plan` against real Postgres (docs/research-merge-import.md §8, milestone M3): the crafted
  * pair of collections, "work" and "home", built through the repositories in two databases of their
  * own; work's bundle planned in home must give EXACTLY the expected issue list in one run. Then the
  * import's refusal, the id map (`uid_aliases`) and the ISBN key. Skipped without
  * HISTMERSION_TEST_DB_URL. */
class MergeSuite extends munit.FunSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val admin            = Db.dataSource(cfg.db.copy(url = testDb.get))
  private val created               = scala.collection.mutable.ListBuffer.empty[String]
  private val tmp                   = Files.createTempDirectory("histmersion-merge-suite")

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try { val st = c.createStatement(); try { val _ = st.execute(stmt) } finally st.close() }
    finally c.close()

  private def fresh(n: String): DataSource =
    val name = s"histmersion_merge_${n}_test"
    exec(admin, s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
    exec(admin, s"CREATE DATABASE $name")
    created += name
    val ds = Db.dataSource(cfg.db.copy(url = testDb.get.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1")))
    Migrate.run(ds)
    ds

  override def afterAll(): Unit =
    if testDb.isDefined then created.foreach(n => exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  private def exportOf(ds: DataSource, originals: Path): Path =
    val out = java.io.ByteArrayOutputStream()
    BundleExport.write(ds, out, None, Originals.inDir(originals)).fold(e => fail(e), identity)
    val f = Files.createTempFile(tmp, "bundle-", ".hmx")
    Files.write(f, out.toByteArray)

  private def planIn(ds: DataSource, bundle: Path, originals: Path, res: MergeResolutions = MergeResolutions()): MergePlan =
    MergeState.planFile(ds, bundle, res, cfg.merge, Originals.inDir(originals)).fold(e => fail(e), identity)

  private def importInto(ds: DataSource, bundle: Path, originals: Path, res: MergeResolutions = MergeResolutions(),
      dryRun: Boolean = false): BundleImportResult =
    BundleImport.run(ds, bundle, BundleFormat.sha256Hex(Files.readAllBytes(bundle)),
      BundleImport.Options(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm), Originals.inDir(originals),
      res, cfg.merge).fold(e => fail(e), identity)

  private def count(ds: DataSource, q: String): Int = connect(ds)(Sql.raw(q).frag.query[Int].run().head)

  // ── the crafted pair ───────────────────────────────────────────────────────────────────────

  private def src(ck: String, title: String, authors: List[String], year: Int, doi: Option[String] = None,
      body: Option[String] = None, status: SourceStatus = SourceStatus.Current, notes: Option[String] = None,
      venue: Option[String] = None, summary: Option[String] = None): Source =
    Source(ck, title, authors, doi, Some(year), venue, status, Nil, Nil, Nil, None, summary, None, None, None, None,
      ocrMarkdown = body, notes = notes)

  private def put(ds: DataSource, s: Source): Unit = SourceRepo.upsert(ds, s, cfg.limits.chunkChars)

  private val olsen: String =
    val rnd = scala.util.Random(2015)
    (1 to 3000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(7)).mkString).mkString(" ")
  private val olsenAgain: String =
    olsen.split(" ").zipWithIndex.map((w, i) => if i % 50 == 0 then w.toUpperCase.reverse else w).mkString(" ")
  private val kuhrtSha = "9f2e" + "0" * 60
  private val Isbn     = "9780198150428"

  /** work, home and its spool; home has already imported work once (the shared history). */
  private def craft(tag: String): (DataSource, DataSource, Path, Path) =
    val (work, home) = (fresh(s"${tag}w"), fresh(s"${tag}h"))
    val (sw, sh)     = (spool(s"${tag}w"), spool(s"${tag}h"))
    BundleRepo.setLabel(work, Some("work"))
    BundleRepo.setLabel(home, Some("home"))
    // ── the shared history ──
    put(work, src("zzshared2001_alpha", "Alpha", List("Doe, John"), 2001, summary = Some("as imported")))
    GoalRepo.put(work, "zz_shared_goal", GoalPut("Shared goal"))
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied, first.problems.mkString("\n"))
    // …then both sides go on
    val _ = SourceRepo.patch(work, "zzshared2001_alpha", SourcePatch(summary = Some("edited at work")), cfg.limits.chunkChars)
    val _ = SourceRepo.patch(home, "zzshared2001_alpha", SourcePatch(summary = Some("edited at home")), cfg.limits.chunkChars)
    val _ = GoalRepo.delete(work, "zz_shared_goal")
    val _ = GoalRepo.patch(home, "zz_shared_goal", GoalPatch(notes = Some("edited at home")))

    // ── work ──
    put(work, src("guterbock1997_perspectives", "Perspectives on Hittite Civilization: Selected Writings",
      List("H. G. Güterbock", "Harry A. Hoffner"), 1997, doi = Some("10.5555/oip26"), venue = Some("Oriental Institute"),
      notes = Some("work's notes")))
    put(work, src("kuhrt1995_near", "The Ancient Near East c. 3000-330 BC", List("Amélie Kuhrt"), 1995,
      doi = Some("10.4324/9780203431566"), body = Some("Kuhrt body, work's OCR")))
    AssetRepo.setSha256(work, "kuhrt1995_near", kuhrtSha)
    put(work, src("bryce2005_kingdom", "The Kingdom of the Hittites", List("Trevor Bryce"), 2006,
      doi = Some("10.1093/acprof:oso/9780199279081.001.0001"), status = SourceStatus.Contested))
    put(work, src("page2024_history", "History and the Homeric Iliad", List("Denys Page"), 2024,
      venue = Some("Sather Classical Lectures")))
    put(work, src("smith2019_ritual", "Ritual and Kingship in Early Mesopotamia", List("M. Smith"), 2019))
    put(work, src("latacz2023_troy", "Troy and Homer: Towards a Solution of an Old Mystery", List("Latacz, Joachim"), 2004))
    put(work, src("harrison1903_old", "Prolegomena to the Study of Greek Religion",
      List("Harrison, Jane Ellen, 1850-1928"), 1903))
    val _ = SourceRepo.rename(work, "harrison1903_old", "harrison1903_prolegomena")
    put(work, src("olsen2015_penelope", "Penelope's Weaving", List("Barbara Olsen"), 2015, body = Some(olsen)))
    put(work, src("zznew2020_only", "A Book Only Work Has", List("Roe, Richard"), 2020))
    put(work, src("west1997_east", "The East Face of Helicon", List("M. L. West"), 1997))
    exec(work, s"INSERT INTO source_identifiers (source_uid, scheme, value) SELECT uid, 'isbn', '$Isbn' FROM sources WHERE citekey = 'west1997_east'")
    val _ = ArticleRepo.put(work, Article("bronze_age_collapse", "Bronze Age collapse", "concept",
      "Work's account of the collapse, citing [[kuhrt1995_near]]."))
    val _ = ArticleRepo.put(work, Article("zz_same_text", "Same", "concept", "Identical on both sides."))
    EventRepo.put(work, "zz_siege", EventPut("Siege of Troy (work)", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
    GoalRepo.put(work, "zz_goal_both", GoalPut("Goal created at work"))

    // ── home ──
    put(home, src("guterbock1997_hittite", "Perspectives on Hittite civilization",
      List("Hoffner, Harry A.", "Güterbock, Hans G."), 1997, venue = Some("Oriental Institute"), notes = Some("home's notes")))
    put(home, src("kuhrt1995_ancient", "The Ancient Near East, c. 3000–330 BC", List("Kuhrt, Amelie"), 1995,
      doi = Some("10.4324/9781315682334"), body = Some("Kuhrt body, home's OCR")))
    AssetRepo.setSha256(home, "kuhrt1995_ancient", kuhrtSha)
    put(home, src("bryce2005_kingdom_hittites", "The kingdom of the Hittites", List("Bryce, Trevor"), 2005,
      doi = Some("https://doi.org/10.1093/acprof:oso/9780199279081.001.0001")))
    put(home, src("page1959_history", "History and the Homeric Iliad", List("Page, Denys"), 1959,
      venue = Some("Sather Classical Lectures")))
    put(home, src("smith2019_ritual", "Ritual Economies of the Aegean", List("Smith, J."), 2019))
    put(home, src("latacz2023_troy", "Troy and Homer: Towards a Solution of an Old Mystery", List("Joachim Latacz"), 2004))
    val _ = SourceRepo.rename(home, "latacz2023_troy", "latacz2004_troy")
    put(home, src("harrison1903_prolegomena_greek", "Prolegomena to the study of Greek religion", List("Jane Harrison"), 1903))
    put(home, src("harrison1903_old", "Themis: A Study of the Social Origins of Greek Religion", List("Jane Harrison"), 1912))
    val _ = SourceRepo.rename(home, "harrison1903_old", "harrison1912_themis")
    put(home, src("olsen2015_penelope_weaving", "Penelope’s weaving", List("Olsen, Barbara"), 2015, body = Some(olsenAgain)))
    put(home, src("west1997_face", "The East Face of Helicon", List("West, Martin L."), 1997))
    exec(home, s"INSERT INTO source_identifiers (source_uid, scheme, value) SELECT uid, 'isbn', '$Isbn' FROM sources WHERE citekey = 'west1997_face'")
    val _ = ArticleRepo.put(home, Article("bronze_age_collapse", "Bronze Age collapse", "concept", "Home's account of the collapse."))
    val _ = ArticleRepo.put(home, Article("zz_same_text", "Same", "concept", "Identical on both sides."))
    EventRepo.put(home, "zz_siege", EventPut("Siege of Troy (home)", "battle", TimeSpan(Some(-1210), None, None, Some(-1180))))
    GoalRepo.put(home, "zz_goal_both", GoalPut("Goal created at home"))
    (work, home, sw, sh)

  /** kind, id letter, left label, right label, severity, conditional on. */
  private type Shape = (IssueKind, String, Option[String], Option[String], IssueSeverity, Option[DecisionKind])

  private def shape(p: MergePlan): Set[Shape] =
    p.issues.map(i => (i.kind, i.id.takeWhile(_ != '-'), i.left.map(_.label), i.right.map(_.label), i.severity,
      i.condition.map(_.when))).toSet

  import IssueKind.*, IssueSeverity.*
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
    (LabelTaken, "G", Some("zz_goal_both"), Some("zz_goal_both"), Blocking, None))

  test("the crafted pair: work's bundle planned at home gives exactly the expected issues, in one run"):
    val (work, home, sw, sh) = craft("fx")
    val bundle = exportOf(work, sw)
    val before = exportOf(home, sh)
    val p = planIn(home, bundle, sh)
    assertEquals(shape(p) -- expected, Set.empty, "unexpected issues")
    assertEquals(expected -- shape(p), Set.empty, "missing issues")
    assertEquals(p.issues.size, expected.size, p.issues.mkString("\n"))
    assertEquals(p.matches, MatchCounts(sources = 11, byUid = 1, byStrongKey = 3, byScore = 4, undecided = 1, unmatched = 2))
    assertEquals(p.problems, Nil)
    assertEquals(p.from.label, Some("work"))
    assertEquals(p.into.label, Some("home"))
    // the ISBN and the near-identical OCR are what matched them
    assert(p.sources.exists(m => m.rightCitekey == "west1997_east" && m.why.exists(_.startsWith("ISBN"))), p.sources.toString)
    assert(p.sources.exists(m => m.rightCitekey == "olsen2015_penelope" && m.why.exists(_.contains("OCR bodies alike"))),
      p.sources.find(_.rightCitekey == "olsen2015_penelope").toString)
    // deterministic, and pure: planning twice gives the same plan and writes nothing
    assertEquals(planIn(home, bundle, sh), p)
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), Files.readAllBytes(before)), "planning wrote something")

  test("the import refuses while issues are open or merges are needed — every one listed, nothing written"):
    val (work, home, sw, sh) = craft("im")
    val bundle = exportOf(work, sw)
    val before = Files.readAllBytes(exportOf(home, sh))
    val r = importInto(home, bundle, sh)
    assert(!r.applied)
    val p = planIn(home, bundle, sh)
    assertEquals(r.planHash, Some(p.planHash))
    p.open.foreach(i => assert(r.problems.exists(_.startsWith(i.id)), s"${i.id} not reported:\n${r.problems.mkString("\n")}"))
    assert(r.problems.exists(x => x.contains("guterbock1997_perspectives") && x.contains("merge apply")), r.problems.mkString("\n"))
    // decided the importable way, the merges still need merge apply
    val res = MergeResolutions(p.open.filter(i => i.options.contains(DecisionKind.KeepLeft)).map(i => MergeResolution(i.id, "keep-left")))
    val r2 = importInto(home, bundle, sh, res)
    assert(!r2.applied)
    assert(r2.problems.forall(x => x.contains("merge apply") || x.contains("open")), r2.problems.mkString("\n"))
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), before), "a refused import wrote something")

  test("uid_aliases: a bundle uid redirected here lands on the surviving row — its label wins, nothing is inserted"):
    val (a, b)   = (fresh("ua"), fresh("ub"))
    val (sa, sb) = (spool("ua"), spool("ub"))
    put(a, src("zzx2001_there", "There", List("Doe, John"), 2001, summary = Some("v1")))
    put(b, src("zzx2001_here", "Here", List("Doe, John"), 2001, summary = Some("ours")))
    val uidThere = connect(a)(sql"SELECT uid FROM sources WHERE citekey = 'zzx2001_there'".query[UUID].run().head)
    val uidHere  = connect(b)(sql"SELECT uid FROM sources WHERE citekey = 'zzx2001_here'".query[UUID].run().head)
    // what merge apply (M5) will leave behind: a redirect, and a ledger row whose local rev is ours now
    val localRev = connect(b)(BundleRepo.entities(BundleScope.Uids(List(uidHere)), Originals.none).revs(uidHere).rev)
    exec(b, s"INSERT INTO merges (id, bundle_id, bundle_sha256, from_instance) VALUES ('${uidHere}', '${uidHere}', 'x', '${uidThere}')")
    exec(b, s"INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES ('$uidThere', '$uidHere', 'source', '$uidHere')")
    exec(b, s"INSERT INTO merge_ledger (foreign_uid, kind, merge_id, foreign_rev, local_uid, local_rev_after, decision) " +
      s"VALUES ('$uidThere', 'source', '$uidHere', 'old', '$uidHere', '$localRev', 'Merged')")
    // it changes over there; ours has not changed since the merge → fast-forward onto OUR row
    val _ = SourceRepo.patch(a, "zzx2001_there", SourcePatch(summary = Some("v2 from there")), cfg.limits.chunkChars)
    val bundle = exportOf(a, sa)
    val p = planIn(b, bundle, sb)
    assertEquals(p.issues, Nil)
    assertEquals(p.matches.byLedger, 1)
    val r = importInto(b, bundle, sb)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(r.counts.find(_.kind == BundleKind.Source).map(_.updated), Some(1), r.toString)
    assertEquals(count(b, "SELECT count(*)::int FROM sources"), 1)
    assertEquals(SourceRepo.load(b, "zzx2001_here").flatMap(_.summary), Some("v2 from there"))
    assertEquals(count(b, s"SELECT count(*)::int FROM sources WHERE uid = '$uidHere' AND citekey = 'zzx2001_here'"), 1)
    assertEquals(count(b, s"SELECT count(*)::int FROM merge_ledger WHERE foreign_uid = '$uidThere' AND local_uid = '$uidHere'"), 1)
    // and again: nothing to do
    val again = importInto(b, bundle, sb)
    assertEquals(again.changed, 0, again.toString)
    assertEquals(again.issues, Nil)

  test("a bundle with an open issue can still be imported once it is decided the importable way"):
    val (a, b)   = (fresh("da"), fresh("db"))
    val (sa, sb) = (spool("da"), spool("db"))
    put(a, src("zzy2001_new", "A new one", List("Roe, Richard"), 2001))
    EventRepo.put(a, "zz_both", EventPut("Event at a", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
    EventRepo.put(b, "zz_both", EventPut("Event at b", "battle", TimeSpan(Some(-1300), None, None, Some(-1290))))
    val bundle = exportOf(a, sa)
    val p = planIn(b, bundle, sb)
    val taken = p.open.find(_.kind == IssueKind.LabelTaken).getOrElse(fail(p.issues.toString))
    assertEquals(p.open.size, 1, p.issues.mkString("\n"))
    val r = importInto(b, bundle, sb, MergeResolutions(List(MergeResolution(taken.id, "keep-left"))))
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(count(b, "SELECT count(*)::int FROM sources WHERE citekey = 'zzy2001_new'"), 1)
    assertEquals(connect(b)(sql"SELECT label FROM events WHERE id = 'zz_both'".query[String].run().head), "Event at b")
