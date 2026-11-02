package com.histmersion.domain.catalogue.evidence

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{TimeSpan, Write}
import com.histmersion.api.catalogue.{EventLinksRequest, EventPut, MatchSpan}
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.{CatalogueJobs, CatalogueState}
import com.histmersion.domain.catalogue.events.EventService
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{JobStops, Pinning}

import java.util.concurrent.atomic.AtomicReference

/** The event links' controllers over the in-memory services and a fake library: a source is confirmed only from a
  * passage of its text naming the event — the attestation carries its quote and offsets — refused with why when none
  * does, rejected on request; an event's card lists the proposals still open.
  */
class EventLinksControllersSuite extends munit.FunSuite:
  private val texts = Map(
    "zzln2020_names" -> "The Siege of Zzaria lasted long.",
    "zzln2021_silent" -> "Nothing of the kind here."
  )

  private val sources = new Passage.Source:
    def canonical(citekey: String): Option[String] = Option.when(texts.contains(citekey))(citekey)
    def naming(name: String, limit: Int): List[String] = texts.collect { case (ck, t) if t.contains(name) => ck }.toList
    def reader(citekey: String): PassageReader = (query, _, _, _, _, _) =>
      val text = texts.getOrElse(citekey, "")
      val at = text.indexOf("Siege of Zzaria")
      Right(Option.when(query.contains("Zzaria") && at >= 0)(Passage(0, text, List(MatchSpan(at, at + 15)))).toList)

  private def controllers() =
    val state = AtomicReference(CatalogueState.empty)
    val events = EventService.InMemory(state)
    events.put("zz_siege", EventPut("Siege of Zzaria", "siege", TimeSpan(Some(-500), None, None, Some(-499))))
    val links = EventLinkService.InMemory(state, ck => Option.when(texts.contains(ck))(Some(ck)))
    val attestations = AttestationService.InMemory(state)
    val c = EventLinksControllers(
      links,
      LeadService.InMemory(state, events, ck => Option.when(texts.contains(ck))((Some(ck), None))),
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
    val first = c.links("zz_siege").toOption.get
    assertEquals(first.candidates.map(_.citekey), List("zzln2020_names"))
    val r = c
      .applyLinks(
        Caller.Local,
        "zz_siege",
        EventLinksRequest(confirm = List("zzln2020_names", "zzln2021_silent", "zzgone"))
      )
      .toOption
      .get
    assertEquals(
      r.attested.map(a => (a.citekey, a.quote, a.charStart)),
      List(("zzln2020_names", Some("Siege of Zzaria"), Some(4)))
    )
    assertEquals(r.refused.size, 2)
    assert(r.refused.exists(_.startsWith("zzgone: no such source")))
    assertEquals(attestations.list(None, Some("event"), Some("zz_siege"), 10, 0).total, 1)
    assertEquals(links.candidates("zz_siege").map(c => (c.citekey, c.status)), List("zzln2020_names" -> "confirmed"))
    val again = c.applyLinks(Caller.Local, "zz_siege", EventLinksRequest(confirm = List("zzln2020_names"))).toOption.get
    assertEquals(again.refused, List("zzln2020_names: already attests it"))
    val card = c.context(events.get("zz_siege").get)
    assertEquals((card.tier, card.candidates, card.sources.map(_.via)), ("attested", Nil, List("attestation")))
    assertEquals(c.links("zz_none").left.map(_.code), Left(Some(Write.CodeNotFound)))
