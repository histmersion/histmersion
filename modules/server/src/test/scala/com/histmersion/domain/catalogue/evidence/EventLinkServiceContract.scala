package com.histmersion.domain.catalogue.evidence

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
      passages = List(EventPassage(1, 5, "Zzev", "Zzev", "a Zzev b")),
      reasons = List("named")
    )

  stest("proposed, decided, saved again; the check recorded; the events still to check") { s =>
    val l = s.eventLinks
    s.events.put("zz_ev", EventPut("Zzev", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
    s.events.put("zz_other", EventPut("Zz other", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
    List("zzl2020_a", "zzl2020_b", "zzl2020_c").foreach(s.source)
    assert(!l.isChecked("zz_ev"))
    l.saveCandidates("zz_ev", List(cand("zzl2020_a", 0.5), cand("zzl2020_b", 0.9), cand("zzl2020_c", 0.7)))
    assert(l.isChecked("zz_ev"))
    assertEquals(l.candidates("zz_ev").map(_.citekey), List("zzl2020_b", "zzl2020_c", "zzl2020_a"))
    assertEquals(l.candidates("zz_ev").head.title, Some("zzl2020_b"))
    assertEquals(l.candidates("zz_ev").head.passages.map(_.quote), List("Zzev"))
    assert(l.setStatus("zz_ev", "zzl2020_a", Event.LinkStatus.Confirmed))
    assert(l.setStatus("zz_ev", "zzl2020_c", Event.LinkStatus.Rejected))
    assert(!l.setStatus("zz_ev", "zzl2020_none", Event.LinkStatus.Confirmed))
    assertEquals(
      l.candidates("zz_ev").map(c => (c.citekey, c.status)),
      List("zzl2020_a" -> "confirmed", "zzl2020_b" -> "proposed")
    )
    assertEquals(l.candidates("zz_ev", withRejected = true).size, 3)
    l.saveCandidates("zz_ev", List(cand("zzl2020_a", 0.1), cand("zzl2020_c", 0.8)))
    assertEquals(
      l.candidates("zz_ev", withRejected = true).map(c => (c.citekey, c.status, c.score)),
      List(("zzl2020_a", "confirmed", 0.1), ("zzl2020_c", "rejected", 0.8)),
      "the proposals replaced, a decided status kept, its evidence refreshed"
    )
    assertEquals(l.eventsToCheck(all = false, 10), List("zz_other"))
    assertEquals(l.eventsToCheck(all = true, 10), List("zz_ev", "zz_other"))
    assertEquals(l.eventsToCheck(all = true, 1), List("zz_ev"))
  }
