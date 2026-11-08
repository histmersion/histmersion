package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.media.imports.MediaRecord
import com.histmersion.domain.catalogue.Place

/** The host's half of the keyless portals (#1446): licences given as Creative Commons URLs, gazetteer ids in their URI
  * forms and the id a place id carries, and the rule that ties a record to a place by its own place id. The real Open
  * Context records' matches (#2571) run over the plugin's parser in the media plugins' suites.
  */
class MediaPortalsSuite extends munit.FunSuite:

  // ── licences and ids ─────────────────────────────────────────────────────────────────────────

  test("a Creative Commons URL is read as its licence; anything else is unknown, never guessed"):
    import Licence.*
    import com.histmersion.domain.Licence.Family.*
    assertEquals(ofUrl(Url("https://creativecommons.org/licenses/by/4.0")), Licence(Licence.Id("CC-BY-4.0"), CcBy))
    assertEquals(
      ofUrl(Url("http://creativecommons.org/licenses/by-sa/3.0/de/")),
      Licence(Licence.Id("CC-BY-SA-3.0-DE"), CcBySa)
    )
    assertEquals(
      ofUrl(Url("https://creativecommons.org/licenses/by-nc/4.0/deed.en")),
      Licence(Licence.Id("CC-BY-NC-4.0"), CcByNc)
    )
    assertEquals(ofUrl(Url("https://creativecommons.org/publicdomain/zero/1.0/")), Licence(Licence.Id("CC0-1.0"), Cc0))
    assertEquals(
      ofUrl(Url("https://creativecommons.org/publicdomain/mark/1.0/")),
      Licence(Licence.Id("public-domain"), PublicDomain)
    )
    assertEquals(ofUrl(Url("https://example.org/terms")).family, Unknown)
    assertEquals(ofUrl(Url("")).family, Unknown)

  test("gazetteer ids in their URI forms, and the id a place id carries itself"):
    assertEquals(
      ExternalIds.of(ExternalIds.Uri("https://pleiades.stoa.org/places/697725")),
      Some("pleiades" -> "697725")
    )
    assertEquals(
      ExternalIds.of(ExternalIds.Uri("http://pleiades.stoa.org/places/697725/")),
      Some("pleiades" -> "697725")
    )
    assertEquals(ExternalIds.of(ExternalIds.Uri("https://www.geonames.org/310858")), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of(ExternalIds.Uri("http://sws.geonames.org/310858/")), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of(ExternalIds.Uri("http://www.wikidata.org/entity/Q5788")), Some("wikidata" -> "Q5788"))
    assertEquals(ExternalIds.of(ExternalIds.Uri("https://opencontext.org/subjects/x")), None)
    assertEquals(ExternalIds.ofPlaceId(Place.Id("pleiades_570491")), Some("pleiades" -> "570491"))
    assertEquals(ExternalIds.ofPlaceId(Place.Id("geonames_390903")), Some("geonames" -> "390903"))
    assertEquals(ExternalIds.ofPlaceId(Place.Id("wikidata_q132564")), Some("wikidata" -> "Q132564"))
    assertEquals(ExternalIds.ofPlaceId(Place.Id("ne_city_1159148451")), None)

  // ── the match by a record's own place id ─────────────────────────────────────────────────────

  private val petra = MediaMatch.PlaceInfo(
    MediaMatch.PlaceInfo.Id("pleiades_697725"),
    MediaMatch.PlaceInfo.Label("Petra"),
    Set(MediaMatch.PlaceInfo.Keys("petra")),
    DeepTime.span(DeepTime.SignedYear(-300L), DeepTime.SignedYear(400L))
  )
  private def rec(
      ids: List[(ExternalIds.Scheme, ExternalIds.ExternalId)],
      geo: List[(MediaRecord.Geography.Field, MediaRecord.Geography.Value)],
      period: Option[Period] = DeepTime.span(DeepTime.SignedYear(-200L), DeepTime.SignedYear(360L))
  ) =
    MediaRecord(
      MediaRecord.Key("k"),
      MediaRecord.Title("t"),
      MediaRecord.PageUrl("p"),
      Some(MediaRecord.ImageUrl("i")),
      None,
      None,
      Left(Problem("x")),
      geo,
      period,
      ids
    )
  private def gp(field: String, value: String) =
    (MediaRecord.Geography.Field(field), MediaRecord.Geography.Value(value))
  private def ext(scheme: String, id: String) = (ExternalIds.Scheme(scheme), ExternalIds.ExternalId(id))
  private val idsOnMap: MediaMatch.ResolveId = Map(
    ext("pleiades", "697725") -> Set(MediaMatch.PlaceInfo.Id("pleiades_697725")),
    ext("geonames", "248816") -> Set(MediaMatch.PlaceInfo.Id("geonames_248816")), // Jordan
    ext("pleiades", "1") -> Set(MediaMatch.PlaceInfo.Id("pleiades_697725"), MediaMatch.PlaceInfo.Id("elsewhere"))
  ).withDefaultValue(Set.empty)
  private val namesOnMap: MediaMatch.Resolve =
    Map(
      MediaMatch.PlaceInfo.Keys("jordan") -> Set(MediaMatch.PlaceInfo.Id("geonames_248816")),
      MediaMatch.PlaceInfo.Keys("lion gate") -> Set(MediaMatch.PlaceInfo.Id("lion_gate"))
    ).withDefaultValue(Set.empty)

  test("a shared place id ties the record; a finer context naming another place on the map refuses it"):
    val geo = List(gp("context", "Trench 94"), gp("context", "Petra Great Temple"), gp("region", "Jordan"))
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "697725")), geo),
        petra,
        idsOnMap,
        namesOnMap,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    val finer = gp("context", "Lion Gate") :: geo
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "697725")), finer),
        petra,
        idsOnMap,
        namesOnMap,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Left("its context names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "697725")), geo, DeepTime.span(DeepTime.SignedYear(800L), DeepTime.SignedYear(900L))),
        petra,
        idsOnMap,
        namesOnMap,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Left("its dates do not overlap the place's")
    )

  test(
    "an id naming only another place refuses it (not filed under the country); an id naming two places is ambiguous"
  ):
    val jordan = MediaMatch.PlaceInfo(
      MediaMatch.PlaceInfo.Id("geonames_248816"),
      MediaMatch.PlaceInfo.Label("Jordan"),
      Set(MediaMatch.PlaceInfo.Keys("jordan")),
      DeepTime.span(DeepTime.SignedYear(-3000L), DeepTime.SignedYear(2000L))
    )
    val geo = List(gp("context", "Petra Great Temple"), gp("region", "Jordan"))
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "697725")), geo),
        jordan,
        idsOnMap,
        namesOnMap,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Left("its place id names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "1")), geo),
        petra,
        idsOnMap,
        namesOnMap,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Left("its place id is ambiguous on the map")
    )

  test("without an id naming a place on the map, the names decide (most specific first), then the dates"):
    val geo = List(gp("context", "Trench 1"), gp("context", "Petra"), gp("region", "Jordan"))
    val withPetra: MediaMatch.Resolve =
      k => if k.value == "petra" then Set(MediaMatch.PlaceInfo.Id("pleiades_697725")) else namesOnMap(k)
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("geonames", "999")), geo),
        petra,
        idsOnMap,
        withPetra,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Right("context = Petra; 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo, None), petra, idsOnMap, withPetra, Set(MediaRecord.Geography.Field("context"))),
      Left("no dates on the record")
    )

  test("a context name shared by two places is settled by an id naming the target alone, else ambiguous"):
    val twoNames: MediaMatch.Resolve =
      k =>
        if k.value == "petra great temple" then
          Set(MediaMatch.PlaceInfo.Id("pleiades_697725"), MediaMatch.PlaceInfo.Id("other"))
        else namesOnMap(k)
    val geo = List(gp("context", "Petra Great Temple"), gp("region", "Jordan"))
    assertEquals(
      MediaMatch.withIds(
        rec(List(ext("pleiades", "697725")), geo),
        petra,
        idsOnMap,
        twoNames,
        Set(MediaRecord.Geography.Field("context"))
      ),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo), petra, idsOnMap, twoNames, Set(MediaRecord.Geography.Field("context"))),
      Left("its context name is ambiguous on the map")
    )
