package com.histmersion
package domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{TimeSpan, Write}
import com.histmersion.api.catalogue.{EventLinksRequest, EventPut, EventSourceRef, MatchSpan}
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.{Caller, Title}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.{CatalogueJobs, CatalogueState}
import com.histmersion.domain.catalogue.events.EventService
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{JobStops, Pinning}

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The event links' controllers over the in-memory services and a fake library: a source is confirmed only from a
  * passage of its text naming the event — the attestation carries its quote and offsets — refused with why when none
  * does, rejected on request; an event's card lists the proposals still open.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class EventLinksControllersSuite extends munit.FunSuite:
  private val texts = Map(
    Citekey("zzln2020_names") -> "The Siege of Zzaria lasted long.",
    Citekey("zzln2021_silent") -> "Nothing of the kind here."
  )

  private val sources = new Passage.Source:
    def canonical(citekey: Citekey): Option[Citekey] = Option.when(texts.contains(citekey))(citekey)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def naming(name: Title, limit: PageSize): List[Citekey] = texts.collect {
      case (ck, t) if t.contains(name.value) => ck
    }.toList
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def reader(citekey: Citekey): PassageReader = (query, _, _, _, _, _) =>
      val text = texts.getOrElse(citekey, "")
      val at = text.indexOf("Siege of Zzaria")
      Right(
        Option
          .when(query.value.contains("Zzaria") && at >= 0)(
            Passage(Passage.Offset(0), Passage.Text(text), List(MatchSpan(MatchSpan.Start(at), MatchSpan.End(at + 15))))
          )
          .toList
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def controllers() =
    val state = AtomicReference(CatalogueState.empty)
    val events = EventService.InMemory(state)
    events.put(
      Event.Id("zz_siege"),
      EventPut(
        EventSummary.Label("Siege of Zzaria"),
        EventSummary.Kind("siege"),
        TimeSpan(Some(Year(-500)), None, None, Some(Year(-499)))
      )
    )
    val links = EventLinkService.InMemory(state, ck => Option.when(texts.contains(ck))(Some(Title(ck.value))))
    val attestations = AttestationService.InMemory(state)
    val c = EventLinksControllers(
      links,
      LeadService
        .InMemory(state, events, ck => Option.when(texts.contains(ck))((Some(EventSourceRef.Title(ck.value)), None))),
      events,
      attestations,
      sources,
      CatalogueJobs(org.postgresql.ds.PGSimpleDataSource(), WriteGate(), Pinning.none, JobStops(), Set.empty),
      _ => None,
      identity,
      _ => PlaceScope.empty,
      () => AppConfig.load(),
      WriteGate()
    )
    (c, links, attestations, events)

  test("proposed from the text that names it; confirmed only from a passage; refused with why; rejected"):
    val (c, links, attestations, events) = controllers()
    val first = c.links(Event.Id("zz_siege")).toOption.get
    assertEquals(first.candidates.map(_.citekey), List("zzln2020_names"))
    val r = c
      .applyLinks(
        Caller.Local,
        Event.Id("zz_siege"),
        EventLinksRequest(confirm =
          List(
            EventLinksRequest.Confirm("zzln2020_names"),
            EventLinksRequest.Confirm("zzln2021_silent"),
            EventLinksRequest.Confirm("zzgone")
          )
        )
      )
      .toOption
      .get
    assertEquals(
      r.attested.map(a => (a.citekey, a.quote, a.charStart)),
      List(("zzln2020_names", Some("Siege of Zzaria"), Some(4)))
    )
    assertEquals(r.refused.size, 2)
    assert(r.refused.exists(_.value.startsWith("zzgone: no such source")))
    assertEquals(
      (attestations
        .list(
          None,
          Some(Attestation.TargetType("event")),
          Some(Attestation.TargetId("zz_siege")),
          PageSize(10),
          Offset(0)
        )
        .total
        .value: Int),
      1
    )
    assertEquals(
      links.candidates(Event.Id("zz_siege")).map(c => (c.citekey, c.status)),
      List("zzln2020_names" -> "confirmed")
    )
    val again = c
      .applyLinks(
        Caller.Local,
        Event.Id("zz_siege"),
        EventLinksRequest(confirm = List(EventLinksRequest.Confirm("zzln2020_names")))
      )
      .toOption
      .get
    assertEquals(again.refused, List("zzln2020_names: already attests it"))
    val card = c.context(events.get(Event.Id("zz_siege")).get)
    assertEquals((card.tier, card.candidates, card.sources.map(_.via)), ("attested", Nil, List("attestation")))
    assertEquals(c.links(Event.Id("zz_none")).left.map(_.code), Left(Some(Write.CodeNotFound)))
