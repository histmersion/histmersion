package com.histmersion
package plugins.media
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.pluginhost.HostLicences

/** The parsers of the two key-gated collections: Smithsonian Open Access and Europeana. The Smithsonian fixture's rows
  * are three real records of the Smithsonian's open-data dump (National Museum of Asian Art, read 2026-10-26;
  * provenance notes trimmed) — the schema the API's rows carry — inside the search envelope its API documentation
  * shows. The Europeana fixtures are CONSTRUCTED in the shape its Search and Record API documentation lists (no key to
  * read a real answer); every value in them is made up. Each item states its licence as the record does (Europeana: its
  * edm:rights URL); the host reads it ([[HostLicences]]).
  */
class KeyedParsersSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    RawJson
      .parse(scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString)
      .fold(e => fail(e.toString), identity)

  private val licences = HostLicences

  // ── free-text dates ──────────────────────────────────────────────────────────────────────────

  test("free-text dates: years, ranges, eras carried back, centuries, millennia, hedges"):
    val cases = List(
      "1550-1196 BCE" -> Some((-1550L, -1196L)),
      "305-30 BCE" -> Some((-305L, -30L)),
      "100 BCE-100 CE" -> Some((-100L, 100L)),
      "1539 BCE-395 CE" -> Some((-1539L, 395L)),
      "ca. 3300-ca. 2250 BCE" -> Some((-3300L, -2250L)),
      "ca. 1809" -> Some((1809L, 1809L)),
      "220-589" -> Some((220L, 589L)),
      "700-750 CE" -> Some((700L, 750L)),
      "1883 or 1884" -> Some((1883L, 1884L)),
      "5th century" -> Some((401L, 500L)),
      "5th century BCE" -> Some((-500L, -401L)),
      "12th-13th century" -> Some((1101L, 1300L)),
      "late 12th-early 13th century" -> Some((1101L, 1300L)),
      "mid-19th century" -> Some((1801L, 1900L)),
      "2nd millennium BCE" -> Some((-2000L, -1001L)),
      "1400 B.C.-1200 B.C." -> Some((-1400L, -1200L)),
      "1550–1196 BCE" -> Some((-1550L, -1196L))
    )
    cases.foreach((t, want) => assertEquals(FreeTextDates.parse(FreeTextDates.Text(t)), want, t))

  test("free-text dates: uncertain, backwards or unreadable texts are not read"):
    List("16th century?", "1550-1196", "New Kingdom", "", "undated", "12th", "1883/1884 or later", "0")
      .foreach(t => assertEquals(FreeTextDates.parse(FreeTextDates.Text(t)), None, t))

  // ── Smithsonian ──────────────────────────────────────────────────────────────────────────────

  test("Smithsonian: the search URL — the name as a phrase, images only, edanmdm objects; the key is NOT in it"):
    val u = Smithsonian.searchUrl(
      CollectionSettings.ApiUrl("https://api.si.edu/openaccess/api/v1.0/"),
      MediaPlace.Label("Tell el-Amarna"),
      CollectionSettings.Candidates(20)
    )
    assertEquals(
      u,
      "https://api.si.edu/openaccess/api/v1.0/search?q=%22Tell+el-Amarna%22+AND+online_media_type%3AImages" +
        "&rows=20&type=edanmdm&row_group=objects"
    )
    assert(!u.value.contains("api_key"))
    assert(
      Smithsonian
        .searchUrl(CollectionSettings.ApiUrl("x"), MediaPlace.Label("a"), CollectionSettings.Candidates(5000))
        .value
        .contains("rows=1000"),
      "the documented maximum"
    )

  test("Smithsonian: records — CC0 from the media item's usage.access, its screen image stored, places and dates"):
    val rs = Smithsonian
      .records(fixture("si-search.json"))
      .fold(e => fail(e.sentence), identity)
      .map(_.fold(e => fail(e.sentence), identity))
    assertEquals(rs.map(_.key), List("fsg_F1909.1185", "fsg_F1908.119", "fsg_F1916.118"))
    val bead = rs.head
    assertEquals(bead.title, "Bead")
    assertEquals(bead.pageUrl, "https://asia.si.edu/object/F1909.1185/")
    assertEquals(bead.imageUrl, Some("https://ids.si.edu/ids/download?id=FS-8431_10_screen"))
    assertEquals(bead.thumbUrl, Some("https://ids.si.edu/ids/download?id=FS-8431_10_thumb"))
    assertEquals(bead.originalUrl, Some("https://ids.si.edu/ids/download?id=FS-8431_10.jpg"))
    assertEquals(bead.period, Period.span(-1550L, -1196L), "the Date entry, not the Period name")
    assertEquals(
      bead.geography,
      List("origin" -> "Egypt", "continent" -> "Africa"),
      "Egypt once; then the coarser levels"
    )
    val rights = bead.rights.fold(e => fail(e.sentence), identity)
    assertEquals(rights.licence, Licence("CC0-1.0"))
    assertEquals(
      licences.readName(LicenceReader.ReadNameName(rights.licence.value)),
      Licence.Read(Licence.Read.Id("CC0-1.0"), Licence.Read.Family(Licence.Read.Cc0))
    )
    assertEquals(
      rights.credit,
      "Bead, National Museum of Asian Art, Smithsonian Institution, Gift of Charles Lang Freer, CC0"
    )
    val tile = rs(1)
    assertEquals(tile.geography, List("origin" -> "Raqqa", "origin" -> "Syria"), "comma parts, most specific first")
    assertEquals(tile.period, Period.span(1101L, 1300L))
    assertEquals(rs(2).period, Period.span(-3300L, -2250L))
    assertEquals(rs(2).geography.head, "origin" -> "Lake Tai region")

  test("Smithsonian: a record whose image is not CC0, or that has no image, is refused with the reason"):
    val row = fixture("si-search.json").cursor.downField("response").downField("rows").downArray.focus.get
    val restricted =
      RawJson.parse(row.noSpaces.replace("\"access\":\"CC0\"", "\"access\":\"Usage conditions apply\"")).toOption.get
    assertEquals(
      Smithsonian.record(restricted).map(_.rights.left.toOption.map(_.sentence)),
      Right(
        Some("media-smithsonian did not take fsg_F1909.1185: no CC0 image (usage.access is Usage conditions apply)")
      )
    )
    val none = RawJson.parse(row.noSpaces.replace("\"online_media\"", "\"x_online_media\"")).toOption.get
    assertEquals(
      Smithsonian.record(none).map(_.rights.left.toOption.map(_.sentence)),
      Right(Some("media-smithsonian did not take fsg_F1909.1185: no image"))
    )
    // an accession or collection date is not the object's date
    val accession =
      RawJson.parse(row.noSpaces.replace("\"label\":\"Date\"", "\"label\":\"Accession Date\"")).toOption.get
    assertEquals(Smithsonian.record(accession).map(_.period), Right(None))

  test("Smithsonian: error answers — api.data.gov's key errors and EDAN's — are Lefts; an empty answer is no rows"):
    val gov = RawJson
      .parse(
        """{"error":{"code":"API_KEY_INVALID","message":"An invalid api_key was supplied. Get one at https://api.si.edu:443"}}"""
      )
      .toOption
      .get
    assertEquals(
      Smithsonian.records(gov).left.map(_.sentence),
      Left(
        "media-smithsonian (failed): API_KEY_INVALID: An invalid api_key was supplied. Get one at https://api.si.edu:443"
      )
    )
    val edan = RawJson
      .parse(
        """{"status":400,"responseCode":0,"response":{"error":"bad request: one of your params did not pass validation"}}"""
      )
      .toOption
      .get
    assertEquals(
      Smithsonian.records(edan).left.map(_.sentence),
      Left("media-smithsonian (failed): bad request: one of your params did not pass validation")
    )
    val empty = RawJson
      .parse(
        """{"status":200,"responseCode":1,"response":{"rows":[],"rowCount":0,"message":"content found"}}"""
      )
      .toOption
      .get
    assertEquals(Smithsonian.records(empty), Right(Nil))

  test("Smithsonian: the search leaves out the excluded units; a row of one is withheld with the reason"):
    assertEquals(
      Smithsonian.query(MediaPlace.Label("Orkney"), List(Smithsonian.Unit("NMNHINV"), Smithsonian.Unit("NMNHBIRDS"))),
      "\"Orkney\" AND online_media_type:Images AND NOT unit_code:(NMNHINV OR NMNHBIRDS)"
    )
    assertEquals(Smithsonian.query(MediaPlace.Label("Orkney")), "\"Orkney\" AND online_media_type:Images")
    val row = fixture("si-search.json").cursor.downField("response").downField("rows").downArray.focus.get
    assertEquals(Smithsonian.unitOf(row), Some("FSG"))
    assertEquals(Smithsonian.record(row, List(Smithsonian.Unit("NMNHINV"))).map(_.withheld), Right(None))
    val specimen = RawJson.parse(row.noSpaces.replace("\"FSG\"", "\"NMNHINV\"")).toOption.get
    assertEquals(
      Smithsonian.record(specimen, List(Smithsonian.Unit("nmnhinv"))).map(_.withheld),
      Right(Some("an excluded unit (NMNHINV)"))
    )

  // ── Europeana ────────────────────────────────────────────────────────────────────────────────

  private val api = CollectionSettings.ApiUrl("https://api.europeana.eu")

  test("Europeana: the search URL — where:(name), reusability=open, media and thumbnail, images, rich profile; no key"):
    val u = Europeana.searchUrl(
      CollectionSettings.ApiUrl(api.value + "/"),
      MediaPlace.Label("Tell el-Amarna"),
      CollectionSettings.Candidates(20)
    )
    assertEquals(
      u,
      "https://api.europeana.eu/record/v2/search.json?query=where%3A%28%22Tell+el-Amarna%22%29" +
        "&reusability=open&media=true&thumbnail=true&qf=TYPE%3AIMAGE&profile=rich&rows=20"
    )
    assert(!u.value.contains("wskey"))
    assert(
      Europeana.searchUrl(api, MediaPlace.Label("a"), CollectionSettings.Candidates(500)).value.endsWith("rows=100"),
      "the documented maximum"
    )
    assertEquals(
      Europeana.recordUrl(api, MediaItem.Key("/9200999/fixture_mycenae_lion_gate")),
      "https://api.europeana.eu/record/v2/9200999/fixture_mycenae_lion_gate.json"
    )

  test("Europeana: the Thumbnail API v3 URL hashes the media URL with MD5 (the documentation's own example)"):
    assertEquals(
      Europeana.thumbnailUrl(
        api,
        Europeana.Source("http://www.mimo-db.eu/media/UEDIN/IMAGE/0032195c.jpg"),
        MediaRendition.Width(200)
      ),
      "https://api.europeana.eu/thumbnail/v3/200/820911931db3ccb20e1b7a022ee6dd33.jpg"
    )
    assertEquals(
      Europeana.previewSource(
        Europeana.Preview(
          "https://api.europeana.eu/thumbnail/v2/url.json?uri=https%3A%2F%2Fa.example%2Fb.jpg&type=IMAGE"
        )
      ),
      Some("https://a.example/b.jpg")
    )
    assertEquals(Europeana.previewSource(Europeana.Preview("https://a.example/b.jpg")), None)

  test("Europeana: items — each one's own rights URL as its licence, places and ids, signed years, renditions"):
    val rs =
      Europeana
        .records(fixture("eu-search.json"), api, licences)
        .fold(e => fail(e.sentence), identity)
        .map(_.fold(e => fail(e.sentence), identity))
    assertEquals(rs.size, 3)
    val gate = rs.head
    assertEquals(gate.key, "9200999/fixture_mycenae_lion_gate")
    assertEquals(
      gate.pageUrl,
      "https://www.europeana.eu/item/9200999/fixture_mycenae_lion_gate",
      "no key-bearing API link"
    )
    assertEquals(
      gate.geography,
      List(
        Europeana.Spatial -> "Mycenae",
        Europeana.Place -> "Mykines",
        Europeana.Place -> "Mycenae",
        Europeana.Place -> "Μυκήνες"
      )
    )
    assertEquals(gate.ids, List("geonames" -> "256075"))
    assertEquals(gate.period, Period.span(-1250L, -1250L))
    assertEquals(
      gate.imageUrl.map(_.value),
      Some(
        Europeana
          .thumbnailUrl(
            api,
            Europeana.Source("https://images.fixture.example/lion-gate.jpg"),
            MediaRendition.Width(400)
          )
          .value
      )
    )
    assertEquals(
      gate.thumbUrl.map(_.value),
      Some(
        Europeana
          .thumbnailUrl(
            api,
            Europeana.Source("https://images.fixture.example/lion-gate.jpg"),
            MediaRendition.Width(200)
          )
          .value
      )
    )
    assertEquals(gate.originalUrl, Some("https://images.fixture.example/lion-gate.jpg"))
    val r = gate.rights.fold(e => fail(e.sentence), identity)
    assertEquals(r.licence, Licence("http://creativecommons.org/licenses/by-sa/4.0/"), "its edm:rights URL")
    assertEquals(
      licences.readUrl(LicenceReader.ReadUrlUrl(r.licence.value)),
      Licence.Read(
        Licence.Read.Id("CC-BY-SA-4.0"),
        Licence.Read.Family("cc-by-sa"),
        needsAttribution = Licence.Read.NeedsAttribution(true)
      )
    )
    assertEquals(r.author, Some("Fixture Photographer"))
    assertEquals(r.credit, "The Lion Gate, Mycenae, Fixture Photographer, Fixture Archive, via Europeana, CC BY-SA 4.0")
    assertEquals(
      rs(1).rights.map(x => licences.readUrl(LicenceReader.ReadUrlUrl(x.licence.value)).family),
      Right(Licence.Read.PublicDomain)
    )
    assertEquals(
      rs(1).rights.map(_.credit),
      Right("Plan of the citadel, Fixture Library, via Europeana, Public Domain Mark")
    )
    assertEquals(
      rs(2).rights.left.toOption.map(_.sentence),
      Some("/9200999/fixture_mycenae_nc: unknown Licence 'http://rightsstatements.org/vocab/NoC-NC/1.0/'")
    )

  test("Europeana: licence short names for the credit line"):
    assertEquals(Europeana.shortName(licences.readName(LicenceReader.ReadNameName("cc-by-4.0"))), "CC BY 4.0")
    assertEquals(
      Europeana.shortName(
        licences.readUrl(LicenceReader.ReadUrlUrl("http://creativecommons.org/licenses/by-sa/3.0/de/"))
      ),
      "CC BY-SA 3.0 DE"
    )
    assertEquals(
      Europeana.shortName(
        licences.readUrl(LicenceReader.ReadUrlUrl("http://creativecommons.org/publicdomain/zero/1.0/"))
      ),
      "CC0"
    )

  test("Europeana: errors — a refused key is a Left with Europeana's message; no items is an empty list"):
    val bad = RawJson
      .parse(
        """{"success":false,"error":"API key is invalid","message":"Please register for an API key","code":"401_key_invalid"}"""
      )
      .toOption
      .get
    assertEquals(
      Europeana.records(bad, api, licences).left.map(_.sentence),
      Left("media-europeana (failed): API key is invalid: Please register for an API key")
    )
    val none = RawJson.parse("""{"success":true,"itemsCount":0,"totalResults":0}""").toOption.get
    assertEquals(Europeana.records(none, api, licences), Right(Nil))

  test("Europeana: the Record API reads the licence again — the shown web resource's, else the aggregation's"):
    val rec = Europeana.records(fixture("eu-search.json"), api, licences).toOption.get.head.toOption.get
    assertEquals(
      Europeana.item(fixture("eu-record.json"), rec, licences).map(_.originalUrl),
      Right(Some("https://images.fixture.example/lion-gate.jpg"))
    )
    val text = fixture("eu-record.json").noSpaces
    val differs = RawJson
      .parse(
        text.replace(
          "\"webResourceEdmRights\":{\"def\":[\"http://creativecommons.org/licenses/by-sa/4.0/\"]}",
          "\"webResourceEdmRights\":{\"def\":[\"http://creativecommons.org/licenses/by-nc/4.0/\"]}"
        )
      )
      .toOption
      .get
    assertEquals(
      Europeana.item(differs, rec, licences).left.toOption.map(_.sentence),
      Some(
        "media-europeana (rejected): the full record's licence (CC-BY-NC-4.0) differs from the search's (CC-BY-SA-4.0)"
      )
    )
    val noResource = RawJson.parse(text.replace("\"webResourceEdmRights\"", "\"x\"")).toOption.get
    assert(Europeana.item(noResource, rec, licences).isRight, "the aggregation's edmRights answers")
    val neither =
      RawJson.parse(text.replace("\"webResourceEdmRights\"", "\"x\"").replace("\"edmRights\"", "\"y\"")).toOption.get
    assertEquals(
      Europeana.item(neither, rec, licences).left.toOption.map(_.sentence),
      Some("media-europeana did not take 9200999/fixture_mycenae_lion_gate: no licence")
    )

  // ── relevance: natural-history specimens and modern objects ─────────────────────────────────

  private val ex = Europeana.Exclusions(
    List(Europeana.Exclusions.Providers("OpenUp!")),
    List(Europeana.Exclusions.DataProviders("Natural History Museum"), Europeana.Exclusions.DataProviders("Herbarium")),
    List(Europeana.Exclusions.Types("PreservedSpecimen"), Europeana.Exclusions.Types("Preserved Specimen"))
  )

  test("Europeana: the search leaves out the excluded aggregators and dc:types; none excluded is the plain query"):
    assertEquals(
      Europeana.query(MediaPlace.Label("Orkney"), ex),
      "where:(\"Orkney\") NOT PROVIDER:(\"OpenUp!\") NOT proxy_dc_type:(\"PreservedSpecimen\" OR \"Preserved Specimen\")"
    )
    assertEquals(Europeana.query(MediaPlace.Label("Orkney")), "where:(\"Orkney\")")
    assert(
      Europeana
        .searchUrl(api, MediaPlace.Label("Orkney"), CollectionSettings.Candidates(20), ex)
        .value
        .contains(Europeana.query(MediaPlace.Label("Orkney"), ex).value.enc)
    )

  test("Europeana: a specimen record — its aggregator or its institution excluded — is withheld with the reason"):
    val rs = Europeana
      .records(fixture("eu-search-orkney.json"), api, licences, ex)
      .fold(e => fail(e.sentence), identity)
      .map(_.fold(e => fail(e.sentence), identity))
    assertEquals(
      rs.map(r => r.key -> r.withheld),
      List[(String, Option[String])](
        "854/NHMUKXZOOX1950X3X1X144" -> Some("an excluded provider (OpenUp!)"),
        "9200999/fixture_orkney_herbarium_sheet" -> Some("an excluded data provider (Fixture Herbarium Trust)"),
        "9200999/fixture_kirkwall_postcard" -> None,
        "9200999/fixture_orkney_mace_head" -> None,
        "9200999/fixture_orkney_undated" -> None
      )
    )
    assert(
      Europeana
        .records(fixture("eu-search-orkney.json"), api, licences)
        .toOption
        .get
        .forall(_.exists(_.withheld.isEmpty)),
      "nothing is withheld without exclusions"
    )

  test("Europeana: an excluded dc:type is compared by its letters, any case; the Record API refuses such a record"):
    assertEquals(
      Europeana.excludedType(
        List(Europeana.Exclusions.Types("Sculpture"), Europeana.Exclusions.Types("preserved_specimen")),
        ex
      ),
      Some("preserved_specimen")
    )
    assertEquals(Europeana.excludedType(List(Europeana.Exclusions.Types("Sculpture")), ex), None)
    val rec = Europeana.records(fixture("eu-search.json"), api, licences).toOption.get.head.toOption.get
    val specimen = RawJson
      .parse(
        fixture("eu-record.json").noSpaces.replace(
          "\"dctermsSpatial\"",
          "\"dcType\":{\"en\":[\"Preserved Specimen\"]},\"dctermsSpatial\""
        )
      )
      .toOption
      .get
    assertEquals(
      Europeana.item(specimen, rec, licences, ex).left.toOption.map(_.sentence),
      Some("media-europeana (rejected): an excluded type (Preserved Specimen)")
    )
    assert(Europeana.item(specimen, rec, licences).isRight, "no exclusions, no refusal")
