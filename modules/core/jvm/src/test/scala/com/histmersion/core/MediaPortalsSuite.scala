package com.histmersion.core

import io.circe.Json
import io.circe.parser.parse

/** The pure half of the keyless portals (#1446): licences given as Creative Commons URLs, gazetteer
  * ids in their URI forms, Open Context's search records and media items, the picture links of a
  * Pleiades place record, and the rule that ties a record to a place by its own place id. Fixtures
  * are trimmed real answers: Open Context's search and a media item (2026-10-17), and a Pleiades
  * place record from the 2026-10-17 dump with three real references of other records appended. */
class MediaPortalsSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    parse(scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString).fold(e => fail(e.toString), identity)

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

  // ── Open Context ─────────────────────────────────────────────────────────────────────────────

  test("Open Context: the search URL asks for images with each record's licence, creators, subjects and places"):
    assertEquals(OpenContext.searchUrl("https://opencontext.org/", "Petra", 20),
      "https://opencontext.org/query/.json?type=media&cat=oc-gen-image&q=Petra&rows=20" +
        "&attributes=dc-terms-license%2Cdc-terms-spatial%2Cdc-terms-creator%2Cdc-terms-subject&add-attribute-uris=1" +
        "&response=metadata%2Curi-meta")
    assertEquals(OpenContext.itemUrl("https://opencontext.org", "f589a368-62ed-43df-5738-4c84d2f5f765"),
      "https://opencontext.org/media/f589a368-62ed-43df-5738-4c84d2f5f765.json")

  test("Open Context: a record's own licence and credit form, its context most specific first, its place ids, its dates"):
    val recs = OpenContext.records(fixture("oc-search.json")).fold(fail(_), identity).map(_.fold(fail(_), identity))
    assertEquals(recs.size, 4)
    val bowls = recs(1)
    assertEquals(bowls.key, "f589a368-62ed-43df-5738-4c84d2f5f765")
    assertEquals(bowls.period, Some((-200L, 360L)))
    assertEquals(bowls.ids, List("pleiades" -> "697725"))
    assertEquals(bowls.geography.take(3), List("context" -> "Pottery 248", "context" -> "Locus none provided",
      "context" -> "Trench 94"))
    assertEquals(bowls.geography.filter(_._1 != "context"), List("region" -> "Jordan", "region" -> "Asia",
      "dc-terms:spatial" -> "al-Wuʿaira"), "Open Context's label for Pleiades 697725 (Petra): the id, not the label, is the key")
    assert(bowls.geography.contains("context" -> "Petra Great Temple"))
    val r = bowls.rights.fold(fail(_), identity)
    assertEquals(r.licence, MediaLicence.Licence("CC-BY-4.0", MediaLicence.CcBy))
    assertEquals(r.licenceUrl, Some("https://creativecommons.org/licenses/by/4.0"))
    assertEquals(r.author, Some("Martha Sharp Joukowsky"))
    assertEquals(r.credit, "Martha Sharp Joukowsky (2010). \"Bowls 7 (30-38).jpg from Asia/Jordan/Petra Great Temple/" +
      "Upper Temenos/Trench 94/Locus none provided/Pottery 248\". In Petra Great Temple Excavations. Open Context. " +
      "https://n2t.net/ark:/28722/k2xk8d17p. CC BY 4.0")
    assert(bowls.imageUrl.exists(_.contains("ia-thumbnails")), "the search names only the thumbnail")
    assertEquals(bowls.withheld, None)
    assertEquals(recs(3).withheld, Some("human remains (Open Context flags them; not shown)"))
    assertEquals(OpenContext.records(parse("""{"totalResults":0}""").toOption.get), Right(Nil))

  test("Open Context: a record without a licence, or with an unreadable one, cannot be used"):
    val row = fixture("oc-search.json").hcursor.downField("oc-api:has-results").downArray.focus.get
    val none = OpenContext.record(row.mapObject(_.remove("License [URI]"))).fold(fail(_), identity)
    assertEquals(none.rights, Left("no licence on the record"))
    val odd = OpenContext.record(row.mapObject(_.add("License [URI]", Json.arr(Json.fromString("https://example.org/t")))))
    assert(odd.toOption.get.rights.left.exists(_.startsWith("licence not readable")))

  test("Open Context: the media item gives the files and its licence is read again; a different one refuses it"):
    val rec = OpenContext.records(fixture("oc-search.json")).toOption.get(1).toOption.get
    val item = fixture("oc-media.json")
    val done = OpenContext.item(item, rec).fold(fail(_), identity)
    assertEquals(done.imageUrl, Some("https://storage.googleapis.com/opencontext-media/internet-archive/ia-previews/10-bowls-7-30-38jpg.jpg"))
    assertEquals(done.thumbUrl, Some("https://storage.googleapis.com/opencontext-media/internet-archive/ia-thumbnails/10-bowls-7-30-38jpg.jpg"))
    assertEquals(done.originalUrl, Some("https://archive.org/download/opencontext-10-bowls-7-30-38jpg/10-bowls-7-30-38jpg.jpg"))
    val nc = parse(item.noSpaces.replace("licenses/by/4.0", "licenses/by-nc/4.0")).toOption.get
    assert(OpenContext.item(nc, rec).left.exists(_.contains("differs")))
    assert(OpenContext.item(item.mapObject(_.remove("dc-terms:license")), rec).left.exists(_.contains("no licence")))
    val bones = parse(item.noSpaces.replace("\"Religion\"", "\"Human remains (Archaeology)\"")).toOption.get
    assert(OpenContext.item(bones, rec).left.exists(_.contains("human remains")))

  test("Open Context: the short licence name of a label"):
    assertEquals(OpenContext.shortLicence("Attribution 4.0 International (CC BY 4.0)", "CC-BY-4.0"), "CC BY 4.0")
    assertEquals(OpenContext.shortLicence("", "CC-BY-4.0"), "CC-BY-4.0")

  // ── the match by a record's own place id ─────────────────────────────────────────────────────

  private val petra = MediaMatch.PlaceInfo("pleiades_697725", "Petra", Set("petra"), Some((-300L, 400L)))
  private def rec(ids: List[(String, String)], geo: List[(String, String)], period: Option[(Long, Long)] = Some((-200L, 360L))) =
    MediaRecord("k", "t", "p", Some("i"), None, None, Left("x"), geo, period, ids)
  private val idsOnMap: MediaMatch.ResolveId = Map(
    ("pleiades", "697725") -> Set("pleiades_697725"),
    ("geonames", "248816") -> Set("geonames_248816"),       // Jordan
    ("pleiades", "1") -> Set("pleiades_697725", "elsewhere")).withDefaultValue(Set.empty)
  private val namesOnMap: MediaMatch.Resolve =
    Map("jordan" -> Set("geonames_248816"), "lion gate" -> Set("lion_gate")).withDefaultValue(Set.empty)

  test("a shared place id ties the record; a finer context naming another place on the map refuses it"):
    val geo = List("context" -> "Trench 94", "context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), petra, idsOnMap, namesOnMap, Set("context")),
      Right("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–400 CE"))
    val finer = ("context" -> "Lion Gate") :: geo
    assertEquals(MediaMatch.withIds(rec(List("pleiades" -> "697725"), finer), petra, idsOnMap, namesOnMap, Set("context")),
      Left("its context names another place on the map"))
    assertEquals(MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo, Some((800L, 900L))), petra, idsOnMap,
      namesOnMap, Set("context")), Left("its dates do not overlap the place's"))

  test("an id naming only another place refuses it (not filed under the country); an id naming two places is ambiguous"):
    val jordan = MediaMatch.PlaceInfo("geonames_248816", "Jordan", Set("jordan"), Some((-3000L, 2000L)))
    val geo = List("context" -> "Petra Great Temple", "region" -> "Jordan")
    assertEquals(MediaMatch.withIds(rec(List("pleiades" -> "697725"), geo), jordan, idsOnMap, namesOnMap, Set("context")),
      Left("its place id names another place on the map"))
    assertEquals(MediaMatch.withIds(rec(List("pleiades" -> "1"), geo), petra, idsOnMap, namesOnMap, Set("context")),
      Left("its place id is ambiguous on the map"))

  test("without an id naming a place on the map, the names decide (most specific first), then the dates"):
    val geo = List("context" -> "Trench 1", "context" -> "Petra", "region" -> "Jordan")
    val withPetra: MediaMatch.Resolve = k => if k == "petra" then Set("pleiades_697725") else namesOnMap(k)
    assertEquals(MediaMatch.withIds(rec(List("geonames" -> "999"), geo), petra, idsOnMap, withPetra, Set("context")),
      Right("context = Petra; 200 BCE–360 CE within 300 BCE–400 CE"))
    assertEquals(MediaMatch.withIds(rec(Nil, geo, None), petra, idsOnMap, withPetra, Set("context")),
      Left("no dates on the record"))

  // ── Pleiades ─────────────────────────────────────────────────────────────────────────────────

  test("Pleiades: the place record URL"):
    assertEquals(PleiadesLinks.placeUrl("https://pleiades.stoa.org/", "570491"), "https://pleiades.stoa.org/places/570491/json")

  test("Pleiades: a referenced URL is a Commons file, a picture not taken (and why), or not a picture"):
    import PleiadesLinks.classify
    assertEquals(classify("https://commons.wikimedia.org/wiki/File:Temple_d%27Apollon,_Oppidum_de_Gaujac.JPG"),
      Some(Right("File:Temple d'Apollon, Oppidum de Gaujac.JPG")))
    assertEquals(classify("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Lions_Gate.jpg/330px-Lions_Gate.jpg"),
      Some(Right("File:Lions Gate.jpg")))
    assertEquals(classify("https://upload.wikimedia.org/wikipedia/commons/a/ab/Lions_Gate.jpg"), Some(Right("File:Lions Gate.jpg")))
    assertEquals(classify("https://it.wikipedia.org/wiki/Vara_(fiume)#/media/File:Brugnato-IMG_0146.JPG"),
      Some(Right("File:Brugnato-IMG 0146.JPG")))
    assertEquals(classify("https://en.wikipedia.org/wiki/File:Afrin,Huri.jpg"), Some(Right("File:Afrin,Huri.jpg")))
    assertEquals(classify("https://commons.wikimedia.org/wiki/Category:Msoura?uselang=en"), Some(Left(PleiadesLinks.Category)))
    assertEquals(classify("https://www.flickr.com/photos/dandiffendale/8039595644/"), Some(Left(PleiadesLinks.OnFlickr)))
    assertEquals(classify("=https://elexikon.ch/RE/X,2_2449.png"),
      Some(Left("a picture on elexikon.ch (no documented keyless licence API)")))
    assertEquals(classify("https://en.wikipedia.org/wiki/Mycenae"), None)
    assertEquals(classify("http://www.worldcat.org/oclc/43970336"), None)
    assertEquals(classify("https://www.wikidata.org/wiki/Q87276897"), None)

  test("Pleiades: a place record's picture links, in its order, with their citation type"):
    val ls = PleiadesLinks.links(fixture("pleiades-place.json")).fold(fail(_), identity)
    assertEquals(ls.map(l => (l.citation, l.file)), List(
      ("citesAsRelated", Right("File:Zygouries-General plan of the excavations.jpg")),
      ("citesAsRelated", Left(PleiadesLinks.OnFlickr)),
      ("citesAsRelated", Left(PleiadesLinks.Category)),
      ("seeFurther", Left("a picture on elexikon.ch (no documented keyless licence API)"))))
    assert(PleiadesLinks.links(parse("""{"message":"not found"}""").toOption.get).isLeft)
