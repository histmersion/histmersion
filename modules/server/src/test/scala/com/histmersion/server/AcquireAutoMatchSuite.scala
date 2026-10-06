package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.Db
import ox.supervised

import com.augustnagro.magnum.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}

/** #970 over real HTTP, the converter stubbed: an import that names no acquisition item closes the
  * open items that name its DOI (whatever case, prefix or punctuation they write it with), and only
  * LISTS the items that resemble it — a preprint's published version among them — in the job's
  * progress. A re-OCR imports nothing and matches nothing; an explicit `acquireItem` is the whole
  * story. Skipped without HISTMERSION_TEST_DB_URL. */
class AcquireAutoMatchSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(90, "s")

  private def client() = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def send(req: HttpRequest): (Int, String) =
    val r = client().send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String = "{}") =
    send(HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def upload(url: String, file: Path) =
    send(HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/octet-stream")
      .POST(HttpRequest.BodyPublishers.ofFile(file)).build())
  private def field(json: String, name: String): Option[String] =
    s""""$name":"((?:[^"\\\\]|\\\\.)*)"""".r.findFirstMatchIn(json).map(_.group(1))
  private def enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

  private object Stub extends ConverterClient:
    def health = Right("ok")
    def convert(file: Path, filename: String, quality: Boolean) =
      Right(ConvertedDoc(markdown = "# Converted\n\nBody.\n", pages = 1, filename = Some(filename)))

  private lazy val cfg =
    val base = AppConfig.load()
    base.copy(db = base.db.copy(url = testDb.get),
      converter = base.converter.copy(spoolDir = Files.createTempDirectory("histmersion-automatch").toString))

  private def clean(): Unit =
    val ds = Db.dataSource(cfg.db)
    com.histmersion.db.Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM queue_items WHERE text LIKE 'zzauto%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzauto%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterAll(): Unit = if testDb.isDefined then clean()

  private def tempPdf(marker: String): Path =
    val p = Files.createTempFile("histmersion-automatch-", ".pdf")
    Files.write(p, s"%PDF-1.4 $marker ${System.nanoTime}".getBytes("UTF-8"))
    p

  private def await(url: String, id: String): String =
    def go(n: Int): String =
      val (_, b) = get(s"$url/api/jobs/$id")
      val st     = field(b, "status").getOrElse("")
      if st == "done" || st == "failed" || n > 300 then b
      else { Thread.sleep(100); go(n + 1) }
    go(0)

  private def addItem(url: String, text: String): Int =
    val (st, b) = post(s"$url/api/queue/acquire", io.circe.Json.obj("text" -> io.circe.Json.fromString(text)).noSpaces)
    assertEquals(st, 200, b)
    """"id":(\d+)""".r.findFirstMatchIn(b).get.group(1).toInt

  private def item(url: String, id: Int): String =
    val (_, b) = get(s"$url/api/queue/acquire?all=true")
    b.split("""\{"id":""").find(_.startsWith(s"$id,")).getOrElse(fail(s"item $id not listed: $b"))

  private val Ck    = "zzauto2021_rise"
  private val Doi   = "10.1163/zzauto.12345"
  private val Title = "The Zzauto Rise of Bronze Societies"

  private def ingest(url: String, extra: String = ""): String =
    val q = s"citekey=$Ck&force=true&doi=${enc(Doi)}&title=${enc(Title)}&author=${enc("Kristiansen, Kristian")}$extra"
    val (st, b) = upload(s"$url/api/ingest?$q", tempPdf(Ck))
    assertEquals(st, 202, b)
    await(url, field(b, "jobId").get)

  test("the DOI closes; a similar title — even the published version of a preprint — is only proposed"):
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0, Stub).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val exact    = addItem(url, s"zzauto Kristiansen, The Zzauto Rise of Bronze Societies (Brill, 2021; DOI: ${Doi.toUpperCase}).")
        val similar  = addItem(url, "zzauto Kristiansen — Zzauto rise of bronze societies, the Brill book")
        val preprint = addItem(url, "zzauto Kristiansen, Zzauto Rise of Bronze Societies (published: 10.1093/zzauto/999)")
        val other    = addItem(url, "zzauto Burkert, Greek Religion")

        val job = ingest(url)
        assertEquals(field(job, "status"), Some("done"), job)
        val progress = field(job, "progress").getOrElse(fail(job))
        assert(progress.contains(s"ticked off acquisition item #$exact (same DOI)"), progress)
        assert(progress.contains(s"possible acquisition item #$similar"), progress)
        assert(progress.contains(s"possible acquisition item #$preprint") && progress.contains("another DOI, 10.1093/zzauto/999"), progress)
        assert(!progress.contains(s"#$other"), progress)

        assert(item(url, exact).contains("\"status\":\"done\""), item(url, exact))
        assert(item(url, exact).contains(s"ingested as [[$Ck]]"), item(url, exact))
        List(similar, preprint, other).foreach(i => assert(item(url, i).contains("\"status\":\"open\""), item(url, i)))

        // `jobs <id>` renders the same progress line
        assert(IngestMarkdown.job(io.circe.parser.decode[JobInfo](job).fold(e => fail(e.getMessage), identity))
          .contains(s"ticked off acquisition item #$exact"))

        // a re-OCR imports nothing new: a fresh item naming the DOI stays open
        val later = addItem(url, s"zzauto again, doi:$Doi")
        val (rs, rb) = post(s"$url/api/sources/$Ck/reocr")
        assertEquals(rs, 202, rb)
        val re = await(url, field(rb, "jobId").get)
        assertEquals(field(re, "status"), Some("done"), re)
        assert(!field(re, "progress").getOrElse("").contains("acquisition"), re)
        assert(item(url, later).contains("\"status\":\"open\""), item(url, later))
      finally b.stop()
    }

  test("an explicit acquireItem is the one ticked off; DOI matching is not run on top of it"):
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0, Stub).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val named  = addItem(url, "zzauto the item the import names")
        val byDoi  = addItem(url, s"zzauto the same DOI, $Doi")
        val job    = ingest(url, s"&acquireItem=$named")
        assert(field(job, "progress").exists(_.contains(s"ticked off acquisition item $named")), job)
        assert(item(url, named).contains("\"status\":\"done\""))
        assert(item(url, byDoi).contains("\"status\":\"open\""), item(url, byDoi))
      finally b.stop()
    }
