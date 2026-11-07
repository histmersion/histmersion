package com.histmersion.server.media

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.MediaStore
import com.histmersion.domain.media.imports.OwnImport
import com.histmersion.domain.media.imports.{ImportService, MediaImporter}
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.pluginhost.{HttpTransport, Registries}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.media.assets.{Known, ProviderAsset, Rights}
import com.histmersion.domain.media.imports.Target
import com.histmersion.api.media.{AssetKind, MediaCount, MediaImportReport, MediaProvider}

/** The Wikidata → Commons provider (#1446) end to end against a FAKE provider (captured answers, trimmed) and an
  * in-memory store — the `media-wikidata` plugin's collection, as the host builds it, run by the host's import of a
  * collection of its targets' own pictures: what it counts, what it refuses and why, what it stores and links, and that
  * a dry run sends two requests and writes nothing.
  */
class WikidataCommonsSuite extends munit.FunSuite:

  /** The store as a few maps. */
  class MemStore extends MediaStore:
    val saved = scala.collection.mutable.LinkedHashMap.empty[ProviderAsset.Key, (AssetService.AssetUid, ProviderAsset, (ImportService.TargetType, ImportService.TargetId))]
    val links = scala.collection.mutable.LinkedHashSet
      .empty[(AssetService.AssetUid, ImportService.TargetType, ImportService.TargetId, AssetService.LinkRole, Option[AssetService.LinkProperty], Option[AssetService.LinkItem], AssetService.LinkRank)]
    val cats = scala.collection.mutable.LinkedHashSet.empty[(Place.Id, ImportService.CommonsCategory, Wikidata.Id)]
    val checks = scala.collection.mutable.LinkedHashMap.empty[(ImportService.TargetType, ImportService.TargetId), ImportService.FoundCount]
    val refreshed = scala.collection.mutable.ListBuffer.empty[(AssetService.AssetUid, Rights)]
    def known(provider: ImportService.Provider, keys: List[AssetService.ProviderKey]) =
      val wanted = keys.map(_.value).toSet
      saved.collect { case (k, (uid, a, _)) if wanted(k.value) => AssetService.ProviderKey(k.value) -> Known(Known.Uid(uid.value), Some(Known.License(a.license.value))) }.toMap
    def save(a: ProviderAsset, first: (ImportService.TargetType, ImportService.TargetId)) =
      val existing = saved.get(a.key).map(_._1)
      val uid = existing.getOrElse(AssetService.AssetUid(f"00000000-0000-0000-0000-${saved.size + 1}%012d"))
      saved(a.key) = (uid, a, first)
      (uid, existing.isEmpty)
    def refreshRights(uid: AssetService.AssetUid, r: Rights) = refreshed += uid -> r
    def link(
        uid: AssetService.AssetUid,
        tt: ImportService.TargetType,
        ti: ImportService.TargetId,
        role: AssetService.LinkRole,
        p: Option[AssetService.LinkProperty],
        q: Option[AssetService.LinkItem],
        rank: AssetService.LinkRank
    ) =
      val before = links.exists(l => l._1 == uid && l._2 == tt && l._3 == ti && l._4 == role)
      links += ((uid, tt, ti, role, p, q, rank))
      !before
    def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id) = cats.add((placeId, category, qid))
    def checked(provider: ImportService.Provider, tt: ImportService.TargetType, ti: ImportService.TargetId, found: ImportService.FoundCount, qid: Option[Wikidata.Id]) = checks((tt, ti)) = found

  private val wbgetentities = FakeMediaHttp.fixture("wbgetentities.json")
  private val imageinfo = FakeMediaHttp.fixture("imageinfo.json")

  private def jpeg(tag: String): Array[Byte] = Array[Byte](-1, -40, -1) ++ tag.getBytes("UTF-8")

  /** The provider: Wikidata and Commons answer from the fixtures (`info` replaces the imageinfo answer), the files are
    * bytes named after their URL.
    */
  private def provider(info: String = imageinfo): FakeMediaHttp = FakeMediaHttp { url =>
    if url.contains("action=wbgetentities") then FakeMediaHttp.json(wbgetentities)
    else if url.contains("prop=imageinfo") && url.contains("iiurlwidth=1280") then FakeMediaHttp.json(info)
    else if url.contains("prop=imageinfo") then FakeMediaHttp.json(info.replace("1280px-", "330px-"))
    else if url.startsWith("https://upload.example/") then FakeMediaHttp.bytes(jpeg(url), "image/jpeg")
    else Left(s"unexpected $url")
  }

  private val targets = List(
    Target(com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("pleiades_570491"), com.histmersion.domain.media.imports.Target.Qid("Q132564"), com.histmersion.domain.media.imports.Target.Label("Mycenae")),
    Target(com.histmersion.domain.media.imports.Target.TargetType("place"), com.histmersion.domain.media.imports.Target.Id("x_site"), com.histmersion.domain.media.imports.Target.Qid("Q999"), com.histmersion.domain.media.imports.Target.Label("X")),
    Target(com.histmersion.domain.media.imports.Target.TargetType("event"), com.histmersion.domain.media.imports.Target.Id("fall_of_x"), com.histmersion.domain.media.imports.Target.Qid("Q404"), com.histmersion.domain.media.imports.Target.Label("Fall of X"))
  )

  private def run(
      http: FakeMediaHttp,
      dryRun: MediaImporter.DryRun,
      store: MemStore = MemStore(),
      refresh: ImportService.Refresh = ImportService.Refresh(false),
      cfg: MediaConfig = FakeMediaHttp.cfg,
      ts: List[Target] = targets,
      settings: String = ""
  ): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    OwnImport(com.histmersion.domain.media.imports.MediaImporter.ImporterName("wikidata"), FakeMediaHttp.collection(Registries.MediaCollectionsKey("wikidata"), settings), polite, cfg).run(ts, dryRun, refresh, store)

  private val DryOn = MediaImporter.DryRun(true)
  private val DryOff = MediaImporter.DryRun(false)
  private val RefreshOn = ImportService.Refresh(true)

  test("dry run: counts per provider, licence and role, the refusals with reasons — two requests, nothing stored"):
    val http = provider()
    val store = MemStore()
    val r = run(http, dryRun = DryOn, store)
    assertEquals(r.provider, MediaProvider.Commons)
    assertEquals((r.targets, r.items, r.statements, r.files), (3, 2, 5, 5))
    assertEquals(
      r.byLicence,
      List(
        MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-4.0"), com.histmersion.api.media.MediaCount.N(1)),
        MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-NC-SA-4.0"), com.histmersion.api.media.MediaCount.N(1), allowed = com.histmersion.api.media.MediaCount.Allowed(false)),
        MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-SA-2.5"), com.histmersion.api.media.MediaCount.N(1)),
        MediaCount(com.histmersion.api.media.MediaCount.Key("all-rights-reserved"), com.histmersion.api.media.MediaCount.N(1), allowed = com.histmersion.api.media.MediaCount.Allowed(false))
      )
    )
    assertEquals(
      r.byRole,
      List(MediaCount(com.histmersion.api.media.MediaCount.Key("image"), com.histmersion.api.media.MediaCount.N(2)), MediaCount(com.histmersion.api.media.MediaCount.Key("aerial-view"), com.histmersion.api.media.MediaCount.N(1)), MediaCount(com.histmersion.api.media.MediaCount.Key("plan"), com.histmersion.api.media.MediaCount.N(1)), MediaCount(com.histmersion.api.media.MediaCount.Key("route-map"), com.histmersion.api.media.MediaCount.N(1)))
    )
    assertEquals(
      r.rejected.map(_.key.value).sorted,
      List(
        "licence cc-by-nc-sa not allowed for wikimedia-commons",
        "marked non-free on Commons",
        "missing on Commons",
        "no author to credit"
      )
    )
    assertEquals((r.stored, r.updated, r.linked, r.categories), (0, 0, 0, 0))
    assertEquals(http.requests.size, 2, "one wbgetentities, one imageinfo")
    assert(store.saved.isEmpty && store.links.isEmpty && store.checks.isEmpty && store.cats.isEmpty)

  test("the requests are the documented APIs: wbgetentities by ids, imageinfo with extmetadata; maxlag to Commons"):
    val http = provider()
    run(http, dryRun = DryOn)
    val List(wd, commons) = http.requests.map(_._1).toList: @unchecked
    assert(
      wd.startsWith("https://www.wikidata.org/w/api.php?action=wbgetentities&") && wd.contains(
        "ids=Q404%7CQ132564%7CQ999"
      ),
      wd
    )
    assert(!wd.contains("maxlag"), "not sent to Wikidata by default (its query-service lag): " + wd)
    assert(commons.contains("maxlag=5"), commons)
    assert(
      commons.startsWith("https://commons.wikimedia.org/w/api.php?action=query&") && commons.contains("prop=imageinfo"),
      commons
    )
    assert(commons.contains("iiprop=url%7Csize%7Cmime%7Cextmetadata"), commons)

  test("import: the allowed file is stored as Commons' renditions with its credit, linked, its place checked"):
    val http = provider()
    val store = MemStore()
    val r = run(http, dryRun = DryOff, store)
    assertEquals((r.stored, r.updated, r.linked, r.categories), (1, 0, 1, 1))
    val (uid, a, first) = store.saved(ProviderAsset.Key("File:Lions-Gate-Mycenae.jpg"))
    assertEquals(first, ("place", "pleiades_570491"))
    assertEquals(a.provider, MediaProvider.Commons)
    assertEquals(a.license, "CC-BY-SA-2.5")
    assertEquals(a.credit, "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons")
    assertEquals(a.author, Some("Andreas Trepte"))
    assertEquals(a.licenseUrl, Some("https://creativecommons.org/licenses/by-sa/2.5"))
    assertEquals(a.sourceUrl, "https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg")
    assertEquals(a.originalUrl, Some("https://upload.example/Lions-Gate-Mycenae.jpg"))
    assertEquals(
      new String(a.bytes.value.drop(3), "UTF-8"),
      "https://upload.example/thumb/Lions-Gate-Mycenae.jpg/1280px-Lions-Gate-Mycenae.jpg"
    )
    assertEquals(
      a.thumb.map(b => new String(b.value.drop(3), "UTF-8")),
      Some("https://upload.example/thumb/Lions-Gate-Mycenae.jpg/330px-Lions-Gate-Mycenae.jpg")
    )
    assertEquals((a.width, a.height, a.mime, a.kind), (Some(1280), Some(1037), "image/jpeg", Some(AssetKind.Photo)))
    assertEquals(store.links.toList, List((uid, "place", "pleiades_570491", "image", Some("P18"), Some("Q132564"), 0)))
    assertEquals(store.cats.toList, List(("pleiades_570491", "Mycenae", "Q132564")))
    assertEquals(
      store.checks.toMap,
      Map(("place", "pleiades_570491") -> 1, ("place", "x_site") -> 0, ("event", "fall_of_x") -> 0)
    )
    assert(
      !http.requests.exists(_._1.contains("Mycenae_plan_NC")) && !http.requests.exists(_._1.contains("arr.jpg")),
      "a refused file is never downloaded"
    )

  test("a second import downloads nothing again; with refresh the licence is re-read"):
    val store = MemStore()
    run(provider(), dryRun = DryOff, store)
    val again = provider()
    val r = run(again, dryRun = DryOff, store)
    assertEquals((r.stored, r.updated, r.linked), (0, 0, 0))
    assert(!again.requests.exists(_._1.startsWith("https://upload.example/")), "no download")
    val r2 = run(provider(), dryRun = DryOff, store, refresh = RefreshOn)
    assertEquals(r2.updated, 1)
    assertEquals(store.refreshed.map(_._2.license).toList, List("CC-BY-SA-2.5"))

  test("a stored picture whose licence is no longer allowed is kept and listed as revoked"):
    val store = MemStore()
    run(provider(), dryRun = DryOff, store)
    val nc = imageinfo.replace("\"cc-by-sa-2.5\"", "\"cc-by-nc-4.0\"").replace("CC BY-SA 2.5", "CC BY-NC 4.0")
    val r = run(provider(nc), dryRun = DryOff, store, refresh = RefreshOn)
    assertEquals(r.revoked.size, 1)
    assert(r.revoked.head.value.startsWith("File:Lions-Gate-Mycenae.jpg (media:"), r.revoked.head)
    assert(store.saved.contains(ProviderAsset.Key("File:Lions-Gate-Mycenae.jpg")), "kept — nothing is deleted")
    assert(store.refreshed.isEmpty, "a refused licence is not written over the stored one")

  test("per-property 2 takes the normal-ranked image too: a public-domain one credits Commons alone"):
    val store = MemStore()
    val r =
      run(provider(), dryRun = DryOff, store, ts = targets.take(1), settings = "histmersion.media.per-property = 2")
    assertEquals(r.stored, 1, "only the preferred P18 is taken while a preferred one exists")
    val withoutPreferred = wbgetentities.replace("\"rank\":\"preferred\"", "\"rank\":\"normal\"")
    val http = FakeMediaHttp { url =>
      if url.contains("action=wbgetentities") then FakeMediaHttp.json(withoutPreferred)
      else provider().get(HttpTransport.Url(url), Map.empty).left.map(_.value)
    }
    val store2 = MemStore()
    run(http, dryRun = DryOff, store2, ts = targets.take(1), settings = "histmersion.media.per-property = 2")
    assertEquals(
      store2.saved.get(ProviderAsset.Key("File:Mycenae ruins dsc06390.jpg")).map(v => (v._2.license, v._2.credit)),
      Some(("public-domain", "Public domain, via Wikimedia Commons"))
    )

  test("a failed batch is reported and the run goes on; NC is stored when the allowlist names it"):
    val down = FakeMediaHttp(url =>
      if url.contains("action=wbgetentities") then FakeMediaHttp.json("{}", code = 404)
      else provider().get(HttpTransport.Url(url), Map.empty).left.map(_.value)
    )
    val r = run(down, dryRun = DryOn)
    assertEquals(r.items, 0)
    assert(r.failed.exists(_.value.startsWith("Wikidata Q404…: www.wikidata.org: HTTP 404")), r.failed.toString)
    val withNc = FakeMediaHttp.cfg.modify(_.commons).setTo(FakeMediaHttp.cfg.commons.modify(_.licences).setTo(MediaCommonsConfig.Licences("cc0,public-domain,cc-by,cc-by-sa,cc-by-nc-sa"))
    )
    val store = MemStore()
    val r2 = run(provider(), dryRun = DryOff, store, cfg = withNc)
    assertEquals(r2.stored, 2)
    assertEquals(store.saved.get(ProviderAsset.Key("File:Mycenae plan NC.png")).map(_._2.kind), Some(Some(AssetKind.Plan)))

  test("a rendition larger than max-bytes is not stored, and says why"):
    val store = MemStore()
    val r = run(provider(), dryRun = DryOff, store, cfg = FakeMediaHttp.cfg.modify(_.maxBytes).setTo(MediaConfig.MaxBytes(2L)))
    assertEquals(r.stored, 0)
    assert(r.failed.exists(_.value.contains("larger than media.max-bytes")), r.failed.toString)

  test(
    "a picture the store cannot write is a line in the report; the others are stored and every target checked (#1491)"
  ):
    val withNc = FakeMediaHttp.cfg.modify(_.commons).setTo(FakeMediaHttp.cfg.commons.modify(_.licences).setTo(MediaCommonsConfig.Licences("cc0,public-domain,cc-by,cc-by-sa,cc-by-nc-sa"))
    )
    val store = new MemStore:
      override def save(a: ProviderAsset, first: (ImportService.TargetType, ImportService.TargetId)) =
        if a.key == ProviderAsset.Key("File:Mycenae plan NC.png") then throw IllegalStateException("disk full") else super.save(a, first)
    val r = run(provider(), dryRun = DryOff, store, cfg = withNc)
    assertEquals(r.stored, 1)
    assert(
      r.failed.exists(f => f.value.startsWith("File:Mycenae plan NC.png (store)") && f.value.contains("disk full")),
      r.failed.toString
    )
    assertEquals(store.checks.keySet, targets.map(t => (t.targetType, t.id)).toSet)
