package com.histmersion
package domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.database.given

import com.histmersion.api.Span
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Geocode.KindHint

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.db.*
import com.histmersion.api.catalogue.{
  Gazetteer,
  GazGeometry,
  GazLink,
  GazName,
  GazRecord,
  Latitude,
  LinkMatch,
  LinkScheme,
  Longitude,
  Period,
  Place,
  Year
}
import com.histmersion.api.library.{ExternalId, Source}
import com.histmersion.db.DbSuite
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.library.Citekey
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.util.database.TestCreators

/** The external gazetteer sources in the store (#1064), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): joining by identifier only, provenance per row, idempotent re-runs, the sweep, the dry run, and the
  * geocoder reading a dated name. Every fixture id is `pleiades_99900…`, `zzgaz…` or an authority id in the 99900000…
  * range, and the sources are `lpf:zzgaz…` or the `wikidata`/`geonames` keys with refs nothing else uses.
  */
class GazetteerRepoSuite extends DbSuite with db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val Seed = "pleiades_999000001"

  private val LpfWd = Gazetteer.SourceInfo.Key("lpf:zzgazwd")
  private val LpfGn = Gazetteer.SourceInfo.Key("lpf:zzgazgn")
  private val Lpf = Gazetteer.SourceInfo.Key("lpf:zzgaz")
  private val Wikidata = Gazetteer.SourceInfo.Key("wikidata")
  private val Geonames = Gazetteer.SourceInfo.Key("geonames")
  private val V1 = Gazetteer.LoadCounts.Version("v1")
  private val V2 = Gazetteer.LoadCounts.Version("v2")

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

  private def pt(lon: Longitude, lat: Latitude) = Some(
    GazGeometry.GeoJson(GazGeometry.GeoJson.Text(s"""{"type":"Point","coordinates":[$lon,$lat]}"""))
  )

  private def rec(
      own: ExternalId,
      label: GazRecord.Label,
      names: List[GazName] = Nil,
      links: List[GazLink] = Nil,
      kind: GazRecord.Kind = GazRecord.Kind(Place.Kind.Site),
      span: Span = Span.always
  ): GazRecord =
    GazRecord(
      GazRecord.Ref(own.key.value),
      Some(own),
      Place.Id(own.placeId.value),
      label,
      kind,
      pt(Longitude(30.0), Latitude(40.0)),
      span,
      Gazetteer.mergeNames(GazName(GazName.Name(label.value)) :: names),
      links,
      Some(GazRecord.Source("fixture")),
      None
    )

  private def wd(q: ExternalId.Id) = ExternalId(LinkScheme.Wikidata, q)
  private def gn(n: ExternalId.Id) = ExternalId(LinkScheme.Geonames, n)

  /** `sweep = false` for `geonames`: GazetteerLoadSuite (another module, maybe running at the same time) holds rows of
    * that source, which a sweep would take.
    */
  private def run(
      source: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      rs: List[GazRecord],
      apply: Boolean = true,
      batch: Int = 100,
      kinds: Set[Gazetteer.PointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet,
      sweep: Boolean = true
  ): Gazetteer.LoadCounts =
    connect(ds):
      val s = GazetteerImport.session(source, version, apply, pointAreaKinds = kinds, clock = TestCreators.clock)
      val pruned = s.prune(batch)
      val c =
        rs.grouped(batch)
          .map(s.batch)
          .foldLeft(
            Gazetteer.LoadCounts(source, version, placesRemoved = Gazetteer.LoadCounts.PlacesRemoved(pruned.value))
          )(_ + _)
      if sweep then c + s.sweep() else c

  private def seedPlace(): Unit =
    val r = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(
            Place.Id(Seed),
            Place.Label("Zzgazhattusa"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[34.6,40.0]}""")),
            Some(Period(Year(-1650), Year(-1180))),
            Some(Place.Source("Pleiades")),
            Some(Place.Properties("""{"pleiades_uri":"https://pleiades.stoa.org/places/999000001"}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzgazhattusa")))
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
      wd(ExternalId.Id("Q999000001")),
      GazRecord.Label("Zzgazhattusa"),
      List(GazName(GazName.Name("Zzḫattuša"), GazName.Lang("hit"), Span(Some(Year(-1650)), Some(Year(-1180))))),
      List(GazLink(ExternalId(LinkScheme.Pleiades, ExternalId.Id("999000001")), LinkMatch.Close))
    )
    val c1 = run(LpfWd, V1, List(w))
    assertEquals((c1.created, c1.joined), (0L, 1L))
    val g = rec(
      gn(ExternalId.Id("999000002")),
      GazRecord.Label("Zzgazbogazkale"),
      List(GazName(GazName.Name("Zzgazboğazköy"), GazName.Lang("tr"))),
      List(GazLink(wd(ExternalId.Id("Q999000001")), LinkMatch.Exact))
    )
    // a GeoNames record at the SAME point that shares no id stays a place of its own
    val alone = rec(gn(ExternalId.Id("999000003")), GazRecord.Label("Zzgazlonely"))
    val c2 = run(LpfGn, V1, List(g, alone))
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
      wd(ExternalId.Id("Q999000001")),
      GazRecord.Label("Zzgazhattusa"),
      links = List(GazLink(ExternalId(LinkScheme.Pleiades, ExternalId.Id("999000001")), LinkMatch.Close)),
      kind = GazRecord.Kind(Place.Kind.Region),
      span = Span(Some(Year(-1650)), Some(Year(-1180)))
    ).modify(_.itemClass).setTo(Some(GazRecord.ItemClass("Q1620908")))
    val c1 = run(LpfWd, V1, List(w))
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
    assert(run(LpfWd, V1, List(w)).unchanged, "a re-run changes nothing")
    // the same version saying something else about the item: only the link changes, and is counted
    val moved = run(
      LpfWd,
      V1,
      List(
        w.modify(_.geometry)
          .setTo(pt(Longitude(31.0), Latitude(40.5)))
          .modify(_.itemClass)
          .setTo(Some(GazRecord.ItemClass("Q3024240")))
      )
    )
    assertEquals((moved.linksChanged, moved.namesChanged, moved.linksAdded), (1L, 0L, 0L))
    assert(!moved.unchanged && moved.describe.contains("links +0 −0 ~1"), moved.describe)
    assertEquals(items().last.take(5), ("wikidata", "Q999000001", Some("Q3024240"), Some(31.0), Some(40.5)))

  test("a re-run of the same version changes nothing; a seed re-import keeps the imported names"):
    seedPlace()
    val w = rec(
      wd(ExternalId.Id("Q999000001")),
      GazRecord.Label("Zzgazhattusa"),
      List(GazName(GazName.Name("Zzgazhattuša"), GazName.Lang("de"))),
      List(GazLink(ExternalId(LinkScheme.Pleiades, ExternalId.Id("999000001")), LinkMatch.Close))
    )
    val g = rec(
      gn(ExternalId.Id("999000003")),
      GazRecord.Label("Zzgazlonely"),
      List(GazName(GazName.Name("Zzgazlonely2"), GazName.Lang("en"), Span(None, Some(Year(1935)))))
    )
    val first = run(LpfWd, V1, List(w))
    assert(!first.unchanged)
    val firstG = run(LpfGn, V1, List(g))
    assertEquals(firstG.created, 1L)
    val before = (names(Seed), names("geonames_999000003"))
    val again = run(LpfWd, V1, List(w))
    val againG = run(LpfGn, V1, List(g))
    assert(again.unchanged, again.describe)
    assert(againG.unchanged, againG.describe)
    assertEquals((names(Seed), names("geonames_999000003")), before)
    seedPlace()
    assertEquals(names(Seed), before._1, "a re-seed replaces only the seed's own names")

  test("a new version: dropped records and names go (the sweep), changed ones move"):
    val a = rec(
      gn(ExternalId.Id("999000004")),
      GazRecord.Label("Zzgaza"),
      List(GazName(GazName.Name("Zzgaza old"), GazName.Lang("en")))
    )
    val b = rec(gn(ExternalId.Id("999000005")), GazRecord.Label("Zzgazb"))
    run(LpfGn, V1, List(a, b))
    assert(exists("geonames_999000004") && exists("geonames_999000005"))
    val a2 = rec(
      gn(ExternalId.Id("999000004")),
      GazRecord.Label("Zzgaza"),
      List(GazName(GazName.Name("Zzgaza new"), GazName.Lang("en")))
    )
    val c = run(LpfGn, V2, List(a2))
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
    val r = rec(gn(ExternalId.Id("999000006")), GazRecord.Label("Zzgazdry"))
    val c = run(LpfGn, V1, List(r), apply = false)
    assertEquals(c.created, 1L)
    assert(c.namesAdded.value >= 1L)
    assert(!exists("geonames_999000006"))

  test("two records of one batch that share an id end on one place"):
    val x = rec(
      gn(ExternalId.Id("999000007")),
      GazRecord.Label("Zzgazx"),
      links = List(GazLink(wd(ExternalId.Id("Q999000007")), LinkMatch.Exact))
    )
    val y = rec(
      gn(ExternalId.Id("999000008")),
      GazRecord.Label("Zzgazy"),
      links = List(GazLink(wd(ExternalId.Id("Q999000007")), LinkMatch.Exact))
    )
    val c = run(LpfGn, V1, List(x, y))
    assertEquals((c.created, c.joined), (1L, 1L))
    assert(!exists("geonames_999000008"))
    assertEquals(names("geonames_999000007").map(_._1), List("Zzgazx", "Zzgazy"))
    assert(run(LpfGn, V1, List(x, y)).unchanged)

  test("a link a joining record copied never pulls another record away on the next run (Varaždin)"):
    // Wikidata: Wa names GeoNames X, Wb names GeoNames Y. GeoNames X and Y both name Wb (X wrongly).
    // X joins Wa by its own id and copies wd:Wb there; Y, read BEFORE X, joins Wb by its own id. On
    // the next run Y must stay on Wb, although Wa (the smaller id) now carries a copy of wd:Wb.
    val wa = rec(
      wd(ExternalId.Id("Q999000501")),
      GazRecord.Label("Zzgaz Grad Varazdin"),
      links = List(GazLink(gn(ExternalId.Id("999000611")), LinkMatch.Exact))
    )
    val wb = rec(
      wd(ExternalId.Id("Q999000502")),
      GazRecord.Label("Zzgaz Varazdin"),
      links = List(GazLink(gn(ExternalId.Id("999000612")), LinkMatch.Exact))
    )
    run(LpfWd, V1, List(wa, wb))
    val y = rec(
      gn(ExternalId.Id("999000612")),
      GazRecord.Label("Zzgaz Varaždin"),
      links = List(GazLink(wd(ExternalId.Id("Q999000502")), LinkMatch.Exact))
    )
    val x = rec(
      gn(ExternalId.Id("999000611")),
      GazRecord.Label("Zzgaz Grad Varaždin"),
      links = List(GazLink(wd(ExternalId.Id("Q999000502")), LinkMatch.Exact))
    )
    val first = run(LpfGn, V1, List(y, x), batch = 1)
    assertEquals((first.created, first.joined), (0L, 2L))
    assert(names("wikidata_q999000502").exists(_._1 == "Zzgaz Varaždin"))
    val again = run(LpfGn, V1, List(y, x), batch = 1)
    assert(again.unchanged, again.describe)
    assert(names("wikidata_q999000502").exists(_._1 == "Zzgaz Varaždin"))

  test("place search: a short query matches by key, and the seed lists before an imported namesake"):
    seedPlace()
    run(
      LpfGn,
      V1,
      List(
        rec(gn(ExternalId.Id("999000009")), GazRecord.Label("Zzgazhattusa")),
        rec(gn(ExternalId.Id("999000010")), GazRecord.Label("Zq"))
      )
    )
    assertEquals(
      PlaceFixtures
        .search(ds, Some(PlaceRepo.SearchText("Zzgazhattusa")), None, PlaceRepo.Limit(5))
        .map(_.id)
        .take(2)
        .toList,
      List(Seed, "geonames_999000009")
    )
    assertEquals(
      PlaceFixtures.search(ds, Some(PlaceRepo.SearchText("zq")), None, PlaceRepo.Limit(5)).map(_.id).toList,
      List("geonames_999000010")
    )

  test("the geocoder: a dated historical name wins for a finding in its period (#1064)"):
    // two imported places that both carry "Zzgazmikoyan" as an alternative name: on one it is dated
    // 1935–1956 (the Soviet name), on the other undated. Before #1064 the name's dates were not read and
    // the tie went to the smaller id, the undated one, at every date.
    val undated = GazRecord(
      GazRecord.Ref("lpf:zzgaz:a"),
      None,
      Place.Id("zzgaz_a_farm"),
      GazRecord.Label("Zzgaz farm"),
      GazRecord.Kind(Place.Kind.Site),
      pt(Longitude(44.0), Latitude(39.0)),
      Span.always,
      Gazetteer.mergeNames(
        List(GazName(GazName.Name("Zzgaz farm")), GazName(GazName.Name("Zzgazmikoyan"), GazName.Lang("en")))
      ),
      Nil,
      None,
      None
    )
    val dated = GazRecord(
      GazRecord.Ref("lpf:zzgaz:b"),
      None,
      Place.Id("zzgaz_b_village"),
      GazRecord.Label("Zzgaz village"),
      GazRecord.Kind(Place.Kind.Site),
      pt(Longitude(45.0), Latitude(39.5)),
      Span.always,
      Gazetteer.mergeNames(
        List(
          GazName(GazName.Name("Zzgaz village")),
          GazName(GazName.Name("Zzgazmikoyan"), GazName.Lang("en"), Span(Some(Year(1935)), Some(Year(1956))))
        )
      ),
      Nil,
      None,
      None
    )
    run(Lpf, V1, List(undated, dated))
    def at(p: Option[Period]) =
      connect(ds)(
        PlaceFixtures.resolve(
          Geocode.Attempt.Name("Zzgazmikoyan"),
          KindHint.Unknown,
          p,
          GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value)
        )
      )
        .map(_.placeId)
    assertEquals(at(Some(Period(Year(1940), Year(1945)))), Some("zzgaz_b_village"))
    assertEquals(at(Some(Period(Year(1990), Year(2000)))), Some("zzgaz_a_farm"))
    assertEquals(at(None), Some("zzgaz_a_farm"))

  // ── an area known only by a point (2026-10-09) ────────────────────────────────────────────────

  private val Poly = Some(
    GazGeometry.GeoJson(
      GazGeometry.GeoJson.Text("""{"type":"Polygon","coordinates":[[[30,40],[31,40],[31,41],[30,40]]]}""")
    )
  )
  private def importRefs(ref: String): Int = connect(ds):
    sql"""SELECT (SELECT count(*) FROM place_names WHERE import_ref = $ref)
               + (SELECT count(*) FROM place_links WHERE import_ref = $ref)
               + (SELECT count(*) FROM place_descriptions WHERE import_ref = $ref)""".query[Long].run().head.toInt

  test("a Wikidata/GeoNames region or polity with only a point makes no place; it still joins a seed place"):
    seedPlace()
    val joined = rec(
      wd(ExternalId.Id("Q999000701")),
      GazRecord.Label("Zzgazhatti land"),
      kind = GazRecord.Kind(Place.Kind.Region),
      span = Span(Some(Year(-1650)), Some(Year(-1180))),
      links = List(GazLink(ExternalId(LinkScheme.Pleiades, ExternalId.Id("999000001")), LinkMatch.Close))
    ).modify(_.itemClass).setTo(Some(GazRecord.ItemClass("Q1620908")))
    val alone = rec(
      wd(ExternalId.Id("Q999000702")),
      GazRecord.Label("Zzgaz point region"),
      kind = GazRecord.Kind(Place.Kind.Region)
    )
    val polity =
      rec(
        wd(ExternalId.Id("Q999000703")),
        GazRecord.Label("Zzgaz point kingdom"),
        kind = GazRecord.Kind(Place.Kind.Polity),
        span = Span(Some(Year(-900)), Some(Year(-700)))
      )
    val shaped = rec(
      wd(ExternalId.Id("Q999000704")),
      GazRecord.Label("Zzgaz shaped region"),
      kind = GazRecord.Kind(Place.Kind.Region)
    )
      .modify(_.geometry)
      .setTo(Poly)
    val site = rec(wd(ExternalId.Id("Q999000705")), GazRecord.Label("Zzgaz town"))
    val c = run(Wikidata, V1, List(joined, alone, polity, shaped, site))
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
    assert(run(Wikidata, V1, List(joined, alone, polity, shaped, site)).unchanged, "a re-run changes nothing")
    // a GeoNames record naming the dropped item finds no place there: an area is dropped too, a site is its own
    val gArea = rec(
      gn(ExternalId.Id("999000706")),
      GazRecord.Label("Zzgaz point region (gn)"),
      kind = GazRecord.Kind(Place.Kind.Region),
      links = List(GazLink(wd(ExternalId.Id("Q999000702")), LinkMatch.Exact))
    )
    val gSite = rec(
      gn(ExternalId.Id("999000707")),
      GazRecord.Label("Zzgaz hamlet"),
      links = List(GazLink(wd(ExternalId.Id("Q999000702")), LinkMatch.Exact))
    )
    val g = connect(ds)(
      GazetteerImport.session(Geonames, V1, apply = true, clock = TestCreators.clock).batch(List(gArea, gSite))
    )
    assertEquals((g.created, g.joined, g.pointAreasSkipped), (1L, 0L, 1L), g.describe)
    assert(!exists("geonames_999000706") && exists("geonames_999000707"))
    // "" keeps every record (the rule is the config's)
    val kept = run(
      Wikidata,
      V1,
      List(
        rec(
          wd(ExternalId.Id("Q999000708")),
          GazRecord.Label("Zzgaz kept region"),
          kind = GazRecord.Kind(Place.Kind.Region)
        )
      ),
      kinds = Set.empty
    )
    assertEquals((kept.created, kept.pointAreasSkipped), (1L, 0L))

  test("a place an earlier import made for a point-only area goes on the next run, with what joined it"):
    val area = rec(
      wd(ExternalId.Id("Q999000711")),
      GazRecord.Label("Zzgaz old region"),
      kind = GazRecord.Kind(Place.Kind.Region)
    )
    val first = run(Wikidata, V1, List(area), kinds = Set.empty)
    assertEquals(first.created, 1L)
    // a GeoNames site joined it before the rule
    val joiner = rec(
      gn(ExternalId.Id("999000712")),
      GazRecord.Label("Zzgaz old region (gn)"),
      links = List(GazLink(wd(ExternalId.Id("Q999000711")), LinkMatch.Exact))
    )
    assertEquals(
      connect(ds)(
        GazetteerImport.session(Geonames, V1, apply = true, clock = TestCreators.clock).batch(List(joiner))
      ).joined,
      1L
    )
    assert(names("wikidata_q999000711").exists(_._3 == "geonames"))
    val dry = run(Wikidata, V1, List(area), apply = false)
    assertEquals((dry.placesRemoved, dry.pointAreasSkipped), (1L, 1L), dry.describe)
    assert(exists("wikidata_q999000711"), "a dry run keeps it")
    val c = run(Wikidata, V1, List(area))
    assertEquals((c.placesRemoved, c.pointAreasSkipped, c.created), (1L, 1L, 0L), c.describe)
    assert(c.describe.contains("1 place(s) removed"), c.describe)
    assert(!exists("wikidata_q999000711"))
    assertEquals(importRefs("wikidata:Q999000711") + importRefs("geonames:999000712"), 0)
    assert(run(Wikidata, V1, List(area)).unchanged, "and it stays gone")

  test(
    "a batch never joins a place it removes: a record that named a point-only area's place makes its own (no prune)"
  ):
    // before the rule: a Wikidata point region, and a Wikidata site that joined it by a shared GeoNames id
    val area = rec(
      wd(ExternalId.Id("Q999000721")),
      GazRecord.Label("Zzgaz doomed region"),
      kind = GazRecord.Kind(Place.Kind.Region),
      links = List(GazLink(gn(ExternalId.Id("999000723")), LinkMatch.Exact))
    )
    val site =
      rec(
        wd(ExternalId.Id("Q999000722")),
        GazRecord.Label("Zzgaz town by the region"),
        links = List(GazLink(gn(ExternalId.Id("999000723")), LinkMatch.Exact))
      )
    val before = run(Wikidata, V1, List(area, site), kinds = Set.empty)
    assertEquals((before.created, before.joined), (1L, 1L))
    assert(names("wikidata_q999000721").exists(_._1 == "Zzgaz town by the region"))
    // one batch, the joiner FIRST, no prune: the area's place is doomed before anything is placed, so
    // the town makes its own place — which the area, sharing the GeoNames id, then joins (an area
    // known only by a point still joins a place its ids name; it only never makes one)
    val c = connect(ds):
      val s = GazetteerImport.session(Wikidata, V1, apply = true, clock = TestCreators.clock)
      s.batch(List(site, area)) + s.sweep()
    assertEquals((c.placesRemoved, c.pointAreasSkipped, c.created, c.joined), (1L, 0L, 1L, 1L), c.describe)
    assert(!exists("wikidata_q999000721") && exists("wikidata_q999000722"))
    assertEquals(names("wikidata_q999000722").map(_._1), List("Zzgaz doomed region", "Zzgaz town by the region"))
    assert(run(Wikidata, V1, List(site, area)).unchanged, "and that is the steady state")
    // the other order: the area, met first, has nothing to join and is dropped; the town is its own
    clean()
    run(Wikidata, V1, List(area, site), kinds = Set.empty)
    val c2 = connect(ds):
      val s = GazetteerImport.session(Wikidata, V1, apply = true, clock = TestCreators.clock)
      s.batch(List(area, site)) + s.sweep()
    assertEquals((c2.placesRemoved, c2.pointAreasSkipped, c2.created, c2.joined), (1L, 1L, 1L, 0L), c2.describe)
    assertEquals(names("wikidata_q999000722").map(_._1), List("Zzgaz town by the region"))

  test("prune: a real run removes the source's point-only areas before its first batch; a dry run leaves them"):
    val area = rec(
      wd(ExternalId.Id("Q999000731")),
      GazRecord.Label("Zzgaz pruned region"),
      kind = GazRecord.Kind(Place.Kind.Polity)
    )
    run(Wikidata, V1, List(area), kinds = Set.empty)
    assert(exists("wikidata_q999000731"))
    connect(ds):
      assertEquals(GazetteerImport.session(Wikidata, V1, apply = false, clock = TestCreators.clock).prune(10), 0L)
    assert(exists("wikidata_q999000731"))
    connect(ds):
      assertEquals(
        GazetteerImport.session(Geonames, V1, apply = true, clock = TestCreators.clock).prune(10),
        0L,
        "only the source's own"
      )
      assertEquals(
        GazetteerImport.session(LpfWd, V1, apply = true, clock = TestCreators.clock).prune(10),
        0L,
        "never an LPF dataset's"
      )
      assertEquals(
        GazetteerImport
          .session(Wikidata, V1, apply = true, pointAreaKinds = Set.empty, clock = TestCreators.clock)
          .prune(10),
        0L
      )
    assert(exists("wikidata_q999000731"))
    connect(ds)(
      assert(GazetteerImport.session(Wikidata, V1, apply = true, clock = TestCreators.clock).prune(1).value >= 1L)
    )
    assert(!exists("wikidata_q999000731"))

  test("evidence keeps a place: a point-only area an attestation names stays, its record on it"):
    val area = rec(
      wd(ExternalId.Id("Q999000741")),
      GazRecord.Label("Zzgaz attested region"),
      kind = GazRecord.Kind(Place.Kind.Region)
    )
    run(Wikidata, V1, List(area), kinds = Set.empty)
    connect(ds):
      SourceFixtures.upsert(
        ds,
        Source(
          citekey = Citekey("zzgaz_src"),
          title = api.library.Title("Gazetteer fixture"),
          authors = (List("T")).map(api.library.AuthorName.apply),
          doi = None,
          year = (Some(2026)).map(Year.apply),
          venue = None,
          status = Source.Status.Current,
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
      val _ = sql"""INSERT INTO attestations (citekey, target_type, target_id, quote, creator)
                    VALUES ('zzgaz_src', 'place', 'wikidata_q999000741', 'here', jsonb_build_object('kind', 'unknown'))""".update
        .run()
    try
      val c = run(Wikidata, V1, List(area))
      assertEquals((c.placesRemoved, c.pointAreasSkipped), (0L, 0L), c.describe)
      assert(exists("wikidata_q999000741"))
      assert(names("wikidata_q999000741").nonEmpty)
    finally connect(ds)(sql"DELETE FROM sources WHERE citekey = 'zzgaz_src'".update.run())
