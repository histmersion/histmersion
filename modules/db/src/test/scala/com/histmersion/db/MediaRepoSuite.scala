package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.config.DbConfig
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.AssetRelation
import com.histmersion.api.media.MediaItem
import com.histmersion.api.media.ImageSize
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.media.FigureLicenceFill
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.media.assets.MediaThumbRow

/** Pictures from outside providers (V082, #1446) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise):
  * the reshaped `assets` key (a source's figures keep `(citekey, name)` and their links; a provider's picture has no
  * citekey), storing and linking a provider's picture with its point and period, the cards' thumbnails, which places
  * and events an import asks about, the list, facets and media-layer filters, the figure-licence fill and the
  * `media-licence` lint rule. Fixtures: ids and citekeys start with `zqmd`, at 130°E 40°S.
  */
class MediaRepoSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val P = MediaProvider.Commons
  private val Prov = MediaItemRow.Provider(P)
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

  private def pt(lon: Double) = Some(Place.Geometry(s"""{"type":"Point","coordinates":[$lon,-40]}"""))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              Place.Id("zqmd_site"),
              Place.Label("Zqmd site"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              pt(130.0),
              Some(Period(Year(-1600), Year(-1100)))
            ),
            List(Place.Name(Place.Name.Value("Zqmd site")))
          ),
          Place.Input(
            Place(Place.Id("zqmd_busy"), Place.Label("Zqmd busy"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(131.0)),
            List(Place.Name(Place.Name.Value("Zqmd busy")))
          ),
          Place.Input(
            Place(Place.Id("zqmd_nolink"), Place.Label("Zqmd no link"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(132.0)),
            List(Place.Name(Place.Name.Value("Zqmd no link")))
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
        BEventRow.Id("zqmd_battle"),
        EventPut(EventSummary.Label("Zqmd battle"), EventSummary.Kind("battle"), TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190))), placeId = Some(Place.Id("zqmd_site")))
      )
      val _ = EventFixtures.patch(ds, BEventRow.Id("zqmd_battle"), EventPatch(wikidata = Some(EventPatch.Wikidata("Q90000003"))))
      val png = Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10)
      val _ = AssetFixtures.add(
        ds,
        Citekey("zqmd2020_figs"),
        List(
          NewFigure(NewFigure.Name("fig1.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(png ++ Array[Byte](1)), None, None),
          NewFigure(NewFigure.Name("fig2.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(png ++ Array[Byte](2)), None, None)
        )
      )
      val _ = AssetFixtures.add(
        ds,
        Citekey("zqmd2021_nolic"),
        List(NewFigure(NewFigure.Name("fig.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(png ++ Array[Byte](3)), None, None))
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def asset(
      key: ProviderAsset.Key,
      license: ProviderAsset.License = ProviderAsset.License(Lic),
      credit: ProviderAsset.Credit = ProviderAsset.Credit("Ann, CC BY-SA 9.9, via Wikimedia Commons"),
      thumb: Boolean = true
  ) =
    ProviderAsset(
      ProviderAsset.Provider(P),
      key,
      ProviderAsset.Name(key.value.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_")),
      ProviderAsset.Mime("image/jpeg"),
      ProviderAsset.Bytes(Array[Byte](-1, -40, 1)),
      Some(ProviderAsset.Width(1280)),
      Some(ProviderAsset.Height(960)),
      Option.when(thumb)(ProviderAsset.Thumb(Array[Byte](-1, -40, 2))),
      Option.when(thumb)(ProviderAsset.ThumbMime("image/jpeg")),
      Some(ProviderAsset.ThumbWidth(330)),
      Some(ProviderAsset.ThumbHeight(248)),
      license,
      Some(ProviderAsset.LicenseUrl("https://creativecommons.org/licenses/by-sa/9.9")),
      Some(ProviderAsset.Author("Ann")),
      credit,
      ProviderAsset.SourceUrl(s"https://commons.wikimedia.org/wiki/${key.value.replace(' ', '_')}"),
      Some(ProviderAsset.OriginalUrl("https://upload.example/x.jpg")),
      Some(ProviderAsset.Kind(AssetKind.Photo))
    )

  test("V082: a provider's picture has no citekey; exactly one of citekey and provider is set"):
    val (uid, inserted) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd one.jpg")), (Target.TargetType("place"), Target.Id("zqmd_site")))
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
    val link = AssetLinkFixtures.create(ds, AssetLink.Put(com.histmersion.api.library.Citekey("zqmd2020_figs"), Bundle.Asset.FileName("fig1.png"), AssetRelation.Wire("depicts"), MediaTarget.TargetType("place"), MediaTarget.TargetId("zqmd_site")))
    assert(link.isRight, link.toString)
    val n = AssetFixtures.replace(
      ds,
      Citekey("zqmd2020_figs"),
      List(
        NewFigure(NewFigure.Name("fig1.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 1)), None, None),
        NewFigure(NewFigure.Name("fig2.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 2)), None, None)
      )
    )
    assertEquals(n, 2)
    assertEquals((AssetLinkFixtures.list(ds, Some(Citekey("zqmd2020_figs")), None, None, None).total.value: Int), 1, "the link survived")
    assertEquals(AssetFixtures.list(ds, Citekey("zqmd2020_figs")).map(_.name), Vector("fig1.png", "fig2.png"))

  test("save: a second save of the same key replaces it in place; point and period come from the place"):
    val (uid1, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd two.jpg")), (Target.TargetType("place"), Target.Id("zqmd_site")))
    val (uid2, inserted) = AssetFixtures.save(
      ds,
      asset(
        ProviderAsset.Key("File:Zqmd two.jpg"),
        credit = ProviderAsset.Credit("Bob, CC BY-SA 9.9, via Wikimedia Commons")
      ),
      (Target.TargetType("place"), Target.Id("zqmd_busy"))
    )
    assertEquals(uid2, uid1)
    assert(!inserted)
    val (lon, lo, hi, credit) = connect(ds)(sql"""SELECT ST_X(geom), lower(period), upper(period) - 1, credit
          FROM assets WHERE uid = $uid1::uuid""".query[(Double, Option[Long], Option[Long], String)].run().head)
    assertEqualsDouble(lon, 130.0, 1e-9)
    assertEquals((lo, hi), (Some(-1600L), Some(-1100L)))
    assertEquals(credit, "Bob, CC BY-SA 9.9, via Wikimedia Commons")
    assertEquals(
      AssetFixtures.known(ds, Prov, List(MediaItemRow.ProviderKey("File:Zqmd two.jpg"), MediaItemRow.ProviderKey("File:Zqmd nothing.jpg"))).keySet,
      Set("File:Zqmd two.jpg")
    )

  test("links and the card: the thumbnails of a place, best role first, only ones with a thumbnail"):
    val (img, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd image.jpg")), (Target.TargetType("place"), Target.Id("zqmd_busy")))
    val (map, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd map.svg")), (Target.TargetType("place"), Target.Id("zqmd_busy")))
    val (bare, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd bare.jpg"), thumb = false), (Target.TargetType("place"), Target.Id("zqmd_busy")))
    assert(AssetFixtures.link(ds, map, Target.TargetType("place"), Target.Id("zqmd_busy"), MediaRole.Wire(MediaRole.LocatorMap), Some(AssetFixtures.LinkProperty("P242")), Some(AssetFixtures.LinkItem("Q90000002")), AssetFixtures.LinkRank(0)))
    assert(AssetFixtures.link(ds, img, Target.TargetType("place"), Target.Id("zqmd_busy"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000002")), AssetFixtures.LinkRank(0)))
    assert(AssetFixtures.link(ds, bare, Target.TargetType("place"), Target.Id("zqmd_busy"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000002")), AssetFixtures.LinkRank(1)))
    assert(
      !AssetFixtures.link(ds, img, Target.TargetType("place"), Target.Id("zqmd_busy"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000002")), AssetFixtures.LinkRank(0)),
      "not new"
    )
    val thumbs = AssetFixtures.thumbs(ds, Target.TargetType("place"), Target.Id("zqmd_busy"))
    assertEquals(thumbs.map(_.uid), List(img, map))
    val t = thumbs.head
    assertEquals(
      t,
      MediaThumb(
        MediaItem.Uid(img.value),
        MediaThumb.Role("image"),
        MediaThumb.Url(s"/media/$img/thumb"),
        MediaItem.Credit("Ann, CC BY-SA 9.9, via Wikimedia Commons"),
        Some(MediaItem.License(Lic)),
        Some(MediaItem.LicenseUrl("https://creativecommons.org/licenses/by-sa/9.9")),
        Some(MediaItem.SourceUrl("https://commons.wikimedia.org/wiki/File:Zqmd_image.jpg")),
        Some(ImageSize.Width(330)),
        Some(ImageSize.Height(248))
      )
    )
    assertEquals(AssetFixtures.thumb(ds, img).map(_._1), Some("image/jpeg"))
    assertEquals(AssetFixtures.thumb(ds, bare), None)
    assertEquals(AssetFixtures.file(ds, bare).map(_._2.value.toList), Some(List[Byte](-1, -40, 1)))

  test("V094: depiction, object and map roles; a link keeps its basis; a picture in two roles is on the card once"):
    val (uid, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd depicted.jpg")), (Target.TargetType("event"), Target.Id("zqmd_battle")))
    assert(AssetFixtures.link(ds, uid, Target.TargetType("event"), Target.Id("zqmd_battle"), MediaRole.Wire(MediaRole.Depiction), Some(AssetFixtures.LinkProperty("P180")), Some(AssetFixtures.LinkItem("Q90000003")), AssetFixtures.LinkRank(0)))
    assert(AssetFixtures.link(ds, uid, Target.TargetType("event"), Target.Id("zqmd_battle"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000003")), AssetFixtures.LinkRank(0)))
    assertEquals(
      AssetFixtures.thumbs(ds, Target.TargetType("event"), Target.Id("zqmd_battle")).filter(_.uid.value == uid.value).map(_.role),
      List(MediaRole.Image),
      "once, in its best role"
    )
    val (obj, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd object.jpg")), (Target.TargetType("place"), Target.Id("zqmd_site")))
    assert(
      AssetFixtures.link(ds, obj, Target.TargetType("place"), Target.Id("zqmd_site"), MediaRole.Wire(MediaRole.Object), None, None, AssetFixtures.LinkRank(0), Some(MediaMatch.Basis("find_spot = Zqmd site")))
    )
    assert(
      !AssetFixtures.link(ds, obj, Target.TargetType("place"), Target.Id("zqmd_site"), MediaRole.Wire(MediaRole.Object), None, None, AssetFixtures.LinkRank(0)),
      "not new; the basis kept"
    )
    assert(
      AssetFixtures.link(ds, obj, Target.TargetType("place"), Target.Id("zqmd_busy"), MediaRole.Wire(MediaRole.Map), None, None, AssetFixtures.LinkRank(0), Some(MediaMatch.Basis("location = zqmd busy")))
    )
    val targets = AssetFixtures.show(ds, obj).get.targets
    assertEquals(targets.filter(_.targetId.value == "zqmd_site").map(_.basis), List(Some("find_spot = Zqmd site")))
    assertEquals(ProviderAsset.kindOf(MediaRole.Wire(MediaRole.Map)), AssetKind.Map)
    connect(ds)(sql"DELETE FROM media_links WHERE asset_uid IN ($uid::uuid, $obj::uuid)".update.run())

  test("an event's card: its Wikidata item and its picture"):
    val (uid, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd battle.jpg")), (Target.TargetType("event"), Target.Id("zqmd_battle")))
    AssetFixtures.link(ds, uid, Target.TargetType("event"), Target.Id("zqmd_battle"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000003")), AssetFixtures.LinkRank(0))
    val d = EventFixtures.get(ds, BEventRow.Id("zqmd_battle")).get
    assertEquals(d.wikidata, Some("Q90000003"))
    assertEquals(
      AssetFixtures.thumbs(ds, ImportFixtures.Event, Target.Id("zqmd_battle")).map(_.uid),
      List(uid),
      "its card's picture"
    )
    val refused =
      intercept[Exception](connect(ds)(sql"UPDATE events SET wikidata = 'x1' WHERE id = 'zqmd_battle'".update.run()))
    assert(refused.getMessage.contains("events_wikidata_check"), refused.getMessage)
    val _ = EventFixtures.patch(ds, BEventRow.Id("zqmd_battle"), EventPatch(wikidata = Some(EventPatch.Wikidata(""))))
    assertEquals(EventFixtures.get(ds, BEventRow.Id("zqmd_battle")).get.wikidata, None, "\"\" clears it")
    val _ = EventFixtures.patch(ds, BEventRow.Id("zqmd_battle"), EventPatch(wikidata = Some(EventPatch.Wikidata("Q90000003"))))

  test("targets: one place by its exact Wikidata link; one with none or unknown is an error"):
    assertEquals(
      ImportFixtures.targets(ds, Prov, Some(com.histmersion.domain.catalogue.Place.Id("zqmd_site")), None, ImportFixtures.Limit(10), refresh = ImportFixtures.Refresh(false)),
      Right(List(Target(Target.TargetType("place"), Target.Id("zqmd_site"), Target.Qid("Q90000001"), Target.Label("Zqmd site"))))
    )
    assert(
      ImportFixtures
        .targets(ds, Prov, Some(com.histmersion.domain.catalogue.Place.Id("zqmd_nolink")), None, ImportFixtures.Limit(10), refresh = ImportFixtures.Refresh(false))
        .left
        .exists(_.value.contains("no Wikidata link"))
    )
    assert(
      ImportFixtures.targets(ds, Prov, Some(com.histmersion.domain.catalogue.Place.Id("zqmd_nope")), None, ImportFixtures.Limit(10), refresh = ImportFixtures.Refresh(false)).left.exists(_.value.contains("No place"))
    )
    assertEquals(
      ImportFixtures.targets(ds, Prov, None, Some(Event.Id("zqmd_battle")), ImportFixtures.Limit(10), refresh = ImportFixtures.Refresh(false)).map(_.map(_.qid)),
      Right(List("Q90000003"))
    )

  test("targets by default: events with an item, then places with findings by count; asked ones skipped"):
    def mine(refresh: Boolean) = ImportFixtures
      .targets(ds, Prov, None, None, ImportFixtures.Limit(100000), ImportFixtures.Refresh(refresh))
      .toOption
      .get
      .filter(_.id.value.startsWith("zqmd"))
      .map(t => (t.targetType, t.id, t.qid))
    assertEquals(
      mine(refresh = false),
      List(
        ("event", "zqmd_battle", "Q90000003"),
        ("place", "zqmd_busy", "Q90000002"),
        ("place", "zqmd_site", "Q90000001")
      )
    )
    ImportFixtures.checked(ds, Prov, Target.TargetType("place"), Target.Id("zqmd_busy"), RowCount(2))
    assertEquals(mine(refresh = false).map(_._2), List("zqmd_battle", "zqmd_site"), "asked already: skipped")
    assertEquals(mine(refresh = true).map(_._2), List("zqmd_battle", "zqmd_busy", "zqmd_site"), "unless refresh")
    // #1567: a refresh re-asks every one, also one checked within the same millisecond (it used to
    // compare this process's whole milliseconds with the check's microseconds, and skip it ~1 time in 10)
    (1 to 50).foreach { i =>
      ImportFixtures.checked(ds, Prov, Target.TargetType("place"), Target.Id("zqmd_busy"), RowCount(2))
      assertEquals(
        mine(refresh = true).map(_._2),
        List("zqmd_battle", "zqmd_busy", "zqmd_site"),
        s"refresh right after a check ($i)"
      )
    }
    ImportFixtures.checked(ds, Prov, Target.TargetType("place"), Target.Id("zqmd_busy"), RowCount(3))
    assertEquals(
      connect(ds)(sql"SELECT found FROM media_checks WHERE target_id = 'zqmd_busy'".query[Int].run()),
      Vector(3)
    )
    connect(ds)(sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmd%'".update.run())

  test("a place's Commons category is a link of the place, once"):
    assert(ImportFixtures.commonsCategory(ds, com.histmersion.domain.catalogue.Place.Id("zqmd_site"), ImportFixtures.Category("Zqmd category"), Target.Qid("Q90000001")))
    assert(!ImportFixtures.commonsCategory(ds, com.histmersion.domain.catalogue.Place.Id("zqmd_site"), ImportFixtures.Category("Zqmd category"), Target.Qid("Q90000001")))
    assert(
      PlaceFixtures
        .get(ds, com.histmersion.domain.catalogue.Place.Id("zqmd_site"), None, DeepTime.SignedYear(cfg.places.modernFrom.value))
        .get
        .links
        .exists(l => l.scheme.value == "commons" && l.externalId.value == "Zqmd category")
    )

  test("list, show and facets: by provider, licence, place; source figures as `source`"):
    val (uid, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd listed.jpg")), (Target.TargetType("place"), Target.Id("zqmd_site")))
    AssetFixtures.link(ds, uid, Target.TargetType("place"), Target.Id("zqmd_site"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000001")), AssetFixtures.LinkRank(0))
    val l =
      AssetFixtures.list(ds, Api.MediaQuery(provider = Some(MediaProvider.Id(P)), license = Some(Api.MediaQuery.License(Lic)), place = Some(Place.Id("zqmd_site"))), AssetFixtures.Limit(50))
    assertEquals(l.items.map(_.providerKey), List(Some("File:Zqmd listed.jpg")))
    assertEquals((l.total.value: Int), 1)
    val figs = AssetFixtures
      .list(ds, Api.MediaQuery(provider = Some(MediaProvider.Id("source")), q = Some(Api.MediaQuery.Q("fig")), limit = None), AssetFixtures.Limit(1000))
      .items
      .filter(_.citekey.exists(_.value.startsWith("zqmd")))
    assertEquals(
      figs.map(f => (f.citekey.map(_.value), f.name.value)).sorted,
      List(
        (Some("zqmd2020_figs"), "fig1.png"),
        (Some("zqmd2020_figs"), "fig2.png"),
        (Some("zqmd2021_nolic"), "fig.png")
      )
    )
    val shown = AssetFixtures.show(ds, uid).get
    assertEquals(shown.targets, List(MediaTarget(MediaTarget.TargetType("place"), MediaTarget.TargetId("zqmd_site"), MediaTarget.Role("image"), Some(MediaTarget.Property("P18")), Some(MediaTarget.Item("Q90000001")))))
    assertEquals((shown.fileUrl, shown.thumbUrl), (s"/media/$uid/file", Some(s"/media/$uid/thumb")))
    assert(shown.retrievedAt.exists(_.value.endsWith("Z")))
    val f = AssetFixtures.facets(ds)
    assert(
      f.providers.exists(c => c.key.value == P && c.n.value >= 1) && f.providers.exists(_.key.value == MediaProvider.Source),
      f.toString
    )
    assert(f.licences.exists(c => c.key.value == Lic && c.n.value >= 1), f.toString)

  test("the media layer: provider and licence filters, a provider's thumbnail and credit on the hit"):
    val q = Api.MediaGeoQuery(provider = Some(MediaProvider.Id(P)), license = Some(Api.MediaGeoQuery.License(Lic)), limit = Some(Api.MediaGeoQuery.Limit(1000)))
    val hits = AssetFixtures.geo(ds, q, AssetFixtures.Limit(1000))
    assert(hits.nonEmpty && hits.forall(h => h.provider.contains(P) && h.license.contains(Lic) && h.citekey.isEmpty))
    assert(hits.exists(h => h.thumbUrl.contains(s"/media/${h.uid}/thumb") && h.credit.isDefined))
    // what a full-size view shows: the stored rendition (this server's), the provider's page, the licence's text
    assert(
      hits.forall(h => h.fileUrl.contains(s"/media/${h.uid}/file") && h.sourceUrl.exists(_.value.startsWith("http"))),
      hits.toString
    )
    assert(
      hits.exists(h =>
        h.licenseUrl.contains("https://creativecommons.org/licenses/by-sa/9.9") && h.author.contains("Ann")
      ),
      hits.toString
    )
    assertEquals(AssetFixtures.geo(ds, q.modify(_.provider).setTo(Some(MediaProvider.Id("source"))), AssetFixtures.Limit(1000)).filter(_.license.contains(Lic)), Nil)
    val dated = AssetFixtures.geo(ds, q.modify(_.from).setTo(Some(Api.MediaGeoQuery.From(-1500L))).modify(_.to).setTo(Some(Api.MediaGeoQuery.To(-1400L))), AssetFixtures.Limit(1000))
    assert(dated.nonEmpty && dated.forall(_.periodStart.exists(_.value <= -1400)), "the place's period dates the picture")
    assert(!dated.exists(_.assetName.value == "Zqmd_image.jpg"), "a place with no dates gives its picture none")

  test("fill-licences: a figure takes its source's licence — counted first, written only with apply"):
    val dry = FigureFixtures.fillLicences(ds, apply = FigureLicenceFill.Applied(false))
    assertEquals(dry.bySource.find(_.key.value == "zqmd2020_figs").map(_.n), Some(2))
    assert(!dry.bySource.exists(_.key.value == "zqmd2021_nolic"), "a source with no licence gives none")
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
    val done = FigureFixtures.fillLicences(ds, apply = FigureLicenceFill.Applied(true))
    assert(done.applied.value && done.updated.value >= 2)
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
    val (uid, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd uncredited.jpg"), credit = ProviderAsset.Credit(" ")), (Target.TargetType("place"), Target.Id("zqmd_site")))
    val items = AssetFixtures.licenceGaps(ds).map((k, v) => (k.value: String, v.value: String)).toMap
    assertEquals(items.get("zqmd2021_nolic/fig.png").map(_.startsWith("no licence")), Some(true))
    assertEquals(items.get(s"media:$uid").map(_.startsWith("no credit line")), Some(true))
    assert(!items.contains("zqmd2020_figs/fig1.png"),"a figure with its source's licence is fine")

  // ── #1446 second slice: equivalents, items, sources' pictures ──────────────────────────────────

  private def eqFixtures(): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(Place.Id("zqmd_eq_a"), Place.Label("Zqmd Gobekli Tepe"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(133.0)),
          List(Place.Name(Place.Name.Value("Zqmd Gobekli Tepe")))
        ),
        // another gazetteer's point of the same site: the same name (the key folds ö and case), 850 m away
        Place.Input(
          Place(Place.Id("zqmd_eq_b"), Place.Label("Zqmd Göbekli tepe"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(133.01)),
          List(Place.Name(Place.Name.Value("Zqmd Göbekli tepe")))
        ),
        // the same name 700 km away is another place
        Place.Input(
          Place(Place.Id("zqmd_eq_far"), Place.Label("Zqmd Gobekli Tepe"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(141.0)),
          List(Place.Name(Place.Name.Value("Zqmd Gobekli Tepe")))
        ),
        // another name, the same Wikidata item (a polity's other time slice, a Wikidata import)
        Place.Input(
          Place(Place.Id("zqmd_eq_wd"), Place.Label("Zqmd Other"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(135.0)),
          List(Place.Name(Place.Name.Value("Zqmd Other")))
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
    val (photo, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd gobekli.jpg")), (Target.TargetType("place"), Target.Id("zqmd_eq_a")))
    AssetFixtures.link(ds, photo, Target.TargetType("place"), Target.Id("zqmd_eq_a"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000011")), AssetFixtures.LinkRank(0))
    val (plan, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd gobekli plan.jpg")), (Target.TargetType("place"), Target.Id("zqmd_eq_a")))
    // stored under an id that was later merged into zqmd_eq_b (its alias), from no item
    AssetFixtures.link(ds, plan, Target.TargetType("place"), Target.Id("zqmd_eq_old"), MediaRole.Wire(MediaRole.Plan), None, None, AssetFixtures.LinkRank(0))
    def uids(id: com.histmersion.domain.catalogue.Place.Id) = AssetFixtures.placeThumbs(ds, id, EventMatcher.NearFinding.Km(cfg.media.sameSiteKm.value)).map(_.uid)
    assertEquals(uids(com.histmersion.domain.catalogue.Place.Id("zqmd_eq_a")), List(photo), "its own; the plan is the old id's, not an equivalent of a")
    assertEquals(uids(com.histmersion.domain.catalogue.Place.Id("zqmd_eq_b")), List(photo, plan), "the same site's other point, and its own old id")
    assertEquals(uids(com.histmersion.domain.catalogue.Place.Id("zqmd_eq_wd")), List(photo), "the same Wikidata item")
    assertEquals(uids(com.histmersion.domain.catalogue.Place.Id("zqmd_eq_far")), Nil,"the same name far away is another place")
    assertEquals(
      AssetFixtures.placeThumbs(ds, com.histmersion.domain.catalogue.Place.Id("zqmd_eq_b"), EventMatcher.NearFinding.Km(0)).map(_.uid),
      List(plan),
      "no same-site radius: the alias only"
    )
    assertEquals(AssetFixtures.thumbs(ds, Target.TargetType("place"), Target.Id("zqmd_eq_b")), Nil, "the plain lookup stays exact")

  test("a place's own picture is shown before an equivalent's in the same role"):
    val (own, _) = AssetFixtures.save(ds, asset(ProviderAsset.Key("File:Zqmd gobekli own.jpg")), (Target.TargetType("place"), Target.Id("zqmd_eq_wd")))
    AssetFixtures.link(ds, own, Target.TargetType("place"), Target.Id("zqmd_eq_wd"), MediaRole.Wire(MediaRole.Image), Some(AssetFixtures.LinkProperty("P18")), Some(AssetFixtures.LinkItem("Q90000011")), AssetFixtures.LinkRank(1))
    assertEquals(AssetFixtures.placeThumbs(ds, com.histmersion.domain.catalogue.Place.Id("zqmd_eq_wd"), EventMatcher.NearFinding.Km(cfg.media.sameSiteKm.value)).map(_.uid).headOption, Some(own))

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
      .targets(ds, Prov, None, None, ImportFixtures.Limit(100000), refresh = ImportFixtures.Refresh(false))
      .toOption
      .get
      .filter(_.id.value.startsWith("zqmd_eq"))
      .map(t => (t.id, t.qid))
    assertEquals(mine(), List(("zqmd_eq_wd", "Q90000011")), "two sources cite zqmd_eq_wd, one zqmd_eq_a")
    val before = ImportFixtures.pending(ds, Prov)
    ImportFixtures.checked(ds, Prov, Target.TargetType("place"), Target.Id("zqmd_eq_a"), RowCount(1), Some(Target.Qid("Q90000011")))
    assertEquals(mine(), Nil, "the item was asked about through zqmd_eq_a")
    assertEquals(ImportFixtures.pending(ds, Prov), before.value - 1)
    val since = System.currentTimeMillis() + 60000
    assertEquals(
      ImportFixtures
        .targets(ds, Prov, None, None, ImportFixtures.Limit(100000), refresh = ImportFixtures.Refresh(true), Some(ImportFixtures.Since(since)))
        .toOption
        .get
        .filter(_.id.value.startsWith("zqmd_eq"))
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
      Citekey("zqmd2022_paper"),
      List(
        NewFigure(NewFigure.Name("fig-0002-01.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(png ++ Array[Byte](7)), Some(NewFigure.Width(900)), Some(NewFigure.Height(700))),
        NewFigure(NewFigure.Name("logo.png"), NewFigure.Mime("image/png"), NewFigure.Bytes(png ++ Array[Byte](8)), Some(NewFigure.Width(40)), Some(NewFigure.Height(40)))
      )
    )
    assertEquals(
      AssetFixtures.figures(ds, Citekey("zqmd2022_paper")).map(f => (f.name, f.width)),
      List(("fig-0002-01.png", Some(900)), ("logo.png", Some(40)))
    )
    assert(SourceFixtures.markdown(ds, Citekey("zqmd2022_paper")).value.contains("assets/logo.png"))
    assert(CoverFixtures.illustrationCandidates(ds, None, refresh = CoverFixtures.Refresh(false), CoverFixtures.Limit(100000)).contains("zqmd2022_paper"))
    val fig = AssetFixtures.figures(ds, Citekey("zqmd2022_paper")).head
    val figUid = MediaItemRow.Uid(fig.uid.value)
    AssetFixtures.setSourcePicture(ds, Citekey("zqmd2022_paper"), figUid, MediaRole.Wire(MediaRole.Illustration), None)
    assert(!CoverFixtures.illustrationCandidates(ds, None, refresh = CoverFixtures.Refresh(false), CoverFixtures.Limit(100000)).contains("zqmd2022_paper"))
    val t = AssetFixtures.sourceThumb(ds, Citekey("zqmd2022_paper")).get
    assertEquals((t.uid, t.role, t.width), (fig.uid, MediaRole.Illustration, Some(900)))
    assertEquals(
      AssetFixtures.thumb(ds, figUid).map(_._2.value.toList),
      Some((png ++ Array[Byte](7)).toList),
      "a figure with no thumbnail is its own"
    )
    AssetFixtures.setThumb(ds, figUid, ProviderAsset.Thumb(Array[Byte](-1, -40, 9)),ProviderAsset.ThumbMime("image/jpeg"), Some(MediaThumbRow.Width(330)), Some(MediaThumbRow.Height(257)))
    assertEquals(AssetFixtures.thumb(ds, figUid).map(_._1), Some("image/jpeg"))
    // a cover comes first; a second cover replaces the first
    val cover = ProviderAsset(
      ProviderAsset.Provider(MediaProvider.Calibre),
      ProviderAsset.Key("zqmd-1"),
      ProviderAsset.Name("cover-calibre-zqmd-1.jpg"),
      ProviderAsset.Mime("image/jpeg"),
      ProviderAsset.Bytes(Array[Byte](-1, -40, 10)),
      Some(ProviderAsset.Width(600)),
      Some(ProviderAsset.Height(900)),
      None,
      None,
      None,
      None,
      ProviderAsset.License(""),
      None,
      None,
      ProviderAsset.Credit("Cover: the local Calibre library"),
      ProviderAsset.SourceUrl("calibre:book/zqmd-1"),
      None,
      Some(ProviderAsset.Kind(AssetKind.Photo))
    )
    val (c1, _) = AssetFixtures.save(ds, cover, (Target.TargetType("source"), Target.Id("zqmd2022_paper")))
    AssetFixtures.setSourcePicture(ds, Citekey("zqmd2022_paper"), c1, MediaRole.Wire(MediaRole.Cover), Some(AssetFixtures.SetSourcePictureProperty("calibre")))
    assertEquals(
      AssetFixtures.sourceThumb(ds, Citekey("zqmd2022_paper")).map(t => (t.uid, t.role, t.license)),
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
        cover.modify(_.key).setTo(ProviderAsset.Key("zqmd-2")).modify(_.name).setTo(ProviderAsset.Name("cover-calibre-zqmd-2.jpg")),
        (Target.TargetType("source"), Target.Id("zqmd2022_paper"))
      )
    AssetFixtures.setSourcePicture(ds, Citekey("zqmd2022_paper"), c2, MediaRole.Wire(MediaRole.Cover), Some(AssetFixtures.SetSourcePictureProperty("calibre")))
    assertEquals(AssetFixtures.sourcePicture(ds, Citekey("zqmd2022_paper"), MediaRole.Wire(MediaRole.Cover)).map(_._1), Some(c2))
    // the licence lint leaves covers alone
    assert(!AssetFixtures.licenceGaps(ds).exists(_._1.value == s"media:$c2"), "a cover needs no reuse licence")
    connect(ds)(sql"DELETE FROM assets WHERE provider = 'calibre' AND provider_key LIKE 'zqmd-%'".update.run())

  test("cover candidates: a source with an ISBN and no cover, not asked of Open Library yet"):
    connect(ds):
      sql"""INSERT INTO source_identifiers (source_uid, scheme, value)
            SELECT uid, 'isbn', '9780691168388' FROM sources WHERE citekey = 'zqmd2022_paper'
            ON CONFLICT DO NOTHING""".update.run()
    def mine(refresh: Boolean) =
      CoverFixtures.coverCandidates(ds, None, CoverFixtures.Refresh(refresh),CoverFixtures.Limit(100000)).filter(_.citekey.value.startsWith("zqmd"))
    assertEquals(mine(false).map(k => (k.citekey, k.isbn)), List(("zqmd2022_paper", Some("9780691168388"))))
    ImportFixtures.checked(ds, MediaItemRow.Provider(MediaProvider.OpenLibrary), Target.TargetType("source"), Target.Id("zqmd2022_paper"), RowCount(0))
    assertEquals(mine(false), Nil, "asked already")
    assertEquals(mine(true).map(_.citekey), List("zqmd2022_paper"), "unless refresh")
    assertEquals(
      CoverFixtures.coverCandidates(ds, Some(CoverFixtures.Only("zqmd2022_paper")), CoverFixtures.Refresh(false), CoverFixtures.Limit(10)).map(_.citekey),
      List("zqmd2022_paper"),
      "one named source is asked again"
    )
    connect(ds)(sql"DELETE FROM source_identifiers WHERE value = '9780691168388'".update.run())
