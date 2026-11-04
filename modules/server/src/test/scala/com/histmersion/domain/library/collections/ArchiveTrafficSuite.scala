package com.histmersion.domain.library.collections

import hearth.kindlings.optics.syntax.*

import com.histmersion.server.ServerPlugins

import com.histmersion.config.{AcquireConfig, AppConfig, LoadedConfig}
import com.histmersion.pluginhost.{FetchChain, Generation, HttpAnswer, HttpTransport, PolicyHttp}
import com.histmersion.server.Deps
import com.histmersion.domain.library.acquisition.*
import com.histmersion.api.library.WorkRef
import io.circe.syntax.*
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.library.{ArchiveFile, ArchiveImportRequest, ArchiveItem, ArchiveSearchResult}
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest

/** The recorded traffic of `archive search|info|import`: every request the server sends to archive.org for them, on the
  * wire below the outbound policy (URL and every header, in order), and what each answers — the search hits, an item,
  * the lending-only refusal, an import's report and the stored record. The answers are fixtures; nothing reaches the
  * network. The transcript is `traffic/archive.txt`, compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites
  * it): moving archive.org behind the library's collections changes no request.
  */
class ArchiveTrafficSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Transcript = Path.of("modules/server/src/test/resources/traffic/archive.txt")

  private lazy val scratch =
    val b = AppConfig.load()
    ScratchDatabases("archive_traffic", b.db.modify(_.url).setTo(testDb.get))
  override protected def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def json(s: String): Either[String, HttpAnswer] =
    Right(HttpAnswer(200, Map("content-type" -> "application/json"), s.getBytes(UTF_8)))
  private val notFound: Either[String, HttpAnswer] =
    Right(HttpAnswer(404, Map("content-type" -> "text/plain"), "not found".getBytes(UTF_8)))

  final private class Wire(routes: PartialFunction[String, Either[String, HttpAnswer]]) extends HttpTransport:
    val out = StringBuilder()
    def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
      val answer = routes.applyOrElse(url, (_: String) => notFound)
      out ++= s"GET $url\n"
      headers.toList.sortBy(_._1.toLowerCase(java.util.Locale.ROOT)).foreach((k, v) => out ++= s"  $k: $v\n")
      out ++= s"  -> ${answer.fold(e => s"error $e", a => a.code.toString)}\n"
      answer

  private def routes(c: AcquireConfig): PartialFunction[String, Either[String, HttpAnswer]] =
    val base = c.archiveOrg.url
    {
      case u if u.startsWith(s"$base/advancedsearch.php") =>
        json(
          """{"response":{"numFound":3,"docs":[{"identifier":"zqbook"},{"identifier":"zqlent"},{"identifier":"zqgone"}]}}"""
        )
      case u if u.startsWith(s"$base/metadata/zqbook") =>
        json(
          """{"metadata":{"identifier":"zqbook","title":"A Book","creator":["An Author","Another"],"date":"1901",
            |"publisher":"A Press","description":"About a book."},
            |"files":[{"name":"zqbook.pdf","format":"Text PDF","size":"40000"},{"name":"zqbook.epub","format":"EPUB","size":"30000"}]}""".stripMargin
        )
      case u if u.startsWith(s"$base/metadata/zqlent") =>
        json(
          """{"metadata":{"identifier":"zqlent","title":"Lent","access-restricted-item":"true"},
            |"files":[{"name":"zqlent.pdf","format":"Text PDF","size":"40000"}]}""".stripMargin
        )
      case u if u.startsWith(s"$base/metadata/zqnofile") =>
        json(
          """{"metadata":{"identifier":"zqnofile","title":"No file"},"files":[{"name":"zqnofile.txt","format":"Text","size":"10"}]}"""
        )
      case u if u.startsWith(s"$base/download/zqbook/zqbook.epub") =>
        Right(
          HttpAnswer(200, Map("content-type" -> "application/epub+zip"), ("PK epub " + ("e" * 20480)).getBytes(UTF_8))
        )
      case u if u.startsWith(s"$base/download/zqbook/zqbook.pdf") =>
        Right(HttpAnswer(200, Map("content-type" -> "application/pdf"), ("%PDF-1.4\n" + ("p" * 20480)).getBytes(UTF_8)))
    }

  private val Text =
    """histmersion.acquire.contact-email = "host@example.org"
      |""".stripMargin

  test("archive search, info and import send the same requests and answer the same"):
    val loaded: LoadedConfig =
      AppConfig
        .loadWithUserText(ConfigFactory.parseMap(Map.empty[String, String].asJava), Text)
        .fold(e => fail(e), identity)
    val ds = scratch("run")
    val spool = Files.createTempDirectory("histmersion-archive-spool-")
    val app = loaded.config.modify(
      _.db).setTo(loaded.config.db.modify(_.url).setTo(scratch.urlOf("archive_traffic_run_test"))).modify(_.acquire).setTo(loaded.config.acquire.modify(_.spoolDir).setTo(spool.toString)
    )
    val wire = Wire(routes(app.acquire))
    val g: Generation = ServerPlugins.load(loaded, transport = _ => wire)
    val surface = ArchiveTrafficSuite.surface(app, ds, g, wire)
    val sb = StringBuilder()
    def step(name: String)(answer: => String): Unit =
      wire.out.clear()
      val a = answer
      sb ++= s"## $name\n" + wire.out.result() + a + "\n\n"
    def show[A: io.circe.Encoder](r: Either[Api.Error, A]): String =
      r.fold(e => s"refused: ${e.message}", _.asJson.spaces2)
    step("archive search bronze")(show(surface.search("bronze", None)))
    step("archive search bronze --rows 2")(show(surface.search("bronze", Some(2))))
    step("archive search (blank)")(show(surface.search("  ", None)))
    step("archive info zqbook")(show(surface.info("zqbook")))
    step("archive info https://archive.org/details/zqbook/page/2")(
      show(surface.info("https://archive.org/details/zqbook/page/2"))
    )
    step("archive info zqgone")(show(surface.info("zqgone")))
    step("archive import zqlent")(show(surface.importItem(ArchiveImportRequest("zqlent"))))
    step("archive import zqnofile")(show(surface.importItem(ArchiveImportRequest("zqnofile"))))
    step("archive import zqbook --format pdf --citekey zqarch_book")(
      show(surface.importItem(ArchiveImportRequest("zqbook", Some("zqarch_book"), Some("pdf"))))
    )
    step("archive import zqbook")(show(surface.importItem(ArchiveImportRequest("zqbook"))))
    sb ++= "## stored\n"
    com.augustnagro.magnum.connect(ds):
      import com.augustnagro.magnum.*
      sql"""SELECT citekey, title, coalesce(source, '-'), array_to_string(authors, '; '), coalesce(year::text, '-')
            FROM sources ORDER BY citekey"""
        .query[(String, String, String, String, String)]
        .run()
        .foreach(r => sb ++= r.toList.mkString(" | ") + "\n")
    val transcript = sb.result().replace(spool.toString, "<spool>")
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(Transcript.getParent)
      Files.writeString(Transcript, transcript, UTF_8): Unit
    else assertEquals(transcript, Files.readString(Transcript, UTF_8))

object ArchiveTrafficSuite:

  /** The archive.org operations as the server runs them, over the generation `g` (built on `wire`). */
  final case class Surface(
      search: (String, Option[Int]) => Either[Api.Error, ArchiveSearchResult],
      info: String => Either[Api.Error, ArchiveItem],
      importItem: ArchiveImportRequest => Either[Api.Error, Ingest.Report]
  )

  def surface(app: AppConfig, ds: javax.sql.DataSource, g: Generation, wire: HttpTransport): Surface =
    val (chain, _) =
      FetchChain.of(
        g.registries.sourceFetchers,
        app.acquire.chainIds,
        app.acquire.allowUnauthorised,
        plugins = g.registered
      )
    val none = new DiscoveryClient:
      def search(q: String, since: Option[Int], sort: Option[String], limit: Int) = Left("not used")
      def meta(doi: String) = Left("not used")
      def references(doi: String) = Left("not used")
      def updates(doi: String) = Left("not used")
    val deps = Deps(app, ds)
    val h = com.histmersion.server.AcquisitionTesting.controllers(
      deps,
      none,
      new CitationsClient { def citing(w: WorkRef, s: Option[Int], l: Int) = Left("not used") },
      chain,
      new WikiClient { def refs(url: String) = Left("not used") },
      new RetractionWatchClient { def csv() = Left("not used") },
      com.histmersion.server.AcquisitionTesting.skeleton(deps)
    )
    val library = new RefusingCollectionLibrary:
      override def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest) =
        h.archiveImport(item, file, req)
    val service = CollectionsService.InMemory(
      java.util.concurrent.atomic.AtomicReference(Nil),
      library,
      () => g.registries.sourceCollections,
      app.acquire.defaults.archiveRows.value
    )
    Surface(
      service.archiveSearch,
      service.archiveInfo,
      req => service.archiveImport(com.histmersion.domain.Caller.Local, req)
    )
