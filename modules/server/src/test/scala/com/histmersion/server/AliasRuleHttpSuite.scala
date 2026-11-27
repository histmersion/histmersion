package com.histmersion
package server
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{Attestation, EventDeleted, Lpf, Place}
import com.histmersion.api.library.SearchResult
import com.histmersion.api.views.EventDetail
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The alias rule over real HTTP and MCP (#1799, #890): for every renamed kind — source, article, event, goal, place —
  * a put, a patch, a delete and a rename naming the OLD identifier go to the record under its current one, and the
  * answer names the current one; a reference (an attestation's target) is stored against the current id; a
  * `place=<old id>` filter selects what `place=<new id>` does. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AliasRuleHttpSuite extends DbSuite with db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())
  private def mcp(url: String, tool: String, args: String): (Int, String) =
    send(
      json(s"$url/mcp")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers
            .ofString(s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$args}}""")
        )
        .build()
    )
  private def decode[A: JsonValueCodec](s: String): A = RawJson.decode[A](s).fold(e => fail(s"$e in $s"), identity)
  private def ok(r: (Int, String)): String = { assertEquals(r._1, 200, r._2); r._2 }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def withServer(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  /** A previous run's rows and aliases must not be in the way. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def clean(): Unit =
    val ds = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    com.augustnagro.magnum.transact(ds):
      import com.augustnagro.magnum.*
      sql"DELETE FROM attestations WHERE citekey LIKE 'zzalias%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzalias%'".update.run()
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zzalias%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zz_alias%'".update.run()
      sql"DELETE FROM article_aliases WHERE alias LIKE 'zz_alias%'".update.run()
      sql"DELETE FROM events WHERE id LIKE 'zz_alias%'".update.run()
      sql"DELETE FROM event_aliases WHERE alias LIKE 'zz_alias%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zz-alias%'".update.run()
      sql"DELETE FROM goal_aliases WHERE alias LIKE 'zz-alias%'".update.run()
      sql"DELETE FROM place_aliases WHERE alias LIKE 'zz_alias%'".update.run()
      sql"DELETE FROM places WHERE id LIKE 'zz_alias%'".update.run()

  test("goals and events: put, patch, rename and delete by the OLD id go to the current record"):
    clean()
    withServer { url =>
      ok(put(s"$url/api/goals/zz-alias-g1", """{"title":"Alias goal"}"""))
      ok(post(s"$url/api/goals/zz-alias-g1/rename", """{"to":"zz-alias-g2"}"""))
      assertEquals(
        decode[Goal](ok(patch(s"$url/api/goals/zz-alias-g1", """{"notes":"via the old slug"}"""))).slug,
        "zz-alias-g2"
      )
      assertEquals(decode[Goal](ok(put(s"$url/api/goals/zz-alias-g1", """{"title":"Replaced"}"""))).slug, "zz-alias-g2")
      val (_, m) = mcp(url, "patch_goal", """{"slug":"zz-alias-g1","status":"seeding"}""")
      assert(m.contains("`zz-alias-g1` is an old slug of this goal — it went to `zz-alias-g2`"), m)
      // a rename by the old slug renames the goal under its current one
      assertEquals(
        decode[LabelRenamed](ok(post(s"$url/api/goals/zz-alias-g1/rename", """{"to":"zz-alias-g3"}"""))).from,
        "zz-alias-g2"
      )
      assertEquals(decode[Goal](ok(get(s"$url/api/goals/zz-alias-g1"))).slug, "zz-alias-g3")
      assertEquals(decode[Goal.Deleted](ok(delete(s"$url/api/goals/zz-alias-g2"))).slug, "zz-alias-g3")
      assertEquals(get(s"$url/api/goals/zz-alias-g3")._1, 404)

      ok(
        put(s"$url/api/events/zz_alias_e1", """{"label":"Alias event","kind":"battle","timeSpan":{"beginBy":-1274}}""")
      )
      ok(post(s"$url/api/events/zz_alias_e1/rename", """{"to":"zz_alias_e2"}"""))
      assertEquals(
        decode[EventDetail](ok(patch(s"$url/api/events/zz_alias_e1", """{"description":"by the old id"}"""))).event.id,
        "zz_alias_e2"
      )
      val (_, me) = mcp(url, "patch_event", """{"id":"zz_alias_e1","label":"Alias event again"}""")
      assert(me.contains("it went to `zz_alias_e2`"), me)
      assertEquals(decode[EventDetail](ok(get(s"$url/api/events/zz_alias_e2"))).event.label, "Alias event again")
      // a reference names the event by its current id
      ok(put(s"$url/api/sources/zzalias2020_src", """{"title":"Alias source","body":"The battle of Qadesh."}"""))
      val a = decode[Attestation](
        ok(
          post(
            s"$url/api/attestations",
            """{"citekey":"zzalias2020_src","targetType":"event","targetId":"zz_alias_e1","quote":"battle"}"""
          )
        )
      )
      assertEquals(a.targetId, "zz_alias_e2")
      assertEquals(
        (decode[Attestation.List](
          ok(get(s"$url/api/attestations?targetType=event&targetId=zz_alias_e1"))
        ).total.value: Int),
        1
      )
      assertEquals(decode[EventDeleted](ok(delete(s"$url/api/events/zz_alias_e1"))).id, "zz_alias_e2")
    }

  test("sources and articles: put by the old key replaces the renamed record, never shadows the alias"):
    clean()
    withServer { url =>
      ok(put(s"$url/api/sources/zzalias2020_old", """{"title":"Old key source"}"""))
      ok(post(s"$url/api/sources/zzalias2020_old/rename", """{"newCitekey":"zzalias2021_new"}"""))
      val sw = decode[Source.WithFindings](
        ok(put(s"$url/api/sources/zzalias2020_old", """{"title":"Replaced by the old key"}"""))
      )
      assertEquals((sw.source.citekey, sw.source.title), ("zzalias2021_new", "Replaced by the old key"))
      val (_, ms) = mcp(url, "patch_source", """{"citekey":"zzalias2020_old","patch":{"summary":"via MCP"}}""")
      assert(ms.contains("`zzalias2020_old` is an old citekey of this source — it went to `zzalias2021_new`"), ms)

      ok(
        put(
          s"$url/api/articles/zz_alias_a1",
          """{"title":"Alias article","kind":"concept","body":"# Alias article\n\nText."}"""
        )
      )
      ok(post(s"$url/api/articles/zz_alias_a1/rename", """{"to":"zz_alias_a2"}"""))
      val v = decode[Article.View](
        ok(
          put(
            s"$url/api/articles/zz_alias_a1",
            """{"title":"Replaced","kind":"concept","body":"# Replaced\n\nNew text."}"""
          )
        )
      )
      assertEquals(v.article.slug, "zz_alias_a2")
      assertEquals(
        decode[Article.View](
          ok(patch(s"$url/api/articles/zz_alias_a1", """{"summary":"by the old slug"}"""))
        ).article.slug,
        "zz_alias_a2"
      )
      // the one exception: an IMPORTED note filed under the old slug is a stale mirror file — refused,
      // naming the current slug, instead of creating an article that shadows the alias
      val (is, ib) = post(
        s"$url/api/articles/import",
        """{"text":"---\nslug: zz_alias_a1\ntitle: Stale\nkind: concept\n---\n# Stale\n","force":true}"""
      )
      assertEquals(is, 400, ib)
      assert(ib.contains("renamed to 'zz_alias_a2'"), ib)
      assertEquals(decode[Article.Deleted](ok(delete(s"$url/api/articles/zz_alias_a1"))).slug, "zz_alias_a2")
    }

  test("places: writes by the old id follow, and place=<old id> selects what place=<new id> does (#890)"):
    clean()
    withServer { url =>
      val geo = Lpf.render(
        List(
          Place.Input(
            Place(
              Place.Id("zz_alias_p1"),
              Place.Label("Zzalias Hill"),
              Place.KindValue(Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[30.5,39.5]}"""))
            ),
            List(Place.Name(Place.Name.Value("Zzalias Hill")))
          )
        )
      )
      ok(post(s"$url/api/places/import", geo.value))
      ok(
        put(
          s"$url/api/sources/zzalias2020_place",
          """{"title":"Placed source","tags":["zz-alias-place"],
        |"findings":[{"claim":"A wall on the hill","region":"Zzalias Hill","lat":39.5,"lon":30.5}]}""".stripMargin
        )
      )
      ok(post(s"$url/api/places/zz_alias_p1/rename", """{"to":"zz_alias_p2"}"""))
      val byNew = decode[SearchResult](ok(get(s"$url/api/sources?place=zz_alias_p2&tag=zz-alias-place")))
      val byOld = decode[SearchResult](ok(get(s"$url/api/sources?place=zz_alias_p1&tag=zz-alias-place")))
      assertEquals((byNew.total.value: Int), 1, "the finding is placed at the renamed place")
      assertEquals(byOld.hits.map(_.source.citekey), byNew.hits.map(_.source.citekey))
      // a write by the old id names the place it went to
      val n = ok(post(s"$url/api/places/zz_alias_p1/names", """{"name":"Zzalias Höyük"}"""))
      assert(n.contains("zz_alias_p2"), n)
      assertEquals(
        decode[LabelRenamed](ok(post(s"$url/api/places/zz_alias_p1/rename", """{"to":"zz_alias_p3"}"""))).from,
        "zz_alias_p2"
      )
      assert(ok(get(s"$url/api/places/zz_alias_p1")).contains("\"zz_alias_p3\""))
    }
