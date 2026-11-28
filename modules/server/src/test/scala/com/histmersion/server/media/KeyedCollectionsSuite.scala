package com.histmersion
package server.media
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.SearchedCollection
import com.histmersion.domain.media.imports.CollectionImport
import com.histmersion.domain.media.imports.PlaceFacts
import com.histmersion.domain.media.imports.Target

import com.histmersion.domain.media.imports.{BuiltinMedia, ImportService}
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.catalogue.Place
import com.histmersion.config.MediaConfig

import com.histmersion.pluginhost.{HttpAnswer, Registries}

import com.histmersion.domain.*
import com.histmersion.plugin.Year
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.api.media.{MediaImportReport, MediaProvider}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The two key-gated collections end to end against FAKE providers and an in-memory store: what is matched, what is
  * refused and why, that the key travels only as a header to the API's own host — never in a URL, never to an image
  * host — and that a refused key ends the batch with the setting to check. Each collection is its plugin's, built by
  * the host with a fixture key from a secret reference. The Smithsonian rows are real records of its open-data dump;
  * the Europeana answers are constructed in its documented shape (see the plugin's parser suite).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class KeyedCollectionsSuite extends munit.FunSuite:

  private val jpeg = Array[Byte](-1, -40, -1)
  private val key = "test-key-0123456789"

  final private class NoConverter extends ConverterClient:
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] = Left(
      Problem.Unclassified(Legacy.Text("not used"))
    )
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )

  private def fx(name: String) = FakeMediaHttp.fixture(name)
  private val cfg = FakeMediaHttp.cfg

  private val egypt =
    PlaceFacts(
      PlaceFacts.Id("geonames_357994"),
      PlaceFacts.Label("Egypt"),
      Set(PlaceFacts.Keys("egypt")),
      Some((DeepTime.SignedYear(-3100L), DeepTime.SignedYear(-30L))),
      Some(Target.Qid("Q79")),
      Some(Latitude(26.0)),
      Some(Longitude(30.0))
    )
  private val syria =
    PlaceFacts(
      PlaceFacts.Id("geonames_163843"),
      PlaceFacts.Label("Syria"),
      Set(PlaceFacts.Keys("syria")),
      Some((DeepTime.SignedYear(-3000L), DeepTime.SignedYear(1500L))),
      Some(Target.Qid("Q858")),
      Some(Latitude(35.0)),
      Some(Longitude(38.0))
    )
  private val mycenae = PlaceFacts(
    PlaceFacts.Id("pleiades_570491"),
    PlaceFacts.Label("Mycenae"),
    Set(PlaceFacts.Keys("mycenae")),
    Some((DeepTime.SignedYear(-1600L), DeepTime.SignedYear(-1100L))),
    Some(Target.Qid("Q132564")),
    Some(Latitude(37.73)),
    Some(Longitude(22.756))
  )
  private val greece =
    PlaceFacts(
      PlaceFacts.Id("geonames_390903"),
      PlaceFacts.Label("Greece"),
      Set(PlaceFacts.Keys("greece")),
      Some((DeepTime.SignedYear(-3000L), DeepTime.SignedYear(-100L))),
      Some(Target.Qid("Q41")),
      Some(Latitude(39.0)),
      Some(Longitude(22.0))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val facts = List(egypt, syria, mycenae, greece).map(f => Place.Id.assume(f.id.value) -> f).toMap
  private val named =
    Map(
      ImportService.PlaceKey("egypt") -> List(egypt),
      ImportService.PlaceKey("syria") -> List(syria),
      ImportService.PlaceKey("mycenae") -> List(mycenae),
      ImportService.PlaceKey("greece") -> List(greece)
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def target(f: PlaceFacts) =
    Target(Target.TargetType("place"), Target.Id(f.id.value), Target.Qid(""), Target.Label(f.label.value))

  private val DryOn = CollectionImport.DryRun.DryRun
  private val DryOff = CollectionImport.DryRun.Applied
  private val Smithsonian = BuiltinMedia.EntryName("smithsonian")
  private val Europeana = BuiltinMedia.EntryName("europeana")

  private def run(
      col: SearchedCollection,
      http: FakeMediaHttp,
      targets: List[Target],
      dryRun: CollectionImport.DryRun,
      store: CollStore = CollStore(facts, named)
  ): MediaImportReport =
    val (polite, _) =
      FakeMediaHttp.polite(http, cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)))
    CollectionImport(col, polite, cfg, NoConverter()).run(
      targets,
      dryRun,
      refresh = CollectionImport.Refresh.Reuse,
      store
    )

  /** The two key-gated plugins switched on, each with the fixture key as a secret reference. */
  private val keyed =
    """histmersion.plugins.media-smithsonian { enabled = true, api-key = "env:ZQMT_SI_KEY" }
      |histmersion.plugins.media-europeana { enabled = true, api-key = "env:ZQMT_EU_KEY" }
      |""".stripMargin
  private val env = Map("ZQMT_SI_KEY" -> key, "ZQMT_EU_KEY" -> key)
  private def collection(name: BuiltinMedia.EntryName): SearchedCollection =
    FakeMediaHttp.builtin(name, keyed, env)

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
    val r = run(collection(Smithsonian), http, List(target(egypt)), dryRun = DryOn)
    assertEquals(r.provider, MediaProvider.Smithsonian)
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(("fsg_F1909.1185", "CC0-1.0", "origin = Egypt; 1550–1196 BCE within 3100–30 BCE"))
    )
    assert(r.rejected.exists(_.key.value == "names another place on the map"), r.rejected.toString)
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
      run(collection(Smithsonian), http, List(target(egypt), target(syria)), dryRun = DryOff, store)
    // the bead to Egypt; the Raqqa tile to Syria (its origin's first part is not on the map, Syria is)
    assertEquals((r.stored, r.linked), (2, 2), r.toString)
    assertEquals(
      store.links.map(l => (l._3, l._6)).toList,
      List(
        ("geonames_357994", Some("origin = Egypt; 1550–1196 BCE within 3100–30 BCE")),
        ("geonames_163843", Some("origin = Syria; 1101–1300 CE within 3000 BCE–1500 CE"))
      )
    )
    val (_, a) = store.saved(ProviderAsset.Key("fsg_F1909.1185"))
    assertEquals((a.provider, a.license), (MediaProvider.Smithsonian, "CC0-1.0"))
    assertEquals(a.originalUrl, Some("https://ids.si.edu/ids/download?id=FS-8431_10.jpg"))
    assertEquals(
      a.credit,
      "Bead, National Museum of Asian Art, Smithsonian Institution, Gift of Charles Lang Freer, CC0"
    )
    assert(http.requests.exists(_._1 == "https://ids.si.edu/ids/download?id=FS-8431_10_screen"))
    assert(http.requests.exists(_._1 == "https://ids.si.edu/ids/download?id=FS-8431_10_thumb"))
    assertEquals(store.checks.keySet.map(_._2.value: String).toSet, Set(egypt.id.value: String, syria.id.value: String))
    noKeyInUrls(http)

  test("Smithsonian: a refused key ends the batch and names the setting; nothing is recorded as asked"):
    val gov =
      """{"error":{"code":"API_KEY_INVALID","message":"An invalid api_key was supplied. Get one at https://api.si.edu:443"}}"""
    val http = si(FakeMediaHttp.json(gov, code = 403))
    val store = CollStore(facts, named)
    val r =
      run(collection(Smithsonian), http, List(target(egypt), target(syria)), dryRun = DryOff, store)
    assert(
      r.failed.exists(
        _.value.contains("histmersion.plugins.media-smithsonian.api-key was refused by media-smithsonian (HTTP 403)")
      ),
      r.failed.toString
    )
    assert(r.failed.exists(_.value.contains("the rest of this batch was not asked")))
    assertEquals(http.requests.size, 1)
    assert(store.checks.isEmpty && store.saved.isEmpty)

  // ── Europeana ────────────────────────────────────────────────────────────────────────────────

  private val gateMedia = "https://images.fixture.example/lion-gate.jpg"

  /** Europeana's Thumbnail API v3 URL of a media file: its URL hashed with MD5, as the API documents. */
  private def thumbnail(media: String, width: Int): String =
    val md5 =
      java.security.MessageDigest.getInstance("MD5").digest(media.getBytes("UTF-8")).map(b => f"${b & 0xff}%02x")
    s"https://api.europeana.eu/thumbnail/v3/$width/${md5.mkString}.jpg"

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
    val r = run(collection(Europeana), http, List(target(mycenae)), dryRun = DryOn)
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
      r.rejected.map(_.key.value).sorted,
      List(
        "its dcterms:spatial names another place on the map",
        "unknown licence 'http://rightsstatements.org/vocab/NoC-NC/1.0/'"
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
    val r = run(collection(Europeana), http, List(target(mycenae)), dryRun = DryOff, store)
    assertEquals((r.stored, r.linked), (1, 1), r.toString)
    val (_, a) = store.saved(ProviderAsset.Key("9200999/fixture_mycenae_lion_gate"))
    assertEquals((a.provider, a.license), (MediaProvider.Europeana, "CC-BY-SA-4.0"))
    assertEquals(a.author, Some("Fixture Photographer"))
    assertEquals(a.originalUrl, Some(gateMedia))
    assertEquals(a.sourceUrl, "https://www.europeana.eu/item/9200999/fixture_mycenae_lion_gate")
    val thumbs = http.requests.filter(_._1.contains("/thumbnail/v3/"))
    assertEquals(
      thumbs.map(_._1).toList,
      List(
        thumbnail(gateMedia, 400),
        thumbnail(gateMedia, 200)
      )
    )
    assert(thumbs.forall(!_._2.contains("X-Api-Key")), "the keyless Thumbnail API gets no key")
    noKeyInUrls(http)

  test("Europeana: a full record whose licence differs from the search's is not stored (a line in the report)"):
    val store = CollStore(facts, named)
    val r = run(
      collection(Europeana),
      eu(
        _.replace(
          "\"webResourceEdmRights\": {\"def\": [\"http://creativecommons.org/licenses/by-sa/4.0/\"]}",
          "\"webResourceEdmRights\": {\"def\": [\"http://rightsstatements.org/vocab/InC/1.0/\"]}"
        )
      ),
      List(target(mycenae)),
      dryRun = DryOff,
      store
    )
    assertEquals(r.stored, 0)
    assert(r.failed.exists(_.value.contains("differs from the search's (CC-BY-SA-4.0)")), r.failed.toString)

  // ── relevance: the default exclusions and max-year ──────────────────────────────────────────

  private val orkney = PlaceFacts(
    PlaceFacts.Id("ne_physical_1159102959"),
    PlaceFacts.Label("Orkney Islands"),
    Set(PlaceFacts.Keys("orkney islands"), PlaceFacts.Keys("orkney")),
    Some((DeepTime.SignedYear(-7000L), DeepTime.SignedYear(2100L))),
    None,
    Some(Latitude(59.0)),
    Some(Longitude(-3.0))
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val orkneyStore =
    CollStore(Map(Place.Id.assume(orkney.id.value) -> orkney), Map(ImportService.PlaceKey("orkney") -> List(orkney)))

  test("Europeana defaults: max-year 1500 and the natural-history exclusions are in the search and the reasons"):
    assertEquals(cfg.europeana.maxYear, 1500)
    assertEquals(
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("europeana"), keyed, env).latestYear,
      Some(Year(1500)),
      "the plugin reads the same"
    )
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://api.europeana.eu/record/v2/search.json?") then
        FakeMediaHttp.json(fx("eu-search-orkney.json"))
      else Left(s"unexpected $url")
    }
    val r = run(collection(Europeana), http, List(target(orkney)), dryRun = DryOn, orkneyStore)
    assertEquals(
      r.samples.map(s => (s.key, s.basis)),
      List(("9200999/fixture_orkney_mace_head", "dcterms:spatial = Orkney; 3000–2500 BCE within 7000 BCE–1500 CE"))
    )
    assertEquals(
      r.rejected.map(c => c.key -> c.n).toMap,
      Map(
        "europeana (rejected): an excluded provider (OpenUp!)" -> 1,
        "europeana (rejected): an excluded data provider (Fixture Herbarium Trust)" -> 1,
        "made after 1500 CE" -> 1,
        "no dates on the record" -> 1
      )
    )
    val search = java.net.URLDecoder.decode(http.requests.head._1, "UTF-8")
    assert(search.contains("NOT PROVIDER:(\"OpenUp!\")"), search)
    assert(search.contains("NOT proxy_dc_type:(\"PreservedSpecimen\""), search)

  test("Smithsonian defaults: max-year 1500, the natural-history units left out of the search and refused"):
    assertEquals(cfg.smithsonian.maxYear, 1500)
    assertEquals(
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("smithsonian"), keyed, env).latestYear,
      Some(Year(1500)),
      "the plugin reads the same"
    )
    val text = fx("si-search.json")
    // the first row as a specimen of the invertebrate zoology department; the second dated after 1500
    val rows = RawJson.parse(text).toOption.get.cursor.downField("response").downField("rows")
    val bead = rows.downArray.focus.get.noSpaces.replace("\"FSG\"", "\"NMNHINV\"")
    val tile = rows.downN(1).focus.get.noSpaces.replace("late 12th-early 13th century", "1890")
    val third = rows.downN(2).focus.get.noSpaces
    val answer = s"""{"status":200,"responseCode":1,"response":{"rows":[$bead,$tile,$third],"rowCount":3}}"""
    val http = si(FakeMediaHttp.json(answer))
    val r = run(collection(Smithsonian), http, List(target(egypt), target(syria)), dryRun = DryOn)
    assertEquals(r.samples, Nil)
    assert(r.rejected.exists(c => c.key.value.endsWith("an excluded unit (NMNHINV)")), r.rejected.toString)
    assert(r.rejected.exists(c => c.key.value == "made after 1500 CE"), r.rejected.toString)
    val search = java.net.URLDecoder.decode(http.requests.head._1, "UTF-8")
    assert(search.contains("AND NOT unit_code:(NMNHBIRDS OR "), search)
    assert(!search.contains("NMNHANTHRO"), "Anthropology's objects are kept")
