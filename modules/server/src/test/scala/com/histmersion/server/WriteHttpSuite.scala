package com.histmersion.server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The write surface over real HTTP: status codes are part of the contract (412 on a failed `ifUpdatedAt` precondition,
  * 404 on an unknown citekey), so they are asserted through the wire and not just through the repo. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class WriteHttpSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
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

  private val ck = "zz_http_writes"

  test("put → get → patch → export over HTTP, with 412 on a stale precondition"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"

      // ── put: a full source, with omitted optional keys defaulting rather than failing to decode ──
      val (ps, pb) = put(
        s"$url/api/sources/$ck",
        s"""{"title":"HTTP writes","authors":["A"],"year":2020,"status":"current",
            |"tags":["adna"],"summary":"s","notes":"# article",
            |"findings":[{"claim":"one","lon":32.0,"lat":39.0,"periodStart":-1800,"periodEnd":-1700}]}""".stripMargin
      )
      assertEquals(ps, 200, pb)
      assert(pb.contains("HTTP writes"), pb)

      val (gs, gb) = get(s"$url/api/sources/$ck")
      assertEquals(gs, 200)
      assert(gb.contains("\"notes\""), gb)
      val token = """"updatedAt":"([^"]+)"""".r.findFirstMatchIn(gb).map(_.group(1)).get

      // ── patch: only tags move ──────────────────────────────────────────────────────────────
      val (as, ab) = patch(s"$url/api/sources/$ck", """{"tags":{"add":["sea-peoples"]}}""")
      assertEquals(as, 200, ab)
      assert(ab.contains("sea-peoples"), ab)
      assert(ab.contains("\"one\""), "the finding must survive a tags-only patch: " + ab)

      // ── 412: the token we captured before the patch is now stale ───────────────────────────
      val (fs, fb) = patch(s"$url/api/sources/$ck", s"""{"summary":"x","ifUpdatedAt":"$token"}""")
      assertEquals(fs, 412, fb)
      assert(fb.contains("has moved on"), fb)

      // ── 404 on an unknown citekey, for both patch and export ───────────────────────────────
      assertEquals(patch(s"$url/api/sources/zz_nope", """{"summary":"x"}""")._1, 404)
      assertEquals(get(s"$url/api/sources/zz_nope/export")._1, 404)

      // ── 400 on an invalid patch ────────────────────────────────────────────────────────────
      assertEquals(patch(s"$url/api/sources/$ck", """{"period":{"start":-100,"end":-900}}""")._1, 400)

      // ── export + citekeys ──────────────────────────────────────────────────────────────────
      val (es, eb) = get(s"$url/api/sources/$ck/export")
      assertEquals(es, 200)
      assert(eb.startsWith("---\n"), eb.take(80))
      assert(eb.contains(s"citekey: $ck"), eb.take(200))
      assert(eb.contains("# article"), eb)

      val (ks, kb) = get(s"$url/api/citekeys")
      assertEquals(ks, 200)
      assert(kb.contains(ck), kb.take(200))

      // ── import a raw note, then the seed guard skips it when the DB row is newer ───────────
      // mtime 1 (1970) is always older than the row this write creates, so the guard has something
      // to bite on; the first call is forced so the suite is re-runnable against a dirty database.
      val note = "---\ncitekey: zz_http_import\ntitle: Imported\n---\nthe article\n"
      val (is, ib) = post(s"$url/api/sources/import", s"""{"text":${quote(note)},"mtime":1,"force":true}""")
      assertEquals(is, 200, ib)
      assert(ib.contains("\"imported\":1"), ib)

      // the same note, unforced: the row we just wrote is newer than mtime 1 → skipped
      val (ss, sb) = post(s"$url/api/sources/import", s"""{"text":${quote(note)},"mtime":1}""")
      assertEquals(ss, 200, sb)
      assert(sb.contains("\"imported\":0"), sb)
      assert(sb.contains("zz_http_import"), sb)

      // ...unless forced
      val (rs, rb) = post(s"$url/api/sources/import", s"""{"text":${quote(note)},"mtime":1,"force":true}""")
      assertEquals(rs, 200, rb)
      assert(rb.contains("\"imported\":1"), rb)

      b.stop()
    }

  /** Minimal JSON string literal (the note text carries newlines). */
  private def quote(s: String): String =
    "\"" + s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case c    => c.toString
    } + "\""

  // #698 / #745 / #721 through the wire: what a JSON caller (the MCP tool, `patch --json`) sends
  test("patch over HTTP: a foreign edge src is a 400, null clears metadata, calibreId re-links"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    val k = "zz_http_patch_rules"
    val dst = "zz_http_patch_dst"
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/sources/$dst", """{"title":"Target","authors":["B"]}""")._1, 200)
        val (ps, pb) = put(
          s"$url/api/sources/$k",
          """{"title":"Rules","authors":["A"],"year":2001,"doi":"10.1/zzrules","venue":"V","calibreId":"1805"}"""
        )
        assertEquals(ps, 200, pb)

        val (es, eb) = patch(
          s"$url/api/sources/$k",
          s"""{"edges":{"add":[{"src":"zz_someone_else","dst":"$dst","kind":"refutes"}]}}"""
        )
        assertEquals(es, 400, eb)
        assert(eb.contains("is not the patched source"), eb)

        val (os, ob) = patch(s"$url/api/sources/$k", s"""{"edges":{"add":[{"dst":"$dst","kind":"refutes"}]}}""")
        assertEquals(os, 200, ob)
        val (_, nb0) = get(s"$url/api/neighbors/$k?kind=refutes")
        assert(nb0.contains(dst), "the edge is stored FROM the patched source: " + nb0)
        assert(!nb0.contains("zz_someone_else"), nb0)

        val (ns, nb) = patch(s"$url/api/sources/$k", """{"metadata":{"doi":null,"year":null,"calibreId":"1806"}}""")
        assertEquals(ns, 200, nb)
        val (_, gb) = get(s"$url/api/sources/$k")
        assert(!gb.contains("10.1/zzrules"), "doi:null must clear the DOI: " + gb.take(400))
        assert(!gb.contains("\"year\":2001"), "year:null must clear the year: " + gb.take(400))
        assert(gb.contains("\"venue\":\"V\""), "an absent field is left alone: " + gb.take(400))
        val (_, mb) = get(s"$url/api/acquire/calibre/meta?citekey=$k")
        assert(mb.contains("\"calibreId\":\"1806\""), "metadata.calibreId must re-link: " + mb)
        assertEquals(patch(s"$url/api/sources/$k", """{"metadata":{"title":null}}""")._1, 400)
      finally b.stop()
    }

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

  // #813: a misplaced or unknown key used to be dropped and the write reported success
  test("strict writes over HTTP and MCP: an unknown or misplaced key is a 400 naming it, and nothing is written"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    val k = "zz_http_strict"
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/sources/$k", """{"title":"Strict","authors":["A"],"tags":["keep"]}""")._1, 200)

        val (s1, b1) = patch(s"$url/api/sources/$k", """{"status":"contested","tags":{"add":["x"]}}""")
        assertEquals(s1, 400, b1)
        assert(b1.contains("unknown key 'status'"), b1)
        assert(b1.contains("metadata.status"), b1)
        val (s2, b2) = patch(s"$url/api/sources/$k", """{"tags":{"items":["x"]}}""")
        assertEquals(s2, 400, b2)
        assert(b2.contains("unknown key 'items'"), b2)
        val (s3, b3) = put(s"$url/api/sources/$k", """{"title":"Strict","tag":["x"]}""")
        assertEquals(s3, 400, b3)
        assert(b3.contains("unknown key 'tag'"), b3)
        val (s4, b4) =
          put(s"$url/api/articles/zz-http-strict", """{"title":"T","kind":"concept","body":"b","tagz":[]}""")
        assertEquals(s4, 400, b4)
        assert(b4.contains("unknown key 'tagz'"), b4)

        val (_, gb) = get(s"$url/api/sources/$k")
        assert(gb.contains("\"status\":\"unassessed\""), "a refused patch writes nothing: " + gb.take(300))
        assert(!gb.contains("\"x\""), gb.take(300))

        // the same through the MCP tool, whose input wraps the same DTO
        val (_, m1) =
          mcp(url, "patch_source", s"""{"citekey":"$k","patch":{"findings":{"add":[{"claim":"c","date":1}]}}}""")
        assert(m1.contains("unknown key 'date'"), m1)
        val (_, m2) = mcp(url, "patch_source", s"""{"citekey":"$k","patch":{},"tags":{"add":["x"]}}""")
        assert(m2.contains("unknown key 'tags'"), m2)
        val (_, gb2) = get(s"$url/api/sources/$k")
        assert(!gb2.contains("\"x\""), gb2.take(300))
      finally b.stop()
    }

  // #3770: put_attestation with an unknown target type (or a missing source) answered 500, the constraint's
  test(
    "an attestation with an unknown target type is a 400 naming the five, a missing source a 404 — over HTTP and MCP"
  ):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    val k = "zz_http_attest"
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/sources/$k", """{"title":"Attested"}""")._1, 200)
        val (s1, b1) = post(s"$url/api/attestations", s"""{"citekey":"$k","targetType":"bogus","targetId":"x"}""")
        assertEquals(s1, 400, b1)
        assert(b1.contains("unknown target type 'bogus' (finding, event, event_dating, place, edge)"), b1)
        val (s2, b2) =
          post(s"$url/api/attestations", """{"citekey":"zz_http_attest_none","targetType":"event","targetId":"x"}""")
        assertEquals(s2, 404, b2)
        val (m1s, m1) = mcp(url, "put_attestation", s"""{"citekey":"$k","targetType":"bogus","targetId":"x"}""")
        assertEquals(m1s, 200, m1)
        assert(m1.contains("unknown target type") && !m1.contains("Internal server error"), m1)
        // a valid one, its type in any case, is written
        val (s3, b3) = post(s"$url/api/attestations", s"""{"citekey":"$k","targetType":" Event ","targetId":"zz_e"}""")
        assertEquals(s3, 200, b3)
        assert(b3.contains("\"targetType\":\"event\""), b3)
        val id = "\"id\":(\\d+)".r.findFirstMatchIn(b3).map(_.group(1)).getOrElse(fail(b3))
        assertEquals(send(json(s"$url/api/attestations/$id").DELETE().build())._1, 200)
      finally b.stop()
    }

  // #766: put_source rewrote a foreign edge src to the citekey (the #698 pattern, fixed for patch only)
  test("put over HTTP: a foreign edge src is a 400; an omitted src or an old citekey of the source is accepted"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    val k = "zz_http_put_edges"
    val dst = "zz_http_put_edges_dst"
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/sources/$dst", """{"title":"Target"}""")._1, 200)
        val (s1, b1) = put(
          s"$url/api/sources/$k",
          s"""{"title":"P","edges":[{"src":"zz_someone_else","dst":"$dst","kind":"refutes"}]}"""
        )
        assertEquals(s1, 400, b1)
        assert(b1.contains("is not the source being put"), b1)
        val (s2, b2) = put(s"$url/api/sources/$k", s"""{"title":"P","edges":[{"dst":"$k","kind":"refutes"}]}""")
        assertEquals(s2, 400, b2)
        assert(b2.contains("self-edge"), b2)
        val (s3, b3) =
          put(s"$url/api/sources/$k", s"""{"title":"P","edges":[{"src":"$k","dst":"$dst","kind":"refutes"}]}""")
        assertEquals(s3, 200, b3)
        val (_, nb) = get(s"$url/api/neighbors/$k?kind=refutes")
        assert(nb.contains(dst), nb)

        // rename, then put to the new key naming the OLD key as src: the same source
        val k2 = k + "_renamed"
        get(s"$url/api/sources/$k2") match
          case (200, _) => () // a re-run: already renamed
          case _        =>
            assertEquals(post(s"$url/api/sources/$k/rename", s"""{"newCitekey":"$k2"}""")._1, 200)
        val (s4, b4) =
          put(s"$url/api/sources/$k2", s"""{"title":"P2","edges":[{"src":"$k","dst":"$dst","kind":"challenges"}]}""")
        assertEquals(s4, 200, b4)
        val (_, nb2) = get(s"$url/api/neighbors/$k2?kind=challenges")
        assert(nb2.contains(dst), nb2)
      finally b.stop()
    }
