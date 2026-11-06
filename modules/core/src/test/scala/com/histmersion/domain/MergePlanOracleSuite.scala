package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.domain.research.ResearchPlan
import com.histmersion.domain.media.assets.AssetPlan

import com.histmersion.util.data.exchange.{BundleFormat, PlanRules}
import com.histmersion.api.MergeFixture
import com.histmersion.api.given

import MergeFixture.*
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import com.histmersion.api.library.Confidence, com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.{MergePlan, MergePolicy, MergeResolution, MergeResolutions}
import com.histmersion.api.exchange.MergeIssue as Issue
import com.histmersion.api.exchange.Bundle

/** The merge plan's source and finding stages, recorded (`oracle/merge/plans.txt`, written on the code before the
  * library's rules of the plan left the planner): the crafted fixture under no resolutions, under a set that answers
  * every question, under a gate answered `both`, under refused resolutions (an invalid citekey, a taken one, a bad map,
  * a stale id) and under both editions policies; findings of merged pairs matched, nearly matched and added on both
  * sides (a confidence note, a near-duplicate kept and merged, ids renumbered and relabelled, a collided source's
  * findings renamed, a gated pair's held); and the score's edge cases (similar and overlapping titles, first and
  * overlapping and disjoint authors, one year apart, a reprint, venues, OCR bodies alike, partly alike and different,
  * an old citekey, two DOIs, an ISBN, an ambiguous match). Each result is written whole: the plan, the rows, what an
  * import cannot do, the old citekeys left out, the notes, what `merge apply` does, and every final line; with the
  * bodies the plan asks for. `HISTMERSION_UPDATE_ORACLE=1` rewrites the file.
  */
class MergePlanOracleSuite extends munit.FunSuite with Bounded:

  private val rules: List[PlanRules] = List(CataloguePlan, AssetPlan, LibraryPlan, ResearchPlan)

  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val oracle: Path =
    Iterator
      .iterate(Paths.get(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("modules/core/src/test/resources"))
      .find(Files.isDirectory(_))
      .get
      .resolve("oracle/merge/plans.txt")

  private val At = "2026-10-01T09:00:00.000000Z"

  private def finding(
      id: String,
      source: String,
      label: Bundle.Finding.Id,
      claim: Bundle.Finding.Claim,
      at: Option[(com.histmersion.api.catalogue.Longitude, com.histmersion.api.catalogue.Latitude)] = None,
      period: Option[(com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year)] = None,
      region: Option[Bundle.Finding.Region] = None,
      confidence: Option[Confidence] = Some(Confidence.High),
      origin: UUID = work
  ): Bundle.Finding =
    Bundle.Finding(
      uid(id),
      origin,
      uid(source),
      label,
      claim,
      region,
      at.map(_._1),
      at.map(_._2),
      period.map((a, b) => Bundle.Range(Some(a), Some(b))),
      None,
      List(com.histmersion.api.exchange.Bundle.Finding.Tags("zz")),
      confidence,
      None,
      Bundle.Finding.UpdatedAt(At)
    )

  private def answer(issue: Issue.Id, decision: MergeResolution.Decision): MergeResolution =
    MergeResolution(MergeResolution.Issue(issue.value), decision)

  private def resolve(pairs: (Issue.Id, MergeResolution.Decision)*): MergeResolutions =
    MergeResolutions(pairs.toList.map((i, d) => answer(i, d)))

  private def idOf(p: MergePlan, kind: Issue.Kind, right: com.histmersion.api.exchange.IssueSide.Label): Issue.Id =
    p.issues
      .find(i => i.kind == kind && i.right.exists(_.label == right))
      .getOrElse(fail(s"no $kind on $right in\n${p.issues.mkString("\n")}"))
      .id

  private def plan(
      in: MergePlanner.Input,
      sk: Map[Bundle.Source.BodySha256, com.histmersion.util.text.MinHash],
      res: MergeResolutions
  ): MergePlanner.Result =
    MergePlanner.plan(in, sk, res, cfg, rules)

  private def wanted(
      in: MergePlanner.Input,
      sk: Map[Bundle.Source.BodySha256, com.histmersion.util.text.MinHash],
      res: MergeResolutions
  ): String =
    val s = MergePlanner.sketchesWanted(in, cfg, rules).toList.sortBy(_.value)
    val b = MergePlanner.bodiesWanted(in, sk, res, cfg, rules).toList.sortBy(_.value)
    s"sketches wanted: ${s.mkString(", ")}\nbodies wanted: ${b.mkString(", ")}\n"

  private def render(r: MergePlanner.Result): String =
    val a = r.apply
    val out = StringBuilder()
    def section(name: String, lines: Iterable[String]): Unit =
      out ++= s"-- $name\n"
      lines.foreach(l => out ++= s"$l\n")
    section("plan", List(r.plan.asJson.spaces2))
    section("rows", r.rows.map(_.toString))
    section("needs apply", r.needsApply.map(_.value))
    section("old citekeys left out", r.skipAliases.toList.map(_.value).sorted)
    section("notes", r.notes.map(_.value))
    section("apply: folds", a.folds.map(_.toString))
    section("apply: renames", a.renames.map(_.toString))
    section("apply: snapshots", a.snapshots.toList.map(_.toString).sorted)
    section("apply: versions", a.versions.map(_.toString))
    section("apply: copies", a.copies.map(_.asJson.noSpaces))
    section("apply: attestations", a.attestations.map(_.toString))
    section("apply: tombstones", a.tombstones.map(_.toString))
    section("apply: body renames", a.bodyRenames.toList.map(_.toString).sorted)
    section("apply: reviews", a.reviews.map(_.value))
    section("apply: remember", a.remember.map((i, d) => s"$i = ${d.wire}"))
    section("apply: touch", a.touch.toList.map(_.toString).sorted)
    section("apply: tally", List(a.tally.asJson.noSpaces))
    section(
      "final lines",
      r.localized.payloads.flatMap((file, bytes) => String(bytes, UTF_8).linesIterator.map(l => s"$file $l"))
    )
    out.result()

  // ── the fixture's findings ───────────────────────────────────────────────────────────────────

  /** Findings on the bundle's merged and new sources and on the ones here they meet. */
  private val foreignFindings: List[Bundle.Finding] = List(
    // guterbock (merged by score): the same claim at another confidence, a near duplicate, one of its own
    finding(
      "wf-g1",
      "w-guterbock",
      Bundle.Finding.Id("guterbock1997_perspectives#1"),
      Bundle.Finding.Claim("The Hittite king ruled from Hattusa"),
      Some((com.histmersion.api.catalogue.Longitude(34.6), com.histmersion.api.catalogue.Latitude(40.0))),
      Some((com.histmersion.api.catalogue.Year(-1600L), com.histmersion.api.catalogue.Year(-1200L))),
      Some(Bundle.Finding.Region("Anatolia")),
      Some(Confidence.Medium)
    ),
    finding(
      "wf-g2",
      "w-guterbock",
      Bundle.Finding.Id("guterbock1997_perspectives#2"),
      Bundle.Finding.Claim("Hittite scribes wrote treaties on clay tablets in Akkadian"),
      None,
      Some((com.histmersion.api.catalogue.Year(-1400L), com.histmersion.api.catalogue.Year(-1300L))),
      Some(Bundle.Finding.Region("Anatolia"))
    ),
    finding(
      "wf-g3",
      "w-guterbock",
      Bundle.Finding.Id("guterbock1997_perspectives#3"),
      Bundle.Finding.Claim("Storm gods were worshipped in the capital"),
      Some((com.histmersion.api.catalogue.Longitude(34.61), com.histmersion.api.catalogue.Latitude(40.01))),
      Some((com.histmersion.api.catalogue.Year(-1500L), com.histmersion.api.catalogue.Year(-1200L)))
    ),
    // kuhrt (one by its original file): the same claim, no place on one side
    finding(
      "wf-k1",
      "w-kuhrt",
      Bundle.Finding.Id("kuhrt1995_near#1"),
      Bundle.Finding.Claim("Assyria rose after the collapse"),
      None,
      Some((com.histmersion.api.catalogue.Year(-1100L), com.histmersion.api.catalogue.Year(-900L))),
      None
    ),
    // page (a question): held until it is decided
    finding(
      "wf-p1",
      "w-page",
      Bundle.Finding.Id("page2024_history#1"),
      Bundle.Finding.Claim("The Catalogue of Ships preserves Mycenaean geography"),
      None,
      Some((com.histmersion.api.catalogue.Year(-1300L), com.histmersion.api.catalogue.Year(-1200L)))
    ),
    // smith (its citekey taken here): renamed with its source
    finding("wf-s1", "w-smith", Bundle.Finding.Id("smith2019_ritual#1"), Bundle.Finding.Claim("Kingship was sacral in early Mesopotamia")),
    finding("wf-s2", "w-smith", Bundle.Finding.Id("smith2019_ritual#2"), Bundle.Finding.Claim("Temples redistributed grain")),
    // new: its id is another finding's here
    finding("wf-n1", "w-new", Bundle.Finding.Id("zznew2020_only#1"), Bundle.Finding.Claim("Only work has this claim")),
    finding("wf-n2", "w-new", Bundle.Finding.Id("zznew2020_only#2"), Bundle.Finding.Claim("And this one"))
  )

  private val localFindings: List[Bundle.Finding] = List(
    finding(
      "hf-g1",
      "h-guterbock",
      Bundle.Finding.Id("guterbock1997_hittite#1"),
      Bundle.Finding.Claim("the Hittite king ruled from Hattusa."),
      Some((com.histmersion.api.catalogue.Longitude(34.62), com.histmersion.api.catalogue.Latitude(40.02))),
      Some((com.histmersion.api.catalogue.Year(-1650L), com.histmersion.api.catalogue.Year(-1180L))),
      Some(Bundle.Finding.Region("anatolia")),
      Some(Confidence.Low),
      home
    ),
    finding(
      "hf-g2",
      "h-guterbock",
      Bundle.Finding.Id("guterbock1997_hittite#2"),
      Bundle.Finding.Claim("Hittite scribes wrote treaties on clay tablets"),
      None,
      Some((com.histmersion.api.catalogue.Year(-1350L), com.histmersion.api.catalogue.Year(-1250L))),
      Some(Bundle.Finding.Region("Anatolia")),
      origin = home
    ),
    finding("hf-g7", "h-guterbock", Bundle.Finding.Id("guterbock1997_hittite#7"), Bundle.Finding.Claim("A claim far away in time"), None, Some((com.histmersion.api.catalogue.Year(500L), com.histmersion.api.catalogue.Year(600L)))),
    finding(
      "hf-k1",
      "h-kuhrt",
      Bundle.Finding.Id("kuhrt1995_ancient#1"),
      Bundle.Finding.Claim("Assyria rose after the collapse"),
      Some((com.histmersion.api.catalogue.Longitude(43.1), com.histmersion.api.catalogue.Latitude(36.3))),
      Some((com.histmersion.api.catalogue.Year(-1150L), com.histmersion.api.catalogue.Year(-950L))),
      Some(Bundle.Finding.Region("Assyria")),
      origin = home
    ),
    finding("hf-p1", "h-page", Bundle.Finding.Id("page1959_history#1"), Bundle.Finding.Claim("The Catalogue of Ships preserves Mycenaean geography.")),
    finding("hf-x", "h-themis", Bundle.Finding.Id("zznew2020_only#1"), Bundle.Finding.Claim("A finding here under the bundle's id"), origin = home)
  )

  private def withFindings: MergePlanner.Input =
    val in = input(foreign.modify(_.findings).setTo(foreignFindings))
    in.modify(
      _.local).setTo(in.local.modify(_.findings).setTo(localFindings)).modify(_.labels).setTo(in.labels.modify(_.findings).setTo(localFindings.map(f => f.id -> f.uid).toMap)
    )

  // ── the score's edge cases ───────────────────────────────────────────────────────────────────

  private def body(seed: Int, words: Int = 2000): String =
    val rnd = scala.util.Random(seed)
    (1 to words).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(7)).mkString).mkString(" ")

  /** The same text with every `every`-th word replaced. */
  private def edited(text: String, every: Int): String =
    text.split(" ").zipWithIndex.map((w, i) => if i % every == 0 then s"x$i" else w).mkString(" ")

  private val bodyA = body(11)
  private val bodyB = edited(bodyA, 3)
  private val bodyC = body(12)

  private val edgeForeign: List[Bundle.Source] = List(
    src("e-jw", "zzhrozny1931_empire", "The Hittite Empire and its Neighbours", List("Hrozny, B."), Some(1931)),
    src(
      "e-tok",
      "zzbittel1970_hattusha",
      "Hattusha capital of the Hittites",
      List("Bittel, Kurt", "Neve, Peter"),
      Some(1970),
      venue = Some("Oxford")
    ),
    src("e-first", "zzgurney1952_hittites", "The Hittites", List("Gurney, O. R.", "Hawkins, J. D."), Some(1952)),
    src(
      "e-reprint",
      "zzgarstang1910_land",
      "The Land of the Hittites (reprint)",
      List("Garstang, John"),
      Some(1975),
      venue = Some("Reprint edition")
    ),
    src("e-far", "zzgoetze1957_kleinasien", "Kleinasien", List("Goetze, Albrecht"), Some(1933)),
    src(
      "e-body",
      "zzmacqueen1986_hittites",
      "The Hittites and their Contemporaries",
      List("Macqueen, J. G."),
      Some(1986),
      body = Some(bodyB)
    ),
    src(
      "e-differ",
      "zzlehmann1977_kings",
      "Kings of Hatti",
      List("Lehmann, Johannes"),
      Some(1977),
      body = Some(bodyC)
    ),
    src(
      "e-dois",
      "zzbeckman1996_treaties",
      "Hittite Diplomatic Texts",
      List("Beckman, Gary"),
      Some(1996),
      doi = Some("10.9999/zz.treaties.a")
    ),
    src("e-isbn", "zzhoffner2009_letters", "Letters from the Hittite Kingdom", List("Hoffner, Harry"), Some(2009))
      .copy(isbns = Some(List(com.histmersion.api.exchange.Bundle.Source.Isbns("978-1-58983-212-1")))),
    src("e-two", "zzcollins2007_hittites", "The Hittites and Their World", List("Collins, Billie Jean"), Some(2007)),
    src("e-alias", "zzseeher2002_hethiter", "Die Hethiter", List("Seeher, Jürgen"), Some(2002)),
    src(
      "e-overlap",
      "zzotten1990_seals",
      "Seals and Sealings of the Hittite Empire Period",
      List("Otten, H.", "Neve, P.", "Seidl, U."),
      Some(1990)
    ),
    src("e-ambiguous", "zzdurand2010_rites", "Rites of Hatti", List("Durand, J."), Some(2010))
  )

  private val edgeAliases: List[Bundle.Alias] = List(Bundle.Alias(com.histmersion.api.exchange.Bundle.Alias.Value("zzseeher_old"), uid("e-alias"), Bundle.Alias.CreatedAt(At)))

  private val edgeHere: List[Bundle.Source] = List(
    src(
      "f-jw",
      "zzhrozny1931_empires",
      "The Hittite Empire and its Neighbors",
      List("Hrozný, Bedřich"),
      Some(1931),
      origin = home
    ),
    src(
      "f-tok",
      "zzbittel1970_hattusa",
      "Hattusha the capital of the Hittites",
      List("Kurt Bittel"),
      Some(1971),
      venue = Some("Oxford University Press"),
      origin = home
    ),
    src(
      "f-first",
      "zzgurney1952_the",
      "The Hittites",
      List("Gurney, Oliver", "Gurney, Robert"),
      Some(1952),
      origin = home
    ),
    src(
      "f-reprint",
      "zzgarstang1910_hittites",
      "The Land of the Hittites",
      List("Garstang, J."),
      Some(1910),
      origin = home
    ),
    src("f-far", "zzgoetze1957_kleinasien_2", "Kleinasien", List("Goetze, A."), Some(1957), origin = home),
    src(
      "f-body",
      "zzmacqueen1986_contemporaries",
      "The Hittites and Their Contemporaries in Asia Minor",
      List("Macqueen, James"),
      Some(1986),
      body = Some(bodyA),
      origin = home
    ),
    src(
      "f-differ",
      "zzlehmann1977_hatti",
      "Kings of Hatti",
      List("Lehmann, J."),
      Some(1977),
      body = Some(bodyA),
      origin = home
    ),
    src(
      "f-dois",
      "zzbeckman1996_diplomatic",
      "Hittite diplomatic texts",
      List("Beckman, G."),
      Some(1996),
      doi = Some("10.9999/zz.treaties.b"),
      origin = home
    ),
    src(
      "f-isbn",
      "zzhoffner2009_hittite_letters",
      "Letters of the Hittites",
      List("Unknown, A."),
      Some(2010),
      origin = home
    ).copy(isbns = Some(List(com.histmersion.api.exchange.Bundle.Source.Isbns("1589832124")))),
    src(
      "f-two-a",
      "zzcollins2007_world",
      "The Hittites and Their World",
      List("Collins, B. J."),
      Some(2007),
      origin = home
    ),
    src(
      "f-two-b",
      "zzcollins2007_world_b",
      "The Hittites and Their World",
      List("Collins, Billie"),
      Some(2008),
      origin = home
    ),
    src("f-alias", "zzseeher2002_hittites", "The Hittites", List("Seeher, Jurgen"), Some(2002), origin = home),
    src(
      "f-overlap",
      "zzotten1990_sealings",
      "The Hittite Empire Period: Seals, Sealings and Bullae",
      List("Neve, Peter", "Seidl, Ursula", "Bittel, K."),
      Some(1990),
      origin = home
    ),
    src("f-amb-a", "zzdurand2012_rites", "Rites of Hatti", List("Durand, Jean"), Some(2012), origin = home),
    src("f-amb-b", "zzdurand2013_rites", "Rites of Hatti", List("Durand, J."), Some(2013), origin = home)
  )

  private def edgeInput: MergePlanner.Input =
    val es = BundleEntities(sources = edgeForeign, aliases = edgeAliases)
    val in = input(es, edgeHere)
    in.modify(
      _.local).setTo(BundleEntities(sources = edgeHere)).modify(_.labels).setTo(MergePlanner.Labels(
        citekeys = edgeHere.map(s => s.citekey -> s.uid).toMap,
        aliases = Map(com.histmersion.api.library.Citekey("zzseeher_old") -> uid("f-alias"))
      )).modify(_.ledger).setTo(Map.empty
    )

  private def edgeSketches: Map[Bundle.Source.BodySha256, com.histmersion.util.text.MinHash] =
    List(bodyA, bodyB, bodyC)
      .map(t => Bundle.Source.BodySha256(BundleFormat.sha256Hex(t).value) -> com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(t), com.histmersion.util.text.MinHash.Size(cfg.minhashSize.value)))
      .toMap

  // ── the record ───────────────────────────────────────────────────────────────────────────────

  test("the merge plan's source and finding stages, as recorded"):
    val out = StringBuilder()
    def scenario(name: String, in: MergePlanner.Input, sk: Map[Bundle.Source.BodySha256, com.histmersion.util.text.MinHash])(
        res: MergeResolutions
    ): MergePlanner.Result =
      val r = plan(in, sk, res)
      out ++= s"=== $name\n${wanted(in, sk, res)}${render(r)}\n"
      r

    // the fixture
    val fixture = input()
    val first = scenario("the fixture, nothing decided", fixture, sketches)(MergeResolutions()).plan
    val answered = resolve(
      idOf(first, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")) -> MergeResolution.Decision("map:page1959_history"),
      idOf(first, Issue.Kind.CitekeyCollision, com.histmersion.api.exchange.IssueSide.Label("smith2019_ritual")) -> MergeResolution.Decision("rename-right:smith2019_ritualkingship"),
      idOf(first, Issue.Kind.DoiDiffers, com.histmersion.api.exchange.IssueSide.Label("kuhrt1995_near")) -> MergeResolution.Decision("keep-right"),
      idOf(first, Issue.Kind.BodyDiffers, com.histmersion.api.exchange.IssueSide.Label("kuhrt1995_near")) -> MergeResolution.Decision("keep-right"),
      idOf(first, Issue.Kind.NotesDiffers, com.histmersion.api.exchange.IssueSide.Label("guterbock1997_perspectives")) -> MergeResolution.Decision("concat"),
      idOf(first, Issue.Kind.StatusDiffers, com.histmersion.api.exchange.IssueSide.Label("bryce2005_kingdom")) -> MergeResolution.Decision("keep-right"),
      idOf(first, Issue.Kind.AliasConflict, com.histmersion.api.exchange.IssueSide.Label("harrison1903_prolegomena")) -> MergeResolution.Decision("keep-left"),
      idOf(first, Issue.Kind.BodyDiffers, com.histmersion.api.exchange.IssueSide.Label("olsen2015_penelope")) -> MergeResolution.Decision("keep-right")
    )
    val mapped = scenario("the fixture, every question answered", fixture, sketches)(answered).plan
    val year = mapped.issues.find(_.kind == Issue.Kind.YearDiffers).map(_.id).toList
    scenario("the fixture, every question answered and the year kept right", fixture, sketches)(
      MergeResolutions(answered.resolve ++ year.map(answer(_, com.histmersion.api.exchange.MergeResolution.Decision("keep-right"))))
    )
    scenario("the fixture, the gate answered both, the collision renamed left", fixture, sketches)(
      resolve(
        idOf(first, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")) -> MergeResolution.Decision("both"),
        idOf(first, Issue.Kind.CitekeyCollision, com.histmersion.api.exchange.IssueSide.Label("smith2019_ritual")) -> MergeResolution.Decision("rename-left:smith2019_ritual_aegean")
      )
    )
    val collision = idOf(first, Issue.Kind.CitekeyCollision, com.histmersion.api.exchange.IssueSide.Label("smith2019_ritual"))
    val gate = idOf(first, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history"))
    scenario("the fixture, an invalid citekey, a bad map and a stale id", fixture, sketches)(
      resolve(collision -> MergeResolution.Decision("rename-right:Smith-2019"), gate -> MergeResolution.Decision("map:nonsense"), Issue.Id("S-00000000") -> MergeResolution.Decision("keep-left"))
    )
    scenario("the fixture, a citekey taken here and a map to another source's citekey", fixture, sketches)(
      resolve(collision -> MergeResolution.Decision("rename-right:kuhrt1995_ancient"), gate -> MergeResolution.Decision("map:guterbock1997_hittite"))
    )
    scenario("the fixture, a citekey taken in the bundle, a map under the bundle's", fixture, sketches)(
      resolve(collision -> MergeResolution.Decision("rename-left:latacz2023_troy"), gate -> MergeResolution.Decision("map:page2024_history"))
    )
    scenario("the fixture, editions merged", fixture, sketches)(
      MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Merge)))
    )
    scenario("the fixture, editions separate", fixture, sketches)(
      MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Separate)))
    )

    // findings
    val fs = withFindings
    val f0 = scenario("findings, nothing decided", fs, sketches)(MergeResolutions()).plan
    val near = f0.issues.filter(_.kind == Issue.Kind.FindingNearDuplicate).map(_.id)
    scenario("findings, the near duplicates merged, the gate mapped, the collision renamed", fs, sketches)(
      MergeResolutions(
        near.map(answer(_, com.histmersion.api.exchange.MergeResolution.Decision("merge"))) ++ List(
          answer(idOf(f0, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")), com.histmersion.api.exchange.MergeResolution.Decision("map:page1959_history")),
          answer(
            idOf(f0, Issue.Kind.CitekeyCollision, com.histmersion.api.exchange.IssueSide.Label("smith2019_ritual")),
            com.histmersion.api.exchange.MergeResolution.Decision("rename-right:smith2019_ritualkingship")
          )
        )
      )
    )
    val f1 = plan(
      fs,
      sketches,
      resolve(idOf(f0, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")) -> MergeResolution.Decision("map:page1959_history"))
    ).plan
    val nearMapped = f1.issues.filter(_.kind == Issue.Kind.FindingNearDuplicate).map(_.id)
    scenario("findings, the gate mapped, every near duplicate merged", fs, sketches)(
      MergeResolutions(
        answer(idOf(f0, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")), com.histmersion.api.exchange.MergeResolution.Decision("map:page1959_history")) ::
          nearMapped.map(answer(_, com.histmersion.api.exchange.MergeResolution.Decision("merge")))
      )
    )
    scenario("findings, the gate both, the near duplicates kept both", fs, sketches)(
      MergeResolutions(
        answer(idOf(f0, Issue.Kind.SameWork, com.histmersion.api.exchange.IssueSide.Label("page2024_history")), com.histmersion.api.exchange.MergeResolution.Decision("both")) ::
          near.map(answer(_, com.histmersion.api.exchange.MergeResolution.Decision("both")))
      )
    )

    // the score's edge cases
    val e = edgeInput
    val e0 = scenario("the score's edge cases, nothing decided", e, edgeSketches)(MergeResolutions()).plan
    scenario("the score's edge cases, every gate mapped to ours", e, edgeSketches)(
      MergeResolutions(
        e0.issues
          .filter(_.kind == Issue.Kind.SameWork)
          .flatMap(i => i.left.map(l => answer(i.id, com.histmersion.api.exchange.MergeResolution.Decision(s"map:${l.label}"))))
      )
    )
    scenario("the score's edge cases, every gate both, editions separate", e, edgeSketches)(
      MergeResolutions(
        e0.issues.filter(_.kind == Issue.Kind.SameWork).map(i => answer(i.id, com.histmersion.api.exchange.MergeResolution.Decision("both"))),
        MergePolicy(Some(EditionsPolicy.Separate))
      )
    )

    val text = out.result()
    if update then
      Files.createDirectories(oracle.getParent)
      val _ = Files.writeString(oracle, text, UTF_8)
    else
      val recorded = if Files.exists(oracle) then Files.readString(oracle, UTF_8) else ""
      if recorded != text then
        val actual = Paths.get("target/oracle-actual/merge/plans.txt")
        Files.createDirectories(actual.getParent)
        val _ = Files.writeString(actual, text, UTF_8)
        val (rl, al) = (recorded.linesIterator.toVector, text.linesIterator.toVector)
        val at = rl.zip(al).indexWhere((a, b) => a != b) match
          case -1 => math.min(rl.size, al.size)
          case i  => i
        fail(
          s"plans.txt differs at line ${at + 1}:\n  recorded: ${rl.lift(at).getOrElse("<end>")}\n" +
            s"  actual:   ${al.lift(at).getOrElse("<end>")}\n(the whole record: $actual)"
        )
