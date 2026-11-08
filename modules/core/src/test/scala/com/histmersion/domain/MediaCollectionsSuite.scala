package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord

/** The host's match rules for collection records (#1446) — a record tied to a place by its most specific place field,
  * by any location of a map, and by the dates — and the deep-time spans they read. The records are built here as the
  * host reads a plugin's item ([[MediaRecord]]); the parsers that produce them are the media plugins' own suites.
  */
class MediaCollectionsSuite extends munit.FunSuite:

  // ── matching ─────────────────────────────────────────────────────────────────────────────────

  private def rec(
      geo: List[(String, String)],
      period: Option[Period] = DeepTime.span(DeepTime.SignedYear(-1400L), DeepTime.SignedYear(-1200L))
  ) =
    MediaRecord(
      MediaRecord.Key("k"),
      MediaRecord.Title("t"),
      MediaRecord.PageUrl("https://x"),
      Some(MediaRecord.ImageUrl("https://x/i.jpg")),
      None,
      None,
      Right(Attribution.cc0(None, Attribution.Cc0Credit("t"))),
      geo.map((k, v) => (MediaRecord.Geography.Field(k), MediaRecord.Geography.Value(v))),
      period
    )

  private val mycenae =
    MediaMatch.PlaceInfo(
      MediaMatch.PlaceInfo.Id("pleiades_570491"),
      MediaMatch.PlaceInfo.Label("Mycenae"),
      Set(MediaMatch.PlaceInfo.Keys("mycenae"), MediaMatch.PlaceInfo.Keys("mykenai")),
      DeepTime.span(DeepTime.SignedYear(-1600L), DeepTime.SignedYear(-1100L))
    )
  private val greece = MediaMatch.PlaceInfo(
    MediaMatch.PlaceInfo.Id("geonames_390903"),
    MediaMatch.PlaceInfo.Label("Greece"),
    Set(MediaMatch.PlaceInfo.Keys("greece")),
    DeepTime.span(DeepTime.SignedYear(-3000L), DeepTime.SignedYear(-100L))
  )
  private val onMap: MediaMatch.Resolve = Map(
    MediaMatch.PlaceInfo.Keys("mycenae") -> Set(MediaMatch.PlaceInfo.Id("pleiades_570491")),
    MediaMatch.PlaceInfo.Keys("mykenai") -> Set(MediaMatch.PlaceInfo.Id("pleiades_570491")),
    MediaMatch.PlaceInfo.Keys("greece") -> Set(MediaMatch.PlaceInfo.Id("geonames_390903")),
    MediaMatch.PlaceInfo.Keys("thebes") -> Set(
      MediaMatch.PlaceInfo.Id("thebes_egypt"),
      MediaMatch.PlaceInfo.Id("thebes_greece")
    )
  ).withDefaultValue(Set.empty)

  test("museum rule: the most specific place field that names a place on the map decides, by name key, with the dates"):
    val r = rec(List("excavation" -> "Mykénai", "country" -> "Greece"))
    assertEquals(
      MediaMatch.museum(r, mycenae, onMap),
      Right("excavation = Mykénai; 1400–1200 BCE within 1600–1100 BCE")
    )
    assertEquals(
      MediaMatch.museum(r, greece, onMap),
      Left("names another place on the map"),
      "an object from Mycenae is not filed under Greece too"
    )
    assertEquals(
      MediaMatch.museum(rec(List("city" -> "Atlantis", "country" -> "Greece")), greece, onMap).map(_.value.take(17)),
      Right("country = Greece;"),
      "a field that names nothing on the map is passed over"
    )

  test("museum rule: uncertain and ambiguous values, missing or disjoint dates are never matched"):
    assertEquals(MediaMatch.museum(rec(List("culture" -> "Mycenae ?")), mycenae, onMap), Left("no place on the record"))
    assertEquals(
      MediaMatch.museum(rec(List("culture" -> "probably Mycenae")), mycenae, onMap),
      Left("no place on the record")
    )
    val thebes = MediaMatch.PlaceInfo(
      MediaMatch.PlaceInfo.Id("thebes_greece"),
      MediaMatch.PlaceInfo.Label("Thebes"),
      Set(MediaMatch.PlaceInfo.Keys("thebes")),
      DeepTime.span(DeepTime.SignedYear(-1600L), DeepTime.SignedYear(-1100L))
    )
    assertEquals(
      MediaMatch.museum(rec(List("city" -> "Thebes")), thebes, onMap),
      Left("its place name is ambiguous on the map")
    )
    assertEquals(
      MediaMatch.museum(rec(List("excavation" -> "Mycenae"), None), mycenae, onMap),
      Left("no dates on the record")
    )
    assertEquals(
      MediaMatch.museum(rec(List("excavation" -> "Mycenae")), mycenae.modify(_.period).setTo(None), onMap),
      Left("the place has no period to compare")
    )
    assertEquals(
      MediaMatch.museum(
        rec(List("excavation" -> "Mycenae"), DeepTime.span(DeepTime.SignedYear(1700L), DeepTime.SignedYear(1750L))),
        mycenae,
        onMap
      ),
      Left("its dates do not overlap the place's")
    )
    assertEquals(
      MediaMatch.museum(rec(List("excavation" -> "Mycenae, Tomb 5")), mycenae, onMap),
      Left("its place is not on the map"),
      "whole value against whole name: never a substring"
    )

  test("map rule: any location naming the place alone, made by max-year"):
    val m = rec(
      List("location" -> "greece", "location" -> "mycenae"),
      DeepTime.span(DeepTime.SignedYear(1880L), DeepTime.SignedYear(1880L))
    )
    assertEquals(MediaMatch.map(m, mycenae, onMap, Year(1928)), Right("location = mycenae; map of 1880–1880 CE"))
    assertEquals(MediaMatch.map(m, greece, onMap, Year(1928)), Right("location = greece; map of 1880–1880 CE"))
    assertEquals(
      MediaMatch.map(
        m.modify(_.period).setTo(DeepTime.span(DeepTime.SignedYear(1950L), DeepTime.SignedYear(1950L))),
        greece,
        onMap,
        Year(1928)
      ),
      Left("made after 1928")
    )
    assertEquals(
      MediaMatch.map(
        rec(List("location" -> "thebes"), DeepTime.span(DeepTime.SignedYear(1880L), DeepTime.SignedYear(1880L))),
        MediaMatch.PlaceInfo(
          MediaMatch.PlaceInfo.Id("thebes_egypt"),
          MediaMatch.PlaceInfo.Label("Thebes"),
          Set(MediaMatch.PlaceInfo.Keys("thebes")),
          None
        ),
        onMap,
        Year(1928)
      ),
      Left("its place name is ambiguous on the map")
    )

  test("year ranges read as a person reads them; overlap is inclusive"):
    def p(a: DeepTime.SignedYear, b: DeepTime.SignedYear) = DeepTime.span(a, b).get
    assertEquals(MediaMatch.years(p(DeepTime.SignedYear(-200L), DeepTime.SignedYear(100L))), "200 BCE–100 CE")
    assert(
      MediaMatch.overlaps(
        p(DeepTime.SignedYear(-1200L), DeepTime.SignedYear(-1100L)),
        p(DeepTime.SignedYear(-1100L), DeepTime.SignedYear(-1000L))
      )
    )
    assert(
      !MediaMatch.overlaps(
        p(DeepTime.SignedYear(-1200L), DeepTime.SignedYear(-1101L)),
        p(DeepTime.SignedYear(-1100L), DeepTime.SignedYear(-1000L))
      )
    )

  test("deep time: two years in either order make a period; a year no Year holds makes none"):
    assertEquals(
      DeepTime.span(DeepTime.SignedYear(-1100L), DeepTime.SignedYear(-1200L)),
      DeepTime.span(DeepTime.SignedYear(-1200L), DeepTime.SignedYear(-1100L))
    )
    assertEquals(
      DeepTime
        .span(DeepTime.SignedYear(-1200L), DeepTime.SignedYear(-1100L))
        .map(q => (q.start.value: Int, q.end.value: Int)),
      Some((-1200, -1100))
    )
    assertEquals(DeepTime.span(DeepTime.SignedYear(5L), DeepTime.SignedYear(5L)).map(_.isPoint), Some(true))
    assertEquals(DeepTime.span(DeepTime.SignedYear(-1L), DeepTime.SignedYear(Int.MaxValue.toLong + 1)), None)
    assertEquals(DeepTime.year(DeepTime.SignedYear(Int.MinValue.toLong - 1)), None)
