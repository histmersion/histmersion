package com.histmersion.domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventLinkCandidate, EventPassage, EventPut}
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.domain.catalogue.Event

/** The contract the events' proposed links keep: the confirmed first then by score, a source that is gone left out, the
  * rejected only when asked; saving again replaces the proposals and keeps a decided status; the check recorded; the
  * events still to check, the catalogue's first.
  */
abstract class EventLinkServiceContract extends CatalogueStoreSuite:
  private def cand(ck: String, score: Double) =
    EventLinkCandidate(
      com.histmersion.api.catalogue.Event.Id("zz_ev"),
      com.histmersion.api.library.Citekey(ck),
      score,
      findings = List("f1"),
      passages = List(EventPassage(com.histmersion.api.catalogue.EventPassage.Start(1), com.histmersion.api.catalogue.EventPassage.End(5), com.histmersion.api.catalogue.EventPassage.Quote("Zzev"), com.histmersion.api.catalogue.EventPassage.Name("Zzev"), com.histmersion.api.catalogue.EventPassage.Context("a Zzev b"))),
      reasons = List("named")
    )

  stest("proposed, decided, saved again; the check recorded; the events still to check") { s =>
    val l = s.eventLinks
    s.events.put(com.histmersion.domain.catalogue.Event.Id("zz_ev"), EventPut(com.histmersion.api.catalogue.EventSummary.Label("Zzev"), com.histmersion.api.catalogue.EventSummary.Kind("battle"), TimeSpan(Some(com.histmersion.api.catalogue.Year(-1200)), None, None, Some(com.histmersion.api.catalogue.Year(-1190)))))
    s.events.put(com.histmersion.domain.catalogue.Event.Id("zz_other"), EventPut(com.histmersion.api.catalogue.EventSummary.Label("Zz other"), com.histmersion.api.catalogue.EventSummary.Kind("battle"), TimeSpan(Some(com.histmersion.api.catalogue.Year(-1200)), None, None, Some(com.histmersion.api.catalogue.Year(-1190)))))
    List("zzl2020_a", "zzl2020_b", "zzl2020_c").foreach(s.source)
    assert(!l.isChecked(com.histmersion.domain.catalogue.Event.Id("zz_ev")))
    l.saveCandidates(com.histmersion.domain.catalogue.Event.Id("zz_ev"), List(cand("zzl2020_a", 0.5), cand("zzl2020_b", 0.9), cand("zzl2020_c", 0.7)))
    assert(l.isChecked(com.histmersion.domain.catalogue.Event.Id("zz_ev")))
    assertEquals(l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev")).map(_.citekey), List("zzl2020_b", "zzl2020_c", "zzl2020_a"))
    assertEquals(l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev")).head.title, Some("zzl2020_b"))
    assertEquals(l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev")).head.passages.map(_.quote), List("Zzev"))
    assert(l.setStatus(com.histmersion.domain.catalogue.Event.Id("zz_ev"), com.histmersion.domain.library.Citekey("zzl2020_a"), Event.LinkStatus.Confirmed))
    assert(l.setStatus(com.histmersion.domain.catalogue.Event.Id("zz_ev"), com.histmersion.domain.library.Citekey("zzl2020_c"), Event.LinkStatus.Rejected))
    assert(!l.setStatus(com.histmersion.domain.catalogue.Event.Id("zz_ev"), com.histmersion.domain.library.Citekey("zzl2020_none"), Event.LinkStatus.Confirmed))
    assertEquals(
      l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev")).map(c => (c.citekey, c.status)),
      List("zzl2020_a" -> "confirmed", "zzl2020_b" -> "proposed")
    )
    assertEquals(l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev"), withRejected = true).size, 3)
    l.saveCandidates(com.histmersion.domain.catalogue.Event.Id("zz_ev"), List(cand("zzl2020_a", 0.1), cand("zzl2020_c", 0.8)))
    assertEquals(
      l.candidates(com.histmersion.domain.catalogue.Event.Id("zz_ev"), withRejected = true).map(c => (c.citekey, c.status, c.score)),
      List(("zzl2020_a", "confirmed", 0.1), ("zzl2020_c", "rejected", 0.8)),
      "the proposals replaced, a decided status kept, its evidence refreshed"
    )
    assertEquals(l.eventsToCheck(all = false, com.histmersion.PageSize(10)), List("zz_other"))
    assertEquals(l.eventsToCheck(all = true, com.histmersion.PageSize(10)), List("zz_ev", "zz_other"))
    assertEquals(l.eventsToCheck(all = true, com.histmersion.PageSize(1)), List("zz_ev"))
  }
