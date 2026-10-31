package com.histmersion.domain

/** The host's half of the keyless portals (#1446): licences given as Creative Commons URLs, gazetteer ids in their URI
  * forms and the id a place id carries, and the rule that ties a record to a place by its own place id. The real Open
  * Context records' matches (#2571) run over the plugin's parser in the media plugins' suites.
  */
class MediaPortalsSuite extends munit.FunSuite:

  // ── licences and ids ─────────────────────────────────────────────────────────────────────────

  test("a Creative Commons URL is read as its licence; anything else is unknown, never guessed"):
    import MediaLicence.*
    assertEquals(ofUrl("https://creativecommons.org/licenses/by/4.0"), Licence("CC-BY-4.0", CcBy))
    assertEquals(ofUrl("http://creativecommons.org/licenses/by-sa/3.0/de/"), Licence("CC-BY-SA-3.0-DE", CcBySa))
    assertEquals(ofUrl("https://creativecommons.org/licenses/by-nc/4.0/deed.en"), Licence("CC-BY-NC-4.0", CcByNc))
    assertEquals(ofUrl("https://creativecommons.org/publicdomain/zero/1.0/"), Licence("CC0-1.0", Cc0))
    assertEquals(ofUrl("https://creativecommons.org/publicdomain/mark/1.0/"), Licence("public-domain", PublicDomain))
    assertEquals(ofUrl("https://example.org/terms").family, Unknown)
    assertEquals(ofUrl("").family, Unknown)

  test("gazetteer ids in their URI forms, and the id a place id carries itself"):
    assertEquals(ExternalIds.of("https://pleiades.stoa.org/places/697725"), Some("pleiades" -> "697725"))
    assertEquals(ExternalIds.of("http://pleiades.stoa.org/places/697725/"), Some("pleiades" -> "697725"))
    assertEquals(ExternalIds.of("https://www.geonames.org/310858"), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of("http://sws.geonames.org/310858/"), Some("geonames" -> "310858"))
    assertEquals(ExternalIds.of("http://www.wikidata.org/entity/Q5788"), Some("wikidata" -> "Q5788"))
    assertEquals(ExternalIds.of("https://opencontext.org/subjects/x"), None)
    assertEquals(ExternalIds.ofPlaceId("pleiades_570491"), Some("pleiades" -> "570491"))
    assertEquals(ExternalIds.ofPlaceId("geonames_390903"), Some("geonames" -> "390903"))
    assertEquals(ExternalIds.ofPlaceId("wikidata_q132564"), Some("wikidata" -> "Q132564"))
    assertEquals(ExternalIds.ofPlaceId("ne_city_1159148451"), None)

  // ── the match by a record's own place id ─────────────────────────────────────────────────────

  private val petra = MediaMatch.PlaceInfo("pleiades_697725", "Petra", Set("petra"), DeepTime.span(-300L, 400L))
  private def rec(
      ids: List[(String, String)],
      geo: List[(String, String)],
      period: Option[com.histmersion.values.Period] = DeepTime.span(-200L, 360L)
  ) =
    MediaRecord("k", "t", "p", Some("i"), None, None, Left("x"), geo, period, ids)
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
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), petra, idsOnMap, namesOnMap, Set("context")),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    val finer = ("context" -> "Lion Gate") :: geo
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), finer), petra, idsOnMap, namesOnMap, Set("context")),
      Left("its context names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(
        rec(List("pleiades" -> "697725"), geo, DeepTime.span(800L, 900L)),
        petra,
        idsOnMap,
        namesOnMap,
        Set("context")
      ),
      Left("its dates do not overlap the place's")
    )

  test(
    "an id naming only another place refuses it (not filed under the country); an id naming two places is ambiguous"
  ):
    val jordan = MediaMatch.PlaceInfo("geonames_248816", "Jordan", Set("jordan"), DeepTime.span(-3000L, 2000L))
    val geo = List("context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), jordan, idsOnMap, namesOnMap, Set("context")),
      Left("its place id names another place on the map")
    )
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "1"), geo), petra, idsOnMap, namesOnMap, Set("context")),
      Left("its place id is ambiguous on the map")
    )

  test("without an id naming a place on the map, the names decide (most specific first), then the dates"):
    val geo = List("context" -> "Trench 1", "context" -> "Petra", "region" -> "Jordan")
    val withPetra: MediaMatch.Resolve = k => if k == "petra" then Set("pleiades_697725") else namesOnMap(k)
    assertEquals(
      MediaMatch.withIds(rec(List("geonames" -> "999"), geo), petra, idsOnMap, withPetra, Set("context")),
      Right("context = Petra; 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo, None), petra, idsOnMap, withPetra, Set("context")),
      Left("no dates on the record")
    )

  test("a context name shared by two places is settled by an id naming the target alone, else ambiguous"):
    val twoNames: MediaMatch.Resolve =
      k => if k == "petra great temple" then Set("pleiades_697725", "other") else namesOnMap(k)
    val geo = List("context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(
      MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), petra, idsOnMap, twoNames, Set("context")),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE")
    )
    assertEquals(
      MediaMatch.withIds(rec(Nil, geo), petra, idsOnMap, twoNames, Set("context")),
      Left("its context name is ambiguous on the map")
    )
