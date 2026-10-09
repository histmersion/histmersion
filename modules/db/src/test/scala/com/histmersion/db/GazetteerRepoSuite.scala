package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.Geocode.KindHint

import com.augustnagro.magnum.*

/** The external gazetteer sources in the store (#1064), against a real PostGIS (HISTMERSION_TEST_DB_URL;
  * skipped otherwise): joining by identifier only, provenance per row, idempotent re-runs, the sweep,
  * the dry run, and the geocoder reading a dated name. Every fixture id is `pleiades_99900…`,
  * `zzgaz…` or an authority id in the 99900000… range, and the sources are `lpf:zzgaz…` or the
  * `wikidata`/`geonames` keys with refs nothing else uses. */
class GazetteerRepoSuite extends munit.FunSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Seed = "pleiades_999000001"

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM places WHERE id LIKE 'zzgaz%' OR id LIKE 'pleiades_99900%' OR id LIKE 'geonames_99900%' OR id LIKE 'wikidata_q99900%' OR id LIKE 'lpf_zzgaz%'".update.run()
    val _ = sql"DELETE FROM place_names WHERE import_ref LIKE '%99900%' OR import_source LIKE 'lpf:zzgaz%'".update.run()
    val _ = sql"DELETE FROM place_links WHERE external_id LIKE '99900%' OR external_id LIKE 'Q99900%'".update.run()
    val _ = sql"DELETE FROM gazetteer_sources WHERE key LIKE 'lpf:zzgaz%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterAll(): Unit  = if testDb.isDefined then clean()
  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()

  private def pt(lon: Double, lat: Double) = Some(GazGeometry.GeoJson(s"""{"type":"Point","coordinates":[$lon,$lat]}"""))

  private def rec(own: ExternalId, label: String, names: List[GazName] = Nil, links: List[GazLink] = Nil,
      kind: String = PlaceKind.Site, span: Span = Span.always): GazRecord =
    GazRecord(own.key, Some(own), own.placeId, label, kind, pt(30.0, 40.0), span,
      Gazetteer.mergeNames(GazName(label) :: names), links, Some("fixture"), None)

  private def wd(q: String) = ExternalId(LinkScheme.Wikidata, q)
  private def gn(n: String) = ExternalId(LinkScheme.Geonames, n)

  private def run(source: String, version: String, rs: List[GazRecord], apply: Boolean = true, batch: Int = 100): GazetteerLoadCounts =
    connect(ds):
      val s = GazetteerRepo.session(source, version, apply)
      val c = rs.grouped(batch).map(s.batch).foldLeft(GazetteerLoadCounts(source, version))(_ + _)
      c + s.sweep()

  private def seedPlace(): Unit =
    val r = PlaceRepo.upsertPlaces(ds, List(PlaceInput(
      Place(Seed, "Zzgazhattusa", PlaceKind.Site, Some("""{"type":"Point","coordinates":[34.6,40.0]}"""),
        Some(Period(-1650, -1180)), Some("Pleiades"), Some("""{"pleiades_uri":"https://pleiades.stoa.org/places/999000001"}""")),
      List(PlaceName("Zzgazhattusa")))))
    assertEquals(r.places, 1)

  private def names(placeId: String): List[(String, String, String)] = connect(ds):
    sql"SELECT name, lang, import_source FROM place_names WHERE place_id = $placeId ORDER BY name, lang, import_source"
      .query[(String, String, String)].run().toList

  private def exists(id: String): Boolean = connect(ds)(sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty)

  test("a seed import links the seed's own ids, so an external record joins it by id, never by coordinates"):
    seedPlace()
    val links = connect(ds)(sql"SELECT scheme, external_id FROM place_links WHERE place_id = $Seed".query[(String, String)].run().toList)
    assertEquals(links, List("pleiades" -> "999000001"))
    // a Wikidata item that names the Pleiades place, and a GeoNames record that names the Wikidata item
    val w = rec(wd("Q999000001"), "Zzgazhattusa", List(GazName("Zzḫattuša", "hit", Span(Some(-1650), Some(-1180)))),
      List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close)))
    val c1 = run("lpf:zzgazwd", "v1", List(w))
    assertEquals((c1.created, c1.joined), (0L, 1L))
    val g = rec(gn("999000002"), "Zzgazbogazkale", List(GazName("Zzgazboğazköy", "tr")), List(GazLink(wd("Q999000001"), LinkMatch.Exact)))
    // a GeoNames record at the SAME point that shares no id stays a place of its own
    val alone = rec(gn("999000003"), "Zzgazlonely")
    val c2 = run("lpf:zzgazgn", "v1", List(g, alone))
    assertEquals((c2.created, c2.joined), (1L, 1L))
    assert(!exists("wikidata_q999000001") && !exists("geonames_999000002"), "joined records make no place")
    assert(exists("geonames_999000003"))
    val ns = names(Seed)
    assert(ns.contains(("Zzḫattuša", "hit", "lpf:zzgazwd")), ns.toString)
    assert(ns.contains(("Zzgazboğazköy", "tr", "lpf:zzgazgn")), ns.toString)
    assert(ns.contains(("Zzgazhattusa", "", "")), "the seed's own name stays")
    // the dated name kept its span
    val span = connect(ds)(sql"SELECT lower(period), upper(period) - 1 FROM place_names WHERE place_id = $Seed AND lang = 'hit'"
      .query[(Option[Long], Option[Long])].run().head)
    assertEquals(span, (Some(-1650L), Some(-1180L)))

  test("a record leaves its class, point and dates on its own link — joined to a seed place or not (V075)"):
    seedPlace()
    val w = rec(wd("Q999000001"), "Zzgazhattusa", links = List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close)),
      kind = PlaceKind.Region, span = Span(Some(-1650), Some(-1180))).copy(itemClass = Some("Q1620908"))
    val c1 = run("lpf:zzgazwd", "v1", List(w))
    assertEquals(c1.joined, 1L)
    type Item = (String, String, Option[String], Option[Double], Option[Double], Option[Long], Option[Long])
    def items(): List[Item] = connect(ds):
      sql"""SELECT scheme, external_id, item_class, ST_X(item_geom), ST_Y(item_geom), lower(item_period), upper(item_period) - 1
            FROM place_links WHERE place_id = $Seed ORDER BY scheme, external_id""".query[Item].run().toList
    assertEquals(items(), List(
      ("pleiades", "999000001", None, None, None, None, None),
      ("wikidata", "Q999000001", Some("Q1620908"), Some(30.0), Some(40.0), Some(-1650L), Some(-1180L))))
    assert(run("lpf:zzgazwd", "v1", List(w)).unchanged, "a re-run changes nothing")
    val moved = run("lpf:zzgazwd", "v2", List(w.copy(geometry = pt(31.0, 40.5), itemClass = Some("Q3024240"))))
    assert(!moved.unchanged)
    assertEquals(items().last.take(5), ("wikidata", "Q999000001", Some("Q3024240"), Some(31.0), Some(40.5)))

  test("a re-run of the same version changes nothing; a seed re-import keeps the imported names"):
    seedPlace()
    val w = rec(wd("Q999000001"), "Zzgazhattusa", List(GazName("Zzgazhattuša", "de")),
      List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close)))
    val g = rec(gn("999000003"), "Zzgazlonely", List(GazName("Zzgazlonely2", "en", Span(None, Some(1935)))))
    val first = run("lpf:zzgazwd", "v1", List(w))
    assert(!first.unchanged)
    val firstG = run("lpf:zzgazgn", "v1", List(g))
    assertEquals(firstG.created, 1L)
    val before = (names(Seed), names("geonames_999000003"))
    val again  = run("lpf:zzgazwd", "v1", List(w))
    val againG = run("lpf:zzgazgn", "v1", List(g))
    assert(again.unchanged, again.describe)
    assert(againG.unchanged, againG.describe)
    assertEquals((names(Seed), names("geonames_999000003")), before)
    seedPlace()
    assertEquals(names(Seed), before._1, "a re-seed replaces only the seed's own names")

  test("a new version: dropped records and names go (the sweep), changed ones move"):
    val a = rec(gn("999000004"), "Zzgaza", List(GazName("Zzgaza old", "en")))
    val b = rec(gn("999000005"), "Zzgazb")
    run("lpf:zzgazgn", "v1", List(a, b))
    assert(exists("geonames_999000004") && exists("geonames_999000005"))
    val a2 = rec(gn("999000004"), "Zzgaza", List(GazName("Zzgaza new", "en")))
    val c  = run("lpf:zzgazgn", "v2", List(a2))
    assertEquals(c.placesRemoved, 1L)
    assert(!exists("geonames_999000005"))
    assertEquals(names("geonames_999000004").map(_._1), List("Zzgaza", "Zzgaza new"))
    val version = connect(ds)(sql"SELECT DISTINCT import_version FROM place_names WHERE place_id = 'geonames_999000004'".query[String].run().toList)
    assertEquals(version, List("v2"))

  test("a dry run reports what it would do and keeps nothing"):
    val r = rec(gn("999000006"), "Zzgazdry")
    val c = run("lpf:zzgazgn", "v1", List(r), apply = false)
    assertEquals(c.created, 1L)
    assert(c.namesAdded >= 1L)
    assert(!exists("geonames_999000006"))

  test("two records of one batch that share an id end on one place"):
    val x = rec(gn("999000007"), "Zzgazx", links = List(GazLink(wd("Q999000007"), LinkMatch.Exact)))
    val y = rec(gn("999000008"), "Zzgazy", links = List(GazLink(wd("Q999000007"), LinkMatch.Exact)))
    val c = run("lpf:zzgazgn", "v1", List(x, y))
    assertEquals((c.created, c.joined), (1L, 1L))
    assert(!exists("geonames_999000008"))
    assertEquals(names("geonames_999000007").map(_._1), List("Zzgazx", "Zzgazy"))
    assert(run("lpf:zzgazgn", "v1", List(x, y)).unchanged)

  test("a link a joining record copied never pulls another record away on the next run (Varaždin)"):
    // Wikidata: Wa names GeoNames X, Wb names GeoNames Y. GeoNames X and Y both name Wb (X wrongly).
    // X joins Wa by its own id and copies wd:Wb there; Y, read BEFORE X, joins Wb by its own id. On
    // the next run Y must stay on Wb, although Wa (the smaller id) now carries a copy of wd:Wb.
    val wa = rec(wd("Q999000501"), "Zzgaz Grad Varazdin", links = List(GazLink(gn("999000611"), LinkMatch.Exact)))
    val wb = rec(wd("Q999000502"), "Zzgaz Varazdin", links = List(GazLink(gn("999000612"), LinkMatch.Exact)))
    run("lpf:zzgazwd", "v1", List(wa, wb))
    val y = rec(gn("999000612"), "Zzgaz Varaždin", links = List(GazLink(wd("Q999000502"), LinkMatch.Exact)))
    val x = rec(gn("999000611"), "Zzgaz Grad Varaždin", links = List(GazLink(wd("Q999000502"), LinkMatch.Exact)))
    val first = run("lpf:zzgazgn", "v1", List(y, x), batch = 1)
    assertEquals((first.created, first.joined), (0L, 2L))
    assert(names("wikidata_q999000502").exists(_._1 == "Zzgaz Varaždin"))
    val again = run("lpf:zzgazgn", "v1", List(y, x), batch = 1)
    assert(again.unchanged, again.describe)
    assert(names("wikidata_q999000502").exists(_._1 == "Zzgaz Varaždin"))

  test("place search: a short query matches by key, and the seed lists before an imported namesake"):
    seedPlace()
    run("lpf:zzgazgn", "v1", List(rec(gn("999000009"), "Zzgazhattusa"), rec(gn("999000010"), "Zq")))
    assertEquals(PlaceRepo.search(ds, Some("Zzgazhattusa"), None, 5).map(_.id).take(2).toList,
      List(Seed, "geonames_999000009"))
    assertEquals(PlaceRepo.search(ds, Some("zq"), None, 5).map(_.id).toList, List("geonames_999000010"))

  test("the geocoder: a dated historical name wins for a finding in its period (#1064)"):
    // two imported places that both carry "Zzgazmikoyan" as an alternative name: on one it is dated
    // 1935–1956 (the Soviet name), on the other undated. Before #1064 the name's dates were not read and
    // the tie went to the smaller id, the undated one, at every date.
    val undated = GazRecord("lpf:zzgaz:a", None, "zzgaz_a_farm", "Zzgaz farm", PlaceKind.Site, pt(44.0, 39.0), Span.always,
      Gazetteer.mergeNames(List(GazName("Zzgaz farm"), GazName("Zzgazmikoyan", "en"))), Nil, None, None)
    val dated = GazRecord("lpf:zzgaz:b", None, "zzgaz_b_village", "Zzgaz village", PlaceKind.Site, pt(45.0, 39.5), Span.always,
      Gazetteer.mergeNames(List(GazName("Zzgaz village"), GazName("Zzgazmikoyan", "en", Span(Some(1935), Some(1956))))), Nil, None, None)
    run("lpf:zzgaz", "v1", List(undated, dated))
    def at(p: Option[Period]) = Geocoder.resolve(ds, "Zzgazmikoyan", KindHint.Unknown, p, cfg.places.geocodePlaceholderMin).map(_.placeId)
    assertEquals(at(Some(Period(1940, 1945))), Some("zzgaz_b_village"))
    assertEquals(at(Some(Period(1990, 2000))), Some("zzgaz_a_farm"))
    assertEquals(at(None), Some("zzgaz_a_farm"))
