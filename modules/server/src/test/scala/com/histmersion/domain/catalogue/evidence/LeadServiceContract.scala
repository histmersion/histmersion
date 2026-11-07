package com.histmersion.domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, EventLinkCandidate, EventPut, EventTier}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.domain.catalogue.events.{CatalogMerge, EventSelection, EventSql}
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.Attestation

/** The contract of the facts the leads and an event's card are computed from: the sources attesting an event (its own
  * first), and each event's lead — its tier, its catalogue's size, the sources proposed and confirmed — the catalogue's
  * events first, the largest first; with no finding stored, none is near.
  */
abstract class LeadServiceContract extends CatalogueStoreSuite:
  private def span(a: Long, b: Long) = TimeSpan(Some(a), None, None, Some(b))

  stest("the attesting sources, and the leads' facts in their order") { s =>
    List("zzld2020_own", "zzld2021_att").foreach(s.source)
    s.events.put(com.histmersion.domain.catalogue.Event.Id("zz_attested"), EventPut(com.histmersion.api.catalogue.EventSummary.Label("Zz attested"), com.histmersion.api.catalogue.EventSummary.Kind("battle"), span(-1300, -1290), citekey = Some("zzld2020_own")))
    val _ = s.attestations.create(
      Attestation.Put(
        com.histmersion.api.library.Citekey("zzld2021_att"),
        com.histmersion.api.catalogue.Attestation.TargetType("event"),
        com.histmersion.api.catalogue.Attestation.TargetId("zz_attested"),
        Some(com.histmersion.api.catalogue.Attestation.Quote("the battle")),
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
      CatalogRecord(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), com.histmersion.api.catalogue.CatalogRecord.ProviderId("9"), com.histmersion.api.catalogue.CatalogRecord.Url("u"), com.histmersion.api.catalogue.CatalogRecord.Label("Zz big"), com.histmersion.api.catalogue.CatalogRecord.Kind("eruption"), span(-1500, -1500), Some(1.0), Some(1.0), vei = Some(6))
    val small =
      CatalogRecord(com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), com.histmersion.api.catalogue.CatalogRecord.ProviderId("8"), com.histmersion.api.catalogue.CatalogRecord.Url("u"), com.histmersion.api.catalogue.CatalogRecord.Label("Zz small"), com.histmersion.api.catalogue.CatalogRecord.Kind("eruption"), span(-1400, -1400), Some(1.0), Some(1.0), vei = Some(3))
    val _ =
      s.eventImports.save(List(big, small).map(r => r -> CatalogMerge.Placement.New(CatalogRecord.eventId(r), com.histmersion.domain.catalogue.events.CatalogMerge.Placement.Why("new"))))
    s.eventLinks.saveCandidates(com.histmersion.domain.catalogue.Event.Id("gvp_8"), List(EventLinkCandidate(com.histmersion.api.catalogue.Event.Id("gvp_8"), com.histmersion.api.library.Citekey("zzld2020_own"), 0.5)))
    assertEquals(
      s.leads.attestingSources(com.histmersion.domain.catalogue.Event.Id("zz_attested")).map(r => (r.citekey, r.via, r.title)),
      List(("zzld2020_own", "citekey", Some("zzld2020_own")), ("zzld2021_att", "attestation", Some("zzld2021_att")))
    )
    assertEquals(s.leads.nearFindings(com.histmersion.domain.catalogue.Event.Id("zz_attested"), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(50), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(50), com.histmersion.PageSize(10)), Nil)
    val (total, rows) = s.leads.leads(EventSelection(Filter()), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(50), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(50), com.histmersion.PageSize(10), com.histmersion.Offset(0))
    assertEquals(total.value, 3)
    assertEquals(
      rows.map(r => (r.id, r.tier)),
      List(("gvp_9", EventTier.Catalogue), ("gvp_8", EventTier.Catalogue), ("zz_attested", EventTier.Attested))
    )
    assertEquals(rows.map(_.findingsNear).distinct, List(0))
    val catalogueOnly =
      s.leads.leads(EventSelection(Filter(), narrow = EventSql.Narrow(tier = Some(EventTier.Catalogue))), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(50), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(50), com.histmersion.PageSize(10), com.histmersion.Offset(0))
    assertEquals(catalogueOnly._1.value, 2)
    assertEquals(s.leads.leadOf(com.histmersion.domain.catalogue.Event.Id("gvp_8"), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(50), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(50)).map(r => (r.vei, r.proposed, r.attesting)), List((Some(3), 1, 0)))
    assert(s.eventLinks.setStatus(com.histmersion.domain.catalogue.Event.Id("gvp_8"), com.histmersion.domain.library.Citekey("zzld2020_own"), Event.LinkStatus.Confirmed))
    assertEquals(s.leads.leadOf(com.histmersion.domain.catalogue.Event.Id("gvp_8"), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(50), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(50)).map(r => (r.proposed, r.attesting)), List((0, 1)))
  }
