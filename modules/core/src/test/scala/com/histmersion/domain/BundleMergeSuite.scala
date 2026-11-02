package com.histmersion.domain

import com.histmersion.domain.catalogue.events.EventPlan

import com.histmersion.api.given

import java.util.UUID
import com.histmersion.values.BundleKind
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.{
  BundleCorrection,
  BundleEvent,
  BundleEventCandidate,
  BundleEventCatalogue,
  BundleEventCheck,
  BundleKindCounts
}

/** The merge's side of the `.hmx` bundle: localizing the event-catalogue sets, folding two events, and the import
  * decisions.
  */
class BundleMergeSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")
  // ── the event-catalogue sets (#1647) ─────────────────────────────────────────────────────────

  private def event(extra: BundleEvent => BundleEvent = identity) = extra(
    BundleEvent(
      U1,
      I,
      "e1",
      "Eruption",
      "eruption",
      Some(-1600),
      None,
      None,
      Some(-1500),
      "attested",
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      "2026-10-03T00:00:00.000000Z"
    )
  )

  test("the event-catalogue sets localize: a matched event's uid is replaced by the local one"):
    val es = BundleEntities(
      eventCatalogue = List(
        BundleEventCatalogue(
          U1,
          "gvp",
          "1",
          None,
          "x",
          "eruption",
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          "attested",
          io.circe.Json.obj(),
          "2026-10-05T00:00:00.000000Z"
        )
      ),
      eventCandidates = List(
        BundleEventCandidate(
          U1,
          U2,
          "ck",
          1.0,
          "proposed",
          Nil,
          io.circe.Json.arr(),
          Nil,
          "2026-10-05T00:00:00.000000Z"
        )
      ),
      eventChecks = List(BundleEventCheck(U1, "2026-10-05T00:00:00.000000Z")),
      corrections = List(
        BundleCorrection(
          "event",
          "e1",
          Some(U1),
          "label",
          "1",
          None,
          "2",
          None,
          "active",
          "2026-10-05T00:00:00.000000Z",
          "2026-10-05T00:00:00.000000Z",
          None
        )
      )
    )
    val l = MergePlanner.localize(es, Map(U1 -> U3, U2 -> U1), Map.empty)
    assertEquals(l.eventCatalogue.map(_.eventUid), List(U3))
    assertEquals(l.eventCandidates.map(c => (c.eventUid, c.sourceUid)), List((U3, U1)))
    assertEquals(l.eventChecks.map(_.eventUid), List(U3))
    assertEquals(l.corrections.map(_.targetUid), List(Some(U3)))

  test("folding two events keeps one stated dating whole and both sets of names"):
    val w = event(_.copy(names = Some(List("Thera")), catalogue = None))
    val o = event(
      _.copy(
        uid = U2,
        names = Some(List("Santorini", "Thera")),
        catalogue = Some("gvp"),
        central = Some(-1560),
        plusMinus = Some(10),
        datingMethod = Some("radiocarbon")
      )
    )
    val f = EventPlan.foldEvent(w, o)
    assertEquals(f.names, Some(List("Thera", "Santorini")))
    assertEquals(f.catalogue, Some("gvp"))
    assertEquals((f.central, f.plusMinus, f.datingMethod), (Some(-1560L), Some(10L), Some("radiocarbon")))
    val w2 = w.copy(plusMinus = Some(50))
    assertEquals(EventPlan.foldEvent(w2, o).central, None, "the winner's own statement is not mixed with the other's")

  import BundlePlan.{Decision, Ledger, Local}

  test("decisions: the whole table of a two-way merge with the ledger as ancestor"):
    def d(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None, own: Boolean = false) =
      BundlePlan.entity("F", Local(local, tomb, ledger), own)
    assertEquals(d(None), Decision.Insert)
    assertEquals(d(None, tomb = true), Decision.SkipDeleted)
    assertEquals(d(Some("F")), Decision.Unchanged)
    assertEquals(d(Some("L"), own = true), Decision.KeepLocal)
    assertEquals(d(Some("L"), ledger = Some(Ledger("F", Some("X")))), Decision.KeepLocal) // theirs unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger("E", Some("L")))), Decision.FastForward) // ours unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger("E", Some("X")))), Decision.Conflict) // both changed
    assertEquals(d(Some("L")), Decision.Conflict) // never merged

  test("tombstones: delete only what is still as the last merge left it; record the rest"):
    def t(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None) =
      BundlePlan.tombstone(Local(local, tomb, ledger), ownBundle = false)
    assertEquals(t(Some("L"), ledger = Some(Ledger("E", Some("L")))), Decision.Delete)
    assertEquals(t(Some("L"), ledger = Some(Ledger("E", Some("X")))), Decision.Conflict)
    assertEquals(t(Some("L")), Decision.Conflict)
    assertEquals(t(None), Decision.RecordTombstone)
    assertEquals(t(None, tomb = true), Decision.Unchanged)

  test("the ledger keeps the old ancestor for a kept row, so a later change over there is a conflict"):
    assertEquals(BundlePlan.ledgerAfter(Decision.KeepLocal, "F", tombstone = false), None)
    assertEquals(BundlePlan.ledgerAfter(Decision.Insert, "F", tombstone = false), Some(Ledger("F", Some("F"))))
    assertEquals(BundlePlan.ledgerAfter(Decision.Unchanged, "deleted", tombstone = true), Some(Ledger("deleted", None)))
    assertEquals(BundlePlan.ledgerAfter(Decision.Conflict, "F", tombstone = false), None)

  test("cascade: a line needing a row deleted here is skipped too, transitively; optional links are not"):
    val (src, asset, link, ev) = (U1, U2, UUID.randomUUID(), UUID.randomUUID())
    val refs = List(
      BundleEntities.Reference(BundleKind.Source, src, asset, "asset", required = true),
      BundleEntities.Reference(BundleKind.Asset, asset, link, "link", required = true),
      BundleEntities.Reference(BundleKind.Event, src, ev, "parent", required = false)
    )
    val out = BundlePlan.cascade(
      Map(src -> Decision.SkipDeleted, asset -> Decision.Insert, link -> Decision.Insert, ev -> Decision.Insert),
      refs,
      _ => false
    )
    assertEquals(out(asset), Decision.SkipDeleted)
    assertEquals(out(link), Decision.SkipDeleted)
    assertEquals(out(ev), Decision.Insert)
    // a parent outside the bundle, tombstoned here, counts as gone too
    assertEquals(BundlePlan.cascade(Map(asset -> Decision.Insert), refs.take(1), _ == src)(asset), Decision.SkipDeleted)

  test("counts tally every decision under its kind"):
    val c = BundlePlan.counts(
      BundleKind.Goal,
      List(Decision.Insert, Decision.Insert, Decision.Conflict, Decision.RecordTombstone, Decision.Delete)
    )
    assertEquals(c, BundleKindCounts(BundleKind.Goal, inserted = 2, conflicts = 1, unchanged = 1, deleted = 1))
    assertEquals(c.changed, 3)
