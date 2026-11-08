package com.histmersion
package api

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import MergePlanner.{Input, Labels, LedgerEntry}

import java.util.UUID
import com.histmersion.api.exchange.MergeDecision.Kind as DecisionKind
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.data.exchange.MatchWeights
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.{Bundle, MergePlan}
import com.histmersion.api.library.Source
import com.histmersion.api.exchange.MergeIssue as Issue
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.util.text.MinHash
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.api.exchange.IssueSide

/** A crafted pair of collections, in memory (docs/research-merge-import.md §8): "work" exported a bundle, "home" plans
  * it. One case per row of the scenario catalogue the pure planner can see. The database twin of this fixture is
  * `db.MergeSuite`.
  */
object MergeFixture:
  private given CanEqual[UUID, UUID] = CanEqual.derived


  private def settings(c: MergeConfig): MergeSettings =
    MergeSettings(
      MergeSettings.AutoMerge(c.autoMerge.value),
      MergeSettings.NewAtMost(c.newAtMost.value),
      MergeSettings.MinhashSize(c.minhashSize.value),
      MergeSettings.FindingNearAtLeast(c.findingNearAtLeast.value),
      MergeSettings.FindingWithinKm(c.findingWithinKm.value),
      MatchWeights(
      MatchWeights.TitleFingerprint(c.weights.titleFingerprint.value),
      MatchWeights.TitleMain(c.weights.titleMain.value),
      MatchWeights.TitleSimilar(c.weights.titleSimilar.value),
      MatchWeights.TitleSimilarAtLeast(c.weights.titleSimilarAtLeast.value),
      MatchWeights.TitleTokens(c.weights.titleTokens.value),
      MatchWeights.TitleTokensAtLeast(c.weights.titleTokensAtLeast.value),
      MatchWeights.TitleDiffers(c.weights.titleDiffers.value),
      MatchWeights.AuthorsSame(c.weights.authorsSame.value),
      MatchWeights.AuthorsFirst(c.weights.authorsFirst.value),
      MatchWeights.AuthorsOverlap(c.weights.authorsOverlap.value),
      MatchWeights.AuthorsDisjoint(c.weights.authorsDisjoint.value),
      MatchWeights.YearEqual(c.weights.yearEqual.value),
      MatchWeights.YearNear(c.weights.yearNear.value),
      MatchWeights.YearReprint(c.weights.yearReprint.value),
      MatchWeights.YearFar(c.weights.yearFar.value),
      MatchWeights.VenueEqual(c.weights.venueEqual.value),
      MatchWeights.VenueDiffers(c.weights.venueDiffers.value),
      MatchWeights.BodySame(c.weights.bodySame.value),
      MatchWeights.BodySameAtLeast(c.weights.bodySameAtLeast.value),
      MatchWeights.BodyEdition(c.weights.bodyEdition.value),
      MatchWeights.BodyEditionAtLeast(c.weights.bodyEditionAtLeast.value),
      MatchWeights.BodyDiffers(c.weights.bodyDiffers.value),
      MatchWeights.BodyDiffersBelow(c.weights.bodyDiffersBelow.value),
      MatchWeights.Alias(c.weights.alias.value)
      ),
      MergeSettings.PlaceWithinKm(c.placeWithinKm.value),
      MergeSettings.PlaceOverlapAtLeast(c.placeOverlapAtLeast.value)
    )

  val cfg: MergeSettings = settings(AppConfig.load().merge)

  val work: UUID = UUID.fromString("0191a2f0-0000-7000-8000-000000000001")
  val home: UUID = UUID.fromString("0193c0f0-0000-7000-8000-000000000002")

  def uid(n: String): UUID = UUID.nameUUIDFromBytes(n.getBytes("UTF-8"))

  private val At = "2026-10-01T09:00:00.000000Z"

  def src(
      id: String,
      ck: String,
      title: String,
      authors: List[String],
      year: Option[Int],
      doi: Option[String] = None,
      sha: Option[String] = None,
      body: Option[String] = None,
      status: Source.Status = Source.Status.Current,
      summary: Option[String] = None,
      notes: Option[String] = None,
      venue: Option[String] = None,
      origin: UUID = work
  ): Bundle.Source =
    Bundle.Source(
      uid(id),
      origin,
      Citekey(ck),
      com.histmersion.api.library.Title(title),
      authors.map(com.histmersion.api.library.AuthorName(_)),
      doi.map(Doi(_)),
      year.map(y => Year(y.toLong)),
      venue.map(Source.Venue(_)),
      status,
      Nil,
      Nil,
      Nil,
      None,
      summary.map(Bundle.Source.Summary(_)),
      notes.map(Bundle.Source.Notes(_)),
      None,
      None,
      None,
      Bundle.Source.AddedDate("2026-01-01"),
      body.map(b => Bundle.Source.BodySha256(BundleFormat.sha256Hex(b).value)),
      sha.map(Bundle.Source.OriginalSha256(_)),
      None,
      Bundle.Source.UpdatedAt(At)
    )

  def article(id: String, slug: String, body: String, origin: UUID = work): Bundle.Article =
    Bundle.Article(uid(id), origin, Bundle.Article.Slug(slug), Bundle.Article.Title(slug.replace('_', ' ')), Bundle.Article.Kind("concept"), Bundle.Article.Body(body), None, Nil, Nil, None, Bundle.Article.UpdatedAt(At))

  def event(
      id: String,
      eventId: String,
      label: String,
      origin: UUID = work,
      from: Long = -1200,
      to: Long = -1190
  ): Bundle.Event =
    Bundle.Event(
      uid(id),
      origin,
      Bundle.Event.Id(eventId),
      Bundle.Event.Label(label),
      Bundle.Event.Kind("battle"),
      Some(Bundle.Event.NotBefore(from)),
      None,
      None,
      Some(Bundle.Event.NotAfter(to)),
      Bundle.Event.Historicity("historical"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      Bundle.Event.UpdatedAt(At)
    )

  def goal(id: String, slug: String, title: String, origin: UUID = work, notes: Option[String] = None): Bundle.Goal =
    Bundle.Goal(uid(id), origin, Bundle.Goal.Slug(slug), Bundle.Goal.Title(title), Bundle.Goal.Status("planned"), notes.map(Bundle.Goal.Notes(_)), Nil, Nil, None, None, Bundle.Goal.CreatedAt(At), Bundle.Goal.UpdatedAt(At))

  def manifest(es: BundleEntities): Bundle.Manifest =
    Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(Bundle.FormatVersion),
      Bundle.Manifest.SchemaVersion("36"),
      uid("bundle"),
      Bundle.Instance(work, Some(Bundle.Instance.Label("work"))),
      None,
      es.asOf,
      Nil,
      Bundle.Blobs(Bundle.Blobs.Count(0), ByteCount(0)),
      Bundle.Reference(Bundle.Reference.Places(0), Bundle.Reference.PlacesDigest(""), Bundle.Reference.Periods(0), Bundle.Reference.PeriodsDigest(""))
    )

  val bodyOlsen: String =
    val rnd = scala.util.Random(2015)
    (1 to 3000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(7)).mkString).mkString(" ")

  /** The same text after another OCR run: one word in fifty misread. */
  val bodyOlsenAgain: String =
    bodyOlsen.split(" ").zipWithIndex.map((w, i) => if i % 50 == 0 then w.toUpperCase.reverse else w).mkString(" ")
  val kuhrtSha = "9f2e" + "0" * 60

  // ── the shared history: a source and a goal both sides got from an earlier import ──
  val sharedOriginal: Bundle.Source =
    src("shared", "zzshared2001_alpha", "Alpha", List("Doe, John"), Some(2001), summary = Some("as imported"))
  val sharedThere: Bundle.Source = sharedOriginal.modify(_.summary).setTo(Some(Bundle.Source.Summary("edited at work")))
  val sharedHere: Bundle.Source = sharedOriginal.modify(_.summary).setTo(Some(Bundle.Source.Summary("edited at home")))
  val goalOriginal: Bundle.Goal = goal("goal-shared", "zz_shared_goal", "Shared goal")
  val goalHere: Bundle.Goal = goalOriginal.modify(_.notes).setTo(Some(Bundle.Goal.Notes("edited at home")))

  /** The bundle from work. */
  val foreign: BundleEntities = BundleEntities(
    sources = List(
      // same work, other citekey: author order and name forms, title case vs sentence case, a subtitle,
      // a DOI on one side only — and notes that differ
      src(
        "w-guterbock",
        "guterbock1997_perspectives",
        "Perspectives on Hittite Civilization: Selected Writings",
        List("H. G. Güterbock", "Harry A. Hoffner"),
        Some(1997),
        doi = Some("10.5555/oip26"),
        venue = Some("Oriental Institute"),
        notes = Some("work's notes")
      ),
      // the same original file, two different DOIs, two OCR runs
      src(
        "w-kuhrt",
        "kuhrt1995_near",
        "The Ancient Near East c. 3000-330 BC",
        List("Amélie Kuhrt"),
        Some(1995),
        doi = Some("10.4324/9780203431566"),
        sha = Some(kuhrtSha),
        body = Some("Kuhrt body, work's OCR")
      ),
      // the same DOI, online-first year, a different status
      src(
        "w-bryce",
        "bryce2005_kingdom",
        "The Kingdom of the Hittites",
        List("Trevor Bryce"),
        Some(2006),
        doi = Some("10.1093/acprof:oso/9780199279081.001.0001"),
        status = Source.Status.Contested
      ),
      // a reprint, 65 years later
      src(
        "w-page",
        "page2024_history",
        "History and the Homeric Iliad",
        List("Denys Page"),
        Some(2024),
        venue = Some("Sather Classical Lectures")
      ),
      // one citekey, two different works
      src("w-smith", "smith2019_ritual", "Ritual and Kingship in Early Mesopotamia", List("M. Smith"), Some(2019)),
      // its citekey is home's OLD citekey of the same work (a rename there)
      src(
        "w-latacz",
        "latacz2023_troy",
        "Troy and Homer: Towards a Solution of an Old Mystery",
        List("Latacz, Joachim"),
        Some(2004)
      ),
      // an old citekey of it names a different source at home
      src(
        "w-harrison",
        "harrison1903_prolegomena",
        "Prolegomena to the Study of Greek Religion",
        List("Harrison, Jane Ellen, 1850-1928"),
        Some(1903)
      ),
      // the same text OCR'd twice, no DOI: MinHash decides
      src(
        "w-olsen",
        "olsen2015_penelope",
        "Penelope's Weaving",
        List("Barbara Olsen"),
        Some(2015),
        body = Some(bodyOlsen)
      ),
      // shared, changed on both sides since the last import
      sharedThere,
      // nothing like it at home
      src("w-new", "zznew2020_only", "A Book Only Work Has", List("Roe, Richard"), Some(2020))
    ),
    aliases = List(Bundle.Alias(Bundle.Alias.Value("harrison1903_old"), uid("w-harrison"), Bundle.Alias.CreatedAt(At))),
    articles = List(
      article("w-bac", "bronze_age_collapse", "Work's account of the collapse, citing [[kuhrt1995_near]]."),
      article("w-same", "zz_same_text", "Identical on both sides.")
    ),
    events = List(event("w-siege", "zz_siege", "Siege of Troy (work)")),
    goals = List(goal("w-goal", "zz_goal_both", "Goal created at work")),
    tombstones =
      List(Bundle.Tombstone(uid("goal-shared"), Bundle.Kind.Goal, work, Bundle.Tombstone.DeletedAt(At)))
  )

  /** Home's sources. */
  val here: List[Bundle.Source] = List(
    src(
      "h-guterbock",
      "guterbock1997_hittite",
      "Perspectives on Hittite civilization",
      List("Hoffner, Harry A.", "Güterbock, Hans G."),
      Some(1997),
      venue = Some("Oriental Institute"),
      notes = Some("home's notes"),
      origin = home
    ),
    src(
      "h-kuhrt",
      "kuhrt1995_ancient",
      "The Ancient Near East, c. 3000–330 BC",
      List("Kuhrt, Amelie"),
      Some(1995),
      doi = Some("10.4324/9781315682334"),
      sha = Some(kuhrtSha),
      body = Some("Kuhrt body, home's OCR"),
      origin = home
    ),
    src(
      "h-bryce",
      "bryce2005_kingdom_hittites",
      "The kingdom of the Hittites",
      List("Bryce, Trevor"),
      Some(2005),
      doi = Some("https://doi.org/10.1093/acprof:oso/9780199279081.001.0001"),
      origin = home
    ),
    src(
      "h-page",
      "page1959_history",
      "History and the Homeric Iliad",
      List("Page, Denys"),
      Some(1959),
      venue = Some("Sather Classical Lectures"),
      origin = home
    ),
    src("h-smith", "smith2019_ritual", "Ritual Economies of the Aegean", List("Smith, J."), Some(2019), origin = home),
    src(
      "h-latacz",
      "latacz2004_troy",
      "Troy and Homer: Towards a Solution of an Old Mystery",
      List("Joachim Latacz"),
      Some(2004),
      origin = home
    ),
    src(
      "h-harrison",
      "harrison1903_prolegomena_greek",
      "Prolegomena to the study of Greek religion",
      List("Jane Harrison"),
      Some(1903),
      origin = home
    ),
    src(
      "h-themis",
      "harrison1912_themis",
      "Themis: A Study of the Social Origins of Greek Religion",
      List("Jane Harrison"),
      Some(1912),
      origin = home
    ),
    src(
      "h-olsen",
      "olsen2015_penelope_weaving",
      "Penelope’s weaving",
      List("Olsen, Barbara"),
      Some(2015),
      body = Some(bodyOlsenAgain),
      origin = home
    ),
    sharedHere
  )

  val localArticles: List[Bundle.Article] = List(
    article("h-bac", "bronze_age_collapse", "Home's account of the collapse.", home),
    article("h-same", "zz_same_text", "Identical on both sides.", home)
  )

  def sketches: Map[Bundle.Source.BodySha256, MinHash] =
    (foreign.sources ++ here).flatMap(s => s.bodySha256.map(_ -> s)).toMap.flatMap { (sha, s) =>
      val text = if s.uid == uid("w-olsen") then bodyOlsen
      else if s.uid == uid("h-olsen") then bodyOlsenAgain
      else if s.uid == uid("w-kuhrt") then "Kuhrt body, work's OCR"
      else "Kuhrt body, home's OCR"
      Some(sha -> MinHash.of(MinHash.Text(text), MinHash.Size(cfg.minhashSize.value)))
    }

  /** Home's event under work's event id, dated differently: not the same event without asking. */
  val localEvent: Bundle.Event = event("h-siege", "zz_siege", "Siege of Troy (home)", home, -1210, -1180)

  /** Home's goal under work's goal slug: one goal, folded. */
  val localGoal: Bundle.Goal = goal("h-goal", "zz_goal_both", "Goal created at home", home)

  /** Home as the database gathers it: every row (`MergeState` reads them all). */
  def input(es: BundleEntities = foreign, local: List[Bundle.Source] = here): Input =
    val localLines = BundleEntities(
      sources = local,
      articles = localArticles,
      events = List(localEvent),
      goals = List(goalHere, localGoal)
    )
    Input(
      manifest = manifest(es),
      here = Bundle.Instance(home, Some(Bundle.Instance.Label("home"))),
      compat = Nil,
      readProblems = Nil,
      foreign = es,
      uidAliases = Map.empty,
      ledger = Map(
        sharedOriginal.uid -> LedgerEntry(
          BundlePlan.Ledger.ForeignRev(BundleFormat.stamp(sharedOriginal).rev),
          sharedOriginal.uid,
          Some(BundlePlan.Ledger.LocalRevAfter(BundleFormat.stamp(sharedOriginal).rev))
        ),
        goalOriginal.uid -> LedgerEntry(
          BundlePlan.Ledger.ForeignRev(BundleFormat.stamp(goalOriginal).rev),
          goalOriginal.uid,
          Some(BundlePlan.Ledger.LocalRevAfter(BundleFormat.stamp(goalOriginal).rev))
        )
      ),
      tombstoned = Set.empty,
      local = localLines,
      sources = local,
      labels = Labels(
        citekeys = local.map(s => s.citekey -> s.uid).toMap,
        aliases = Map(Citekey("latacz2023_troy") -> uid("h-latacz"), Citekey("harrison1903_old") -> uid("h-themis")),
        slugs = localArticles.map(a => a.slug -> a.uid).toMap,
        events = Map(Bundle.Event.Id("zz_siege") -> uid("h-siege")),
        goals = Map(Bundle.Goal.Slug("zz_goal_both") -> uid("h-goal"), Bundle.Goal.Slug("zz_shared_goal") -> goalOriginal.uid)
      ),
      live = Set.empty,
      places = Set.empty,
      reference = Bundle.Reference(Bundle.Reference.Places(0), Bundle.Reference.PlacesDigest(""), Bundle.Reference.Periods(0), Bundle.Reference.PeriodsDigest(""))
    )

  /** An issue as the expected list names it: kind, letter, the two labels, severity, conditional on. */
  final case class Expect(
      kind: Issue.Kind,
      letter: String,
      left: Option[IssueSide.Label],
      right: Option[IssueSide.Label],
      severity: Issue.Severity,
      when: Option[DecisionKind] = None
  )

  def summarize(p: MergePlan): Set[Expect] =
    p.issues
      .map(i =>
        Expect(
          i.kind,
          i.id.value.takeWhile(_ != '-'),
          i.left.map(_.label),
          i.right.map(_.label),
          i.severity,
          i.condition.map(_.when)
        )
      )
      .toSet

  import com.histmersion.api.exchange.MergeIssue.Kind.*
  import com.histmersion.api.exchange.MergeIssue.Severity.*
  val expected: Set[Expect] = Set(
    Expect(NotesDiffers, "S", Some(IssueSide.Label("guterbock1997_hittite")), Some(IssueSide.Label("guterbock1997_perspectives")), Blocking),
    Expect(DoiDiffers, "S", Some(IssueSide.Label("kuhrt1995_ancient")), Some(IssueSide.Label("kuhrt1995_near")), Blocking),
    Expect(BodyDiffers, "O", Some(IssueSide.Label("kuhrt1995_ancient")), Some(IssueSide.Label("kuhrt1995_near")), Default),
    Expect(StatusDiffers, "S", Some(IssueSide.Label("bryce2005_kingdom_hittites")), Some(IssueSide.Label("bryce2005_kingdom")), Blocking),
    Expect(SameWork, "S", Some(IssueSide.Label("page1959_history")), Some(IssueSide.Label("page2024_history")), Blocking),
    Expect(YearDiffers, "S", Some(IssueSide.Label("page1959_history")), Some(IssueSide.Label("page2024_history")), Blocking, Some(DecisionKind.Map)),
    Expect(CitekeyCollision, "S", Some(IssueSide.Label("smith2019_ritual")), Some(IssueSide.Label("smith2019_ritual")), Blocking),
    Expect(AliasConflict, "L", Some(IssueSide.Label("harrison1912_themis")), Some(IssueSide.Label("harrison1903_prolegomena")), Blocking),
    Expect(BodyDiffers, "O", Some(IssueSide.Label("olsen2015_penelope_weaving")), Some(IssueSide.Label("olsen2015_penelope")), Default),
    Expect(ChangedBothSides, "S", Some(IssueSide.Label("zzshared2001_alpha")), Some(IssueSide.Label("zzshared2001_alpha")), Blocking),
    Expect(DeletedThereChangedHere, "G", Some(IssueSide.Label("zz_shared_goal")), None, Blocking),
    Expect(ArticleDiverged, "A", Some(IssueSide.Label("bronze_age_collapse")), Some(IssueSide.Label("bronze_age_collapse")), Blocking),
    Expect(ArticleDiverged, "A", Some(IssueSide.Label("zz_same_text")), Some(IssueSide.Label("zz_same_text")), Default),
    Expect(LabelTaken, "E", Some(IssueSide.Label("zz_siege")), Some(IssueSide.Label("zz_siege")), Blocking)
  )
