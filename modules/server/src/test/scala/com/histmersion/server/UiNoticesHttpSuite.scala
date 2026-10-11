package com.histmersion.server

import ox.supervised
import com.histmersion.core.AppConfig
import com.histmersion.core.UiNotices.Counters
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The toolbar's notices over HTTP: `GET /api/ui/counters` counts what waits and says what is new,
  * `POST /api/ui/seen/{board}` marks a window read — without the API key, which every other write
  * still needs — and the mark only moves forward. Skipped without HISTMERSION_TEST_DB_URL. */
class UiNoticesHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String, key: Option[String] = None) =
    val b = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
    send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).POST(HttpRequest.BodyPublishers.ofString(body)).build())

  private def decode(body: String): Counters =
    io.circe.parser.decode[Counters](body).fold(e => fail(s"$e: $body"), identity)

  /** The queue tables are shared with QueueRepoSuite and OpsHttpSuite (which wipe them): the same
    * advisory lock serialises the three. `var`: the connection outlives `beforeAll`. */
  private var lock: java.sql.Connection = null
  private val LockKey = 2808

  override def beforeAll(): Unit =
    if testDb.isDefined then
      lock = com.histmersion.db.Db.dataSource(AppConfig.load().db.copy(url = testDb.get)).getConnection
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_lock($LockKey)") } finally st.close()

  override def afterAll(): Unit =
    if lock != null then
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_unlock($LockKey)") } finally st.close()
      lock.close()
      lock = null

  test("counts, news, marking read without the key, and the mark only moving forward"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get), auth = base.auth.copy(apiKey = "s3cret"))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}/api"
      // `var`: the fixture's row, removed whatever the assertions did
      var added = Option.empty[Int]
      try
        val (s0, b0) = get(s"$url/ui/counters")
        assertEquals(s0, 200, b0)
        val before = decode(b0)
        assertEquals(before.boards.map(_.key), List("goals", "tasks", "approvals", "acquire", "log"))
        assert(before.boards.filter(_.count.isDefined).map(_.key) == List("tasks", "approvals", "acquire"), b0)
        val tasks0 = before.boards.find(_.key == "tasks").get

        // read the backlog as it is: no key needed, and it is no longer new
        val (m0, mb0) = post(s"$url/ui/seen/tasks", "{}")
        assertEquals(m0, 200, mb0)
        assert(!decode(mb0).boards.find(_.key == "tasks").get.fresh, mb0)

        // something lands in the backlog (a write that still needs the key)
        assertEquals(post(s"$url/queue/tasks", """{"text":"zz notice task"}""")._1, 401)
        val (as, ab) = post(s"$url/queue/tasks", """{"text":"zz notice task"}""", Some("s3cret"))
        assertEquals(as, 200, ab)
        added = io.circe.parser.decode[com.histmersion.core.Queue.QueueItem](ab).toOption.map(_.id)
        val after = decode(get(s"$url/ui/counters")._2).boards.find(_.key == "tasks").get
        assertEquals(after.count, tasks0.count.map(_ + 1))
        assert(after.fresh, after.toString)
        val latest = after.latest.getOrElse(fail("no latest"))

        // an older mark changes nothing; the window's own latest clears it
        val (o, ob) = post(s"$url/ui/seen/tasks", """{"at":"2000-01-01T00:00:00.000000Z"}""")
        assertEquals(o, 200, ob)
        assert(decode(ob).boards.find(_.key == "tasks").get.fresh, ob)
        val (m1, mb1) = post(s"$url/ui/seen/tasks", s"""{"at":"$latest"}""")
        assertEquals(m1, 200, mb1)
        val read = decode(mb1).boards.find(_.key == "tasks").get
        assert(!read.fresh, read.toString)
        assertEquals(read.seen, Some(latest))

        // refused: an unknown board, a malformed instant; a longer path is not the exemption
        assertEquals(post(s"$url/ui/seen/jobs", "{}")._1, 400)
        assertEquals(post(s"$url/ui/seen/tasks", """{"at":"yesterday"}""")._1, 400)
        assertEquals(post(s"$url/ui/seen/tasks/x", "{}")._1, 401)
      finally
        added.foreach { i =>
          val _ = send(HttpRequest.newBuilder(URI.create(s"$url/queue/items/$i"))
            .header("Authorization", "Bearer s3cret").DELETE().build())
        }
        b.stop()
    }
