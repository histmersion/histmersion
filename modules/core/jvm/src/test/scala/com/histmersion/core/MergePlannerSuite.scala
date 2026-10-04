package com.histmersion.core

import BundlePlan.Decision
import MergeFixture.*

/** `merge plan` as a pure function over the crafted fixture (docs/research-merge-import.md §8): the
  * whole issue list in one run, conditional issues, resolutions (stale, invalid, partial), the
  * editions policy, and the properties — determinism, stable ids, monotone resolution. */
class MergePlannerSuite extends munit.FunSuite with Bounded:

  private def plan(in: MergePlanner.Input = input(), res: MergeResolutions = MergeResolutions()): MergePlanner.Result =
    MergePlanner.plan(in, sketches, res, cfg)

  private def resolve(pairs: (String, String)*): MergeResolutions =
    MergeResolutions(pairs.toList.map((i, d) => MergeResolution(i, d)))

  private def issue(p: MergePlan, kind: IssueKind, right: String): MergeIssue =
    p.issues.find(i => i.kind == kind && i.right.exists(_.label == right)).getOrElse(fail(s"no $kind on $right in\n${p.issues.mkString("\n")}"))

  test("the crafted fixture: exactly the expected issues, in one run"):
    val p = plan().plan
    val got = summarize(p)
    assertEquals(got -- expected, Set.empty, "unexpected issues")
    assertEquals(expected -- got, Set.empty, "missing issues")
    assertEquals(p.issues.size, expected.size, p.issues.mkString("\n"))
    assertEquals(p.matches, MatchCounts(sources = 10, byUid = 1, byStrongKey = 2, byScore = 4, undecided = 1, unmatched = 2))
    assertEquals(p.problems, Nil)
    assertEquals(p.resolutionErrors, Nil)
    // ids are <letter>-<8 hex>, unique
    assert(p.issues.forall(_.id.matches("[A-Z]-[0-9a-f]{8}")), p.issues.map(_.id).toString)
    assertEquals(p.issues.map(_.id).distinct.size, p.issues.size)

  test("the evidence behind the matches: strong keys, the score, a DOI on one side only is neutral"):
    val p = plan().plan
    val byRight = p.sources.map(m => m.rightCitekey -> m).toMap
    assertEquals(byRight("kuhrt1995_near").method, MatchMethod.StrongKey)
    assert(byRight("kuhrt1995_near").why.exists(_.contains("sha256")), byRight("kuhrt1995_near").why.toString)
    assertEquals(byRight("bryce2005_kingdom").leftCitekey, Some("bryce2005_kingdom_hittites"))
    assertEquals(byRight("guterbock1997_perspectives").outcome, MatchOutcome.AutoMerge)
    assertEquals(byRight("guterbock1997_perspectives").leftCitekey, Some("guterbock1997_hittite"))
    assert(!byRight("guterbock1997_perspectives").why.exists(_.toLowerCase.contains("doi")))
    assertEquals(byRight("latacz2023_troy").leftCitekey, Some("latacz2004_troy"))
    assert(byRight("latacz2023_troy").why.exists(_.contains("old citekey")))
    assertEquals(byRight("harrison1903_prolegomena").leftCitekey, Some("harrison1903_prolegomena_greek"))
    assertEquals(byRight("olsen2015_penelope").outcome, MatchOutcome.AutoMerge)
    assertEquals(byRight("page2024_history").outcome, MatchOutcome.NeedsDecision)
    assertEquals(byRight("smith2019_ritual").outcome, MatchOutcome.New)
    // the citekey collision suggests the next title word
    assertEquals(issue(p, IssueKind.CitekeyCollision, "smith2019_ritual").suggested.map(_.wire),
      Some("rename-right:smith2019_ritualkingship"))
    // the gate suggests merging under ours (the score is high; only the year gap asks)
    assertEquals(issue(p, IssueKind.SameWork, "page2024_history").suggested.map(_.wire), Some("map:page1959_history"))

  test("an import of this plan holds every merged source back and says why"):
    val r = plan()
    assert(r.needsApply.exists(_.contains("guterbock1997_perspectives")), r.needsApply.mkString("\n"))
    assert(r.needsApply.exists(_.contains("kuhrt1995_near")))
    assert(r.rows.filter(_.foreignUid == uid("w-guterbock")).forall(_.decision == Decision.Held))
    assertEquals(r.rows.find(_.foreignUid == uid("w-new")).map(_.decision), Some(Decision.Insert))
    assertEquals(r.rows.find(_.foreignUid == sharedThere.uid).map(_.decision), Some(Decision.Conflict))

  test("deciding the gate: map makes its conditional issues unconditional, both drops them"):
    val p0   = plan().plan
    val gate = issue(p0, IssueKind.SameWork, "page2024_history")
    val year = issue(p0, IssueKind.YearDiffers, "page2024_history")
    assertEquals(year.condition, Some(IssueCondition(gate.id, DecisionKind.Map)))
    val mapped = plan(res = resolve(gate.id -> "map:page1959_history")).plan
    assertEquals(issue(mapped, IssueKind.YearDiffers, "page2024_history").condition, None)
    assertEquals(issue(mapped, IssueKind.YearDiffers, "page2024_history").id, year.id, "the id does not depend on the decision")
    val both = plan(res = resolve(gate.id -> "both", year.id -> "keep-left")).plan
    assert(!both.issues.exists(_.id == year.id))
    assert(both.resolutionErrors.exists(e => e.startsWith(year.id) && e.contains("applies only if")), both.resolutionErrors.toString)
    val bothRows = plan(res = resolve(gate.id -> "both")).rows
    assertEquals(bothRows.find(_.foreignUid == uid("w-page")).map(_.decision), Some(Decision.Insert))

  test("--editions: merge answers the year question by the earliest year; separate keeps two sources"):
    val merged = plan(res = MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Merge)))).plan
    assert(!merged.issues.exists(_.kind == IssueKind.SameWork), merged.issues.mkString("\n"))
    val y = issue(merged, IssueKind.YearDiffers, "page2024_history")
    assertEquals(y.decision.map(_.wire), Some("keep-left"))
    assertEquals(y.decidedBy, Some(DecidedBy.Policy))
    val separate = plan(res = MergeResolutions(policy = MergePolicy(Some(EditionsPolicy.Separate)))).plan
    assert(!separate.issues.exists(i => i.right.exists(_.label == "page2024_history")), separate.issues.mkString("\n"))
    assertEquals(separate.sources.find(_.rightCitekey == "page2024_history").map(_.outcome), Some(MatchOutcome.New))

  test("resolutions: invalid decisions and unknown ids are errors, never ignored; valid ones are counted"):
    val p0   = plan().plan
    val gate = issue(p0, IssueKind.SameWork, "page2024_history")
    val coll = issue(p0, IssueKind.CitekeyCollision, "smith2019_ritual")
    val r = plan(res = resolve(
      gate.id -> "map:nonsense2000_x",
      coll.id -> "keep-left",
      "S-00000000" -> "keep-left",
      issue(p0, IssueKind.DoiDiffers, "kuhrt1995_near").id -> "keep-right",
      issue(p0, IssueKind.StatusDiffers, "bryce2005_kingdom").id -> "sideways",
      coll.id + "x" -> "both")).plan
    assertEquals(r.resolved, 1)
    assert(r.resolutionErrors.exists(e => e.startsWith(gate.id) && e.contains("map takes one of")), r.resolutionErrors.toString)
    assert(r.resolutionErrors.exists(e => e.startsWith(coll.id) && e.contains("not a decision for this issue")))
    assert(r.resolutionErrors.exists(e => e.startsWith("S-00000000") && e.contains("stale")))
    assert(r.resolutionErrors.exists(_.contains("'sideways' is not a decision")))
    assertEquals(r.resolutionErrors.size, 5, r.resolutionErrors.mkString("\n"))
    // a rename must be a free citekey
    val taken = plan(res = resolve(coll.id -> "rename-right:page1959_history")).plan
    assert(taken.resolutionErrors.exists(_.contains("already a citekey")), taken.resolutionErrors.toString)

  test("an id goes stale when a record it is about changes; the resolution is reported, not re-applied"):
    val p0  = plan().plan
    val doi = issue(p0, IssueKind.DoiDiffers, "kuhrt1995_near")
    val changed = foreign.copy(sources = foreign.sources.map(s =>
      if s.uid == uid("w-kuhrt") then s.copy(summary = Some("edited since")) else s))
    val p1 = plan(input(changed), resolve(doi.id -> "keep-left")).plan
    val again = issue(p1, IssueKind.DoiDiffers, "kuhrt1995_near")
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
    val both  = issue(p0, IssueKind.ChangedBothSides, "zzshared2001_alpha")
    val gone  = p0.issues.find(_.kind == IssueKind.DeletedThereChangedHere).get
    val event = issue(p0, IssueKind.LabelTaken, "zz_siege")
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
    in.copy(
      foreign = f.copy(sources = rnd.shuffle(f.sources), aliases = rnd.shuffle(f.aliases), articles = rnd.shuffle(f.articles),
        events = rnd.shuffle(f.events), goals = rnd.shuffle(f.goals), tombstones = rnd.shuffle(f.tombstones)),
      sources = rnd.shuffle(in.sources),
      labels = in.labels)

  test("property: the plan is deterministic, and the same under any order of the input lines"):
    val base = plan().plan
    assertEquals(plan().plan, base)
    val rnd = scala.util.Random(20261004L)
    (1 to 25).foreach { i =>
      val p = MergePlanner.plan(shuffled(rnd, input()), sketches, MergeResolutions(), cfg).plan
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
    val rnd  = scala.util.Random(42L)
    foreign.sources.foreach { s =>
      val edited = foreign.copy(sources = foreign.sources.map(x =>
        if x.uid == s.uid then x.copy(summary = Some(s"edit ${rnd.nextInt()}")) else x))
      val p = plan(input(edited)).plan
      val untouched = base.issues.filterNot(i => i.right.exists(_.uid == s.uid)).map(_.id).toSet
      assertEquals(untouched -- p.issues.map(_.id).toSet, Set.empty, s"editing ${s.citekey}")
      base.issues.filter(i => i.right.exists(_.uid == s.uid) && i.kind != IssueKind.AliasConflict).foreach(i =>
        assert(!p.issues.exists(_.id == i.id), s"editing ${s.citekey} kept ${i.id}"))
    }

  // ── the resolution file ───────────────────────────────────────────────────────────────────

  test("the template lists every issue; filled in, it reads back as the decisions written"):
    val p0 = plan().plan
    val t  = MergeResolutionsFile.template(p0, MergeResolutions())
    p0.issues.foreach(i => assert(t.contains(i.id), s"${i.id} missing from\n$t"))
    assertEquals(MergeResolutionsFile.parse(t), Right(MergeResolutions(Nil, MergePolicy(None), Some(p0.bundleId))))
    val doi  = issue(p0, IssueKind.DoiDiffers, "kuhrt1995_near").id
    val coll = issue(p0, IssueKind.CitekeyCollision, "smith2019_ritual").id
    // (`editions: separate` answers the page gate: two sources, so no gate is left to decide)
    val filled = t.replace(s"  $doi:", s"  $doi: keep-right").replace(s"  $coll:", s"  $coll: rename-right:smith2019_ritualkingship")
      .replace("  # editions: merge", "  editions: separate")
    val read = MergeResolutionsFile.parse(filled).fold(e => fail(e.mkString), identity)
    assertEquals(read.resolve.map(r => r.issue -> r.decision).toMap,
      Map(doi -> "keep-right", coll -> "rename-right:smith2019_ritualkingship"))
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
    val bad = MergeResolutionsFile.parse("bundel: x\npolicy:\n  editions: sometimes\n  colour: red\nresolve: [1, 2]\n")
    val es  = bad.left.getOrElse(fail("parsed"))
    assert(es.exists(_.contains("unknown key 'bundel'")), es.toString)
    assert(es.exists(_.contains("'sometimes' is not merge or separate")))
    assert(es.exists(_.contains("policy: unknown key 'colour'")))
    assert(es.exists(_.contains("resolve: expected a map")))
    assertEquals(MergeResolutionsFile.parse(""), Right(MergeResolutions()))
    assertEquals(MergeResolutionsFile.flag("S-12345678=keep-left"), Right(MergeResolution("S-12345678", "keep-left")))
    assert(MergeResolutionsFile.flag("S-12345678").isLeft)

  test("decisions: the wire form, arguments where they belong"):
    assertEquals(MergeDecision.parse("map:guterbock1997_hittite"), Right(MergeDecision.withArg(DecisionKind.Map, "guterbock1997_hittite")))
    assertEquals(MergeDecision.parse(" keep-left "), Right(MergeDecision.of(DecisionKind.KeepLeft)))
    assert(MergeDecision.parse("map").isLeft)
    assert(MergeDecision.parse("keep-left:x").isLeft)
    assert(MergeDecision.parse("keep_left").isLeft)
    DecisionKind.values.foreach { k =>
      val d = if k.takesArgument then MergeDecision.withArg(k, "a1") else MergeDecision.of(k)
      assertEquals(MergeDecision.parse(d.wire), Right(d))
    }

  test("the Markdown plan names every open issue, its options and the plan hash"):
    val p  = plan().plan
    val md = MergeMarkdown.plan(p, all = true)
    p.issues.foreach(i => assert(md.contains(i.id), i.id))
    assert(md.contains(p.planHash))
    assert(md.contains("`map:page1959_history|page2024_history`"), md)
    assert(md.contains("if "), md)
    assert(!md.contains("_differs"), "no snake-case identifiers in user-facing text")
