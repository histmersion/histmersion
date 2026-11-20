package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.plugins.calibre.{CalibreFixture, CalibrePlugin}
import com.histmersion.plugin as spi
import com.augustnagro.magnum.*
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.{InetSocketAddress, URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import com.histmersion.db.*
import com.histmersion.api.media.DataCredits
import com.histmersion.api.ocr.{ConvertedDoc, ConvertedImage}
import com.histmersion.config.DbConfig
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Spi
import com.histmersion.plugin.Registry
import com.histmersion.plugin.SourceFetcher
import com.histmersion.plugin.Access
import com.histmersion.plugin.WorkKey
import com.histmersion.plugin.Fetch
import com.histmersion.plugin.FetchedFile
import com.histmersion.plugin.Licence
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.util.storage.Blob
import com.histmersion.api.media.ImageSize
import com.histmersion.config.BackupsConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.AcquireConfig
import com.histmersion.config.InitConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.BasemapConfig
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.domain.library.acquisition.SttpHttp
import com.histmersion.pluginhost.PolicyHttp
import com.histmersion.domain.library.acquisition.HttpPlugin
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given

/** What acquisition, ingest and the collections answer, recorded on the code before they moved into the library's
  * domain. A server on its own scratch database, every upstream a fixture on a loopback port (never the network), the
  * acquisition chain the first-party fetcher plugins over it, a fixture converter, and a fixture Calibre library:
  *
  *   - `discovery.txt`: discover (a query, `notInDb`, a year and an order), meta by DOI, arXiv id and citekey,
  *     Wikipedia's references, a paper's deposited references (dry and applied), its follow-ups (dry and applied), the
  *     assessment (dry, applied, a retraction, a status kept and forced), the Retraction Watch mirror's refresh; the
  *     MCP `discover`, `refs`, `assess`, `followups` tools;
  *   - `ingest.txt`: ingest by DOI with an open-access copy (its job, the source written, its stored original, its
  *     references and assessment, its licence), again (the original kept) and with `refetch`, by arXiv id, with no copy
  *     (the drop-in, a browser download on the acquisition list), `legalOnly`, an unknown DOI, a file uploaded, the
  *     same bytes under another citekey (refused, then forced), the MCP `ingest` tool with a payload, a re-OCR, an
  *     upload ticking an acquisition item off; the MCP `ingest_doi` tool; then, on a second server whose config
  *     switches `acquire.allow-unauthorised` on with a fixture fetcher the config marks unauthorised: a person's ingest
  *     (asked), `legalOnly` (not asked) and the re-check (not asked);
  *   - `acquire.txt`: the acquisition list (add, done, rm, the listing with its checks, export), the re-check (a dry
  *     run, a pass with its job lines), a goal's candidates (a dry sweep, a sweep, the list, a mark, the counts, a
  *     second sweep with CORE's budget spent), the upstream quotas `about` shows; the MCP `candidates` tool;
  *   - `collections.txt`: the Calibre library over a fixture copy (the collections, its info, gaps, audit, a push, a
  *     sync dry and applied, the acquisition surface's gaps, meta and pushed) and archive.org (search, info, a
  *     lending-only item refused, an import); the MCP `collection` and `archive` tools;
  *   - `licence.txt`: the sources' licences as `query` reads them, the figures' licence fill, a source's export, and
  *     the datasets' credits the web page shows;
  *   - each part's rows: the sources, edges, assessments, the acquisition list and its checks, candidates and watches,
  *     the upstream quota, the jobs; and every request the upstreams received, in order.
  *
  * Uids are `UUID`, timestamps `TS`, days `DAY`, the fixture's port `PORT`, scratch directories `TMP`.
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites the files. Skipped without HISTMERSION_TEST_DB_URL.
  */
class AcquisitionOracleSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(15, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/acquisition")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-acquisition-oracle").toRealPath()
  private lazy val base = AppConfig.load()
  private lazy val scratch = ScratchDatabases("histmersion_s10p", base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  // ── the upstreams: one loopback server, a fixture per path ─────────────────────────────────

  /** Every upstream on one loopback port, each under its own path; what was asked is kept in order. */
  final private class Upstreams:
    val seen = ConcurrentLinkedQueue[String]()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val port: Int = server.getAddress.getPort
    val url: String = s"http://127.0.0.1:$port"
    server.createContext(
      "/",
      (ex: HttpExchange) =>
        val uri = ex.getRequestURI
        val target = uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
        seen.add(s"${ex.getRequestMethod} $target")
        val (code, kind, body) =
          try Fixtures.answer(uri.getPath, Option(uri.getQuery).getOrElse(""))
          catch case e: Exception => (500, "text/plain", s"fixture failed: $e".getBytes(UTF_8))
        ex.getResponseHeaders.add("content-type", kind)
        ex.sendResponseHeaders(code, if body.isEmpty then -1 else body.length.toLong)
        if body.nonEmpty then ex.getResponseBody.write(body)
        ex.close()
    )
    server.setExecutor(null)
    server.start()
    def take(): List[String] =
      val out = seen.asScala.toList
      seen.clear()
      out
    def stop(): Unit = server.stop(0)

  private object Fixtures:
    private def js(s: String): (Int, String, Array[Byte]) = (200, "application/json", s.getBytes(UTF_8))
    private val missing: (Int, String, Array[Byte]) = (404, "text/plain", "not found".getBytes(UTF_8))

    def pdf(name: String): Array[Byte] = s"%PDF-1.4\n% zq fixture $name\n${"z" * 12288}\n%%EOF\n".getBytes(UTF_8)

    /** A synthetic OpenAlex work. */
    def work(
        doi: String,
        title: String,
        year: Int,
        authors: List[String],
        cited: Int,
        oa: Boolean = false,
        oaStatus: String = "closed",
        oaUrl: Option[String] = None,
        licence: Option[String] = None,
        retracted: Boolean = false,
        refs: List[String] = Nil,
        landing: Option[String] = None
    ): Json =
      Json.obj(
        "id" -> s"https://openalex.org/W${doi.hashCode.abs}".asJson,
        "doi" -> s"https://doi.org/$doi".asJson,
        "display_name" -> title.asJson,
        "publication_year" -> year.asJson,
        "authorships" -> authors.map(a => Json.obj("author" -> Json.obj("display_name" -> a.asJson))).asJson,
        "open_access" -> Json.obj("is_oa" -> oa.asJson, "oa_status" -> oaStatus.asJson, "oa_url" -> oaUrl.asJson),
        "primary_location" -> Json.obj(
          "source" -> Json.obj("display_name" -> "Journal of Zq Studies".asJson),
          "landing_page_url" -> landing.getOrElse(s"https://doi.org/$doi").asJson
        ),
        "best_oa_location" -> (if oa then Json.obj("license" -> licence.asJson, "landing_page_url" -> landing.asJson)
                               else Json.Null),
        "cited_by_count" -> cited.asJson,
        "is_retracted" -> retracted.asJson,
        "type" -> "article".asJson,
        "abstract_inverted_index" -> Json.obj("Zq" -> List(0).asJson, "abstract." -> List(1).asJson),
        "referenced_works" -> refs.map(r => s"https://openalex.org/$r").asJson
      )

    /** The works the fixture knows, by DOI. */
    lazy val works: Map[String, Json] = Map(
      "10.5555/zq.open" -> work(
        "10.5555/zq.open",
        "Tin roads of Zqland",
        2019,
        List("Ann Zqauthor", "Bo Zqwriter"),
        120,
        oa = true,
        oaStatus = "green",
        licence = Some("cc-by")
      ),
      "10.5555/zq.cited" -> work("10.5555/zq.cited", "The harbour at Zqcove", 2001, List("Bo Zqwriter"), 40),
      "10.5555/zq.ref1" -> work("10.5555/zq.ref1", "Copper of Zqisles", 1995, List("Cy Zqmaker"), 3),
      "10.5555/zq.closed" -> work("10.5555/zq.closed", "A closed study of Zqtell", 2021, List("Di Zqreader"), 5),
      "10.5555/zq.retracted" -> work(
        "10.5555/zq.retracted",
        "A retracted claim about Zqtell",
        2012,
        List("Ed Zqfalse"),
        80,
        retracted = true
      ),
      "10.5555/zq.gold" -> work(
        "10.5555/zq.gold",
        "Gold open access on a refusing host",
        2022,
        List("Fay Zqgold"),
        9,
        oa = true,
        oaStatus = "gold",
        licence = Some("cc-by-4.0"),
        landing = Some("https://refusing.zq.example/article/zq.gold")
      ),
      "10.5555/zq.later" -> work("10.5555/zq.later", "A later reply on Zqtell", 2024, List("Gus Zqlater"), 1),
      "10.5555/zq.open2" -> work(
        "10.5555/zq.open2",
        "Bronze of Zqcove",
        2018,
        List("Lu Zqsecond"),
        12,
        oa = true,
        oaStatus = "green",
        licence = Some("CC BY-SA 4.0")
      ),
      "10.5555/zq.licensed" -> work("10.5555/zq.licensed", "A licensed Zq survey", 2020, List("Qi Zqlicence"), 7),
      "10.5555/zq.fresh1" -> work("10.5555/zq.fresh1", "New finds at Zqtell", 2025, List("Mo Zqnew", "Ni Zqnew"), 0),
      "10.5555/zq.fresh2" -> work("10.5555/zq.fresh2", "Tin isotopes of Zqland", 2025, List("Ox Zqiso"), 2),
      "10.48550/arxiv.2101.00001" -> work(
        "10.48550/arxiv.2101.00001",
        "A preprint on Zq metallurgy",
        2021,
        List("Hal Zqarx"),
        2
      )
    )

    private def openalex(path: String, query: String): (Int, String, Array[Byte]) =
      val prefix = "/openalex/works/https://doi.org/"
      if path.startsWith(prefix) then works.get(path.stripPrefix(prefix).toLowerCase).fold(missing)(w => js(w.noSpaces))
      else if query.contains("cites:") then js(Json.obj("results" -> List(works("10.5555/zq.later")).asJson).noSpaces)
      else if query.contains("search=") then
        js(
          Json
            .obj(
              "results" -> List("zq.open", "zq.cited", "zq.later", "zq.fresh1", "zq.fresh2")
                .map(d => works(s"10.5555/$d"))
                .asJson
            )
            .noSpaces
        )
      else js("""{"results":[]}""")

    private def crossref(path: String): (Int, String, Array[Byte]) =
      if path == "/crossref/works" then
        js(
          """{"message":{"items":[
            |{"DOI":"10.5555/zq.fresh2","title":["Tin isotopes of Zqland"],"author":[{"given":"Ox","family":"Zqiso"}],
            | "issued":{"date-parts":[[2025]]},"type":"journal-article"},
            |{"DOI":"10.5555/zq.fresh3","title":["Zq slag heaps"],"author":[{"given":"Pi","family":"Zqslag"}],
            | "issued":{"date-parts":[[2025]]},"type":"journal-article","container-title":["Zq Notes"]}]}}""".stripMargin
        )
      else crossrefWork(path)

    private def crossrefWork(path: String): (Int, String, Array[Byte]) =
      path.stripPrefix("/crossref/works/").toLowerCase match
        case "10.5555/zq.open" =>
          js(
            """{"message":{"DOI":"10.5555/zq.open","title":["Tin roads of Zqland"],"reference":[
              |{"DOI":"10.5555/zq.ref1","article-title":"Copper of Zqisles","year":"1995"},
              |{"article-title":"The harbour at Zqcove","author":"Zqwriter","year":"2001"},
              |{"unstructured":"An unknown Zq work, 1904."}]}}""".stripMargin
          )
        case "10.5555/zq.retracted" =>
          js(
            """{"message":{"DOI":"10.5555/zq.retracted","title":["A retracted claim about Zqtell"],
              |"update-to":[{"type":"retraction","label":"Retraction","DOI":"10.5555/zq.retraction-notice"}]}}""".stripMargin
          )
        case d if works.contains(d) => js(s"""{"message":{"DOI":"$d","title":["$d"]}}""")
        case _                      => missing

    private def unpaywall(path: String, port: Int): (Int, String, Array[Byte]) =
      path.stripPrefix("/unpaywall/").toLowerCase match
        case "10.5555/zq.open" =>
          js(
            s"""{"doi":"10.5555/zq.open","is_oa":true,"oa_status":"green",
               |"best_oa_location":{"url_for_pdf":"http://127.0.0.1:$port/files/zq-open.pdf","license":"cc-by"}}""".stripMargin
          )
        case "10.5555/zq.open2" =>
          js(
            s"""{"doi":"10.5555/zq.open2","is_oa":true,"oa_status":"green",
               |"best_oa_location":{"url_for_pdf":"http://127.0.0.1:$port/files/zq-open2.pdf","license":"CC BY-SA 4.0"}}""".stripMargin
          )
        case _ => js("""{"is_oa":false,"oa_status":"closed","best_oa_location":null}""")

    private val s2 =
      """{"offset":0,"data":[
        |{"intents":["background"],"contexts":["Zq tin roads were wrong, as we show."],"isInfluential":true,
        | "citingPaper":{"title":"A later reply on Zqtell","year":2024,"externalIds":{"DOI":"10.5555/zq.later"},"citationCount":1}},
        |{"intents":[],"contexts":[],"isInfluential":false,
        | "citingPaper":{"title":"A study not held","year":2023,"externalIds":{"DOI":"10.5555/zq.elsewhere"},"citationCount":0}}
        |]}""".stripMargin

    private val wiki =
      """{"parse":{"title":"Zqland","wikitext":{"*":"Zqland {{cite journal |doi=10.5555/zq.cited}} and {{cite journal |doi=10.5555/zq.unheld}} arXiv:2101.00001"}}}"""

    private val retractionCsv =
      """Record ID,Title,RetractionDate,OriginalPaperDOI,RetractionNature,Reason
        |1,"A retracted claim about Zqtell",3/4/2020 0:00,10.5555/zq.retracted,Retraction,"+Concerns/Issues About Data"
        |2,"Another",1/2/2019 0:00,10.5555/zq.other,Correction,"+Error in Text"
        |""".stripMargin

    private val archiveSearch =
      """{"response":{"numFound":2,"docs":[{"identifier":"zqbook"},{"identifier":"zqlent"}]}}"""

    private def archive(path: String): (Int, String, Array[Byte]) = path match
      case p if p.startsWith("/archive/advancedsearch.php") => js(archiveSearch)
      case "/archive/metadata/zqbook"                       =>
        js(
          """{"metadata":{"identifier":"zqbook","title":"A Zq book","creator":["Ivy Zqbook"],"date":"1911",
            |"publisher":"Zq Press","description":"About Zqland."},
            |"files":[{"name":"zqbook.pdf","format":"Text PDF","size":"40000"}]}""".stripMargin
        )
      case "/archive/metadata/zqlent" =>
        js(
          """{"metadata":{"identifier":"zqlent","title":"Lent Zq","access-restricted-item":"true"},
            |"files":[{"name":"zqlent.pdf","format":"Text PDF","size":"40000"}]}""".stripMargin
        )
      case "/archive/download/zqbook/zqbook.pdf" => (200, "application/pdf", pdf("zqbook"))
      case _                                     => missing

    /** arXiv's Atom answer with no entry: the candidates' query finds nothing new there. */
    private val emptyFeed =
      """<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom"><title>arXiv Query</title></feed>"""

    var port: Int = 0 // set once the upstreams listen: the fixture names its own files by URL

    def answer(path: String, query: String): (Int, String, Array[Byte]) =
      path match
        case p if p.startsWith("/openalex/")  => openalex(p, query)
        case p if p.startsWith("/crossref/")  => crossref(p)
        case p if p.startsWith("/unpaywall/") => unpaywall(p, port)
        case p if p.startsWith("/s2/")        => js(s2)
        case p if p.startsWith("/wikipedia")  => js(wiki)
        case "/rw/retractions.csv"            => (200, "text/csv", retractionCsv.getBytes(UTF_8))
        case p if p.startsWith("/archive/")   => archive(p)
        case p if p.startsWith("/files/")     => (200, "application/pdf", pdf(p.stripPrefix("/files/")))
        case p if p.startsWith("/arxiv/pdf/") => (200, "application/pdf", pdf("arxiv-" + p.stripPrefix("/arxiv/pdf/")))
        case "/arxiv-api/query"               => (200, "application/atom+xml", emptyFeed.getBytes(UTF_8))
        case p if p.startsWith("/europepmc/") => js("""{"resultList":{"result":[]}}""")
        case p if p.startsWith("/core/")      => js("""{"totalHits":0,"results":[]}""")
        case _                                => missing

  // ── a fetcher the config marks unauthorised (a fixture: it only counts and misses) ─────────

  private val unauthorisedAsked = java.util.concurrent.atomic.AtomicInteger(0)

  final private class ZqUnauthorisedPlugin extends Plugin:
    type Config = ZqOracleConfig
    val info =
      Plugin.Info(Plugin.Info.Id("zq-unauthorised"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-none.conf")
    def register(c: ZqOracleConfig, ctx: Plugin.Context) =
      ctx.sourceFetchers
        .add(
          Registry.AddId("zq-unauthorised"),
          new SourceFetcher:
            val access = Access.Unauthorised
            def fetch(w: WorkKey, c: Fetch.Context) =
              unauthorisedAsked.incrementAndGet()
              Fetch.Miss(Problem.Unclassified(Legacy.Text("the zq fixture has no copy")))
        )

  /** An open fetcher a plugin adds: it has one work, and states that copy's licence. */
  final private class ZqShelfPlugin extends Plugin:
    type Config = ZqOracleConfig
    val info =
      Plugin.Info(Plugin.Info.Id("zq-shelf"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-none.conf")
    def register(c: ZqOracleConfig, ctx: Plugin.Context) =
      ctx.sourceFetchers
        .add(
          Registry.AddId("zq-shelf"),
          new SourceFetcher:
            val access = Access.Open
            def fetch(w: WorkKey, c: Fetch.Context) =
              if w.doi.map(_.value).contains("10.5555/zq.licensed") then
                Fetch.Found(
                  FetchedFile(
                    FetchedFile.Source("zq-shelf"),
                    Some(FetchedFile.Url("https://shelf.zq.example/licensed.pdf")),
                    FetchedFile.Bytes(Fixtures.pdf("licensed")),
                    FetchedFile.Extension("pdf"),
                    Some(Licence("CC BY 4.0"))
                  )
                )
              else Fetch.Miss(Problem.Unclassified(Legacy.Text("the zq shelf has only one work")))
        )

  // ── the converter: every document converts, deterministically ──────────────────────────────

  /** A converter that never leaves the process: a body naming the file and its size, two pages, one figure. */
  final private class FixtureConverter extends ConverterClient:
    private val Png =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )
    def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
      Right(
        ConvertedDoc(
          ConvertedDoc.Markdown(
            s"# Converted $filename\n\nA body of ${file.size} bytes (quality: $quality).\n\n![fig](assets/zq-fig.png)\n"
          ),
          pages = ConvertedDoc.Pages(2),
          images = List(
            ConvertedImage(
              ConvertedImage.Name("zq-fig.png"),
              ConvertedImage.Mime("image/png"),
              ConvertedImage.Base64(Png),
              Some(ImageSize.Width(1)),
              Some(ImageSize.Height(1))
            )
          )
        )
      )

  // ── the server ─────────────────────────────────────────────────────────────────────────────

  private def userText(up: Upstreams, lib: CalibreFixture, allowUnauthorised: Boolean): String =
    val u = up.url
    s"""histmersion.acquire {
       |  contact-email = "oracle@example.org"
       |  openalex.url = "$u/openalex"
       |  crossref.url = "$u/crossref"
       |  unpaywall.url = "$u/unpaywall"
       |  core.url = "$u/core"
       |  core.api-key = "zq-core-key"
       |  core.daily-tokens = 6
       |  core.search-daily-tokens = 2
       |  core.max-wait-seconds = 0
       |  semantic-scholar.url = "$u/s2"
       |  europepmc.url = "$u/europepmc"
       |  pmc-dataset.url = "$u/pmcds"
       |  zenodo.url = "$u/zenodo"
       |  datacite.url = "$u/datacite"
       |  doi-handles.url = "$u/handles"
       |  arxiv.url = "$u/arxiv"
       |  candidates.arxiv-api-url = "$u/arxiv-api/query"
       |  archive-org.url = "$u/archive"
       |  wikipedia.url = "$u/wikipedia"
       |  retraction-watch.csv-url = "$u/rw/retractions.csv"
       |  landing.refusing-hosts = ["refusing.zq.example"]
       |  allow-unauthorised = $allowUnauthorised
       |}
       |histmersion.plugins.zq-unauthorised.enabled = true
       |histmersion.plugins.zq-shelf.enabled = true
       |histmersion.plugins.calibre {
       |  library-path = "${lib.dir}"
       |  calibredb = "${lib.calibredb}"
       |  sqlite = "${lib.sqlite3}"
       |  gui-process = "/no/such/calibre-gui-process"
       |}
       |""".stripMargin

  /** The acquisition settings whose URL is not the fixture's: every upstream is a loopback fixture, never the network
    * (a URL left at its default reached the real arXiv, whose 429s made a sweep slow and its watch's error vary).
    */
  private def offFixture(merged: org.ekrich.config.Config, fixture: String): List[String] =
    merged
      .getConfig("histmersion.acquire")
      .entrySet
      .asScala
      .toList
      .flatMap { e =>
        e.getValue.unwrapped match
          case url: String if url.matches("(?i)https?://.*") && !url.startsWith(fixture) => List(s"${e.getKey} = $url")
          case _                                                                         => Nil
      }
      .sorted

  test("an acquisition URL off the fixture is found"):
    val merged = ConfigFactory.parseString(
      """histmersion.acquire {
        |  openalex.url = "http://127.0.0.1:4711/openalex"
        |  candidates.arxiv-api-url = "https://export.arxiv.org/api/query"
        |  contact-email = "oracle@example.org"
        |}""".stripMargin
    )
    assertEquals(
      offFixture(merged, "http://127.0.0.1:4711"),
      List("candidates.arxiv-api-url = https://export.arxiv.org/api/query")
    )

  private def withServer[A](name: String, up: Upstreams, lib: CalibreFixture, allowUnauthorised: Boolean = false)(
      body: String => A
  ): A =
    CalibreFixture.refuseRealLibrary(lib.dir)
    val loaded = AppConfig
      .loadWithUserText(ConfigFactory.parseMap(Map.empty[String, String].asJava), userText(up, lib, allowUnauthorised))
      .fold(e => fail(e.sentences.mkString("; ")), identity)
    val off = offFixture(loaded.merged, up.url)
    assert(off.isEmpty, s"upstreams outside the loopback fixture (the network): ${off.mkString(", ")}")
    val c = loaded.config
    val cfg = c
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(scratch.urlOf(s"histmersion_s10p_${name}_test")))
      .modify(_.backups)
      .setTo(c.backups.modify(_.dir).setTo(BackupsConfig.Dir(tmp.resolve(s"$name-backups").toString)))
      .modify(_.bundles)
      .setTo(c.bundles.modify(_.dir).setTo(BundlesConfig.Dir(tmp.resolve(s"$name-bundles").toString)))
      .modify(_.converter)
      .setTo(c.converter.modify(_.spoolDir).setTo(ConverterConfig.SpoolDir(tmp.resolve(s"$name-spool").toString)))
      .modify(_.acquire)
      .setTo(c.acquire.modify(_.spoolDir).setTo(AcquireConfig.SpoolDir(tmp.resolve(s"$name-acquire").toString)))
      .modify(_.init)
      .setTo(c.init.modify(_.auto).setTo(InitConfig.Auto(false)))
      .modify(_.runtime)
      .setTo(c.runtime.modify(_.dataDir).setTo(RuntimeConfig.DataDir(tmp.resolve(s"$name-data").toString)))
      .modify(_.basemap)
      .setTo(c.basemap.modify(_.dir).setTo(BasemapConfig.Dir(tmp.resolve(s"$name-basemap").toString)))
    val core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))
    val http = SttpHttp(
      cfg.acquire,
      tries = PolicyHttp.Tries(1),
      clock = () => java.time.Instant.now(java.time.Clock.systemUTC())
    )
    val plugins = ServerPlugins.fetchers(
      loaded,
      () => HttpPlugin(http),
      core,
      extra = List(ZqShelfPlugin(), ZqUnauthorisedPlugin(), CalibrePlugin())
    )
    supervised {
      val b = Composition
        .server(
          cfg,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          FixtureConverter(),
          plugins = plugins,
          core = Some(core)
        )
        .start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def enc(s: String) = URLEncoder.encode(s, UTF_8)

  private def raw(req: HttpRequest.Builder): HttpResponse[Array[Byte]] =
    http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())

  private def get(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  /** A file's answer: its status, length and digest. */
  private def fileOf(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    val sha = java.security.MessageDigest.getInstance("SHA-256").digest(r.body).map(b => f"${b & 0xff}%02x").mkString
    val disposition = r.headers().firstValue("content-disposition").orElse("-")
    s"${r.statusCode} ${r.body.length} bytes, sha256 ${sha.take(16)}, $disposition"

  private def send(method: String, url: String, body: String, contentType: String = "application/json"): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", contentType)
        .method(method, HttpRequest.BodyPublishers.ofString(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def post(url: String, body: String = "{}"): String = send("POST", url, body)

  private def postBytes(url: String, bytes: Array[Byte]): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def field(json: String, name: String): Option[String] =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))

  @scala.annotation.tailrec
  private def awaitJob(url: String, id: String, left: Int = 600): String =
    val j = get(s"$url/api/jobs/$id")
    if j.contains("\"done\"") || j.contains("\"failed\"") || j.contains("\"cancelled\"") || left == 0 then j
    else { Thread.sleep(100); awaitJob(url, id, left - 1) }

  /** Wait for every job the answer names, then record each. */
  private def jobsOf(url: String, answer: String): String =
    "\"jobId\"\\s*:\\s*\"([^\"]+)\"|job `?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})".r
      .findAllMatchIn(answer)
      .map(m => Option(m.group(1)).getOrElse(m.group(2)))
      .toList
      .distinct
      .map(id => s"job: ${awaitJob(url, id)}")
      .mkString("\n")

  /** An MCP tool's answer, its Markdown text. */
  private def tool(url: String, name: String, args: Json): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(s"$url/mcp"))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            Json
              .obj(
                "jsonrpc" -> 2.asJson,
                "id" -> 1.asJson,
                "method" -> "tools/call".asJson,
                "params" -> Json.obj("name" -> name.asJson, "arguments" -> args)
              )
              .noSpaces
          )
        )
    )
    val text = String(r.body, UTF_8)
    val payload = text.linesIterator.find(_.startsWith("data:")).map(_.stripPrefix("data:").trim).getOrElse(text)
    RawJson
      .parse(payload)
      .toOption
      .flatMap(_.cursor.downField("result").downField("content").downArray.downField("text").as[String].toOption)
      .getOrElse(s"${r.statusCode} $text")

  private def source(title: String, year: Int, authors: List[String], doi: Option[String], status: String): String =
    Json
      .obj(
        "title" -> title.asJson,
        "authors" -> authors.asJson,
        "year" -> year.asJson,
        "doi" -> doi.asJson,
        "status" -> status.asJson,
        "summary" -> s"A study: $title.".asJson
      )
      .noSpaces

  /** A recorder: each step's name, its answer, and the requests the upstreams received for it. */
  final private class Log(up: Upstreams):
    private val out = StringBuilder()
    def step(name: String)(answer: => String): String =
      val _ = up.take()
      val a = answer
      val asked = up.take()
      out ++= s"== $name\n$a\n"
      if asked.nonEmpty then out ++= asked.map("  > " + _).mkString("", "\n", "\n")
      a
    def note(text: String): Unit = out ++= text + "\n"
    def result: String = out.result()

  // ── the parts ──────────────────────────────────────────────────────────────────────────────

  private def seed(url: String, log: Log): Unit =
    val _ = log.step("put zqa_cited (holds 10.5555/zq.cited)")(
      send(
        "PUT",
        s"$url/api/sources/zqa_cited",
        source("The harbour at Zqcove", 2001, List("Bo Zqwriter"), Some("10.5555/zq.cited"), "current")
      )
    )
    val _ = log.step("put zqa_ref (holds 10.5555/zq.ref1)")(
      send(
        "PUT",
        s"$url/api/sources/zqa_ref",
        source("Copper of Zqisles", 1995, List("Cy Zqmaker"), Some("10.5555/zq.ref1"), "unassessed")
      )
    )
    val _ = log.step("put zqa_retracted (holds 10.5555/zq.retracted, contested)")(
      send(
        "PUT",
        s"$url/api/sources/zqa_retracted",
        source("A retracted claim about Zqtell", 2012, List("Ed Zqfalse"), Some("10.5555/zq.retracted"), "contested")
      )
    )
    val _ = log.step("put zqa_later (holds 10.5555/zq.later)")(
      send(
        "PUT",
        s"$url/api/sources/zqa_later",
        source("A later reply on Zqtell", 2024, List("Gus Zqlater"), Some("10.5555/zq.later"), "current")
      )
    )
    val _ = log.step("put zqa_nodoi (no DOI)")(
      send("PUT", s"$url/api/sources/zqa_nodoi", source("Undated notes", 1990, List("Jo Zqnotes"), None, "current"))
    )

  private def discovery(url: String, log: Log): Unit =
    def s(name: String)(a: => String): Unit = { val _ = log.step(name)(a) }
    s("discover tin")(get(s"$url/api/acquire/discover?q=${enc("zq tin")}"))
    s("discover tin --not-in-db --since 2010 --sort cited --limit 2")(
      get(s"$url/api/acquire/discover?q=${enc("zq tin")}&notInDb=true&since=2010&sort=cited&limit=2")
    )
    s("discover (blank)")(get(s"$url/api/acquire/discover?q=${enc("  ")}"))
    s("meta 10.5555/zq.open")(get(s"$url/api/acquire/meta?id=${enc("10.5555/zq.open")}"))
    s("meta https://doi.org/10.5555/ZQ.CITED")(
      get(s"$url/api/acquire/meta?id=${enc("https://doi.org/10.5555/ZQ.CITED")}")
    )
    s("meta arxiv:2101.00001")(get(s"$url/api/acquire/meta?id=${enc("arxiv:2101.00001")}"))
    s("meta zqa_cited")(get(s"$url/api/acquire/meta?id=zqa_cited"))
    s("meta zqa_nodoi")(get(s"$url/api/acquire/meta?id=zqa_nodoi"))
    s("meta zqa_missing")(get(s"$url/api/acquire/meta?id=zqa_missing"))
    s("meta 10.5555/zq.unknown")(get(s"$url/api/acquire/meta?id=${enc("10.5555/zq.unknown")}"))
    s("wikirefs")(get(s"$url/api/acquire/wikirefs?url=${enc("https://en.wikipedia.org/wiki/Zqland")}"))
    s("wikirefs (not an article)")(get(s"$url/api/acquire/wikirefs?url=${enc("https://en.wikipedia.org/")}"))
    s("refs 10.5555/zq.open (not held)")(post(s"$url/api/acquire/refs", """{"id":"10.5555/zq.open"}"""))
    s("refs zqa_nodoi")(post(s"$url/api/acquire/refs", """{"id":"zqa_nodoi"}"""))
    s("followups zqa_cited")(post(s"$url/api/acquire/followups", """{"id":"zqa_cited"}"""))
    s("followups zqa_cited --apply --since 2020 --limit 5")(
      post(s"$url/api/acquire/followups", """{"id":"zqa_cited","since":2020,"limit":5,"apply":true}""")
    )
    s("retractions (before)")(get(s"$url/api/acquire/retractions"))
    s("retractions update, and the mirror once refreshed") {
      val started = post(s"$url/api/acquire/retractions")
      s"$started\n${awaitRetractions(url)}"
    }
    s("assess zqa_ref")(post(s"$url/api/acquire/assess", """{"id":"zqa_ref"}"""))
    s("assess zqa_ref --apply")(post(s"$url/api/acquire/assess", """{"id":"zqa_ref","apply":true}"""))
    s("assess zqa_cited --apply (a status kept)")(
      post(s"$url/api/acquire/assess", """{"id":"zqa_cited","apply":true}""")
    )
    s("assess zqa_cited --apply --force-status")(
      post(s"$url/api/acquire/assess", """{"id":"zqa_cited","apply":true,"forceStatus":true}""")
    )
    s("assess zqa_retracted --apply (a retraction is always written)")(
      post(s"$url/api/acquire/assess", """{"id":"zqa_retracted","apply":true}""")
    )
    s("assess 10.5555/zq.closed (not held)")(post(s"$url/api/acquire/assess", """{"id":"10.5555/zq.closed"}"""))
    s("assess zqa_nodoi")(post(s"$url/api/acquire/assess", """{"id":"zqa_nodoi"}"""))
    s("assess (bad id)")(post(s"$url/api/acquire/assess", """{"id":"not an id!"}"""))
    s("mcp discover query")(tool(url, "discover", Json.obj("query" -> "zq tin".asJson, "notInDb" -> true.asJson)))
    s("mcp discover doi")(tool(url, "discover", Json.obj("doi" -> "10.5555/zq.cited".asJson)))
    s("mcp discover wikipediaUrl")(
      tool(url, "discover", Json.obj("wikipediaUrl" -> "https://en.wikipedia.org/wiki/Zqland".asJson))
    )
    s("mcp discover (nothing)")(tool(url, "discover", Json.obj()))
    s("mcp refs")(tool(url, "refs", Json.obj("id" -> "zqa_cited".asJson)))
    s("mcp assess")(tool(url, "assess", Json.obj("id" -> "zqa_later".asJson)))
    s("mcp assess retractionsStatus")(tool(url, "assess", Json.obj("retractionsStatus" -> true.asJson)))
    s("mcp assess (nothing)")(tool(url, "assess", Json.obj()))
    s("mcp followups")(tool(url, "followups", Json.obj("id" -> "zqa_cited".asJson)))

  @scala.annotation.tailrec
  private def awaitRetractions(url: String, left: Int = 300): String =
    val r = get(s"$url/api/acquire/retractions")
    if !r.contains("\"running\":true") || left == 0 then r
    else { Thread.sleep(100); awaitRetractions(url, left - 1) }

  private def ingest(url: String, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    def withJobs(name: String)(a: => String): Unit =
      val _ = s(name) {
        val answer = a
        val jobs = jobsOf(url, answer)
        if jobs.isEmpty then answer else s"$answer\n$jobs"
      }
    withJobs("ingest 10.5555/zq.open (an open copy)")(post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.open"}"""))
    val ck = "zqauthor2019_roads"
    s("the source written")(get(s"$url/api/sources/$ck"))
    s("its original")(fileOf(s"$url/api/sources/$ck/original"))
    s("its edges")(get(s"$url/api/neighbors/$ck"))
    withJobs("ingest 10.5555/zq.open again (the original kept)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.open"}""")
    )
    withJobs("ingest 10.5555/zq.open --refetch")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.open","refetch":true}""")
    )
    withJobs("ingest 10.5555/zq.licensed (a plugin's copy with its licence)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.licensed"}""")
    )
    withJobs("ingest 10.5555/zq.licensed --refetch (the licence kept)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.licensed","refetch":true}""")
    )
    withJobs("ingest arxiv:2101.00001")(post(s"$url/api/acquire/ingest", """{"id":"arxiv:2101.00001"}"""))
    withJobs("ingest 10.5555/zq.closed (no copy)")(post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.closed"}"""))
    withJobs("ingest 10.5555/zq.gold (a refusing host)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.gold"}""")
    )
    withJobs("ingest 10.5555/zq.closed --legal-only --citekey zqa_legal")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.closed","citekey":"zqa_legal","legalOnly":true}""")
    )
    withJobs("ingest 10.5555/zq.unknown")(post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.unknown"}"""))
    withJobs("ingest 10.5555/zq.unknown --citekey zqa_byhand")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.unknown","citekey":"zqa_byhand"}""")
    )
    withJobs("ingest zqa_ref (by citekey)")(post(s"$url/api/acquire/ingest", """{"id":"zqa_ref"}"""))
    withJobs("ingest (bad citekey)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.closed","citekey":"../escape"}""")
    )
    val upload = Fixtures.pdf("zq-upload")
    withJobs("upload zqa_upload")(
      postBytes(
        s"$url/api/ingest?citekey=zqa_upload&filename=zq-upload.pdf&title=${enc("An uploaded Zq study")}&authors=${enc("Kay Zqup")}&year=2020",
        upload
      )
    )
    s("upload the same bytes as zqa_dup")(postBytes(s"$url/api/ingest?citekey=zqa_dup&filename=zq-upload.pdf", upload))
    withJobs("upload the same bytes as zqa_dup --force")(
      postBytes(s"$url/api/ingest?citekey=zqa_dup&filename=zq-upload.pdf&force=true", upload)
    )
    s("upload (empty)")(postBytes(s"$url/api/ingest?citekey=zqa_empty", Array.emptyByteArray))
    s("upload (bad citekey)")(postBytes(s"$url/api/ingest?citekey=${enc("Bad Key")}", upload))
    val item = s("acquire add (an item an upload fulfils)")(
      post(s"$url/api/queue/acquire", """{"text":"Find the Zq ledger (no DOI)"}""")
    )
    val itemId = "\"id\"\\s*:\\s*(\\d+)".r.findFirstMatchIn(item).map(_.group(1)).getOrElse("0")
    withJobs("upload zqa_ledger --acquire-item")(
      postBytes(s"$url/api/ingest?citekey=zqa_ledger&filename=ledger.pdf&acquireItem=$itemId", Fixtures.pdf("ledger"))
    )
    s("upload with a done item")(
      postBytes(s"$url/api/ingest?citekey=zqa_ledger2&filename=ledger2.pdf&acquireItem=$itemId", Fixtures.pdf("l2"))
    )
    s("upload with no such item")(
      postBytes(s"$url/api/ingest?citekey=zqa_ledger3&filename=ledger3.pdf&acquireItem=99999", Fixtures.pdf("l3"))
    )
    withJobs("reocr zqa_upload --quality")(post(s"$url/api/sources/zqa_upload/reocr?quality=true", ""))
    s("reocr zqa_nodoi (no original)")(post(s"$url/api/sources/zqa_nodoi/reocr", ""))
    s("reocr zqa_missing")(post(s"$url/api/sources/zqa_missing/reocr", ""))
    s("original zqa_upload")(fileOf(s"$url/api/sources/zqa_upload/original"))
    s("original zqa_nodoi")(get(s"$url/api/sources/zqa_nodoi/original"))
    val payload = java.util.Base64.getEncoder.encodeToString(Fixtures.pdf("zq-mcp"))
    withJobs("mcp ingest base64")(
      tool(
        url,
        "ingest",
        Json.obj(
          "citekey" -> "zqa_mcp".asJson,
          "base64" -> payload.asJson,
          "filename" -> "zq-mcp.pdf".asJson,
          "title" -> "Sent by a model".asJson
        )
      )
    )
    s("mcp ingest (neither)")(tool(url, "ingest", Json.obj("citekey" -> "zqa_none".asJson)))
    s("mcp ingest path outside")(
      tool(url, "ingest", Json.obj("citekey" -> "zqa_path".asJson, "path" -> "/no/such/zq.pdf".asJson))
    )
    withJobs("mcp ingest_doi")(tool(url, "ingest_doi", Json.obj("id" -> "10.5555/zq.later".asJson)))
    withJobs("mcp ingest_doi --legalOnly")(
      tool(url, "ingest_doi", Json.obj("id" -> "10.5555/zq.closed".asJson, "legalOnly" -> true.asJson))
    )
    s("mcp ingest_doi legalOnly false (an agent asking for more: refused)")(
      tool(url, "ingest_doi", Json.obj("id" -> "10.5555/zq.closed".asJson, "legalOnly" -> false.asJson))
    )
    s("jobs")(get(s"$url/api/jobs?limit=50"))
    s("job (unknown)")(get(s"$url/api/jobs/00000000-0000-0000-0000-000000000000"))
    s("unauthorised fetcher asked")(s"${unauthorisedAsked.get} time(s)")

  private def loose(url: String, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    unauthorisedAsked.set(0)
    s("ingest 10.5555/zq.closed (the default: legal-only, not asked)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.closed"}""")
    )
    s("asked")(s"${unauthorisedAsked.get} time(s)")
    s("ingest 10.5555/zq.closed legalOnly false (a person's explicit choice: the unauthorised fetcher is asked)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.closed","legalOnly":false}""")
    )
    s("asked")(s"${unauthorisedAsked.get} time(s)")
    s("ingest 10.5555/zq.cited --legal-only (not asked)")(
      post(s"$url/api/acquire/ingest", """{"id":"10.5555/zq.cited","citekey":"zqa_strict","legalOnly":true}""")
    )
    s("asked")(s"${unauthorisedAsked.get} time(s)")
    s("acquire add (an item with a DOI)")(post(s"$url/api/queue/acquire", """{"text":"Get 10.5555/zq.ref1 please"}"""))
    val _ = s("recheck (the watcher's path: not asked)")(recheck(url))
    s("asked")(s"${unauthorisedAsked.get} time(s)")

  /** A re-check of every open item, and its job once it has run. */
  private def recheck(url: String): String =
    val r = post(s"$url/api/queue/acquire/recheck", """{"all":true}""")
    val job = RawJson.parse(r.drop(4)).toOption.flatMap(_.cursor.downField("job").downField("id").as[String].toOption)
    job.fold(r)(id => s"$r\njob: ${awaitJob(url, id)}")

  private def acquireList(url: String, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    def id(a: String) = "\"id\"\\s*:\\s*(\\d+)".r.findFirstMatchIn(a).map(_.group(1)).getOrElse("0")
    val a = id(s("acquire add (an open copy)")(post(s"$url/api/queue/acquire", """{"text":"Get 10.5555/zq.open2"}""")))
    s("acquire add (held already)")(post(s"$url/api/queue/acquire", """{"text":"Have we 10.5555/zq.open?"}"""))
    s("acquire add (no legal copy)")(post(s"$url/api/queue/acquire", """{"text":"Get 10.5555/zq.ref1"}"""))
    val b = id(
      s("acquire add (no copy)")(
        post(s"$url/api/queue/acquire", """{"text":"Wanted: doi:10.5555/zq.closed","topic":"zq"}""")
      )
    )
    val c = id(s("acquire add (to remove)")(post(s"$url/api/queue/acquire", """{"text":"Remove me (zq)"}""")))
    val d = id(s("acquire add (to close by hand)")(post(s"$url/api/queue/acquire", """{"text":"A Zq atlas (buy)"}""")))
    s("acquire add (blank)")(post(s"$url/api/queue/acquire", """{"text":"  "}"""))
    s("acquire done")(send("PATCH", s"$url/api/queue/items/$d", """{"status":"done","reason":"bought"}"""))
    s("acquire rm")(send("DELETE", s"$url/api/queue/items/$c", ""))
    s("acquire rm (again)")(send("DELETE", s"$url/api/queue/items/$c", ""))
    s("acquire list")(get(s"$url/api/queue/acquire"))
    s("recheck --dry-run")(post(s"$url/api/queue/acquire/recheck", """{"dryRun":true}"""))
    s("recheck --id (dry)")(post(s"$url/api/queue/acquire/recheck", s"""{"dryRun":true,"id":$b}"""))
    val _ = s("recheck")(recheck(url))
    s("recheck again (nothing due)")(post(s"$url/api/queue/acquire/recheck", """{"dryRun":true}"""))
    s("acquire list --all (with the checks)")(get(s"$url/api/queue/acquire?all=true"))
    s("acquire export")(get(s"$url/api/queue/acquire/export"))
    s("about: the re-check")(aboutPart(url, "acquireRecheck"))
    s("mcp queue acquire")(
      tool(url, "queue", Json.obj("action" -> "list".asJson, "list" -> "acquire".asJson, "all" -> true.asJson))
    )
    val _ = a

  private def aboutPart(url: String, name: String): String =
    val a = get(s"$url/api/about")
    RawJson
      .parse(a.drop(4))
      .toOption
      .flatMap(_.cursor.downField(name).focus)
      .fold(s"${a.take(3)} (no $name)")(j => s"200 ${j.noSpaces}")

  private def candidates(url: String, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    s("put goal zqa-goal")(send("PUT", s"$url/api/goals/zqa-goal", """{"title":"Zq tin trade","status":"active"}"""))
    s("link a query")(post(s"$url/api/goals/zqa-goal/links", """{"kind":"query","ref":"zq tin trade"}"""))
    s("link a source")(post(s"$url/api/goals/zqa-goal/links", """{"kind":"source","ref":"zqa_cited"}"""))
    s("candidates (none yet)")(get(s"$url/api/acquire/candidates?goal=zqa-goal"))
    s("sweep --dry-run")(post(s"$url/api/acquire/candidates/sweep", """{"goal":"zqa-goal","dryRun":true}"""))
    s("sweep")(post(s"$url/api/acquire/candidates/sweep", """{"goal":"zqa-goal"}"""))
    s("sweep (no such goal)")(post(s"$url/api/acquire/candidates/sweep", """{"goal":"zqa-none"}"""))
    val listed = s("candidates")(get(s"$url/api/acquire/candidates?goal=zqa-goal"))
    val ids = "\"id\"\\s*:\\s*(\\d+)".r.findAllMatchIn(listed).map(_.group(1)).toList
    s("candidates --limit 1 --offset 1")(get(s"$url/api/acquire/candidates?goal=zqa-goal&limit=1&offset=1"))
    s("mark seen")(
      post(s"$url/api/acquire/candidates/mark", s"""{"ids":[${ids.take(1).mkString(",")}],"state":"seen"}""")
    )
    s("mark (bad state)")(post(s"$url/api/acquire/candidates/mark", """{"ids":[1],"state":"maybe"}"""))
    s("candidates --state seen")(get(s"$url/api/acquire/candidates?state=seen"))
    s("candidates --state wrong")(get(s"$url/api/acquire/candidates?state=wrong"))
    s("sweep again (CORE's budget spent)")(post(s"$url/api/acquire/candidates/sweep", """{"goal":"zqa-goal"}"""))
    s("about: the upstream quotas")(aboutPart(url, "upstreams"))
    s("mcp candidates")(tool(url, "candidates", Json.obj("goal" -> "zqa-goal".asJson)))
    s("mcp candidates sweep dry")(
      tool(url, "candidates", Json.obj("goal" -> "zqa-goal".asJson, "sweep" -> true.asJson, "dryRun" -> true.asJson))
    )
    s("mcp candidates sweep (no goal)")(tool(url, "candidates", Json.obj("sweep" -> true.asJson)))
    s("mcp candidates mark")(
      tool(url, "candidates", Json.obj("mark" -> ids.drop(1).take(1).map(_.toInt).asJson, "as" -> "dismissed".asJson))
    )

  private def collections(url: String, lib: CalibreFixture, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    def calls(): Unit =
      val c = lib.takeLog()
      if c.nonEmpty then log.note(c.map("  $ " + _).mkString("\n"))
    s("collections")(get(s"$url/api/collections"))
    s("calibre")(get(s"$url/api/collections/calibre"))
    calls()
    s("calibre search Bronze")(get(s"$url/api/collections/calibre/search?q=Bronze"))
    calls()
    s("calibre gaps")(get(s"$url/api/collections/calibre/gaps"))
    calls()
    s("calibre audit")(get(s"$url/api/collections/calibre/audit"))
    calls()
    s("calibre push zqa_upload")(post(s"$url/api/collections/calibre/push", """{"citekey":"zqa_upload"}"""))
    calls()
    s("calibre push zqa_nodoi (no original)")(post(s"$url/api/collections/calibre/push", """{"citekey":"zqa_nodoi"}"""))
    calls()
    s("calibre sync (dry)")(post(s"$url/api/collections/calibre/sync", """{"limit":3}"""))
    calls()
    s("calibre sync --apply --limit 2")(post(s"$url/api/collections/calibre/sync", """{"apply":true,"limit":2}"""))
    calls()
    s("acquire calibre gaps --limit 3")(get(s"$url/api/acquire/calibre/gaps?limit=3"))
    s("acquire calibre meta zqa_cited")(get(s"$url/api/acquire/calibre/meta?citekey=zqa_cited"))
    s("acquire calibre meta (bad)")(get(s"$url/api/acquire/calibre/meta?citekey=${enc("No Key")}"))
    s("acquire calibre pushed zqa_cited")(
      post(s"$url/api/acquire/calibre/pushed", """{"citekey":"zqa_cited","calibreId":"77"}""")
    )
    s("acquire calibre pushed zqa_missing")(
      post(s"$url/api/acquire/calibre/pushed", """{"citekey":"zqa_missing","calibreId":"78"}""")
    )
    s("archive search zq")(get(s"$url/api/acquire/archive/search?q=zq"))
    s("archive info zqbook")(get(s"$url/api/acquire/archive/info?id=zqbook"))
    s("archive info zqlent")(get(s"$url/api/acquire/archive/info?id=zqlent"))
    s("archive import zqlent (lending only)")(post(s"$url/api/acquire/archive/import", """{"id":"zqlent"}"""))
    val _ = s("archive import zqbook") {
      val imported = post(s"$url/api/acquire/archive/import", """{"id":"zqbook"}""")
      val jobs = jobsOf(url, imported)
      if jobs.isEmpty then imported else s"$imported\n$jobs"
    }
    s("mcp archive search")(tool(url, "archive", Json.obj("query" -> "zq".asJson)))
    s("mcp archive id")(tool(url, "archive", Json.obj("id" -> "zqlent".asJson)))
    s("mcp archive import lent")(tool(url, "archive", Json.obj("id" -> "zqlent".asJson, "import" -> true.asJson)))
    s("mcp collection list")(tool(url, "collection", Json.obj("action" -> "list".asJson)))
    calls()
    s("mcp collection gaps")(tool(url, "collection", Json.obj("action" -> "gaps".asJson, "name" -> "calibre".asJson)))
    calls()

  private def licence(url: String, log: Log): Unit =
    def s(name: String)(a: => String): String = log.step(name)(a)
    s("query licences")(
      post(s"$url/api/query", """{"sql":"SELECT citekey, license FROM sources ORDER BY citekey"}""")
    )
    s("fill-licences (dry)")(post(s"$url/api/media/fill-licences", ""))
    s("export zqlicence2020_licensed")(get(s"$url/api/sources/zqlicence2020_licensed/export"))
    s("fill-licences --apply")(post(s"$url/api/media/fill-licences?apply=true", ""))
    s("export zqauthor2019_roads")(get(s"$url/api/sources/zqauthor2019_roads/export"))
    s("mcp export_source")(tool(url, "export_source", Json.obj("citekey" -> "zqauthor2019_roads".asJson)))
    s("the datasets' credits")(
      DataCredits.all
        .map(c =>
          List(c.name, c.usedFor, c.licence, c.licenceUrl.getOrElse("-"), c.credit, c.url, c.onMap).mkString(" | ")
        )
        .mkString("\n")
    )

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r

  /** A day anywhere, inside a query string too (`%5B2025-11-01+TO`): the sweeps' look-back starts from today. */
  private val DayRe = "(?<![0-9])\\d{4}-\\d{2}-\\d{2}(?![0-9])".r
  private val DurationRe = "(\"(?:durationMs|elapsedMs|tookMillis|ageSeconds|tookMs)\" ?: ?)\\d+".r
  private val MsRe = "\\b\\d+ ms\\b".r

  private def normalise(text: String, port: Int): String =
    val t1 = text.replace(tmp.toString, "TMP").replace(s"127.0.0.1:$port", "127.0.0.1:PORT")
    val t2 = "127\\.0\\.0\\.1:\\d{4,5}".r.replaceAllIn(UuidRe.replaceAllIn(t1, "UUID"), "127.0.0.1:SERVER")
    val t3 = DurationRe.replaceAllIn(TimeRe.replaceAllIn(t2, "TS"), "$1N")
    MsRe.replaceAllIn(DayRe.replaceAllIn(t3, "DAY"), "N ms")

  private def json(text: String, port: Int): String =
    RawJson.parse(text) match
      case Right(j) => normalise(j.sortedKeys.spaces2, port)
      case Left(_)  => normalise(text, port)

  /** A recorded answer: its status, then its body as sorted JSON when it is JSON. */
  private def answers(text: String, port: Int): String =
    text.linesIterator
      .map { l =>
        val m = "^(\\d{3}) (.*)$".r.findFirstMatchIn(l)
        m.fold(normalise(l, port))(m => s"${m.group(1)} ${json(m.group(2), port)}")
      }
      .mkString("", "\n", "\n")

  /** The tables acquisition, ingest and the collections write, every row, normalised. */
  private val Tables = List(
    "sources" -> ("citekey, title, authors, doi, year, venue, status, source, license, calibre_id, " +
      "sha256 IS NOT NULL AS has_original, length(coalesce(ocr_markdown, '')) AS body, cited_by_count, summary"),
    "edges" -> "*",
    "unresolved_edges" -> "*",
    "assessments" -> "citekey, retracted, notices, cited_by, influential, verdict",
    "retraction_watch" -> "*",
    "queue_items" -> "list, text, topic, status, reason",
    "acquire_checks" -> "generation IS NOT NULL AS generation, outcome, reason, checks",
    "source_candidates" -> "goal, doi, title, year, authors, venue, is_oa, arxiv_id, found_by, state",
    "candidate_watches" -> "goal, kind, subject, index_name, last_error, last_found",
    "upstream_quota" -> "upstream, lookup_tokens, search_tokens, blocked_why",
    "jobs" -> "kind, citekey, status, progress, error",
    "assets" -> "citekey, name, license"
  )

  private def rows(ds: DataSource, port: Int): String =
    Tables
      .map { (t, cols) =>
        val rs = connect(ds)(
          sql"""SELECT to_jsonb(x)::text FROM (SELECT ${SqlLiteral(cols)} FROM "${SqlLiteral(t)}") x"""
            .query[String]
            .run()
            .toList
        )
        val normed = rs.map(r => json(r, port).linesIterator.map(_.trim).mkString(" ")).sorted
        (s"## $t ${normed.size} rows" :: normed).mkString("\n")
      }
      .mkString("", "\n", "\n")

  private def compare(name: String, actual: String): Unit =
    val file = oracleDir.resolve(name)
    if update then
      Files.createDirectories(oracleDir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  test("acquisition, ingest, the collections and the sources' licences, as recorded"):
    val up = Upstreams()
    Fixtures.port = up.port
    val lib = CalibreFixture.create(tmp.resolve("calibre-master")).copyTo(tmp.resolve("calibre-library"))
    try
      val acq = scratch("acq")
      val (d, i, a, c, cl, l) = withServer("acq", up, lib) { url =>
        val parts = List.fill(6)(Log(up))
        seed(url, parts(0))
        discovery(url, parts(0))
        ingest(url, parts(1))
        acquireList(url, parts(2))
        candidates(url, parts(2))
        collections(url, lib, parts(3))
        licence(url, parts(4))
        (parts(0).result, parts(1).result, parts(2).result, parts(3).result, parts(4).result, rows(acq, up.port))
      }
      val lax = scratch("lax")
      val (lo, lr) = withServer("lax", up, lib, allowUnauthorised = true) { url =>
        val log = Log(up)
        seed(url, log)
        loose(url, log)
        (log.result, rows(lax, up.port))
      }
      compare("discovery.txt", answers(d, up.port))
      compare("ingest.txt", answers(i + "== the unauthorised switch on\n" + lo, up.port))
      compare("acquire.txt", answers(a, up.port))
      compare("collections.txt", answers(c, up.port))
      compare("licence.txt", answers(cl, up.port))
      compare("rows.txt", s"# the first server\n$l# the switch on\n$lr")
    finally up.stop()
