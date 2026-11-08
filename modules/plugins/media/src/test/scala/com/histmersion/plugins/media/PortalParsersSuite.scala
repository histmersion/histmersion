package com.histmersion
package plugins.media

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.domain.DeepTime
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.pluginhost.{HostLicences, PluginMedia}
import io.circe.Json
import io.circe.parser.parse
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.media.imports.MediaRecord

/** The keyless portals' parsers (#1446): gazetteer ids in their URI forms, Open Context's search records and media
  * items (the licence stated as the record's own Creative Commons URL, read by the host), and the picture links of a
  * Pleiades place record. Fixtures are trimmed real answers: Open Context's search and a media item (2026-10-17), real
  * Domuztepe records (2026-10-23), and a Pleiades place record from the 2026-10-17 dump with three real references of
  * other records appended.
  */
class PortalParsersSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    parse(scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(e.toString), identity)

  private val licences = HostLicences

  // ── ids ──────────────────────────────────────────────────────────────────────────────────────

  test("gazetteer ids in their URI forms"):
    assertEquals(
      GazetteerUri.of(GazetteerPlace.Id("https://pleiades.stoa.org/places/697725")),
      Some("pleiades" -> "697725")
    )
    assertEquals(
      GazetteerUri.of(GazetteerPlace.Id("http://pleiades.stoa.org/places/697725/")),
      Some("pleiades" -> "697725")
    )
    assertEquals(GazetteerUri.of(GazetteerPlace.Id("https://www.geonames.org/310858")), Some("geonames" -> "310858"))
    assertEquals(GazetteerUri.of(GazetteerPlace.Id("http://sws.geonames.org/310858/")), Some("geonames" -> "310858"))
    assertEquals(
      GazetteerUri.of(GazetteerPlace.Id("http://www.wikidata.org/entity/Q5788")),
      Some("wikidata" -> "Q5788")
    )
    assertEquals(GazetteerUri.of(GazetteerPlace.Id("https://opencontext.org/subjects/x")), None)
    assertEquals(GazetteerUri.of(GazetteerPlace.Id(null)), None)

  // ── Open Context ─────────────────────────────────────────────────────────────────────────────

  test("Open Context: the search URL asks for images with each record's licence, creators, subjects and places"):
    assertEquals(
      OpenContext.searchUrl(
        CollectionSettings.ApiUrl("https://opencontext.org/"),
        MediaPlace.Label("Petra"),
        CollectionSettings.Candidates(20)
      ),
      "https://opencontext.org/query/.json?type=media&cat=oc-gen-image&q=Petra&rows=20" +
        "&attributes=dc-terms-license%2Cdc-terms-spatial%2Cdc-terms-creator%2Cdc-terms-subject&add-attribute-uris=1" +
        "&response=metadata%2Curi-meta"
    )
    assertEquals(
      OpenContext.itemUrl(
        CollectionSettings.ApiUrl("https://opencontext.org"),
        OpenContext.Uuid("f589a368-62ed-43df-5738-4c84d2f5f765")
      ),
      "https://opencontext.org/media/f589a368-62ed-43df-5738-4c84d2f5f765.json"
    )

  test(
    "Open Context: a record's own licence and credit form, its context most specific first, its place ids, its dates"
  ):
    val recs =
      OpenContext
        .records(fixture("oc-search.json"), licences)
        .fold(e => fail(e.value), identity)
        .map(_.fold(e => fail(e.value), identity))
    assertEquals(recs.size, 4)
    val bowls = recs(1)
    assertEquals(bowls.key, "f589a368-62ed-43df-5738-4c84d2f5f765")
    assertEquals(bowls.period, Period.span(-200L, 360L))
    assertEquals(bowls.ids, List("pleiades" -> "697725"))
    // the excavation units (Pottery 248, Locus none provided, Trench 94) name no place (#2571)
    assertEquals(bowls.geography.take(2), List("context" -> "Upper Temenos", "context" -> "Petra Great Temple"))
    assertEquals(
      bowls.geography.filter(_._1.value != "context"),
      List("region" -> "Jordan", "region" -> "Asia", "dc-terms:spatial" -> "al-Wuʿaira"),
      "Open Context's label for Pleiades 697725 (Petra): the id, not the label, is the key"
    )
    assert(bowls.geography.contains("context" -> "Petra Great Temple"))
    val r = bowls.rights.fold(e => fail(e.value), identity)
    assertEquals(r.licence, Licence("https://creativecommons.org/licenses/by/4.0"), "stated as the record states it")
    assertEquals(
      licences.readUrl(LicenceReader.ReadUrlUrl(r.licence.value)),
      Licence.Read(
        Licence.Read.Id("CC-BY-4.0"),
        Licence.Read.Family("cc-by"),
        needsAttribution = Licence.Read.NeedsAttribution(true)
      ),
      "the host reads it as CC BY 4.0"
    )
    assertEquals(r.licenceUrl, Some("https://creativecommons.org/licenses/by/4.0"))
    assertEquals(r.author, Some("Martha Sharp Joukowsky"))
    assertEquals(
      r.credit,
      "Martha Sharp Joukowsky (2010). \"Bowls 7 (30-38).jpg from Asia/Jordan/Petra Great Temple/" +
        "Upper Temenos/Trench 94/Locus none provided/Pottery 248\". In Petra Great Temple Excavations. Open Context. " +
        "https://n2t.net/ark:/28722/k2xk8d17p. CC BY 4.0"
    )
    assert(bowls.imageUrl.exists(_.value.contains("ia-thumbnails")), "the search names only the thumbnail")
    assertEquals(bowls.withheld, None)
    assertEquals(recs(3).withheld, Some("human remains (Open Context flags them; not shown)"))
    assertEquals(OpenContext.records(parse("""{"totalResults":0}""").toOption.get, licences), Right(Nil))

  test("Open Context: a record without a licence, or with an unreadable one, cannot be used"):
    val row = fixture("oc-search.json").hcursor.downField("oc-api:has-results").downArray.focus.get
    val none = OpenContext.record(row.mapObject(_.remove("License [URI]")), licences).fold(e => fail(e.value), identity)
    assertEquals(none.rights, Left("no licence on the record"))
    val odd = OpenContext.record(
      row.mapObject(_.add("License [URI]", Json.arr(Json.fromString("https://example.org/t")))),
      licences
    )
    assert(odd.toOption.get.rights.left.exists(_.value.startsWith("licence not readable")))
    assert(
      OpenContext
        .record(row, LicenceReader.Unread)
        .toOption
        .get
        .rights
        .left
        .exists(_.value.startsWith("licence not readable")),
      "a host that lends no reading: no licence is readable, so none is stated"
    )

  test("Open Context: the media item gives the files and its licence is read again; a different one refuses it"):
    val rec = OpenContext.records(fixture("oc-search.json"), licences).toOption.get(1).toOption.get
    val item = fixture("oc-media.json")
    val done = OpenContext.item(item, rec, licences).fold(e => fail(e.value), identity)
    assertEquals(
      done.imageUrl,
      Some("https://storage.googleapis.com/opencontext-media/internet-archive/ia-previews/10-bowls-7-30-38jpg.jpg")
    )
    assertEquals(
      done.thumbUrl,
      Some("https://storage.googleapis.com/opencontext-media/internet-archive/ia-thumbnails/10-bowls-7-30-38jpg.jpg")
    )
    assertEquals(
      done.originalUrl,
      Some("https://archive.org/download/opencontext-10-bowls-7-30-38jpg/10-bowls-7-30-38jpg.jpg")
    )
    val nc = parse(item.noSpaces.replace("licenses/by/4.0", "licenses/by-nc/4.0")).toOption.get
    assert(OpenContext.item(nc, rec, licences).left.exists(_.value.contains("differs")))
    assert(
      OpenContext
        .item(item.mapObject(_.remove("dc-terms:license")), rec, licences)
        .left
        .exists(_.value.contains("no licence"))
    )
    val bones = parse(item.noSpaces.replace("\"Religion\"", "\"Human remains (Archaeology)\"")).toOption.get
    assert(OpenContext.item(bones, rec, licences).left.exists(_.value.contains("human remains")))

  test("Open Context: the short licence name of a label"):
    assertEquals(
      OpenContext.shortLicence(Licence("Attribution 4.0 International (CC BY 4.0)"), Licence.Read.Id("CC-BY-4.0")),
      "CC BY 4.0"
    )
    assertEquals(OpenContext.shortLicence(Licence(""), Licence.Read.Id("CC-BY-4.0")), "CC-BY-4.0")

  // ── #2571: real Domuztepe records (Open Context, 2026-10-23) ─────────────────────────────────

  test("Open Context: excavation units and items are not places; a site's named parts are"):
    for u <- List(
        "I",
        "IV",
        "Lot 666",
        "2631.1",
        "DT# 347",
        "Trench 94",
        "Locus none provided",
        "Seq. 42112",
        "A",
        "Survey",
        "Stray",
        "Stray Finds",
        "Area",
        "Pottery 248"
      )
    do assert(OpenContext.unitSegment(OpenContext.Segment(u)), u)
    for p <- List("Domuztepe", "Petra Great Temple", "Upper Temenos", "Lower Town", "Ishtar Gate") do
      assert(!OpenContext.unitSegment(OpenContext.Segment(p)), p)
    val recs = OpenContext
      .records(fixture("oc-search-domuztepe.json"), licences)
      .fold(e => fail(e.value), identity)
      .map(_.fold(e => fail(e.value), identity))
    assertEquals(
      recs.map(_.geography.filter(_._1 == OpenContext.Context)),
      List.fill(4)(List("context" -> "Domuztepe"))
    )
    assertEquals(recs.head.ids, List("geonames" -> "310858", "geonames" -> "6269133"), "a province and the Middle East")
    assertEquals(recs.map(_.withheld.isDefined), List(false, false, false, true), "the crania are withheld")
    assertEquals(recs.flatMap(_.period).distinct, Period.span(-6500L, -5500L).toList)

  // the host's rule over the plugin's records, read as the host reads them
  private def domuztepeRecords =
    OpenContext
      .records(fixture("oc-search-domuztepe.json"), licences)
      .fold(e => fail(e.value), identity)
      .map(_.fold(e => fail(e.value), identity))
      .map(PluginMedia.record)

  private val domuztepe =
    MediaMatch.PlaceInfo(
      MediaMatch.PlaceInfo.Id("wikidata_q4165990"),
      MediaMatch.PlaceInfo.Label("Domuztepe"),
      Set(MediaMatch.PlaceInfo.Keys("domuztepe")),
      DeepTime.span(DeepTime.SignedYear(-6500L), DeepTime.SignedYear(-5400L))
    )
  // the live map (2026-10-23) has a place called "I" and Kahramanmaraş; the record's ids name the province
  private val domNames: MediaMatch.Resolve = Map(
    MediaMatch.PlaceInfo.Keys("domuztepe") -> Set(MediaMatch.PlaceInfo.Id("wikidata_q4165990")),
    MediaMatch.PlaceInfo.Keys("i") -> Set(MediaMatch.PlaceInfo.Id("wikidata_q103776406")),
    MediaMatch.PlaceInfo.Keys("turkey") -> Set(MediaMatch.PlaceInfo.Id("ne_country_tur")),
    MediaMatch.PlaceInfo.Keys("asia") -> Set(MediaMatch.PlaceInfo.Id("asia"))
  ).withDefaultValue(Set.empty)
  private val domIds: MediaMatch.ResolveId =
    Map(
      (
        ExternalIds.Scheme("geonames"),
        ExternalIds.ExternalId("310858")
      ) -> Set(MediaMatch.PlaceInfo.Id("ne_city_1159127453"))
    ).withDefaultValue(Set.empty)
  private val deeper = Set(MediaRecord.Geography.Field(OpenContext.Context.value))

  test("a context naming the site ties the record although its ids name only its province (#2571)"):
    val recs = domuztepeRecords
    assertEquals(
      recs.map(MediaMatch.withIds(_, domuztepe, domIds, domNames, deeper)).distinct,
      List(Right("context = Domuztepe; 6500–5500 BCE within 6500–5400 BCE"))
    )
    // the province's own place is not given the site's finds: the finer context names another place
    val kahramanmaras =
      MediaMatch.PlaceInfo(
        MediaMatch.PlaceInfo.Id("ne_city_1159127453"),
        MediaMatch.PlaceInfo.Label("Kahramanmaraş"),
        Set(MediaMatch.PlaceInfo.Keys("kahramanmaras")),
        DeepTime.span(DeepTime.SignedYear(-8000L), DeepTime.SignedYear(2000L))
      )
    assertEquals(
      MediaMatch.withIds(recs.head, kahramanmaras, domIds, domNames, deeper),
      Left("its context names another place on the map")
    )
    // the unit "I" kept as a context field (as before #2571) refused it: the map's "I" is another place
    val before = recs.head.copy(geography =
      (MediaRecord.Geography.Field("context") -> MediaRecord.Geography.Value("I")) :: recs.head.geography
    )
    assertEquals(
      MediaMatch.withIds(before, domuztepe, domIds, domNames, deeper),
      Left("its context names another place on the map")
    )

  // ── Pleiades ─────────────────────────────────────────────────────────────────────────────────

  test("Pleiades: the place record URL"):
    assertEquals(
      PleiadesLinks.placeUrl(CollectionSettings.ApiUrl("https://pleiades.stoa.org/"), GazetteerPlace.Id("570491")),
      "https://pleiades.stoa.org/places/570491/json"
    )

  test("Pleiades: a referenced URL is a Commons file, a picture not taken (and why), or not a picture"):
    import PleiadesLinks.classify
    assertEquals(
      classify(Plugin.Http.Url("https://commons.wikimedia.org/wiki/File:Temple_d%27Apollon,_Oppidum_de_Gaujac.JPG")),
      Some(Right("File:Temple d'Apollon, Oppidum de Gaujac.JPG"))
    )
    assertEquals(
      classify(
        Plugin.Http.Url(
          "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Lions_Gate.jpg/330px-Lions_Gate.jpg"
        )
      ),
      Some(Right("File:Lions Gate.jpg"))
    )
    assertEquals(
      classify(Plugin.Http.Url("https://upload.wikimedia.org/wikipedia/commons/a/ab/Lions_Gate.jpg")),
      Some(Right("File:Lions Gate.jpg"))
    )
    assertEquals(
      classify(Plugin.Http.Url("https://it.wikipedia.org/wiki/Vara_(fiume)#/media/File:Brugnato-IMG_0146.JPG")),
      Some(Right("File:Brugnato-IMG 0146.JPG"))
    )
    assertEquals(
      classify(Plugin.Http.Url("https://en.wikipedia.org/wiki/File:Afrin,Huri.jpg")),
      Some(Right("File:Afrin,Huri.jpg"))
    )
    assertEquals(
      classify(Plugin.Http.Url("https://commons.wikimedia.org/wiki/Category:Msoura?uselang=en")),
      Some(Left(PleiadesLinks.Category))
    )
    assertEquals(
      classify(Plugin.Http.Url("https://www.flickr.com/photos/dandiffendale/8039595644/")),
      Some(Left(PleiadesLinks.OnFlickr))
    )
    assertEquals(
      classify(Plugin.Http.Url("=https://elexikon.ch/RE/X,2_2449.png")),
      Some(Left(PleiadesLinks.Problem("a picture on elexikon.ch (no documented keyless licence API)")))
    )
    assertEquals(classify(Plugin.Http.Url("https://en.wikipedia.org/wiki/Mycenae")), None)
    assertEquals(classify(Plugin.Http.Url("http://www.worldcat.org/oclc/43970336")), None)
    assertEquals(classify(Plugin.Http.Url("https://www.wikidata.org/wiki/Q87276897")), None)

  test("Pleiades: a place record's picture links, in its order, with their citation type"):
    val ls = PleiadesLinks.links(fixture("pleiades-place.json")).fold(e => fail(e.value), identity)
    assertEquals(
      ls.map(l => (l.citation, l.file)),
      List[(String, Either[PleiadesLinks.Problem, String])](
        ("citesAsRelated", Right("File:Zygouries-General plan of the excavations.jpg")),
        ("citesAsRelated", Left(PleiadesLinks.OnFlickr)),
        ("citesAsRelated", Left(PleiadesLinks.Category)),
        ("seeFurther", Left(PleiadesLinks.Problem("a picture on elexikon.ch (no documented keyless licence API)")))
      )
    )
    assert(PleiadesLinks.links(parse("""{"message":"not found"}""").toOption.get).isLeft)
