package com.histmersion.domain

import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord

/** The host's half of the keyless portals (#1446): licences given as Creative Commons URLs, gazetteer ids in their URI
  * forms and the id a place id carries, and the rule that ties a record to a place by its own place id. The real Open
  * Context records' matches (#2571) run over the plugin's parser in the media plugins' suites.
  */
class MediaPortalsSuite extends munit.FunSuite:

  // ── licences and ids ─────────────────────────────────────────────────────────────────────────

  test("a Creative Commons URL is read as its licence; anything else is unknown, never guessed"):
    import Licence.*
    import com.histmersion.domain.Licence.Family.*
    assertEquals(ofUrl(com.histmersion.domain.Licence.Url("https://creativecommons.org/licenses/by/4.0")), Licence(Licence.Id("CC-BY-4.0"), CcBy))
    assertEquals(
      ofUrl(com.histmersion.domain.Licence.Url("http://creativecommons.org/licenses/by-sa/3.0/de/")),
      Licence(Licence.Id("CC-BY-SA-3.0-DE"), CcBySa)
    )
    assertEquals(
      ofUrl(com.histmersion.domain.Licence.Url("https://creativecommons.org/licenses/by-nc/4.0/deed.en")),
      Licence(Licence.Id("CC-BY-NC-4.0"), CcByNc)
    )
    assertEquals(ofUrl(com.histmersion.domain.Licence.Url("https://creativecommons.org/publicdomain/zero/1.0/")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(
      ofUrl(com.histmersion.domain.Licence.Url("https://creativecommons.org/publicdomain/mark/1.0/")),
      Licence(Licence.Id("public-domain"), PublicDomain)
    )
    assertEquals(ofUrl(com.histmersion.domain.Licence.Url("https://example.org/terms")).family, Unknown)
    assertEquals(ofUrl(com.histmersion.domain.Licence.Url("")).family, Unknown)

  test("gazetteer ids in their URI forms, and the id a place id carries itself"):
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("https://pleiades.stoa.org/places/697725")), Some("pleiades" -> "697725"))
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("http://pleiades.stoa.org/places/697725/")), Some("pleiades" -> "697725"))
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("https://www.geonames.org/310858")), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("http://sws.geonames.org/310858/")), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("http://www.wikidata.org/entity/Q5788")), Some("wikidata" -> "Q5788"))
    assertEquals(ExternalIds.of(com.histmersion.domain.catalogue.places.ExternalIds.Uri("https://opencontext.org/subjects/x")), None)
    assertEquals(ExternalIds.ofPlaceId(com.histmersion.domain.catalogue.Place.Id("pleiades_570491")), Some("pleiades" -> "570491"))
    assertEquals(ExternalIds.ofPlaceId(com.histmersion.domain.catalogue.Place.Id("geonames_390903")), Some("geonames" -> "390903"))
    assertEquals(ExternalIds.ofPlaceId(com.histmersion.domain.catalogue.Place.Id("wikidata_q132564")), Some("wikidata" -> "Q132564"))
    assertEquals(ExternalIds.ofPlaceId(com.histmersion.domain.catalogue.Place.Id("ne_city_1159148451")), None)

  // ── the match by a record's own place id ─────────────────────────────────────────────────────

  private val petra = MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("pleiades_697725"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Petra"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("petra")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-300L), com.histmersion.domain.DeepTime.SignedYear(400L)))
  private def rec(
      ids: List[(String, String)],
      geo: List[(String, String)],
      period: Option[com.histmersion.Period] = DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-200L), com.histmersion.domain.DeepTime.SignedYear(360L))
  ) =
    MediaRecord(com.histmersion.domain.media.imports.MediaRecord.Key("k"), com.histmersion.domain.media.imports.MediaRecord.Title("t"), com.histmersion.domain.media.imports.MediaRecord.PageUrl("p"), Some(com.histmersion.domain.media.imports.MediaRecord.ImageUrl("i")), None, None, Left(com.histmersion.domain.media.imports.MediaRecord.RightsPart1("x")), geo, period, ids)
  private val idsOnMap: MediaMatch.ResolveId = Map(
    ("pleiades", "697725") -> Set("pleiades_697725"),
    ("geonames", "248816") -> Set("geonames_248816"), // Jordan
    ("pleiades", "1") -> Set("pleiades_697725", "elsewhere")
  ).withDefaultValue(Set.empty)
  private val namesOnMap: MediaMatch.Resolve =
    Map("jordan" -> Set("geonames_248816"), "lion gate" -> Set("lion_gate")).withDefaultValue(Set.empty)

  test("a shared place id ties the record; a finer context naming another place on the map refuses it"):
    val geo = List("context" -> "Trench 94", "context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), petra, idsOnMap, namesOnMap, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    val finer = ("context" -> "Lion Gate") :: geo
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), finer), petra, idsOnMap, namesOnMap, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Left("its context names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(
        rec(List("pleiades" -> "697725"), geo, DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(800L), com.histmersion.domain.DeepTime.SignedYear(900L))),
        petra,
        idsOnMap,
        namesOnMap,
        Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))
      ),
      Left("its dates do not overlap the place's")
    )

  test(
    "an id naming only another place refuses it (not filed under the country); an id naming two places is ambiguous"
  ):
    val jordan = MediaMatch.PlaceInfo(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("geonames_248816"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Label("Jordan"), Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Keys("jordan")), DeepTime.span(com.histmersion.domain.DeepTime.SignedYear(-3000L), com.histmersion.domain.DeepTime.SignedYear(2000L)))
    val geo = List("context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), jordan, idsOnMap, namesOnMap, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Left("its place id names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "1"), geo), petra, idsOnMap, namesOnMap, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Left("its place id is ambiguous on the map")
    )

  test("without an id naming a place on the map, the names decide (most specific first), then the dates"):
    val geo = List("context" -> "Trench 1", "context" -> "Petra", "region" -> "Jordan")
    val withPetra: MediaMatch.Resolve = k => if k == "petra" then Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("pleiades_697725")) else namesOnMap(k)
    assertEquals(
      MediaMatch.withIds(rec(List("geonames" -> "999"), geo), petra, idsOnMap, withPetra, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Right("context = Petra; 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo, None), petra, idsOnMap, withPetra, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Left("no dates on the record")
    )

  test("a context name shared by two places is settled by an id naming the target alone, else ambiguous"):
    val twoNames: MediaMatch.Resolve =
      k => if k == "petra great temple" then Set(com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("pleiades_697725"), com.histmersion.domain.media.imports.MediaMatch.PlaceInfo.Id("other")) else namesOnMap(k)
    val geo = List("context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), petra, idsOnMap, twoNames, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo), petra, idsOnMap, twoNames, Set(com.histmersion.domain.media.imports.MediaRecord.GeographyPart1("context"))),
      Left("its context name is ambiguous on the map")
    )
