package com.histmersion.domain

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
      period: Option[com.histmersion.Period] = DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1400L), com.histmersion.domain.DeepTime.SignedYear(-1200L))
  ) =
    MediaRecord(
      com.histmersion.domain.media.imports.MediaRecord.Key("k"),
      com.histmersion.domain.media.imports.MediaRecord.Title("t"),
      com.histmersion.domain.media.imports.MediaRecord.PageUrl("https://x"),
      Some(com.histmersion.domain.media.imports.MediaRecord.ImageUrl("https://x/i.jpg")),
      None,
      None,
      Right(Attribution.cc0(None, com.histmersion.domain.Attribution.Cc0Credit("t"))),
      geo,
      period
    )

  private val mycenae =
    MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("pleiades_570491"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Mycenae"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("mycenae"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("mykenai")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1600L), com.histmersion.domain.DeepTime.SignedYear(-1100L)))
  private val greece = MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("geonames_390903"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Greece"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("greece")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-3000L), com.histmersion.domain.DeepTime.SignedYear(-100L)))
  private val onMap: MediaMatch.Resolve = Map(
    "mycenae" -> Set("pleiades_570491"),
    "mykenai" -> Set("pleiades_570491"),
    "greece" -> Set("geonames_390903"),
    "thebes" -> Set("thebes_egypt", "thebes_greece")
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
      MediaMatch.museum(rec(List("city" -> "Atlantis", "country" -> "Greece")), greece, onMap).map(_.take(17)),
      Right("country = Greece;"),
      "a field that names nothing on the map is passed over"
    )

  test("museum rule: uncertain and ambiguous values, missing or disjoint dates are never matched"):
    assertEquals(MediaMatch.museum(rec(List("culture" -> "Mycenae ?")), mycenae, onMap), Left("no place on the record"))
    assertEquals(
      MediaMatch.museum(rec(List("culture" -> "probably Mycenae")), mycenae, onMap),
      Left("no place on the record")
    )
    val thebes = MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("thebes_greece"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Thebes"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("thebes")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1600L), com.histmersion.domain.DeepTime.SignedYear(-1100L)))
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
      MediaMatch.museum(rec(List("excavation" -> "Mycenae"), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1700L), com.histmersion.domain.DeepTime.SignedYear(1750L))), mycenae, onMap),
      Left("its dates do not overlap the place's")
    )
    assertEquals(
      MediaMatch.museum(rec(List("excavation" -> "Mycenae, Tomb 5")), mycenae, onMap),
      Left("its place is not on the map"),
      "whole value against whole name: never a substring"
    )

  test("map rule: any location naming the place alone, made by max-year"):
    val m = rec(List("location" -> "greece", "location" -> "mycenae"), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1880L), com.histmersion.domain.DeepTime.SignedYear(1880L)))
    assertEquals(MediaMatch.map(m, mycenae, onMap, com.histmersion.Year(1928)), Right("location = mycenae; map of 1880–1880 CE"))
    assertEquals(MediaMatch.map(m, greece, onMap, com.histmersion.Year(1928)), Right("location = greece; map of 1880–1880 CE"))
    assertEquals(
      MediaMatch.map(m.modify(_.period).setTo(DeepTime.span(1950L, 1950L)), greece, onMap, com.histmersion.Year(1928)),
      Left("made after 1928")
    )
    assertEquals(
      MediaMatch.map(
        rec(List("location" -> "thebes"), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(1880L), com.histmersion.domain.DeepTime.SignedYear(1880L))),
        MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("thebes_egypt"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Thebes"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("thebes")), None),
        onMap,
        com.histmersion.Year(1928)
      ),
      Left("its place name is ambiguous on the map")
    )

  test("year ranges read as a person reads them; overlap is inclusive"):
    def p(a: Long, b: Long) = DeepTime.span(a, b).get
    assertEquals(MediaMatch.years(p(-200L, 100L)), "200 BCE–100 CE")
    assert(MediaMatch.overlaps(p(-1200L, -1100L), p(-1100L, -1000L)))
    assert(!MediaMatch.overlaps(p(-1200L, -1101L), p(-1100L, -1000L)))

  test("deep time: two years in either order make a period; a year no Year holds makes none"):
    assertEquals(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1100L), com.histmersion.domain.DeepTime.SignedYear(-1200L)), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1200L), com.histmersion.domain.DeepTime.SignedYear(-1100L)))
    assertEquals(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1200L), com.histmersion.domain.DeepTime.SignedYear(-1100L)).map(q => (q.start.value: Int, q.end.value: Int)), Some((-1200, -1100)))
    assertEquals(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(5L), com.histmersion.domain.DeepTime.SignedYear(5L)).map(_.isPoint), Some(true))
    assertEquals(DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-1L), Int.MaxValue.toLong + 1), None)
    assertEquals(DeepTime.year(Int.MinValue.toLong - 1), None)
