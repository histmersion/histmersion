package com.histmersion
package server.media
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.SearchedCollection
import com.histmersion.domain.media.imports.MediaStore
import com.histmersion.domain.media.imports.CollectionImport
import com.histmersion.domain.media.imports.{BuiltinMedia, ImportService, MediaMatch}
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.domain.media.imports.PlaceFacts
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.media.assets.Known
import com.histmersion.domain.media.assets.Rights

import com.histmersion.pluginhost.HttpAnswer

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.plugin.MediaCollection
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{AssetKind, MediaCount, MediaImportReport, MediaProvider}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob
import com.histmersion.util.ocr.Pixels
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.TestPermits.given
import com.histmersion.domain.Creator
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The store as a few maps, with the gazetteer facts the matches read. */
final class CollStore(facts: Map[Place.Id, PlaceFacts], named: Map[ImportService.PlaceKey, List[PlaceFacts]])
    extends MediaStore:
  val saved = scala.collection.mutable.LinkedHashMap.empty[ProviderAsset.Key, (AssetService.AssetUid, ProviderAsset)]
  val links = scala.collection.mutable.ListBuffer.empty[
    (
        AssetService.AssetUid,
        ImportService.TargetType,
        ImportService.TargetId,
        AssetService.LinkRole,
        AssetService.LinkRank,
        Option[AssetService.LinkBasis]
    )
  ]
  val checks = scala.collection.mutable.LinkedHashMap
    .empty[(ImportService.Provider, ImportService.TargetId), ImportService.FoundCount]
  val refreshed = scala.collection.mutable.ListBuffer.empty[AssetService.AssetUid]
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def known(provider: ImportService.Provider, keys: List[AssetService.ProviderKey]) =
    val wanted = keys.map(_.value).toSet
    saved.collect {
      case (k, (uid, a)) if wanted(k.value) =>
        AssetService.ProviderKey(k.value) -> Known(Known.Uid(uid.value), Some(Known.License(a.license.value)))
    }.toMap
  def save(a: ProviderAsset, first: (ImportService.TargetType, ImportService.TargetId))(using
      WritePermit
  )(using Creator.New) =
    val existing = saved.get(a.key).map(_._1)
    val uid = existing.getOrElse(AssetService.AssetUid(f"00000000-0000-0000-0000-${saved.size + 1}%012d"))
    saved(a.key) = (uid, a)
    (uid, existing.isEmpty)
  def refreshRights(uid: AssetService.AssetUid, r: Rights)(using WritePermit) = refreshed += uid
  def link(
      uid: AssetService.AssetUid,
      tt: ImportService.TargetType,
      ti: ImportService.TargetId,
      role: AssetService.LinkRole,
      p: Option[AssetService.LinkProperty],
      q: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank
  )(using WritePermit) =
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
  )(using WritePermit) =
    links += ((uid, tt, ti, role, rank, basis)); true
  def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id)(using WritePermit) =
    false
  def checked(
      provider: ImportService.Provider,
      tt: ImportService.TargetType,
      ti: ImportService.TargetId,
      found: ImportService.FoundCount,
      qid: Option[Wikidata.Id]
  )(using WritePermit) = checks((provider, ti)) = found
  override def placeFacts(ids: List[Place.Id]) = facts.view.filterKeys(ids.toSet).toMap
  override def mapPlacesNamed(keys: List[ImportService.PlaceKey]) = named.view.filterKeys(keys.toSet).toMap

/** The collections (#1446) end to end against FAKE providers (trimmed real answers; loc.gov's follow its documented
  * shape) and an in-memory store: each first-party plugin's collection, as the host builds it, run by the host's
  * collection import — what a dry run lists for a person to judge, what is refused and why, what a real run stores,
  * links (with the match's reason) and records as asked — and that no request leaves this machine.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CollectionsSuite extends munit.FunSuite:

  private val jpeg = Array[Byte](-1, -40, -1)

  final private class ThumbConverter extends ConverterClient:
    val calls = scala.collection.mutable.ListBuffer.empty[ConverterClient.Filename]
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def thumbnail(
        bytes: ConverterClient.DocumentBytes,
        filename: ConverterClient.Filename,
        maxPx: Pixels
    ): Either[Problem, ConverterClient.Thumb] =
      calls += filename
      Right(
        ConverterClient.Thumb(
          ConverterClient.Thumb.Bytes(jpeg ++ Array[Byte](42)),
          ConverterClient.Thumb.Mime("image/jpeg"),
          ConverterClient.Thumb.Width(330),
          ConverterClient.Thumb.Height(250)
        )
      )

  private def fx(name: String) = FakeMediaHttp.fixture(name)

  private val mycenae = PlaceFacts(
    PlaceFacts.Id("pleiades_570491"),
    PlaceFacts.Label("Mycenae"),
    Set(PlaceFacts.Keys("mycenae")),
    Some((DeepTime.SignedYear(-1600L), DeepTime.SignedYear(-1100L))),
    Some(Target.Qid("Q132564")),
    Some(Latitude(37.73)),
    Some(Longitude(22.756))
  )
  private val mycenaeGn =
    PlaceFacts(
      PlaceFacts.Id("geonames_mycenae"),
      PlaceFacts.Label("Mykines"),
      Set(PlaceFacts.Keys("mycenae"), PlaceFacts.Keys("mykines")),
      None,
      None,
      Some(Latitude(37.731)),
      Some(Longitude(22.757))
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
  private val cyprus =
    PlaceFacts(
      PlaceFacts.Id("geonames_146669"),
      PlaceFacts.Label("Cyprus"),
      Set(PlaceFacts.Keys("cyprus")),
      Some((DeepTime.SignedYear(-1200L), DeepTime.SignedYear(-300L))),
      Some(Target.Qid("Q229")),
      Some(Latitude(35.0)),
      Some(Longitude(33.0))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val facts = List(mycenae, greece, cyprus).map(f => Place.Id.assume(f.id.value) -> f).toMap
  // Pleiades' and GeoNames' Mycenae are one site (3 km): the name resolves to it once
  private val named = Map(
    ImportService.PlaceKey("mycenae") -> List(mycenae, mycenaeGn),
    ImportService.PlaceKey("greece") -> List(greece),
    ImportService.PlaceKey("cyprus") -> List(cyprus)
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def target(f: PlaceFacts) =
    Target(Target.TargetType("place"), Target.Id(f.id.value), Target.Qid(""), Target.Label(f.label.value))

  private val cfg = FakeMediaHttp.cfg

  private val Met = BuiltinMedia.EntryName("met")
  private val Aic = BuiltinMedia.EntryName("aic")
  private val Cleveland = BuiltinMedia.EntryName("cleveland")
  private val Loc = BuiltinMedia.EntryName("loc")
  private val DryOn = CollectionImport.DryRun(true)
  private val DryOff = CollectionImport.DryRun(false)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def run(
      col: SearchedCollection,
      http: FakeMediaHttp,
      targets: List[Target],
      dryRun: CollectionImport.DryRun,
      store: CollStore = CollStore(facts, named),
      conv: ThumbConverter = ThumbConverter()
  ): MediaImportReport =
    val (polite, _) = FakeMediaHttp.polite(
      http,
      cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(col.conf.requestIntervalMs.value))
    )
    CollectionImport(col, polite, cfg, conv).run(targets, dryRun, refresh = CollectionImport.Refresh(false), store)

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
    else if url.contains("/v1/objects/") then
      Right(HttpAnswer(HttpAnswer.Code(404), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))
    else if url.startsWith("https://images.metmuseum.org/") then
      FakeMediaHttp.bytes(jpeg ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  test("Met dry run: the matches with their reasons, the refusals by reason, the licences — nothing stored"):
    val http = met()
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin(Met), http, List(target(mycenae)), dryRun = DryOn, store)
    assertEquals(r.provider, MediaProvider.Met)
    assertEquals((r.targets, r.items, r.statements, r.files), (1, 3, 1, 1))
    assertEquals(
      r.samples.map(s => (s.targetId, s.key, s.license, s.basis)),
      List(("pleiades_570491", "252448", "CC0-1.0", "excavation = Mycenae; 1400–1201 BCE within 1600–1100 BCE"))
    )
    assertEquals(
      r.rejected.map(_.key.value).sorted,
      List("names another place on the map", "not Open Access (isPublicDomain is false)")
    )
    assertEquals(
      r.byLicence,
      List(
        MediaCount(MediaCount.Key("CC0-1.0"), MediaCount.N(2)),
        MediaCount(MediaCount.Key("not open access"), MediaCount.N(1), allowed = MediaCount.Allowed(false))
      )
    )
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
      else if url.endsWith("/v1/objects/248284") then
        Right(HttpAnswer(HttpAnswer.Code(403), Map.empty, HttpAnswer.Body("Forbidden".getBytes("UTF-8"))))
      else if url.contains("/v1/objects/") then metObject(252448)
      else Left(s"unexpected $url")
    }
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin(Met), http, List(target(mycenae), target(greece)), dryRun = DryOff, store)
    assert(
      r.failed.exists(_.value.contains("refused the request; the rest of this batch was not asked")),
      r.failed.toString
    )
    assertEquals(http.requests.count(_._1.contains("/v1.1/search?")), 1, "Greece was not searched")
    assert(store.checks.isEmpty && store.saved.isEmpty, "nothing recorded: the next run asks both again")

  test(
    "Met real run: the object stored with its CC0 licence, credit, page and original; a thumbnail made here; the link keeps the reason"
  ):
    val store = CollStore(facts, named)
    val conv = ThumbConverter()
    val r = run(FakeMediaHttp.builtin(Met), met(), List(target(mycenae)), dryRun = DryOff, store, conv)
    assertEquals((r.stored, r.linked), (1, 1))
    val (uid, a) = store.saved(ProviderAsset.Key("252448"))
    assertEquals(a.provider, MediaProvider.Met)
    assertEquals(a.license, "CC0-1.0")
    assertEquals(a.credit, "Terracotta animal figurine, The Metropolitan Museum of Art, CC0")
    assertEquals(a.sourceUrl, "https://www.metmuseum.org/art/collection/search/252448")
    assertEquals(a.originalUrl, Some("https://images.metmuseum.org/CRDImages/gr/original/DP121403.jpg"))
    assert(new String(a.bytes.value, "UTF-8").contains("web-large"), "the museum's web rendition, not the original")
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
    val http =
      FakeMediaHttp(_ => Right(HttpAnswer(HttpAnswer.Code(500), Map.empty, HttpAnswer.Body(Array.emptyByteArray))))
    val c = cfg.modify(_.maxRetries).setTo(MediaConfig.MaxRetries(0))
    val (polite, _) = FakeMediaHttp.polite(http, c)
    val r = CollectionImport(FakeMediaHttp.builtin(Met), polite, c, ThumbConverter()).run(
      List(target(mycenae)),
      CollectionImport.DryRun(false),
      CollectionImport.Refresh(false),
      store
    )
    assert(r.failed.exists(_.value.contains("search for pleiades_570491")), r.failed.toString)
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
    val dry = run(FakeMediaHttp.builtin(Aic), aic(), List(target(cyprus)), dryRun = DryOn, store, conv)
    assertEquals(
      dry.samples.map(s => (s.key, s.basis)),
      List(("108896", "place_of_origin = Cyprus; 500–401 BCE within 1200–300 BCE"))
    )
    assert(dry.rejected.exists(_.key.value == "its dates do not overlap the place's"), dry.rejected.toString)
    assert(dry.rejected.exists(_.key.value == "not Open Access (is_public_domain is false)"), dry.rejected.toString)
    val r = run(FakeMediaHttp.builtin(Aic), aic(), List(target(cyprus)), dryRun = DryOff, store, conv)
    assertEquals(r.stored, 1)
    val a = store.saved(ProviderAsset.Key("108896"))._2
    assert(new String(a.bytes.value, "UTF-8").contains("/full/843,/0/default.jpg"))
    assert(a.thumb.exists(t => new String(t.value, "UTF-8").contains("/full/330,/0/default.jpg")))
    assert(conv.calls.isEmpty)

  test("Cleveland: CC0 records whose culture names the place, of its period"):
    val http = FakeMediaHttp { url =>
      if url.startsWith("https://openaccess-api.clevelandart.org/api/artworks/?") then
        FakeMediaHttp.json(fx("cma-search.json"))
      else Left(s"unexpected $url")
    }
    val r = run(FakeMediaHttp.builtin(Cleveland), http, List(target(cyprus)), dryRun = DryOn)
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
    val r = run(FakeMediaHttp.builtin(Loc), http, List(target(mycenae)), dryRun = DryOff, store)
    assertEquals(r.rejected.map(_.key), List("media-loc (rejected): made after 1928 (may be in copyright)"))
    assertEquals(
      store.links.toList.map(l => (l._3, l._4, l._6)),
      List(("pleiades_570491", MediaRole.Map, Some("location = mycenae; map of 1880–1880 CE")))
    )
    val a = store.saved(ProviderAsset.Key("2007627513"))._2
    assertEquals((a.license, a.kind), ("public-domain", Some(AssetKind.Map)))

  test(
    "LoC: loc.gov's bot challenge (403) ends the search with that reason — not worked around, not recorded as asked"
  ):
    val http = FakeMediaHttp(_ =>
      Right(
        HttpAnswer(
          HttpAnswer.Code(403),
          Map(CoreQuota.HeaderName("cf-mitigated") -> CoreQuota.HeaderValue("challenge")),
          HttpAnswer.Body("Just a moment...".getBytes("UTF-8"))
        )
      )
    )
    val store = CollStore(facts, named)
    val r = run(FakeMediaHttp.builtin(Loc), http, List(target(mycenae), target(greece)), dryRun = DryOff, store)
    assert(
      r.failed.exists(f => f.value.contains("HTTP 403") && f.value.contains("bot-protection challenge")),
      r.failed.toString
    )
    assertEquals(http.requests.size, 1, "asked once, not retried, and not asked again for the next place")
    assert(store.checks.isEmpty)

  // ── equivalents ──────────────────────────────────────────────────────────────────────────────

  test("a name naming the place's equivalents (same item, or within same-site-km) is not ambiguous; another place is"):
    val far = PlaceFacts(
      PlaceFacts.Id("other_mycenae"),
      PlaceFacts.Label("Mycenae"),
      Set(PlaceFacts.Keys("mycenae")),
      None,
      None,
      Some(Latitude(10.0)),
      Some(Longitude(10.0))
    )
    val keyed = named.map((k, v) => MediaMatch.PlaceInfo.Keys(k.value) -> v)
    val r1 =
      SearchedCollection.resolveFor(mycenae, keyed, MediaConfig.SameSiteKm(3.0))(MediaMatch.PlaceInfo.Keys("mycenae"))
    assertEquals(r1, Set("pleiades_570491"))
    val r2 =
      SearchedCollection.resolveFor(
        mycenae,
        keyed.updated(MediaMatch.PlaceInfo.Keys("mycenae"), List(mycenae, mycenaeGn, far)),
        MediaConfig.SameSiteKm(3.0)
      )(MediaMatch.PlaceInfo.Keys("mycenae"))
    assertEquals(r2, Set("pleiades_570491", "other_mycenae"))
    assert(
      SearchedCollection.distanceKm(Latitude(37.73), Longitude(22.756), Latitude(37.731), Longitude(22.757)).value < 0.2
    )

  // ── roles ────────────────────────────────────────────────────────────────────────────────────

  test("the roles a collection's pictures may play are the place and event roles the host stores"):
    assertEquals(
      MediaCollection.Roles.map(_.value: String),
      MediaRole.all.map(_.value: String).toSet -- Set(MediaRole.Cover, MediaRole.Illustration)
    )
    assertEquals(FakeMediaHttp.builtin(Loc).role, MediaRole.Map)
    assertEquals(FakeMediaHttp.builtin(Met).role, MediaRole.Object)
