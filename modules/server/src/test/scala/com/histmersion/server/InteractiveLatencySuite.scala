package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.{Lpf, Place}
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig

/** The interactive budgets (docs/research-interactive-performance.md) as a CI check: the requests a hover, a keystroke
  * and a filter change fire, against a server on the test database, each repeated so the statement is prepared
  * server-side and planned again (the generic-plan trap struck on the SIXTH hover), with the p50 held to the budget ×
  * [[Slack]]. Loose on purpose — a CI box is slower and noisier than the reader's machine; scripts/perf/bench.py
  * measures the real thing against a live-sized copy. Gated on HISTMERSION_TEST_DB_URL like the other server suites.
  */
class InteractiveLatencySuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  /** The budget multiplier for CI. */
  private val Slack = 10
  private val Runs = 9

  private val client = java.net.http.HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = client.send(
      java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
      java.net.http.HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)
  private def post(url: String, body: String): (Int, String) =
    val r = client.send(
      java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .build(),
      java.net.http.HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  /** The median wall time (ms) of `Runs` GETs of `url`, each answered 200. */
  private def p50(url: String): Double =
    val ms = (1 to Runs).map { _ =>
      val t0 = System.nanoTime()
      val (s, b) = get(url)
      assertEquals(s, 200, s"$url: $b")
      (System.nanoTime() - t0) / 1e6
    }.sorted
    ms(ms.size / 2)

  private def within(budgetMs: Int, url: String): Unit =
    val m = p50(url)
    assert(m <= budgetMs * Slack, f"$url: p50 $m%.1f ms over $budgetMs ms × $Slack")

  private val doc = Lpf.render(
    List(
      Place.Input(
        Place(
          Place.Id("zzl_world"),
          Place.Label("Zzl World"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region)
        ),
        List(Place.Name(Place.Name.Value("Zzl World")))
      ),
      Place.Input(
        Place(
          Place.Id("zzl_land"),
          Place.Label("Zzl Land"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Some(
            Place.Geometry(
              """{"type":"Polygon","coordinates":[[[60.0,-50.0],[70.0,-50.0],[70.0,-40.0],[60.0,-40.0],[60.0,-50.0]]]}"""
            )
          )
        ),
        List(Place.Name(Place.Name.Value("Zzl Land")), Place.Name(Place.Name.Value("Zzlandia"), Place.Name.Lang("xx"))),
        List(Place.Relation(Place.Relation.ChildId("zzl_land"), Place.Relation.ParentId("zzl_world")))
      )
    )
  )

  test("hover cards, suggestions and a filter change stay within their budgets (loosely)"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}/api"
      try
        assertEquals(post(s"$url/places/import", doc.value)._1, 200)
        val span = "from=-3000&to=-1000"
        // hover: a place's card with its counts, a source link, an article link (brief)
        within(50, s"$url/places/zzl_land?asOf=-1500&counts=true")
        within(50, s"$url/places/zzl_world?asOf=-1500&counts=true")
        // the toolbar's counts and "new" marks, polled by every open page
        within(50, s"$url/ui/counters")
        // typing in the place box and the chip inputs
        List("Z", "Zz", "Zzl", "Zzla", "Zzl Land").foreach(q =>
          within(50, s"$url/places?q=${java.net.URLEncoder.encode(q, "UTF-8")}&asOf=-2000&limit=12")
        )
        within(50, s"$url/facets?field=tags&prefix=b&$span")
        // a chosen place: the lists and the strip
        val f = s"place=zzl_land&place=zzl_world&$span"
        within(150, s"$url/findings?$f")
        within(150, s"$url/sources?$f&limit=20")
        within(150, s"$url/articles?$f&limit=200")
        within(150, s"$url/events?$f&limit=200")
        within(150, s"$url/aggregate?by=time&unit=sources&$f&size=2")
        // the map at a year
        within(150, s"$url/places/geo?kind=polity&asOf=-1200&limit=400")
        // and over the whole span, every state in it ("States shown": all in period) — the shape
        // columns of V090 put a range at the budget of a hover, the whole timeline included
        within(50, s"$url/places/geo?kind=polity&from=-2000&to=-1000&pick=earliest&limit=2000")
        within(50, s"$url/places/geo?kind=polity&from=-3000&to=2000&pick=latest&limit=2000")
      finally b.stop()
    }
