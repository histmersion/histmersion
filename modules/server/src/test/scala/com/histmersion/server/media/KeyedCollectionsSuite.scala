package com.histmersion.server.media

import com.histmersion.pluginhost.HttpAnswer

import com.histmersion.api.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.{PlaceFacts, Target}
import com.histmersion.server.ConverterClient

/** The two key-gated collections end to end against FAKE providers and an in-memory store: what is matched, what is
  * refused and why, that the key travels only as a header to the API's own host — never in a URL, never to an image
  * host — and that a refused key ends the batch with the setting to check. The Smithsonian rows are real records of its
  * open-data dump; the Europeana answers are constructed in its documented shape (see the core suite).
  */
class KeyedCollectionsSuite extends munit.FunSuite:

  private val jpeg = Array[Byte](-1, -40, -1)
  private val key = "test-key-0123456789"

  final private class NoConverter extends ConverterClient:
    def convert(file: java.nio.file.Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] = Left(
      "not used"
    )
    def health: Either[String, String] = Right("ok")

  private def fx(name: String) = FakeMediaHttp.fixture(name)
  private val cfg = FakeMediaHttp.cfg

  private val egypt =
    PlaceFacts("geonames_357994", "Egypt", Set("egypt"), Some((-3100L, -30L)), Some("Q79"), Some(26.0), Some(30.0))
  private val syria =
    PlaceFacts("geonames_163843", "Syria", Set("syria"), Some((-3000L, 1500L)), Some("Q858"), Some(35.0), Some(38.0))
  private val mycenae = PlaceFacts(
    "pleiades_570491",
    "Mycenae",
    Set("mycenae"),
    Some((-1600L, -1100L)),
    Some("Q132564"),
    Some(37.73),
    Some(22.756)
  )
  private val greece =
    PlaceFacts("geonames_390903", "Greece", Set("greece"), Some((-3000L, -100L)), Some("Q41"), Some(39.0), Some(22.0))
  private val facts = List(egypt, syria, mycenae, greece).map(f => f.id -> f).toMap
  private val named =
    Map("egypt" -> List(egypt), "syria" -> List(syria), "mycenae" -> List(mycenae), "greece" -> List(greece))
  private def target(f: PlaceFacts) = Target("place", f.id, "", f.label)

  private def run(
      col: Collection,
      http: FakeMediaHttp,
      targets: List[Target],
      dryRun: Boolean,
      store: CollStore = CollStore(facts, named)
  ): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg.copy(requestIntervalMs = 0))
    CollectionImport(col, polite, cfg, NoConverter()).run(targets, dryRun, refresh = false, store)

  private def noKeyInUrls(http: FakeMediaHttp) =
    assert(http.requests.forall(!_._1.contains(key)), "the key is never in a URL")

  // ── Smithsonian ──────────────────────────────────────────────────────────────────────────────

  private def si(answer: => Either[String, HttpAnswer] = FakeMediaHttp.json(fx("si-search.json"))) =
    FakeMediaHttp { url =>
      if url.startsWith("https://api.si.edu/openaccess/api/v1.0/search?") then answer
      else if url.startsWith("https://ids.si.edu/") then
        FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
      else Left(s"unexpected $url")
    }

  test(
    "Smithsonian dry run: the CC0 bead matched to Egypt by its origin and dates; the key only as a header to api.si.edu"
  ):
    val http = si()
    val r = run(SmithsonianCollection(cfg.smithsonian, key), http, List(target(egypt)), dryRun = true)
    assertEquals(r.provider, MediaProvider.Smithsonian)
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(("fsg_F1909.1185", "CC0-1.0", "origin = Egypt; 1550–1196 BCE within 3100–30 BCE"))
    )
    assert(r.rejected.exists(_.key == "names another place on the map"), r.rejected.toString)
    noKeyInUrls(http)
    val (apiCalls, others) = http.requests.partition(_._1.startsWith("https://api.si.edu/"))
    assertEquals(apiCalls.size, 1)
    assert(apiCalls.forall(_._2.get("X-Api-Key").contains(key)))
    assert(others.forall(!_._2.contains("X-Api-Key")))

  test(
    "Smithsonian real run: the screen image stored with CC0, its own thumbnail and the high-resolution original recorded"
  ):
    val store = CollStore(facts, named)
    val http = si()
    val r =
      run(SmithsonianCollection(cfg.smithsonian, key), http, List(target(egypt), target(syria)), dryRun = false, store)
    // the bead to Egypt; the Raqqa tile to Syria (its origin's first part is not on the map, Syria is)
    assertEquals((r.stored, r.linked), (2, 2), r.toString)
    assertEquals(
      store.links.map(l => (l._3, l._6)).toList,
      List(
        ("geonames_357994", Some("origin = Egypt; 1550–1196 BCE within 3100–30 BCE")),
        ("geonames_163843", Some("origin = Syria; 1101–1300 CE within 3000 BCE–1500 CE"))
      )
    )
    val (_, a) = store.saved("fsg_F1909.1185")
    assertEquals((a.provider, a.license), (MediaProvider.Smithsonian, "CC0-1.0"))
    assertEquals(a.originalUrl, Some("https://ids.si.edu/ids/download?id=FS-8431_10.jpg"))
    assertEquals(
      a.credit,
      "Bead, National Museum of Asian Art, Smithsonian Institution, Gift of Charles Lang Freer, CC0"
    )
    assert(http.requests.exists(_._1 == "https://ids.si.edu/ids/download?id=FS-8431_10_screen"))
    assert(http.requests.exists(_._1 == "https://ids.si.edu/ids/download?id=FS-8431_10_thumb"))
    assertEquals(store.checks.keySet.map(_._2), Set(egypt.id, syria.id))
    noKeyInUrls(http)

  test("Smithsonian: a refused key ends the batch and names the setting; nothing is recorded as asked"):
    val gov =
      """{"error":{"code":"API_KEY_INVALID","message":"An invalid api_key was supplied. Get one at https://api.si.edu:443"}}"""
    val http = si(FakeMediaHttp.json(gov, code = 403))
    val store = CollStore(facts, named)
    val r =
      run(SmithsonianCollection(cfg.smithsonian, key), http, List(target(egypt), target(syria)), dryRun = false, store)
    assert(
      r.failed.exists(_.contains("the API key was refused (check histmersion.plugins.media-smithsonian.api-key)")),
      r.failed.toString
    )
    assert(r.failed.exists(_.contains("the rest of this batch was not asked")))
    assertEquals(http.requests.size, 1)
    assert(store.checks.isEmpty && store.saved.isEmpty)

  // ── Europeana ────────────────────────────────────────────────────────────────────────────────

  private val gateMedia = "https://images.fixture.example/lion-gate.jpg"

  private def eu(record: String => String = identity) = FakeMediaHttp { url =>
    if url.startsWith("https://api.europeana.eu/record/v2/search.json?") then FakeMediaHttp.json(fx("eu-search.json"))
    else if url == "https://api.europeana.eu/record/v2/9200999/fixture_mycenae_lion_gate.json" then
      FakeMediaHttp.json(record(fx("eu-record.json")))
    else if url.startsWith("https://api.europeana.eu/thumbnail/v3/") then
      FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  test("Europeana dry run: matched by its own dcterms:spatial; a coarser place and a non-CC statement refused"):
    val http = eu()
    val r = run(EuropeanaCollection(cfg.europeana, key), http, List(target(mycenae)), dryRun = true)
    assertEquals(r.provider, MediaProvider.Europeana)
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(
        (
          "9200999/fixture_mycenae_lion_gate",
          "CC-BY-SA-4.0",
          "dcterms:spatial = Mycenae; 1250–1250 BCE within 1600–1100 BCE"
        )
      )
    )
    assertEquals(
      r.rejected.map(_.key).sorted,
      List(
        "its dcterms:spatial names another place on the map",
        "not an open licence (http://rightsstatements.org/vocab/NoC-NC/1.0/)"
      )
    )
    assertEquals(http.requests.size, 1, "a dry run never reads the Record API")
    assert(http.requests.forall(_._2.get("X-Api-Key").contains(key)))
    noKeyInUrls(http)

  test(
    "Europeana real run: the Record API reads the licence again; the 400 px rendition stored, no key to the thumbnail host"
  ):
    val store = CollStore(facts, named)
    val http = eu()
    val r = run(EuropeanaCollection(cfg.europeana, key), http, List(target(mycenae)), dryRun = false, store)
    assertEquals((r.stored, r.linked), (1, 1), r.toString)
    val (_, a) = store.saved("9200999/fixture_mycenae_lion_gate")
    assertEquals((a.provider, a.license), (MediaProvider.Europeana, "CC-BY-SA-4.0"))
    assertEquals(a.author, Some("Fixture Photographer"))
    assertEquals(a.originalUrl, Some(gateMedia))
    assertEquals(a.sourceUrl, "https://www.europeana.eu/item/9200999/fixture_mycenae_lion_gate")
    val thumbs = http.requests.filter(_._1.contains("/thumbnail/v3/"))
    assertEquals(
      thumbs.map(_._1).toList,
      List(
        Europeana.thumbnailUrl("https://api.europeana.eu", gateMedia, 400),
        Europeana.thumbnailUrl("https://api.europeana.eu", gateMedia, 200)
      )
    )
    assert(thumbs.forall(!_._2.contains("X-Api-Key")), "the keyless Thumbnail API gets no key")
    noKeyInUrls(http)

  test("Europeana: a full record whose licence differs from the search's is not stored (a line in the report)"):
    val store = CollStore(facts, named)
    val r = run(
      EuropeanaCollection(cfg.europeana, key),
      eu(
        _.replace(
          "\"webResourceEdmRights\": {\"def\": [\"http://creativecommons.org/licenses/by-sa/4.0/\"]}",
          "\"webResourceEdmRights\": {\"def\": [\"http://rightsstatements.org/vocab/InC/1.0/\"]}"
        )
      ),
      List(target(mycenae)),
      dryRun = false,
      store
    )
    assertEquals(r.stored, 0)
    assert(r.failed.exists(_.contains("differs from the search's (CC-BY-SA-4.0)")), r.failed.toString)

  // ── relevance: the default exclusions and max-year ──────────────────────────────────────────

  private val orkney = PlaceFacts(
    "ne_physical_1159102959",
    "Orkney Islands",
    Set("orkney islands", "orkney"),
    Some((-7000L, 2100L)),
    None,
    Some(59.0),
    Some(-3.0)
  )
  private val orkneyStore = CollStore(Map(orkney.id -> orkney), Map("orkney" -> List(orkney)))

  test("Europeana defaults: max-year 1500 and the natural-history exclusions are in the search and the reasons"):
    assertEquals(cfg.europeana.maxYear, 1500)
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://api.europeana.eu/record/v2/search.json?") then
        FakeMediaHttp.json(fx("eu-search-orkney.json"))
      else Left(s"unexpected $url")
    }
    val r = run(EuropeanaCollection(cfg.europeana, key), http, List(target(orkney)), dryRun = true, orkneyStore)
    assertEquals(
      r.samples.map(s => (s.key, s.basis)),
      List(("9200999/fixture_orkney_mace_head", "dcterms:spatial = Orkney; 3000–2500 BCE within 7000 BCE–1500 CE"))
    )
    assertEquals(
      r.rejected.map(c => c.key -> c.n).toMap,
      Map(
        "an excluded provider (OpenUp!)" -> 1,
        "an excluded data provider (Fixture Herbarium Trust)" -> 1,
        "made after 1500 CE" -> 1,
        "no dates on the record" -> 1
      )
    )
    val search = java.net.URLDecoder.decode(http.requests.head._1, "UTF-8")
    assert(search.contains("NOT PROVIDER:(\"OpenUp!\")"), search)
    assert(search.contains("NOT proxy_dc_type:(\"PreservedSpecimen\""), search)

  test("Smithsonian defaults: max-year 1500, the natural-history units left out of the search and refused"):
    assertEquals(cfg.smithsonian.maxYear, 1500)
    val text = fx("si-search.json")
    // the first row as a specimen of the invertebrate zoology department; the second dated after 1500
    val rows = io.circe.parser.parse(text).toOption.get.hcursor.downField("response").downField("rows")
    val bead = rows.downArray.focus.get.noSpaces.replace("\"FSG\"", "\"NMNHINV\"")
    val tile = rows.downArray.right.focus.get.noSpaces.replace("late 12th-early 13th century", "1890")
    val third = rows.downArray.right.right.focus.get.noSpaces
    val answer = s"""{"status":200,"responseCode":1,"response":{"rows":[$bead,$tile,$third],"rowCount":3}}"""
    val http = si(FakeMediaHttp.json(answer))
    val r = run(SmithsonianCollection(cfg.smithsonian, key), http, List(target(egypt), target(syria)), dryRun = true)
    assertEquals(r.samples, Nil)
    assert(r.rejected.exists(c => c.key == "an excluded unit (NMNHINV)"), r.rejected.toString)
    assert(r.rejected.exists(c => c.key == "made after 1500 CE"), r.rejected.toString)
    val search = java.net.URLDecoder.decode(http.requests.head._1, "UTF-8")
    assert(search.contains("AND NOT unit_code:(NMNHBIRDS OR "), search)
    assert(!search.contains("NMNHANTHRO"), "Anthropology's objects are kept")
