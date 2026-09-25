package histdb.server

import ox.supervised
import histdb.core.*

/** The gazetteer over real HTTP (plan §1.6a): import a tiny Linked-Places document, then read it
  * back through every route. Gated on HISTDB_TEST_DB_URL like the other server suites.
  *
  * Deliberately does NOT call `POST /api/places/rebuild`: that re-materialises `finding_places` for
  * the WHOLE corpus, and the db-module suites (which may run in another JVM against the same test
  * database) assert on it. `PlaceRepoSuite` covers the rebuild. */
class PlacesHttpSuite extends munit.FunSuite:
  private val testDb = histdb.db.TestDb.url
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
