package com.histmersion.domain.catalogue.maptiles

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
import com.histmersion.util.database.{Migrate, Param, Sql}
import com.histmersion.db.*
import com.histmersion.api.catalogue.{Period, SiteTile}
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Place

/** The map's tiles (`TileRepo`, V105) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): which sites,
  * events and pictures a tile holds for a span (a RANGE: every item whose dates overlap it), the grouping that keeps a
  * tile small and its counts honest, the site-type filter, the SQL Mercator against the Scala one, and the versions a
  * write moves. Fixtures sit around 100°W 70°S, where no other suite has any; ids carry `zztile`.
  */
class TileRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM places WHERE id LIKE 'zztile%' OR id LIKE 'wikidata_qzztile%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zztile%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider = 'zztile'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Lon = -100.0
  private val Lat = -70.0
  private val grid = TileGrid.of(maxZoom = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxZoom(11), maxPoints = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxPoints(600), cellPx = com.histmersion.domain.catalogue.maptiles.TileGrid.CellPx(32))

  /** The tile of zoom `z` that holds (`lat`, `lon`). */
  private def tileAt(z: Int, lat: Double = Lat, lon: Double = Lon): TileCoord =
    val (mx, my) = TileCoord.mercator(lat, lon)
    TileCoord.of(z, math.floor(mx * (1 << z)).toLong, math.floor(my * (1 << z)).toLong, com.histmersion.domain.catalogue.maptiles.TileCoord.Z(22)).toOption.get

  private def site(
      id: String,
      label: String,
      period: String,
      dx: Double = 0,
      dy: Double = 0,
      importSource: Option[String] = None,
      props: String = "{}",
      siteType: Option[String] = None
  ): Unit = connect(ds):
    val _ = Sql
      .of(
        s"INSERT INTO places (id, label, kind, period, geom)" +
          s" VALUES (?, ?, 'site', $period, ST_SetSRID(ST_MakePoint(?, ?), 4326))",
        Param.S(id),
        Param.S(label),
        Param.D(Lon + dx),
        Param.D(Lat + dy)
      )
      .frag
      .update
      .run()
    // the optional columns set apart: `Param` has no null
    val _ =
      sql"UPDATE places SET import_source = $importSource, properties = $props::jsonb, site_type = $siteType WHERE id = $id".update
        .run()

  private def keysIn(cells: List[TileCell]): Set[String] = cells.filter(_.isItem).map(_.key).toSet

  test("the SQL Mercator places a point where the Scala one does"):
    val c = tileAt(7, -33.9, 18.4)
    val (ux, uy) = TileRepo.unitsSql(c)
    val (x, y) = connect(ds):
      Sql
        .raw(s"SELECT $ux, $uy FROM (SELECT 18.4::float8 AS lon, -33.9::float8 AS lat) p")
        .frag
        .query[(Double, Double)]
        .run()
        .head
    val (sx, sy) = c.unitsOf(-33.9, 18.4)
    assertEqualsDouble(x, sx, 1e-6)
    assertEqualsDouble(y, sy, 1e-6)

  test(
    "sites: every site whose dates overlap the span, a dated import as much as a curated one; never an undated import"
  ):
    site("zztile_a", "Zztile A", "int8range(-2000, -1500, '[]')")
    site("zztile_b", "Zztile B", "int8range(-3000, -2500, '[]')", dx = 0.01)
    site("zztile_c", "Zztile C", "int8range(-1200, NULL)", dx = 0.02)
    site(
      "wikidata_qzztile1",
      "Zztile Wikidata",
      "int8range(-1550, -1400, '[]')",
      dx = 0.03,
      importSource = Some("wikidata")
    )
    site(
      "wikidata_qzztile2",
      "Zztile Undated",
      "int8range(NULL, NULL)",
      dx = 0.04,
      importSource = Some("wikidata"),
      props = """{"modern": true}"""
    )
    val c = tileAt(9)
    def shown(lo: Long, hi: Long) =
      keysIn(connect(ds)(TileRepo.siteCells(c, grid, (lo, hi), None, com.histmersion.domain.DeepTime.SignedYear(1500)))).filter(_.contains("zztile"))
    assertEquals(shown(-1600, -1000), Set("zztile_a", "zztile_c", "wikidata_qzztile1"))
    assertEquals(shown(-2600, -2600), Set("zztile_b"))
    // a single year is the range of that year; the undated import stays off the map even after modern-from
    assertEquals(shown(1800, 1800), Set("zztile_c"))
    val rec = connect(ds)(TileRepo.sitesOf(List(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_a"), com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_c"))))
    assertEquals(rec(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_a")), SiteTile(com.histmersion.api.catalogue.SiteTile.Id("zztile_a"), com.histmersion.api.catalogue.SiteTile.Label("Zztile A"), com.histmersion.api.catalogue.SiteTile.Kind("site"), None, com.histmersion.api.catalogue.SiteTile.Basis("dated"), Some(com.histmersion.api.catalogue.Year(-2000L)), Some(com.histmersion.api.catalogue.Year(-1500L))))
    assertEquals(rec(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_c")).periodEnd, None)

  test("sites: the site-type filter reads the same type the site is drawn with"):
    site(
      "zztile_t1",
      "Zztile Temple",
      "int8range(-500, -100, '[]')",
      props = """{"feature_types": "settlement,temple-2"}"""
    )
    site(
      "zztile_t2",
      "Zztile Town",
      "int8range(-500, -100, '[]')",
      dx = 0.01,
      props = """{"feature_types": "settlement"}"""
    )
    site("zztile_t3", "Zztile Fort", "int8range(-500, -100, '[]')", dx = 0.02, siteType = Some("fortification"))
    val c = tileAt(9)
    def of(t: String) = keysIn(connect(ds)(TileRepo.siteCells(c, grid, (com.histmersion.domain.DeepTime.SignedYear(-300), com.histmersion.domain.DeepTime.SignedYear(-300)), Some(t), com.histmersion.domain.DeepTime.SignedYear(1500))))
    assertEquals(of("sanctuary"), Set("zztile_t1"))
    assertEquals(of("settlement"), Set("zztile_t2"))
    assertEquals(of("fortification"), Set("zztile_t3"))
    assertEquals(
      connect(ds)(TileRepo.sitesOf(List(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_t1")))).get(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_t1")).flatMap(_.siteType),
      Some("sanctuary")
    )

  test(
    "undated sites: listed in a tile but those excepted, counted by squares, the kept counts the same after a rebuild"
  ):
    site(
      "zztile_u1",
      "Zztile Tower",
      "int8range(NULL, NULL)",
      importSource = Some("wikidata"),
      siteType = Some("tower")
    )
    site("zztile_u2", "Zztile Ruin", "int8range(NULL, NULL)", dx = 0.01, importSource = Some("geonames"))
    site("zztile_u3", "Zztile Ruin 2", "int8range(NULL, NULL)", dx = 0.3, dy = 0.3)
    site("zztile_d1", "Zztile Dated", "int8range(-500, -100, '[]')", dx = 0.02)
    val deep = tileAt(9)
    val listed =
      connect(ds)(UndatedSiteRepo.items(deep, None, except = List(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteId("zztile_u2")))).filter(_.key.startsWith("zztile"))
    assertEquals(listed.map(c => (c.key, c.undated)).toSet, Set("zztile_u1" -> 1))
    assertEquals(
      connect(ds)(UndatedSiteRepo.items(deep, Some(com.histmersion.domain.catalogue.maptiles.TileRepo.SiteType("tower")), Nil)).map(_.key).filter(_.startsWith("zztile")),
      List("zztile_u1")
    )
    val shallow = tileAt(4)
    val counted = connect(ds)(UndatedSiteRepo.counted(shallow, grid, None))
    val v = connect(ds)(UndatedSiteRepo.version())
    assert(transact(ds)(UndatedSiteRepo.rebuild(v, com.histmersion.domain.catalogue.maptiles.UndatedBuild.PerSide(16), com.histmersion.domain.catalogue.maptiles.UndatedBuild.MaxZoom(6))))
    assertEquals(connect(ds)(UndatedSiteRepo.built()).map(_.version), Some(v))
    val kept = connect(ds)(UndatedSiteRepo.kept(shallow, com.histmersion.domain.catalogue.maptiles.UndatedBuild.PerSide(16), None))
    assertEquals(kept.map(k => (k.gx, k.gy, k.n)).sortBy(_._1), counted.map(k => (k.gx, k.gy, k.n)).sortBy(_._1))
    kept.zip(counted.sortBy(_.gx)).foreach((k, c) => assertEqualsDouble(k.sumX, c.sumX, 1e-3))
    assertEquals(counted.map(_.n).sum, 3)
    // the dated site is no undated one, and the span's dated count marks no site as undated
    assertEquals(
      connect(ds)(TileRepo.items(TileRepo.datedSites(deep, (com.histmersion.domain.DeepTime.SignedYear(-300), com.histmersion.domain.DeepTime.SignedYear(-300)), None, com.histmersion.domain.DeepTime.SignedYear(1500)), deep))
        .filter(_.key.startsWith("zztile"))
        .map(c => (c.key, c.undated)),
      List("zztile_d1" -> 0)
    )

  test("undated sites: a write to one moves the undated version; a write to a dated site does not"):
    site("zztile_v1", "Zztile Dated V", "int8range(-500, -100, '[]')")
    val before = connect(ds)(UndatedSiteRepo.version())
    connect(ds)(sql"UPDATE places SET label = 'Zztile Dated W' WHERE id = 'zztile_v1'".update.run())
    assertEquals(connect(ds)(UndatedSiteRepo.version()), before)
    site("zztile_v2", "Zztile Undated V", "int8range(NULL, NULL)")
    val after = connect(ds)(UndatedSiteRepo.version())
    assert(after > before)
    connect(ds)(sql"DELETE FROM places WHERE id = 'zztile_v2'".update.run())
    assert(connect(ds)(UndatedSiteRepo.version()) > after)

  test("a place's record: where its dates come from, its designations and registers; anything malformed read as none"):
    site(
      "zztile_r1",
      "Zztile Castle",
      "int8range(1200, 1400, '[]')",
      importSource = Some("wikidata"),
      props = """{"date_source": "wikidata-p571", "periods": [{"id": "Q1", "label": "Byzantine"}],
        "heritage": [{"id": "Q2", "label": "listed monument", "start": "1922"}, "garbage"],
        "registers": {"P2186": ["GR-1", "GR-2"], "P1936": "99"}}"""
    )
    site(
      "zztile_r2",
      "Zztile Odd",
      "int8range(1200, 1400, '[]')",
      props = """{"heritage": {"id": "Q3"}, "registers": []}"""
    )
    val r = PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("zztile_r1")).get.record
    assertEquals(r.dateSource, Some("wikidata-p571"))
    assertEquals(r.periods, List("Byzantine"))
    assertEquals(r.designations, List(Place.Designation(com.histmersion.api.catalogue.Place.Designation.Id("Q2"), Some(com.histmersion.api.catalogue.Place.Designation.Label("listed monument")), Some(com.histmersion.api.catalogue.Place.Designation.Start("1922")), None)))
    assertEquals(
      r.registers,
      List(Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P1936"), com.histmersion.api.catalogue.Place.Register.Id("99")), Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P2186"), com.histmersion.api.catalogue.Place.Register.Id("GR-1")), Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P2186"), com.histmersion.api.catalogue.Place.Register.Id("GR-2")))
    )
    assert(PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("zztile_r2")).get.record.isEmpty)

  test("grouping: a tile of more items than it keeps alone counts them by cell, and the counts add up to every item"):
    // 900 sites on a 30 × 30 grid 0.01° apart: more than a tile keeps alone
    connect(ds):
      val _ = Sql
        .of(
          "INSERT INTO places (id, label, kind, period, geom) SELECT 'zztile_g' || i || '_' || j, 'Zztile G', 'site'," +
            " int8range(-1000 + i, -900 + j, '[]'), ST_SetSRID(ST_MakePoint(? + i * 0.01, ? + j * 0.01), 4326)" +
            " FROM generate_series(0, 29) i, generate_series(0, 29) j",
          Param.D(Lon),
          Param.D(Lat)
        )
        .frag
        .update
        .run()
    val small = TileGrid.of(maxZoom = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxZoom(11), maxPoints = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxPoints(100), cellPx = com.histmersion.domain.catalogue.maptiles.TileGrid.CellPx(32))
    List(3, 6, 9).foreach { z =>
      // every tile of zoom z the fixture touches
      val corners = for dx <- List(0.0, 0.3); dy <- List(0.0, 0.3) yield tileAt(z, Lat + dy, Lon + dx)
      val tiles = corners.distinct
      val cells = tiles
        .flatMap(t => connect(ds)(TileRepo.siteCells(t, small, (com.histmersion.domain.DeepTime.SignedYear(-2000), com.histmersion.domain.DeepTime.SignedYear(2000)), None, com.histmersion.domain.DeepTime.SignedYear(1500))))
        .filter(c => c.key.startsWith("zztile") || c.key.startsWith("c"))
      val ours = cells.filter(c => !c.isItem || c.key.startsWith("zztile_g"))
      assertEquals(ours.map(_.n).sum, 900, s"zoom $z")
      tiles.foreach(t =>
        assert(
          connect(ds)(
            TileRepo.siteCells(t, small, (com.histmersion.domain.DeepTime.SignedYear(-2000), com.histmersion.domain.DeepTime.SignedYear(2000)), None, com.histmersion.domain.DeepTime.SignedYear(1500))
          ).size <= (TileCoord.Extent / small.cellUnits) * (TileCoord.Extent / small.cellUnits)
        )
      )
      // a cell's box holds its items, its dates' range their middles
      ours.filterNot(_.isItem).foreach { c =>
        assert(c.west <= c.east && c.south <= c.north, c.toString)
        assert(c.midLo.exists(lo => c.midHi.exists(_ >= lo)), c.toString)
      }
    }
    // at the deepest tile every item is one feature
    val deepest = connect(ds)(TileRepo.siteCells(tileAt(11), small, (com.histmersion.domain.DeepTime.SignedYear(-2000), com.histmersion.domain.DeepTime.SignedYear(2000)), None, com.histmersion.domain.DeepTime.SignedYear(1500)))
    assert(deepest.forall(_.isItem), deepest.filterNot(_.isItem).take(3).toString)

  test("events and pictures: a span is a range of overlap; a picture's provider and licence narrow it"):
    connect(ds):
      val _ = sql"""INSERT INTO events (id, label, kind, not_before, not_after, geom)
                    VALUES ('zztile_e1', 'Zztile battle', 'battle', -1300, -1290, ST_SetSRID(ST_MakePoint($Lon, $Lat), 4326)),
                           ('zztile_e2', 'Zztile flood', 'flood', -800, -790, ST_SetSRID(ST_MakePoint(${Lon + 0.01}, $Lat), 4326))""".update
        .run()
      val _ = sql"""INSERT INTO assets (provider, provider_key, source_url, retrieved_at, name, mime, bytes, sha256, geom, period, license)
                    VALUES ('zztile', 'k1', 'https://example.org/1', now(), 'Zz one', 'image/jpeg', decode('00', 'hex'), 'zz1', ST_SetSRID(ST_MakePoint($Lon, $Lat), 4326), int8range(-1400, -1200, '[]'), 'CC0-1.0'),
                           ('zztile', 'k2', 'https://example.org/2', now(), 'Zz two', 'image/jpeg', decode('00', 'hex'), 'zz2', ST_SetSRID(ST_MakePoint($Lon, ${Lat + 0.01}), 4326), int8range(100, 200, '[]'), NULL)""".update
        .run()
    val c = tileAt(9)
    def events(lo: Long, hi: Long) =
      keysIn(
        connect(ds)(
          TileRepo.eventCells(
            c,
            grid,
            EventFixtures.listWhere(Filter(period = Some(Period(lo, hi))), PlaceScope.empty, None, None, None, None)
          )
        )
      ).filter(_.startsWith("zztile"))
    assertEquals(events(-1295, -1295), Set("zztile_e1"))
    assertEquals(events(-1500, 0), Set("zztile_e1", "zztile_e2"))
    assertEquals(EventFixtures.geoOf(ds, List(com.histmersion.domain.catalogue.events.BEventRow.Id("zztile_e1"))).get(com.histmersion.domain.catalogue.events.BEventRow.Id("zztile_e1")).map(_.label), Some("Zztile battle"))
    // an event with no known date: only when asked for, marked, whatever the span
    connect(ds):
      val _ = sql"""INSERT INTO events (id, label, kind, geom)
                    VALUES ('zztile_e3', 'Zztile quake', 'earthquake', ST_SetSRID(ST_MakePoint(${Lon + 0.02}, $Lat), 4326))""".update
        .run()
    assertEquals(events(-1500, 0), Set("zztile_e1", "zztile_e2"))
    def withUndated(lo: Long, hi: Long) =
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
        .filter(_.key.startsWith("zztile"))
        .map(x => x.key -> x.undated)
        .toSet
    assertEquals(withUndated(-1295, -1295), Set("zztile_e1" -> 0, "zztile_e3" -> 1))
    def media(window: Option[(Long, Long)], license: Option[String] = None) =
      connect(ds)(
        TileRepo.cells(
          AssetFixtures.tileCandidates(None, Some(com.histmersion.domain.media.assets.MediaItemRow.Provider("zztile")), license, window, TileRepo.envelope(c)),
          c,
          grid
        )
      )
        .filter(_.isItem)
        .flatMap(x => AssetFixtures.geoOf(ds, List(x.key)).values.map(_.assetName))
        .toSet
    assertEquals(media(Some((-1250L, -1250L))), Set("Zz one"))
    assertEquals(media(None), Set("Zz one", "Zz two"))
    assertEquals(media(None, Some("none")), Set("Zz two"))

  test("versions: a write moves its group's version, once per transaction, and folding keeps it"):
    val before = connect(ds)(TileRepo.versions)
    site("zztile_v1", "Zztile V", "int8range(1, 2, '[]')")
    val after = connect(ds)(TileRepo.versions)
    assertEquals(
      after.getOrElse(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places"), 0L),
      before.getOrElse(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places"), 0L) + 2
    ) // the insert's transaction, then the update's
    assertEquals(after.getOrElse(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("media"), 0L), before.getOrElse(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("media"), 0L))
    transact(ds):
      val _ = sql"UPDATE places SET label = 'Zztile V2' WHERE id = 'zztile_v1'".update.run()
      val _ = sql"UPDATE places SET label = 'Zztile V3' WHERE id = 'zztile_v1'".update.run()
    assertEquals(connect(ds)(TileRepo.versions)(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places")), after(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places")) + 1)
    val v = connect(ds)(TileRepo.versions)
    transact(ds)(TileRepo.compact(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places")))
    assertEquals(connect(ds)(TileRepo.versions), v)
    assertEquals(connect(ds)(TileRepo.changeRows)(com.histmersion.domain.catalogue.maptiles.TileRepo.GroupName("places")), 1)
