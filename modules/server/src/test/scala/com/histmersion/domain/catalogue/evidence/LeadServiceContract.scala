package com.histmersion.domain.catalogue.evidence

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
    s.events.put("zz_attested", EventPut("Zz attested", "battle", span(-1300, -1290), citekey = Some("zzld2020_own")))
    val _ = s.attestations.create(
      Attestation.Put(
        "zzld2021_att",
        "event",
        "zz_attested",
        Some("the battle"),
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
      CatalogRecord("gvp", "9", "u", "Zz big", "eruption", span(-1500, -1500), Some(1.0), Some(1.0), vei = Some(6))
    val small =
      CatalogRecord("gvp", "8", "u", "Zz small", "eruption", span(-1400, -1400), Some(1.0), Some(1.0), vei = Some(3))
    val _ =
      s.eventImports.save(List(big, small).map(r => r -> CatalogMerge.Placement.New(CatalogRecord.eventId(r), "new")))
    s.eventLinks.saveCandidates("gvp_8", List(EventLinkCandidate(com.histmersion.api.catalogue.Event.Id("gvp_8"), com.histmersion.api.library.Citekey("zzld2020_own"), 0.5)))
    assertEquals(
      s.leads.attestingSources("zz_attested").map(r => (r.citekey, r.via, r.title)),
      List(("zzld2020_own", "citekey", Some("zzld2020_own")), ("zzld2021_att", "attestation", Some("zzld2021_att")))
    )
    assertEquals(s.leads.nearFindings("zz_attested", 50, 50, 10), Nil)
    val (total, rows) = s.leads.leads(EventSelection(Filter()), 50, 50, 10, 0)
    assertEquals(total.value, 3)
    assertEquals(
      rows.map(r => (r.id, r.tier)),
      List(("gvp_9", EventTier.Catalogue), ("gvp_8", EventTier.Catalogue), ("zz_attested", EventTier.Attested))
    )
    assertEquals(rows.map(_.findingsNear).distinct, List(0))
    val catalogueOnly =
      s.leads.leads(EventSelection(Filter(), narrow = EventSql.Narrow(tier = Some(EventTier.Catalogue))), 50, 50, 10, 0)
    assertEquals(catalogueOnly._1.value, 2)
    assertEquals(s.leads.leadOf("gvp_8", 50, 50).map(r => (r.vei, r.proposed, r.attesting)), List((Some(3), 1, 0)))
    assert(s.eventLinks.setStatus("gvp_8", "zzld2020_own", Event.LinkStatus.Confirmed))
    assertEquals(s.leads.leadOf("gvp_8", 50, 50).map(r => (r.proposed, r.attesting)), List((0, 1)))
  }
