package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.{AppConfig, AuthConfig, BackupsConfig, DbConfig, ServerConfig}
import java.io.{BufferedReader, InputStreamReader}
import java.net.{Socket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import com.histmersion.db.TestDb
import com.histmersion.util.nonempty.NonEmptyVector

/** Who the server lets in, over real HTTP on loopback. The peer of every request here is 127.0.0.1, so the two sides of
  * the trust decision are two settings: the default (loopback trusted as the owner) and [[KeyedLoopback]] (loopback
  * listed as an authenticated network, standing for a client on another one). Every refusal is the gate's — 421 for a
  * foreign `Host`, 403 for another site's change, 401 for a missing key — and is answered as every endpoint answers a
  * problem. Skipped without HISTMERSION_TEST_DB_URL.
  */
class AuthHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key = "s3cret"
  private val Dump = "zz-access.dump"
  private val ck = "zz_http_auth"

  private val http = HttpClient.newHttpClient()

  final private case class Answer(status: Int, body: String, authenticate: Option[String])

  private def send(
      url: String,
      method: String = "GET",
      body: Option[String] = None,
      key: Option[String] = None,
      headers: List[(String, String)] = Nil
  ): Answer =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .method(method, body.fold(HttpRequest.BodyPublishers.noBody())(HttpRequest.BodyPublishers.ofString(_)))
    val withBody = if body.isDefined then b.header("content-type", "application/json") else b
    val withKey = key.fold(withBody)(k => withBody.header("Authorization", s"Bearer $k"))
    val r = http.send(
      headers.foldLeft(withKey)((acc, h) => acc.header(h._1, h._2)).build(),
      HttpResponse.BodyHandlers.ofString()
    )
    Answer(r.statusCode, r.body, Option(r.headers.firstValue("www-authenticate").orElse(null)))

  private def upload(url: String, bytes: Array[Byte], key: Option[String]) =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .header("content-type", "application/octet-stream")
      .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  /** The status line's code of a GET sent with `host` as its `Host` header, which the JDK client will not set. */
  private def statusWithHost(port: Int, host: String, path: String): Int =
    val socket = Socket("127.0.0.1", port)
    try
      val out = socket.getOutputStream
      out.write(s"GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".getBytes(UTF_8))
      out.flush()
      val in = BufferedReader(InputStreamReader(socket.getInputStream, UTF_8))
      in.readLine().split(' ')(1).toInt
    finally socket.close()

  /** The accepted upload's job converts through this stub, never the live sidecar on :8100. */
  private val converter = OfflineConverter()

  /** A server on loopback over the test database, with `auth` as its access settings, and one backup archive. */
  private def withServer(auth: AuthConfig => AuthConfig)(body: (String, Int) => Unit): Unit =
    val backups = Files.createTempDirectory("zz-access-backups")
    val _ = Files.writeString(backups.resolve(Dump), "DUMP")
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.auth)
      .setTo(auth(base.auth))
      .modify(_.backups)
      .setTo(base.backups.modify(_.dir).setTo(BackupsConfig.Dir(backups.toString)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), converter).start()
      try body(s"http://127.0.0.1:${b.port}", b.port)
      finally b.stop()
    }

  private def keyed(auth: AuthConfig): AuthConfig = KeyedLoopback(auth, Key)
  private def trustedWithKey(auth: AuthConfig): AuthConfig = auth.modify(_.apiKey).setTo(AuthConfig.ApiKey(Key))

  test("from a network the server does not trust: a read needs the key like a write, and the backups above all"):
    withServer(keyed) { (url, _) =>
      val anonymous = send(s"$url/api/backups/$Dump")
      assertEquals(anonymous.status, 401, anonymous.body)
      assert(anonymous.body.contains("\"Unauthenticated\""), anonymous.body)
      assert(anonymous.body.contains("Authorization: Bearer"), "the answer says what to send: " + anonymous.body)
      assertEquals(anonymous.authenticate, Some("Bearer"))
      assertEquals(send(s"$url/api/backups/$Dump", key = Some("wrong")).status, 401)
      val served = send(s"$url/api/backups/$Dump", key = Some(Key))
      assertEquals((served.status, served.body), (200, "DUMP"))

      // the other sensitive reads
      List("/api/backups", "/api/settings", "/api/agent-state", "/api/jobs", "/api/config").foreach { p =>
        assertEquals(send(s"$url$p").status, 401, p)
      }
      val config = send(s"$url/api/config", key = Some(Key))
      assertEquals(config.status, 200, config.body)
      assert(config.body.contains("\"keyRequired\":true"), config.body)
      assert(!config.body.contains(Key), "the key is never served: " + config.body)

      // the public allow-list: the probes and the page that asks for the key
      assertEquals(send(s"$url/api/health").status, 200)
      assertEquals(send(s"$url/api/version").status, 200)
      assertEquals(send(s"$url/").status, 200)
      assertEquals(send(s"$url/web/style.css").status, 200)

      // writes as before: a JSON write, a raw-body upload, a body-less delete
      val source = s"$url/api/sources/$ck"
      assertEquals(send(source, "PUT", Some("""{"title":"auth gated"}""")).status, 401)
      assertEquals(send(source, "PUT", Some("""{"title":"auth gated"}"""), Some(Key)).status, 200)
      val pdf = "%PDF-1.4\n%auth test\n".getBytes(UTF_8)
      val refused = upload(s"$url/api/ingest?citekey=zz_http_auth_upload&filename=a.pdf", pdf, None)
      assertEquals(refused.statusCode, 401, refused.body)
      // a refused body is drained, not left in the connection: the next requests are answered
      assertEquals(send(s"$url/api/health").status, 200)
      val accepted = upload(s"$url/api/ingest?citekey=zz_http_auth_upload&filename=a.pdf", pdf, Some(Key))
      assertEquals(accepted.statusCode, 202, accepted.body)
      val deadline = System.currentTimeMillis() + 10000
      while converter.converted.isEmpty && System.currentTimeMillis() < deadline do Thread.sleep(50)
      assert(converter.converted.exists(_.value.endsWith(".pdf")), s"the upload never reached the stub")
      assertEquals(send(s"$url/api/articles/zz-auth-none", "DELETE").status, 401)
    }

  test("from a trusted network (loopback, by default): served with no key, even with one configured"):
    withServer(trustedWithKey) { (url, _) =>
      val served = send(s"$url/api/backups/$Dump")
      assertEquals((served.status, served.body), (200, "DUMP"))
      val config = send(s"$url/api/config")
      assertEquals(config.status, 200, config.body)
      assert(config.body.contains("\"keyRequired\":false"), config.body)
      assertEquals(send(s"$url/api/sources/$ck", "PUT", Some("""{"title":"auth trusted"}""")).status, 200)
    }

  test("a client's own admission header is replaced by the gate's"):
    withServer(keyed) { (url, _) =>
      val forged = send(s"$url/api/config", key = Some(Key), headers = List("X-Histmersion-Admission" -> "trusted"))
      assert(forged.body.contains("\"keyRequired\":true"), forged.body)
    }

  test("no key configured, on this machine: everything as it always was"):
    withServer(identity) { (url, _) =>
      val config = send(s"$url/api/config")
      assert(config.body.contains("\"keyRequired\":false"), config.body)
      assertEquals(send(s"$url/api/sources/$ck", "PATCH", Some("""{"summary":"open"}""")).status, 200)
    }

  test("a foreign Host is answered 421 whoever asks; the server's own names pass (DNS rebinding)"):
    for auth <- List[AuthConfig => AuthConfig](identity, keyed) do
      withServer(auth) { (_, port) =>
        assertEquals(statusWithHost(port, "rebound.example", "/api/health"), 421)
        assertEquals(statusWithHost(port, s"rebound.example:$port", s"/api/backups/$Dump"), 421)
        assertEquals(statusWithHost(port, s"localhost:$port", "/api/health"), 200)
        assertEquals(statusWithHost(port, s"[::1]:$port", "/api/health"), 200)
      }

  test("another site's change is answered 403 whoever sends it; the server's own page, and no page at all, pass"):
    for (auth, key) <- List[(AuthConfig => AuthConfig, Option[String])]((identity, None), (keyed, Some(Key))) do
      withServer(auth) { (url, port) =>
        def put(headers: List[(String, String)]) =
          send(s"$url/api/sources/$ck", "PUT", Some("""{"title":"origin"}"""), key, headers).status
        assertEquals(put(List("Origin" -> "https://evil.example")), 403)
        assertEquals(put(List("Referer" -> "https://evil.example/page")), 403)
        assertEquals(put(List("Origin" -> "null")), 403)
        val refused = send(s"$url/api/sources/$ck", "PUT", Some("{}"), key, List("Origin" -> "https://evil.example"))
        assert(refused.body.contains("CrossSite"), refused.body)
        assertEquals(put(List("Origin" -> s"http://127.0.0.1:$port")), 200)
        assertEquals(put(List("Origin" -> s"http://localhost:$port")), 200)
        assertEquals(put(Nil), 200) // the CLI and MCP clients send no Origin
      }

  /** A server over the test database listening on every address of `binds` (each on an ephemeral port of its own). */
  private def withListener(auth: AuthConfig => AuthConfig, binds: NonEmptyVector[ServerConfig.Host])(
      body: Vector[String] => Unit
  ): Unit =
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.auth)
      .setTo(auth(base.auth))
    supervised {
      val bound = Composition.listener(cfg, binds, ServerConfig.Port(0)).start()
      try
        body(bound.toVector.zip(binds.toVector).map { (b, h) =>
          if s"$h".contains(':') then s"http://[$h]:${b.port}" else s"http://$h:${b.port}"
        })
      finally bound.toVector.foreach(_.stop())
    }

  test("every address of server.host is bound, each behind the same gate"):
    withListener(identity, NonEmptyVector.of(ServerConfig.Host("127.0.0.1"), ServerConfig.Host("::1"))) { urls =>
      assertEquals(urls.size, 2)
      urls.foreach { url =>
        assertEquals(send(s"$url/api/health").status, 200, url)
        val config = send(s"$url/api/config")
        assertEquals(config.status, 200, url)
        assert(config.body.contains("\"keyRequired\":false"), s"loopback of either family is trusted: $url")
        assertEquals(
          send(s"$url/api/sources/$ck", "PUT", Some("{}"), headers = List("Origin" -> "https://evil.example")).status,
          403,
          url
        )
      }
    }

  test("a refused network is answered 403 with its own rule, the public probes included"):
    // IPv4 loopback refused, so the peer of these requests stands for a refused network; the bind's whole reach is
    // accounted for, so the server starts with no key
    val refusedLoopback: AuthConfig => AuthConfig = _.modify(_.trustedNetworks)
      .setTo(List(AuthConfig.Network("::1/128")))
      .modify(_.refusedNetworks)
      .setTo(List(AuthConfig.Network("127.0.0.0/8")))
    withListener(refusedLoopback, NonEmptyVector.of(ServerConfig.Host("127.0.0.1"))) { urls =>
      for path <- List("/api/health", "/", "/api/settings") do
        val refused = send(s"${urls.head}$path")
        assertEquals(refused.status, 403, path)
        assert(refused.body.contains("NetworkRefused"), refused.body)
        assertEquals(refused.authenticate, None, "a refused network is not asked for a key")
    }

  test("a request a proxy forwarded earns no trust from the proxy's loopback address"):
    withServer(trustedWithKey) { (url, _) =>
      val proxied = List("X-Forwarded-For" -> "203.0.113.9")
      assertEquals(send(s"$url/api/backups/$Dump", headers = proxied).status, 401)
      assertEquals(send(s"$url/api/backups/$Dump", key = Some(Key), headers = proxied).status, 200)
    }
