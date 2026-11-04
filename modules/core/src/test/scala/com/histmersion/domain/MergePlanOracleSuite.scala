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
      label: String,
      claim: String,
      at: Option[(Double, Double)] = None,
      period: Option[(Long, Long)] = None,
      region: Option[String] = None,
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
      List("zz"),
      confidence,
      None,
      At
    )

  private def resolve(pairs: (String, String)*): MergeResolutions =
    MergeResolutions(pairs.toList.map((i, d) => MergeResolution(i, d)))

  private def idOf(p: MergePlan, kind: Issue.Kind, right: String): String =
    p.issues
      .find(i => i.kind == kind && i.right.exists(_.label == right))
      .getOrElse(fail(s"no $kind on $right in\n${p.issues.mkString("\n")}"))
      .id

  private def plan(
      in: MergePlanner.Input,
      sk: Map[String, com.histmersion.util.text.MinHash],
      res: MergeResolutions
  ): MergePlanner.Result =
    MergePlanner.plan(in, sk, res, cfg, rules)

  private def wanted(
      in: MergePlanner.Input,
      sk: Map[String, com.histmersion.util.text.MinHash],
      res: MergeResolutions
  ): String =
    val s = MergePlanner.sketchesWanted(in, cfg, rules).toList.sorted
    val b = MergePlanner.bodiesWanted(in, sk, res, cfg, rules).toList.sorted
    s"sketches wanted: ${s.mkString(", ")}\nbodies wanted: ${b.mkString(", ")}\n"

  private def render(r: MergePlanner.Result): String =
    val a = r.apply
    val out = StringBuilder()
    def section(name: String, lines: Iterable[String]): Unit =
      out ++= s"-- $name\n"
      lines.foreach(l => out ++= s"$l\n")
    section("plan", List(r.plan.asJson.spaces2))
    section("rows", r.rows.map(_.toString))
    section("needs apply", r.needsApply)
    section("old citekeys left out", r.skipAliases.toList.sorted)
    section("notes", r.notes)
    section("apply: folds", a.folds.map(_.toString))
    section("apply: renames", a.renames.map(_.toString))
    section("apply: snapshots", a.snapshots.toList.map(_.toString).sorted)
    section("apply: versions", a.versions.map(_.toString))
    section("apply: copies", a.copies.map(_.asJson.noSpaces))
    section("apply: attestations", a.attestations.map(_.toString))
    section("apply: tombstones", a.tombstones.map(_.toString))
    section("apply: body renames", a.bodyRenames.toList.map(_.toString).sorted)
    section("apply: reviews", a.reviews)
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
      "guterbock1997_perspectives#1",
      "The Hittite king ruled from Hattusa",
      Some((34.6, 40.0)),
      Some((-1600L, -1200L)),
      Some("Anatolia"),
      Some(Confidence.Medium)
    ),
    finding(
      "wf-g2",
      "w-guterbock",
      "guterbock1997_perspectives#2",
      "Hittite scribes wrote treaties on clay tablets in Akkadian",
      None,
      Some((-1400L, -1300L)),
      Some("Anatolia")
    ),
    finding(
      "wf-g3",
      "w-guterbock",
      "guterbock1997_perspectives#3",
      "Storm gods were worshipped in the capital",
      Some((34.61, 40.01)),
      Some((-1500L, -1200L))
    ),
    // kuhrt (one by its original file): the same claim, no place on one side
    finding(
      "wf-k1",
      "w-kuhrt",
      "kuhrt1995_near#1",
      "Assyria rose after the collapse",
      None,
      Some((-1100L, -900L)),
      None
    ),
    // page (a question): held until it is decided
    finding(
      "wf-p1",
      "w-page",
      "page2024_history#1",
      "The Catalogue of Ships preserves Mycenaean geography",
      None,
      Some((-1300L, -1200L))
    ),
    // smith (its citekey taken here): renamed with its source
    finding("wf-s1", "w-smith", "smith2019_ritual#1", "Kingship was sacral in early Mesopotamia"),
    finding("wf-s2", "w-smith", "smith2019_ritual#2", "Temples redistributed grain"),
    // new: its id is another finding's here
    finding("wf-n1", "w-new", "zznew2020_only#1", "Only work has this claim"),
    finding("wf-n2", "w-new", "zznew2020_only#2", "And this one")
  )

  private val localFindings: List[Bundle.Finding] = List(
    finding(
      "hf-g1",
      "h-guterbock",
      "guterbock1997_hittite#1",
      "the Hittite king ruled from Hattusa.",
      Some((34.62, 40.02)),
      Some((-1650L, -1180L)),
      Some("anatolia"),
      Some(Confidence.Low),
      home
    ),
    finding(
      "hf-g2",
      "h-guterbock",
      "guterbock1997_hittite#2",
      "Hittite scribes wrote treaties on clay tablets",
      None,
      Some((-1350L, -1250L)),
      Some("Anatolia"),
      origin = home
    ),
    finding("hf-g7", "h-guterbock", "guterbock1997_hittite#7", "A claim far away in time", None, Some((500L, 600L))),
    finding(
      "hf-k1",
      "h-kuhrt",
      "kuhrt1995_ancient#1",
      "Assyria rose after the collapse",
      Some((43.1, 36.3)),
      Some((-1150L, -950L)),
      Some("Assyria"),
      origin = home
    ),
    finding("hf-p1", "h-page", "page1959_history#1", "The Catalogue of Ships preserves Mycenaean geography."),
    finding("hf-x", "h-themis", "zznew2020_only#1", "A finding here under the bundle's id", origin = home)
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
      .copy(isbns = Some(List("978-1-58983-212-1"))),
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

  private val edgeAliases: List[Bundle.Alias] = List(Bundle.Alias("zzseeher_old", uid("e-alias"), At))

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
    ).copy(isbns = Some(List("1589832124"))),
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
        aliases = Map("zzseeher_old" -> uid("f-alias"))
      )).modify(_.ledger).setTo(Map.empty
    )

  private def edgeSketches: Map[String, com.histmersion.util.text.MinHash] =
    List(bodyA, bodyB, bodyC)
      .map(t => BundleFormat.sha256Hex(t) -> com.histmersion.util.text.MinHash.of(t, cfg.minhashSize))
      .toMap

  // ── the record ───────────────────────────────────────────────────────────────────────────────

  test("the merge plan's source and finding stages, as recorded"):
    val out = StringBuilder()
    def scenario(name: String, in: MergePlanner.Input, sk: Map[String, com.histmersion.util.text.MinHash])(
        res: MergeResolutions
    ): MergePlanner.Result =
      val r = plan(in, sk, res)
      out ++= s"=== $name\n${wanted(in, sk, res)}${render(r)}\n"
      r

    // the fixture
    val fixture = input()
    val first = scenario("the fixture, nothing decided", fixture, sketches)(MergeResolutions()).plan
    val answered = resolve(
      idOf(first, Issue.Kind.SameWork, "page2024_history") -> "map:page1959_history",
      idOf(first, Issue.Kind.CitekeyCollision, "smith2019_ritual") -> "rename-right:smith2019_ritualkingship",
      idOf(first, Issue.Kind.DoiDiffers, "kuhrt1995_near") -> "keep-right",
      idOf(first, Issue.Kind.BodyDiffers, "kuhrt1995_near") -> "keep-right",
      idOf(first, Issue.Kind.NotesDiffers, "guterbock1997_perspectives") -> "concat",
      idOf(first, Issue.Kind.StatusDiffers, "bryce2005_kingdom") -> "keep-right",
      idOf(first, Issue.Kind.AliasConflict, "harrison1903_prolegomena") -> "keep-left",
      idOf(first, Issue.Kind.BodyDiffers, "olsen2015_penelope") -> "keep-right"
    )
    val mapped = scenario("the fixture, every question answered", fixture, sketches)(answered).plan
    val year = mapped.issues.find(_.kind == Issue.Kind.YearDiffers).map(_.id).toList
    scenario("the fixture, every question answered and the year kept right", fixture, sketches)(
      MergeResolutions(answered.resolve ++ year.map(MergeResolution(_, "keep-right")))
    )
    scenario("the fixture, the gate answered both, the collision renamed left", fixture, sketches)(
      resolve(
        idOf(first, Issue.Kind.SameWork, "page2024_history") -> "both",
        idOf(first, Issue.Kind.CitekeyCollision, "smith2019_ritual") -> "rename-left:smith2019_ritual_aegean"
      )
    )
    val collision = idOf(first, Issue.Kind.CitekeyCollision, "smith2019_ritual")
    val gate = idOf(first, Issue.Kind.SameWork, "page2024_history")
    scenario("the fixture, an invalid citekey, a bad map and a stale id", fixture, sketches)(
      resolve(collision -> "rename-right:Smith-2019", gate -> "map:nonsense", "S-00000000" -> "keep-left")
    )
    scenario("the fixture, a citekey taken here and a map to another source's citekey", fixture, sketches)(
      resolve(collision -> "rename-right:kuhrt1995_ancient", gate -> "map:guterbock1997_hittite")
    )
    scenario("the fixture, a citekey taken in the bundle, a map under the bundle's", fixture, sketches)(
      resolve(collision -> "rename-left:latacz2023_troy", gate -> "map:page2024_history")
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
        near.map(MergeResolution(_, "merge")) ++ List(
          MergeResolution(idOf(f0, Issue.Kind.SameWork, "page2024_history"), "map:page1959_history"),
          MergeResolution(
            idOf(f0, Issue.Kind.CitekeyCollision, "smith2019_ritual"),
            "rename-right:smith2019_ritualkingship"
          )
        )
      )
    )
    val f1 = plan(
      fs,
      sketches,
      resolve(idOf(f0, Issue.Kind.SameWork, "page2024_history") -> "map:page1959_history")
    ).plan
    val nearMapped = f1.issues.filter(_.kind == Issue.Kind.FindingNearDuplicate).map(_.id)
    scenario("findings, the gate mapped, every near duplicate merged", fs, sketches)(
      MergeResolutions(
        MergeResolution(idOf(f0, Issue.Kind.SameWork, "page2024_history"), "map:page1959_history") ::
          nearMapped.map(MergeResolution(_, "merge"))
      )
    )
    scenario("findings, the gate both, the near duplicates kept both", fs, sketches)(
      MergeResolutions(
        MergeResolution(idOf(f0, Issue.Kind.SameWork, "page2024_history"), "both") ::
          near.map(MergeResolution(_, "both"))
      )
    )

    // the score's edge cases
    val e = edgeInput
    val e0 = scenario("the score's edge cases, nothing decided", e, edgeSketches)(MergeResolutions()).plan
    scenario("the score's edge cases, every gate mapped to ours", e, edgeSketches)(
      MergeResolutions(
        e0.issues
          .filter(_.kind == Issue.Kind.SameWork)
          .flatMap(i => i.left.map(l => MergeResolution(i.id, s"map:${l.label}")))
      )
    )
    scenario("the score's edge cases, every gate both, editions separate", e, edgeSketches)(
      MergeResolutions(
        e0.issues.filter(_.kind == Issue.Kind.SameWork).map(i => MergeResolution(i.id, "both")),
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
