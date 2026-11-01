package com.histmersion.domain

import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord

import com.histmersion.values.Period

/** The host's dates rule for the key-gated collections: a collection's latest year refuses a modern record, and an
  * ancient one is matched within the place's period up to it. The Smithsonian and Europeana parsers are the media
  * plugins' own suites.
  */
class MediaKeyedCollectionsSuite extends munit.FunSuite:

  // ── relevance: modern objects ───────────────────────────────────────────────────────────────

  test(
    "dates: a modern record is refused by the latest year; an ancient one matches within the place's period up to it"
  ):
    val orkney = MediaMatch.PlaceInfo("orkney", "Orkney Islands", Set("orkney"), DeepTime.span(-7000L, 2100L))
    def rec(period: Option[Period]) =
      MediaRecord("k", "t", "p", None, None, None, Left("unused"), List("x" -> "Orkney"), period)
    assertEquals(
      MediaMatch.dated("x = Orkney", rec(DeepTime.span(1896L, 1896L)), orkney, Some(1500)),
      Left("made after 1500 CE")
    )
    assertEquals(
      MediaMatch.dated("x = Orkney", rec(DeepTime.span(1400L, 1600L)), orkney, Some(1500)),
      Left("made after 1500 CE"),
      "made by the latest year: its end, not its start"
    )
    assertEquals(
      MediaMatch.dated("x = Orkney", rec(DeepTime.span(-3000L, -2500L)), orkney, Some(1500)),
      Right("x = Orkney; 3000–2500 BCE within 7000 BCE–1500 CE")
    )
    assertEquals(
      MediaMatch.dated("x = Orkney", rec(DeepTime.span(1896L, 1896L)), orkney, None),
      Right("x = Orkney; 1896–1896 CE within 7000 BCE–2100 CE"),
      "no latest year: the place's period alone"
    )
    assertEquals(MediaMatch.dated("x = Orkney", rec(None), orkney, Some(1500)), Left("no dates on the record"))
    val late = orkney.copy(period = DeepTime.span(1600L, 1900L))
    assertEquals(
      MediaMatch.dated("x = Orkney", rec(DeepTime.span(1200L, 1300L)), late, Some(1500)),
      Left("its dates do not overlap the place's"),
      "a place that begins after the latest year is never widened back"
    )
