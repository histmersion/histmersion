package histdb.server

import ox.supervised
import histdb.core.*

/** `/api/aggregate` with the §1.6 axes over real HTTP: the nested tree, its parameters and its
  * errors. Gated on HISTDB_TEST_DB_URL like every other server suite.
  *
  * Writes its own source and its own two-level gazetteer through the API (so it does not depend on
  * what else is in the test database) but deliberately does NOT call `POST /api/places/rebuild` —
  * that re-materialises `finding_places` for the whole corpus and the db-module suites assert on it.
  * The source reaches the tree through the `region_name` fallback that `put_source` now genuinely
  * maintains (it re-materialises THIS citekey on every write); the containment path is covered by
  * `TreeRepoSuite`. */
class TreeHttpSuite extends munit.FunSuite:
  private val testDb = sys.env.get("HISTDB_TEST_DB_URL")
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

  private def put(url: String, body: String) =
    send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build())

  /** Two nested regions; the inner one carries the name the fixture's finding is labelled with. */
  private val places = Lpf.render(List(
    PlaceInput(Place("zzg_world", "Zzg World", PlaceKind.Region), List(PlaceName("Zzg World"))),
    PlaceInput(Place("zzg_land", "Zzg Land", PlaceKind.Region), List(PlaceName("Zzg Land")),
      List(PlaceRelation("zzg_land", "zzg_world")))))

  private val source =
    """{"title":"Tree HTTP fixture","authors":["T"],"year":2021,"status":"current",
      | "tags":["zzg-tree"],"periodStart":-1400,"periodEnd":-1350,
      | "findings":[
      |   {"claim":"Located claim","region":"Zzg Land","periodStart":-1400,"periodEnd":-1350},
      |   {"claim":"Unlocated claim","periodStart":-1400,"periodEnd":-1350}]}""".stripMargin

  test("by=time,region returns the nested tree; by=time keeps the flat strip"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      val filter = "tag=zzg-tree&from=-1600&to=-1200"

      assertEquals(post(s"$url/api/places/import", places)._1, 200)
      val (ps, pb) = put(s"$url/api/sources/zzg_tree_src", source)
      assertEquals(ps, 200, pb)

      // ── by=time (wave 1) is untouched: the flat bars, gaps included ──
      val (ts, tb) = get(s"$url/api/aggregate?by=time&$filter")
      assertEquals(ts, 200, tb)
      assert(tb.contains("\"buckets\":["), tb)
      assert(tb.contains("\"by\":\"time\""), tb)
      assert(tb.contains("\"bucket\":10"), tb)
      assert(tb.contains("\"tree\":[]"), s"by=time alone stays the flat strip: $tb")
      // …and the same axis WITH a depth nests the same numbers (what the "by time" tab asks for)
      val (ns, nb) = get(s"$url/api/aggregate?by=time&depth=0&$filter")
      assertEquals(ns, 200, nb)
      assert(nb.contains("\"kind\":\"time\""), s"by=time&depth=0 is the nested answer: $nb")

      // ── by=time,region: time buckets first, then the gazetteer inside each ──
      val (gs, gb) = get(s"$url/api/aggregate?by=time,region&depth=2&samples=2&$filter")
      assertEquals(gs, 200, gb)
      assert(gb.contains("\"by\":\"time,region\""), gb)
      assert(gb.contains("\"depth\":2"), gb)
      assert(gb.contains("\"tree\":["), gb)
      assert(gb.contains("\"kind\":\"time\""), gb)
      assert(gb.contains("\"kind\":\"region\""), gb)
      // unit=sources counts the SOURCE, and this one has a located finding — so it hangs under the
      // real region. (Before `put_source` re-materialised `finding_places`, the fixture had no
      // assignment at all and the whole source fell into `unlocated`, which is what this used to
      // assert.)
      assert(gb.contains("\"placeId\":\"zzg_land\""), s"the located finding places its source: $gb")
      assert(gb.contains("\"citekey\":\"zzg_tree_src\""), s"leaves carry sample sources: $gb")

      // …and the UNLOCATED finding is visible where findings are the unit
      val (us, ub) = get(s"$url/api/aggregate?by=region&unit=findings&depth=2&$filter")
      assertEquals(us, 200, ub)
      assert(ub.contains("\"unlocated\""), s"the source's unlocated finding needs a home: $ub")
      assert(ub.contains("\"placeId\":\"zzg_land\""), s"…and the located one still has its own: $ub")
      assert(!gb.contains("\"asOf\":-"), s"each bucket reads the gazetteer at its OWN midpoint: $gb")

      // ── by=region: one hierarchy, read as of one year ──
      val (rs, rb) = get(s"$url/api/aggregate?by=region&depth=3&asOf=-1400&$filter")
      assertEquals(rs, 200, rb)
      assert(rb.contains("\"by\":\"region\""), rb)
      assert(rb.contains("\"asOf\":-1400"), rb)
      assert(rb.contains("\"buckets\":[]"), s"no flat strip for a region-first axis: $rb")

      // ── by=region,time: the gazetteer first, time buckets under each leaf ──
      val (qs, qb) = get(s"$url/api/aggregate?by=region,time&$filter")
      assertEquals(qs, 200, qb)
      assert(qb.contains("\"by\":\"region,time\""), qb)

      // ── unit=findings counts findings, not sources ──
      val (fs, fb) = get(s"$url/api/aggregate?by=region&unit=findings&$filter")
      assertEquals(fs, 200, fb)
      assert(fb.contains("\"unit\":\"findings\""), fb)

      // ── the errors ──
      val (es, eb) = get(s"$url/api/aggregate?by=author&$filter")
      assertEquals(es, 400, eb)
      assert(eb.contains("time,region"), eb)
      val (us, _) = get(s"$url/api/aggregate?by=region&unit=nonsense&$filter")
      assertEquals(us, 400)

      b.stop()
    }
