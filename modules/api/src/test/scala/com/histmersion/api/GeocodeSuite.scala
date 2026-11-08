package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.Geocode.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey

/** The geocoder's pure half (#981): the name key, the readings of a name, and the ranking — each case mirrors a real
  * failure of 2026-10-06.
  */
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
    assertEquals(stripQualifier(Geocode.Attempt.Name("southern Levant")), Some(("southern", "Levant")))
    assertEquals(stripQualifier(Geocode.Attempt.Name("Northern Albania")), Some(("Northern", "Albania")))
    assertEquals(stripQualifier(Geocode.Attempt.Name("Western and Central Balkans")), Some(("Western and Central", "Balkans")))
    assertEquals(stripQualifier(Geocode.Attempt.Name("North-western Anatolia")), Some(("North western", "Anatolia")))
    assertEquals(stripQualifier(Geocode.Attempt.Name("Northern")), None)
    assertEquals(stripQualifier(Geocode.Attempt.Name("Northumbria")), None)
    assertEquals(stripQualifier(Geocode.Attempt.Name("Albania")), None)

  test("attempts: the name, its aside, X within Y, the qualifier dropped, Y alone — in that order"):
    assertEquals(
      attempts(Geocode.Attempt.Name("Ur (Tell el-Muqayyar)")).map(a => (a.name, a.within)),
      List(("Ur (Tell el-Muqayyar)", None), ("Tell el-Muqayyar", None))
    )
    assertEquals(
      attempts(Geocode.Attempt.Name("Gornij, Croatia")),
      List(
        Attempt(Geocode.Attempt.Name("Gornij, Croatia")),
        Attempt(Geocode.Attempt.Name("Gornij"), Some(Geocode.Attempt.Within("Croatia"))),
        Attempt(Geocode.Attempt.Name("Croatia"), None, Some(Approximation.OnlyWithin(Geocode.Approximation.OnlyWithin.Missing("Gornij"))))
      )
    )
    assertEquals(
      attempts(Geocode.Attempt.Name("southern Levant")),
      List(Attempt(Geocode.Attempt.Name("southern Levant")), Attempt(Geocode.Attempt.Name("Levant"), None, Some(Approximation.Qualifier(Geocode.Approximation.Qualifier.Dropped("southern")))))
    )
    assertEquals(attempts(Geocode.Attempt.Name("Northern Albania")).last.approx, Some(Approximation.Qualifier(Geocode.Approximation.Qualifier.Dropped("Northern"))))
    assertEquals(attempts(Geocode.Attempt.Name("  ")), Nil)

  // ── ranking ──────────────────────────────────────────────────────────────────────────────────

  private val Min = Placeholder.Min(4)

  private def c(
      id: Place.Id,
      label: Place.Label,
      kind: Candidate.Kind,
      byLabel: Candidate.ByLabel = Candidate.ByLabel(true),
      from: Option[Year] = None,
      to: Option[Year] = None,
      dim: Candidate.Dimension = Candidate.Dimension(2),
      area: Candidate.Area = Candidate.Area(10.0),
      shared: Approximation.Rough.Places = Approximation.Rough.Places(1),
      lon: Longitude = Longitude(0),
      lat: Latitude = Latitude(0)
  ) =
    Candidate(id, label, kind, byLabel, from, to, dim, area, shared, lon, lat)

  // the live gazetteer's "Albania" candidates
  private val iranSite = c(
    Place.Id("pleiades_903043"),
    Place.Label("[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania"),
    Candidate.Kind(Place.Kind.Site),
    byLabel = Candidate.ByLabel(false),
    from = Some(Year(-750)),
    to = Some(Year(1599)),
    dim = Candidate.Dimension(0),
    area = Candidate.Area(0),
    lon = Longitude(45.86),
    lat = Latitude(34.47)
  )
  private val country = c(Place.Id("ne_country_alb"), Place.Label("Albania"), Candidate.Kind(Place.Kind.Region), area = Candidate.Area(3.0), lon = Longitude(19.97), lat = Latitude(41.25))
  private val caucasian = c(
    Place.Id("pleiades_863731"),
    Place.Label("Albania"),
    Candidate.Kind(Place.Kind.Region),
    from = Some(Year(-330)),
    to = Some(Year(2100)),
    dim = Candidate.Dimension(0),
    area = Candidate.Area(0),
    lon = Longitude(48.16),
    lat = Latitude(41.38)
  )
  private val snap1920 =
    c(Place.Id("cliopatria_albania_1920_1935"), Place.Label("Albania"), Candidate.Kind(Place.Kind.Polity), from = Some(Year(1920)), to = Some(Year(1935)), area = Candidate.Area(2.9))
  private val albania = List(iranSite, country, caucasian, snap1920)

  private def best(cs: List[Candidate], hint: KindHint = KindHint.Unknown, at: Option[Period] = None) =
    rank(cs, hint, at, Min).head.id

  test("Albania: the country labelled Albania, not the Iranian site that lists it among five names"):
    assertEquals(best(albania), "ne_country_alb")
    assertEquals(
      best(albania, at = Some(Period(Year(800), Year(900)))),
      "ne_country_alb",
      "a dated point (Caucasian Albania, -330..2100) does not beat the country's shape"
    )
    assertEquals(
      best(albania, at = Some(Period(Year(1925), Year(1925)))),
      "cliopatria_albania_1920_1935",
      "a dated snapshot that covers the finding is the more specific shape"
    )
    assertEquals(
      best(albania, hint = KindHint.Site),
      "ne_country_alb",
      "an alias never beats a label, whatever the hint"
    )
    assertEquals(best(List(iranSite)), "pleiades_903043", "an alias is still a match when it is the only one")

  test("Mesopotamia: Pleiades' placeholder points at 37.5/37.5 rank after the curated region"):
    val ph = (1 to 3).toList.map(i =>
      c(Place.Id(s"pleiades_$i"), Place.Label("Mesopotamia"), Candidate.Kind(Place.Kind.Region), dim = Candidate.Dimension(0), area = Candidate.Area(0), shared = Approximation.Rough.Places(17), lon = Longitude(37.5), lat = Latitude(37.5))
    )
    val curated = c(Place.Id("mesopotamia"), Place.Label("Mesopotamia"), Candidate.Kind(Place.Kind.Region), area = Candidate.Area(50.0), lon = Longitude(43.74), lat = Latitude(33.15))
    assertEquals(best(ph :+ curated), "mesopotamia")
    // alone, a placeholder still resolves — and the choice says it is rough
    val r = choose(Geocode.Resolution.Asked("Mesopotamia"), Attempt(Geocode.Attempt.Name("Mesopotamia")), rank(ph, KindHint.Unknown, None, Min), Min).get
    assertEquals(r.approximate, List(Approximation.Rough(Geocode.Approximation.Rough.Places(17))))
    assert(r.describe.contains("placeholder point shared by 17 places"), r.describe)
    assert(
      !isPlaceholder(c(Place.Id("x"), Place.Label("x"), Candidate.Kind(Place.Kind.Site), dim = Candidate.Dimension(0), shared = Approximation.Rough.Places(3)), Min),
      "fewer than the minimum is a real point"
    )

  test("Thessalia: a point on an atlas grid square's centre ranks below every precise candidate, and says so"):
    // the live pair (2026-10-09): the late Roman province at the 5° cell's centre 37.5 N 22.5 E — in
    // the Peloponnese — and the region at its real point; both Pleiades, both labelled Thessalia
    val province = c(Place.Id("pleiades_1001895"), Place.Label("Thessalia"), Candidate.Kind(Place.Kind.Region), dim = Candidate.Dimension(0), area = Candidate.Area(0), lon = Longitude(22.5), lat = Latitude(37.5))
      .modify(_.gridCell).setTo(Candidate.GridCell(true))
    val region = c(Place.Id("pleiades_1332"), Place.Label("Thessalia"), Candidate.Kind(Place.Kind.Region), dim = Candidate.Dimension(0), area = Candidate.Area(0), lon = Longitude(22.29543), lat = Latitude(39.44387))
    assertEquals(best(List(province, region)), "pleiades_1332")
    assertEquals(best(List(province, region), at = Some(Period(Year(400), Year(500)))), "pleiades_1332")
    val site = c(Place.Id("pleiades_x"), Place.Label("Thessalia"), Candidate.Kind(Place.Kind.Site), byLabel = Candidate.ByLabel(false), dim = Candidate.Dimension(0), area = Candidate.Area(0), lon = Longitude(22.4), lat = Latitude(39.6))
    assertEquals(best(List(province, site)), "pleiades_x", "a precise alias beats a grid-square label")
    // alone, it still resolves — marked approximate
    val r = choose(Geocode.Resolution.Asked("Thessalia"), Attempt(Geocode.Attempt.Name("Thessalia")), rank(List(province), KindHint.Unknown, None, Min), Min).get
    assertEquals(r.approximate, List(Approximation.GridSquare))
    assert(r.describe.contains("atlas grid square"), r.describe)

  test("Kievan Rus': the snapshot whose period covers the finding's, else the nearest in time"):
    val snaps = List((882L, 883L), (990L, 1017L), (1040L, 1235L), (1236L, 1240L)).map((a, b) =>
      c(Place.Id(s"cliopatria_kievan_rus_${a}_$b"), Place.Label("Kievan Rus'"), Candidate.Kind(Place.Kind.Polity), from = Some(Year(a)), to = Some(Year(b)), area = Candidate.Area(1.0))
    )
    assertEquals(best(snaps, at = Some(Period(Year(1000), Year(1000)))), "cliopatria_kievan_rus_990_1017")
    assertEquals(best(snaps, at = Some(Period(Year(1100), Year(1200)))), "cliopatria_kievan_rus_1040_1235")
    assertEquals(
      best(snaps, at = Some(Period(Year(1300), Year(1350)))),
      "cliopatria_kievan_rus_1236_1240",
      "after the last: the last"
    )
    assertEquals(
      best(snaps, at = Some(Period(Year(960), Year(970)))),
      "cliopatria_kievan_rus_990_1017",
      "a gap: the nearer neighbour"
    )
    assertEquals(
      best(snaps, at = Some(Period(Year(900), Year(910)))),
      "cliopatria_kievan_rus_882_883",
      "a gap: the nearer neighbour"
    )
    assertEquals(
      best(snaps, at = Some(Period(Year(800), Year(1300)))),
      "cliopatria_kievan_rus_1040_1235",
      "several overlap: the smaller area, then the id"
    )
    // undated: the longest-lived snapshot, never the 1236-1240 rump (whose shape is the smallest)
    val rump = snaps.map(s => if s.id.value.endsWith("1236_1240") then s.modify(_.area).setTo(Candidate.Area(0.1)) else s)
    assertEquals(best(rump), "cliopatria_kievan_rus_1040_1235")

  test("Ur: without a hint the broad entity, with 'site' the city"):
    val city = c(Place.Id("pleiades_912985"), Place.Label("Ur(i)"), Candidate.Kind(Place.Kind.Site), dim = Candidate.Dimension(0), area = Candidate.Area(0), from = Some(Year(-6200)), to = Some(Year(2100)))
    val polity = c(Place.Id("ur_iii"), Place.Label("Ur"), Candidate.Kind(Place.Kind.Polity), from = Some(Year(-2112)), to = Some(Year(-2004)))
    assertEquals(best(List(city, polity)), "ur_iii")
    assertEquals(best(List(city, polity), hint = KindHint.Site), "pleiades_912985")
    assertEquals(best(List(city, polity), hint = KindHint.Site, at = Some(Period(Year(-2100), Year(-2000)))), "pleiades_912985")

  test("water: a feature before a site of the same name"):
    val sea = c(Place.Id("pleiades_697709"), Place.Label("Mortuum Mare"), Candidate.Kind(Place.Kind.Feature), dim = Candidate.Dimension(0), area = Candidate.Area(0))
    val town = c(Place.Id("x_town"), Place.Label("Mortuum Mare"), Candidate.Kind(Place.Kind.Site), dim = Candidate.Dimension(0), area = Candidate.Area(0))
    assertEquals(best(List(town, sea), hint = KindHint.Water), "pleiades_697709")
    assertEquals(best(List(town, sea), hint = KindHint.Site), "x_town")

  test("an undated finding prefers an undated place over a dated snapshot"):
    val snap =
      c(Place.Id("cliopatria_denmark_1945_1945"), Place.Label("Denmark"), Candidate.Kind(Place.Kind.Polity), from = Some(Year(1945)), to = Some(Year(1945)), area = Candidate.Area(1.0))
    val ne = c(Place.Id("ne_country_dnk"), Place.Label("Denmark"), Candidate.Kind(Place.Kind.Region), area = Candidate.Area(5.0))
    assertEquals(best(List(snap, ne)), "ne_country_dnk")

  test("a qualifier-stripped choice is approximate and says what was dropped"):
    val a = attempts(Geocode.Attempt.Name("Northern Albania")).last
    val r = choose(Geocode.Resolution.Asked("Northern Albania"), a, rank(albania, KindHint.Unknown, None, Min), Min).get
    assertEquals((r.placeId, r.isApproximate), ("ne_country_alb", true))
    assertEquals(r.describe, "'Northern Albania' → Albania [ne_country_alb] (approximate: 'Northern' dropped)")

  test("nothing to rank, nothing chosen"):
    assertEquals(choose(Geocode.Resolution.Asked("Dead Sea"), Attempt(Geocode.Attempt.Name("Dead Sea")), Nil, Min), None)

  test("period overlap and gap use inclusive bounds"):
    val p = c(Place.Id("p"), Place.Label("p"), Candidate.Kind(Place.Kind.Polity), from = Some(Year(-100)), to = Some(Year(100)))
    assert(p.overlaps(Period(Year(100), Year(200))) && p.overlaps(Period(Year(-300), Year(-100))))
    assertEquals(p.gap(Period(Year(150), Year(160))), 50L)
    assertEquals(p.gap(Period(Year(-300), Year(-200))), 100L)
    assert(c(Place.Id("u"), Place.Label("u"), Candidate.Kind(Place.Kind.Region)).overlaps(Period(Year(-5000), Year(-4000))), "undated is always valid")

  test("kind hints parse leniently: a model's synonym is a hint, anything else none"):
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("Site")), KindHint.Site)
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("city")), KindHint.Site)
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("country")), KindHint.Region)
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("kingdom")), KindHint.Polity)
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("lake")), KindHint.Water)
    assertEquals(KindHint.parse(Geocode.KindHint.Wire("galaxy")), KindHint.Unknown)

  // ── the repair ───────────────────────────────────────────────────────────────────────────────

  test("distance and the move line"):
    assertEqualsDouble(distanceKm(Longitude(0), Latitude(0), Longitude(0), Latitude(1)).value, 111.19, 0.01)
    val m = Move(
      Finding.Id("heraclides2025_ancient#0"),
      Citekey("heraclides2025_ancient"),
      Geocode.Move.Asked("Albania"),
      Some(Geocode.Move.Region("[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania")),
      Longitude(45.85589),
      Latitude(34.46511),
      Resolution(Geocode.Resolution.Asked("Albania"), Place.Id("ne_country_alb"), Place.Label("Albania"), Longitude(19.97), Latitude(41.25))
    )
    assert(m.km.value > 2000 && m.km.value < 2500, m.km)
    assert(worthMoving(m, Geocode.Move.MinKm(25)) && !worthMoving(m.modify(_.to).setTo(m.to.modify(_.lon).setTo(Longitude(45.86)).modify(_.lat).setTo(Latitude(34.47))), Geocode.Move.MinKm(25)))
    assertEquals(
      m.describe,
      "heraclides2025_ancient#0: 'Albania' (stored as '[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania') at 34.47 N 45.86 E → Albania [ne_country_alb] at 41.25 N 19.97 E, " +
        String.format(java.util.Locale.ROOT, "%.0f km", m.km.value)
    )

  // ── the fill (#987) ──────────────────────────────────────────────────────────────────────────

  test("a placement line names the finding, the stored name and where it lands"):
    val p = Placement(
      Finding.Id("meshel2025_dead#2"),
      Citekey("meshel2025_dead"),
      Geocode.Placement.Region("Dead Sea"),
      Resolution(Geocode.Resolution.Asked("Dead Sea"), Place.Id("ne_lake_1159126747"), Place.Label("Dead Sea"), Longitude(35.5), Latitude(31.5))
    )
    assertEquals(p.describe, "meshel2025_dead#2: 'Dead Sea' → Dead Sea [ne_lake_1159126747] at 31.50 N 35.50 E")
    val q = Placement(
      Finding.Id("x#0"),
      Citekey("x"),
      Geocode.Placement.Region("southern Levant"),
      Resolution(Geocode.Resolution.Asked("southern Levant"), Place.Id("levant"), Place.Label("Levant"), Longitude(36.0), Latitude(33.0), List(Approximation.Qualifier(Geocode.Approximation.Qualifier.Dropped("southern"))))
    )
    assert(q.describe.endsWith("(approximate: 'southern' dropped)"), q.describe)

  test("a stored finding's period for ranking: both ends, one end, or none"):
    assertEquals(periodOf(Some(Year(-1200)), Some(Year(-1100))), Some(Period(Year(-1200), Year(-1100))))
    assertEquals(periodOf(Some(Year(-1100)), Some(Year(-1200))), Some(Period(Year(-1200), Year(-1100))), "reversed ends are ordered")
    assertEquals(periodOf(Some(Year(-1200)), None), Some(Period(Year(-1200), Year(-1200))))
    assertEquals(periodOf(None, Some(Year(500))), Some(Period(Year(500), Year(500))))
    assertEquals(periodOf(None, None), None)

  test("a dropped compass qualifier names one part of the shape's box; two directions or none keep it whole"):
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("Eastern")), Some((0.5, 0.0, 1.0, 1.0)))
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("southern")), Some((0.0, 0.0, 1.0, 0.5)))
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("Northwestern")), Some((0.0, 0.5, 0.5, 1.0)))
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("south-east")), None, "two words, two directions")
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("central")), Some((0.25, 0.25, 0.75, 0.75)))
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("Western and Central")), None)
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("upper")), None)
    assertEquals(compassBox(Geocode.Approximation.Qualifier.Dropped("coastal")), None)

  // ── the external sources (#1064) ─────────────────────────────────────────────────────────────

  test("tier: a bulk-imported place never outranks a seed place of the same label ('Varna' stays the city)"):
    val city = c(Place.Id("ne_city_varna"), Place.Label("Varna"), Candidate.Kind(Place.Kind.Site), dim = Candidate.Dimension(0), area = Candidate.Area(0))
    val province =
      c(Place.Id("geonames_864563"), Place.Label("Varna"), Candidate.Kind(Place.Kind.Region), dim = Candidate.Dimension(0), area = Candidate.Area(0)).modify(_.tier).setTo(tierOf(Some(Place.Match.ImportSource("geonames"))))
    assertEquals(
      rank(List(province, city), KindHint.Unknown, None, Geocode.Placeholder.Min(4)).map(_.id),
      List("ne_city_varna", "geonames_864563")
    )
    assertEquals(tierOf(None), 0)
    assertEquals(tierOf(Some(Place.Match.ImportSource("lpf:hittite"))), 1)
    assertEquals(tierOf(Some(Place.Match.ImportSource("wikidata"))), 2)

  test("a place matched by a DATED name is dated by it: the historical name wins in its own period"):
    // two places known as "Zzmikoyan" by an alternative name: one from 1935 to 1956, one undated
    val dated = c(
      Place.Id("geonames_174710"),
      Place.Label("Yeghegnadzor"),
      Candidate.Kind(Place.Kind.Site),
      byLabel = Candidate.ByLabel(false),
      from = Some(Year(1935)),
      to = Some(Year(1956)),
      dim = Candidate.Dimension(0),
      area = Candidate.Area(0)
    )
    val undated = c(Place.Id("geonames_100"), Place.Label("Mikoyan farm"), Candidate.Kind(Place.Kind.Site), byLabel = Candidate.ByLabel(false), dim = Candidate.Dimension(0), area = Candidate.Area(0))
    assertEquals(rank(List(undated, dated), KindHint.Unknown, Some(Period(Year(1940), Year(1945))), Geocode.Placeholder.Min(4)).head.id, "geonames_174710")
    assertEquals(rank(List(dated, undated), KindHint.Unknown, Some(Period(Year(1990), Year(2000))), Geocode.Placeholder.Min(4)).head.id, "geonames_100")
