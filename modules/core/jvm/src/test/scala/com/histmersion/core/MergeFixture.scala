package com.histmersion.core

import MergePlanner.{Input, Labels, LedgerEntry}

import java.util.UUID

/** A crafted pair of collections, in memory (docs/research-merge-import.md §8): "work" exported a
  * bundle, "home" plans it. One case per row of the scenario catalogue the pure planner can see.
  * The database twin of this fixture is `db.MergeSuite`. */
object MergeFixture:

  val cfg: MergeConfig = AppConfig.load().merge

  val work: UUID = UUID.fromString("0191a2f0-0000-7000-8000-000000000001")
  val home: UUID = UUID.fromString("0193c0f0-0000-7000-8000-000000000002")

  def uid(n: String): UUID = UUID.nameUUIDFromBytes(n.getBytes("UTF-8"))

  private val At = "2026-10-01T09:00:00.000000Z"

  def src(id: String, ck: String, title: String, authors: List[String], year: Option[Int], doi: Option[String] = None,
      sha: Option[String] = None, body: Option[String] = None, status: SourceStatus = SourceStatus.Current,
      summary: Option[String] = None, notes: Option[String] = None, venue: Option[String] = None,
      origin: UUID = work): BundleSource =
    BundleSource(uid(id), origin, ck, title, authors, doi, year, venue, status, Nil, Nil, Nil, None, summary, notes,
      None, None, None, "2026-01-01", body.map(BundleFormat.sha256Hex), sha, None, At)

  def article(id: String, slug: String, body: String, origin: UUID = work): BundleArticle =
    BundleArticle(uid(id), origin, slug, slug.replace('_', ' '), "concept", body, None, Nil, Nil, None, At)

  def event(id: String, eventId: String, label: String, origin: UUID = work): BundleEvent =
    BundleEvent(uid(id), origin, eventId, label, "battle", Some(-1200), None, None, Some(-1190), "historical", None,
      None, None, None, None, None, Nil, Nil, Nil, At)

  def goal(id: String, slug: String, title: String, origin: UUID = work, notes: Option[String] = None): BundleGoal =
    BundleGoal(uid(id), origin, slug, title, "planned", notes, Nil, Nil, None, None, At, At)

  def manifest(es: BundleEntities): BundleManifest =
    BundleManifest(Bundle.Format, Bundle.FormatVersion, "36", uid("bundle"), BundleInstance(work, Some("work")), None,
      es.asOf, Nil, BundleBlobs(0, 0), BundleReference(0, "", 0, ""))

  val bodyOlsen: String =
    val rnd = scala.util.Random(2015)
    (1 to 3000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(7)).mkString).mkString(" ")
  /** The same text after another OCR run: one word in fifty misread. */
  val bodyOlsenAgain: String = bodyOlsen.split(" ").zipWithIndex.map((w, i) => if i % 50 == 0 then w.toUpperCase.reverse else w).mkString(" ")
  val kuhrtSha = "9f2e" + "0" * 60

  // ── the shared history: a source and a goal both sides got from an earlier import ──
  val sharedOriginal: BundleSource = src("shared", "zzshared2001_alpha", "Alpha", List("Doe, John"), Some(2001),
    summary = Some("as imported"))
  val sharedThere: BundleSource = sharedOriginal.copy(summary = Some("edited at work"))
  val sharedHere: BundleSource  = sharedOriginal.copy(summary = Some("edited at home"))
  val goalOriginal: BundleGoal  = goal("goal-shared", "zz_shared_goal", "Shared goal")
  val goalHere: BundleGoal      = goalOriginal.copy(notes = Some("edited at home"))

  /** The bundle from work. */
  val foreign: BundleEntities = BundleEntities(
    sources = List(
      // same work, other citekey: author order and name forms, title case vs sentence case, a subtitle,
      // a DOI on one side only — and notes that differ
      src("w-guterbock", "guterbock1997_perspectives", "Perspectives on Hittite Civilization: Selected Writings",
        List("H. G. Güterbock", "Harry A. Hoffner"), Some(1997), doi = Some("10.5555/oip26"),
        venue = Some("Oriental Institute"), notes = Some("work's notes")),
      // the same original file, two different DOIs, two OCR runs
      src("w-kuhrt", "kuhrt1995_near", "The Ancient Near East c. 3000-330 BC", List("Amélie Kuhrt"), Some(1995),
        doi = Some("10.4324/9780203431566"), sha = Some(kuhrtSha), body = Some("Kuhrt body, work's OCR")),
      // the same DOI, online-first year, a different status
      src("w-bryce", "bryce2005_kingdom", "The Kingdom of the Hittites", List("Trevor Bryce"), Some(2006),
        doi = Some("10.1093/acprof:oso/9780199279081.001.0001"), status = SourceStatus.Contested),
      // a reprint, 65 years later
      src("w-page", "page2024_history", "History and the Homeric Iliad", List("Denys Page"), Some(2024),
        venue = Some("Sather Classical Lectures")),
      // one citekey, two different works
      src("w-smith", "smith2019_ritual", "Ritual and Kingship in Early Mesopotamia", List("M. Smith"), Some(2019)),
      // its citekey is home's OLD citekey of the same work (a rename there)
      src("w-latacz", "latacz2023_troy", "Troy and Homer: Towards a Solution of an Old Mystery",
        List("Latacz, Joachim"), Some(2004)),
      // an old citekey of it names a different source at home
      src("w-harrison", "harrison1903_prolegomena", "Prolegomena to the Study of Greek Religion",
        List("Harrison, Jane Ellen, 1850-1928"), Some(1903)),
      // the same text OCR'd twice, no DOI: MinHash decides
      src("w-olsen", "olsen2015_penelope", "Penelope's Weaving", List("Barbara Olsen"), Some(2015),
        body = Some(bodyOlsen)),
      // shared, changed on both sides since the last import
      sharedThere,
      // nothing like it at home
      src("w-new", "zznew2020_only", "A Book Only Work Has", List("Roe, Richard"), Some(2020))
    ),
    aliases = List(BundleAlias("harrison1903_old", uid("w-harrison"), At)),
    articles = List(
      article("w-bac", "bronze_age_collapse", "Work's account of the collapse, citing [[kuhrt1995_near]]."),
      article("w-same", "zz_same_text", "Identical on both sides.")),
    events = List(event("w-siege", "zz_siege", "Siege of Troy (work)")),
    goals = List(goal("w-goal", "zz_goal_both", "Goal created at work")),
    tombstones = List(BundleTombstone(uid("goal-shared"), BundleKind.Goal, work, At))
  )

  /** Home's sources. */
  val here: List[BundleSource] = List(
    src("h-guterbock", "guterbock1997_hittite", "Perspectives on Hittite civilization",
      List("Hoffner, Harry A.", "Güterbock, Hans G."), Some(1997), venue = Some("Oriental Institute"),
      notes = Some("home's notes"), origin = home),
    src("h-kuhrt", "kuhrt1995_ancient", "The Ancient Near East, c. 3000–330 BC", List("Kuhrt, Amelie"), Some(1995),
      doi = Some("10.4324/9781315682334"), sha = Some(kuhrtSha), body = Some("Kuhrt body, home's OCR"), origin = home),
    src("h-bryce", "bryce2005_kingdom_hittites", "The kingdom of the Hittites", List("Bryce, Trevor"), Some(2005),
      doi = Some("https://doi.org/10.1093/acprof:oso/9780199279081.001.0001"), origin = home),
    src("h-page", "page1959_history", "History and the Homeric Iliad", List("Page, Denys"), Some(1959),
      venue = Some("Sather Classical Lectures"), origin = home),
    src("h-smith", "smith2019_ritual", "Ritual Economies of the Aegean", List("Smith, J."), Some(2019), origin = home),
    src("h-latacz", "latacz2004_troy", "Troy and Homer: Towards a Solution of an Old Mystery",
      List("Joachim Latacz"), Some(2004), origin = home),
    src("h-harrison", "harrison1903_prolegomena_greek", "Prolegomena to the study of Greek religion",
      List("Jane Harrison"), Some(1903), origin = home),
    src("h-themis", "harrison1912_themis", "Themis: A Study of the Social Origins of Greek Religion",
      List("Jane Harrison"), Some(1912), origin = home),
    src("h-olsen", "olsen2015_penelope_weaving", "Penelope’s weaving", List("Olsen, Barbara"), Some(2015),
      body = Some(bodyOlsenAgain), origin = home),
    sharedHere
  )

  val localArticles: List[BundleArticle] = List(
    article("h-bac", "bronze_age_collapse", "Home's account of the collapse.", home),
    article("h-same", "zz_same_text", "Identical on both sides.", home))

  def sketches: Map[String, MergeText.MinHash] =
    (foreign.sources ++ here).flatMap(s => s.bodySha256.map(_ -> s)).toMap.flatMap { (sha, s) =>
      val text = if s.uid == uid("w-olsen") then bodyOlsen else if s.uid == uid("h-olsen") then bodyOlsenAgain
                 else if s.uid == uid("w-kuhrt") then "Kuhrt body, work's OCR" else "Kuhrt body, home's OCR"
      Some(sha -> MergeText.minHash(text, cfg.minhashSize))
    }

  def input(es: BundleEntities = foreign, local: List[BundleSource] = here): Input =
    val localLines = BundleEntities(sources = local.filter(_.uid == sharedHere.uid), articles = localArticles,
      goals = List(goalHere))
    Input(
      manifest = manifest(es),
      here = BundleInstance(home, Some("home")),
      compat = Nil,
      readProblems = Nil,
      foreign = es,
      uidAliases = Map.empty,
      ledger = Map(
        sharedOriginal.uid -> LedgerEntry(BundleFormat.stamp(sharedOriginal).rev, sharedOriginal.uid,
          Some(BundleFormat.stamp(sharedOriginal).rev)),
        goalOriginal.uid -> LedgerEntry(BundleFormat.stamp(goalOriginal).rev, goalOriginal.uid,
          Some(BundleFormat.stamp(goalOriginal).rev))),
      tombstoned = Set.empty,
      local = localLines,
      sources = local,
      labels = Labels(
        citekeys = local.map(s => s.citekey -> s.uid).toMap,
        aliases = Map("latacz2023_troy" -> uid("h-latacz"), "harrison1903_old" -> uid("h-themis")),
        slugs = localArticles.map(a => a.slug -> a.uid).toMap,
        events = Map("zz_siege" -> uid("h-siege")),
        goals = Map("zz_goal_both" -> uid("h-goal"), "zz_shared_goal" -> goalOriginal.uid)),
      live = Set.empty,
      places = Set.empty,
      reference = BundleReference(0, "", 0, ""))

  /** An issue as the expected list names it: kind, letter, the two labels, severity, conditional on. */
  final case class Expect(kind: IssueKind, letter: String, left: Option[String], right: Option[String],
      severity: IssueSeverity, when: Option[DecisionKind] = None)

  def summarize(p: MergePlan): Set[Expect] =
    p.issues.map(i => Expect(i.kind, i.id.takeWhile(_ != '-'), i.left.map(_.label), i.right.map(_.label), i.severity,
      i.condition.map(_.when))).toSet

  import IssueKind.*, IssueSeverity.*
  val expected: Set[Expect] = Set(
    Expect(NotesDiffers, "S", Some("guterbock1997_hittite"), Some("guterbock1997_perspectives"), Blocking),
    Expect(DoiDiffers, "S", Some("kuhrt1995_ancient"), Some("kuhrt1995_near"), Blocking),
    Expect(BodyDiffers, "O", Some("kuhrt1995_ancient"), Some("kuhrt1995_near"), Default),
    Expect(StatusDiffers, "S", Some("bryce2005_kingdom_hittites"), Some("bryce2005_kingdom"), Blocking),
    Expect(SameWork, "S", Some("page1959_history"), Some("page2024_history"), Blocking),
    Expect(YearDiffers, "S", Some("page1959_history"), Some("page2024_history"), Blocking, Some(DecisionKind.Map)),
    Expect(CitekeyCollision, "S", Some("smith2019_ritual"), Some("smith2019_ritual"), Blocking),
    Expect(AliasConflict, "L", Some("harrison1912_themis"), Some("harrison1903_prolegomena"), Blocking),
    Expect(BodyDiffers, "O", Some("olsen2015_penelope_weaving"), Some("olsen2015_penelope"), Default),
    Expect(ChangedBothSides, "S", Some("zzshared2001_alpha"), Some("zzshared2001_alpha"), Blocking),
    Expect(DeletedThereChangedHere, "G", Some("zz_shared_goal"), None, Blocking),
    Expect(ArticleDiverged, "A", Some("bronze_age_collapse"), Some("bronze_age_collapse"), Blocking),
    Expect(ArticleDiverged, "A", Some("zz_same_text"), Some("zz_same_text"), Default),
    Expect(LabelTaken, "E", Some("zz_siege"), Some("zz_siege"), Blocking),
    Expect(LabelTaken, "G", Some("zz_goal_both"), Some("zz_goal_both"), Blocking)
  )
