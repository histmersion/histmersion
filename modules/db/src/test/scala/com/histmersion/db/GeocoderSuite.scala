package com.histmersion.db

import com.histmersion.domain.library.sources.SourceCheckFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.{GeocodeSettings, PlaceFixtures}

import com.histmersion.api.{GeoPoint, LatinFold}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Geocode.{Approximation, KindHint}

import com.augustnagro.magnum.*
import com.histmersion.values.{Confidence, PlaceKind, SourceStatus}
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Finding, Source}

/** The geocoder against a real gazetteer (HISTMERSION_TEST_DB_URL; skipped otherwise), seeded to mirror the failures of
  * 2026-10-06 (#981). Every fixture name starts with "Zz" and every id with `zzgeo_`, so whatever else the shared test
  * database holds cannot match.
  */
class GeocoderSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private lazy val settings =
    GeocodeSettings(cfg.places.geocodePlaceholderMin, cfg.places.geocodeRepairKm, cfg.places.pointToleranceKm)
  private def min = cfg.places.geocodePlaceholderMin

  private val Ck = "zzgeo_src"

  private def box(x0: Double, y0: Double, x1: Double, y1: Double) =
    s"POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))"

  /** id, label, kind, WKT, period (`[a,b)` text or null), extra names. */
  private val places: List[(String, String, String, String, Option[String], List[String])] = List(
    // Albania: an Iranian SITE that lists "Albania" among its names, the country, Caucasian Albania
    // (a dated Pleiades point), and a dated snapshot of the modern state
    (
      "zzgeo_site_alb",
      "[Zzhulwan]/Zzchala/Zzalbania",
      PlaceKind.Site,
      "POINT(45.85589 34.46511)",
      Some("[-750,1600)"),
      List("Zzhulwan", "Zzchala", "Zzalbania")
    ),
    ("zzgeo_ne_alb", "Zzalbania", PlaceKind.Region, box(19.3, 39.7, 21.0, 42.6), None, Nil),
    ("zzgeo_cauc_alb", "Zzalbania", PlaceKind.Region, "POINT(48.16 41.38)", Some("[-330,2101)"), Nil),
    ("zzgeo_alb_1920", "Zzalbania", PlaceKind.Polity, box(19.4, 39.8, 20.9, 42.5), Some("[1920,1936)"), Nil),
    // Mesopotamia: Pleiades placeholders sharing ONE point with other provinces, and the curated region
    ("zzgeo_ph_1", "Zzmesopotamia", PlaceKind.Region, "POINT(37.5 37.5)", None, Nil),
    ("zzgeo_ph_2", "Zzmesopotamia", PlaceKind.Region, "POINT(37.5 37.5)", None, Nil),
    ("zzgeo_ph_3", "Zzosroene", PlaceKind.Region, "POINT(37.5 37.5)", None, Nil),
    ("zzgeo_ph_4", "Zzsyria Coele", PlaceKind.Region, "POINT(37.5 37.5)", None, Nil),
    ("zzgeo_mesopotamia", "Zzmesopotamia", PlaceKind.Region, box(40.0, 30.0, 47.0, 37.0), None, Nil),
    // Kievan Rus': two dated snapshots, far apart
    ("zzgeo_kr_990_1017", "Zzkievan Rus'", PlaceKind.Polity, box(28.0, 50.0, 36.0, 57.0), Some("[990,1018)"), Nil),
    ("zzgeo_kr_1236_1240", "Zzkievan Rus'", PlaceKind.Polity, box(36.5, 44.9, 37.5, 45.5), Some("[1236,1241)"), Nil),
    // Ur: the city (Pleiades writes "Ur(i)") and a polity of the same name
    ("zzgeo_ur_city", "Zzur(i)", PlaceKind.Site, "POINT(46.103 30.963)", Some("[-6200,2101)"), Nil),
    ("zzgeo_ur_polity", "Zzur", PlaceKind.Polity, box(45.0, 30.0, 47.5, 32.0), Some("[-2112,-2003)"), Nil),
    // a diacritic
    ("zzgeo_krakow", "Zzkraków", PlaceKind.Site, "POINT(19.94 50.06)", None, Nil),
    // "X, Country": two places named Zzkormadin, one inside Zzserbia
    ("zzgeo_serbia", "Zzserbia", PlaceKind.Region, box(19.0, 42.0, 23.0, 46.0), None, Nil),
    ("zzgeo_kormadin", "Zzkormadin", PlaceKind.Site, "POINT(20.2 44.8)", None, Nil),
    ("zzgeo_kormadin_far", "Zzkormadin", PlaceKind.Site, "POINT(10.0 50.0)", None, Nil),
    // a qualifier
    ("zzgeo_levant", "Zzlevant", PlaceKind.Region, box(34.0, 31.0, 37.0, 37.0), None, List("Zzsyria-Palestine"))
  )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey = $Ck".update.run()
    val _ = sql"DELETE FROM jobs WHERE id = 'zzgeo_job'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zzgeo%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      connect(ds):
        places.foreach { (id, label, kind, wkt, period, names) =>
          val _ = sql"""INSERT INTO places (id, label, kind, geom, period, dataset)
                        VALUES ($id, $label, $kind, ST_GeomFromText($wkt, 4326), coalesce($period::int8range, '(,)'::int8range), 'seed')""".update
            .run()
          (label :: names).distinct.foreach { n =>
            val _ = sql"INSERT INTO place_names (place_id, name) VALUES ($id, $n) ON CONFLICT DO NOTHING".update.run()
          }
        }

  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def resolve(name: String, hint: KindHint = KindHint.Unknown, at: Option[Period] = None) =
    connect(ds)(PlaceFixtures.resolve(name, hint, at, min))

  private def chosen(name: String, hint: KindHint = KindHint.Unknown, at: Option[Period] = None) =
    resolve(name, hint, at).map(_.placeId)

  // ── the key: SQL and Scala must agree ────────────────────────────────────────────────────────

  test("histmersion_place_key (V067) equals Geocode.key for every folded letter, mark, separator and apostrophe"):
    val chars = (LatinFold.tableChars ++ ('̀' to 'ͯ') ++ Geocode.Separators ++ Geocode.Apostrophes ++
      ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ "Ἀωжא€".toSet).toList.sorted
    val samples = chars.map(c => s"x${c}y") ++ List(
      "Kraków",
      "Kievan Rus'",
      "Ur(i)",
      "Levant (region)",
      "the Southern Levant",
      "The",
      "Theodosia",
      "Tell el-Muqayyar",
      "  Ḫattuša  ",
      "Straße",
      "[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania",
      "Baʿal",
      "a\t\nb",
      "Ἀθῆναι",
      "(only an aside)",
      "x (a) y (b) z",
      "unclosed (aside",
      "Æthelred",
      "ǅ"
    )
    val got = connect(ds):
      sql"SELECT histmersion_place_key(s) FROM unnest($samples::text[]) WITH ORDINALITY AS t(s, n) ORDER BY n"
        .query[String]
        .run()
        .toList
    val diffs = samples
      .zip(got)
      .filter((s, g) => Geocode.key(s) != g)
      .map((s, g) => s"'$s': scala '${Geocode.key(s)}' sql '$g'")
    assertEquals(diffs, Nil)

  // ── the real failures ────────────────────────────────────────────────────────────────────────

  test("Albania: the country, not the site that has it as an alias; a covering snapshot when dated"):
    assertEquals(chosen("Zzalbania"), Some("zzgeo_ne_alb"))
    assertEquals(chosen("Zzalbania", at = Some(Period(800, 900))), Some("zzgeo_ne_alb"))
    assertEquals(chosen("Zzalbania", at = Some(Period(1925, 1925))), Some("zzgeo_alb_1920"))
    assertEquals(chosen("Zzhulwan"), Some("zzgeo_site_alb"), "the site still answers to its own names")

  test("Mesopotamia: the curated region, not the placeholder point Pleiades shares with other provinces"):
    val r = resolve("Zzmesopotamia").get
    assertEquals((r.placeId, r.isApproximate), ("zzgeo_mesopotamia", false))
    // alone on a placeholder, a name still resolves, and says it is rough
    val o = resolve("Zzosroene").get
    assertEquals(o.approximate, List(Approximation.Rough(4)))

  test("Kievan Rus': the snapshot of the finding's own time"):
    assertEquals(chosen("Zzkievan Rus'", at = Some(Period(1000, 1000))), Some("zzgeo_kr_990_1017"))
    assertEquals(chosen("Zzkievan Rus", at = Some(Period(1237, 1239))), Some("zzgeo_kr_1236_1240"))
    assertEquals(chosen("Zzkievan Rus’", at = Some(Period(1100, 1100))), Some("zzgeo_kr_990_1017"), "nearest in time")

  test("Ur: the Pleiades city under 'Ur(i)' when the model says site; the polity otherwise"):
    assertEquals(chosen("Zzur", KindHint.Site), Some("zzgeo_ur_city"))
    assertEquals(chosen("Zzur"), Some("zzgeo_ur_polity"))
    assertEquals(chosen("Zzur (Tell el-Muqayyar)", KindHint.Site), Some("zzgeo_ur_city"))

  test("diacritics, case and 'the' are folded on both sides"):
    assertEquals(chosen("Zzkrakow"), Some("zzgeo_krakow"))
    assertEquals(chosen("ZZKRAKÓW"), Some("zzgeo_krakow"))
    assertEquals(chosen("the Zzlevant"), Some("zzgeo_levant"))
    assertEquals(chosen("Zzsyria Palestine"), Some("zzgeo_levant"), "an alias, hyphen or not")

  test("'X, Country': the X inside that country; an unknown X falls back to the country, approximately"):
    val k = resolve("Zzkormadin, Zzserbia").get
    assertEquals((k.placeId, k.isApproximate, k.lon, k.lat), ("zzgeo_kormadin", false, 20.2, 44.8))
    val g = resolve("Zzgornij, Zzserbia").get
    assertEquals((g.placeId, g.approximate), ("zzgeo_serbia", List(Approximation.OnlyWithin("Zzgornij"))))

  test("a directional qualifier resolves to the base region, flagged approximate"):
    val s = resolve("southern Zzlevant").get
    assertEquals((s.placeId, s.approximate), ("zzgeo_levant", List(Approximation.Qualifier("southern"))))
    assertEquals(resolve("Southern Zzlevant").map(_.placeId), Some("zzgeo_levant"))
    val n = resolve("Northern Zzalbania").get
    assertEquals((n.placeId, n.isApproximate), ("zzgeo_ne_alb", true))
    assertEquals(resolve("Western and Central Zzalbania").map(_.placeId), Some("zzgeo_ne_alb"))
    // #987: the point moves into the part the qualifier names (Zzlevant spans 31–37 N, 34–37 E)
    assert(s.lat < 34.0, s)
    val whole = resolve("Zzlevant").get
    assert(whole.lat > 33.0, whole)
    val e = resolve("eastern Zzlevant").get
    assert(e.lon > 35.5, e)
    assertEquals(
      resolve("Western and Central Zzalbania").map(r => (r.lon, r.lat)),
      resolve("Zzalbania").map(r => (r.lon, r.lat)),
      "two directions keep the whole shape's point"
    )

  test("unknown stays unknown: nothing is guessed"):
    assertEquals(resolve("Zzdead Sea"), None)
    assertEquals(resolve("Zzsea of Zzgalilee"), None)
    assertEquals(resolve("   "), None)
    assertEquals(resolve("Western Zznowhere"), None)

  // ── the repair ───────────────────────────────────────────────────────────────────────────────

  private def finding(n: Int, region: String, lon: Double, lat: Double, claim: String, period: Option[Period] = None) =
    Finding(s"$Ck#$n", Ck, claim, Some(region), Some(GeoPoint(lon, lat)), period, None, Nil, Some(Confidence.High))

  private def point(id: String): (Double, Double, Option[String]) = connect(ds):
    sql"SELECT ST_X(geom), ST_Y(geom), region_name FROM findings WHERE id = $id"
      .query[(Double, Double, Option[String])]
      .run()
      .head

  test("finding-geocode: only old-geocoder points move; hand-placed ones and the unchanged never do"):
    val siteLabel = "[Zzhulwan]/Zzchala/Zzalbania"
    val src = Source(
      citekey = Ck,
      title = "Fixture",
      authors = List("A"),
      doi = None,
      year = Some(2025),
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
      findings = List(
        // what the OLD pipeline wrote: the alias site's point under ITS label, the asked name in the run
        finding(0, siteLabel, 45.85589, 34.46511, "Albanians descend from early medieval Albania."),
        // a person placed this one by hand, in Albania: never touched
        finding(1, "Zzalbania", 19.8, 41.3, "A hand-placed claim."),
        // the old pick by region_name (no run): the placeholder point
        finding(2, "Zzmesopotamia", 37.5, 37.5, "Sumerian cities in Mesopotamia.", Some(Period(-2500, -2000))),
        // the old pick, dated 1000 CE: the snapshot with the smaller area, 1236-1240
        finding(3, "Zzkievan Rus'", 0, 0, "Rus in 1000.", Some(Period(1000, 1000))),
        // the old pick and the new one are the same place: nothing to do
        finding(4, "Zzlevant", 0, 0, "Levant claim.")
      )
    )
    SourceFixtures.upsert(ds, src, cfg.limits.chunkChars)
    connect(ds):
      // the stored point of #3/#4 is EXACTLY the old geocoder's (its point on the surface)
      val _ =
        sql"""UPDATE findings SET geom = (SELECT ST_PointOnSurface(geom) FROM places WHERE id = 'zzgeo_kr_1236_1240')
                    WHERE id = ${Ck + "#3"}""".update.run()
      val _ = sql"""UPDATE findings SET geom = (SELECT ST_PointOnSurface(geom) FROM places WHERE id = 'zzgeo_levant')
                    WHERE id = ${Ck + "#4"}""".update.run()
      // the enrich-source run that asked for "Zzalbania"
      val _ = sql"""INSERT INTO jobs (id, kind, status, pipeline, target_kind, target_id)
                    VALUES ('zzgeo_job', 'agent', 'done', 'enrich-source', 'source', $Ck)""".update.run()
      val out = """{"findings":[{"claim":"Albanians descend from early medieval Albania.","quote":"q","ratio":1.0,""" +
        """"place":"Zzalbania","start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}]}"""
      val _ = sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, output)
                    VALUES ('zzgeo_job', 4, 1, 'findings-1', 'llm', 'done', $out::jsonb)""".update.run()

    val moves = connect(ds)(PlaceFixtures.moves(settings)).filter(_.citekey == Ck)
    assertEquals(
      moves.map(m => (m.findingId, m.asked, m.to.placeId)).sortBy(_._1),
      List(
        (s"$Ck#0", "Zzalbania", "zzgeo_ne_alb"),
        (s"$Ck#2", "Zzmesopotamia", "zzgeo_mesopotamia"),
        (s"$Ck#3", "Zzkievan Rus'", "zzgeo_kr_990_1017")
      )
    )
    assert(moves.forall(_.km > settings.repairKm), moves.map(_.km))

    // the lint rule lists the same moves, under the source
    val rep = SourceCheckFixtures.run(ds, List(Lint.rule(Lint.FindingGeocode).get), 500, 3, (_, _, _) => Nil, settings)
    val items = rep.rules.head.items.filter(_.citekey == Ck)
    assertEquals(items.size, 3, items)
    assert(items.exists(_.detail.contains(s"stored as '$siteLabel'")), items)

    val before1 = point(s"$Ck#1")
    assertEquals(transact(ds)(PlaceFixtures.applyMoves(moves, settings.toleranceKm)), 3)
    val (lon0, lat0, region0) = point(s"$Ck#0")
    assert(lon0 > 19.3 && lon0 < 21.0 && lat0 > 39.7 && lat0 < 42.6, (lon0, lat0))
    assertEquals(region0, Some("Zzalbania"), "the asked name replaces the alias site's label")
    assertEquals(point(s"$Ck#1"), before1, "a hand-placed point is never moved")
    val (lon2, lat2, _) = point(s"$Ck#2")
    assert(lon2 > 40.0 && lat2 < 37.0, (lon2, lat2))
    val (lon3, lat3, _) = point(s"$Ck#3")
    assert(lat3 > 50.0, (lon3, lat3))
    // finding_places follows the new points
    val inAlbania = connect(ds):
      sql"SELECT count(*) FROM finding_places WHERE finding_id = ${Ck + "#0"} AND place_id = 'zzgeo_ne_alb'"
        .query[Long]
        .run()
        .head
    assertEquals(inAlbania, 1L)

    // idempotent: nothing is left to move, and a stale move is not applied over a newer point
    assertEquals(connect(ds)(PlaceFixtures.moves(settings)).filter(_.citekey == Ck), Nil)
    assertEquals(transact(ds)(PlaceFixtures.applyMoves(moves, settings.toleranceKm)), 0)
