package com.histmersion.db

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.links.AssetLinkFixtures
import com.histmersion.domain.media.imports.ImportFixtures
import com.histmersion.domain.media.covers.CoverFixtures
import com.histmersion.domain.media.figures.FigureFixtures
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.media.imports.Target

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{Api, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{EventPatch, EventPut, Period, Place}
import com.histmersion.api.media.{AssetKind, MediaProvider, MediaTarget, MediaThumb}
import com.histmersion.api.media.AssetLink
import com.histmersion.util.database.given

/** Pictures from outside providers (V082, #1446) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise):
  * the reshaped `assets` key (a source's figures keep `(citekey, name)` and their links; a provider's picture has no
  * citekey), storing and linking a provider's picture with its point and period, the cards' thumbnails, which places
  * and events an import asks about, the list, facets and media-layer filters, the figure-licence fill and the
  * `media-licence` lint rule. Fixtures: ids and citekeys start with `zqmd`, at 130°E 40°S.
  */
class MediaRepoSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val P = MediaProvider.Commons
  private val Lic = "CC-BY-SA-9.9" // a licence no other suite stores, so the filters can be checked exactly

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider_key LIKE 'File:Zqmd%' OR citekey LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM finding_places WHERE place_id LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM findings WHERE citekey LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqmd%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqmd%'".update.run()

  private def pt(lon: Double) = Some(s"""{"type":"Point","coordinates":[$lon,-40]}""")

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              "zqmd_site",
              "Zqmd site",
              com.histmersion.domain.catalogue.Place.Kind.Site,
              pt(130.0),
              Some(Period(-1600, -1100))
            ),
            List(Place.Name("Zqmd site"))
          ),
          Place.Input(
            Place("zqmd_busy", "Zqmd busy", com.histmersion.domain.catalogue.Place.Kind.Site, pt(131.0)),
            List(Place.Name("Zqmd busy"))
          ),
          Place.Input(
            Place("zqmd_nolink", "Zqmd no link", com.histmersion.domain.catalogue.Place.Kind.Site, pt(132.0)),
            List(Place.Name("Zqmd no link"))
          )
        )
      )
      connect(ds):
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref) VALUES
              ('zqmd_site', 'wikidata', 'Q90000001', 'exact_match', 'test', 'zqmd'),
              ('zqmd_site', 'wikidata', 'Q90000009', 'close_match', 'test', 'zqmd'),
              ('zqmd_busy', 'wikidata', 'Q90000002', 'exact_match', 'test', 'zqmd')""".update.run()
        sql"""INSERT INTO sources (citekey, title, license) VALUES ('zqmd2020_figs', 'Zqmd figures', 'CC-BY-4.0'),
              ('zqmd2021_nolic', 'Zqmd no licence', NULL)""".update.run()
        sql"""INSERT INTO findings (id, citekey, claim) VALUES ('zqmd2020_figs#1', 'zqmd2020_figs', 'a'),
              ('zqmd2020_figs#2', 'zqmd2020_figs', 'b'), ('zqmd2020_figs#3', 'zqmd2020_figs', 'c')""".update.run()
        sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zqmd2020_figs#1', 'zqmd_busy'),
              ('zqmd2020_figs#2', 'zqmd_busy'), ('zqmd2020_figs#3', 'zqmd_site'),
              ('zqmd2020_figs#3', 'zqmd_nolink')""".update.run()
        ()
      EventFixtures.put(
        ds,
        "zqmd_battle",
        EventPut("Zqmd battle", "battle", TimeSpan(Some(-1200), None, None, Some(-1190)), placeId = Some("zqmd_site"))
      )
      val _ = EventFixtures.patch(ds, "zqmd_battle", EventPatch(wikidata = Some("Q90000003")))
      val png = Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10)
      val _ = AssetFixtures.add(
        ds,
        "zqmd2020_figs",
        List(
          NewFigure("fig1.png", "image/png", png ++ Array[Byte](1), None, None),
          NewFigure("fig2.png", "image/png", png ++ Array[Byte](2), None, None)
        )
      )
      val _ = AssetFixtures.add(
        ds,
        "zqmd2021_nolic",
        List(NewFigure("fig.png", "image/png", png ++ Array[Byte](3), None, None))
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def asset(
      key: String,
      license: String = Lic,
      credit: String = "Ann, CC BY-SA 9.9, via Wikimedia Commons",
      thumb: Boolean = true
  ) =
    ProviderAsset(
      P,
      key,
      key.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_"),
      "image/jpeg",
      Array[Byte](-1, -40, 1),
      Some(1280),
      Some(960),
      Option.when(thumb)(Array[Byte](-1, -40, 2)),
      Option.when(thumb)("image/jpeg"),
      Some(330),
      Some(248),
      license,
      Some("https://creativecommons.org/licenses/by-sa/9.9"),
      Some("Ann"),
      credit,
      s"https://commons.wikimedia.org/wiki/${key.replace(' ', '_')}",
      Some("https://upload.example/x.jpg"),
      Some(AssetKind.Photo)
    )

  test("V082: a provider's picture has no citekey; exactly one of citekey and provider is set"):
    val (uid, inserted) = AssetFixtures.save(ds, asset("File:Zqmd one.jpg"), ("place", "zqmd_site"))
    assert(inserted)
    val row = connect(ds)(
      sql"SELECT citekey, provider FROM assets WHERE uid = $uid::uuid"
        .query[(Option[String], Option[String])]
        .run()
        .head
    )
    assertEquals(row, (None, Some(P)))
    val both = intercept[Exception](
      connect(ds)(sql"UPDATE assets SET citekey = 'zqmd2020_figs' WHERE uid = $uid::uuid".update.run())
    )
    assert(both.getMessage.contains("assets_one_owner"), both.getMessage)
    val neither = intercept[Exception](
      connect(ds)(
        sql"""INSERT INTO assets (name, mime, bytes, sha256) VALUES ('x', 'image/png', decode('00', 'hex'), 'x')""".update
          .run()
      )
    )
    assert(neither.getMessage.contains("assets_one_owner"), neither.getMessage)

  test("V082: a source's figures keep their (citekey, name) key, their links and the re-conversion path"):
    val link = AssetLinkFixtures.create(ds, AssetLink.Put("zqmd2020_figs", "fig1.png", "depicts", "place", "zqmd_site"))
    assert(link.isRight, link.toString)
    val n = AssetFixtures.replace(
      ds,
      "zqmd2020_figs",
      List(
        NewFigure("fig1.png", "image/png", Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 1), None, None),
        NewFigure("fig2.png", "image/png", Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 2), None, None)
      )
    )
    assertEquals(n, 2)
    assertEquals(AssetLinkFixtures.list(ds, Some("zqmd2020_figs"), None, None, None).total, 1, "the link survived")
    assertEquals(AssetFixtures.list(ds, "zqmd2020_figs").map(_.name), Vector("fig1.png", "fig2.png"))

  test("save: a second save of the same key replaces it in place; point and period come from the place"):
    val (uid1, _) = AssetFixtures.save(ds, asset("File:Zqmd two.jpg"), ("place", "zqmd_site"))
    val (uid2, inserted) = AssetFixtures.save(
      ds,
      asset("File:Zqmd two.jpg", credit = "Bob, CC BY-SA 9.9, via Wikimedia Commons"),
      ("place", "zqmd_busy")
    )
    assertEquals(uid2, uid1)
    assert(!inserted)
    val (lon, lo, hi, credit) = connect(ds)(sql"""SELECT ST_X(geom), lower(period), upper(period) - 1, credit
          FROM assets WHERE uid = $uid1::uuid""".query[(Double, Option[Long], Option[Long], String)].run().head)
    assertEqualsDouble(lon, 130.0, 1e-9)
    assertEquals((lo, hi), (Some(-1600L), Some(-1100L)))
    assertEquals(credit, "Bob, CC BY-SA 9.9, via Wikimedia Commons")
    assertEquals(
      AssetFixtures.known(ds, P, List("File:Zqmd two.jpg", "File:Zqmd nothing.jpg")).keySet,
      Set("File:Zqmd two.jpg")
    )

  test("links and the card: the thumbnails of a place, best role first, only ones with a thumbnail"):
    val (img, _) = AssetFixtures.save(ds, asset("File:Zqmd image.jpg"), ("place", "zqmd_busy"))
    val (map, _) = AssetFixtures.save(ds, asset("File:Zqmd map.svg"), ("place", "zqmd_busy"))
    val (bare, _) = AssetFixtures.save(ds, asset("File:Zqmd bare.jpg", thumb = false), ("place", "zqmd_busy"))
    assert(AssetFixtures.link(ds, map, "place", "zqmd_busy", MediaRole.LocatorMap, Some("P242"), Some("Q90000002"), 0))
    assert(AssetFixtures.link(ds, img, "place", "zqmd_busy", MediaRole.Image, Some("P18"), Some("Q90000002"), 0))
    assert(AssetFixtures.link(ds, bare, "place", "zqmd_busy", MediaRole.Image, Some("P18"), Some("Q90000002"), 1))
    assert(
      !AssetFixtures.link(ds, img, "place", "zqmd_busy", MediaRole.Image, Some("P18"), Some("Q90000002"), 0),
      "not new"
    )
    val thumbs = AssetFixtures.thumbs(ds, "place", "zqmd_busy")
    assertEquals(thumbs.map(_.uid), List(img, map))
    val t = thumbs.head
    assertEquals(
      t,
      MediaThumb(
        img,
        "image",
        s"/media/$img/thumb",
        "Ann, CC BY-SA 9.9, via Wikimedia Commons",
        Some(Lic),
        Some("https://creativecommons.org/licenses/by-sa/9.9"),
        Some("https://commons.wikimedia.org/wiki/File:Zqmd_image.jpg"),
        Some(330),
        Some(248)
      )
    )
    assertEquals(AssetFixtures.thumb(ds, img).map(_._1), Some("image/jpeg"))
    assertEquals(AssetFixtures.thumb(ds, bare), None)
    assertEquals(AssetFixtures.file(ds, bare).map(_._2.toList), Some(List[Byte](-1, -40, 1)))

  test("V094: depiction, object and map roles; a link keeps its basis; a picture in two roles is on the card once"):
    val (uid, _) = AssetFixtures.save(ds, asset("File:Zqmd depicted.jpg"), ("event", "zqmd_battle"))
    assert(AssetFixtures.link(ds, uid, "event", "zqmd_battle", MediaRole.Depiction, Some("P180"), Some("Q90000003"), 0))
    assert(AssetFixtures.link(ds, uid, "event", "zqmd_battle", MediaRole.Image, Some("P18"), Some("Q90000003"), 0))
    assertEquals(
      AssetFixtures.thumbs(ds, "event", "zqmd_battle").filter(_.uid == uid).map(_.role),
      List(MediaRole.Image),
      "once, in its best role"
    )
    val (obj, _) = AssetFixtures.save(ds, asset("File:Zqmd object.jpg"), ("place", "zqmd_site"))
    assert(
      AssetFixtures.link(ds, obj, "place", "zqmd_site", MediaRole.Object, None, None, 0, Some("find_spot = Zqmd site"))
    )
    assert(
      !AssetFixtures.link(ds, obj, "place", "zqmd_site", MediaRole.Object, None, None, 0),
      "not new; the basis kept"
    )
    assert(
      AssetFixtures.link(ds, obj, "place", "zqmd_busy", MediaRole.Map, None, None, 0, Some("location = zqmd busy"))
    )
    val targets = AssetFixtures.show(ds, obj).get.targets
    assertEquals(targets.filter(_.targetId == "zqmd_site").map(_.basis), List(Some("find_spot = Zqmd site")))
    assertEquals(ProviderAsset.kindOf(MediaRole.Map), AssetKind.Map)
    connect(ds)(sql"DELETE FROM media_links WHERE asset_uid IN ($uid::uuid, $obj::uuid)".update.run())

  test("an event's card: its Wikidata item and its picture"):
    val (uid, _) = AssetFixtures.save(ds, asset("File:Zqmd battle.jpg"), ("event", "zqmd_battle"))
    AssetFixtures.link(ds, uid, "event", "zqmd_battle", MediaRole.Image, Some("P18"), Some("Q90000003"), 0)
    val d = EventFixtures.get(ds, "zqmd_battle").get
    assertEquals(d.wikidata, Some("Q90000003"))
    assertEquals(
      AssetFixtures.thumbs(ds, ImportFixtures.Event, "zqmd_battle").map(_.uid),
      List(uid),
      "its card's picture"
    )
    val refused =
      intercept[Exception](connect(ds)(sql"UPDATE events SET wikidata = 'x1' WHERE id = 'zqmd_battle'".update.run()))
    assert(refused.getMessage.contains("events_wikidata_check"), refused.getMessage)
    val _ = EventFixtures.patch(ds, "zqmd_battle", EventPatch(wikidata = Some("")))
    assertEquals(EventFixtures.get(ds, "zqmd_battle").get.wikidata, None, "\"\" clears it")
    val _ = EventFixtures.patch(ds, "zqmd_battle", EventPatch(wikidata = Some("Q90000003")))

  test("targets: one place by its exact Wikidata link; one with none or unknown is an error"):
    assertEquals(
      ImportFixtures.targets(ds, P, Some("zqmd_site"), None, 10, refresh = false),
      Right(List(Target("place", "zqmd_site", "Q90000001", "Zqmd site")))
    )
    assert(
      ImportFixtures
        .targets(ds, P, Some("zqmd_nolink"), None, 10, refresh = false)
        .left
        .exists(_.contains("no Wikidata link"))
    )
    assert(
      ImportFixtures.targets(ds, P, Some("zqmd_nope"), None, 10, refresh = false).left.exists(_.contains("No place"))
    )
    assertEquals(
      ImportFixtures.targets(ds, P, None, Some("zqmd_battle"), 10, refresh = false).map(_.map(_.qid)),
      Right(List("Q90000003"))
    )

  test("targets by default: events with an item, then places with findings by count; asked ones skipped"):
    def mine(refresh: Boolean) = ImportFixtures
      .targets(ds, P, None, None, 100000, refresh)
      .toOption
      .get
      .filter(_.id.startsWith("zqmd"))
      .map(t => (t.targetType, t.id, t.qid))
    assertEquals(
      mine(refresh = false),
      List(
        ("event", "zqmd_battle", "Q90000003"),
        ("place", "zqmd_busy", "Q90000002"),
        ("place", "zqmd_site", "Q90000001")
      )
    )
    ImportFixtures.checked(ds, P, "place", "zqmd_busy", 2)
    assertEquals(mine(refresh = false).map(_._2), List("zqmd_battle", "zqmd_site"), "asked already: skipped")
    assertEquals(mine(refresh = true).map(_._2), List("zqmd_battle", "zqmd_busy", "zqmd_site"), "unless refresh")
    // #1567: a refresh re-asks every one, also one checked within the same millisecond (it used to
    // compare this process's whole milliseconds with the check's microseconds, and skip it ~1 time in 10)
    (1 to 50).foreach { i =>
      ImportFixtures.checked(ds, P, "place", "zqmd_busy", 2)
      assertEquals(
        mine(refresh = true).map(_._2),
        List("zqmd_battle", "zqmd_busy", "zqmd_site"),
        s"refresh right after a check ($i)"
      )
    }
    ImportFixtures.checked(ds, P, "place", "zqmd_busy", 3)
    assertEquals(
      connect(ds)(sql"SELECT found FROM media_checks WHERE target_id = 'zqmd_busy'".query[Int].run()),
      Vector(3)
    )
    connect(ds)(sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmd%'".update.run())

  test("a place's Commons category is a link of the place, once"):
    assert(ImportFixtures.commonsCategory(ds, "zqmd_site", "Zqmd category", "Q90000001"))
    assert(!ImportFixtures.commonsCategory(ds, "zqmd_site", "Zqmd category", "Q90000001"))
    assert(
      PlaceFixtures
        .get(ds, "zqmd_site", None, cfg.places.modernFrom)
        .get
        .links
        .exists(l => l.scheme == "commons" && l.externalId == "Zqmd category")
    )

  test("list, show and facets: by provider, licence, place; source figures as `source`"):
    val (uid, _) = AssetFixtures.save(ds, asset("File:Zqmd listed.jpg"), ("place", "zqmd_site"))
    AssetFixtures.link(ds, uid, "place", "zqmd_site", MediaRole.Image, Some("P18"), Some("Q90000001"), 0)
    val l =
      AssetFixtures.list(ds, Api.MediaQuery(provider = Some(P), license = Some(Lic), place = Some("zqmd_site")), 50)
    assertEquals(l.items.map(_.providerKey), List(Some("File:Zqmd listed.jpg")))
    assertEquals(l.total, 1)
    val figs = AssetFixtures
      .list(ds, Api.MediaQuery(provider = Some("source"), q = Some("fig"), limit = None), 1000)
      .items
      .filter(_.citekey.exists(_.startsWith("zqmd")))
    assertEquals(
      figs.map(f => (f.citekey, f.name)).sorted,
      List(
        (Some("zqmd2020_figs"), "fig1.png"),
        (Some("zqmd2020_figs"), "fig2.png"),
        (Some("zqmd2021_nolic"), "fig.png")
      )
    )
    val shown = AssetFixtures.show(ds, uid).get
    assertEquals(shown.targets, List(MediaTarget("place", "zqmd_site", "image", Some("P18"), Some("Q90000001"))))
    assertEquals((shown.fileUrl, shown.thumbUrl), (s"/media/$uid/file", Some(s"/media/$uid/thumb")))
    assert(shown.retrievedAt.exists(_.endsWith("Z")))
    val f = AssetFixtures.facets(ds)
    assert(
      f.providers.exists(c => c.key == P && c.n >= 1) && f.providers.exists(_.key == MediaProvider.Source),
      f.toString
    )
    assert(f.licences.exists(c => c.key == Lic && c.n >= 1), f.toString)

  test("the media layer: provider and licence filters, a provider's thumbnail and credit on the hit"):
    val q = Api.MediaGeoQuery(provider = Some(P), license = Some(Lic), limit = Some(1000))
    val hits = AssetFixtures.geo(ds, q, 1000)
    assert(hits.nonEmpty && hits.forall(h => h.provider.contains(P) && h.license.contains(Lic) && h.citekey.isEmpty))
    assert(hits.exists(h => h.thumbUrl.contains(s"/media/${h.uid}/thumb") && h.credit.isDefined))
    // what a full-size view shows: the stored rendition (this server's), the provider's page, the licence's text
    assert(
      hits.forall(h => h.fileUrl.contains(s"/media/${h.uid}/file") && h.sourceUrl.exists(_.startsWith("http"))),
      hits.toString
    )
    assert(
      hits.exists(h =>
        h.licenseUrl.contains("https://creativecommons.org/licenses/by-sa/9.9") && h.author.contains("Ann")
      ),
      hits.toString
    )
    assertEquals(AssetFixtures.geo(ds, q.copy(provider = Some("source")), 1000).filter(_.license.contains(Lic)), Nil)
    val dated = AssetFixtures.geo(ds, q.copy(from = Some(-1500), to = Some(-1400)), 1000)
    assert(dated.nonEmpty && dated.forall(_.periodStart.exists(_ <= -1400)), "the place's period dates the picture")
    assert(!dated.exists(_.assetName == "Zqmd_image.jpg"), "a place with no dates gives its picture none")

  test("fill-licences: a figure takes its source's licence — counted first, written only with apply"):
    val dry = FigureFixtures.fillLicences(ds, apply = false)
    assertEquals(dry.bySource.find(_.key == "zqmd2020_figs").map(_.n), Some(2))
    assert(!dry.bySource.exists(_.key == "zqmd2021_nolic"), "a source with no licence gives none")
    assertEquals(
      connect(ds)(
        sql"SELECT count(*)::int FROM assets WHERE citekey = 'zqmd2020_figs' AND license IS NOT NULL"
          .query[Int]
          .run()
          .head
      ),
      0,
      "a dry run writes nothing"
    )
    val done = FigureFixtures.fillLicences(ds, apply = true)
    assert(done.applied && done.updated >= 2)
    assertEquals(
      connect(ds)(
        sql"SELECT DISTINCT license FROM assets WHERE citekey = 'zqmd2020_figs'"
          .query[Option[String]]
          .run()
          .toList
      ),
      List(Some("CC-BY-4.0"))
    )

  test("lint media-licence: a figure with no licence, a provider's picture with no credit line"):
    val (uid, _) = AssetFixtures.save(ds, asset("File:Zqmd uncredited.jpg", credit = " "), ("place", "zqmd_site"))
    val items = AssetFixtures.licenceGaps(ds).toMap
    assertEquals(items.get("zqmd2021_nolic/fig.png").map(_.startsWith("no licence")), Some(true))
    assertEquals(items.get(s"media:$uid").map(_.startsWith("no credit line")), Some(true))
    assert(!items.contains("zqmd2020_figs/fig1.png"), "a figure with its source's licence is fine")

  // ── #1446 second slice: equivalents, items, sources' pictures ──────────────────────────────────

  private def eqFixtures(): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place("zqmd_eq_a", "Zqmd Gobekli Tepe", com.histmersion.domain.catalogue.Place.Kind.Site, pt(133.0)),
          List(Place.Name("Zqmd Gobekli Tepe"))
        ),
        // another gazetteer's point of the same site: the same name (the key folds ö and case), 850 m away
        Place.Input(
          Place("zqmd_eq_b", "Zqmd Göbekli tepe", com.histmersion.domain.catalogue.Place.Kind.Site, pt(133.01)),
          List(Place.Name("Zqmd Göbekli tepe"))
        ),
        // the same name 700 km away is another place
        Place.Input(
          Place("zqmd_eq_far", "Zqmd Gobekli Tepe", com.histmersion.domain.catalogue.Place.Kind.Site, pt(141.0)),
          List(Place.Name("Zqmd Gobekli Tepe"))
        ),
        // another name, the same Wikidata item (a polity's other time slice, a Wikidata import)
        Place.Input(
          Place("zqmd_eq_wd", "Zqmd Other", com.histmersion.domain.catalogue.Place.Kind.Site, pt(135.0)),
          List(Place.Name("Zqmd Other"))
        )
      )
    )
    connect(ds):
      sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref) VALUES
            ('zqmd_eq_a', 'wikidata', 'Q90000011', 'exact_match', 'test', 'zqmd'),
            ('zqmd_eq_wd', 'wikidata', 'Q90000011', 'exact_match', 'test', 'zqmd')
            ON CONFLICT DO NOTHING""".update.run()
      sql"INSERT INTO place_aliases (alias, place_id) VALUES ('zqmd_eq_old', 'zqmd_eq_b') ON CONFLICT DO NOTHING".update
        .run()
      ()

  test("a place's card reaches the pictures of its equivalents: same item, an old id, the same site's other point"):
    eqFixtures()
    val (photo, _) = AssetFixtures.save(ds, asset("File:Zqmd gobekli.jpg"), ("place", "zqmd_eq_a"))
    AssetFixtures.link(ds, photo, "place", "zqmd_eq_a", MediaRole.Image, Some("P18"), Some("Q90000011"), 0)
    val (plan, _) = AssetFixtures.save(ds, asset("File:Zqmd gobekli plan.jpg"), ("place", "zqmd_eq_a"))
    // stored under an id that was later merged into zqmd_eq_b (its alias), from no item
    AssetFixtures.link(ds, plan, "place", "zqmd_eq_old", MediaRole.Plan, None, None, 0)
    def uids(id: String) = AssetFixtures.placeThumbs(ds, id, cfg.media.sameSiteKm).map(_.uid)
    assertEquals(uids("zqmd_eq_a"), List(photo), "its own; the plan is the old id's, not an equivalent of a")
    assertEquals(uids("zqmd_eq_b"), List(photo, plan), "the same site's other point, and its own old id")
    assertEquals(uids("zqmd_eq_wd"), List(photo), "the same Wikidata item")
    assertEquals(uids("zqmd_eq_far"), Nil, "the same name far away is another place")
    assertEquals(
      AssetFixtures.placeThumbs(ds, "zqmd_eq_b", 0).map(_.uid),
      List(plan),
      "no same-site radius: the alias only"
    )
    assertEquals(AssetFixtures.thumbs(ds, "place", "zqmd_eq_b"), Nil, "the plain lookup stays exact")

  test("a place's own picture is shown before an equivalent's in the same role"):
    val (own, _) = AssetFixtures.save(ds, asset("File:Zqmd gobekli own.jpg"), ("place", "zqmd_eq_wd"))
    AssetFixtures.link(ds, own, "place", "zqmd_eq_wd", MediaRole.Image, Some("P18"), Some("Q90000011"), 1)
    assertEquals(AssetFixtures.placeThumbs(ds, "zqmd_eq_wd", cfg.media.sameSiteKm).map(_.uid).headOption, Some(own))

  test("targets: one per Wikidata item, the place most sources cite; an item asked through any place is done"):
    connect(ds):
      sql"""INSERT INTO findings (id, citekey, claim) VALUES ('zqmd2020_figs#11', 'zqmd2020_figs', 'x'),
            ('zqmd2020_figs#12', 'zqmd2020_figs', 'y'), ('zqmd2021_nolic#1', 'zqmd2021_nolic', 'z')
            ON CONFLICT DO NOTHING""".update.run()
      sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zqmd2020_figs#11', 'zqmd_eq_a'),
            ('zqmd2020_figs#12', 'zqmd_eq_wd'), ('zqmd2021_nolic#1', 'zqmd_eq_wd') ON CONFLICT DO NOTHING""".update
        .run()
      ()
    def mine() = ImportFixtures
      .targets(ds, P, None, None, 100000, refresh = false)
      .toOption
      .get
      .filter(_.id.startsWith("zqmd_eq"))
      .map(t => (t.id, t.qid))
    assertEquals(mine(), List(("zqmd_eq_wd", "Q90000011")), "two sources cite zqmd_eq_wd, one zqmd_eq_a")
    val before = ImportFixtures.pending(ds, P)
    ImportFixtures.checked(ds, P, "place", "zqmd_eq_a", 1, Some("Q90000011"))
    assertEquals(mine(), Nil, "the item was asked about through zqmd_eq_a")
    assertEquals(ImportFixtures.pending(ds, P), before - 1)
    val since = System.currentTimeMillis() + 60000
    assertEquals(
      ImportFixtures
        .targets(ds, P, None, None, 100000, refresh = true, Some(since))
        .toOption
        .get
        .filter(_.id.startsWith("zqmd_eq"))
        .map(_.id),
      List("zqmd_eq_wd"),
      "a refresh job: not asked since its start"
    )
    connect(ds)(sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmd%'".update.run())

  test("V084: media_checks keep the item an existing check asked about"):
    val qid = connect(ds)(sql"""SELECT external_id FROM place_links WHERE place_id = 'zqmd_site' AND scheme = 'wikidata'
          ORDER BY (relation = 'exact_match') DESC, external_id LIMIT 1""".query[String].run().head)
    assertEquals(qid, "Q90000001")

  test("a source's pictures: its figures, an illustration and a cover, the card's order, served without a thumbnail"):
    val png = Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10)
    connect(ds)(sql"""INSERT INTO sources (citekey, title, ocr_markdown) VALUES ('zqmd2022_paper', 'Zqmd paper',
          'Intro ![](assets/fig-0002-01.png) text ![](assets/logo.png) ![](assets/logo.png)')
          ON CONFLICT DO NOTHING""".update.run())
    val _ = AssetFixtures.add(
      ds,
      "zqmd2022_paper",
      List(
        NewFigure("fig-0002-01.png", "image/png", png ++ Array[Byte](7), Some(900), Some(700)),
        NewFigure("logo.png", "image/png", png ++ Array[Byte](8), Some(40), Some(40))
      )
    )
    assertEquals(
      AssetFixtures.figures(ds, "zqmd2022_paper").map(f => (f.name, f.width)),
      List(("fig-0002-01.png", Some(900)), ("logo.png", Some(40)))
    )
    assert(SourceFixtures.markdown(ds, "zqmd2022_paper").contains("assets/logo.png"))
    assert(CoverFixtures.illustrationCandidates(ds, None, refresh = false, 100000).contains("zqmd2022_paper"))
    val fig = AssetFixtures.figures(ds, "zqmd2022_paper").head
    AssetFixtures.setSourcePicture(ds, "zqmd2022_paper", fig.uid, MediaRole.Illustration, None)
    assert(!CoverFixtures.illustrationCandidates(ds, None, refresh = false, 100000).contains("zqmd2022_paper"))
    val t = AssetFixtures.sourceThumb(ds, "zqmd2022_paper").get
    assertEquals((t.uid, t.role, t.width), (fig.uid, MediaRole.Illustration, Some(900)))
    assertEquals(
      AssetFixtures.thumb(ds, fig.uid).map(_._2.toList),
      Some((png ++ Array[Byte](7)).toList),
      "a figure with no thumbnail is its own"
    )
    AssetFixtures.setThumb(ds, fig.uid, Array[Byte](-1, -40, 9), "image/jpeg", Some(330), Some(257))
    assertEquals(AssetFixtures.thumb(ds, fig.uid).map(_._1), Some("image/jpeg"))
    // a cover comes first; a second cover replaces the first
    val cover = ProviderAsset(
      MediaProvider.Calibre,
      "zqmd-1",
      "cover-calibre-zqmd-1.jpg",
      "image/jpeg",
      Array[Byte](-1, -40, 10),
      Some(600),
      Some(900),
      None,
      None,
      None,
      None,
      "",
      None,
      None,
      "Cover: the local Calibre library",
      "calibre:book/zqmd-1",
      None,
      Some(AssetKind.Photo)
    )
    val (c1, _) = AssetFixtures.save(ds, cover, ("source", "zqmd2022_paper"))
    AssetFixtures.setSourcePicture(ds, "zqmd2022_paper", c1, MediaRole.Cover, Some("calibre"))
    assertEquals(
      AssetFixtures.sourceThumb(ds, "zqmd2022_paper").map(t => (t.uid, t.role, t.license)),
      Some((c1, "cover", None))
    )
    assertEquals(
      connect(ds)(sql"SELECT geom IS NULL FROM assets WHERE uid = $c1::uuid".query[Boolean].run().head),
      true,
      "a cover is not on the map"
    )
    val (c2, _) =
      AssetFixtures.save(
        ds,
        cover.copy(key = "zqmd-2", name = "cover-calibre-zqmd-2.jpg"),
        ("source", "zqmd2022_paper")
      )
    AssetFixtures.setSourcePicture(ds, "zqmd2022_paper", c2, MediaRole.Cover, Some("calibre"))
    assertEquals(AssetFixtures.sourcePicture(ds, "zqmd2022_paper", MediaRole.Cover).map(_._1), Some(c2))
    // the licence lint leaves covers alone
    assert(!AssetFixtures.licenceGaps(ds).exists(_._1 == s"media:$c2"), "a cover needs no reuse licence")
    connect(ds)(sql"DELETE FROM assets WHERE provider = 'calibre' AND provider_key LIKE 'zqmd-%'".update.run())

  test("cover candidates: a source with an ISBN and no cover, not asked of Open Library yet"):
    connect(ds):
      sql"""INSERT INTO source_identifiers (source_uid, scheme, value)
            SELECT uid, 'isbn', '9780691168388' FROM sources WHERE citekey = 'zqmd2022_paper'
            ON CONFLICT DO NOTHING""".update.run()
    def mine(refresh: Boolean) =
      CoverFixtures.coverCandidates(ds, None, refresh, 100000).filter(_.citekey.startsWith("zqmd"))
    assertEquals(mine(false).map(k => (k.citekey, k.isbn)), List(("zqmd2022_paper", Some("9780691168388"))))
    ImportFixtures.checked(ds, MediaProvider.OpenLibrary, "source", "zqmd2022_paper", 0)
    assertEquals(mine(false), Nil, "asked already")
    assertEquals(mine(true).map(_.citekey), List("zqmd2022_paper"), "unless refresh")
    assertEquals(
      CoverFixtures.coverCandidates(ds, Some("zqmd2022_paper"), false, 10).map(_.citekey),
      List("zqmd2022_paper"),
      "one named source is asked again"
    )
    connect(ds)(sql"DELETE FROM source_identifiers WHERE value = '9780691168388'".update.run())
