package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord

import com.histmersion.Period

/** The host's dates rule for the key-gated collections: a collection's latest year refuses a modern record, and an
  * ancient one is matched within the place's period up to it. The Smithsonian and Europeana parsers are the media
  * plugins' own suites.
  */
class MediaKeyedCollectionsSuite extends munit.FunSuite:

  // ── relevance: modern objects ───────────────────────────────────────────────────────────────

  test(
    "dates: a modern record is refused by the latest year; an ancient one matches within the place's period up to it"
  ):
    val orkney = MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("orkney"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Orkney Islands"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("orkney")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-7000L), com.histmersion.domain.DeepTime.SignedYear(2100L)))
    def rec(period: Option[Period]) =
      MediaRecord(com.histmersion.domain.media.imports.MediaRecord.Key("k"), com.histmersion.domain.media.imports.MediaRecord.Title("t"), com.histmersion.domain.media.imports.MediaRecord.PageUrl("p"), None, None, None, Left(com.histmersion.domain.media.imports.MediaRecord.RightsPart1("unused")), List("x" -> "Orkney"), period)
    assertEquals(
      MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1896L), com.histmersion.domain.DeepTime.SignedYear(1896L))), orkney, Some(com.histmersion.Year(1500))),
      Left("made after 1500 CE")
    )
    assertEquals(
      MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1400L), com.histmersion.domain.DeepTime.SignedYear(1600L))), orkney, Some(com.histmersion.Year(1500))),
      Left("made after 1500 CE"),
      "made by the latest year: its end, not its start"
    )
    assertEquals(
      MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-3000L), com.histmersion.domain.DeepTime.SignedYear(-2500L))), orkney, Some(com.histmersion.Year(1500))),
      Right("x = Orkney; 3000–2500 BCE within 7000 BCE–1500 CE")
    )
    assertEquals(
      MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1896L), com.histmersion.domain.DeepTime.SignedYear(1896L))), orkney, None),
      Right("x = Orkney; 1896–1896 CE within 7000 BCE–2100 CE"),
      "no latest year: the place's period alone"
    )
    assertEquals(MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(None), orkney, Some(com.histmersion.Year(1500))), Left("no dates on the record"))
    val late = orkney.modify(_.period).setTo(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1600L), com.histmersion.domain.DeepTime.SignedYear(1900L)))
    assertEquals(
      MediaMatch.dated(com.histmersion.domain.media.imports.MediaMatch.Basis("x = Orkney"), rec(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1200L), com.histmersion.domain.DeepTime.SignedYear(1300L))), late, Some(com.histmersion.Year(1500))),
      Left("its dates do not overlap the place's"),
      "a place that begins after the latest year is never widened back"
    )
