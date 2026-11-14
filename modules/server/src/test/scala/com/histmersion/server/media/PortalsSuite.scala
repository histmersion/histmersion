package com.histmersion
package server.media
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaStore
import com.histmersion.domain.media.imports.CollectionImport
import com.histmersion.domain.media.imports.OwnImport
import com.histmersion.domain.media.imports.PlaceFacts
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.imports.{BuiltinMedia, ImportService, MediaImporter}
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.media.assets.Known
import com.histmersion.domain.media.assets.Rights

import com.histmersion.domain.catalogue.places.ExternalIds

import com.histmersion.pluginhost.{HttpAnswer, Registries}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{AssetKind, MediaCount, MediaImportReport, MediaProvider}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob
import com.histmersion.util.ocr.Pixels
import com.histmersion.domain.DeepTime

/** The store as a few maps, with the gazetteer facts and ids the keyless portals read. */
final class PortalStore(
    facts: Map[Place.Id, PlaceFacts],
    named: Map[ImportService.PlaceKey, List[PlaceFacts]],
    byId: Map[(ExternalIds.Scheme, ExternalIds.ExternalId), List[PlaceFacts]] = Map.empty,
    pleiades: Map[Place.Id, List[ExternalIds.ExternalId]] = Map.empty
) extends MediaStore:
  val saved = scala.collection.mutable.LinkedHashMap.empty[ProviderAsset.Key, (AssetService.AssetUid, ProviderAsset)]
  val links = scala.collection.mutable.ListBuffer
    .empty[
      (
          AssetService.AssetUid,
          ImportService.TargetId,
          AssetService.LinkRole,
          String,
          Option[AssetService.LinkProperty],
          Option[AssetService.LinkItem],
          Option[AssetService.LinkBasis]
      )
    ]
  val checks = scala.collection.mutable.LinkedHashMap
    .empty[(ImportService.Provider, ImportService.TargetId), ImportService.FoundCount]
  def known(provider: ImportService.Provider, keys: List[AssetService.ProviderKey]) =
    val wanted = keys.map(_.value).toSet
    saved.collect {
      case (k, (uid, a)) if wanted(k.value) =>
        AssetService.ProviderKey(k.value) -> Known(Known.Uid(uid.value), Some(Known.License(a.license.value)))
    }.toMap
  def save(a: ProviderAsset, first: (ImportService.TargetType, ImportService.TargetId)) =
    val existing = saved.get(a.key).map(_._1)
    val uid = existing.getOrElse(AssetService.AssetUid(f"00000000-0000-0000-0000-${saved.size + 1}%012d"))
    saved(a.key) = (uid, a)
    (uid, existing.isEmpty)
  def refreshRights(uid: AssetService.AssetUid, r: Rights) = ()
  def link(
      uid: AssetService.AssetUid,
      tt: ImportService.TargetType,
      ti: ImportService.TargetId,
      role: AssetService.LinkRole,
      p: Option[AssetService.LinkProperty],
      q: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank
  ) =
    linkBasis(uid, tt, ti, role, p, q, rank, None)
  override def linkBasis(
      uid: AssetService.AssetUid,
      tt: ImportService.TargetType,
      ti: ImportService.TargetId,
      role: AssetService.LinkRole,
      p: Option[AssetService.LinkProperty],
      q: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank,
      basis: Option[AssetService.LinkBasis]
  ) =
    links += ((uid, ti, role, s"$rank", p, q, basis)); true
  def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id) = false
  def checked(
      provider: ImportService.Provider,
      tt: ImportService.TargetType,
      ti: ImportService.TargetId,
      found: ImportService.FoundCount,
      qid: Option[Wikidata.Id]
  ) = checks((provider, ti)) = found
  override def placeFacts(ids: List[Place.Id]) = facts.view.filterKeys(ids.toSet).toMap
  override def mapPlacesNamed(keys: List[ImportService.PlaceKey]) = named.view.filterKeys(keys.toSet).toMap
  override def placeExternalIds(ids: List[Place.Id]) =
    ids.flatMap(id => ExternalIds.ofPlaceId(id).map(x => id -> Set(x))).toMap
  override def mapPlacesWithIds(ids: List[(ExternalIds.Scheme, ExternalIds.ExternalId)]) =
    byId.view.filterKeys(ids.toSet).toMap
  override def pleiadesIds(ids: List[Place.Id]) = pleiades.view.filterKeys(ids.toSet).toMap

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
    val calls = scala.collection.mutable.ListBuffer.empty[ConverterClient.Filename]
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
    override def thumbnail(
        bytes: ConverterClient.DocumentBytes,
        filename: ConverterClient.Filename,
        maxPx: Pixels
    ): Either[Problem, ConverterClient.Thumb] =
      calls += filename
      Right(
        ConverterClient.Thumb(
          ConverterClient.Thumb.Bytes(jpeg),
          ConverterClient.Thumb.Mime("image/jpeg"),
          ConverterClient.Thumb.Width(330),
          ConverterClient.Thumb.Height(250)
        )
      )

  // ── Open Context ─────────────────────────────────────────────────────────────────────────────

  private val petra =
    PlaceFacts(
      PlaceFacts.Id("pleiades_697725"),
      PlaceFacts.Label("Petra"),
      Set(PlaceFacts.Keys("petra")),
      Some((DeepTime.SignedYear(-300L), DeepTime.SignedYear(100L))),
      Some(Target.Qid("Q5788")),
      Some(Latitude(30.33)),
      Some(Longitude(35.44))
    )
  private val jordan =
    PlaceFacts(
      PlaceFacts.Id("geonames_248816"),
      PlaceFacts.Label("Jordan"),
      Set(PlaceFacts.Keys("jordan")),
      Some((DeepTime.SignedYear(-3000L), DeepTime.SignedYear(2000L))),
      Some(Target.Qid("Q810")),
      Some(Latitude(31.0)),
      Some(Longitude(36.0))
    )
  private val facts = List(petra, jordan).map(f => Place.Id.assume(f.id.value) -> f).toMap
  private val named =
    Map(ImportService.PlaceKey("jordan") -> List(jordan), ImportService.PlaceKey("petra") -> List(petra))
  private val byId = Map((ExternalIds.Scheme("pleiades"), ExternalIds.ExternalId("697725")) -> List(petra))
  private def target(f: PlaceFacts) =
    Target(Target.TargetType("place"), Target.Id(f.id.value), Target.Qid(""), Target.Label(f.label.value))

  private val DryOn = CollectionImport.DryRun(true)
  private val DryOff = CollectionImport.DryRun(false)
  private val PlDryOn = MediaImporter.DryRun(true)
  private val PlDryOff = MediaImporter.DryRun(false)

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
      dryRun: CollectionImport.DryRun,
      store: PortalStore,
      conv: ThumbConverter = ThumbConverter()
  ): MediaImportReport =
    val col = FakeMediaHttp.builtin(BuiltinMedia.EntryName("opencontext"))
    val (polite, _) = FakeMediaHttp.polite(
      http,
      cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(col.conf.requestIntervalMs.value))
    )
    CollectionImport(col, polite, cfg, conv).run(
      List(target(t)),
      dryRun,
      refresh = CollectionImport.Refresh(false),
      store
    )

  test(
    "Open Context dry run: tied by the record's own Pleiades id and its dates; human remains and other dates refused; one request"
  ):
    val http = openContext()
    val store = PortalStore(facts, named, byId)
    val r = runOc(http, petra, dryRun = DryOn, store)
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
      r.rejected.map(_.key.value).sorted,
      List(
        "its dates do not overlap the place's",
        "open-context (rejected): human remains (Open Context flags them; not shown)"
      )
    )
    assertEquals(r.byLicence, List(MediaCount(MediaCount.Key("CC-BY-4.0"), MediaCount.N(4))))
    assert(store.saved.isEmpty && store.links.isEmpty && store.checks.isEmpty)
    assertEquals(http.requests.size, 1, "one search answers the records; no item is read for a dry run")
    assert(http.requests.forall(_._2.get("User-Agent").exists(ua => !ua.contains("@"))), "no personal data")

  test(
    "Open Context, real Domuztepe records (#2571): tied by the site its context names, though a place 'I' and the province are on the map"
  ):
    // the map as it was when the full run stored 0: a place called "I" (the records' operation "I"),
    // Kahramanmaraş carrying the province's GeoNames id the records give
    val dom = PlaceFacts(
      PlaceFacts.Id("wikidata_q4165990"),
      PlaceFacts.Label("Domuztepe"),
      Set(PlaceFacts.Keys("domuztepe")),
      Some((DeepTime.SignedYear(-6500L), DeepTime.SignedYear(-5400L))),
      Some(Target.Qid("Q4165990")),
      Some(Latitude(37.33)),
      Some(Longitude(36.87))
    )
    val placeI = PlaceFacts(
      PlaceFacts.Id("wikidata_q103776406"),
      PlaceFacts.Label("I"),
      Set(PlaceFacts.Keys("i")),
      None,
      Some(Target.Qid("Q103776406")),
      Some(Latitude(50.0)),
      Some(Longitude(10.0))
    )
    val kahr =
      PlaceFacts(
        PlaceFacts.Id("ne_city_1159127453"),
        PlaceFacts.Label("Kahramanmaraş"),
        Set(PlaceFacts.Keys("kahramanmaras")),
        None,
        None,
        Some(Latitude(37.58)),
        Some(Longitude(36.93))
      )
    val turkey = PlaceFacts(
      PlaceFacts.Id("ne_country_tur"),
      PlaceFacts.Label("Turkey"),
      Set(PlaceFacts.Keys("turkey")),
      None,
      Some(Target.Qid("Q43")),
      Some(Latitude(39.0)),
      Some(Longitude(35.0))
    )
    val store = PortalStore(
      Map(Place.Id.assume(dom.id.value) -> dom),
      Map(
        ImportService.PlaceKey("domuztepe") -> List(dom),
        ImportService.PlaceKey("i") -> List(placeI),
        ImportService.PlaceKey("turkey") -> List(turkey),
        ImportService.PlaceKey("kahramanmaras") -> List(kahr)
      ),
      Map((ExternalIds.Scheme("geonames"), ExternalIds.ExternalId("310858")) -> List(kahr))
    )
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://opencontext.org/query/.json?") then FakeMediaHttp.json(fx("oc-search-domuztepe.json"))
      else Left(s"unexpected $url")
    }
    val r = runOc(http, dom, dryRun = DryOn, store)
    assertEquals(
      r.samples.map(_.basis).distinct,
      List("context = Domuztepe; 6500–5500 BCE within 6500–5400 BCE"),
      r.rejected.toString
    )
    assertEquals(r.samples.size, 3)
    assertEquals(
      r.rejected.map(_.key.value),
      List("open-context (rejected): human remains (Open Context flags them; not shown)")
    )

  test("Open Context: a record from Petra is not filed under the country its context path names"):
    val r = runOc(openContext(), jordan, dryRun = DryOn, PortalStore(facts, named, byId))
    assert(r.samples.isEmpty, r.samples.toString)
    assert(
      r.rejected.exists(c => c.key.value == "its place id names another place on the map" && c.n.value == 3),
      r.rejected.toString
    )

  test(
    "Open Context real run: the item read for each stored record — its preview stored, its thumbnail the card's, its licence re-read"
  ):
    val http = openContext()
    val store = PortalStore(facts, named, byId)
    val conv = ThumbConverter()
    val r = runOc(http, petra, dryRun = DryOff, store, conv)
    assertEquals((r.stored, r.linked), (2, 2), r.failed.toString)
    val (uid, a) = store.saved(ProviderAsset.Key(Bowls7))
    assertEquals(a.provider, MediaProvider.OpenContext)
    assertEquals((a.license, a.licenseUrl), ("CC-BY-4.0", Some("https://creativecommons.org/licenses/by/4.0")))
    assertEquals(a.author, Some("Martha Sharp Joukowsky"))
    assert(
      a.credit.value.endsWith(
        "In Petra Great Temple Excavations. Open Context. https://n2t.net/ark:/28722/k2xk8d17p. CC BY 4.0"
      ),
      a.credit
    )
    assertEquals(a.sourceUrl, s"https://opencontext.org/media/$Bowls7")
    assertEquals(
      a.originalUrl,
      Some("https://archive.org/download/opencontext-10-bowls-7-30-38jpg/10-bowls-7-30-38jpg.jpg")
    )
    assert(new String(a.bytes.value, "UTF-8").contains("ia-previews"), "the preview rendition, not the original")
    assert(a.thumb.exists(t => new String(t.value, "UTF-8").contains("ia-thumbnails")))
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
    val r = runOc(openContext(_.replace("licenses/by/4.0", "licenses/by-nc/4.0")), petra, dryRun = DryOff, store)
    assertEquals(r.stored, 1)
    assert(!store.saved.contains(ProviderAsset.Key(Bowls6)))
    assert(r.failed.exists(f => f.value.contains(Bowls6) && f.value.contains("differs")), r.failed.toString)

  // ── Pleiades ─────────────────────────────────────────────────────────────────────────────────

  private val zygouries =
    Target(Target.TargetType("place"), Target.Id("pleiades_757208100"), Target.Qid(""), Target.Label("Zygouries"))

  private def param(url: String, name: String): Option[String] =
    url.split("[?&]").toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name => java.net.URLDecoder.decode(v, "UTF-8")
    }

  /** Commons' imageinfo: every title asked is a CC BY-SA 4.0 file with an author. */
  private def info(titles: List[String], width: String): String =
    val pages = titles.zipWithIndex.map { (t, i) =>
      s"""{"pageid":${i + 1},"ns":6,"title":${Json
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

  private def runPl(http: FakeMediaHttp, dryRun: MediaImporter.DryRun, store: PortalStore): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    OwnImport(
      MediaImporter.ImporterName("pleiades"),
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("pleiades")),
      polite,
      cfg
    )
      .run(List(zygouries), dryRun, refresh = ImportService.Refresh(false), store)

  private def plStore() = PortalStore(
    Map.empty,
    Map.empty,
    pleiades = Map(Place.Id("pleiades_757208100") -> List(ExternalIds.ExternalId("757208100")))
  )

  test(
    "Pleiades dry run: the Commons file the record links, licence read from Commons; Flickr, a category, another site counted"
  ):
    val http = pleiades()
    val store = plStore()
    val r = runPl(http, dryRun = PlDryOn, store)
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
      r.rejected.map(c => (c.key.value, c.n.value)).sorted,
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
    val r = runPl(pleiades(), dryRun = PlDryOff, store)
    assertEquals((r.stored, r.linked), (1, 1), r.failed.toString)
    val (uid, a) = store.saved(ProviderAsset.Key("File:Zygouries-General plan of the excavations.jpg"))
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
    val c = cfg.modify(_.maxRetries).setTo(MediaConfig.MaxRetries(0))
    val (polite, _) = FakeMediaHttp.polite(
      pleiades(Right(HttpAnswer(HttpAnswer.Code(503), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))),
      c
    )
    val r = OwnImport(
      MediaImporter.ImporterName("pleiades"),
      FakeMediaHttp.collection(Registries.MediaCollectionsKey("pleiades")),
      polite,
      c
    )
      .run(List(zygouries), dryRun = MediaImporter.DryRun(false), refresh = ImportService.Refresh(false), store)
    assert(r.failed.exists(_.value.contains("Pleiades place 757208100")), r.failed.toString)
    assert(store.checks.isEmpty)
