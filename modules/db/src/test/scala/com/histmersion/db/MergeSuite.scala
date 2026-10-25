package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.values.{BundleKind, Confidence, DecisionKind, IssueKind, IssueSeverity, SourceStatus}
import io.scalaland.chimney.dsl.*

/** `merge plan` against real Postgres (docs/research-merge-import.md §8, milestone M3): the crafted
  * pair of collections, "work" and "home", built through the repositories in two databases of their
  * own; work's bundle planned in home must give EXACTLY the expected issue list in one run. Then the
  * import's refusal, the id map (`uid_aliases`) and the ISBN key. Skipped without
  * HISTMERSION_TEST_DB_URL. */
class MergeSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val merge            = cfg.merge.transformInto[MergeSettings]
  private lazy val admin            = testPool(cfg.db.copy(url = testDb.get))
  private val tmp                   = Files.createTempDirectory("histmersion-merge-suite")

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try { val st = c.createStatement(); try { val _ = st.execute(stmt) } finally st.close() }
    finally c.close()

  private def fresh(n: String): DataSource =
    val ds = scratch(n)
    ds

  // every database a test makes: a copy of one migrated once (#3765)
  private lazy val scratch = ScratchDatabases("histmersion_merge", cfg.db.copy(url = testDb.get))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  private def exportOf(ds: DataSource, originals: Path): Path =
    val out = java.io.ByteArrayOutputStream()
    BundleExport.write(ds, out, None, Originals.inDir(originals)).fold(e => fail(e), identity)
    val f = Files.createTempFile(tmp, "bundle-", ".hmx")
    Files.write(f, out.toByteArray)

  private def planIn(ds: DataSource, bundle: Path, originals: Path, res: MergeResolutions = MergeResolutions()): MergePlan =
    MergeState.planFile(ds, bundle, res, merge, Originals.inDir(originals), cfg.limits.chunkChars).fold(e => fail(e), identity)

  private def importInto(ds: DataSource, bundle: Path, originals: Path, res: MergeResolutions = MergeResolutions(),
      dryRun: Boolean = false): BundleImportResult =
    BundleImport.run(ds, bundle, BundleFormat.sha256Hex(Files.readAllBytes(bundle)),
      BundleImport.Options(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm), Originals.inDir(originals),
      res, merge).fold(e => fail(e), identity)

  private def count(ds: DataSource, q: String): Int = connect(ds)(Sql.raw(q).frag.query[Int].run().head)

  // ── the crafted pair ───────────────────────────────────────────────────────────────────────

  private def src(ck: String, title: String, authors: List[String], year: Int, doi: Option[String] = None,
      body: Option[String] = None, status: SourceStatus = SourceStatus.Current, notes: Option[String] = None,
      venue: Option[String] = None, summary: Option[String] = None, findings: List[(String, List[String])] = Nil,
      edges: List[(String, String)] = Nil): Source =
    Source(ck, title, authors, doi, Some(year), venue, status, Nil, Nil, Nil, None, summary, None, None, None, None,
      ocrMarkdown = body, notes = notes,
      findings = findings.zipWithIndex.map { case ((claim, tags), i) =>
        Finding(s"$ck#${i + 1}", ck, claim, Some("Anatolia"), Some(GeoPoint(34.6, 40.0)), Some(Period(-1400, -1200)),
          Some("LBA"), tags, Some(Confidence.High))
      },
      edges = edges.map((dst, kind) => Edge(ck, dst, kind)))

  private def png(n: Byte): Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10, n, n, n)
  private def asset(name: String, n: Byte) = AssetRepo.Stored(name, "image/png", png(n), None, None)
  private def attest(ds: DataSource, ck: String, quote: String, target: String): Unit =
    val _ = AttestationRepo.create(ds, AttestationPut(ck, "place", target, quote = Some(quote), charStart = Some(0),
      charEnd = Some(quote.length)))

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
    // two articles both sides will edit: in different places (a clean three-way merge), in one place
    val _ = ArticleRepo.put(work, Article("zz_shared_art", "Shared", "concept", "Intro.\n\nMiddle.\n\nEnd."))
    val _ = ArticleRepo.put(work, Article("zz_shared_conf", "Conflict", "concept", "One.\n\nTwo.\n\nThree."))
    val first = importInto(home, exportOf(work, sw), sh)
    assert(first.applied, first.problems.mkString("\n"))
    // …then both sides go on
    val _ = SourceRepo.patch(work, "zzshared2001_alpha", SourcePatch(summary = Some("edited at work")), cfg.limits.chunkChars)
    val _ = SourceRepo.patch(home, "zzshared2001_alpha", SourcePatch(summary = Some("edited at home")), cfg.limits.chunkChars)
    val _ = GoalRepo.delete(work, "zz_shared_goal")
    val _ = GoalRepo.patch(home, "zz_shared_goal", GoalPatch(notes = Some("edited at home")))
    val _ = ArticleRepo.put(work, Article("zz_shared_art", "Shared", "concept", "Intro (work).\n\nMiddle.\n\nEnd."))
    val _ = ArticleRepo.put(home, Article("zz_shared_art", "Shared", "concept", "Intro.\n\nMiddle.\n\nEnd (home)."))
    val _ = ArticleRepo.put(work, Article("zz_shared_conf", "Conflict", "concept", "One.\n\nTwo at work.\n\nThree."))
    val _ = ArticleRepo.put(home, Article("zz_shared_conf", "Conflict", "concept", "One.\n\nTwo at home.\n\nThree."))

    // ── work ──
    put(work, src("guterbock1997_perspectives", "Perspectives on Hittite Civilization: Selected Writings",
      List("H. G. Güterbock", "Harry A. Hoffner"), 1997, doi = Some("10.5555/oip26"), venue = Some("Oriental Institute"),
      notes = Some("work's notes"),
      // the same claim as home's #1 (other word order), a near duplicate of it, and one home lacks
      findings = List("the storm god of Hatti is Tarhunna" -> List("work"),
        "Tarhunna the storm god ruled Hatti and Arinna" -> Nil, "Hattusa burned around 1180 BC" -> Nil),
      edges = List("zznew2020_only" -> "references", "smith1900_unknown" -> "references")))
    // one figure as home's (the same bytes), one new, one whose name home uses for another figure
    val _ = AssetRepo.add(work, "guterbock1997_perspectives", List(asset("fig1.png", 1), asset("fig2.png", 2), asset("fig3.png", 4)))
    put(work, src("kuhrt1995_near", "The Ancient Near East c. 3000-330 BC", List("Amélie Kuhrt"), 1995,
      doi = Some("10.4324/9780203431566"), body = Some("Kuhrt body, work's OCR")))
    AssetRepo.setSha256(work, "kuhrt1995_near", kuhrtSha)
    // one passage home's body has too (it moves), one only work's has (it cannot)
    attest(work, "kuhrt1995_near", "Kuhrt body", "zz_found")
    attest(work, "kuhrt1995_near", "work's OCR", "zz_lost")
    put(work, src("bryce2005_kingdom", "The Kingdom of the Hittites", List("Trevor Bryce"), 2006,
      doi = Some("10.1093/acprof:oso/9780199279081.001.0001"), status = SourceStatus.Contested,
      edges = List("guterbock1997_perspectives" -> "references")))
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
    // the same event (dated alike) created on both sides
    EventRepo.put(work, "zz_ev_same", EventPut("Fall of Hattusa (work)", "destruction", TimeSpan(Some(-1190), None, None, Some(-1180))))
    GoalRepo.put(work, "zz_goal_both", GoalPut("Goal created at work", status = "seeding"))
    val _ = QueueRepo.add(work, Queue.Kind.Tasks, Queue.QueueAdd("Find the  Kuhrt reprint"))

    // ── home ──
    put(home, src("guterbock1997_hittite", "Perspectives on Hittite civilization",
      List("Hoffner, Harry A.", "Güterbock, Hans G."), 1997, venue = Some("Oriental Institute"), notes = Some("home's notes"),
      findings = List("Tarhunna is the storm god of Hatti" -> List("home"), "The storm god Tarhunna ruled Hatti" -> Nil),
      edges = List("page1959_history" -> "references")))
    val _ = AssetRepo.add(home, "guterbock1997_hittite", List(asset("fig1.png", 1), asset("fig3.png", 3)))
    put(home, src("kuhrt1995_ancient", "The Ancient Near East, c. 3000–330 BC", List("Kuhrt, Amelie"), 1995,
      doi = Some("10.4324/9781315682334"), body = Some("Preface. Kuhrt body, home's OCR")))
    AssetRepo.setSha256(home, "kuhrt1995_ancient", kuhrtSha)
    // home's own attestation: should the merge take work's body, it could not move
    attest(home, "kuhrt1995_ancient", "home's OCR", "zz_home")
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
    EventRepo.put(home, "zz_ev_same", EventPut("Fall of Hattusa (home)", "destruction", TimeSpan(Some(-1190), None, None, Some(-1180))))
    GoalRepo.put(home, "zz_goal_both", GoalPut("Goal created at home", notes = Some("home's plan")))
    val task = QueueRepo.add(home, Queue.Kind.Tasks, Queue.QueueAdd("Find the Kuhrt reprint"))
    val _ = QueueRepo.patch(home, task.id, Queue.QueuePatch(status = Some(Queue.Done)))
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
    // M4: inside the merged pairs, and the articles both sides edited
    (FindingNearDuplicate, "F", Some("guterbock1997_hittite#2"), Some("guterbock1997_perspectives#2"), Default, None),
    (AttestationUnanchored, "T", Some("kuhrt1995_ancient"), Some("place zz_lost"), Blocking, None),
    (AttestationUnanchored, "T", Some("place zz_home"), Some("kuhrt1995_near"), Blocking, Some(DecisionKind.KeepRight)),
    (ChangedBothSides, "A", Some("zz_shared_conf"), Some("zz_shared_conf"), Blocking, None))

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

  // ── merge apply (M5) ────────────────────────────────────────────────────────────────────────

  private val backupsTaken = java.util.concurrent.atomic.AtomicInteger(0)

  /** `merge apply` with a stub backup (a test takes no `pg_dump`); it counts the backups asked for. */
  private def applyIn(ds: DataSource, bundle: Path, originals: Path, res: MergeResolutions, hash: String): MergeApplyResult =
    BundleImport.apply(ds, bundle, BundleFormat.sha256Hex(Files.readAllBytes(bundle)),
      BundleImport.Options(dryRun = false, cfg.limits.chunkChars, cfg.places.pointToleranceKm), Originals.inDir(originals),
      res, merge, hash, () => { val _ = backupsTaken.incrementAndGet(); Right("stub.dump") }).fold(e => fail(e), identity)

  private val confText = "One.\n\nTwo, as merged by hand.\n\nThree."

  /** A decision for every kind of issue the crafted pair raises — each one exercising a decision
    * `merge apply` carries out. */
  private def decide(i: MergeIssue): MergeResolution =
    val d = i.kind match
      case SameWork                => s"map:${i.left.get.label}"
      case YearDiffers             => "keep-left"
      case CitekeyCollision        => i.suggested.get.wire
      case AliasConflict           => "keep-left"
      case DoiDiffers              => "keep-left"
      case StatusDiffers           => "keep-right"
      case NotesDiffers            => "concat"
      case ChangedBothSides if i.entity == BundleKind.Article => "file:merged.md"
      case ChangedBothSides        => "keep-right"
      case DeletedThereChangedHere => "keep-left"
      case ArticleDiverged         => "both"
      case LabelTaken              => "rename-right:zz_siege_work"
      case AttestationUnanchored   => "drop"
      case _                       => i.suggested.fold(DecisionKind.wireOf(i.options.head))(_.wire)
    MergeResolution(i.id, d, Option.when(d.startsWith("file:"))(confText))

  /** Plan, decide what is open, plan again — until nothing is open (a decision can make a conditional
    * issue unconditional). */
  private def decideAll(ds: DataSource, bundle: Path, originals: Path): (MergeResolutions, MergePlan) =
    @annotation.tailrec
    def loop(res: MergeResolutions, n: Int): (MergeResolutions, MergePlan) =
      val p = planIn(ds, bundle, originals, res)
      assertEquals(p.resolutionErrors, Nil)
      if p.open.isEmpty || n == 0 then (res, p)
      else loop(res.copy(resolve = res.resolve ++ p.open.map(decide)), n - 1)
    loop(MergeResolutions(), 5)

  private def one[A](ds: DataSource, q: String)(using DbCodec[A]): A = connect(ds)(Sql.raw(q).frag.query[A].run().head)
  private def uidOf(ds: DataSource, table: String, col: String, label: String): UUID =
    connect(ds)(Sql.of(s"SELECT uid FROM $table WHERE $col = ?", Param.S(label)).frag.query[UUID].run().head)

  test("merge apply: the crafted pair, every issue decided — one transaction; re-planned, 0 changes; applied again, a no-op"):
    val (work, home, sw, sh) = craft("ap")
    val bundle = exportOf(work, sw)
    val (res, p) = decideAll(home, bundle, sh)
    assertEquals(p.open, Nil, p.issues.mkString("\n"))
    assert(p.changes > 0)
    // a stale plan is refused, and nothing is written — not even a backup
    val before = Files.readAllBytes(exportOf(home, sh))
    val stale = applyIn(home, bundle, sh, res, "0badc0de")
    assert(!stale.applied)
    assert(stale.problems.exists(_.contains("not 0badc0de")), stale.problems.mkString("\n"))
    assertEquals(backupsTaken.get, 0)
    assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(home, sh)), before), "a refused apply wrote something")

    val uidW = uidOf(work, "sources", "citekey", "guterbock1997_perspectives")
    val uidH = uidOf(home, "sources", "citekey", "guterbock1997_hittite")
    val r = applyIn(home, bundle, sh, res, p.planHash)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(r.backup, Some("stub.dump"))
    assertEquals(backupsTaken.get, 1)
    assertEquals(r.replanChanges, Some(0), "planning the same bundle again after the apply")

    // sources: folded, renamed, new — and the redirects and old citekeys a merge leaves
    assertEquals(count(home, "SELECT count(*)::int FROM sources"), 11 + 2) // + zznew2020_only, smith2019_ritualkingship
    assertEquals(count(home, s"SELECT count(*)::int FROM uid_aliases WHERE alias_uid = '$uidW' AND uid = '$uidH'"), 1)
    assertEquals(SourceRepo.load(home, "guterbock1997_hittite").flatMap(_.notes), Some("home's notes\n\nwork's notes"))
    assertEquals(SourceRepo.load(home, "guterbock1997_hittite").map(_.doi), Some(Some("10.5555/oip26")))
    assertEquals(count(home, "SELECT count(*)::int FROM source_aliases WHERE alias = 'guterbock1997_perspectives' AND citekey = 'guterbock1997_hittite'"), 1)
    assertEquals(SourceRepo.load(home, "bryce2005_kingdom_hittites").map(_.status), Some(SourceStatus.Contested))
    assertEquals(SourceRepo.load(home, "page1959_history").flatMap(_.year), Some(1959))
    assertEquals(SourceRepo.load(home, "smith2019_ritualkingship").map(_.title), Some("Ritual and Kingship in Early Mesopotamia"))
    assertEquals(SourceRepo.load(home, "smith2019_ritual").map(_.title), Some("Ritual Economies of the Aegean"))
    assertEquals(SourceRepo.load(home, "zzshared2001_alpha").flatMap(_.summary), Some("edited at work"))
    // the kept body stays (keep-left by default); the ISBN and MinHash pairs folded too
    assertEquals(SourceRepo.load(home, "kuhrt1995_ancient").flatMap(_.ocrMarkdown), Some("Preface. Kuhrt body, home's OCR"))
    assertEquals(count(home, "SELECT count(*)::int FROM sources WHERE citekey IN ('west1997_east', 'olsen2015_penelope', 'kuhrt1995_near')"), 0)

    // findings: the same claim is one (tags unioned), the near duplicate kept beside it, the new one
    // numbered after home's last
    val gf = SourceRepo.load(home, "guterbock1997_hittite").get.findings
    assertEquals(gf.map(_.id).sorted,
      List("guterbock1997_hittite#1", "guterbock1997_hittite#2", "guterbock1997_hittite#3", "guterbock1997_hittite#4"))
    assertEquals(gf.find(_.id == "guterbock1997_hittite#1").map(_.tags.sorted), Some(List("home", "work")))
    assert(gf.exists(_.claim == "Hattusa burned around 1180 BC"))
    // figures: the same bytes are one, a taken name gets work's label
    assertEquals(AssetRepo.list(home, "guterbock1997_hittite").map(_.name).toList.sorted,
      List("fig1.png", "fig2.png", "fig3-work.png", "fig3.png"))
    // edges: re-pointed through the merge, ours kept, the parked one carried
    val edges = connect(home)(sql"SELECT src, dst FROM edges ORDER BY src, dst".query[(String, String)].run().toSet)
    assert(edges(("guterbock1997_hittite", "zznew2020_only")), edges.toString)
    assert(edges(("guterbock1997_hittite", "page1959_history")), edges.toString)
    assert(edges(("bryce2005_kingdom_hittites", "guterbock1997_hittite")), edges.toString)
    assertEquals(count(home, "SELECT count(*)::int FROM unresolved_edges WHERE src = 'guterbock1997_hittite' AND dst = 'smith1900_unknown'"), 1)
    // attestations: the one whose quote home's body has moved to it; the other dropped, for good
    val (start, end) = one[(Int, Int)](home, "SELECT char_start, char_end FROM attestations WHERE target_id = 'zz_found'")
    assertEquals("Preface. Kuhrt body, home's OCR".substring(start, end), "Kuhrt body")
    assertEquals(count(home, "SELECT count(*)::int FROM attestations WHERE target_id = 'zz_lost'"), 0)
    val lost = connect(work)(sql"SELECT uid FROM attestations WHERE target_id = 'zz_lost'".query[UUID].run().head)
    assertEquals(count(home, s"SELECT count(*)::int FROM tombstones WHERE uid = '$lost'"), 1)

    // articles: the clean three-way merge, the merged text sent as a file, the other side's beside ours
    assertEquals(ArticleRepo.load(home, "zz_shared_art").map(_.body), Some("Intro (work).\n\nMiddle.\n\nEnd (home)."))
    assertEquals(ArticleRepo.load(home, "zz_shared_conf").map(_.body), Some(confText))
    val copy = ArticleRepo.load(home, "bronze_age_collapse-work").getOrElse(fail("no bronze_age_collapse-work"))
    // its `[[citekey]]` follows the merge: work's kuhrt1995_near is home's kuhrt1995_ancient
    assertEquals(copy.body, "Work's account of the collapse, citing [[kuhrt1995_ancient]].")
    assertEquals(ArticleRepo.load(home, "bronze_age_collapse").map(_.body), Some("Home's account of the collapse."))
    // the texts the merge replaced are versions now
    assert(count(home, "SELECT count(*)::int FROM article_versions WHERE slug = 'zz_shared_art' AND body = 'Intro.\n\nMiddle.\n\nEnd (home).'") >= 1)
    // events, goals, queue: one each where both sides had one
    assertEquals(count(home, "SELECT count(*)::int FROM events WHERE id LIKE 'zz_ev_same%'"), 1)
    assertEquals(one[String](home, "SELECT label FROM events WHERE id = 'zz_ev_same'"), "Fall of Hattusa (home)")
    assertEquals(one[String](home, "SELECT label FROM events WHERE id = 'zz_siege_work'"), "Siege of Troy (work)")
    assertEquals(one[String](home, "SELECT status FROM goals WHERE slug = 'zz_goal_both'"), "seeding")
    assertEquals(count(home, "SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE 'Find the%Kuhrt reprint' AND status = 'done'"), 1)
    assertEquals(count(home, "SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE 'Find the%Kuhrt reprint'"), 1)
    // the reviews, the log entry, the merge with its plan and backup, the remembered decisions
    assertEquals(r.reviews.sorted, List("bronze_age_collapse-work", "zz_shared_art", "zz_shared_conf"))
    r.reviews.foreach(slug =>
      assertEquals(count(home, s"SELECT count(*)::int FROM queue_items WHERE list = 'tasks' AND text LIKE '/review-article $slug %'"), 1))
    assert(r.logEntry.isDefined)
    assertEquals(one[(String, String)](home, s"SELECT plan_hash, backup FROM merges WHERE id = '${r.mergeId.get}'"), (p.planHash, "stub.dump"))
    assertEquals(count(home, "SELECT count(*)::int FROM merge_decisions"), res.resolve.size)

    // planning again: nothing open, nothing to change; applying again: a no-op, no backup
    val again = planIn(home, bundle, sh, res)
    assertEquals(again.open, Nil)
    assertEquals(again.resolutionErrors, Nil, "a resolution the apply carried out is not stale")
    assertEquals(again.changes, 0, again.issues.filter(_.decision.isDefined).mkString("\n"))
    val snapshot = Files.readAllBytes(exportOf(home, sh))
    val noop = applyIn(home, bundle, sh, res, again.planHash)
    assert(noop.applied, noop.problems.mkString("\n"))
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
    assert(!r.applied)
    p.open.foreach(i => assert(r.problems.exists(_.startsWith(i.id)), s"${i.id} not reported"))
    assertEquals(backupsTaken.get, taken)

  test("property: random pairs of collections — applied, every reference holds; planned again, nothing; applied again, a no-op"):
    val rnd    = scala.util.Random(20261004L)
    val claims = List("Tarhunna ruled the storm", "Hattusa burned in fire", "Troy fell to the Achaeans", "Ugarit traded with Cyprus")
    (1 to 4).foreach { round =>
      val (a, b)   = (fresh(s"p${round}a"), fresh(s"p${round}b"))
      val (sa, sb) = (spool(s"p${round}a"), spool(s"p${round}b"))
      BundleRepo.setLabel(a, Some("work"))
      // six works; each side holds some of them under its own citekeys, a DOI or not, a year off by
      // more than one now and then (an edition question), findings from one pool, edges among its own
      def side(ds: DataSource, tag: String): Unit =
        val held = (0 until 6).filter(_ => rnd.nextDouble() < 0.7).toList
        val cks  = held.map(i => s"zzw${i}${tag}2000_t")
        held.zip(cks).foreach { (i, ck) =>
          val year = if rnd.nextDouble() < 0.15 then 2010 else 2000
          put(ds, src(ck, s"${List("Kingship", "Ritual", "Trade", "Warfare", "Writing", "Burial")(i)} in the Bronze Age", List(s"Author$i, Ann"), year,
            doi = Option.when(rnd.nextBoolean())(s"10.9999/w$i"),
            findings = rnd.shuffle(claims).take(rnd.nextInt(3)).map(_ -> List(tag)),
            edges = cks.filter(_ != ck).filter(_ => rnd.nextDouble() < 0.3).map(_ -> "references")))
        }
      side(a, "a")
      side(b, "b")
      val bundle = exportOf(a, sa)
      @annotation.tailrec
      def loop(res: MergeResolutions, n: Int): (MergeResolutions, MergePlan) =
        val p = planIn(b, bundle, sb, res)
        if p.open.isEmpty || n == 0 then (res, p)
        else loop(res.copy(resolve = res.resolve ++ p.open.map(i =>
          MergeResolution(i.id, i.kind match
            case SameWork => s"map:${i.left.get.label}"
            case _        => i.suggested.fold(DecisionKind.wireOf(i.options.head))(_.wire)))), n - 1)
      val (res, p) = loop(MergeResolutions(), 5)
      assertEquals(p.open, Nil, s"round $round")
      val r = applyIn(b, bundle, sb, res, p.planHash)
      assert(r.applied, s"round $round: ${r.problems.mkString("\n")}")
      assertEquals(r.replanChanges, Some(0), s"round $round")
      assertEquals(count(b, "SELECT count(*)::int FROM edges e WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = e.dst)"), 0)
      assertEquals(count(b, "SELECT count(*)::int FROM findings f WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = f.citekey)"), 0)
      assertEquals(count(b, "SELECT count(*)::int FROM uid_aliases u WHERE NOT EXISTS (SELECT 1 FROM sources s WHERE s.uid = u.uid) " +
        "AND NOT EXISTS (SELECT 1 FROM findings f WHERE f.uid = u.uid)"), 0)
      val again = planIn(b, bundle, sb, res)
      assertEquals(again.changes, 0, s"round $round")
      val snapshot = Files.readAllBytes(exportOf(b, sb))
      val noop = applyIn(b, bundle, sb, res, again.planHash)
      assert(noop.applied && noop.mergeId.isEmpty, s"round $round: ${noop.problems.mkString("\n")}")
      assert(java.util.Arrays.equals(Files.readAllBytes(exportOf(b, sb)), snapshot), s"round $round: a second apply wrote something")
    }

  test("renames over there are followed: an article slug (its old one kept as an alias), an event id, a goal slug"):
    val (a, b)   = (fresh("rna"), fresh("rnb"))
    val (sa, sb) = (spool("rna"), spool("rnb"))
    val _ = ArticleRepo.put(a, Article("zz_old_slug", "Old", "concept", "Text."))
    val _ = ArticleRepo.put(a, Article("zz_linker", "Linker", "concept", "See [[zz_old_slug]]."))
    EventRepo.put(a, "zz_old_event", EventPut("An event", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
    GoalRepo.put(a, "zz_old_goal", GoalPut("A goal"))
    val first = importInto(b, exportOf(a, sa), sb)
    assert(first.applied, first.problems.mkString("\n"))
    transact(a) {
      ArticleRepo.renameIn("zz_old_slug", "zz_new_slug").fold(e => fail(e.message), identity)
      EventRepo.renameIn("zz_old_event", "zz_new_event").fold(e => fail(e.message), identity)
      GoalRepo.renameIn("zz_old_goal", "zz_new_goal").fold(e => fail(e.message), identity)
    }
    // the rename rewrote the link in the other article over there
    assertEquals(ArticleRepo.load(a, "zz_linker").map(_.body), Some("See [[zz_new_slug]]."))
    val r = importInto(b, exportOf(a, sa), sb)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(ArticleRepo.load(b, "zz_new_slug").map(_.body), Some("Text."))
    assertEquals(ArticleRepo.view(b, "zz_old_slug").map(_.article.slug), Some("zz_new_slug"))
    assertEquals(ArticleRepo.load(b, "zz_linker").map(_.body), Some("See [[zz_new_slug]]."))
    assertEquals(count(b, "SELECT count(*)::int FROM events WHERE id = 'zz_new_event'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM goals WHERE slug = 'zz_new_goal'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM events WHERE id = 'zz_old_event'"), 0)
    // and the same bundle again changes nothing
    assertEquals(importInto(b, exportOf(a, sa), sb).changed, 0)
