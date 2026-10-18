package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.Geocode.{Approximation, KindHint}

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}

/** #987 against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise), on the REAL bundled seed:
  * the places a finding names in plain English are read out of `db/places` itself (Natural Earth
  * physical + cities, Pleiades with its names dump, the macro-regions, the curated supplement), so
  * these tests fail the moment a regenerated seed loses "Dead Sea" or "Kraków" again.
  *
  * Only the places listed in [[Wanted]] are imported, under their real ids; this suite deletes exactly
  * those (and its `zzgn_` fixtures) before and after. */
class GazetteerNamesSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d
  private lazy val settings =
    Geocoder.Settings(cfg.places.geocodePlaceholderMin, cfg.places.geocodeRepairKm, cfg.places.pointToleranceKm)

  private val DeadSea   = Set("ne_lake_1159126747", "ne_lake_1159126865")
  private val Galilee   = "ne_lake_1159114879"
  private val Krakow    = "ne_city_1159149709"
  private val Balkans   = "ne_physical_1159104241"
  private val Levant    = "levant"
  private val Med       = "ne_marine_1159115321"
  private val Arkona    = "curated_jaromarsburg"
  private val Lavra     = "curated_trinity_lavra"
  private val BeerSheba = "curated_beersheba_valley"
  private val SLevant   = "curated_southern_levant"

  /** The places every name below can mean — the right one and the near-misses it must beat. */
  private val Wanted: Set[String] = DeadSea ++ Set(
    "pleiades_697709",                                    // Mortuum Mare/Asphaltitis Limne — "Dead Sea" (en)
    Galilee, "pleiades_678430",                           // Tiberiadis Mare — "Sea of Galilee" (en)
    Krakow, "pleiades_128432",                            // Kraków-Nowa Huta, a Pleiades findspot
    Balkans, "ne_physical_1159103605", "pleiades_216830", // Balkan Mountains, Haemus
    Levant, "ne_physical_1730072159", "pleiades_930907033", // Levant Coast, Levant (region)
    Med, Arkona, "curated_cape_arkona", Lavra, "ne_city_1159128635", BeerSheba, SLevant)

  private val Ck = "zzgn_src"

  private lazy val root: Path =
    Iterator.iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent).takeWhile(_ != null)
      .find(d => Files.isDirectory(d.resolve("db/places"))).getOrElse(fail("no db/places above the working directory"))

  /** The wanted features of the real seed files, as the first-run import parses them. */
  private lazy val seed: List[PlaceInput] =
    PlaceSeed.files(root.resolve("db/places")).flatMap { (f, kind) =>
      val text = Files.readString(f)
      // the 17 MB Pleiades file is parsed only when it holds a wanted id
      if !Wanted.exists(id => text.contains(s"\"$id\"")) then Nil
      else Lpf.parse(text, kind.getOrElse(PlaceKind.Region)).fold(e => fail(s"$f: $e"), identity)
        .filter(in => Wanted.contains(in.place.id))
    }

  private def clean(): Unit = connect(ds):
    val ids = (Wanted + "local_zzgn_gornji").toList
    val _ = sql"DELETE FROM sources WHERE citekey = $Ck".update.run()
    val _ = sql"DELETE FROM places WHERE id = ANY($ids::text[])".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      assertEquals(seed.map(_.place.id).toSet, Wanted, "every wanted place is in the bundled seed")
      val _ = PlaceRepo.upsertPlaces(ds, seed)

  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def resolve(name: String, at: Option[Period] = None) =
    Geocoder.resolve(ds, name, KindHint.Unknown, at, cfg.places.geocodePlaceholderMin)

  private def resolvesTo(name: String, ids: Set[String]): Geocode.Resolution =
    val r = resolve(name).getOrElse(fail(s"'$name' resolves to nothing"))
    assert(ids.contains(r.placeId), s"'$name' → ${r.describe}, expected one of $ids")
    r

  test("the plain English names the enrich agents write resolve in the seeded gazetteer"):
    resolvesTo("Dead Sea", DeadSea)
    for n <- List("Sea of Galilee", "Lake Kinneret", "Lake Tiberias", "Sea of Tiberias") do resolvesTo(n, Set(Galilee))
    for n <- List("Kraków", "Krakow", "Cracow", "KRAKÓW") do resolvesTo(n, Set(Krakow))
    for n <- List("Balkans", "Balkan Peninsula", "the Balkans") do resolvesTo(n, Set(Balkans))
    resolvesTo("Levant", Set(Levant))
    resolvesTo("Arkona", Set(Arkona))
    resolvesTo("Holy Trinity-Saint Sergius Monastery", Set(Lavra))
    resolvesTo("Beer Sheba Valley", Set(BeerSheba))
    // exact, not a qualifier dropped
    assert(!resolvesTo("Dead Sea", DeadSea).isApproximate)

  test("a qualified name falls back to the English name it qualifies, marked approximate"):
    // the curated Southern Levant is a name of its own: exact, not the Levant with a word dropped
    assert(!resolvesTo("southern Levant", Set(SLevant)).isApproximate)
    val lev = resolvesTo("northern Levant", Set(Levant))
    assertEquals(lev.approximate, List(Approximation.Qualifier("northern")))
    assert(lev.lat > resolvesTo("Levant", Set(Levant)).lat, "the point moves into the northern half")
    val east = resolvesTo("Eastern Mediterranean", Set(Med))
    assertEquals(east.approximate, List(Approximation.Qualifier("Eastern")))
    // the eastern half of the sea, not its point on the surface off Algeria
    assert(east.lon > 17.0, east)
    assertEquals(resolvesTo("Western and Central Balkans", Set(Balkans)).approximate,
      List(Approximation.Qualifier("Western and Central")))

  test("a dated finding still finds the timeless English name (names carry no Pleiades date range)"):
    resolvesTo("Dead Sea", DeadSea)
    assert(resolve("Dead Sea", Some(Period(-1200, -1100))).exists(r => DeadSea.contains(r.placeId) || r.placeId == "pleiades_697709"))
    // the Pleiades record carries the English name itself, undated, so `finding_places` can match it
    val dated = connect(ds):
      sql"SELECT count(*) FROM place_names WHERE place_id = 'pleiades_697709' AND name = 'Dead Sea' AND period @> (-1200)::bigint"
        .query[Long].run().head
    assertEquals(dated, 1L)

  private def names(id: String): List[(String, String, Boolean)] = connect(ds):
    sql"SELECT name, lang, manual FROM place_names WHERE place_id = $id ORDER BY name, lang"
      .query[(String, String, Boolean)].run().toList

  test("a name added by hand survives a re-seed; an import never deletes it, and the seed's own are replaced"):
    val before = names(Galilee)
    val r = PlaceRepo.addName(ds, Galilee, "Kinnereth", "en").fold(e => fail(e.message), identity)
    assertEquals((r.changed, r.wasImported, r.label), (true, false, "Sea of Galilee"))
    // a name the seed already gives becomes manual: kept even when a later seed drops it
    val again = PlaceRepo.addName(ds, Galilee, "Sea of Tiberias", "en").fold(e => fail(e.message), identity)
    assertEquals((again.changed, again.wasImported), (true, true))
    assertEquals(PlaceRepo.addName(ds, Galilee, "Kinnereth", "en").map(_.changed), Right(false), "adding twice changes nothing")

    // re-seed: the same files again, then a NEWER seed that no longer has "Sea of Tiberias"
    val _ = PlaceRepo.upsertPlaces(ds, seed)
    assert(names(Galilee).contains(("Kinnereth", "en", true)), names(Galilee))
    val newer = seed.map(in => if in.place.id == Galilee then in.copy(names = in.names.filterNot(_.name == "Sea of Tiberias")) else in)
    val _ = PlaceRepo.upsertPlaces(ds, newer)
    val after = names(Galilee)
    assert(after.contains(("Kinnereth", "en", true)) && after.contains(("Sea of Tiberias", "en", true)), after)
    assertEquals(after.filterNot(_._3).size, before.size - 1, "the seed's own names are still replaced by the seed")
    assertEquals(PlaceRepo.manualNames(ds, Some(Galilee)).map(_.name), List("Kinnereth", "Sea of Tiberias"))
    resolvesTo("Kinnereth", Set(Galilee))

    // removal: only a name added by hand; an imported one is refused
    assertEquals(PlaceRepo.removeName(ds, Galilee, "Lake Tiberias", "en").left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(PlaceRepo.removeName(ds, Galilee, "Kinnereth", "").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assert(PlaceRepo.removeName(ds, Galilee, "Kinnereth", "en").isRight)
    assert(PlaceRepo.removeName(ds, Galilee, "Sea of Tiberias", "en").isRight)
    val _ = PlaceRepo.upsertPlaces(ds, seed)
    assertEquals(names(Galilee), before, "after removing them, a re-seed restores exactly the seed's names")
    assertEquals(PlaceRepo.addName(ds, "zzgn_no_such_place", "X", "").left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a place added by hand is local, refused when the id is taken, and untouched by a re-seed"):
    val in = PlaceEdits.toInput(PlaceAdd("Zzgn Gornji", PlaceKind.Site, 15.98, 45.81, "fixture: a hand-checked map",
      names = List("Zzgn Gornij"), lang = Some("hr"))).fold(e => fail(e.message), identity)
    val p = PlaceRepo.addPlace(ds, in).fold(e => fail(e.message), identity)
    assertEquals((p.id, p.kind, p.source), ("local_zzgn_gornji", PlaceKind.Site, Some("fixture: a hand-checked map")))
    val dataset = connect(ds)(sql"SELECT dataset FROM places WHERE id = 'local_zzgn_gornji'".query[String].run().head)
    assertEquals(dataset, "local")
    assertEquals(PlaceRepo.addPlace(ds, in).left.map(_.code), Left(Some(Write.CodeInvalid)), "the id is taken")
    val seedId = in.copy(place = in.place.copy(id = Krakow))
    assertEquals(PlaceRepo.addPlace(ds, seedId).left.map(_.code), Left(Some(Write.CodeInvalid)), "a seed id is never overwritten")
    val _ = PlaceRepo.upsertPlaces(ds, seed)
    assertEquals(resolve("Zzgn Gornij").map(_.placeId), Some("local_zzgn_gornji"))

  // ── the fill ───────────────────────────────────────────────────────────────────────────────

  private def finding(n: Int, region: String, at: Option[GeoPoint], period: Option[Period] = None) =
    Finding(s"$Ck#$n", Ck, s"claim $n about $region", Some(region), at, period, None, Nil, Some(Confidence.High))

  private def pointOf(n: Int): Option[(Double, Double)] = connect(ds):
    sql"SELECT ST_X(geom), ST_Y(geom) FROM findings WHERE id = ${s"$Ck#$n"} AND geom IS NOT NULL"
      .query[(Double, Double)].run().headOption

  test("finding-unplaced: a finding with a name and no point gets the geocoder's point; a placed one never moves"):
    val src = Source(
      citekey = Ck, title = "Fixture", authors = List("A"), doi = None, year = Some(2025), venue = None,
      status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil, period = None, summary = None,
      citedByCount = None, influentialCitations = None, provenance = None, calibreId = None,
      findings = List(
        finding(0, "Dead Sea", None, Some(Period(-1200, -1100))),
        finding(1, "Balkans", Some(GeoPoint(-40.0, 10.0))),  // placed by hand (in the Atlantic): never touched
        finding(2, "Zzgn nowhere at all", None),             // unknown: stays unplaced, never guessed
        finding(3, "northern Levant", None),
        finding(4, "Kraków", None)))
    SourceRepo.upsert(ds, src, cfg.limits.chunkChars)

    val ps = Geocoder.placements(ds, settings).filter(_.citekey == Ck)
    assertEquals(ps.map(p => (p.findingId, p.region)).sorted,
      List((s"$Ck#0", "Dead Sea"), (s"$Ck#3", "northern Levant"), (s"$Ck#4", "Kraków")))
    assert(ps.find(_.region == "northern Levant").exists(_.to.isApproximate))

    val rep = LintRepo.run(ds, List(Lint.rule(Lint.FindingUnplaced).get), 500, 3, (_, _, _) => Nil, settings)
    val items = rep.rules.head.items.filter(_.citekey == Ck)
    assertEquals(items.size, 3, items)
    assert(items.exists(_.detail.startsWith(s"$Ck#4: 'Kraków' → Kraków [$Krakow]")), items)

    assertEquals(Geocoder.place(ds, ps, settings.toleranceKm), 3)
    val (lon0, lat0) = pointOf(0).getOrElse(fail("#0 has no point"))
    assert(lon0 > 35.2 && lon0 < 35.7 && lat0 > 30.9 && lat0 < 31.9, (lon0, lat0))
    assertEquals(pointOf(1), Some((-40.0, 10.0)), "a finding that had a point is never touched")
    assertEquals(pointOf(2), None, "an unknown name is never guessed")
    val (lon4, lat4) = pointOf(4).getOrElse(fail("#4 has no point"))
    assertEqualsDouble(lon4, 19.958, 0.01); assertEqualsDouble(lat4, 50.062, 0.01)
    // the names are kept as written, and finding_places follows the new points
    val regions = connect(ds)(sql"SELECT region_name FROM findings WHERE citekey = $Ck ORDER BY id".query[String].run().toList)
    assertEquals(regions, src.findings.flatMap(_.region))
    val inKrakow = connect(ds):
      sql"SELECT count(*) FROM finding_places WHERE finding_id = ${s"$Ck#4"} AND place_id = $Krakow".query[Long].run().head
    assertEquals(inKrakow, 1L)

    // idempotent: nothing left to place, and a stale placement is not applied over a newer point
    assertEquals(Geocoder.placements(ds, settings).filter(_.citekey == Ck), Nil)
    assertEquals(Geocoder.place(ds, ps, settings.toleranceKm), 0)
