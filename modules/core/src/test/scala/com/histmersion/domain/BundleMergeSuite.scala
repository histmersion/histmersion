package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.EventPlan

import com.histmersion.api.given

import java.util.UUID
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.Bundle

/** The merge's side of the `.hmx` bundle: localizing the event-catalogue sets, folding two events, and the import
  * decisions.
  */
class BundleMergeSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val U3 = UUID.fromString("01920000-0000-7000-8000-000000000003")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")
  // ── the event-catalogue sets (#1647) ─────────────────────────────────────────────────────────

  private def event(
      extra: com.histmersion.api.exchange.Bundle.Event => com.histmersion.api.exchange.Bundle.Event = identity
  ) = extra(
    com.histmersion.api.exchange.Bundle.Event(
      U1,
      I,
      com.histmersion.api.exchange.Bundle.Event.Id("e1"),
      com.histmersion.api.exchange.Bundle.Event.Label("Eruption"),
      com.histmersion.api.exchange.Bundle.Event.Kind("eruption"),
      Some(com.histmersion.api.exchange.Bundle.Event.NotBefore(-1600)),
      None,
      None,
      Some(com.histmersion.api.exchange.Bundle.Event.NotAfter(-1500)),
      com.histmersion.api.exchange.Bundle.Event.Historicity("attested"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      com.histmersion.api.exchange.Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )
  )

  test("the event-catalogue sets localize: a matched event's uid is replaced by the local one"):
    val es = BundleEntities(
      eventCatalogue = List(
        com.histmersion.api.exchange.Bundle.EventCatalogue(
          U1,
          com.histmersion.api.exchange.Bundle.EventCatalogue.Provider("gvp"),
          com.histmersion.api.exchange.Bundle.EventCatalogue.ProviderId("1"),
          None,
          com.histmersion.api.exchange.Bundle.EventCatalogue.Label("x"),
          com.histmersion.api.exchange.Bundle.EventCatalogue.Kind("eruption"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          com.histmersion.api.exchange.Bundle.EventCatalogue.Historicity("attested"),
          io.circe.Json.obj(),
          com.histmersion.api.exchange.Bundle.EventCatalogue.RetrievedAt("2026-10-05T00:00:00.000000Z")
        )
      ),
      eventCandidates = List(
        com.histmersion.api.exchange.Bundle.EventCandidate(
          U1,
          U2,
          com.histmersion.api.library.Citekey("ck"),
          1.0,
          com.histmersion.api.exchange.Bundle.EventCandidate.Status("proposed"),
          Nil,
          io.circe.Json.arr(),
          Nil,
          com.histmersion.api.exchange.Bundle.EventCandidate.ComputedAt("2026-10-05T00:00:00.000000Z")
        )
      ),
      eventChecks = List(com.histmersion.api.exchange.Bundle.EventCheck(U1, com.histmersion.api.exchange.Bundle.EventCheck.CheckedAt("2026-10-05T00:00:00.000000Z"))),
      corrections = List(
        com.histmersion.api.exchange.Bundle.Correction(
          com.histmersion.api.exchange.Bundle.Correction.TargetKind("event"),
          com.histmersion.api.exchange.Bundle.Correction.TargetId("e1"),
          Some(U1),
          com.histmersion.api.exchange.Bundle.Correction.Field("label"),
          com.histmersion.api.exchange.Bundle.Correction.Value("1"),
          None,
          com.histmersion.api.exchange.Bundle.Correction.UpstreamValue("2"),
          None,
          com.histmersion.api.exchange.Bundle.Correction.Status("active"),
          com.histmersion.api.exchange.Bundle.Correction.CreatedAt("2026-10-05T00:00:00.000000Z"),
          com.histmersion.api.exchange.Bundle.Correction.UpdatedAt("2026-10-05T00:00:00.000000Z"),
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
    val w = event(_.modify(_.names).setTo(Some(List("Thera"))).modify(_.catalogue).setTo(None))
    val o = event(
      _.modify(
        _.uid).setTo(U2).modify(_.names).setTo(Some(List("Santorini", "Thera"))).modify(_.catalogue).setTo(Some("gvp")).modify(_.central).setTo((Some(-1560): _root_.scala.Option[_root_.scala.Long])).modify(_.plusMinus).setTo((Some(10): _root_.scala.Option[_root_.scala.Long])).modify(_.datingMethod).setTo(Some("radiocarbon")
      )
    )
    val f = EventPlan.foldEvent(w, o)
    assertEquals(f.names, Some(List("Thera", "Santorini")))
    assertEquals(f.catalogue, Some("gvp"))
    assertEquals((f.central, f.plusMinus, f.datingMethod), (Some(-1560L), Some(10L), Some("radiocarbon")))
    val w2 = w.modify(_.plusMinus).setTo((Some(50): _root_.scala.Option[_root_.scala.Long]))
    assertEquals(EventPlan.foldEvent(w2, o).central, None, "the winner's own statement is not mixed with the other's")

  import BundlePlan.{Decision, Ledger, Local}

  test("decisions: the whole table of a two-way merge with the ledger as ancestor"):
    def d(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None, own: Boolean = false) =
      BundlePlan.entity(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), Local(local, tomb, ledger), own)
    assertEquals(d(None), Decision.Insert)
    assertEquals(d(None, tomb = true), Decision.SkipDeleted)
    assertEquals(d(Some("F")), Decision.Unchanged)
    assertEquals(d(Some("L"), own = true), Decision.KeepLocal)
    assertEquals(d(Some("L"), ledger = Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("X"))))), Decision.KeepLocal) // theirs unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("E"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("L"))))), Decision.FastForward) // ours unchanged
    assertEquals(d(Some("L"), ledger = Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("E"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("X"))))), Decision.Conflict) // both changed
    assertEquals(d(Some("L")), Decision.Conflict) // never merged

  test("tombstones: delete only what is still as the last merge left it; record the rest"):
    def t(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None) =
      BundlePlan.tombstone(Local(local, tomb, ledger), ownBundle = com.histmersion.util.data.exchange.BundlePlan.TombstoneOwnBundle(false))
    assertEquals(t(Some("L"), ledger = Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("E"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("L"))))), Decision.Delete)
    assertEquals(t(Some("L"), ledger = Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("E"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("X"))))), Decision.Conflict)
    assertEquals(t(Some("L")), Decision.Conflict)
    assertEquals(t(None), Decision.RecordTombstone)
    assertEquals(t(None, tomb = true), Decision.Unchanged)

  test("the ledger keeps the old ancestor for a kept row, so a later change over there is a conflict"):
    assertEquals(BundlePlan.ledgerAfter(Decision.KeepLocal, com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), tombstone = com.histmersion.util.data.exchange.BundlePlan.Local.Tombstoned(false)), None)
    assertEquals(BundlePlan.ledgerAfter(Decision.Insert, com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), tombstone = com.histmersion.util.data.exchange.BundlePlan.Local.Tombstoned(false)), Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), Some(com.histmersion.util.data.exchange.BundlePlan.Rev("F")))))
    assertEquals(BundlePlan.ledgerAfter(Decision.Unchanged, com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("deleted"), tombstone = com.histmersion.util.data.exchange.BundlePlan.Local.Tombstoned(true)), Some(Ledger(com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("deleted"), None)))
    assertEquals(BundlePlan.ledgerAfter(Decision.Conflict, com.histmersion.util.data.exchange.BundlePlan.Ledger.ForeignRev("F"), tombstone = com.histmersion.util.data.exchange.BundlePlan.Local.Tombstoned(false)), None)

  test("cascade: a line needing a row deleted here is skipped too, transitively; optional links are not"):
    val (src, asset, link, ev) = (U1, U2, UUID.randomUUID(), UUID.randomUUID())
    val refs = List(
      BundleEntities.Reference(Bundle.Kind.Source, src, asset, "asset", required = true),
      BundleEntities.Reference(Bundle.Kind.Asset, asset, link, "link", required = true),
      BundleEntities.Reference(Bundle.Kind.Event, src, ev, "parent", required = false)
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
      Bundle.Kind.Goal,
      List(Decision.Insert, Decision.Insert, Decision.Conflict, Decision.RecordTombstone, Decision.Delete)
    )
    assertEquals(
      c,
      com.histmersion.api.exchange.Bundle
        .KindCounts(Bundle.Kind.Goal, inserted = com.histmersion.api.exchange.Bundle.KindCounts.Inserted(2), conflicts = com.histmersion.api.exchange.Bundle.KindCounts.Conflicts(1), unchanged = com.histmersion.api.exchange.Bundle.KindCounts.Unchanged(1), deleted = com.histmersion.api.exchange.Bundle.KindCounts.Deleted(1))
    )
    assertEquals(c.changed, 3)
