package com.histmersion.server.media

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{Db, EventRepo, Migrate}
import com.histmersion.server.Composition
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.parser.decode
import java.net.{InetSocketAddress, URI, URLDecoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CountDownLatch, TimeUnit}

/** #1491: a media import over a few hundred places/events is a JOB, through the real server — the request answers at
  * once with the job, the job reports `n/total` as it goes, a picture that cannot be fetched is a line in its summary
  * rather than the end of it, and an import over many is refused on the synchronous endpoint. Wikidata and Commons are
  * a stand-in HTTP server on this machine (the config's API URLs point at it); the database is the test one
  * (HISTMERSION_TEST_DB_URL; skipped otherwise). Fixtures: events `0zqmj_*` (sorting before every other event, so a
  * `limit` job takes exactly these), pictures `File:Zqmj *`.
  */
class MediaImportJobSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val N = 300
  private def id(i: Int) = f"0zqmj_$i%03d"
  private def qid(i: Int) = s"Q${9_100_000 + i}"
  private def idx(q: String): Int = q.stripPrefix("Q").toInt - 9_100_000

  /** Items with no picture statement, and pictures whose download fails (HTTP 500). */
  private def noPicture(i: Int) = i % 30 == 0
  private def brokenFile(i: Int) = i % 50 == 7
  private val noPictures = (0 until N).count(noPicture)
  private val broken = (0 until N).count(i => !noPicture(i) && brokenFile(i))

  private val http = HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def post(url: String, body: String): (Int, String) =
    val r = http.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  private def param(query: String, name: String): Option[String] =
    query.split('&').toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name => URLDecoder.decode(v, UTF_8)
    }

  /** The 3rd Wikidata call waits for this, so the test can see the job's progress mid-way. */
  private val hold = CountDownLatch(1)
  // `var`: how many wbgetentities calls the stand-in answered (its handler runs on its own thread)
  @volatile private var entityCalls = 0

  /** Wikidata's wbgetentities, Commons' imageinfo, and the files, all from the asked ids/titles. */
  private def provider(): HttpServer =
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base = s"http://127.0.0.1:${srv.getAddress.getPort}"
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        val query = Option(ex.getRequestURI.getRawQuery).getOrElse("")
        val (code, mime, body) =
          if path == "/wikidata" then
            entityCalls += 1
            if entityCalls == 3 then { val _ = hold.await(30, TimeUnit.SECONDS) }
            val ids = param(query, "ids").toList.flatMap(_.split('|'))
            val entities = ids.map { q =>
              val claims = if noPicture(idx(q)) then "{}"
              else
                s"""{"P18":[{"mainsnak":{"snaktype":"value","property":"P18","datavalue":{"value":"Zqmj $q.jpg","type":"string"}},"rank":"normal"}]}"""
              s""""$q":{"id":"$q","claims":$claims}"""
            }
            (200, "application/json", s"""{"entities":{${entities.mkString(",")}}}""".getBytes(UTF_8))
          else if path == "/commons" then
            val width = param(query, "iiurlwidth").getOrElse("0")
            val titles = param(query, "titles").toList.flatMap(_.split('|'))
            val pages = titles.zipWithIndex.map { (t, n) =>
              val q = t.stripPrefix("File:Zqmj ").stripSuffix(".jpg")
              s"""{"pageid":${n + 1},"ns":6,"title":"$t","imagerepository":"local","imageinfo":[{
               "size":5000,"width":2000,"height":1500,
               "thumburl":"$base/files/$q-$width.jpg","thumbwidth":$width,"thumbheight":$width,
               "url":"$base/files/$q.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/${t.replace(' ', '_')}",
               "mime":"image/jpeg","extmetadata":{"Artist":{"value":"Zqmj"},"LicenseShortName":{"value":"CC BY-SA 4.0"},
               "License":{"value":"cc-by-sa-4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0"}}}]}"""
            }
            (
              200,
              "application/json",
              s"""{"batchcomplete":true,"query":{"pages":[${pages.mkString(",")}]}}""".getBytes(UTF_8)
            )
          else if path.startsWith("/files/") then
            val q = path.stripPrefix("/files/").takeWhile(_ != '-').stripSuffix(".jpg")
            if brokenFile(idx(q)) then (500, "text/plain", "boom".getBytes(UTF_8))
            else (200, "image/jpeg", Array[Byte](-1, -40, -1) ++ path.getBytes(UTF_8))
          else (404, "text/plain", Array.emptyByteArray)
        ex.getResponseHeaders.add("Content-Type", mime)
        ex.sendResponseHeaders(code, if body.isEmpty then -1 else body.length.toLong)
        if body.nonEmpty then ex.getResponseBody.write(body)
        ex.close()
    )
    srv.start()
    srv

  private lazy val providerServer = provider()
  private lazy val providerUrl = s"http://127.0.0.1:${providerServer.getAddress.getPort}"

  private lazy val cfg =
    val base = AppConfig.load()
    base.copy(
      db = base.db.copy(url = testDb.get),
      media = base.media.copy(
        requestIntervalMs = 0,
        importBatch = 50,
        wikidata = base.media.wikidata.copy(apiUrl = s"$providerUrl/wikidata", batch = 50),
        commons = base.media.commons.copy(apiUrl = s"$providerUrl/commons", batch = 50)
      )
    )
  private lazy val ds =
    val d = testPool(cfg.db)
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE '0zqmj%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider_key LIKE 'File:Zqmj %'".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE '0zqmj%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE '0zqmj%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      (0 until N).foreach(i =>
        EventRepo.put(ds, id(i), EventPut(s"Zqmj event $i", "battle", TimeSpan(Some(-1200), None, None, Some(-1190))))
      )
      connect(ds)(sql"""UPDATE events SET wikidata = 'Q' || (9100000 + substring(id FROM 7)::int)::text
            WHERE id LIKE '0zqmj%'""".update.run())
  override def afterSuite(): Unit =
    if testDb.isDefined then
      clean()
      providerServer.stop(0)

  private def job(url: String, jid: String): JobInfo =
    val (s, b) = get(s"$url/api/jobs/$jid")
    assertEquals(s, 200, b)
    decode[JobInfo](b).fold(e => fail(s"$e: $b"), identity)

  @annotation.tailrec
  private def until(url: String, jid: String, n: Int)(p: JobInfo => Boolean): JobInfo =
    val j = job(url, jid)
    if p(j) || n <= 0 then j else { Thread.sleep(50); until(url, jid, n - 1)(p) }

  test("a 300-event import: the request answers at once with a job, progress is n/total, failures are collected"):
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        // the synchronous endpoint keeps the dry run and ONE place/event; many is a job
        val (rs, rb) = post(s"$url/api/media/import", s"""{"dryRun":false,"limit":$N}""")
        assertEquals(rs, 400, rb)
        assert(rb.contains("/api/media/import/job"), rb)

        val (s, body) = post(s"$url/api/media/import/job", s"""{"dryRun":false,"limit":$N}""")
        assertEquals(s, 202, body)
        val started = decode[JobInfo](body).fold(e => fail(s"$e: $body"), identity)
        assertEquals(started.kind, MediaHandlers.ImportJobKind)
        assert(!started.isTerminal, started.toString)

        // mid-way: two batches of 50 asked, the third held at Wikidata
        val mid = until(url, started.id, 400)(_.progress.exists(_.startsWith(s"100/$N ")))
        assertEquals(mid.status, "running", mid.toString)
        assert(mid.progress.exists(_.contains("batch 2")), mid.toString)
        // a second import while this one runs is refused, not queued behind it
        val (bs, bb) = post(s"$url/api/media/import/job", s"""{"dryRun":false,"limit":1}""")
        assert(bs >= 400 && bb.contains("a media run is already running"), s"$bs $bb")
        hold.countDown()

        val end = until(url, started.id, 1200)(_.isTerminal)
        assertEquals(end.status, "done", end.toString)
        val summary = end.progress.getOrElse(fail(end.toString))
        assert(summary.contains(s"$N place(s)/event(s) asked in ${N / 50} batch(es)"), summary)
        val stored = N - noPictures - broken
        assert(summary.contains(s"$stored stored"), summary)
        assert(summary.contains(s"$broken failed"), summary)

        val (n, links, checks) = connect(ds):
          (
            sql"SELECT count(*)::int FROM assets WHERE provider_key LIKE 'File:Zqmj %'".query[Int].run().head,
            sql"SELECT count(*)::int FROM media_links WHERE target_id LIKE '0zqmj%'".query[Int].run().head,
            sql"SELECT count(*)::int FROM media_checks WHERE target_id LIKE '0zqmj%'".query[Int].run().head
          )
        assertEquals((n, links, checks), (stored, stored, N), "every event recorded as asked, the failed ones included")
      finally b.stop()
    }
