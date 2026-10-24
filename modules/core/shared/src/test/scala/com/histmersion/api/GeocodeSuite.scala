package com.histmersion.api

import com.histmersion.domain.*
import com.histmersion.domain.given

import com.histmersion.api.Geocode.*

/** The geocoder's pure half (#981): the name key, the readings of a name, and the ranking — each
  * case mirrors a real failure of 2026-10-06. */
class GeocodeSuite extends munit.FunSuite:

  // ── key ──────────────────────────────────────────────────────────────────────────────────────

  test("key: diacritics, case, apostrophes, punctuation, an aside and a leading 'the' do not matter"):
    assertEquals(key("Kraków"), "krakow")
    assertEquals(key("KRAKOW"), "krakow")
    assertEquals(key("Kievan Rus'"), key("Kievan Rus"))
    assertEquals(key("Kievan Rus’"), "kievan rus")
    assertEquals(key("Ur(i)"), "ur")
    assertEquals(key("Levant (region)"), "levant")
    assertEquals(key("the Southern Levant"), "southern levant")
    assertEquals(key("Tell el-Muqayyar"), "tell el muqayyar")
    assertEquals(key("  Ḫattuša  "), "hattusa")
    assertEquals(key("Straße"), "strasse")
    assertEquals(key("[Hulwan]/Valashfarr"), "hulwan valashfarr")
    assertEquals(key("Baʿal"), "baal")

  test("key: other scripts pass through, only ASCII is lower-cased (as the SQL twin does in any locale)"):
    assertEquals(key("Ἀθῆναι"), "Ἀθῆναι")
    assertEquals(key("Theodosia"), "theodosia", "'the' is dropped only as a word")
    assertEquals(key("The"), "the")

  // ── attempts ─────────────────────────────────────────────────────────────────────────────────

  test("qualifiers: a directional prefix is dropped as a whole word, and something must remain"):
    assertEquals(stripQualifier("southern Levant"), Some(("southern", "Levant")))
    assertEquals(stripQualifier("Northern Albania"), Some(("Northern", "Albania")))
    assertEquals(stripQualifier("Western and Central Balkans"), Some(("Western and Central", "Balkans")))
    assertEquals(stripQualifier("North-western Anatolia"), Some(("North western", "Anatolia")))
    assertEquals(stripQualifier("Northern"), None)
    assertEquals(stripQualifier("Northumbria"), None)
    assertEquals(stripQualifier("Albania"), None)

  test("attempts: the name, its aside, X within Y, the qualifier dropped, Y alone — in that order"):
    assertEquals(attempts("Ur (Tell el-Muqayyar)").map(a => (a.name, a.within)),
      List(("Ur (Tell el-Muqayyar)", None), ("Tell el-Muqayyar", None)))
    assertEquals(attempts("Gornij, Croatia"), List(
      Attempt("Gornij, Croatia"),
      Attempt("Gornij", Some("Croatia")),
      Attempt("Croatia", None, Some(Approximation.OnlyWithin("Gornij")))))
    assertEquals(attempts("southern Levant"), List(
      Attempt("southern Levant"), Attempt("Levant", None, Some(Approximation.Qualifier("southern")))))
    assertEquals(attempts("Northern Albania").last.approx, Some(Approximation.Qualifier("Northern")))
    assertEquals(attempts("  "), Nil)

  // ── ranking ──────────────────────────────────────────────────────────────────────────────────

  private val Min = 4

  private def c(id: String, label: String, kind: String, byLabel: Boolean = true, from: Option[Long] = None,
      to: Option[Long] = None, dim: Int = 2, area: Double = 10.0, shared: Int = 1, lon: Double = 0, lat: Double = 0) =
    Candidate(id, label, kind, byLabel, from, to, dim, area, shared, lon, lat)

  // the live gazetteer's "Albania" candidates
  private val iranSite   = c("pleiades_903043", "[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania", PlaceKind.Site,
                             byLabel = false, from = Some(-750), to = Some(1599), dim = 0, area = 0, lon = 45.86, lat = 34.47)
  private val country    = c("ne_country_alb", "Albania", PlaceKind.Region, area = 3.0, lon = 19.97, lat = 41.25)
  private val caucasian  = c("pleiades_863731", "Albania", PlaceKind.Region, from = Some(-330), to = Some(2100),
                             dim = 0, area = 0, lon = 48.16, lat = 41.38)
  private val snap1920   = c("cliopatria_albania_1920_1935", "Albania", PlaceKind.Polity, from = Some(1920), to = Some(1935), area = 2.9)
  private val albania    = List(iranSite, country, caucasian, snap1920)

  private def best(cs: List[Candidate], hint: KindHint = KindHint.Unknown, at: Option[Period] = None) =
    rank(cs, hint, at, Min).head.id

  test("Albania: the country labelled Albania, not the Iranian site that lists it among five names"):
    assertEquals(best(albania), "ne_country_alb")
    assertEquals(best(albania, at = Some(Period(800, 900))), "ne_country_alb",
      "a dated point (Caucasian Albania, -330..2100) does not beat the country's shape")
    assertEquals(best(albania, at = Some(Period(1925, 1925))), "cliopatria_albania_1920_1935",
      "a dated snapshot that covers the finding is the more specific shape")
    assertEquals(best(albania, hint = KindHint.Site), "ne_country_alb",
      "an alias never beats a label, whatever the hint")
    assertEquals(best(List(iranSite)), "pleiades_903043", "an alias is still a match when it is the only one")

  test("Mesopotamia: Pleiades' placeholder points at 37.5/37.5 rank after the curated region"):
    val ph = (1 to 3).toList.map(i => c(s"pleiades_$i", "Mesopotamia", PlaceKind.Region, dim = 0, area = 0, shared = 17,
      lon = 37.5, lat = 37.5))
    val curated = c("mesopotamia", "Mesopotamia", PlaceKind.Region, area = 50.0, lon = 43.74, lat = 33.15)
    assertEquals(best(ph :+ curated), "mesopotamia")
    // alone, a placeholder still resolves — and the choice says it is rough
    val r = choose("Mesopotamia", Attempt("Mesopotamia"), rank(ph, KindHint.Unknown, None, Min), Min).get
    assertEquals(r.approximate, List(Approximation.Rough(17)))
    assert(r.describe.contains("placeholder point shared by 17 places"), r.describe)
    assert(!isPlaceholder(c("x", "x", PlaceKind.Site, dim = 0, shared = 3), Min), "fewer than the minimum is a real point")

  test("Thessalia: a point on an atlas grid square's centre ranks below every precise candidate, and says so"):
    // the live pair (2026-10-09): the late Roman province at the 5° cell's centre 37.5 N 22.5 E — in
    // the Peloponnese — and the region at its real point; both Pleiades, both labelled Thessalia
    val province = c("pleiades_1001895", "Thessalia", PlaceKind.Region, dim = 0, area = 0, lon = 22.5, lat = 37.5)
      .copy(gridCell = true)
    val region   = c("pleiades_1332", "Thessalia", PlaceKind.Region, dim = 0, area = 0, lon = 22.29543, lat = 39.44387)
    assertEquals(best(List(province, region)), "pleiades_1332")
    assertEquals(best(List(province, region), at = Some(Period(400, 500))), "pleiades_1332")
    val site = c("pleiades_x", "Thessalia", PlaceKind.Site, byLabel = false, dim = 0, area = 0, lon = 22.4, lat = 39.6)
    assertEquals(best(List(province, site)), "pleiades_x", "a precise alias beats a grid-square label")
    // alone, it still resolves — marked approximate
    val r = choose("Thessalia", Attempt("Thessalia"), rank(List(province), KindHint.Unknown, None, Min), Min).get
    assertEquals(r.approximate, List(Approximation.GridSquare))
    assert(r.describe.contains("atlas grid square"), r.describe)

  test("Kievan Rus': the snapshot whose period covers the finding's, else the nearest in time"):
    val snaps = List((882L, 883L), (990L, 1017L), (1040L, 1235L), (1236L, 1240L)).map((a, b) =>
      c(s"cliopatria_kievan_rus_${a}_$b", "Kievan Rus'", PlaceKind.Polity, from = Some(a), to = Some(b), area = 1.0))
    assertEquals(best(snaps, at = Some(Period(1000, 1000))), "cliopatria_kievan_rus_990_1017")
    assertEquals(best(snaps, at = Some(Period(1100, 1200))), "cliopatria_kievan_rus_1040_1235")
    assertEquals(best(snaps, at = Some(Period(1300, 1350))), "cliopatria_kievan_rus_1236_1240", "after the last: the last")
    assertEquals(best(snaps, at = Some(Period(960, 970))), "cliopatria_kievan_rus_990_1017", "a gap: the nearer neighbour")
    assertEquals(best(snaps, at = Some(Period(900, 910))), "cliopatria_kievan_rus_882_883", "a gap: the nearer neighbour")
    assertEquals(best(snaps, at = Some(Period(800, 1300))), "cliopatria_kievan_rus_1040_1235",
      "several overlap: the smaller area, then the id")
    // undated: the longest-lived snapshot, never the 1236-1240 rump (whose shape is the smallest)
    val rump = snaps.map(s => if s.id.endsWith("1236_1240") then s.copy(area = 0.1) else s)
    assertEquals(best(rump), "cliopatria_kievan_rus_1040_1235")

  test("Ur: without a hint the broad entity, with 'site' the city"):
    val city   = c("pleiades_912985", "Ur(i)", PlaceKind.Site, dim = 0, area = 0, from = Some(-6200), to = Some(2100))
    val polity = c("ur_iii", "Ur", PlaceKind.Polity, from = Some(-2112), to = Some(-2004))
    assertEquals(best(List(city, polity)), "ur_iii")
    assertEquals(best(List(city, polity), hint = KindHint.Site), "pleiades_912985")
    assertEquals(best(List(city, polity), hint = KindHint.Site, at = Some(Period(-2100, -2000))), "pleiades_912985")

  test("water: a feature before a site of the same name"):
    val sea  = c("pleiades_697709", "Mortuum Mare", PlaceKind.Feature, dim = 0, area = 0)
    val town = c("x_town", "Mortuum Mare", PlaceKind.Site, dim = 0, area = 0)
    assertEquals(best(List(town, sea), hint = KindHint.Water), "pleiades_697709")
    assertEquals(best(List(town, sea), hint = KindHint.Site), "x_town")

  test("an undated finding prefers an undated place over a dated snapshot"):
    val snap = c("cliopatria_denmark_1945_1945", "Denmark", PlaceKind.Polity, from = Some(1945), to = Some(1945), area = 1.0)
    val ne   = c("ne_country_dnk", "Denmark", PlaceKind.Region, area = 5.0)
    assertEquals(best(List(snap, ne)), "ne_country_dnk")

  test("a qualifier-stripped choice is approximate and says what was dropped"):
    val a = attempts("Northern Albania").last
    val r = choose("Northern Albania", a, rank(albania, KindHint.Unknown, None, Min), Min).get
    assertEquals((r.placeId, r.isApproximate), ("ne_country_alb", true))
    assertEquals(r.describe, "'Northern Albania' → Albania [ne_country_alb] (approximate: 'Northern' dropped)")

  test("nothing to rank, nothing chosen"):
    assertEquals(choose("Dead Sea", Attempt("Dead Sea"), Nil, Min), None)

  test("period overlap and gap use inclusive bounds"):
    val p = c("p", "p", PlaceKind.Polity, from = Some(-100), to = Some(100))
    assert(p.overlaps(Period(100, 200)) && p.overlaps(Period(-300, -100)))
    assertEquals(p.gap(Period(150, 160)), 50L)
    assertEquals(p.gap(Period(-300, -200)), 100L)
    assert(c("u", "u", PlaceKind.Region).overlaps(Period(-5000, -4000)), "undated is always valid")

  test("kind hints parse leniently: a model's synonym is a hint, anything else none"):
    assertEquals(KindHint.parse("Site"), KindHint.Site)
    assertEquals(KindHint.parse("city"), KindHint.Site)
    assertEquals(KindHint.parse("country"), KindHint.Region)
    assertEquals(KindHint.parse("kingdom"), KindHint.Polity)
    assertEquals(KindHint.parse("lake"), KindHint.Water)
    assertEquals(KindHint.parse("galaxy"), KindHint.Unknown)

  // ── the repair ───────────────────────────────────────────────────────────────────────────────

  test("distance and the move line"):
    assertEqualsDouble(distanceKm(0, 0, 0, 1), 111.19, 0.01)
    val m = Move("heraclides2025_ancient#0", "heraclides2025_ancient", "Albania",
      Some("[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania"), 45.85589, 34.46511,
      Resolution("Albania", "ne_country_alb", "Albania", 19.97, 41.25))
    assert(m.km > 2000 && m.km < 2500, m.km)
    assert(worthMoving(m, 25) && !worthMoving(m.copy(to = m.to.copy(lon = 45.86, lat = 34.47)), 25))
    assertEquals(m.describe,
      "heraclides2025_ancient#0: 'Albania' (stored as '[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania') at 34.47 N 45.86 E → Albania [ne_country_alb] at 41.25 N 19.97 E, " +
        String.format(java.util.Locale.ROOT, "%.0f km", m.km))

  // ── the fill (#987) ──────────────────────────────────────────────────────────────────────────

  test("a placement line names the finding, the stored name and where it lands"):
    val p = Placement("meshel2025_dead#2", "meshel2025_dead", "Dead Sea",
      Resolution("Dead Sea", "ne_lake_1159126747", "Dead Sea", 35.5, 31.5))
    assertEquals(p.describe, "meshel2025_dead#2: 'Dead Sea' → Dead Sea [ne_lake_1159126747] at 31.50 N 35.50 E")
    val q = Placement("x#0", "x", "southern Levant",
      Resolution("southern Levant", "levant", "Levant", 36.0, 33.0, List(Approximation.Qualifier("southern"))))
    assert(q.describe.endsWith("(approximate: 'southern' dropped)"), q.describe)

  test("a stored finding's period for ranking: both ends, one end, or none"):
    assertEquals(periodOf(Some(-1200), Some(-1100)), Some(Period(-1200, -1100)))
    assertEquals(periodOf(Some(-1100), Some(-1200)), Some(Period(-1200, -1100)), "reversed ends are ordered")
    assertEquals(periodOf(Some(-1200), None), Some(Period(-1200, -1200)))
    assertEquals(periodOf(None, Some(500)), Some(Period(500, 500)))
    assertEquals(periodOf(None, None), None)

  test("a dropped compass qualifier names one part of the shape's box; two directions or none keep it whole"):
    assertEquals(compassBox("Eastern"), Some((0.5, 0.0, 1.0, 1.0)))
    assertEquals(compassBox("southern"), Some((0.0, 0.0, 1.0, 0.5)))
    assertEquals(compassBox("Northwestern"), Some((0.0, 0.5, 0.5, 1.0)))
    assertEquals(compassBox("south-east"), None, "two words, two directions")
    assertEquals(compassBox("central"), Some((0.25, 0.25, 0.75, 0.75)))
    assertEquals(compassBox("Western and Central"), None)
    assertEquals(compassBox("upper"), None)
    assertEquals(compassBox("coastal"), None)

  // ── the external sources (#1064) ─────────────────────────────────────────────────────────────

  test("tier: a bulk-imported place never outranks a seed place of the same label ('Varna' stays the city)"):
    val city     = c("ne_city_varna", "Varna", PlaceKind.Site, dim = 0, area = 0)
    val province = c("geonames_864563", "Varna", PlaceKind.Region, dim = 0, area = 0).copy(tier = tierOf(Some("geonames")))
    assertEquals(rank(List(province, city), KindHint.Unknown, None, 4).map(_.id), List("ne_city_varna", "geonames_864563"))
    assertEquals(tierOf(None), 0)
    assertEquals(tierOf(Some("lpf:hittite")), 1)
    assertEquals(tierOf(Some("wikidata")), 2)

  test("a place matched by a DATED name is dated by it: the historical name wins in its own period"):
    // two places known as "Zzmikoyan" by an alternative name: one from 1935 to 1956, one undated
    val dated   = c("geonames_174710", "Yeghegnadzor", PlaceKind.Site, byLabel = false, from = Some(1935), to = Some(1956), dim = 0, area = 0)
    val undated = c("geonames_100", "Mikoyan farm", PlaceKind.Site, byLabel = false, dim = 0, area = 0)
    assertEquals(rank(List(undated, dated), KindHint.Unknown, Some(Period(1940, 1945)), 4).head.id, "geonames_174710")
    assertEquals(rank(List(dated, undated), KindHint.Unknown, Some(Period(1990, 2000)), 4).head.id, "geonames_100")
