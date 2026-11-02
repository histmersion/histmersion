package com.histmersion.domain

import com.histmersion.domain.catalogue.events.EventPlan
import com.histmersion.domain.catalogue.places.PlacePlan
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.domain.research.ResearchPlan

import com.histmersion.util.data.exchange.{BundlePlan, MergeResolutionsFile}
import com.histmersion.config.*
import com.histmersion.api.MergeFixture
import com.histmersion.config.given
import com.histmersion.api.given

import BundlePlan.Decision
import MergeFixture.*
import io.circe.Json
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.{
  BundleArticleAlias,
  BundleEventAlias,
  BundleFiles,
  BundleGoalAlias,
  BundleGoalLink,
  BundleGoalSchedule,
  BundlePeriod,
  BundlePlace,
  BundlePlaceAlias,
  BundlePlaceName,
  BundleRange,
  IssueHunk,
  IssueSpan,
  MergeResolution,
  MergeResolutions,
  MergeResolutionsText
}
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.data.exchange.Issue

/** What the merge carries and decides beyond M4–M5 (docs/research-merge-import.md §7.8): places and named periods of a
  * collection's own, events matched across two ids, a goal's plan, links and schedules (#881), old article slugs, and
  * the detail the review screen shows beside an issue.
  */
class MergeExtrasSuite extends munit.FunSuite with Bounded:

  private val At = "2026-10-01T09:00:00.000000Z"

  private def run(
      es: BundleEntities,
      in0: MergePlanner.Input => MergePlanner.Input = identity,
      res: MergeResolutions = MergeResolutions()
  ): MergePlanner.Result =
    MergePlanner.plan(in0(input(es)), sketches, res, cfg, List(CataloguePlan, LibraryPlan, ResearchPlan))

  private def resolve(pairs: (String, String)*): MergeResolutions =
    MergeResolutions(pairs.toList.map((i, d) => MergeResolution(i, d)))

  // hex EWKB of POINT(32.5 40) and POINT(33 41), SRID 4326
  private val PointA = "0101000020e610000000000000004040400000000000004440"
  private val PointB = "0101000020e610000000000000008040400000000000804440"

  private def place(
      id: String,
      pid: String,
      label: String,
      geom: Option[String] = Some(PointA),
      origin: java.util.UUID = work
  ): BundlePlace =
    BundlePlace(
      uid(id),
      origin,
      pid,
      label,
      "site",
      geom,
      Some(BundleRange(Some(-1650), Some(-1180))),
      None,
      None,
      List(BundlePlaceName(label, None, None)),
      Nil,
      Nil,
      Nil,
      At
    )

  private def period(
      id: String,
      pid: String,
      label: String,
      from: Long,
      to: Long,
      origin: java.util.UUID = work
  ): BundlePeriod =
    BundlePeriod(uid(id), origin, pid, label, Some("me"), Some(BundleRange(Some(from), Some(to))), None, None, At)

  // ── places ────────────────────────────────────────────────────────────────────────────────────

  test("places: a new one is inserted; the same id with the same geometry here is one place, ours kept"):
    val fresh = place("w-p1", "zz_hattusa", "Hattusa")
    val same = place("w-p2", "zz_kanesh", "Kanesh")
    val ours = same.copy(uid = uid("h-p2"), origin = home, names = List(BundlePlaceName("Kültepe", Some("tr"), None)))
    val r = run(foreign.copy(places = List(fresh, same)), in => in.copy(local = in.local.copy(places = List(ours))))
    assertEquals(r.rows.find(_.foreignUid == fresh.uid).map(_.decision), Some(Decision.Insert))
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Place, same.uid, ours.uid)), r.apply.folds.toString)
    // the fold carries the bundle's name ours lacked: written as ours, both names
    assertEquals(
      r.localized.places.find(_.uid == ours.uid).map(_.names.map(_.name).toSet),
      Some(Set("Kanesh", "Kültepe"))
    )
    assertEquals(r.apply.tally.placesMerged, 1)
    assert(!r.plan.issues.exists(_.entity == Bundle.Kind.Place), r.plan.issues.mkString("\n"))
    assert(r.plan.info.exists(_.contains("is the same place")), r.plan.info.mkString("\n"))

  test("places: one id, two geometries is a question with both on the map; keep-right takes the bundle's"):
    val theirs = place("w-p3", "zz_ugarit", "Ugarit", Some(PointB))
    val ours = place("h-p3", "zz_ugarit", "Ugarit", Some(PointA), home)
    val in: MergePlanner.Input => MergePlanner.Input = i => i.copy(local = i.local.copy(places = List(ours)))
    val p = run(foreign.copy(places = List(theirs)), in).plan
    val q = p.issues.find(_.kind == Issue.Kind.PlaceDiffers).getOrElse(fail(p.issues.mkString("\n")))
    assert(q.id.startsWith("P-") && q.open, q.toString)
    assertEquals(
      q.detail.flatMap(_.place).map(x => (x.leftGeometry, x.rightGeometry)),
      Some((Some(PointA), Some(PointB)))
    )
    val kept = run(foreign.copy(places = List(theirs)), in, resolve(q.id -> "keep-left"))
    assertEquals(kept.rows.find(_.foreignUid == theirs.uid).map(_.decision), Some(Decision.Kept))
    val right = run(foreign.copy(places = List(theirs)), in, resolve(q.id -> "keep-right"))
    assertEquals(
      right.rows.find(_.foreignUid == theirs.uid).map(r => (r.uid, r.decision)),
      Some((ours.uid, Decision.FastForward))
    )
    assertEquals(right.localized.places.find(_.uid == ours.uid).flatMap(_.geom), Some(PointB))

  test("places: a place matched here under another id (label and geometry) is one, and events point at ours"):
    val theirs = place("w-p4", "work_troy", "Troy")
    val ours = place("h-p4", "pleiades_550595", "Troy", origin = home)
    val ev = event("w-ev-troy", "zz_troy_fire", "Troy burns").copy(placeId = Some("work_troy"))
    val r = run(
      foreign.copy(places = List(theirs), events = List(ev)),
      i => i.copy(local = i.local.copy(places = List(ours)), placeMatches = Map(theirs.uid -> "pleiades_550595"))
    )
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Place, theirs.uid, ours.uid)))
    assertEquals(r.localized.events.find(_.uid == ev.uid).flatMap(_.placeId), Some("pleiades_550595"))
    assertEquals(r.plan.problems, Nil)

  test("places: an event whose place only the bundle carries is no problem; one nobody has still is"):
    val p = place("w-p5", "zz_new_site", "New site")
    val ok = event("w-ev-ok", "zz_ev_ok", "At the new site").copy(placeId = Some("zz_new_site"))
    val no = event("w-ev-no", "zz_ev_no", "Nowhere").copy(placeId = Some("zz_nowhere"))
    val r = run(foreign.copy(places = List(p), events = List(ok, no)))
    assertEquals(r.plan.problems.filter(_.contains("gazetteer")).map(_.takeWhile(_ != ':')), List("event zz_ev_no"))

  // #888: a place renamed over there (a new id under the same uid) used to keep our id, and the
  // bundle's events naming the new id pointed at nothing
  test("renamedPlaces: one of ours under a new id; whether that id is another place's here; a redirected one is not"):
    val ours = place("h-p6", "zz_old_site", "Site", origin = home)
    val theirs = ours.copy(id = "zz_new_site")
    val other = place("h-p7", "zz_new_site", "Another site", origin = home)
    assertEquals(PlacePlan.renamedPlaces(List(theirs), List(ours), Set.empty), List((theirs, "zz_old_site", false)))
    assertEquals(
      PlacePlan.renamedPlaces(List(theirs), List(ours, other), Set.empty),
      List((theirs, "zz_old_site", true))
    )
    assertEquals(PlacePlan.renamedPlaces(List(ours), List(ours), Set.empty), Nil)
    assertEquals(PlacePlan.renamedPlaces(List(theirs), List(ours), Set(theirs.uid)), Nil)

  test("places: a rename over there is followed — the new id is written, events keep pointing at it"):
    val ours = place("h-p8", "zz_old_site", "Site", origin = home)
    val theirs = ours.copy(id = "zz_new_site")
    val ev = event("w-ev-site", "zz_at_site", "At the site").copy(placeId = Some("zz_new_site"))
    val r = run(
      foreign.copy(places = List(theirs), events = List(ev)),
      i => i.copy(local = i.local.copy(places = List(ours)))
    )
    assertEquals(r.localized.places.find(_.uid == ours.uid).map(_.id), Some("zz_new_site"))
    assertEquals(r.localized.events.find(_.uid == ev.uid).flatMap(_.placeId), Some("zz_new_site"))
    assert(!r.plan.issues.exists(_.kind == Issue.Kind.PlaceDiffers), r.plan.issues.mkString("\n"))
    assertEquals(r.plan.problems, Nil)
    assert(
      r.plan.info.exists(_.contains("renamed zz_new_site in the bundle — renamed here too")),
      r.plan.info.mkString("\n")
    )

  test("places: a rename over there onto an id another place has here keeps ours, and points the bundle at it"):
    val ours = place("h-p9", "zz_old_site", "Site", origin = home)
    val other = place("h-p10", "zz_new_site", "Another site", Some(PointB), origin = home)
    val theirs = ours.copy(id = "zz_new_site")
    val ev = event("w-ev-site2", "zz_at_site2", "At the site").copy(placeId = Some("zz_new_site"))
    val r = run(
      foreign.copy(places = List(theirs), events = List(ev)),
      i => i.copy(local = i.local.copy(places = List(ours, other)))
    )
    assertEquals(r.localized.places.find(_.uid == ours.uid).map(_.id), Some("zz_old_site"))
    assertEquals(r.localized.events.find(_.uid == ev.uid).flatMap(_.placeId), Some("zz_old_site"))
    // not a place "added on both sides": no question about the other place
    assert(!r.plan.issues.exists(_.kind == Issue.Kind.PlaceDiffers), r.plan.issues.mkString("\n"))
    assert(
      r.plan.info.exists(_.contains("here zz_new_site is another place — it keeps its id")),
      r.plan.info.mkString("\n")
    )

  test("named periods: the same id and span is one; a different span asks; a match by label folds"):
    val same = period("w-n1", "zz_lba", "Late Bronze Age", -1600, -1200)
    val differ = period("w-n2", "zz_iron", "Iron Age", -1200, -550)
    val other = period("w-n3", "work_mba", "Middle Bronze Age", -2000, -1600)
    val local = List(
      same.copy(uid = uid("h-n1"), origin = home),
      period("h-n2", "zz_iron", "Iron Age", -1150, -586, home),
      period("h-n3", "periodo_mba", "Middle Bronze Age", -2100, -1550, home)
    )
    val r = run(
      foreign.copy(periods = List(same, differ, other)),
      i => i.copy(local = i.local.copy(periods = local), periodMatches = Map(other.uid -> "periodo_mba"))
    )
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Period, same.uid, uid("h-n1"))))
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Period, other.uid, uid("h-n3"))))
    val q = r.plan.issues.find(_.kind == Issue.Kind.NamedPeriodDiffers).getOrElse(fail(r.plan.issues.mkString("\n")))
    assert(q.id.startsWith("N-") && q.title.contains("-1200..-550"), q.title)
    assertEquals(r.apply.tally.periodsMerged, 2)

  // ── events across ids ─────────────────────────────────────────────────────────────────────────

  test("events: the same label, overlapping dates and the same place under two ids are one event"):
    val theirs =
      event("w-ev-k", "work_kadesh", "Battle of Kadesh", from = -1274, to = -1274).copy(placeId = Some("zz_kadesh"))
    val ours = event("h-ev-k", "kadesh_1274", "battle of  Kadesh", home, -1275, -1274).copy(placeId = Some("zz_kadesh"))
    val elsewhere = theirs.copy(uid = uid("w-ev-k2"), id = "work_kadesh2", placeId = Some("zz_other"))
    val in: MergePlanner.Input => MergePlanner.Input =
      i => i.copy(local = i.local.copy(events = i.local.events :+ ours), places = Set("zz_kadesh", "zz_other"))
    val r = run(foreign.copy(events = List(theirs)), in)
    assert(r.apply.folds.contains(MergePlanner.Fold(Bundle.Kind.Event, theirs.uid, ours.uid)), r.apply.folds.toString)
    assertEquals(r.localized.events.find(_.uid == ours.uid).map(_.id), Some("kadesh_1274"))
    assert(r.plan.info.exists(_.contains("same label, dates and place")), r.plan.info.mkString("\n"))
    // another place: two events
    val r2 = run(foreign.copy(events = List(elsewhere)), in)
    assertEquals(r2.rows.find(_.foreignUid == elsewhere.uid).map(_.decision), Some(Decision.Insert))

  test("MergeFold.sameEventAcrossIds: label fingerprint, a shared year, one place (or none on both)"):
    val a = event("a", "a", "Fall of Hattusa", from = -1190, to = -1180)
    assert(EventPlan.sameEventAcrossIds(a, a.copy(id = "b", label = "fall of HATTUSA")))
    assert(!EventPlan.sameEventAcrossIds(a, a.copy(id = "b", notBefore = Some(-1100), notAfter = Some(-1050))))
    assert(!EventPlan.sameEventAcrossIds(a, a.copy(id = "b", placeId = Some("x"))))
    assert(!EventPlan.sameEventAcrossIds(a, a.copy(id = "b", label = "Sack of Hattusa")))

  // ── goals (#881) ──────────────────────────────────────────────────────────────────────────────

  private val planA = Json.obj("goal" -> Json.fromString("g"), "rationale" -> Json.fromString("A"))
  private val planB = Json.obj("goal" -> Json.fromString("g"), "rationale" -> Json.fromString("B"))

  test("MergeFold.planOutcome: equal or one-sided is no question; the later approval wins; else a question"):
    val g = goal("g", "g", "G")
    assertEquals(MergeFold.planOutcome(g.copy(plan = Some(planA)), g), MergeFold.PlanOutcome.Ours)
    assertEquals(MergeFold.planOutcome(g, g.copy(plan = Some(planA))), MergeFold.PlanOutcome.Theirs)
    assertEquals(
      MergeFold.planOutcome(g.copy(plan = Some(planA)), g.copy(plan = Some(planA))),
      MergeFold.PlanOutcome.Ours
    )
    assertEquals(
      MergeFold.planOutcome(
        g.copy(plan = Some(planA), planApprovedAt = Some("2026-01-01")),
        g.copy(plan = Some(planB), planApprovedAt = Some("2026-02-01"))
      ),
      MergeFold.PlanOutcome.Theirs
    )
    assertEquals(
      MergeFold.planOutcome(
        g.copy(plan = Some(planA), planApprovedAt = Some("2026-03-01")),
        g.copy(plan = Some(planB), planApprovedAt = Some("2026-02-01"))
      ),
      MergeFold.PlanOutcome.Ours
    )
    assertEquals(
      MergeFold.planOutcome(g.copy(plan = Some(planA)), g.copy(plan = Some(planB), planApprovedAt = Some("x"))),
      MergeFold.PlanOutcome.Differ
    )

  test(
    "MergeFold.keepLocalSchedules: links unioned (by uid, else label), our schedules kept, theirs only where we have none"
  ):
    val l = goal("g", "g", "G").copy(
      links = Some(List(BundleGoalLink("source", "a2001_x", Some(uid("s-a")), None, At))),
      schedules = Some(List(BundleGoalSchedule("currency", "@weekly", true, None)))
    )
    val r = l.copy(
      links = Some(
        List(
          BundleGoalLink("source", "a2001_old", Some(uid("s-a")), Some("theirs"), At),
          BundleGoalLink("query", "hittite storm god", None, None, At)
        )
      ),
      schedules = Some(
        List(
          BundleGoalSchedule("currency", "@daily", false, Some(1.5)),
          BundleGoalSchedule("acquisition", "@monthly", true, None)
        )
      )
    )
    val k = MergeFold.keepLocalSchedules(l, r)
    assertEquals(k.links.map(_.map(_.ref)), Some(List("hittite storm god", "a2001_x")))
    assertEquals(
      k.schedules.map(_.map(s => s.kind -> s.cron)),
      Some(List("acquisition" -> "@monthly", "currency" -> "@weekly"))
    )
    assertEquals(MergeFold.scheduleDifferences(l, r).map(_._1), List("currency"))

  test(
    "goals: two plans of one goal with neither newer ask; keep-right takes theirs; schedule differences are reported"
  ):
    val g0 =
      localGoal.copy(plan = Some(planA), schedules = Some(List(BundleGoalSchedule("currency", "@weekly", true, None))))
    val theirs = goal("w-goal", "zz_goal_both", "Goal created at work").copy(
      plan = Some(planB),
      schedules = Some(List(BundleGoalSchedule("currency", "@daily", true, None))),
      links = Some(List(BundleGoalLink("query", "ahhiyawa", None, None, At)))
    )
    val es = foreign.copy(goals = List(theirs))
    val in: MergePlanner.Input => MergePlanner.Input = i => i.copy(local = i.local.copy(goals = List(goalHere, g0)))
    val p = run(es, in).plan
    val q = p.issues.find(_.kind == Issue.Kind.GoalPlanDiffers).getOrElse(fail(p.issues.mkString("\n")))
    assert(q.id.startsWith("G-") && q.open, q.toString)
    assertEquals(q.detail.map(_.fields.map(_.field)), Some(List("plan", "approved")))
    assert(p.info.exists(_.contains("its currency schedule differs")), p.info.mkString("\n"))
    val r = run(es, in, resolve(q.id -> "keep-right"))
    val folded = r.localized.goals.find(_.uid == g0.uid).getOrElse(fail(r.localized.goals.toString))
    assertEquals(folded.plan, Some(planB))
    assertEquals(folded.schedules.map(_.map(_.cron)), Some(List("@weekly")))
    assertEquals(folded.links.map(_.map(_.ref)), Some(List("ahhiyawa")))
    // the later approval decides by itself
    val newer = es.copy(goals = List(theirs.copy(planApprovedAt = Some("2026-05-01"))))
    val auto = run(
      newer,
      i => i.copy(local = i.local.copy(goals = List(goalHere, g0.copy(planApprovedAt = Some("2026-04-01")))))
    )
    assert(!auto.plan.issues.exists(_.kind == Issue.Kind.GoalPlanDiffers))
    assertEquals(auto.localized.goals.find(_.uid == g0.uid).flatMap(_.plan), Some(planB))

  test("goals: a link to a merged source is written under the survivor's citekey"):
    val linked = goal("w-goal-l", "zz_goal_linked", "Linked").copy(
      links = Some(List(BundleGoalLink("source", "guterbock1997_perspectives", Some(uid("w-guterbock")), None, At)))
    )
    val r = run(foreign.copy(goals = List(linked)))
    assertEquals(
      r.localized.goals.find(_.uid == linked.uid).flatMap(_.links).map(_.map(l => (l.ref, l.refUid))),
      Some(List(("guterbock1997_hittite", Some(uid("h-guterbock")))))
    )

  // ── old article slugs ─────────────────────────────────────────────────────────────────────────

  test("old article slugs travel; one that names another article here is left out and said"):
    val ok = BundleArticleAlias("bronze_collapse_old", uid("w-bac"), At)
    val taken = BundleArticleAlias("zz_same_text", uid("w-bac"), At)
    val r = run(foreign.copy(articleAliases = List(ok, taken)))
    assertEquals(r.localized.articleAliases.map(_.alias), List("bronze_collapse_old"))
    assert(r.notes.exists(_.contains("old slug zz_same_text")), r.notes.mkString("\n"))

  // ── old event ids, goal slugs and place ids (#890) ──────────────────────────────────────────

  test("old event ids, goal slugs and place ids travel; one that names another record here is left out and said"):
    val ev = event("w-ev-al", "zz_ev_now", "Renamed event")
    val gl = goal("w-goal-al", "zz_goal_now", "Renamed goal")
    val pl = place("w-p-al", "zz_place_now", "Renamed place")
    val es = foreign.copy(
      events = List(ev),
      goals = List(gl),
      places = List(pl),
      eventAliases = List(BundleEventAlias("zz_ev_before", ev.uid, At), BundleEventAlias("zz_ev_taken", ev.uid, At)),
      goalAliases = List(BundleGoalAlias("zz_goal_before", gl.uid, At), BundleGoalAlias("zz_goal_taken", gl.uid, At)),
      placeAliases = List(
        BundlePlaceAlias("zz_place_before", pl.uid, At),
        BundlePlaceAlias("zz_place_taken", pl.uid, At),
        BundlePlaceAlias("zz_place_old_here", pl.uid, At)
      )
    )
    // here: a record of each kind already answers to the `…_taken` label, and an old id of another place
    val other = uid("h-other")
    val r = run(
      es,
      i =>
        i.copy(labels =
          i.labels.copy(
            events = Map("zz_ev_taken" -> other),
            goals = Map("zz_goal_taken" -> other),
            places = Map("zz_place_taken" -> other),
            placeAliases = Map("zz_place_old_here" -> other)
          )
        )
    )
    assertEquals(r.localized.eventAliases.map(_.alias), List("zz_ev_before"))
    assertEquals(r.localized.goalAliases.map(_.alias), List("zz_goal_before"))
    assertEquals(r.localized.placeAliases.map(_.alias), List("zz_place_before"))
    List(
      "old event id zz_ev_taken",
      "old goal slug zz_goal_taken",
      "old place id zz_place_taken",
      "old place id zz_place_old_here"
    ).foreach(n => assert(r.notes.exists(_.contains(n)), s"$n: ${r.notes.mkString("\n")}"))
    // an old label that already names THIS record here is kept (a re-import of the same rename)
    val again = run(
      es.copy(eventAliases = List(BundleEventAlias("zz_ev_mine", ev.uid, At))),
      i => i.copy(labels = i.labels.copy(eventAliases = Map("zz_ev_mine" -> ev.uid)))
    )
    assertEquals(again.localized.eventAliases.map(_.alias), List("zz_ev_mine"))

  test("an old place id follows the place into this collection's uid when the place is one with ours"):
    val theirs = place("w-p-fold", "work_troy2", "Troy")
    val ours = place("h-p-fold", "pleiades_550596", "Troy", origin = home)
    val r = run(
      foreign.copy(places = List(theirs), placeAliases = List(BundlePlaceAlias("work_troy_old", theirs.uid, At))),
      i => i.copy(local = i.local.copy(places = List(ours)), placeMatches = Map(theirs.uid -> "pleiades_550596"))
    )
    assertEquals(r.localized.placeAliases.map(a => (a.alias, a.placeUid)), List(("work_troy_old", ours.uid)))

  test("the alias files round-trip through a bundle's payloads, and an old bundle without them reads as none"):
    val es = BundleEntities(
      eventAliases = List(BundleEventAlias("a", uid("e"), At)),
      goalAliases = List(BundleGoalAlias("b", uid("g"), At)),
      placeAliases = List(BundlePlaceAlias("c", uid("p"), At))
    )
    val (back, problems) = BundleEntities.decode(es.payloads.toMap)
    assertEquals(problems, Nil)
    assertEquals(
      (back.eventAliases, back.goalAliases, back.placeAliases),
      (es.eventAliases, es.goalAliases, es.placeAliases)
    )
    val old = es.payloads.toMap -- List(BundleFiles.EventAliases, BundleFiles.GoalAliases, BundleFiles.PlaceAliases)
    assertEquals(BundleEntities.decode(old)._1.placeAliases, Nil)
    assert(
      BundleFiles.all.containsSlice(List(BundleFiles.EventAliases, BundleFiles.GoalAliases, BundleFiles.PlaceAliases))
    )
    // they are optional references: a dangling one is a note, not a failure
    assert(es.references.filter(_.from.startsWith("old")).forall(!_.required))
    assertEquals(es.asOf, Some(At))

  // ── what the review screen shows ──────────────────────────────────────────────────────────────

  test("detail: a source question carries both records field by field; an article its diff; an event both places"):
    val p = run(foreign).plan
    val gate = p.issues.find(_.kind == Issue.Kind.SameWork).get
    val fields = gate.detail.map(_.fields).getOrElse(Nil)
    assertEquals(fields.find(_.field == "year").map(f => (f.left, f.right)), Some((Some("1959"), Some("2024"))))
    val art = p.issues.find(i => i.kind == Issue.Kind.ArticleDiverged && i.severity == Issue.Severity.Blocking).get
    val text = art.detail.flatMap(_.text).getOrElse(fail(art.toString))
    assertEquals(text.left, "Home's account of the collapse.")
    assert(text.hunks.exists(_.conflict), text.hunks.toString)
    val ev = p.issues.find(i => i.kind == Issue.Kind.LabelTaken && i.entity == Bundle.Kind.Event).get
    assertEquals(ev.detail.flatMap(_.place).flatMap(_.rightSpan), Some(IssueSpan(Some(-1200), Some(-1190))))
    // the detail is not part of the plan hash: dropping it changes nothing the apply checks
    assert(p.issues.forall(i => i.id.matches("[A-Z]-[0-9a-f]{8}")))

  test("articleText: the regions of a three-way merge, agreed and conflicting"):
    val t = MergePlanner.articleText(Some("a\nb\nc"), "a\nB\nc", "a\nβ\nc")
    assertEquals(
      t.hunks,
      List(
        IssueHunk(false, left = List("a")),
        IssueHunk(true, List("b"), List("B"), List("β")),
        IssueHunk(false, left = List("c"))
      )
    )
    assertEquals(MergePlanner.articleText(None, "x", "y").hunks, List(IssueHunk(true, Nil, List("x"), List("y"))))

  test("the resolution file carries a merged text (texts:) and reads it back exactly — the web screen's download"):
    val p = run(foreign).plan
    val id = p.issues.find(i => i.kind == Issue.Kind.ArticleDiverged && i.severity == Issue.Severity.Blocking).get.id
    List(
      "one line",
      "two\nlines\n",
      "  indented first\n\nblank inside",
      "trailing blanks\n\n\n",
      "# not a comment: x\n"
    )
      .foreach { t =>
        val res = MergeResolutions(List(MergeResolution(id, "file:merged.md", Some(t))))
        val yaml = MergeResolutionsText.template(p, res)
        assert(yaml.contains("texts:"), yaml)
        assertEquals(MergeResolutionsFile.parse(yaml).map(_.resolve), Right(res.resolve), yaml)
      }
    // a text with no decision is an error, not a silent drop
    val orphan = "resolve:\n  S-1234abcd: keep-left\ntexts:\n  A-1234abcd: |-\n    x\n"
    assert(MergeResolutionsFile.parse(orphan).left.exists(_.exists(_.contains("A-1234abcd"))))
