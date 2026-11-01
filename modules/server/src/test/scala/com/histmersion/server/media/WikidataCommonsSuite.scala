package com.histmersion.server.media

import com.histmersion.domain.media.imports.MediaStore
import com.histmersion.domain.media.imports.OwnImport

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.media.assets.{Known, ProviderAsset, Rights}
import com.histmersion.domain.media.imports.Target

/** The Wikidata → Commons provider (#1446) end to end against a FAKE provider (captured answers, trimmed) and an
  * in-memory store — the `media-wikidata` plugin's collection, as the host builds it, run by the host's import of a
  * collection of its targets' own pictures: what it counts, what it refuses and why, what it stores and links, and that
  * a dry run sends two requests and writes nothing.
  */
class WikidataCommonsSuite extends munit.FunSuite:

  /** The store as a few maps. */
  class MemStore extends MediaStore:
    val saved = scala.collection.mutable.LinkedHashMap.empty[String, (String, ProviderAsset, (String, String))]
    val links = scala.collection.mutable.LinkedHashSet
      .empty[(String, String, String, String, Option[String], Option[String], Int)]
    val cats = scala.collection.mutable.LinkedHashSet.empty[(String, String, String)]
    val checks = scala.collection.mutable.LinkedHashMap.empty[(String, String), Int]
    val refreshed = scala.collection.mutable.ListBuffer.empty[(String, Rights)]
    def known(provider: String, keys: List[String]) =
      saved.view.filterKeys(keys.toSet).map((k, v) => k -> Known(v._1, Some(v._2.license))).toMap
    def save(a: ProviderAsset, first: (String, String)) =
      val existing = saved.get(a.key).map(_._1)
      val uid = existing.getOrElse(f"00000000-0000-0000-0000-${saved.size + 1}%012d")
      saved(a.key) = (uid, a, first)
      (uid, existing.isEmpty)
    def refreshRights(uid: String, r: Rights) = refreshed += uid -> r
    def link(uid: String, tt: String, ti: String, role: String, p: Option[String], q: Option[String], rank: Int) =
      val before = links.exists(l => l._1 == uid && l._2 == tt && l._3 == ti && l._4 == role)
      links += ((uid, tt, ti, role, p, q, rank))
      !before
    def commonsCategory(placeId: String, category: String, qid: String) = cats.add((placeId, category, qid))
    def checked(provider: String, tt: String, ti: String, found: Int, qid: Option[String]) = checks((tt, ti)) = found

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
    Target("place", "pleiades_570491", "Q132564", "Mycenae"),
    Target("place", "x_site", "Q999", "X"),
    Target("event", "fall_of_x", "Q404", "Fall of X")
  )

  private def run(
      http: FakeMediaHttp,
      dryRun: Boolean,
      store: MemStore = MemStore(),
      refresh: Boolean = false,
      cfg: MediaConfig = FakeMediaHttp.cfg,
      ts: List[Target] = targets,
      settings: String = ""
  ): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg)
    OwnImport("wikidata", FakeMediaHttp.collection("wikidata", settings), polite, cfg).run(ts, dryRun, refresh, store)

  test("dry run: counts per provider, licence and role, the refusals with reasons — two requests, nothing stored"):
    val http = provider()
    val store = MemStore()
    val r = run(http, dryRun = true, store)
    assertEquals(r.provider, MediaProvider.Commons)
    assertEquals((r.targets, r.items, r.statements, r.files), (3, 2, 5, 5))
    assertEquals(
      r.byLicence,
      List(
        MediaCount("CC-BY-4.0", 1),
        MediaCount("CC-BY-NC-SA-4.0", 1, allowed = false),
        MediaCount("CC-BY-SA-2.5", 1),
        MediaCount("all-rights-reserved", 1, allowed = false)
      )
    )
    assertEquals(
      r.byRole,
      List(MediaCount("image", 2), MediaCount("aerial-view", 1), MediaCount("plan", 1), MediaCount("route-map", 1))
    )
    assertEquals(
      r.rejected.map(_.key).sorted,
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
    run(http, dryRun = true)
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
    val r = run(http, dryRun = false, store)
    assertEquals((r.stored, r.updated, r.linked, r.categories), (1, 0, 1, 1))
    val (uid, a, first) = store.saved("File:Lions-Gate-Mycenae.jpg")
    assertEquals(first, ("place", "pleiades_570491"))
    assertEquals(a.provider, MediaProvider.Commons)
    assertEquals(a.license, "CC-BY-SA-2.5")
    assertEquals(a.credit, "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons")
    assertEquals(a.author, Some("Andreas Trepte"))
    assertEquals(a.licenseUrl, Some("https://creativecommons.org/licenses/by-sa/2.5"))
    assertEquals(a.sourceUrl, "https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg")
    assertEquals(a.originalUrl, Some("https://upload.example/Lions-Gate-Mycenae.jpg"))
    assertEquals(
      new String(a.bytes.drop(3), "UTF-8"),
      "https://upload.example/thumb/Lions-Gate-Mycenae.jpg/1280px-Lions-Gate-Mycenae.jpg"
    )
    assertEquals(
      a.thumb.map(b => new String(b.drop(3), "UTF-8")),
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
    run(provider(), dryRun = false, store)
    val again = provider()
    val r = run(again, dryRun = false, store)
    assertEquals((r.stored, r.updated, r.linked), (0, 0, 0))
    assert(!again.requests.exists(_._1.startsWith("https://upload.example/")), "no download")
    val r2 = run(provider(), dryRun = false, store, refresh = true)
    assertEquals(r2.updated, 1)
    assertEquals(store.refreshed.map(_._2.license).toList, List("CC-BY-SA-2.5"))

  test("a stored picture whose licence is no longer allowed is kept and listed as revoked"):
    val store = MemStore()
    run(provider(), dryRun = false, store)
    val nc = imageinfo.replace("\"cc-by-sa-2.5\"", "\"cc-by-nc-4.0\"").replace("CC BY-SA 2.5", "CC BY-NC 4.0")
    val r = run(provider(nc), dryRun = false, store, refresh = true)
    assertEquals(r.revoked.size, 1)
    assert(r.revoked.head.startsWith("File:Lions-Gate-Mycenae.jpg (media:"), r.revoked.head)
    assert(store.saved.contains("File:Lions-Gate-Mycenae.jpg"), "kept — nothing is deleted")
    assert(store.refreshed.isEmpty, "a refused licence is not written over the stored one")

  test("per-property 2 takes the normal-ranked image too: a public-domain one credits Commons alone"):
    val store = MemStore()
    val r =
      run(provider(), dryRun = false, store, ts = targets.take(1), settings = "histmersion.media.per-property = 2")
    assertEquals(r.stored, 1, "only the preferred P18 is taken while a preferred one exists")
    val withoutPreferred = wbgetentities.replace("\"rank\":\"preferred\"", "\"rank\":\"normal\"")
    val http = FakeMediaHttp { url =>
      if url.contains("action=wbgetentities") then FakeMediaHttp.json(withoutPreferred)
      else provider().get(url, Map.empty)
    }
    val store2 = MemStore()
    run(http, dryRun = false, store2, ts = targets.take(1), settings = "histmersion.media.per-property = 2")
    assertEquals(
      store2.saved.get("File:Mycenae ruins dsc06390.jpg").map(v => (v._2.license, v._2.credit)),
      Some(("public-domain", "Public domain, via Wikimedia Commons"))
    )

  test("a failed batch is reported and the run goes on; NC is stored when the allowlist names it"):
    val down = FakeMediaHttp(url =>
      if url.contains("action=wbgetentities") then FakeMediaHttp.json("{}", code = 404)
      else provider().get(url, Map.empty)
    )
    val r = run(down, dryRun = true)
    assertEquals(r.items, 0)
    assert(r.failed.exists(_.startsWith("Wikidata Q404…: www.wikidata.org: HTTP 404")), r.failed.toString)
    val withNc = FakeMediaHttp.cfg.copy(commons =
      FakeMediaHttp.cfg.commons.copy(licences = "cc0,public-domain,cc-by,cc-by-sa,cc-by-nc-sa")
    )
    val store = MemStore()
    val r2 = run(provider(), dryRun = false, store, cfg = withNc)
    assertEquals(r2.stored, 2)
    assertEquals(store.saved.get("File:Mycenae plan NC.png").map(_._2.kind), Some(Some(AssetKind.Plan)))

  test("a rendition larger than max-bytes is not stored, and says why"):
    val store = MemStore()
    val r = run(provider(), dryRun = false, store, cfg = FakeMediaHttp.cfg.copy(maxBytes = 2))
    assertEquals(r.stored, 0)
    assert(r.failed.exists(_.contains("larger than media.max-bytes")), r.failed.toString)

  test(
    "a picture the store cannot write is a line in the report; the others are stored and every target checked (#1491)"
  ):
    val withNc = FakeMediaHttp.cfg.copy(commons =
      FakeMediaHttp.cfg.commons.copy(licences = "cc0,public-domain,cc-by,cc-by-sa,cc-by-nc-sa")
    )
    val store = new MemStore:
      override def save(a: ProviderAsset, first: (String, String)) =
        if a.key == "File:Mycenae plan NC.png" then throw IllegalStateException("disk full") else super.save(a, first)
    val r = run(provider(), dryRun = false, store, cfg = withNc)
    assertEquals(r.stored, 1)
    assert(
      r.failed.exists(f => f.startsWith("File:Mycenae plan NC.png (store)") && f.contains("disk full")),
      r.failed.toString
    )
    assertEquals(store.checks.keySet, targets.map(t => (t.targetType, t.id)).toSet)
