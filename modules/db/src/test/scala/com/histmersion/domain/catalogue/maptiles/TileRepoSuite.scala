package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.assets.AssetFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.db.*
import com.histmersion.api.catalogue.{Gazetteer, Period, SiteTile, Year}
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.domain.catalogue.Place as DPlace
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.db.DbSuite
import com.histmersion.config.DbConfig
import com.histmersion.domain.catalogue.events.BEventRow

/** The map's tiles (`TileRepo`, V105) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): which sites,
  * events and pictures a tile holds for a span (a RANGE: every item whose dates overlap it), the grouping that keeps a
  * tile small and its counts honest, the site-type filter, the SQL Mercator against the Scala one, and the versions a
  * write moves. Fixtures sit around 100°W 70°S, where no other suite has any; ids carry `zztile`.
  */
class TileRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM places WHERE id LIKE 'zztile%' OR id LIKE 'wikidata_qzztile%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zztile%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider = 'zztile'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Lon = Longitude(-100.0)
  private val Lat = Latitude(-70.0)
  private val grid =
    TileGrid.of(maxZoom = TileGrid.MaxZoom(11), maxPoints = TileGrid.MaxPoints(600), cellPx = TileGrid.CellPx(32))

  /** The tile of zoom `z` that holds (`lat`, `lon`). */
  private def tileAt(z: TileCoord.Z, lat: Latitude = Lat, lon: Longitude = Lon): TileCoord =
    val (mx, my) = TileCoord.mercator(lat, lon)
    TileCoord
      .of(
        z,
        TileCoord.X(math.floor(mx * (1 << z.value)).toLong),
        TileCoord.Y(math.floor(my * (1 << z.value)).toLong),
        TileCoord.Z(22)
      )
      .toOption
      .get

  private def site(
      id: DPlace.Id,
      label: Place.Label,
      period: String,
      dx: Double = 0,
      dy: Double = 0,
      importSource: Option[Place.Source] = None,
      props: Place.Properties = Place.Properties("{}"),
      siteType: Option[TileRepo.SiteType] = None
  ): Unit = connect(ds):
    val _ =
      sql"INSERT INTO places (id, label, kind, period, geom, creator) VALUES ($id, $label, 'site', ${SqlLiteral(period)}, ST_SetSRID(ST_MakePoint(${Lon.value + dx}, ${Lat.value + dy}), 4326), jsonb_build_object('kind', 'unknown'))".update
        .run()
    // the optional columns set apart: `Param` has no null
    val _ =
      sql"UPDATE places SET import_source = $importSource, properties = $props::jsonb, site_type = $siteType WHERE id = $id".update
        .run()

  private def keysIn(cells: List[TileCell]): Set[String] = cells.filter(_.isItem).map(_.key.value).toSet

  test("the sites' partial-index predicates name Place.Kind.Site and the imported sources word for word"):
    val kind = s"p.kind = '${DPlace.Kind.Site}'"
    val sites =
      TileRepo.siteCandidates((SignedYear(-100L), SignedYear(100L)), None, SignedYear(1500L), sql"TRUE").sqlString
    assert(sites.contains(kind), sites)
    assert(UndatedSiteRepo.UndatedSiteSql.sqlString.startsWith(kind), UndatedSiteRepo.UndatedSiteSql.sqlString)
    assertEquals(
      PlaceFixtures.ImportedList.sqlString,
      Gazetteer.ImportedSources.map(s => s"'$s'").mkString("(", ", ", ")")
    )

  test("the SQL Mercator places a point where the Scala one does"):
    val c = tileAt(TileCoord.Z(7), Latitude(-33.9), Longitude(18.4))
    val (ux, uy) = TileRepo.unitsSql(c)
    val (x, y) = connect(ds):
      sql"SELECT $ux, $uy FROM (SELECT 18.4::float8 AS lon, -33.9::float8 AS lat) p"
        .query[(Double, Double)]
        .run()
        .head
    val (sx, sy) = c.unitsOf(Latitude(-33.9), Longitude(18.4))
    assertEqualsDouble(x, sx, 1e-6)
    assertEqualsDouble(y, sy, 1e-6)

  test(
    "sites: every site whose dates overlap the span, a dated import as much as a curated one; never an undated import"
  ):
    site(DPlace.Id("zztile_a"), Place.Label("Zztile A"), "int8range(-2000, -1500, '[]')")
    site(DPlace.Id("zztile_b"), Place.Label("Zztile B"), "int8range(-3000, -2500, '[]')", dx = 0.01)
    site(DPlace.Id("zztile_c"), Place.Label("Zztile C"), "int8range(-1200, NULL)", dx = 0.02)
    site(
      DPlace.Id("wikidata_qzztile1"),
      Place.Label("Zztile Wikidata"),
      "int8range(-1550, -1400, '[]')",
      dx = 0.03,
      importSource = Some(Place.Source("wikidata"))
    )
    site(
      DPlace.Id("wikidata_qzztile2"),
      Place.Label("Zztile Undated"),
      "int8range(NULL, NULL)",
      dx = 0.04,
      importSource = Some(Place.Source("wikidata")),
      props = Place.Properties("""{"modern": true}""")
    )
    val c = tileAt(TileCoord.Z(9))
    def shown(lo: SignedYear, hi: SignedYear) =
      keysIn(connect(ds)(TileRepo.siteCells(c, grid, (lo, hi), None, SignedYear(1500)))).filter(_.contains("zztile"))
    assertEquals(shown(SignedYear(-1600), SignedYear(-1000)), Set("zztile_a", "zztile_c", "wikidata_qzztile1"))
    assertEquals(shown(SignedYear(-2600), SignedYear(-2600)), Set("zztile_b"))
    // a single year is the range of that year; the undated import stays off the map even after modern-from
    assertEquals(shown(SignedYear(1800), SignedYear(1800)), Set("zztile_c"))
    val rec = connect(ds)(TileRepo.sitesOf(List(TileRepo.SiteId("zztile_a"), TileRepo.SiteId("zztile_c"))))
    assertEquals(
      rec(TileRepo.SiteId("zztile_a")),
      SiteTile(
        SiteTile.Id("zztile_a"),
        SiteTile.Label("Zztile A"),
        SiteTile.Kind("site"),
        None,
        SiteTile.Basis("dated"),
        Some(Year(-2000L)),
        Some(Year(-1500L))
      )
    )
    assertEquals(rec(TileRepo.SiteId("zztile_c")).periodEnd, None)

  test("sites: the site-type filter reads the same type the site is drawn with"):
    site(
      DPlace.Id("zztile_t1"),
      Place.Label("Zztile Temple"),
      "int8range(-500, -100, '[]')",
      props = Place.Properties("""{"feature_types": "settlement,temple-2"}""")
    )
    site(
      DPlace.Id("zztile_t2"),
      Place.Label("Zztile Town"),
      "int8range(-500, -100, '[]')",
      dx = 0.01,
      props = Place.Properties("""{"feature_types": "settlement"}""")
    )
    site(
      DPlace.Id("zztile_t3"),
      Place.Label("Zztile Fort"),
      "int8range(-500, -100, '[]')",
      dx = 0.02,
      siteType = Some(TileRepo.SiteType("fortification"))
    )
    val c = tileAt(TileCoord.Z(9))
    def of(t: TileRepo.SiteType) =
      keysIn(connect(ds)(TileRepo.siteCells(c, grid, (SignedYear(-300), SignedYear(-300)), Some(t), SignedYear(1500))))
    assertEquals(of(TileRepo.SiteType("sanctuary")), Set("zztile_t1"))
    assertEquals(of(TileRepo.SiteType("settlement")), Set("zztile_t2"))
    assertEquals(of(TileRepo.SiteType("fortification")), Set("zztile_t3"))
    assertEquals(
      connect(ds)(TileRepo.sitesOf(List(TileRepo.SiteId("zztile_t1"))))
        .get(TileRepo.SiteId("zztile_t1"))
        .flatMap(_.siteType),
      Some("sanctuary")
    )

  test(
    "undated sites: listed in a tile but those excepted, counted by squares, the kept counts the same after a rebuild"
  ):
    site(
      DPlace.Id("zztile_u1"),
      Place.Label("Zztile Tower"),
      "int8range(NULL, NULL)",
      importSource = Some(Place.Source("wikidata")),
      siteType = Some(TileRepo.SiteType("tower"))
    )
    site(
      DPlace.Id("zztile_u2"),
      Place.Label("Zztile Ruin"),
      "int8range(NULL, NULL)",
      dx = 0.01,
      importSource = Some(Place.Source("geonames"))
    )
    site(DPlace.Id("zztile_u3"), Place.Label("Zztile Ruin 2"), "int8range(NULL, NULL)", dx = 0.3, dy = 0.3)
    site(DPlace.Id("zztile_d1"), Place.Label("Zztile Dated"), "int8range(-500, -100, '[]')", dx = 0.02)
    val deep = tileAt(TileCoord.Z(9))
    val listed =
      connect(ds)(UndatedSiteRepo.items(deep, None, except = List(TileRepo.SiteId("zztile_u2"))))
        .filter(_.key.value.startsWith("zztile"))
    assertEquals(listed.map(c => (c.key, c.undated)).toSet, Set("zztile_u1" -> 1))
    assertEquals(
      connect(ds)(UndatedSiteRepo.items(deep, Some(TileRepo.SiteType("tower")), Nil))
        .map(_.key)
        .filter(_.value.startsWith("zztile")),
      List("zztile_u1")
    )
    val shallow = tileAt(TileCoord.Z(4))
    val counted = connect(ds)(UndatedSiteRepo.counted(shallow, grid, None))
    val v = connect(ds)(UndatedSiteRepo.version())
    assert(transact(ds)(UndatedSiteRepo.rebuild(v, UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(6))))
    assertEquals(connect(ds)(UndatedSiteRepo.built()).map(_.version), Some(v))
    val kept = connect(ds)(UndatedSiteRepo.kept(shallow, UndatedBuild.PerSide(16), None))
    assertEquals(
      kept.map(k => (k.gx, k.gy, k.n)).sortBy(t => t._1.value: Long),
      counted.map(k => (k.gx, k.gy, k.n)).sortBy(t => t._1.value: Long)
    )
    kept
      .zip(counted.sortBy(c => c.gx.value: Long))
      .foreach((k, c) => assertEqualsDouble(k.sumX.value, c.sumX.value, 1e-3))
    assertEquals(counted.map(_.n.value).sum, 3)
    // the dated site is no undated one, and the span's dated count marks no site as undated
    assertEquals(
      connect(ds)(
        TileRepo.items(TileRepo.datedSites(deep, (SignedYear(-300), SignedYear(-300)), None, SignedYear(1500)), deep)
      )
        .filter(_.key.value.startsWith("zztile"))
        .map(c => (c.key, c.undated)),
      List("zztile_d1" -> 0)
    )

  test("undated sites: a write to one moves the undated version; a write to a dated site does not"):
    site(DPlace.Id("zztile_v1"), Place.Label("Zztile Dated V"), "int8range(-500, -100, '[]')")
    val before = connect(ds)(UndatedSiteRepo.version())
    connect(ds)(sql"UPDATE places SET label = 'Zztile Dated W' WHERE id = 'zztile_v1'".update.run())
    assertEquals(connect(ds)(UndatedSiteRepo.version()), before)
    site(DPlace.Id("zztile_v2"), Place.Label("Zztile Undated V"), "int8range(NULL, NULL)")
    val after = connect(ds)(UndatedSiteRepo.version())
    assert(after.value > before.value)
    connect(ds)(sql"DELETE FROM places WHERE id = 'zztile_v2'".update.run())
    assert(connect(ds)(UndatedSiteRepo.version()).value > after.value)

  test("a place's record: where its dates come from, its designations and registers; anything malformed read as none"):
    site(
      DPlace.Id("zztile_r1"),
      Place.Label("Zztile Castle"),
      "int8range(1200, 1400, '[]')",
      importSource = Some(Place.Source("wikidata")),
      props = Place.Properties("""{"date_source": "wikidata-p571", "periods": [{"id": "Q1", "label": "Byzantine"}],
        "heritage": [{"id": "Q2", "label": "listed monument", "start": "1922"}, "garbage"],
        "registers": {"P2186": ["GR-1", "GR-2"], "P1936": "99"}}""")
    )
    site(
      DPlace.Id("zztile_r2"),
      Place.Label("Zztile Odd"),
      "int8range(1200, 1400, '[]')",
      props = Place.Properties("""{"heritage": {"id": "Q3"}, "registers": []}""")
    )
    val r = PlaceFixtures.get(ds, domain.catalogue.Place.Id("zztile_r1")).get.record
    assertEquals(r.dateSource, Some("wikidata-p571"))
    assertEquals(r.periods, List("Byzantine"))
    assertEquals(
      r.designations,
      List(
        Place.Designation(
          Place.Designation.Id("Q2"),
          Some(Place.Designation.Label("listed monument")),
          Some(Place.Designation.Start("1922")),
          None
        )
      )
    )
    assertEquals(
      r.registers,
      List(
        Place.Register(Place.Register.Property("P1936"), Place.Register.Id("99")),
        Place.Register(Place.Register.Property("P2186"), Place.Register.Id("GR-1")),
        Place.Register(Place.Register.Property("P2186"), Place.Register.Id("GR-2"))
      )
    )
    assert(PlaceFixtures.get(ds, domain.catalogue.Place.Id("zztile_r2")).get.record.isEmpty)

  test("grouping: a tile of more items than it keeps alone counts them by cell, and the counts add up to every item"):
    // 900 sites on a 30 × 30 grid 0.01° apart: more than a tile keeps alone
    connect(ds):
      val _ =
        sql"INSERT INTO places (id, label, kind, period, geom, creator) SELECT 'zztile_g' || i || '_' || j, 'Zztile G', 'site', int8range(-1000 + i, -900 + j, '[]'), ST_SetSRID(ST_MakePoint($Lon + i * 0.01, $Lat + j * 0.01), 4326), jsonb_build_object('kind', 'unknown') FROM generate_series(0, 29) i, generate_series(0, 29) j".update
          .run()
    val small =
      TileGrid.of(maxZoom = TileGrid.MaxZoom(11), maxPoints = TileGrid.MaxPoints(100), cellPx = TileGrid.CellPx(32))
    List(3, 6, 9).foreach { z =>
      // every tile of zoom z the fixture touches
      val corners =
        for dx <- List(0.0, 0.3); dy <- List(0.0, 0.3)
        yield tileAt(TileCoord.Z(z), Latitude.assume(Lat.value + dy), Longitude.assume(Lon.value + dx))
      val tiles = corners.distinct
      val cells = tiles
        .flatMap(t =>
          connect(ds)(TileRepo.siteCells(t, small, (SignedYear(-2000), SignedYear(2000)), None, SignedYear(1500)))
        )
        .filter(c => c.key.value.startsWith("zztile") || c.key.value.startsWith("c"))
      val ours = cells.filter(c => !c.isItem || c.key.value.startsWith("zztile_g"))
      assertEquals(ours.map(_.n.value).sum, 900, s"zoom $z")
      tiles.foreach(t =>
        assert(
          connect(ds)(
            TileRepo.siteCells(t, small, (SignedYear(-2000), SignedYear(2000)), None, SignedYear(1500))
          ).size <= (TileCoord.Extent.value / small.cellUnits.value) * (TileCoord.Extent.value / small.cellUnits.value)
        )
      )
      // a cell's box holds its items, its dates' range their middles
      ours.filterNot(_.isItem).foreach { c =>
        assert(c.west.value <= c.east.value && c.south.value <= c.north.value, c.toString)
        assert(c.midLo.exists(lo => c.midHi.exists(_.value >= lo.value)), c.toString)
      }
    }
    // at the deepest tile every item is one feature
    val deepest = connect(ds)(
      TileRepo.siteCells(tileAt(TileCoord.Z(11)), small, (SignedYear(-2000), SignedYear(2000)), None, SignedYear(1500))
    )
    assert(deepest.forall(_.isItem), deepest.filterNot(_.isItem).take(3).toString)

  test("events and pictures: a span is a range of overlap; a picture's provider and licence narrow it"):
    connect(ds):
      val _ = sql"""INSERT INTO events (id, label, kind, not_before, not_after, geom, creator)
                    VALUES ('zztile_e1', 'Zztile battle', 'battle', -1300, -1290, ST_SetSRID(ST_MakePoint($Lon, $Lat), 4326), jsonb_build_object('kind', 'unknown')),
                           ('zztile_e2', 'Zztile flood', 'flood', -800, -790, ST_SetSRID(ST_MakePoint(${Lon.value + 0.01}, $Lat), 4326), jsonb_build_object('kind', 'unknown'))""".update
        .run()
      val _ = sql"""INSERT INTO assets (provider, provider_key, source_url, retrieved_at, name, mime, bytes, sha256, geom, period, license, creator)
                    VALUES ('zztile', 'k1', 'https://example.org/1', now(), 'Zz one', 'image/jpeg', decode('00', 'hex'), 'zz1', ST_SetSRID(ST_MakePoint($Lon, $Lat), 4326), int8range(-1400, -1200, '[]'), 'CC0-1.0', jsonb_build_object('kind', 'unknown')),
                           ('zztile', 'k2', 'https://example.org/2', now(), 'Zz two', 'image/jpeg', decode('00', 'hex'), 'zz2', ST_SetSRID(ST_MakePoint($Lon, ${Lat.value + 0.01}), 4326), int8range(100, 200, '[]'), NULL, jsonb_build_object('kind', 'unknown'))""".update
        .run()
    val c = tileAt(TileCoord.Z(9))
    def events(lo: Year, hi: Year) =
      keysIn(
        connect(ds)(
          TileRepo.eventCells(
            c,
            grid,
            EventFixtures.listWhere(Filter(period = Some(Period(lo, hi))), PlaceScope.empty, None, None, None, None)
          )
        )
      ).filter(_.startsWith("zztile"))
    assertEquals(events(Year(-1295), Year(-1295)), Set("zztile_e1"))
    assertEquals(events(Year(-1500), Year(0)), Set("zztile_e1", "zztile_e2"))
    assertEquals(
      EventFixtures.geoOf(ds, List(BEventRow.Id("zztile_e1"))).get(BEventRow.Id("zztile_e1")).map(_.label),
      Some("Zztile battle")
    )
    // an event with no known date: only when asked for, marked, whatever the span
    connect(ds):
      val _ = sql"""INSERT INTO events (id, label, kind, geom, creator)
                    VALUES ('zztile_e3', 'Zztile quake', 'earthquake', ST_SetSRID(ST_MakePoint(${Lon.value + 0.02}, $Lat), 4326), jsonb_build_object('kind', 'unknown'))""".update
        .run()
    assertEquals(events(Year(-1500), Year(0)), Set("zztile_e1", "zztile_e2"))
    def withUndated(lo: Year, hi: Year) =
      val f = Filter(period = Some(Period(lo, hi)))
      connect(ds)(
        TileRepo
          .eventCellsWithUndated(
            c,
            grid,
            EventFixtures.listWhere(f, PlaceScope.empty, None, None, None, None),
            EventFixtures.listWhere(f.modify(_.period).setTo(None), PlaceScope.empty, None, None, None, None)
          )
      )
        .filter(_.key.value.startsWith("zztile"))
        .map(x => x.key -> x.undated)
        .toSet
    assertEquals(withUndated(Year(-1295), Year(-1295)), Set("zztile_e1" -> 0, "zztile_e3" -> 1))
    def media(window: Option[(SignedYear, SignedYear)], license: Option[MediaItemRow.License] = None) =
      connect(ds)(
        TileRepo.cells(
          AssetFixtures.tileCandidates(
            None,
            Some(MediaItemRow.Provider("zztile")),
            license,
            window,
            TileRepo.envelope(c)
          ),
          c,
          grid
        )
      )
        .filter(_.isItem)
        .flatMap(x => AssetFixtures.geoOf(ds, List(MediaItemRow.Uid(x.key.value))).values.map(_.assetName))
        .toSet
    assertEquals(media(Some((SignedYear(-1250L), SignedYear(-1250L)))), Set("Zz one"))
    assertEquals(media(None), Set("Zz one", "Zz two"))
    assertEquals(media(None, Some(MediaItemRow.License("none"))), Set("Zz two"))

  test("versions: a write moves its group's version, once per transaction, and folding keeps it"):
    val before = connect(ds)(TileRepo.versions)
    site(domain.catalogue.Place.Id("zztile_v1"), Place.Label("Zztile V"), "int8range(1, 2, '[]')")
    val after = connect(ds)(TileRepo.versions)
    assertEquals(
      after.getOrElse(TileRepo.GroupName("places"), UndatedBuild.Version(0L)).value,
      before.getOrElse(TileRepo.GroupName("places"), UndatedBuild.Version(0L)).value + 2
    ) // the insert's transaction, then the update's
    assertEquals(after.getOrElse(TileRepo.GroupName("media"), 0L), before.getOrElse(TileRepo.GroupName("media"), 0L))
    transact(ds):
      val _ = sql"UPDATE places SET label = 'Zztile V2' WHERE id = 'zztile_v1'".update.run()
      val _ = sql"UPDATE places SET label = 'Zztile V3' WHERE id = 'zztile_v1'".update.run()
    assertEquals(
      connect(ds)(TileRepo.versions)(TileRepo.GroupName("places")),
      after(TileRepo.GroupName("places")).value + 1
    )
    val v = connect(ds)(TileRepo.versions)
    transact(ds)(TileRepo.compact(TileRepo.GroupName("places")))
    assertEquals(connect(ds)(TileRepo.versions), v)
    assertEquals(connect(ds)(TileRepo.changeRows)(TileRepo.GroupName("places")), 1)
