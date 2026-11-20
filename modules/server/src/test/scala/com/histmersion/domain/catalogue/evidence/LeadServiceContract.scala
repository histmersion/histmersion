package com.histmersion
package domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, EventLinkCandidate, EventPut, EventTier, Latitude, Longitude, Year}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.domain.catalogue.events.{CatalogMerge, EventSelection, EventSql}
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.library.Citekey
import com.histmersion.util.database.TestPermits.given

/** The contract of the facts the leads and an event's card are computed from: the sources attesting an event (its own
  * first), and each event's lead — its tier, its catalogue's size, the sources proposed and confirmed — the catalogue's
  * events first, the largest first; with no finding stored, none is near.
  */
abstract class LeadServiceContract extends CatalogueStoreSuite:
  private def span(a: Year, b: Year) = TimeSpan(Some(a), None, None, Some(b))

  stest("the attesting sources, and the leads' facts in their order") { s =>
    List("zzld2020_own", "zzld2021_att").foreach(s.source)
    s.events.put(
      Event.Id("zz_attested"),
      EventPut(
        EventSummary.Label("Zz attested"),
        EventSummary.Kind("battle"),
        span(Year(-1300), Year(-1290)),
        citekey = Some(Citekey("zzld2020_own"))
      )
    )
    val _ = s.attestations.create(
      Attestation.Put(
        Citekey("zzld2021_att"),
        Attestation.TargetType("event"),
        Attestation.TargetId("zz_attested"),
        Some(Attestation.Quote("the battle")),
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )
    )
    val big =
      CatalogRecord(
        CatalogRecord.Provider("gvp"),
        CatalogRecord.ProviderId("9"),
        CatalogRecord.Url("u"),
        CatalogRecord.Label("Zz big"),
        CatalogRecord.Kind("eruption"),
        span(Year(-1500), Year(-1500)),
        Some(Latitude(1.0)),
        Some(Longitude(1.0)),
        vei = Some(CatalogRecord.Vei(6))
      )
    val small =
      CatalogRecord(
        CatalogRecord.Provider("gvp"),
        CatalogRecord.ProviderId("8"),
        CatalogRecord.Url("u"),
        CatalogRecord.Label("Zz small"),
        CatalogRecord.Kind("eruption"),
        span(Year(-1400), Year(-1400)),
        Some(Latitude(1.0)),
        Some(Longitude(1.0)),
        vei = Some(CatalogRecord.Vei(3))
      )
    val _ =
      s.eventImports.save(
        List(big, small).map(r =>
          r -> CatalogMerge.Placement
            .New(Event.Id.assume(CatalogRecord.eventId(r).value), CatalogMerge.Rationale("new"))
        )
      )
    s.eventLinks.saveCandidates(
      Event.Id("gvp_8"),
      List(
        EventLinkCandidate(
          api.catalogue.Event.Id("gvp_8"),
          Citekey("zzld2020_own"),
          EventLinkCandidate.Score(0.5)
        )
      )
    )
    assertEquals(
      s.leads.attestingSources(Event.Id("zz_attested")).map(r => (r.citekey, r.via, r.title)),
      List(("zzld2020_own", "citekey", Some("zzld2020_own")), ("zzld2021_att", "attestation", Some("zzld2021_att")))
    )
    assertEquals(
      s.leads.nearFindings(
        Event.Id("zz_attested"),
        CatalogMerge.Tolerance.Km(50),
        CatalogMerge.Tolerance.Years(50),
        PageSize(10)
      ),
      Nil
    )
    val (total, rows) = s.leads.leads(
      EventSelection(Filter()),
      CatalogMerge.Tolerance.Km(50),
      CatalogMerge.Tolerance.Years(50),
      PageSize(10),
      Offset(0)
    )
    assertEquals(total.value, 3)
    assertEquals(
      rows.map(r => (r.id, r.tier)),
      List(("gvp_9", EventTier.Catalogue), ("gvp_8", EventTier.Catalogue), ("zz_attested", EventTier.Attested))
    )
    assertEquals(rows.map(_.findingsNear).distinct, List(0))
    val catalogueOnly =
      s.leads.leads(
        EventSelection(Filter(), narrow = EventSql.Narrow(tier = Some(EventSql.Narrow.Tier(EventTier.Catalogue)))),
        CatalogMerge.Tolerance.Km(50),
        CatalogMerge.Tolerance.Years(50),
        PageSize(10),
        Offset(0)
      )
    assertEquals(catalogueOnly._1.value, 2)
    assertEquals(
      s.leads
        .leadOf(Event.Id("gvp_8"), CatalogMerge.Tolerance.Km(50), CatalogMerge.Tolerance.Years(50))
        .map(r => (r.vei, r.proposed, r.attesting)),
      List((Some(3), 1, 0))
    )
    assert(
      s.eventLinks.setStatus(
        Event.Id("gvp_8"),
        domain.library.Citekey("zzld2020_own"),
        Event.LinkStatus.Wire(Event.LinkStatus.Confirmed)
      )
    )
    assertEquals(
      s.leads
        .leadOf(Event.Id("gvp_8"), CatalogMerge.Tolerance.Km(50), CatalogMerge.Tolerance.Years(50))
        .map(r => (r.proposed, r.attesting)),
      List((0, 1))
    )
  }
