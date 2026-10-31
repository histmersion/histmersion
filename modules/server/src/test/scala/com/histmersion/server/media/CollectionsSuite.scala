package com.histmersion.server.media

import com.histmersion.pluginhost.HttpAnswer

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.{Known, PlaceFacts, ProviderAsset, Rights, Target}
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.plugin.MediaCollection
import com.histmersion.values.MediaRole

/** The store as a few maps, with the gazetteer facts the matches read. */
final class CollStore(facts: Map[String, PlaceFacts], named: Map[String, List[PlaceFacts]]) extends MediaStore:
  val saved = scala.collection.mutable.LinkedHashMap.empty[String, (String, ProviderAsset)]
  val links = scala.collection.mutable.ListBuffer.empty[(String, String, String, String, Int, Option[String])]
  val checks = scala.collection.mutable.LinkedHashMap.empty[(String, String), Int]
  val refreshed = scala.collection.mutable.ListBuffer.empty[String]
  def known(provider: String, keys: List[String]) =
    saved.view.filterKeys(keys.toSet).map((k, v) => k -> Known(v._1, Some(v._2.license))).toMap
  def save(a: ProviderAsset, first: (String, String)) =
    val existing = saved.get(a.key).map(_._1)
    val uid = existing.getOrElse(f"00000000-0000-0000-0000-${saved.size + 1}%012d")
    saved(a.key) = (uid, a)
    (uid, existing.isEmpty)
  def refreshRights(uid: String, r: Rights) = refreshed += uid
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
    links += ((uid, tt, ti, role, rank, basis)); true
  def commonsCategory(placeId: String, category: String, qid: String) = false
  def checked(provider: String, tt: String, ti: String, found: Int, qid: Option[String]) = checks((provider, ti)) =
    found
  override def placeFacts(ids: List[String]) = facts.view.filterKeys(ids.toSet).toMap
  override def mapPlacesNamed(keys: List[String]) = named.view.filterKeys(keys.toSet).toMap

/** The collections (#1446) end to end against FAKE providers (trimmed real answers; loc.gov's follow its documented
  * shape) and an in-memory store: each first-party plugin's collection, as the host builds it, run by the host's
  * collection import — what a dry run lists for a person to judge, what is refused and why, what a real run stores,
  * links (with the match's reason) and records as asked — and that no request leaves this machine.
  */
class CollectionsSuite extends munit.FunSuite:

  private val jpeg = Array[Byte](-1, -40, -1)

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
      Right(ConverterClient.Thumb(jpeg ++ Array[Byte](42), "image/jpeg", 330, 250))

  private def fx(name: String) = FakeMediaHttp.fixture(name)

  private val mycenae = PlaceFacts(
    "pleiades_570491",
    "Mycenae",
    Set("mycenae"),
    Some((-1600L, -1100L)),
    Some("Q132564"),
    Some(37.73),
    Some(22.756)
  )
  private val mycenaeGn =
    PlaceFacts("geonames_mycenae", "Mykines", Set("mycenae", "mykines"), None, None, Some(37.731), Some(22.757))
  private val greece =
    PlaceFacts("geonames_390903", "Greece", Set("greece"), Some((-3000L, -100L)), Some("Q41"), Some(39.0), Some(22.0))
  private val cyprus =
    PlaceFacts("geonames_146669", "Cyprus", Set("cyprus"), Some((-1200L, -300L)), Some("Q229"), Some(35.0), Some(33.0))
  private val facts = List(mycenae, greece, cyprus).map(f => f.id -> f).toMap
  // Pleiades' and GeoNames' Mycenae are one site (3 km): the name resolves to it once
  private val named = Map("mycenae" -> List(mycenae, mycenaeGn), "greece" -> List(greece), "cyprus" -> List(cyprus))
  private def target(f: PlaceFacts) = Target("place", f.id, "", f.label)

  private val cfg = FakeMediaHttp.cfg

  private def run(
      col: Collection,
      http: FakeMediaHttp,
      targets: List[Target],
      dryRun: Boolean,
      store: CollStore = CollStore(facts, named),
      conv: ThumbConverter = ThumbConverter()
  ): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(http, cfg.copy(requestIntervalMs = col.conf.requestIntervalMs))
    CollectionImport(col, polite, cfg, conv).run(targets, dryRun, refresh = false, store)

  // ── the Met ──────────────────────────────────────────────────────────────────────────────────

  private def metObject(id: Long, f: String => String = identity): Either[String, HttpAnswer] =
    FakeMediaHttp.json(f(fx("met-object.json").replace("252448", id.toString)))

  private def met(): FakeMediaHttp = FakeMediaHttp { url =>
    if url.contains("/v1.1/search?") then FakeMediaHttp.json(fx("met-search.json"))
    else if url.endsWith("/v1/objects/252448") then metObject(252448)
    else if url.endsWith("/v1/objects/248284") then
      metObject(248284, _.replace("\"isPublicDomain\": true", "\"isPublicDomain\": false"))
    else if url.endsWith("/v1/objects/252447") then
      metObject(252447, _.replace("\"excavation\": \"Mycenae\"", "\"excavation\": \"Argos\""))
    else if url.contains("/v1/objects/") then Right(HttpAnswer(404, Map.empty, Array.emptyByteArray))
    else if url.startsWith("https://images.metmuseum.org/") then
      FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  test("Met dry run: the matches with their reasons, the refusals by reason, the licences — nothing stored"):
    val http = met()
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin("met"), http, List(target(mycenae)), dryRun = true, store)
    assertEquals(r.provider, MediaProvider.Met)
    assertEquals((r.targets, r.items, r.statements, r.files), (1, 3, 1, 1))
    assertEquals(
      r.samples.map(s => (s.targetId, s.key, s.license, s.basis)),
      List(("pleiades_570491", "252448", "CC0-1.0", "excavation = Mycenae; 1400–1201 BCE within 1600–1100 BCE"))
    )
    assertEquals(
      r.rejected.map(_.key).sorted,
      List("names another place on the map", "not Open Access (isPublicDomain is false)")
    )
    assertEquals(r.byLicence, List(MediaCount("CC0-1.0", 2), MediaCount("not open access", 1, allowed = false)))
    assertEquals(r.failed.size, 2, "the two objects the stand-in has no record of are lines, not the end")
    assert(store.saved.isEmpty && store.links.isEmpty && store.checks.isEmpty)
    assert(http.requests.forall(_._1.startsWith("https://collectionapi.metmuseum.org/")), "documented API only")
    assert(http.requests.forall(_._2.get("User-Agent").exists(ua => !ua.contains("@"))), "no personal data")
    assertEquals(http.requests.size, 6, "one search, five objects")

  test(
    "Met: an object refused (403) ends the batch — the next place is not asked, neither place recorded as asked (#2561)"
  ):
    val http = FakeMediaHttp { url =>
      if url.contains("/v1.1/search?") then FakeMediaHttp.json(fx("met-search.json"))
      else if url.endsWith("/v1/objects/248284") then Right(HttpAnswer(403, Map.empty, "Forbidden".getBytes("UTF-8")))
      else if url.contains("/v1/objects/") then metObject(252448)
      else Left(s"unexpected $url")
    }
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin("met"), http, List(target(mycenae), target(greece)), dryRun = false, store)
    assert(r.failed.exists(_.contains("refused the request; the rest of this batch was not asked")), r.failed.toString)
    assertEquals(http.requests.count(_._1.contains("/v1.1/search?")), 1, "Greece was not searched")
    assert(store.checks.isEmpty && store.saved.isEmpty, "nothing recorded: the next run asks both again")

  test(
    "Met real run: the object stored with its CC0 licence, credit, page and original; a thumbnail made here; the link keeps the reason"
  ):
    val store = CollStore(facts, named)
    val conv = ThumbConverter()
    val r = run(FakeMediaHttp.builtin("met"), met(), List(target(mycenae)), dryRun = false, store, conv)
    assertEquals((r.stored, r.linked), (1, 1))
    val (uid, a) = store.saved("252448")
    assertEquals(a.provider, MediaProvider.Met)
    assertEquals(a.license, "CC0-1.0")
    assertEquals(a.credit, "Terracotta animal figurine, The Metropolitan Museum of Art, CC0")
    assertEquals(a.sourceUrl, "https://www.metmuseum.org/art/collection/search/252448")
    assertEquals(a.originalUrl, Some("https://images.metmuseum.org/CRDImages/gr/original/DP121403.jpg"))
    assert(new String(a.bytes, "UTF-8").contains("web-large"), "the museum's web rendition, not the original")
    assertEquals(a.kind, Some(AssetKind.Photo))
    assertEquals(conv.calls.size, 1, "the Met offers no small rendition: the thumbnail is made locally")
    assertEquals(a.thumbWidth, Some(330))
    assertEquals(
      store.links.toList,
      List(
        (
          uid,
          "place",
          "pleiades_570491",
          MediaRole.Object,
          0,
          Some("excavation = Mycenae; 1400–1201 BCE within 1600–1100 BCE")
        )
      )
    )
    assertEquals(store.checks.toMap, Map((MediaProvider.Met, "pleiades_570491") -> 1))

  test("a place whose search fails is not recorded as asked (the next run asks again)"):
    val store = CollStore(facts, named)
    val http = FakeMediaHttp(_ => Right(HttpAnswer(500, Map.empty, Array.emptyByteArray)))
    val c = cfg.copy(maxRetries = 0)
    val (polite, _) = FakeMediaHttp.polite(http, c)
    val r = CollectionImport(FakeMediaHttp.builtin("met"), polite, c, ThumbConverter()).run(
      List(target(mycenae)),
      false,
      false,
      store
    )
    assert(r.failed.exists(_.contains("search for pleiades_570491")), r.failed.toString)
    assert(store.checks.isEmpty)

  // ── Chicago and Cleveland ────────────────────────────────────────────────────────────────────

  private def aic(): FakeMediaHttp = FakeMediaHttp { url =>
    if url.startsWith("https://api.artic.edu/api/v1/artworks/search?") then FakeMediaHttp.json(fx("aic-search.json"))
    else if url.startsWith("https://www.artic.edu/iiif/2/") then
      FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  test("Chicago: only a public-domain work of the place's period; its own IIIF thumbnail, no local one"):
    val store = CollStore(facts, named)
    val conv = ThumbConverter()
    val dry = run(FakeMediaHttp.builtin("aic"), aic(), List(target(cyprus)), dryRun = true, store, conv)
    assertEquals(
      dry.samples.map(s => (s.key, s.basis)),
      List(("108896", "place_of_origin = Cyprus; 500–401 BCE within 1200–300 BCE"))
    )
    assert(dry.rejected.exists(_.key == "its dates do not overlap the place's"), dry.rejected.toString)
    assert(dry.rejected.exists(_.key.startsWith("not public domain")), dry.rejected.toString)
    val r = run(FakeMediaHttp.builtin("aic"), aic(), List(target(cyprus)), dryRun = false, store, conv)
    assertEquals(r.stored, 1)
    val a = store.saved("108896")._2
    assert(new String(a.bytes, "UTF-8").contains("/full/843,/0/default.jpg"))
    assert(a.thumb.exists(t => new String(t, "UTF-8").contains("/full/330,/0/default.jpg")))
    assert(conv.calls.isEmpty)

  test("Cleveland: CC0 records whose culture names the place, of its period"):
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://openaccess-api.clevelandart.org/api/artworks/?") then
        FakeMediaHttp.json(fx("cma-search.json"))
      else Left(s"unexpected $url")
    }
    val r = run(FakeMediaHttp.builtin("cleveland"), http, List(target(cyprus)), dryRun = true)
    // the earring (500–400 BCE) and the bull protome (2000–1000 BCE); the 1700s cloth's culture is uncertain
    assertEquals(r.samples.map(_.key), List("1916.131", "1974.38"))
    assertEquals(http.requests.size, 1, "one search answers the records")

  // ── the Library of Congress ──────────────────────────────────────────────────────────────────

  test("LoC: a public-domain map linked as a map of the place it names; a later one refused; nothing else asked"):
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://www.loc.gov/maps/?") then FakeMediaHttp.json(fx("loc-search.json"))
      else if url.startsWith("https://www.loc.gov/item/2007627513/") then FakeMediaHttp.json(fx("loc-item.json"))
      else if url.startsWith("https://www.loc.gov/item/2010593001/") then
        FakeMediaHttp.json(fx("loc-item.json").replace("2007627513", "2010593001").replace("\"1880\"", "\"1952\""))
      else if url.startsWith("https://tile.loc.gov/") then
        FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
      else Left(s"unexpected $url")
    }
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin("loc"), http, List(target(mycenae)), dryRun = false, store)
    assertEquals(r.rejected.map(_.key), List("made after 1928 (may be in copyright)"))
    assertEquals(
      store.links.toList.map(l => (l._3, l._4, l._6)),
      List(("pleiades_570491", MediaRole.Map, Some("location = mycenae; map of 1880–1880 CE")))
    )
    val a = store.saved("2007627513")._2
    assertEquals((a.license, a.kind), ("public-domain", Some(AssetKind.Map)))

  test(
    "LoC: loc.gov's bot challenge (403) ends the search with that reason — not worked around, not recorded as asked"
  ):
    val http = FakeMediaHttp(_ =>
      Right(HttpAnswer(403, Map("cf-mitigated" -> "challenge"), "Just a moment...".getBytes("UTF-8")))
    )
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin("loc"), http, List(target(mycenae), target(greece)), dryRun = false, store)
    assert(r.failed.exists(f => f.contains("HTTP 403") && f.contains("bot challenge")), r.failed.toString)
    assertEquals(http.requests.size, 1, "asked once, not retried, and not asked again for the next place")
    assert(store.checks.isEmpty)

  // ── equivalents ──────────────────────────────────────────────────────────────────────────────

  test("a name naming the place's equivalents (same item, or within same-site-km) is not ambiguous; another place is"):
    val far = PlaceFacts("other_mycenae", "Mycenae", Set("mycenae"), None, None, Some(10.0), Some(10.0))
    val r1 = Collection.resolveFor(mycenae, named, 3.0)("mycenae")
    assertEquals(r1, Set("pleiades_570491"))
    val r2 = Collection.resolveFor(mycenae, named.updated("mycenae", List(mycenae, mycenaeGn, far)), 3.0)("mycenae")
    assertEquals(r2, Set("pleiades_570491", "other_mycenae"))
    assert(Collection.distanceKm(37.73, 22.756, 37.731, 22.757) < 0.2)

  // ── roles ────────────────────────────────────────────────────────────────────────────────────

  test("the roles a collection's pictures may play are the place and event roles the host stores"):
    assertEquals(MediaCollection.Roles, MediaRole.all.toSet -- Set(MediaRole.Cover, MediaRole.Illustration))
    assertEquals(FakeMediaHttp.builtin("loc").role, MediaRole.Map)
    assertEquals(FakeMediaHttp.builtin("met").role, MediaRole.Object)
