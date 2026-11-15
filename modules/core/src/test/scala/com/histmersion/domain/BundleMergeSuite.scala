package com.histmersion
package domain
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.EventPlan

import com.histmersion.api.given

import java.util.UUID
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle.uidEquality

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
      extra: Bundle.Event => Bundle.Event = identity
  ) = extra(
    Bundle.Event(
      U1,
      I,
      Bundle.Event.Id("e1"),
      Bundle.Event.Label("Eruption"),
      Bundle.Event.Kind("eruption"),
      Some(Bundle.Event.NotBefore(-1600)),
      None,
      None,
      Some(Bundle.Event.NotAfter(-1500)),
      Bundle.Event.Historicity("attested"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )
  )

  test("the event-catalogue sets localize: a matched event's uid is replaced by the local one"):
    val es = BundleEntities(
      eventCatalogue = List(
        Bundle.EventCatalogue(
          U1,
          Bundle.EventCatalogue.Provider("gvp"),
          Bundle.EventCatalogue.ProviderId("1"),
          None,
          Bundle.EventCatalogue.Label("x"),
          Bundle.EventCatalogue.Kind("eruption"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Bundle.EventCatalogue.Historicity("attested"),
          Json.obj(),
          Bundle.EventCatalogue.RetrievedAt("2026-10-05T00:00:00.000000Z")
        )
      ),
      eventCandidates = List(
        Bundle.EventCandidate(
          U1,
          U2,
          Citekey("ck"),
          Bundle.EventCandidate.Score(1.0),
          Bundle.EventCandidate.Status("proposed"),
          Nil,
          Json.arr(),
          Nil,
          Bundle.EventCandidate.ComputedAt("2026-10-05T00:00:00.000000Z")
        )
      ),
      eventChecks = List(Bundle.EventCheck(U1, Bundle.EventCheck.CheckedAt("2026-10-05T00:00:00.000000Z"))),
      corrections = List(
        Bundle.Correction(
          Bundle.Correction.TargetKind("event"),
          Bundle.Correction.TargetId("e1"),
          Some(U1),
          Bundle.Correction.Field("label"),
          Bundle.Correction.Value("1"),
          None,
          Bundle.Correction.UpstreamValue("2"),
          None,
          Bundle.Correction.Status("active"),
          Bundle.Correction.CreatedAt("2026-10-05T00:00:00.000000Z"),
          Bundle.Correction.UpdatedAt("2026-10-05T00:00:00.000000Z"),
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
    val w = event(_.modify(_.names).setTo(Some(List(Bundle.Event.Names("Thera")))).modify(_.catalogue).setTo(None))
    val o = event(
      _.modify(_.uid)
        .setTo(U2)
        .modify(_.names)
        .setTo(Some(List(Bundle.Event.Names("Santorini"), Bundle.Event.Names("Thera"))))
        .modify(_.catalogue)
        .setTo(Some(Bundle.Event.Catalogue("gvp")))
        .modify(_.central)
        .setTo(Some(Bundle.Event.Central(-1560L)))
        .modify(_.plusMinus)
        .setTo(Some(Bundle.Event.PlusMinus(10L)))
        .modify(_.datingMethod)
        .setTo(Some(Bundle.Event.DatingMethod("radiocarbon")))
    )
    val f = EventPlan.foldEvent(w, o)
    assertEquals(f.names, Some(List(Bundle.Event.Names("Thera"), Bundle.Event.Names("Santorini"))))
    assertEquals(f.catalogue, Some(Bundle.Event.Catalogue("gvp")))
    assertEquals(
      (f.central, f.plusMinus, f.datingMethod),
      (
        Some(Bundle.Event.Central(-1560L)),
        Some(Bundle.Event.PlusMinus(10L)),
        Some(Bundle.Event.DatingMethod("radiocarbon"))
      )
    )
    val w2 = w.modify(_.plusMinus).setTo(Some(Bundle.Event.PlusMinus(50L)))
    assertEquals(EventPlan.foldEvent(w2, o).central, None, "the winner's own statement is not mixed with the other's")

  import BundlePlan.{Decision, Ledger, Local}

  test("decisions: the whole table of a two-way merge with the ledger as ancestor"):
    def d(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None, own: Boolean = false) =
      BundlePlan.entity(
        Ledger.ForeignRev("F"),
        Local(local.map(Local.Rev(_)), Local.Tombstoned(tomb), ledger),
        BundlePlan.EntityOwnBundle(own)
      )
    assertEquals(d(None), Decision.Insert)
    assertEquals(d(None, tomb = true), Decision.SkipDeleted)
    assertEquals(d(Some("F")), Decision.Unchanged)
    assertEquals(d(Some("L"), own = true), Decision.KeepLocal)
    assertEquals(
      d(Some("L"), ledger = Some(Ledger(Ledger.ForeignRev("F"), Some(BundlePlan.Rev("X"))))),
      Decision.KeepLocal
    ) // theirs unchanged
    assertEquals(
      d(Some("L"), ledger = Some(Ledger(Ledger.ForeignRev("E"), Some(BundlePlan.Rev("L"))))),
      Decision.FastForward
    ) // ours unchanged
    assertEquals(
      d(Some("L"), ledger = Some(Ledger(Ledger.ForeignRev("E"), Some(BundlePlan.Rev("X"))))),
      Decision.Conflict
    ) // both changed
    assertEquals(d(Some("L")), Decision.Conflict) // never merged

  test("tombstones: delete only what is still as the last merge left it; record the rest"):
    def t(local: Option[String], tomb: Boolean = false, ledger: Option[Ledger] = None) =
      BundlePlan.tombstone(
        Local(local.map(Local.Rev(_)), Local.Tombstoned(tomb), ledger),
        ownBundle = BundlePlan.TombstoneOwnBundle(false)
      )
    assertEquals(
      t(Some("L"), ledger = Some(Ledger(Ledger.ForeignRev("E"), Some(BundlePlan.Rev("L"))))),
      Decision.Delete
    )
    assertEquals(
      t(Some("L"), ledger = Some(Ledger(Ledger.ForeignRev("E"), Some(BundlePlan.Rev("X"))))),
      Decision.Conflict
    )
    assertEquals(t(Some("L")), Decision.Conflict)
    assertEquals(t(None), Decision.RecordTombstone)
    assertEquals(t(None, tomb = true), Decision.Unchanged)

  test("the ledger keeps the old ancestor for a kept row, so a later change over there is a conflict"):
    assertEquals(
      BundlePlan.ledgerAfter(Decision.KeepLocal, Ledger.ForeignRev("F"), tombstone = Local.Tombstoned(false)),
      None
    )
    assertEquals(
      BundlePlan.ledgerAfter(Decision.Insert, Ledger.ForeignRev("F"), tombstone = Local.Tombstoned(false)),
      Some(Ledger(Ledger.ForeignRev("F"), Some(BundlePlan.Rev("F"))))
    )
    assertEquals(
      BundlePlan.ledgerAfter(Decision.Unchanged, Ledger.ForeignRev("deleted"), tombstone = Local.Tombstoned(true)),
      Some(Ledger(Ledger.ForeignRev("deleted"), None))
    )
    assertEquals(
      BundlePlan.ledgerAfter(Decision.Conflict, Ledger.ForeignRev("F"), tombstone = Local.Tombstoned(false)),
      None
    )

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
      Bundle
        .KindCounts(
          Bundle.Kind.Goal,
          inserted = Bundle.KindCounts.Inserted(2),
          conflicts = Bundle.KindCounts.Conflicts(1),
          unchanged = Bundle.KindCounts.Unchanged(1),
          deleted = Bundle.KindCounts.Deleted(1)
        )
    )
    assertEquals(c.changed, 3)
