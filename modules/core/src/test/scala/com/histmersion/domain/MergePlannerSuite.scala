package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.domain.research.ResearchPlan

import com.histmersion.util.data.exchange.{BundleFormat, BundlePlan, MergeResolutionsFile}
import com.histmersion.api.MergeFixture
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import BundlePlan.Decision
import MergeFixture.*
import com.histmersion.api.exchange.MergeDecision.Kind as DecisionKind
import com.histmersion.api.exchange.EditionsPolicy, com.histmersion.api.library.{MatchMethod, MatchOutcome}
import com.histmersion.api.exchange.{
  IssueCondition,
  MatchCounts,
  MergeApplyResult,
  MergeDecision,
  MergeIssue,
  MergeMarkdown,
  MergePlan,
  MergePolicy,
  MergeResolution,
  MergeResolutions
}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeIssue as Issue

/** `merge plan` as a pure function over the crafted fixture (docs/research-merge-import.md §8): the whole issue list in
  * one run, conditional issues, resolutions (stale, invalid, partial), the editions policy, and the properties —
  * determinism, stable ids, monotone resolution.
  */
class MergePlannerSuite extends munit.FunSuite with Bounded:

  private def plan(in: MergePlanner.Input = input(), res: MergeResolutions = MergeResolutions()): MergePlanner.Result =
    MergePlanner.plan(in, sketches, res, cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))

  private def resolve(pairs: (String, String)*): MergeResolutions =
    MergeResolutions(pairs.toList.map((i, d) => MergeResolution(i, d)))

  private def issue(p: MergePlan, kind: Issue.Kind, right: String): MergeIssue =
    p.issues
      .find(i => i.kind == kind && i.right.exists(_.label == right))
      .getOrElse(fail(s"no $kind on $right in\n${p.issues.mkString("\n")}"))

  test("the crafted fixture: exactly the expected issues, in one run"):
    val p = plan().plan
    val got = summarize(p)
    assertEquals(got -- expected, Set.empty, "unexpected issues")
    assertEquals(expected -- got, Set.empty, "missing issues")
    assertEquals(p.issues.size, expected.size, p.issues.mkString("\n"))
    assertEquals(
      p.matches,
      MatchCounts(sources = com.histmersion.api.exchange.MatchCounts.Sources(10), byUid = com.histmersion.api.exchange.MatchCounts.ByUid(1), byStrongKey = 2, byScore = 4, undecided = 1, unmatched = 2)
    )
    assertEquals(p.problems, Nil)
    assertEquals(p.resolutionErrors, Nil)
    // ids are <letter>-<8 hex>, unique
    assert(p.issues.forall(_.id.matches("[A-Z]-[0-9a-f]{8}")), p.issues.map(_.id).toString)
    assertEquals(p.issues.map(_.id).distinct.size, p.issues.size)

  test("the evidence behind the matches: strong keys, the score, a DOI on one side only is neutral"):
    val p = plan().plan
    val byRight = com.histmersion.api.RegisteredPlanMatches.kind.all(p.sources).map(m => m.rightCitekey -> m).toMap
    assertEquals(byRight(com.histmersion.api.library.Citekey("kuhrt1995_near")).method, MatchMethod.StrongKey)
    assert(byRight(com.histmersion.api.library.Citekey("kuhrt1995_near")).why.exists(_.contains("sha256")), byRight(com.histmersion.api.library.Citekey("kuhrt1995_near")).why.toString)
    assertEquals(byRight(com.histmersion.api.library.Citekey("bryce2005_kingdom")).leftCitekey, Some("bryce2005_kingdom_hittites"))
    assertEquals(byRight(com.histmersion.api.library.Citekey("guterbock1997_perspectives")).outcome, MatchOutcome.AutoMerge)
    assertEquals(byRight(com.histmersion.api.library.Citekey("guterbock1997_perspectives")).leftCitekey, Some("guterbock1997_hittite"))
    assert(!byRight(com.histmersion.api.library.Citekey("guterbock1997_perspectives")).why.exists(_.toLowerCase.contains("doi")))
    assertEquals(byRight(com.histmersion.api.library.Citekey("latacz2023_troy")).leftCitekey, Some("latacz2004_troy"))
    assert(byRight(com.histmersion.api.library.Citekey("latacz2023_troy")).why.exists(_.contains("old citekey")))
    assertEquals(byRight(com.histmersion.api.library.Citekey("harrison1903_prolegomena")).leftCitekey, Some("harrison1903_prolegomena_greek"))
    assertEquals(byRight(com.histmersion.api.library.Citekey("olsen2015_penelope")).outcome, MatchOutcome.AutoMerge)
    assertEquals(byRight(com.histmersion.api.library.Citekey("page2024_history")).outcome, MatchOutcome.NeedsDecision)
    assertEquals(byRight(com.histmersion.api.library.Citekey("smith2019_ritual")).outcome, MatchOutcome.New)
    // the citekey collision suggests the next title word
    assertEquals(
      issue(p, Issue.Kind.CitekeyCollision, "smith2019_ritual").suggested.map(_.wire),
      Some("rename-right:smith2019_ritualkingship")
    )
    // the gate suggests merging under ours (the score is high; only the year gap asks)
    assertEquals(issue(p, Issue.Kind.SameWork, "page2024_history").suggested.map(_.wire), Some("map:page1959_history"))

  test("an import of this plan refuses every merged source (merge apply folds it) and says why"):
    val r = plan()
    assert(r.needsApply.exists(_.contains("guterbock1997_perspectives")), r.needsApply.mkString("\n"))
    assert(r.needsApply.exists(_.contains("kuhrt1995_near")))
    // the pair folds into this collection's row: its uid, ours
    assertEquals(r.rows.find(_.foreignUid == uid("w-guterbock")).map(_.uid), Some(uid("h-guterbock")))
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Source, uid("w-guterbock"), uid("h-guterbock"))))
    // the gate is open: its source is held, not folded
    assertEquals(r.rows.find(_.foreignUid == uid("w-page")).map(_.decision), Some(Decision.Held))
    // the goal created on both sides under one slug is one goal
    assert(
      r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Goal, uid("w-goal"), uid("h-goal"))),
      r.apply.folds.toString
    )
    assertEquals(r.rows.find(_.foreignUid == uid("w-new")).map(_.decision), Some(Decision.Insert))
    assertEquals(r.rows.find(_.foreignUid == sharedThere.uid).map(_.decision), Some(Decision.Conflict))

  test("deciding the gate: map makes its conditional issues unconditional, both drops them"):
    val p0 = plan().plan
    val gate = issue(p0, Issue.Kind.SameWork, "page2024_history")
    val year = issue(p0, Issue.Kind.YearDiffers, "page2024_history")
    assertEquals(year.condition, Some(IssueCondition(gate.id, DecisionKind.Map)))
    val mapped = plan(res = resolve(gate.id -> "map:page1959_history")).plan
    assertEquals(issue(mapped, Issue.Kind.YearDiffers, "page2024_history").condition, None)
    assertEquals(
      issue(mapped, Issue.Kind.YearDiffers, "page2024_history").id,
      year.id,
      "the id does not depend on the decision"
    )
    val both = plan(res = resolve(gate.id -> "both", year.id -> "keep-left")).plan
    assert(!both.issues.exists(_.id == year.id))
    assert(
      both.resolutionErrors.exists(e => e.startsWith(year.id) && e.contains("applies only if")),
      both.resolutionErrors.toString
    )
    val bothRows = plan(res = resolve(gate.id -> "both")).rows
    assertEquals(bothRows.find(_.foreignUid == uid("w-page")).map(_.decision), Some(Decision.Insert))

  test("--editions: merge answers the year question by the earliest year; separate keeps two sources"):
    val merged = plan(res = MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Merge)))).plan
    assert(!merged.issues.exists(_.kind == Issue.Kind.SameWork), merged.issues.mkString("\n"))
    val y = issue(merged, Issue.Kind.YearDiffers, "page2024_history")
    assertEquals(y.decision.map(_.wire), Some("keep-left"))
    assertEquals(y.decidedBy, Some(Issue.DecidedBy.Policy))
    val separate = plan(res = MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Separate)))).plan
    assert(!separate.issues.exists(i => i.right.exists(_.label == "page2024_history")), separate.issues.mkString("\n"))
    assertEquals(
      com.histmersion.api.RegisteredPlanMatches.kind
        .all(separate.sources)
        .find(_.rightCitekey == "page2024_history")
        .map(_.outcome),
      Some(MatchOutcome.New)
    )

  test("resolutions: invalid decisions and unknown ids are errors, never ignored; valid ones are counted"):
    val p0 = plan().plan
    val gate = issue(p0, Issue.Kind.SameWork, "page2024_history")
    val coll = issue(p0, Issue.Kind.CitekeyCollision, "smith2019_ritual")
    val r = plan(res =
      resolve(
        gate.id -> "map:nonsense2000_x",
        coll.id -> "keep-left",
        "S-00000000" -> "keep-left",
        issue(p0, Issue.Kind.DoiDiffers, "kuhrt1995_near").id -> "keep-right",
        issue(p0, Issue.Kind.StatusDiffers, "bryce2005_kingdom").id -> "sideways",
        coll.id + "x" -> "both"
      )
    ).plan
    assertEquals(r.resolved, 1)
    assert(
      r.resolutionErrors.exists(e => e.startsWith(gate.id) && e.contains("map takes one of")),
      r.resolutionErrors.toString
    )
    assert(r.resolutionErrors.exists(e => e.startsWith(coll.id) && e.contains("not a decision for this issue")))
    assert(r.resolutionErrors.exists(e => e.startsWith("S-00000000") && e.contains("stale")))
    assert(r.resolutionErrors.exists(_.contains("'sideways' is not a decision")))
    assertEquals(r.resolutionErrors.size, 5, r.resolutionErrors.mkString("\n"))
    // a rename must be a free citekey
    val taken = plan(res = resolve(coll.id -> "rename-right:page1959_history")).plan
    assert(taken.resolutionErrors.exists(_.contains("already a citekey")), taken.resolutionErrors.toString)

  test("an id goes stale when a record it is about changes; the resolution is reported, not re-applied"):
    val p0 = plan().plan
    val doi = issue(p0, Issue.Kind.DoiDiffers, "kuhrt1995_near")
    val changed = foreign.modify(_.sources).setTo(foreign.sources.map(s => if s.uid == uid("w-kuhrt") then s.modify(_.summary).setTo(Some("edited since")) else s)
    )
    val p1 = plan(input(changed), resolve(doi.id -> "keep-left")).plan
    val again = issue(p1, Issue.Kind.DoiDiffers, "kuhrt1995_near")
    assertNotEquals(again.id, doi.id)
    assertEquals(again.decision, None)
    assert(p1.resolutionErrors.exists(_.startsWith(doi.id)), p1.resolutionErrors.toString)
    // …while every issue about other records keeps its id
    val others = p0.issues.filterNot(_.right.exists(_.label == "kuhrt1995_near")).map(_.id).toSet
    assertEquals(others -- p1.issues.map(_.id).toSet, Set.empty)

  test("an old citekey dropped here (renamed back) after the bundle made it is not imported; a newer one is (#827)"):
    val dropped = plan(input().copy(aliasTombstones = Map("harrison1903_old" -> "2026-10-02T00:00:00.000000Z")))
    assert(dropped.skipAliases.contains("harrison1903_old"), dropped.skipAliases.toString)
    assert(dropped.notes.exists(_.contains("harrison1903_old: dropped here")), dropped.notes.toString)
    // the fixture's alias is from 2026-10-01: a tombstone older than that does not hold it back
    val older = plan(input().copy(aliasTombstones = Map("harrison1903_old" -> "2026-09-30T00:00:00.000000Z")))
    assert(!older.notes.exists(_.contains("dropped here")), older.notes.toString)

  test("the importable decisions turn into row decisions"):
    val p0 = plan().plan
    val both = issue(p0, Issue.Kind.ChangedBothSides, "zzshared2001_alpha")
    val gone = p0.issues.find(_.kind == Issue.Kind.DeletedThereChangedHere).get
    val event = issue(p0, Issue.Kind.LabelTaken, "zz_siege")
    val left = plan(res = resolve(both.id -> "keep-left", gone.id -> "keep-right", event.id -> "keep-left"))
    assertEquals(left.rows.find(_.foreignUid == sharedThere.uid).map(_.decision), Some(Decision.Kept))
    assertEquals(left.rows.find(_.foreignUid == goalOriginal.uid).map(_.decision), Some(Decision.Delete))
    assertEquals(left.rows.find(_.foreignUid == uid("w-siege")).map(_.decision), Some(Decision.Skip))
    val right = plan(res = resolve(both.id -> "keep-right"))
    assertEquals(right.rows.find(_.foreignUid == sharedThere.uid).map(_.decision), Some(Decision.FastForward))
    // keep-right on a taken event label is merge apply's
    val apply = plan(res = resolve(event.id -> "keep-right"))
    assert(apply.needsApply.exists(_.startsWith(event.id)), apply.needsApply.mkString("\n"))

  // ── properties ────────────────────────────────────────────────────────────────────────────

  private def shuffled(rnd: scala.util.Random, in: MergePlanner.Input): MergePlanner.Input =
    val f = in.foreign
    in.modify(
      _.foreign).setTo(f.copy(
        sources = rnd.shuffle(f.sources),
        aliases = rnd.shuffle(f.aliases),
        articles = rnd.shuffle(f.articles),
        events = rnd.shuffle(f.events),
        goals = rnd.shuffle(f.goals),
        tombstones = rnd.shuffle(f.tombstones)
      )).modify(_.sources).setTo(rnd.shuffle(in.sources)).modify(_.labels).setTo(in.labels
    )

  test("property: the plan is deterministic, and the same under any order of the input lines"):
    val base = plan().plan
    assertEquals(plan().plan, base)
    val rnd = scala.util.Random(20261004L)
    (1 to 25).foreach { i =>
      val p = MergePlanner
        .plan(shuffled(rnd, input()), sketches, MergeResolutions(), cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))
        .plan
      assertEquals(p.issues, base.issues, s"round $i")
      assertEquals(p.planHash, base.planHash, s"round $i")
      assertEquals(p.sources, base.sources, s"round $i")
    }

  /** A decision an issue allows: its suggestion, else its first option (with an argument it accepts). */
  private def someDecision(i: MergeIssue, rnd: scala.util.Random): String =
    i.suggested.filter(_ => rnd.nextBoolean()).map(_.wire).getOrElse {
      i.options(rnd.nextInt(i.options.size)) match
        case DecisionKind.Map         => s"map:${i.left.get.label}"
        case DecisionKind.RenameRight => s"rename-right:zzrenamed${rnd.nextInt(1000)}_x"
        case DecisionKind.RenameLeft  => s"rename-left:zzrenamed${rnd.nextInt(1000)}_y"
        case DecisionKind.File        => "file:merged.md"
        case k                        => DecisionKind.wireOf(k)
    }

  test("property: adding a decision never adds an issue that was not listed (conditional ones included)"):
    val rnd = scala.util.Random(865L)
    (1 to 20).foreach { round =>
      val decided = scala.collection.mutable.ListBuffer.empty[MergeResolution]
      var current = plan().plan // a var: the plan after each added decision, compared with the next
      while current.issues.exists(i => i.decision.isEmpty && i.condition.isEmpty && i.options.nonEmpty) do
        val open = current.issues.filter(i => i.decision.isEmpty && i.condition.isEmpty && i.options.nonEmpty)
        val pick = open(rnd.nextInt(open.size))
        decided += MergeResolution(pick.id, someDecision(pick, rnd))
        val next = plan(res = MergeResolutions(decided.toList)).plan
        val added = next.issues.map(_.id).toSet -- current.issues.map(_.id).toSet
        assertEquals(added, Set.empty, s"round $round: deciding ${pick.id} added issues")
        assertEquals(next.resolutionErrors, Nil, s"round $round")
        assert(!next.open.exists(_.id == pick.id), s"round $round: ${pick.id} is still open")
        current = next
      assertEquals(current.open, Nil)
    }

  test("property: issue ids hash only their own records — editing one source moves only its issues"):
    val base = plan().plan
    val rnd = scala.util.Random(42L)
    foreign.sources.foreach { s =>
      val edited = foreign.modify(_.sources).setTo(foreign.sources.map(x => if x.uid == s.uid then x.modify(_.summary).setTo(Some(s"edit ${rnd.nextInt()}")) else x)
      )
      val p = plan(input(edited)).plan
      val untouched = base.issues.filterNot(i => i.right.exists(_.uid == s.uid)).map(_.id).toSet
      assertEquals(untouched -- p.issues.map(_.id).toSet, Set.empty, s"editing ${s.citekey}")
      base.issues
        .filter(i => i.right.exists(_.uid == s.uid) && i.kind != Issue.Kind.AliasConflict)
        .foreach(i => assert(!p.issues.exists(_.id == i.id), s"editing ${s.citekey} kept ${i.id}"))
    }

  // ── the resolution file ───────────────────────────────────────────────────────────────────

  test("the template lists every issue; filled in, it reads back as the decisions written"):
    val p0 = plan().plan
    val t = MergeResolutionsFile.template(p0, MergeResolutions())
    p0.issues.foreach(i => assert(t.contains(i.id), s"${i.id} missing from\n$t"))
    assertEquals(MergeResolutionsFile.parse(t), Right(MergeResolutions(Nil, MergePolicy(None), Some(p0.bundleId))))
    val doi = issue(p0, Issue.Kind.DoiDiffers, "kuhrt1995_near").id
    val coll = issue(p0, Issue.Kind.CitekeyCollision, "smith2019_ritual").id
    // (`editions: separate` answers the page gate: two sources, so no gate is left to decide)
    val filled = t
      .replace(s"  $doi:", s"  $doi: keep-right")
      .replace(s"  $coll:", s"  $coll: rename-right:smith2019_ritualkingship")
      .replace("  # editions: merge", "  editions: separate")
    val read = MergeResolutionsFile.parse(filled).fold(e => fail(e.mkString), identity)
    assertEquals(
      read.resolve.map(r => r.issue -> r.decision).toMap,
      Map(doi -> "keep-right", coll -> "rename-right:smith2019_ritualkingship")
    )
    assertEquals(read.policy.editions, Some(EditionsPolicy.Separate))
    // and a partial re-plan shows what remains
    val again = plan(res = read).plan
    assertEquals(again.resolutionErrors, Nil)
    assert(!again.open.exists(_.id == coll))
    // written again, the decisions are kept
    val t2 = MergeResolutionsFile.template(again, read)
    assert(t2.contains(s"  $coll: rename-right:smith2019_ritualkingship"), t2)
    assert(t2.contains("editions: separate"))

  test("the resolution file: unknown keys, bad policies and bad ids are all reported at once"):
    val bad = MergeResolutionsFile.parse(com.histmersion.util.data.exchange.MergeResolutionsFile.Text("bundel: x\npolicy:\n  editions: sometimes\n  colour: red\nresolve: [1, 2]\n"))
    val es = bad.left.getOrElse(fail("parsed"))
    assert(es.exists(_.contains("unknown key 'bundel'")), es.toString)
    assert(es.exists(_.contains("'sometimes' is not merge or separate")))
    assert(es.exists(_.contains("policy: unknown key 'colour'")))
    assert(es.exists(_.contains("resolve: expected a map")))
    assertEquals(MergeResolutionsFile.parse(com.histmersion.util.data.exchange.MergeResolutionsFile.Text("")), Right(MergeResolutions()))
    assertEquals(MergeResolutionsFile.flag(com.histmersion.api.exchange.MergeResolutions.Text.Flag("S-12345678=keep-left")), Right(MergeResolution(com.histmersion.api.exchange.MergeResolution.Issue("S-12345678"), com.histmersion.api.exchange.MergeResolution.Decision("keep-left"))))
    assert(MergeResolutionsFile.flag(com.histmersion.api.exchange.MergeResolutions.Text.Flag("S-12345678")).isLeft)

  test("decisions: the wire form, arguments where they belong"):
    assertEquals(
      MergeDecision.parse(com.histmersion.api.exchange.MergeDecision.Wire("map:guterbock1997_hittite")),
      Right(MergeDecision.withArg(DecisionKind.Map, com.histmersion.api.exchange.MergeDecision.Argument("guterbock1997_hittite")))
    )
    assertEquals(MergeDecision.parse(com.histmersion.api.exchange.MergeDecision.Wire(" keep-left ")), Right(MergeDecision.of(DecisionKind.KeepLeft)))
    assert(MergeDecision.parse(com.histmersion.api.exchange.MergeDecision.Wire("map")).isLeft)
    assert(MergeDecision.parse(com.histmersion.api.exchange.MergeDecision.Wire("keep-left:x")).isLeft)
    assert(MergeDecision.parse(com.histmersion.api.exchange.MergeDecision.Wire("keep_left")).isLeft)
    DecisionKind.values.foreach { k =>
      val d = if k.takesArgument then MergeDecision.withArg(k, com.histmersion.api.exchange.MergeDecision.Argument("a1")) else MergeDecision.of(k)
      assertEquals(MergeDecision.parse(d.wire), Right(d))
    }

  test("the Markdown plan names every open issue, its options and the plan hash"):
    val p = plan().plan
    val md = MergeMarkdown.plan(p, all = true)
    p.issues.foreach(i => assert(md.contains(i.id), i.id))
    assert(md.contains(p.planHash))
    assert(md.contains("`map:page1959_history|page2024_history`"), md)
    assert(md.contains("if "), md)
    assert(!md.contains("_differs"), "no snake-case identifiers in user-facing text")

  // ── what a pair owns (M4) ─────────────────────────────────────────────────────────────────

  private val At = "2026-10-01T09:00:00.000000Z"
  private def att(
      id: String,
      source: String,
      quote: String,
      origin: java.util.UUID
  ): com.histmersion.api.exchange.Bundle.Attestation =
    com.histmersion.api.exchange.Bundle.Attestation(
      uid(id),
      origin,
      uid(source),
      com.histmersion.api.exchange.Bundle.Attestation.TargetType("place"),
      id,
      None,
      Some(quote),
      None,
      None,
      Some(com.histmersion.api.exchange.Bundle.Attestation.CharStart(0)),
      Some(quote.length),
      None,
      None,
      None,
      None,
      None,
      None,
      At,
      At
    )

  test(
    "attestations: re-anchored into the body kept; one that cannot be is blocking, under each alternative of the body question"
  ):
    val workBody = "Kuhrt body, work's OCR"
    val homeBody = "Kuhrt body, home's OCR"
    val es = foreign.modify(_.attestations).setTo(List(att("t-found", "w-kuhrt", "Kuhrt body", work), att("t-lost", "w-kuhrt", "work's OCR", work))
    )
    val in0 = input(es)
    val in = in0.modify(
      _.local).setTo(in0.local.modify(_.attestations).setTo(List(att("t-home", "h-kuhrt", "home's OCR", home)))).modify(_.bodies).setTo(Map(BundleFormat.sha256Hex(workBody) -> workBody, BundleFormat.sha256Hex(homeBody) -> homeBody)
    )
    val r0 = MergePlanner.plan(in, sketches, MergeResolutions(), cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))
    val body = issue(r0.plan, Issue.Kind.BodyDiffers, "kuhrt1995_near")
    val lost =
      r0.plan.issues.find(i => i.kind == Issue.Kind.AttestationUnanchored && i.right.exists(_.uid == uid("t-lost"))).get
    val mine =
      r0.plan.issues.find(i => i.kind == Issue.Kind.AttestationUnanchored && i.left.exists(_.uid == uid("t-home"))).get
    // the body stays ours by default: work's lost attestation is a question now; home's would be one
    // only if work's body were taken — listed, conditional, so deciding the body reveals nothing new
    assertEquals(lost.condition, None)
    assertEquals(lost.severity, Issue.Severity.Blocking)
    assertEquals(mine.condition, Some(IssueCondition(body.id, DecisionKind.KeepRight)))
    assert(
      !r0.plan.issues.exists(i => i.kind == Issue.Kind.AttestationUnanchored && i.right.exists(_.uid == uid("t-found")))
    )
    // the quote that is in home's body arrives at its place there; the lost one, decided drop, is left out for good
    val r1 = MergePlanner.plan(
      in,
      sketches,
      resolve(lost.id -> "drop", issue(r0.plan, Issue.Kind.DoiDiffers, "kuhrt1995_near").id -> "keep-left"),
      cfg,
      List(CataloguePlan, LibraryPlan, ResearchPlan)
    )
    val found = r1.localized.attestations.find(_.uid == uid("t-found")).get
    assertEquals(found.sourceUid, uid("h-kuhrt"))
    assertEquals(homeBody.substring(found.charStart.get, found.charEnd.get), "Kuhrt body")
    assert(!r1.localized.attestations.exists(_.uid == uid("t-lost")))
    assertEquals(r1.apply.tombstones.map(_._1), List(uid("t-lost")))
    assertEquals(r1.rows.find(_.foreignUid == uid("t-lost")).map(_.decision), Some(Decision.Skip))
    // taking work's body instead: the lost one is no question any more, home's own is
    val r2 = MergePlanner.plan(
      in,
      sketches,
      resolve(body.id -> "keep-right"),
      cfg,
      List(CataloguePlan, LibraryPlan, ResearchPlan)
    )
    assert(!r2.plan.issues.exists(_.id == lost.id))
    assert(r2.plan.open.exists(_.id == mine.id), r2.plan.open.mkString("\n"))
    val r3 = MergePlanner.plan(
      in,
      sketches,
      resolve(body.id -> "keep-right", mine.id -> "keep-unanchored"),
      cfg,
      List(CataloguePlan, LibraryPlan, ResearchPlan)
    )
    assertEquals(r3.apply.attestations, List(MergePlanner.AttestationFix(uid("t-home"), None, None, drop = com.histmersion.domain.MergePlanner.AttestationFix.Drop(false))))

  test("edges: a merge that would make a source cite itself drops that edge and says so (an incremental bundle)"):
    // work's olsen cites a record of the bundle's collection that this collection holds under home's
    // olsen (a redirect from an earlier merge): folding the two would make olsen cite itself
    val earlier = uid("w-olsen-earlier")
    val es = foreign.modify(_.edges).setTo(List(
        com.histmersion.api.exchange.Bundle.Edge(uid("w-olsen"), earlier, com.histmersion.api.exchange.Bundle.Edge.Kind("superseded_by")),
        com.histmersion.api.exchange.Bundle.Edge(uid("w-olsen"), uid("w-new"), com.histmersion.api.exchange.Bundle.Edge.Kind("references"))
      )
    )
    val in = input(es).copy(uidAliases = Map(earlier -> uid("h-olsen")))
    val r = MergePlanner.plan(in, sketches, MergeResolutions(), cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))
    val olsen = r.localized.edges.filter(_.srcUid == uid("h-olsen"))
    assertEquals(olsen, List(com.histmersion.api.exchange.Bundle.Edge(uid("h-olsen"), uid("w-new"), com.histmersion.api.exchange.Bundle.Edge.Kind("references"))))
    assertEquals(r.apply.tally.selfLoopsDropped, 1)
    assert(r.plan.info.exists(_.contains("from a source to itself")), r.plan.info.mkString("\n"))

  test(
    "articles: a common version in the two histories merges three-way, or asks with the conflicting regions counted"
  ):
    val base = "Intro.\n\nMiddle.\n\nEnd."
    val baseSha = BundleFormat.sha256Hex(base)
    val shared = article("shared-art", "zz_shared", base)
    val theirs = shared.modify(_.body).setTo("Intro (work).\n\nMiddle.\n\nEnd.")
    val ours = shared.modify(_.body).setTo("Intro.\n\nMiddle.\n\nEnd (home).").modify(_.origin).setTo(work)
    val version =
      com.histmersion.api.exchange.Bundle.ArticleVersion(uid("v-base"), work, shared.uid, baseSha, None, At, At)
    val es = foreign.copy(articles = foreign.articles :+ theirs, versions = List(version))
    val in0 = input(es)
    val ledgered = in0.ledger + (shared.uid -> MergePlanner.LedgerEntry(
      BundleFormat.stamp(shared).rev,
      shared.uid,
      Some(BundleFormat.stamp(shared).rev)
    ))
    val in = in0.modify(
      _.ledger).setTo(ledgered).modify(_.local).setTo(in0.local.modify(_.articles).setTo(in0.local.articles :+ ours)).modify(_.articleHistory).setTo(Map(shared.uid -> List(baseSha -> base))
    )
    val r = MergePlanner.plan(in, sketches, MergeResolutions(), cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))
    assert(!r.plan.issues.exists(_.left.exists(_.label == "zz_shared")), r.plan.issues.mkString("\n"))
    assertEquals(
      r.localized.articles.find(_.uid == shared.uid).map(_.body),
      Some("Intro (work).\n\nMiddle.\n\nEnd (home).")
    )
    assertEquals(r.rows.find(_.foreignUid == shared.uid).map(_.decision), Some(Decision.FastForward))
    assertEquals(r.apply.reviews, List("zz_shared"))
    assert(r.apply.snapshots(shared.uid))
    // both edited the same paragraph: a question, with the number of conflicting regions
    val clash = es.modify(_.articles).setTo(foreign.articles :+ theirs.modify(_.body).setTo("Intro.\n\nMiddle.\n\nEnd (work)."))
    val c = MergePlanner.plan(
      in.modify(_.foreign).setTo(clash).modify(_.manifest).setTo(manifest(clash)),
      sketches,
      MergeResolutions(),
      cfg,
      List(CataloguePlan, LibraryPlan, ResearchPlan)
    )
    val q = c.plan.issues.find(_.left.exists(_.label == "zz_shared")).get
    assertEquals(q.kind, Issue.Kind.ChangedBothSides)
    assert(q.title.contains("1 conflicting region"), q.title)
    // decided file:, the merged text is what is written
    val f = MergePlanner.plan(
      in.modify(_.foreign).setTo(clash).modify(_.manifest).setTo(manifest(clash)),
      sketches,
      MergeResolutions(List(MergeResolution(q.id, com.histmersion.api.exchange.MergeResolution.Decision("file:merged.md"), Some(com.histmersion.api.exchange.MergeResolution.Text("By hand."))))),
      cfg,
      List(CataloguePlan, LibraryPlan, ResearchPlan)
    )
    assertEquals(f.localized.articles.find(_.uid == shared.uid).map(_.body), Some("By hand."))

  test("property: whatever is decided, the merged lines hold no edge to a source that is neither in them nor here"):
    val rnd = scala.util.Random(445L)
    (1 to 15).foreach { round =>
      val decided = scala.collection.mutable.ListBuffer.empty[MergeResolution]
      var current = plan(res = MergeResolutions()) // a var: the plan after each added decision
      while current.plan.issues.exists(i => i.decision.isEmpty && i.condition.isEmpty && i.options.nonEmpty) do
        val open = current.plan.issues.filter(i => i.decision.isEmpty && i.condition.isEmpty && i.options.nonEmpty)
        val pick = open(rnd.nextInt(open.size))
        decided += MergeResolution(pick.id, someDecision(pick, rnd), Some(com.histmersion.api.exchange.MergeResolution.Text("text")))
        current = plan(res = MergeResolutions(decided.toList))
      val known = current.localized.sources.map(_.uid).toSet ++ input().local.sources.map(_.uid)
      current.localized.edges.foreach(e => assert(known(e.srcUid) && known(e.dstUid), s"round $round: dangling $e"))
      // a folded record is never written twice under one uid
      assertEquals(current.localized.sources.map(_.uid).distinct.size, current.localized.sources.size, s"round $round")
      assert(current.apply.folds.groupBy(_.foreignUid).forall(_._2.size == 1), s"round $round")
    }

  test(
    "the Markdown apply report: what was merged, the reviews, the idempotent re-plan; a refusal says nothing was written"
  ):
    val r = plan()
    val done = MergeApplyResult(
      foreign.sources.head.uid,
      com.histmersion.api.exchange.Bundle.Instance(work, Some(com.histmersion.api.exchange.Bundle.Instance.Label("work"))),
      applied = com.histmersion.api.exchange.MergeApplyResult.Applied(true),
      r.plan.planHash,
      mergeId = Some(uid("merge")),
      backup = Some(com.histmersion.api.exchange.MergeApplyResult.Backup("histmersion-20261004-1000.dump")),
      counts = List(com.histmersion.api.exchange.Bundle.KindCounts(Bundle.Kind.Source, inserted = com.histmersion.api.exchange.Bundle.KindCounts.Inserted(2))),
      tally = r.apply.tally,
      reviews = List(com.histmersion.api.exchange.MergeApplyResult.Reviews("zz_shared")),
      logEntry = Some(com.histmersion.api.exchange.MergeApplyResult.LogEntry(590)),
      replanChanges = Some(com.histmersion.api.exchange.MergeApplyResult.ReplanChanges(0))
    )
    val md = MergeMarkdown.applied(done)
    assert(md.contains("histmersion-20261004-1000.dump"), md)
    assert(md.contains("+2 new"), md)
    assert(md.contains(s"${r.apply.tally.sourcesMerged} merged"), md)
    assert(md.contains("`zz_shared`") && md.contains("log entry #590"), md)
    assert(md.contains("0 changes (idempotent)"), md)
    val refused =
      MergeMarkdown.applied(done.modify(_.applied).setTo(false).modify(_.mergeId).setTo(None).modify(_.problems).setTo(List("the plan is x, not y")))
    assert(refused.contains("nothing was written") && refused.contains("the plan is x, not y"), refused)
    assert(!md.contains("_merged") && !md.contains("Kept"), "no identifiers in user-facing text")
