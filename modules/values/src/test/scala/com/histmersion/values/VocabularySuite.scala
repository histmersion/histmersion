package com.histmersion.values

import com.histmersion.{Ascii, WireEnumValues}
import com.histmersion.domain.automation.{Pipeline, ProviderKind, ScheduleKind}
import com.histmersion.domain.catalogue.Historicity
import com.histmersion.domain.library.{Confidence, EditionsPolicy, MatchMethod, MatchOutcome}
import com.histmersion.domain.media.MediaRole
import com.histmersion.domain.research.DraftMode
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.data.exchange.Issue
class VocabularySuite extends munit.FunSuite:

  private def roundTrips[E](using w: WireEnumValues[E]): Unit =
    val wires = w.all.toList.map(w.wire)
    assertEquals(wires.distinct, wires, "two values share a wire spelling")
    w.all.foreach(e => assertEquals(w.fromWire(w.wire(e)), Some(e), s"${w.wire(e)} does not read back"))
    assertEquals(w.fromWire("no-such-value"), None)

  test("every enum vocabulary reads back what it writes, and no two values share a spelling"):
    roundTrips[Source.Status]
    roundTrips[Confidence]
    roundTrips[Edge.Kind]
    roundTrips[Place.Precision]
    roundTrips[Issue.Severity]
    roundTrips[Issue.Kind]
    roundTrips[DecisionKind]
    roundTrips[MatchOutcome]
    roundTrips[EditionsPolicy]
    roundTrips[Bundle.Kind]
    roundTrips[Issue.DecidedBy]
    roundTrips[MatchMethod]
    roundTrips[Approval.Kind]
    roundTrips[DraftMode]
    roundTrips[Goal.LinkKind]
    roundTrips[ScheduleKind]

  test("the agent and manager vocabularies parse what they spell"):
    for r <- Run.State.values do assertEquals(Run.State.parse(r.wire.toUpperCase), Some(r))
    for p <- Pipeline.values do assertEquals(Pipeline.parse(p.wire), Some(p))
    for k <- Manager.Kind.values do assertEquals(Manager.Kind.parse(k.wire), Some(k))
    assertEquals(ProviderKind.parse("claude-code"), Some(ProviderKind.ClaudeCode))
    assertEquals(ProviderKind.parse("acme", known = Set("acme")), Some(ProviderKind.Plugin("acme")))
    assertEquals(ProviderKind.parse("acme"), None)
    assertEquals(ScheduleKind.Acquisition.pipeline, Pipeline.AcquisitionSweep)
    assertEquals(Run.State.next(Run.State.Paused, Run.State.Queued), Some(Run.State.Queued))
    assertEquals(Run.State.next(Run.State.Done, Run.State.Queued), None)
    assertEquals(Approval.DecidedBy.of("open", None, None), None)
    assertEquals(Approval.DecidedBy.of("approved", Some("autopilot: ok"), None), Some(Approval.DecidedBy.Autopilot))

  test("the wire spellings the store and the clients rely on"):
    assertEquals(Edge.Kind.wireOf(Edge.Kind.SupersededBy), "superseded_by")
    assertEquals(Edge.Kind.fromWire("SUPERSEDED_BY"), Some(Edge.Kind.SupersededBy))
    assertEquals(Source.Status.fromString("nonsense"), Source.Status.Unassessed)
    assertEquals(Source.Status.Current.wire, "current")
    assertEquals(Place.Precision.GridCell.wire, "grid-cell")
    assertEquals(Issue.Kind.wireOf(Issue.Kind.DeletedThereChangedHere), "deleted-there-changed-here")
    assertEquals(DecisionKind.wireOf(DecisionKind.KeepUnanchored), "keep-unanchored")
    assertEquals(Bundle.Kind.ArticleVersion.wire, "article-version")
    assertEquals(Bundle.Kind.QueueItem.file, "entities/queue.ndjson")

  test("the string vocabularies list each value once"):
    for all <- List(
        Goal.Status.all,
        Place.Kind.all,
        MediaRole.all,
        Historicity.all,
        Event.EdgeKind.all,
        Event.LinkStatus.all
      )
    do assertEquals(all.distinct, all)
    assertEquals(Event.EdgeKind.allen ++ Event.EdgeKind.causal, Event.EdgeKind.all.toSet)

  test("a foreign type label maps onto a place kind"):
    assertEquals(Place.Kind.parse("Kingdom"), Some(Place.Kind.Polity))
    assertEquals(Place.Kind.parse("tribe"), Some(Place.Kind.People))
    assertEquals(Place.Kind.parse("  "), None)

  test("a Pleiades point on an atlas grid centre is a grid cell; anything else is stated"):
    assertEquals(Place.Precision.of("pleiades_1", 22.5, 37.5), Place.Precision.GridCell)
    assertEquals(Place.Precision.of("wikidata_q1", 22.5, 37.5), Place.Precision.Stated)
    assertEquals(Place.Precision.of("pleiades_1", 22.51, 37.5), Place.Precision.Stated)

  test("a picture's role ranks on a card; an unknown role last"):
    assertEquals(MediaRole.order(MediaRole.Image), 0)
    assertEquals(MediaRole.order("nope"), MediaRole.all.size)

  test("the spellings lower-case A-Z only, as Locale.ROOT does for them"):
    assertEquals(Ascii.lower("SupersededBy_X-1"), "supersededby_x-1")
    assertEquals(Ascii.lower("İK"), "İk")
    assertEquals(
      Source.Status.values.toList.map(_.wire),
      List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed")
    )
    assertEquals(Confidence.values.toList.map(_.wire), List("high", "medium", "low", "contested"))

  test("every kind of record a bundle carries has its own letter of an issue id"):
    val letters = Bundle.Kind.values.toList.map(_.issueLetter)
    assertEquals(letters, List("S", "F", "A", "V", "X", "K", "T", "E", "G", "Q", "P", "N"))
    assertEquals(letters.distinct, letters)
