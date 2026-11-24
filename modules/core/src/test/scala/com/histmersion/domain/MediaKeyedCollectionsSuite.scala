package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord

import com.histmersion.Period
import com.histmersion.lint.{LintRule, tolerated}

/** The host's dates rule for the key-gated collections: a collection's latest year refuses a modern record, and an
  * ancient one is matched within the place's period up to it. The Smithsonian and Europeana parsers are the media
  * plugins' own suites.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class MediaKeyedCollectionsSuite extends munit.FunSuite:

  // ── relevance: modern objects ───────────────────────────────────────────────────────────────

  test(
    "dates: a modern record is refused by the latest year; an ancient one matches within the place's period up to it"
  ):
    val orkney = MediaMatch.PlaceInfo(
      MediaMatch.PlaceInfo.Id("orkney"),
      MediaMatch.PlaceInfo.Label("Orkney Islands"),
      Set(MediaMatch.PlaceInfo.Keys("orkney")),
      DeepTime.span(DeepTime.SignedYear(-7000L), DeepTime.SignedYear(2100L))
    )
    def rec(period: Option[Period]) =
      MediaRecord(
        MediaRecord.Key("k"),
        MediaRecord.Title("t"),
        MediaRecord.PageUrl("p"),
        None,
        None,
        None,
        Left(Problem.Unclassified(Legacy.Text("unused"))),
        List(MediaRecord.Geography.Field("x") -> MediaRecord.Geography.Value("Orkney")),
        period
      )
    assertEquals(
      MediaMatch
        .dated(
          MediaMatch.Basis("x = Orkney"),
          rec(DeepTime.span(DeepTime.SignedYear(1896L), DeepTime.SignedYear(1896L))),
          orkney,
          Some(Year(1500))
        )
        .left
        .map(w => s"$w"),
      Left("made after 1500 CE")
    )
    assertEquals(
      MediaMatch
        .dated(
          MediaMatch.Basis("x = Orkney"),
          rec(DeepTime.span(DeepTime.SignedYear(1400L), DeepTime.SignedYear(1600L))),
          orkney,
          Some(Year(1500))
        )
        .left
        .map(w => s"$w"),
      Left("made after 1500 CE"),
      "made by the latest year: its end, not its start"
    )
    assertEquals(
      MediaMatch.dated(
        MediaMatch.Basis("x = Orkney"),
        rec(DeepTime.span(DeepTime.SignedYear(-3000L), DeepTime.SignedYear(-2500L))),
        orkney,
        Some(Year(1500))
      ),
      Right("x = Orkney; 3000–2500 BCE within 7000 BCE–1500 CE")
    )
    assertEquals(
      MediaMatch.dated(
        MediaMatch.Basis("x = Orkney"),
        rec(DeepTime.span(DeepTime.SignedYear(1896L), DeepTime.SignedYear(1896L))),
        orkney,
        None
      ),
      Right("x = Orkney; 1896–1896 CE within 7000 BCE–2100 CE"),
      "no latest year: the place's period alone"
    )
    assertEquals(
      MediaMatch.dated(MediaMatch.Basis("x = Orkney"), rec(None), orkney, Some(Year(1500))).left.map(w => s"$w"),
      Left("no dates on the record")
    )
    val late = orkney.modify(_.period).setTo(DeepTime.span(DeepTime.SignedYear(1600L), DeepTime.SignedYear(1900L)))
    assertEquals(
      MediaMatch
        .dated(
          MediaMatch.Basis("x = Orkney"),
          rec(DeepTime.span(DeepTime.SignedYear(1200L), DeepTime.SignedYear(1300L))),
          late,
          Some(Year(1500))
        )
        .left
        .map(w => s"$w"),
      Left("its dates do not overlap the place's"),
      "a place that begins after the latest year is never widened back"
    )
