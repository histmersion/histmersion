package com.histmersion.values

import com.histmersion.{Ascii, WireEnumValues}
import com.histmersion.domain.automation.{Pipeline, ProviderKind, ScheduleKind}
import com.histmersion.domain.catalogue.Historicity
import com.histmersion.domain.library.{Confidence, EditionsPolicy, MatchMethod, MatchOutcome}
import com.histmersion.domain.media.MediaRole
import com.histmersion.domain.research.DraftMode
import com.histmersion.util.data.exchange.DecisionKind
class VocabularySuite extends munit.FunSuite:

  private def roundTrips[E](using w: WireEnumValues[E]): Unit =
    val wires = w.all.toList.map(w.wire)
    assertEquals(wires.distinct, wires, "two values share a wire spelling")
    w.all.foreach(e => assertEquals(w.fromWire(w.wire(e)), Some(e), s"${w.wire(e)} does not read back"))
    assertEquals(w.fromWire("no-such-value"), None)

  test("every enum vocabulary reads back what it writes, and no two values share a spelling"):
    roundTrips[SourceStatus]
    roundTrips[Confidence]
    roundTrips[EdgeKind]
    roundTrips[LocationPrecision]
    roundTrips[IssueSeverity]
    roundTrips[IssueKind]
    roundTrips[DecisionKind]
    roundTrips[MatchOutcome]
    roundTrips[EditionsPolicy]
    roundTrips[BundleKind]
    roundTrips[DecidedBy]
    roundTrips[MatchMethod]
    roundTrips[ApprovalKind]
    roundTrips[DraftMode]
    roundTrips[GoalLinkKind]
    roundTrips[ScheduleKind]

  test("the agent and manager vocabularies parse what they spell"):
    for r <- RunState.values do assertEquals(RunState.parse(r.wire.toUpperCase), Some(r))
    for p <- Pipeline.values do assertEquals(Pipeline.parse(p.wire), Some(p))
    for k <- ManagerKind.values do assertEquals(ManagerKind.parse(k.wire), Some(k))
    assertEquals(ProviderKind.parse("claude-code"), Some(ProviderKind.ClaudeCode))
    assertEquals(ProviderKind.parse("acme", known = Set("acme")), Some(ProviderKind.Plugin("acme")))
    assertEquals(ProviderKind.parse("acme"), None)
    assertEquals(ScheduleKind.Acquisition.pipeline, Pipeline.AcquisitionSweep)
    assertEquals(RunState.next(RunState.Paused, RunState.Queued), Some(RunState.Queued))
    assertEquals(RunState.next(RunState.Done, RunState.Queued), None)
    assertEquals(ApprovalDecidedBy.of("open", None, None), None)
    assertEquals(ApprovalDecidedBy.of("approved", Some("autopilot: ok"), None), Some(ApprovalDecidedBy.Autopilot))

  test("the wire spellings the store and the clients rely on"):
    assertEquals(EdgeKind.wireOf(EdgeKind.SupersededBy), "superseded_by")
    assertEquals(EdgeKind.fromWire("SUPERSEDED_BY"), Some(EdgeKind.SupersededBy))
    assertEquals(SourceStatus.fromString("nonsense"), SourceStatus.Unassessed)
    assertEquals(SourceStatus.Current.wire, "current")
    assertEquals(LocationPrecision.GridCell.wire, "grid-cell")
    assertEquals(IssueKind.wireOf(IssueKind.DeletedThereChangedHere), "deleted-there-changed-here")
    assertEquals(DecisionKind.wireOf(DecisionKind.KeepUnanchored), "keep-unanchored")
    assertEquals(BundleKind.ArticleVersion.wire, "article-version")
    assertEquals(BundleKind.QueueItem.file, "entities/queue.ndjson")

  test("the string vocabularies list each value once"):
    for all <- List(
        GoalStatus.all,
        PlaceKind.all,
        MediaRole.all,
        Historicity.all,
        EventEdgeKind.all,
        EventLinkStatus.all
      )
    do assertEquals(all.distinct, all)
    assertEquals(EventEdgeKind.allen ++ EventEdgeKind.causal, EventEdgeKind.all.toSet)

  test("a foreign type label maps onto a place kind"):
    assertEquals(PlaceKind.parse("Kingdom"), Some(PlaceKind.Polity))
    assertEquals(PlaceKind.parse("tribe"), Some(PlaceKind.People))
    assertEquals(PlaceKind.parse("  "), None)

  test("a Pleiades point on an atlas grid centre is a grid cell; anything else is stated"):
    assertEquals(LocationPrecision.of("pleiades_1", 22.5, 37.5), LocationPrecision.GridCell)
    assertEquals(LocationPrecision.of("wikidata_q1", 22.5, 37.5), LocationPrecision.Stated)
    assertEquals(LocationPrecision.of("pleiades_1", 22.51, 37.5), LocationPrecision.Stated)

  test("a picture's role ranks on a card; an unknown role last"):
    assertEquals(MediaRole.order(MediaRole.Image), 0)
    assertEquals(MediaRole.order("nope"), MediaRole.all.size)

  test("the spellings lower-case A-Z only, as Locale.ROOT does for them"):
    assertEquals(Ascii.lower("SupersededBy_X-1"), "supersededby_x-1")
    assertEquals(Ascii.lower("İK"), "İk")
    assertEquals(
      SourceStatus.values.toList.map(_.wire),
      List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed")
    )
    assertEquals(Confidence.values.toList.map(_.wire), List("high", "medium", "low", "contested"))

  test("every kind of record a bundle carries has its own letter of an issue id"):
    val letters = BundleKind.values.toList.map(_.issueLetter)
    assertEquals(letters, List("S", "F", "A", "V", "X", "K", "T", "E", "G", "Q", "P", "N"))
    assertEquals(letters.distinct, letters)
