package com.histmersion.server.media

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaStore
import com.histmersion.domain.media.imports.CollectionImport
import com.histmersion.domain.media.imports.OwnImport
import com.histmersion.domain.media.imports.PlaceFacts
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.media.assets.Known
import com.histmersion.domain.media.assets.Rights

import com.histmersion.domain.catalogue.places.ExternalIds

import com.histmersion.pluginhost.HttpAnswer

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{AssetKind, MediaCount, MediaImportReport, MediaProvider}
import com.histmersion.api.ocr.ConvertedDoc

/** The store as a few maps, with the gazetteer facts and ids the keyless portals read. */
final class PortalStore(
    facts: Map[String, PlaceFacts],
    named: Map[String, List[PlaceFacts]],
    byId: Map[(String, String), List[PlaceFacts]] = Map.empty,
    pleiades: Map[String, List[String]] = Map.empty
) extends MediaStore:
  val saved = scala.collection.mutable.LinkedHashMap.empty[String, (String, ProviderAsset)]
  val links = scala.collection.mutable.ListBuffer
    .empty[(String, String, String, String, Option[String], Option[String], Option[String])]
  val checks = scala.collection.mutable.LinkedHashMap.empty[(String, String), Int]
  def known(provider: String, keys: List[String]) =
    saved.view.filterKeys(keys.toSet).map((k, v) => k -> Known(v._1, Some(v._2.license))).toMap
  def save(a: ProviderAsset, first: (String, String)) =
    val existing = saved.get(a.key).map(_._1)
    val uid = existing.getOrElse(f"00000000-0000-0000-0000-${saved.size + 1}%012d")
    saved(a.key) = (uid, a)
    (uid, existing.isEmpty)
  def refreshRights(uid: String, r: Rights) = ()
  def link(uid: String, tt: String, ti: String, role: String, p: Option[String], q: Option[String], rank: Int) =
    linkBasis(uid, tt, ti, role, p, q, rank, None)
  override def linkBasis(
      uid: String,
      tt: String,
      ti: String,
      role: String,
      p: Option[String],
      q: Option[String],
      rank: Int,
      basis: Option[String]
  ) =
    links += ((uid, ti, role, s"$rank", p, q, basis)); true
  def commonsCategory(placeId: String, category: String, qid: String) = false
  def checked(provider: String, tt: String, ti: String, found: Int, qid: Option[String]) = checks((provider, ti)) =
    found
  override def placeFacts(ids: List[String]) = facts.view.filterKeys(ids.toSet).toMap
  override def mapPlacesNamed(keys: List[String]) = named.view.filterKeys(keys.toSet).toMap
  override def placeExternalIds(ids: List[String]) =
    ids.flatMap(id => ExternalIds.ofPlaceId(id).map(x => id -> Set(x))).toMap
  override def mapPlacesWithIds(ids: List[(String, String)]) = byId.view.filterKeys(ids.toSet).toMap
  override def pleiadesIds(ids: List[String]) = pleiades.view.filterKeys(ids.toSet).toMap

/** The keyless portals (#1446) end to end against FAKE providers (trimmed real answers of 2026-10-17) and an in-memory
  * store, each its plugin's collection as the host builds it: Open Context's records (the host's collection import)
  * judged by their own licence, tied by their own place id or their site's name and their dates, completed from the
  * media item only when stored; the Commons files a Pleiades place record links (the host's import of a collection of
  * its targets' own pictures), judged by their licence on Commons, the rest counted — and no request leaving this
  * machine.
  */
class PortalsSuite extends munit.FunSuite:

  private val jpeg = Array[Byte](-1, -40, -1)
  private def fx(name: String) = FakeMediaHttp.fixture(name)
  private val cfg = FakeMediaHttp.cfg

  final private class ThumbConverter extends ConverterClient:
    val calls = scala.collection.mutable.ListBuffer.empty[String]
    def convert(
        file: com.histmersion.util.storage.Blob,
        filename: String,
        quality: Boolean
    ): Either[String, ConvertedDoc] = Left(
      "not used"
    )
    def health: Either[String, String] = Right("ok")
    override def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
      calls += filename
      Right(ConverterClient.Thumb(jpeg, "image/jpeg", 330, 250))

  // ── Open Context ─────────────────────────────────────────────────────────────────────────────

  private val petra =
    PlaceFacts("pleiades_697725", "Petra", Set("petra"), Some((-300L, 100L)), Some("Q5788"), Some(30.33), Some(35.44))
  private val jordan =
    PlaceFacts("geonames_248816", "Jordan", Set("jordan"), Some((-3000L, 2000L)), Some("Q810"), Some(31.0), Some(36.0))
  private val facts = List(petra, jordan).map(f => f.id -> f).toMap
  private val named = Map("jordan" -> List(jordan), "petra" -> List(petra))
  private val byId = Map(("pleiades", "697725") -> List(petra))
  private def target(f: PlaceFacts) = Target("place", f.id, "", f.label)

  private val Bowls7 = "f589a368-62ed-43df-5738-4c84d2f5f765"
  private val Bowls6 = "d9feb4e7-3ec6-444f-6a85-2b6873c8822a"

  private def openContext(item6: String => String = identity): FakeMediaHttp = FakeMediaHttp { url =>
    if url.startsWith("https://opencontext.org/query/.json?") then FakeMediaHttp.json(fx("oc-search.json"))
    else if url == s"https://opencontext.org/media/$Bowls7.json" then FakeMediaHttp.json(fx("oc-media.json"))
    else if url == s"https://opencontext.org/media/$Bowls6.json" then
      FakeMediaHttp.json(item6(fx("oc-media.json").replace(Bowls7, Bowls6).replace("bowls-7-30-38", "bowls-6-24-29")))
    else if url.startsWith("https://storage.googleapis.com/") then
      FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  private def runOc(
      http: FakeMediaHttp,
      t: PlaceFacts,
      dryRun: Boolean,
      store: PortalStore,
      conv: ThumbConverter = ThumbConverter()
  ): MediaImportReport =
    val col = FakeMediaHttp.builtin("opencontext")
    val (polite, _) = FakeMediaHttp.polite(http, cfg.modify(_.requestIntervalMs).setTo(col.conf.requestIntervalMs))
    CollectionImport(col, polite, cfg, conv).run(List(target(t)), dryRun, refresh = false, store)

  test(
    "Open Context dry run: tied by the record's own Pleiades id and its dates; human remains and other dates refused; one request"
  ):
    val http = openContext()
    val store = PortalStore(facts, named, byId)
    val r = runOc(http, petra, dryRun = true, store)
    assertEquals(r.provider, MediaProvider.OpenContext)
    assertEquals((r.targets, r.items, r.statements, r.files), (1, 4, 2, 2))
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(
        (Bowls7, "CC-BY-4.0", "pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE"),
        (Bowls6, "CC-BY-4.0", "pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE")
      )
    )
    assertEquals(
      r.rejected.map(_.key).sorted,
      List("human remains (Open Context flags them; not shown)", "its dates do not overlap the place's")
    )
    assertEquals(r.byLicence, List(MediaCount("CC-BY-4.0", 4)))
    assert(store.saved.isEmpty && store.links.isEmpty && store.checks.isEmpty)
    assertEquals(http.requests.size, 1, "one search answers the records; no item is read for a dry run")
    assert(http.requests.forall(_._2.get("User-Agent").exists(ua => !ua.contains("@"))), "no personal data")

  test(
    "Open Context, real Domuztepe records (#2571): tied by the site its context names, though a place 'I' and the province are on the map"
  ):
    // the map as it was when the full run stored 0: a place called "I" (the records' operation "I"),
    // Kahramanmaraş carrying the province's GeoNames id the records give
    val dom = PlaceFacts(
      "wikidata_q4165990",
      "Domuztepe",
      Set("domuztepe"),
      Some((-6500L, -5400L)),
      Some("Q4165990"),
      Some(37.33),
      Some(36.87)
    )
    val placeI = PlaceFacts("wikidata_q103776406", "I", Set("i"), None, Some("Q103776406"), Some(50.0), Some(10.0))
    val kahr =
      PlaceFacts("ne_city_1159127453", "Kahramanmaraş", Set("kahramanmaras"), None, None, Some(37.58), Some(36.93))
    val turkey = PlaceFacts("ne_country_tur", "Turkey", Set("turkey"), None, Some("Q43"), Some(39.0), Some(35.0))
    val store = PortalStore(
      Map(dom.id -> dom),
      Map("domuztepe" -> List(dom), "i" -> List(placeI), "turkey" -> List(turkey), "kahramanmaras" -> List(kahr)),
      Map(("geonames", "310858") -> List(kahr))
    )
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://opencontext.org/query/.json?") then FakeMediaHttp.json(fx("oc-search-domuztepe.json"))
      else Left(s"unexpected $url")
    }
    val r = runOc(http, dom, dryRun = true, store)
    assertEquals(
      r.samples.map(_.basis).distinct,
      List("context = Domuztepe; 6500–5500 BCE within 6500–5400 BCE"),
      r.rejected.toString
    )
    assertEquals(r.samples.size, 3)
    assertEquals(r.rejected.map(_.key), List("human remains (Open Context flags them; not shown)"))

  test("Open Context: a record from Petra is not filed under the country its context path names"):
    val r = runOc(openContext(), jordan, dryRun = true, PortalStore(facts, named, byId))
    assert(r.samples.isEmpty, r.samples.toString)
    assert(
      r.rejected.exists(c => c.key == "its place id names another place on the map" && c.n == 3),
      r.rejected.toString
    )

  test(
    "Open Context real run: the item read for each stored record — its preview stored, its thumbnail the card's, its licence re-read"
  ):
    val http = openContext()
    val store = PortalStore(facts, named, byId)
    val conv = ThumbConverter()
    val r = runOc(http, petra, dryRun = false, store, conv)
    assertEquals((r.stored, r.linked), (2, 2), r.failed.toString)
    val (uid, a) = store.saved(Bowls7)
    assertEquals(a.provider, MediaProvider.OpenContext)
    assertEquals((a.license, a.licenseUrl), ("CC-BY-4.0", Some("https://creativecommons.org/licenses/by/4.0")))
    assertEquals(a.author, Some("Martha Sharp Joukowsky"))
    assert(
      a.credit.endsWith(
        "In Petra Great Temple Excavations. Open Context. https://n2t.net/ark:/28722/k2xk8d17p. CC BY 4.0"
      ),
      a.credit
    )
    assertEquals(a.sourceUrl, s"https://opencontext.org/media/$Bowls7")
    assertEquals(
      a.originalUrl,
      Some("https://archive.org/download/opencontext-10-bowls-7-30-38jpg/10-bowls-7-30-38jpg.jpg")
    )
    assert(new String(a.bytes, "UTF-8").contains("ia-previews"), "the preview rendition, not the original")
    assert(a.thumb.exists(t => new String(t, "UTF-8").contains("ia-thumbnails")))
    assert(conv.calls.isEmpty, "Open Context's own thumbnail is used")
    assertEquals(a.kind, Some(AssetKind.Photo))
    assertEquals(
      store.links.head,
      (
        uid,
        "pleiades_697725",
        MediaRole.Object,
        "0",
        None,
        None,
        Some("pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE")
      )
    )
    assertEquals(store.checks.toMap, Map((MediaProvider.OpenContext, "pleiades_697725") -> 2))
    assert(!http.requests.exists(_._1.contains("archive.org")), "the original is recorded, never downloaded")
    assertEquals(http.requests.size, 7, "a search, two items, two previews, two thumbnails")

  test("Open Context: a media item whose licence differs from the search's is not stored (a line, not the end)"):
    val store = PortalStore(facts, named, byId)
    val r = runOc(openContext(_.replace("licenses/by/4.0", "licenses/by-nc/4.0")), petra, dryRun = false, store)
    assertEquals(r.stored, 1)
    assert(!store.saved.contains(Bowls6))
    assert(r.failed.exists(f => f.contains(Bowls6) && f.contains("differs")), r.failed.toString)

  // ── Pleiades ─────────────────────────────────────────────────────────────────────────────────

  private val zygouries = Target("place", "pleiades_757208100", "", "Zygouries")

  private def param(url: String, name: String): Option[String] =
    url.split("[?&]").toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name => java.net.URLDecoder.decode(v, "UTF-8")
    }

  /** Commons' imageinfo: every title asked is a CC BY-SA 4.0 file with an author. */
  private def info(titles: List[String], width: String): String =
    val pages = titles.zipWithIndex.map { (t, i) =>
      s"""{"pageid":${i + 1},"ns":6,"title":${io.circe.Json
          .fromString(t)
          .noSpaces},"imageinfo":[{"size":100,"width":10,"height":10,
         "thumburl":"https://upload.example/$i-$width.jpg","thumbwidth":$width,"thumbheight":$width,
         "url":"https://upload.example/$i.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/F$i","mime":"image/jpeg",
         "extmetadata":{"Artist":{"value":"Zqmp"},"LicenseShortName":{"value":"CC BY-SA 4.0"},"License":{"value":"cc-by-sa-4.0"}}}]}"""
    }
    s"""{"query":{"pages":[${pages.mkString(",")}]}}"""

  private def pleiades(
      place: Either[String, HttpAnswer] = FakeMediaHttp.json(fx("pleiades-place.json"))
  ): FakeMediaHttp =
    FakeMediaHttp { url =>
      if url == "https://pleiades.stoa.org/places/757208100/json" then place
      else if url.startsWith("https://commons.wikimedia.org/w/api.php?") && url.contains("prop=imageinfo") then
        FakeMediaHttp.json(
          info(param(url, "titles").toList.flatMap(_.split('|')), param(url, "iiurlwidth").getOrElse("0"))
        )
      else if url.startsWith("https://upload.example/") then
        FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
      else Left(s"unexpected $url")
    }

  private def runPl(http: FakeMediaHttp, dryRun: Boolean, store: PortalStore): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    OwnImport("pleiades", FakeMediaHttp.collection("pleiades"), polite, cfg)
      .run(List(zygouries), dryRun, refresh = false, store)

  private def plStore() = PortalStore(Map.empty, Map.empty, pleiades = Map("pleiades_757208100" -> List("757208100")))

  test(
    "Pleiades dry run: the Commons file the record links, licence read from Commons; Flickr, a category, another site counted"
  ):
    val http = pleiades()
    val store = plStore()
    val r = runPl(http, dryRun = true, store)
    assertEquals(r.provider, MediaProvider.Pleiades)
    assertEquals((r.targets, r.items, r.statements, r.files), (1, 1, 1, 1))
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(
        (
          "File:Zygouries-General plan of the excavations.jpg",
          "CC-BY-SA-4.0",
          "Pleiades place 757208100 links it (citesAsRelated)"
        )
      )
    )
    assertEquals(
      r.rejected.map(c => (c.key, c.n)).sorted,
      List(
        ("a Commons category, not a file", 1),
        ("a picture on elexikon.ch (no documented keyless licence API)", 1),
        ("on Flickr (its API needs a key)", 1)
      )
    )
    assert(store.saved.isEmpty && store.checks.isEmpty)
    assertEquals(
      http.requests.toList.map(_._1.takeWhile(_ != '?')),
      List("https://pleiades.stoa.org/places/757208100/json", "https://commons.wikimedia.org/w/api.php"),
      "no Flickr, no other site asked"
    )

  test(
    "Pleiades real run: a Commons picture linked as the place's image with the record as its basis; checked as pleiades"
  ):
    val store = plStore()
    val r = runPl(pleiades(), dryRun = false, store)
    assertEquals((r.stored, r.linked), (1, 1), r.failed.toString)
    val (uid, a) = store.saved("File:Zygouries-General plan of the excavations.jpg")
    assertEquals((a.provider, a.license, a.author), (MediaProvider.Commons, "CC-BY-SA-4.0", Some("Zqmp")))
    assertEquals(
      store.links.toList,
      List(
        (
          uid,
          "pleiades_757208100",
          MediaRole.Image,
          "0",
          None,
          None,
          Some("Pleiades place 757208100 links it (citesAsRelated)")
        )
      )
    )
    assertEquals(store.checks.toMap, Map((MediaProvider.Pleiades, "pleiades_757208100") -> 1))

  test("Pleiades: a record that does not answer leaves the place not asked (the next run asks again)"):
    val store = plStore()
    val c = cfg.modify(_.maxRetries).setTo(0)
    val (polite, _) = FakeMediaHttp.polite(pleiades(Right(HttpAnswer(503, Map.empty, Array.emptyByteArray))), c)
    val r = OwnImport("pleiades", FakeMediaHttp.collection("pleiades"), polite, c)
      .run(List(zygouries), dryRun = false, refresh = false, store)
    assert(r.failed.exists(_.contains("Pleiades place 757208100")), r.failed.toString)
    assert(store.checks.isEmpty)
