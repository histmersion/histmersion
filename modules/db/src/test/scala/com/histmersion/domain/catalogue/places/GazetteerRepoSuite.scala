package com.histmersion.domain.catalogue.places

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.database.given

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Geocode.KindHint

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}
import com.histmersion.util.database.Migrate
import com.histmersion.db.*

/** The external gazetteer sources in the store (#1064), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): joining by identifier only, provenance per row, idempotent re-runs, the sweep, the dry run, and the
  * geocoder reading a dated name. Every fixture id is `pleiades_99900…`, `zzgaz…` or an authority id in the 99900000…
  * range, and the sources are `lpf:zzgaz…` or the `wikidata`/`geonames` keys with refs nothing else uses.
  */
class GazetteerRepoSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Seed = "pleiades_999000001"

  // only this suite's rows: GazetteerLoadSuite (the server module, whose tests may run at the same
  // time) imports `lpf:zzgaz_hittite`, `lpf:zzgaz_kultepe` and `geonames` rows with 99900… ids too
  private val Mine = List("lpf:zzgaz", "lpf:zzgazwd", "lpf:zzgazgn", "wikidata")
  private def clean(): Unit = connect(ds):
    val _ =
      sql"DELETE FROM places WHERE id LIKE 'zzgaz%' OR id LIKE 'pleiades_99900%' OR id LIKE 'geonames_99900%' OR id LIKE 'wikidata_q99900%'".update
        .run()
    val _ =
      sql"DELETE FROM place_names WHERE import_source = ANY($Mine::text[]) AND (import_ref LIKE '%99900%' OR import_source LIKE 'lpf:zzgaz%')".update
        .run()
    val _ = sql"""DELETE FROM place_links WHERE (external_id LIKE '99900%' OR external_id LIKE 'Q99900%')
                  AND (import_source = ANY($Mine::text[]) OR import_source = '')""".update.run()
    val _ = sql"DELETE FROM gazetteer_sources WHERE key = ANY($Mine::text[]) AND key LIKE 'lpf:zzgaz%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()
  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()

  private def pt(lon: Double, lat: Double) = Some(
    GazGeometry.GeoJson(s"""{"type":"Point","coordinates":[$lon,$lat]}""")
  )

  private def rec(
      own: ExternalId,
      label: String,
      names: List[GazName] = Nil,
      links: List[GazLink] = Nil,
      kind: String = PlaceKind.Site,
      span: Span = Span.always
  ): GazRecord =
    GazRecord(
      own.key,
      Some(own),
      own.placeId,
      label,
      kind,
      pt(30.0, 40.0),
      span,
      Gazetteer.mergeNames(GazName(label) :: names),
      links,
      Some("fixture"),
      None
    )

  private def wd(q: String) = ExternalId(LinkScheme.Wikidata, q)
  private def gn(n: String) = ExternalId(LinkScheme.Geonames, n)

  /** `sweep = false` for `geonames`: GazetteerLoadSuite (another module, maybe running at the same time) holds rows of
    * that source, which a sweep would take.
    */
  private def run(
      source: String,
      version: String,
      rs: List[GazRecord],
      apply: Boolean = true,
      batch: Int = 100,
      kinds: Set[String] = Gazetteer.DefaultPointAreaKinds.toSet,
      sweep: Boolean = true
  ): GazetteerLoadCounts =
    connect(ds):
      val s = GazetteerRepo.session(source, version, apply, pointAreaKinds = kinds)
      val pruned = s.prune(batch)
      val c =
        rs.grouped(batch).map(s.batch).foldLeft(GazetteerLoadCounts(source, version, placesRemoved = pruned))(_ + _)
      if sweep then c + s.sweep() else c

  private def seedPlace(): Unit =
    val r = PlaceFixtures.upsertPlaces(
      ds,
      List(
        PlaceInput(
          Place(
            Seed,
            "Zzgazhattusa",
            PlaceKind.Site,
            Some("""{"type":"Point","coordinates":[34.6,40.0]}"""),
            Some(Period(-1650, -1180)),
            Some("Pleiades"),
            Some("""{"pleiades_uri":"https://pleiades.stoa.org/places/999000001"}""")
          ),
          List(PlaceName("Zzgazhattusa"))
        )
      )
    )
    assertEquals(r.places, 1)

  private def names(placeId: String): List[(String, String, String)] = connect(ds):
    sql"SELECT name, lang, import_source FROM place_names WHERE place_id = $placeId ORDER BY name, lang, import_source"
      .query[(String, String, String)]
      .run()
      .toList

  private def exists(id: String): Boolean =
    connect(ds)(sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty)

  test("a seed import links the seed's own ids, so an external record joins it by id, never by coordinates"):
    seedPlace()
    val links = connect(ds)(
      sql"SELECT scheme, external_id FROM place_links WHERE place_id = $Seed".query[(String, String)].run().toList
    )
    assertEquals(links, List("pleiades" -> "999000001"))
    // a Wikidata item that names the Pleiades place, and a GeoNames record that names the Wikidata item
    val w = rec(
      wd("Q999000001"),
      "Zzgazhattusa",
      List(GazName("Zzḫattuša", "hit", Span(Some(-1650), Some(-1180)))),
      List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close))
    )
    val c1 = run("lpf:zzgazwd", "v1", List(w))
    assertEquals((c1.created, c1.joined), (0L, 1L))
    val g = rec(
      gn("999000002"),
      "Zzgazbogazkale",
      List(GazName("Zzgazboğazköy", "tr")),
      List(GazLink(wd("Q999000001"), LinkMatch.Exact))
    )
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
    val span = connect(ds)(
      sql"SELECT lower(period), upper(period) - 1 FROM place_names WHERE place_id = $Seed AND lang = 'hit'"
        .query[(Option[Long], Option[Long])]
        .run()
        .head
    )
    assertEquals(span, (Some(-1650L), Some(-1180L)))

  test("a record leaves its class, point and dates on its own link — joined to a seed place or not (V077)"):
    seedPlace()
    val w = rec(
      wd("Q999000001"),
      "Zzgazhattusa",
      links = List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close)),
      kind = PlaceKind.Region,
      span = Span(Some(-1650), Some(-1180))
    ).copy(itemClass = Some("Q1620908"))
    val c1 = run("lpf:zzgazwd", "v1", List(w))
    assertEquals(c1.joined, 1L)
    type Item = (String, String, Option[String], Option[Double], Option[Double], Option[Long], Option[Long])
    def items(): List[Item] = connect(ds):
      sql"""SELECT scheme, external_id, item_class, ST_X(item_geom), ST_Y(item_geom), lower(item_period), upper(item_period) - 1
            FROM place_links WHERE place_id = $Seed ORDER BY scheme, external_id""".query[Item].run().toList
    assertEquals(
      items(),
      List(
        ("pleiades", "999000001", None, None, None, None, None),
        ("wikidata", "Q999000001", Some("Q1620908"), Some(30.0), Some(40.0), Some(-1650L), Some(-1180L))
      )
    )
    assert(run("lpf:zzgazwd", "v1", List(w)).unchanged, "a re-run changes nothing")
    // the same version saying something else about the item: only the link changes, and is counted
    val moved = run("lpf:zzgazwd", "v1", List(w.copy(geometry = pt(31.0, 40.5), itemClass = Some("Q3024240"))))
    assertEquals((moved.linksChanged, moved.namesChanged, moved.linksAdded), (1L, 0L, 0L))
    assert(!moved.unchanged && moved.describe.contains("links +0 −0 ~1"), moved.describe)
    assertEquals(items().last.take(5), ("wikidata", "Q999000001", Some("Q3024240"), Some(31.0), Some(40.5)))

  test("a re-run of the same version changes nothing; a seed re-import keeps the imported names"):
    seedPlace()
    val w = rec(
      wd("Q999000001"),
      "Zzgazhattusa",
      List(GazName("Zzgazhattuša", "de")),
      List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close))
    )
    val g = rec(gn("999000003"), "Zzgazlonely", List(GazName("Zzgazlonely2", "en", Span(None, Some(1935)))))
    val first = run("lpf:zzgazwd", "v1", List(w))
    assert(!first.unchanged)
    val firstG = run("lpf:zzgazgn", "v1", List(g))
    assertEquals(firstG.created, 1L)
    val before = (names(Seed), names("geonames_999000003"))
    val again = run("lpf:zzgazwd", "v1", List(w))
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
    val c = run("lpf:zzgazgn", "v2", List(a2))
    assertEquals(c.placesRemoved, 1L)
    assert(!exists("geonames_999000005"))
    assertEquals(names("geonames_999000004").map(_._1), List("Zzgaza", "Zzgaza new"))
    val version = connect(ds)(
      sql"SELECT DISTINCT import_version FROM place_names WHERE place_id = 'geonames_999000004'"
        .query[String]
        .run()
        .toList
    )
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
    assertEquals(
      PlaceFixtures.search(ds, Some("Zzgazhattusa"), None, 5).map(_.id).take(2).toList,
      List(Seed, "geonames_999000009")
    )
    assertEquals(PlaceFixtures.search(ds, Some("zq"), None, 5).map(_.id).toList, List("geonames_999000010"))

  test("the geocoder: a dated historical name wins for a finding in its period (#1064)"):
    // two imported places that both carry "Zzgazmikoyan" as an alternative name: on one it is dated
    // 1935–1956 (the Soviet name), on the other undated. Before #1064 the name's dates were not read and
    // the tie went to the smaller id, the undated one, at every date.
    val undated = GazRecord(
      "lpf:zzgaz:a",
      None,
      "zzgaz_a_farm",
      "Zzgaz farm",
      PlaceKind.Site,
      pt(44.0, 39.0),
      Span.always,
      Gazetteer.mergeNames(List(GazName("Zzgaz farm"), GazName("Zzgazmikoyan", "en"))),
      Nil,
      None,
      None
    )
    val dated = GazRecord(
      "lpf:zzgaz:b",
      None,
      "zzgaz_b_village",
      "Zzgaz village",
      PlaceKind.Site,
      pt(45.0, 39.5),
      Span.always,
      Gazetteer.mergeNames(List(GazName("Zzgaz village"), GazName("Zzgazmikoyan", "en", Span(Some(1935), Some(1956))))),
      Nil,
      None,
      None
    )
    run("lpf:zzgaz", "v1", List(undated, dated))
    def at(p: Option[Period]) =
      connect(ds)(PlaceFixtures.resolve("Zzgazmikoyan", KindHint.Unknown, p, cfg.places.geocodePlaceholderMin))
        .map(_.placeId)
    assertEquals(at(Some(Period(1940, 1945))), Some("zzgaz_b_village"))
    assertEquals(at(Some(Period(1990, 2000))), Some("zzgaz_a_farm"))
    assertEquals(at(None), Some("zzgaz_a_farm"))

  // ── an area known only by a point (2026-10-09) ────────────────────────────────────────────────

  private val Poly = Some(
    GazGeometry.GeoJson("""{"type":"Polygon","coordinates":[[[30,40],[31,40],[31,41],[30,40]]]}""")
  )
  private def importRefs(ref: String): Int = connect(ds):
    sql"""SELECT (SELECT count(*) FROM place_names WHERE import_ref = $ref)
               + (SELECT count(*) FROM place_links WHERE import_ref = $ref)
               + (SELECT count(*) FROM place_descriptions WHERE import_ref = $ref)""".query[Long].run().head.toInt

  test("a Wikidata/GeoNames region or polity with only a point makes no place; it still joins a seed place"):
    seedPlace()
    val joined = rec(
      wd("Q999000701"),
      "Zzgazhatti land",
      kind = PlaceKind.Region,
      span = Span(Some(-1650), Some(-1180)),
      links = List(GazLink(ExternalId(LinkScheme.Pleiades, "999000001"), LinkMatch.Close))
    ).copy(itemClass = Some("Q1620908"))
    val alone = rec(wd("Q999000702"), "Zzgaz point region", kind = PlaceKind.Region)
    val polity =
      rec(wd("Q999000703"), "Zzgaz point kingdom", kind = PlaceKind.Polity, span = Span(Some(-900), Some(-700)))
    val shaped = rec(wd("Q999000704"), "Zzgaz shaped region", kind = PlaceKind.Region).copy(geometry = Poly)
    val site = rec(wd("Q999000705"), "Zzgaz town")
    val c = run("wikidata", "v1", List(joined, alone, polity, shaped, site))
    assertEquals((c.created, c.joined, c.pointAreasSkipped), (2L, 1L, 2L), c.describe)
    assert(!exists("wikidata_q999000702") && !exists("wikidata_q999000703"), "no place for a point-only area")
    assert(exists("wikidata_q999000704") && exists("wikidata_q999000705"), "a shape and a site are kept")
    assertEquals(importRefs("wikidata:Q999000702"), 0, "no names, links or descriptions either")
    // the joined record's evidence is on the seed place, as before (V077)
    val item = connect(ds)(
      sql"""SELECT item_class, lower(item_period) FROM place_links
                                 WHERE place_id = $Seed AND scheme = 'wikidata' AND external_id = 'Q999000701'"""
        .query[(Option[String], Option[Long])]
        .run()
        .toList
    )
    assertEquals(item, List[(Option[String], Option[Long])]((Some("Q1620908"), Some(-1650L))))
    assert(names(Seed).exists(_._1 == "Zzgazhatti land"))
    assert(run("wikidata", "v1", List(joined, alone, polity, shaped, site)).unchanged, "a re-run changes nothing")
    // a GeoNames record naming the dropped item finds no place there: an area is dropped too, a site is its own
    val gArea = rec(
      gn("999000706"),
      "Zzgaz point region (gn)",
      kind = PlaceKind.Region,
      links = List(GazLink(wd("Q999000702"), LinkMatch.Exact))
    )
    val gSite = rec(gn("999000707"), "Zzgaz hamlet", links = List(GazLink(wd("Q999000702"), LinkMatch.Exact)))
    val g = connect(ds)(GazetteerRepo.session("geonames", "v1", apply = true).batch(List(gArea, gSite)))
    assertEquals((g.created, g.joined, g.pointAreasSkipped), (1L, 0L, 1L), g.describe)
    assert(!exists("geonames_999000706") && exists("geonames_999000707"))
    // "" keeps every record (the rule is the config's)
    val kept = run(
      "wikidata",
      "v1",
      List(rec(wd("Q999000708"), "Zzgaz kept region", kind = PlaceKind.Region)),
      kinds = Set.empty
    )
    assertEquals((kept.created, kept.pointAreasSkipped), (1L, 0L))

  test("a place an earlier import made for a point-only area goes on the next run, with what joined it"):
    val area = rec(wd("Q999000711"), "Zzgaz old region", kind = PlaceKind.Region)
    val first = run("wikidata", "v1", List(area), kinds = Set.empty)
    assertEquals(first.created, 1L)
    // a GeoNames site joined it before the rule
    val joiner = rec(gn("999000712"), "Zzgaz old region (gn)", links = List(GazLink(wd("Q999000711"), LinkMatch.Exact)))
    assertEquals(connect(ds)(GazetteerRepo.session("geonames", "v1", apply = true).batch(List(joiner))).joined, 1L)
    assert(names("wikidata_q999000711").exists(_._3 == "geonames"))
    val dry = run("wikidata", "v1", List(area), apply = false)
    assertEquals((dry.placesRemoved, dry.pointAreasSkipped), (1L, 1L), dry.describe)
    assert(exists("wikidata_q999000711"), "a dry run keeps it")
    val c = run("wikidata", "v1", List(area))
    assertEquals((c.placesRemoved, c.pointAreasSkipped, c.created), (1L, 1L, 0L), c.describe)
    assert(c.describe.contains("1 place(s) removed"), c.describe)
    assert(!exists("wikidata_q999000711"))
    assertEquals(importRefs("wikidata:Q999000711") + importRefs("geonames:999000712"), 0)
    assert(run("wikidata", "v1", List(area)).unchanged, "and it stays gone")

  test(
    "a batch never joins a place it removes: a record that named a point-only area's place makes its own (no prune)"
  ):
    // before the rule: a Wikidata point region, and a Wikidata site that joined it by a shared GeoNames id
    val area = rec(
      wd("Q999000721"),
      "Zzgaz doomed region",
      kind = PlaceKind.Region,
      links = List(GazLink(gn("999000723"), LinkMatch.Exact))
    )
    val site =
      rec(wd("Q999000722"), "Zzgaz town by the region", links = List(GazLink(gn("999000723"), LinkMatch.Exact)))
    val before = run("wikidata", "v1", List(area, site), kinds = Set.empty)
    assertEquals((before.created, before.joined), (1L, 1L))
    assert(names("wikidata_q999000721").exists(_._1 == "Zzgaz town by the region"))
    // one batch, the joiner FIRST, no prune: the area's place is doomed before anything is placed, so
    // the town makes its own place — which the area, sharing the GeoNames id, then joins (an area
    // known only by a point still joins a place its ids name; it only never makes one)
    val c = connect(ds):
      val s = GazetteerRepo.session("wikidata", "v1", apply = true)
      s.batch(List(site, area)) + s.sweep()
    assertEquals((c.placesRemoved, c.pointAreasSkipped, c.created, c.joined), (1L, 0L, 1L, 1L), c.describe)
    assert(!exists("wikidata_q999000721") && exists("wikidata_q999000722"))
    assertEquals(names("wikidata_q999000722").map(_._1), List("Zzgaz doomed region", "Zzgaz town by the region"))
    assert(run("wikidata", "v1", List(site, area)).unchanged, "and that is the steady state")
    // the other order: the area, met first, has nothing to join and is dropped; the town is its own
    clean()
    run("wikidata", "v1", List(area, site), kinds = Set.empty)
    val c2 = connect(ds):
      val s = GazetteerRepo.session("wikidata", "v1", apply = true)
      s.batch(List(area, site)) + s.sweep()
    assertEquals((c2.placesRemoved, c2.pointAreasSkipped, c2.created, c2.joined), (1L, 1L, 1L, 0L), c2.describe)
    assertEquals(names("wikidata_q999000722").map(_._1), List("Zzgaz town by the region"))

  test("prune: a real run removes the source's point-only areas before its first batch; a dry run leaves them"):
    val area = rec(wd("Q999000731"), "Zzgaz pruned region", kind = PlaceKind.Polity)
    run("wikidata", "v1", List(area), kinds = Set.empty)
    assert(exists("wikidata_q999000731"))
    connect(ds):
      assertEquals(GazetteerRepo.session("wikidata", "v1", apply = false).prune(10), 0L)
    assert(exists("wikidata_q999000731"))
    connect(ds):
      assertEquals(GazetteerRepo.session("geonames", "v1", apply = true).prune(10), 0L, "only the source's own")
      assertEquals(GazetteerRepo.session("lpf:zzgazwd", "v1", apply = true).prune(10), 0L, "never an LPF dataset's")
      assertEquals(GazetteerRepo.session("wikidata", "v1", apply = true, pointAreaKinds = Set.empty).prune(10), 0L)
    assert(exists("wikidata_q999000731"))
    connect(ds)(assert(GazetteerRepo.session("wikidata", "v1", apply = true).prune(1) >= 1L))
    assert(!exists("wikidata_q999000731"))

  test("evidence keeps a place: a point-only area an attestation names stays, its record on it"):
    val area = rec(wd("Q999000741"), "Zzgaz attested region", kind = PlaceKind.Region)
    run("wikidata", "v1", List(area), kinds = Set.empty)
    connect(ds):
      SourceFixtures.upsert(
        ds,
        Source(
          citekey = "zzgaz_src",
          title = "Gazetteer fixture",
          authors = List("T"),
          doi = None,
          year = Some(2026),
          venue = None,
          status = SourceStatus.Current,
          topics = Nil,
          regions = Nil,
          tags = Nil,
          period = None,
          summary = None,
          citedByCount = None,
          influentialCitations = None,
          provenance = None,
          calibreId = None,
          findings = Nil
        ),
        cfg.limits.chunkChars,
        cfg.places.pointToleranceKm
      )
      val _ = sql"""INSERT INTO attestations (citekey, target_type, target_id, quote)
                    VALUES ('zzgaz_src', 'place', 'wikidata_q999000741', 'here')""".update.run()
    try
      val c = run("wikidata", "v1", List(area))
      assertEquals((c.placesRemoved, c.pointAreasSkipped), (0L, 0L), c.describe)
      assert(exists("wikidata_q999000741"))
      assert(names("wikidata_q999000741").nonEmpty)
    finally connect(ds)(sql"DELETE FROM sources WHERE citekey = 'zzgaz_src'".update.run())
