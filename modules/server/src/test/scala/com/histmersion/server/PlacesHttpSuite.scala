package com.histmersion.server

import ox.supervised
import com.histmersion.core.*

/** The gazetteer over real HTTP (plan §1.6a): import a tiny Linked-Places document, then read it
  * back through every route. Gated on HISTMERSION_TEST_DB_URL like the other server suites.
  *
  * Deliberately does NOT call `POST /api/places/rebuild`: that re-materialises `finding_places` for
  * the WHOLE corpus, and the db-module suites (which may run in another JVM against the same test
  * database) assert on it. `PlaceRepoSuite` covers the rebuild. */
class PlacesHttpSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val client = java.net.http.HttpClient.newHttpClient()

  private def send(req: java.net.http.HttpRequest) =
    val r = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def get(url: String) =
    send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build())

  private def post(url: String, body: String) =
    send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build())

  /** Two nested regions and a site, with a relation that is only valid in the Late Bronze Age. */
  private val doc = Lpf.render(List(
    PlaceInput(Place("zzh_world", "Zzh World", PlaceKind.Region), List(PlaceName("Zzh World"))),
    PlaceInput(
      Place("zzh_land", "Zzh Land", PlaceKind.Region,
        Some("""{"type":"Polygon","coordinates":[[[100.0,10.0],[110.0,10.0],[110.0,20.0],[100.0,20.0],[100.0,10.0]]]}""")),
      List(PlaceName("Zzh Land"), PlaceName("Zzhland", "xx")),
      List(PlaceRelation("zzh_land", "zzh_world"))),
    PlaceInput(
      Place("zzh_polity", "Zzh Polity", PlaceKind.Polity,
        Some("""{"type":"Point","coordinates":[105.0,15.0]}"""), Some(Period(-1400, -1200))),
      List(PlaceName("Zzh Polity")),
      List(PlaceRelation("zzh_polity", "zzh_land", PlaceRelation.PartOf, Some(Period(-1400, -1200)))))))

  test("import → search → detail → children → tree, all as of a year"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"

      val (is, ib) = post(s"$url/api/places/import", doc)
      assertEquals(is, 200, ib)
      assert(ib.contains("\"places\":3"), ib)
      assert(ib.contains("\"relations\":2"), ib)

      // search finds a place by an ALTERNATE name and reports the id a `place=` filter wants
      val (ss, sb) = get(s"$url/api/places?q=Zzhland")
      assertEquals(ss, 200, sb)
      assert(sb.contains("\"id\":\"zzh_land\""), sb)
      assert(sb.contains("\"matchedName\":\"Zzhland\""), sb)

      // detail: geometry comes back as GeoJSON TEXT, parents/children as of the asked-for year
      val (ds, db) = get(s"$url/api/places/zzh_land?asOf=-1300")
      assertEquals(ds, 200, db)
      assert(db.contains("\\\"Polygon\\\""), db)
      assert(db.contains("\"zzh_world\""), db)
      assert(db.contains("\"zzh_polity\""), db)

      // …but the polity edge is out of validity in the Iron Age
      val (_, iron) = get(s"$url/api/places/zzh_land?asOf=-700")
      assert(!iron.contains("zzh_polity"), iron)

      val (cs, cb) = get(s"$url/api/places/zzh_land/children?asOf=-1300")
      assertEquals(cs, 200, cb)
      assert(cb.contains("zzh_polity"), cb)

      val (ts, tb) = get(s"$url/api/places/zzh_world/tree?asOf=-1300")
      assertEquals(ts, 200, tb)
      assert(tb.contains("\"depth\":2"), tb) // world > land > polity

      assertEquals(get(s"$url/api/places/zzh_nope")._1, 404)
      assertEquals(post(s"$url/api/places/import", "{ not json")._1, 400)
      assertEquals(post(s"$url/api/places/import?kind=nonsense", doc)._1, 400)

      b.stop()
    }

  test("a place id in the filter reaches sources through finding_places; an unknown one is a name"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      // no findings were ever materialised into zzh_land, so filtering by it must return nothing —
      // and must NOT fall back to matching the id as a region-name substring
      val (s1, b1) = get(s"$url/api/sources?place=zzh_land&limit=1")
      assertEquals(s1, 200, b1)
      assert(b1.contains("\"total\":0"), b1)
      // an unknown value is still the wave-1 name match, so the endpoint never 500s on free text
      assertEquals(get(s"$url/api/sources?place=Definitely%20Not%20A%20Place&limit=1")._1, 200)
      b.stop()
    }

  private def put(url: String, body: String) =
    send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build())

  /** Its own places and source (`zzc_*`), far from `zzh_*`: the test above asserts `zzh_land` selects
    * nothing, and a shared test database keeps what earlier runs wrote. */
  private val countsDoc = Lpf.render(List(
    PlaceInput(Place("zzc_world", "Zzc World", PlaceKind.Region), List(PlaceName("Zzc World"))),
    PlaceInput(
      Place("zzc_land", "Zzc Land", PlaceKind.Region,
        Some("""{"type":"Polygon","coordinates":[[[120.0,10.0],[130.0,10.0],[130.0,20.0],[120.0,20.0],[120.0,10.0]]]}""")),
      List(PlaceName("Zzc Land")),
      List(PlaceRelation("zzc_land", "zzc_world")))))

  test("#716: counts=true says what place=<id> selects — the place and its descendants"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(post(s"$url/api/places/import", countsDoc)._1, 200)
      // a source write materialises its findings into the places that cover them (odd years: the
      // aggregate suites assert on which periods exist anywhere in the shared test database)
      val (ps, pb) = put(s"$url/api/sources/zzc_counted",
        """{"title":"Counted","authors":["A"],"year":2020,"status":"current",
          |"findings":[{"claim":"here","lon":125.0,"lat":15.0,"periodStart":-1337,"periodEnd":-1221},
          |{"claim":"also here","lon":126.0,"lat":16.0,"periodStart":-1337,"periodEnd":-1221}]}""".stripMargin)
      assertEquals(ps, 200, pb)

      val (ls, lb) = get(s"$url/api/places/zzc_land?counts=true")
      assertEquals(ls, 200, lb)
      assert(lb.contains("\"counts\":{\"sources\":1,\"findings\":2,\"articles\":0,\"events\":0}"), lb)
      // the parent has no shape and no findings of its own: it selects its child's
      val (ws, wb) = get(s"$url/api/places/zzc_world?counts=true")
      assertEquals(ws, 200, wb)
      assert(wb.contains("\"counts\":{\"sources\":1,\"findings\":2"), wb)
      // without the flag, no counting
      val (_, plain) = get(s"$url/api/places/zzc_land")
      assert(!plain.contains("\"sources\":"), plain)
      b.stop()
    }

  test("#718: /api/about reports the build, health, job queue and store counts"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(post(s"$url/api/places/import", countsDoc)._1, 200)
      val (as, ab) = get(s"$url/api/about")
      assertEquals(as, 200, ab)
      val about = io.circe.parser.decode[About](ab).fold(e => fail(s"$e: $ab"), identity)
      assertEquals(about.build.version, Version.version)
      assertEquals(about.health.db, "ok")
      assert(about.health.schemaVersion.isDefined, ab)
      val counts = about.counts.getOrElse(fail(s"no counts: $ab"))
      assert(counts.places >= 2, ab)        // at least the two imported above
      assert(counts.sources >= 0 && counts.periods >= 0, ab)
      assert(about.jobs.queued >= 0, ab)
      b.stop()
    }
