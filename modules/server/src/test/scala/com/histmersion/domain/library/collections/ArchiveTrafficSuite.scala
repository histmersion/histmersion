package com.histmersion
package domain.library.collections

import hearth.kindlings.optics.syntax.*

import com.histmersion.server.ServerPlugins

import com.histmersion.config.{AcquireConfig, AppConfig, DbConfig, LoadedConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{FetchChain, Generation, HttpAnswer, HttpTransport, PolicyHttp}
import com.histmersion.server.Deps
import com.histmersion.domain.library.acquisition.*
import com.histmersion.api.library.WorkRef
import com.histmersion.domain.library.ArchiveId
import com.histmersion.domain.library.search.Search
import com.histmersion.util.plugins.Plugin
import scala.collection.immutable.ListMap
import io.circe.syntax.*
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.library.{ArchiveFile, ArchiveImportRequest, ArchiveItem, ArchiveSearchResult}
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.api.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.server.AcquisitionTesting
import com.histmersion.domain.library.acquisition.RetractionWatchClient
import com.histmersion.domain.Caller

/** The recorded traffic of `archive search|info|import`: every request the server sends to archive.org for them, on the
  * wire below the outbound policy (URL and every header, in order), and what each answers — the search hits, an item,
  * the lending-only refusal, an import's report and the stored record. The answers are fixtures; nothing reaches the
  * network. The transcript is `traffic/archive.txt`, compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites
  * it): moving archive.org behind the library's collections changes no request.
  */
class ArchiveTrafficSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Transcript = Path.of("modules/server/src/test/resources/traffic/archive.txt")

  private lazy val scratch =
    val b = AppConfig.load()
    ScratchDatabases("archive_traffic", b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  override protected def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private val ContentType = CoreQuota.HeaderName("content-type")

  private def json(s: String): Either[HttpTransport.Problem, HttpAnswer] =
    Right(HttpAnswer(HttpAnswer.Code(200), Map(ContentType -> CoreQuota.HeaderValue("application/json")), HttpAnswer.Body(s.getBytes(UTF_8))))
  private val notFound: Either[HttpTransport.Problem, HttpAnswer] =
    Right(HttpAnswer(HttpAnswer.Code(404), Map(ContentType -> CoreQuota.HeaderValue("text/plain")), HttpAnswer.Body("not found".getBytes(UTF_8))))

  final private class Wire(routes: PartialFunction[String, Either[HttpTransport.Problem, HttpAnswer]]) extends HttpTransport:
    val out = StringBuilder()
    def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[HttpTransport.Problem, HttpAnswer] =
      val answer = routes.applyOrElse(url.value, (_: String) => notFound)
      out ++= s"GET $url\n"
      headers.toList.sortBy(_._1.value.toLowerCase(java.util.Locale.ROOT)).foreach((k, v) => out ++= s"  $k: $v\n")
      out ++= s"  -> ${answer.fold(e => s"error $e", a => a.code.toString)}\n"
      answer

  private def routes(c: AcquireConfig): PartialFunction[String, Either[HttpTransport.Problem, HttpAnswer]] =
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
          HttpAnswer(HttpAnswer.Code(200), Map(ContentType -> CoreQuota.HeaderValue("application/epub+zip")), HttpAnswer.Body(("PK epub " + ("e" * 20480)).getBytes(UTF_8)))
        )
      case u if u.startsWith(s"$base/download/zqbook/zqbook.pdf") =>
        Right(HttpAnswer(HttpAnswer.Code(200), Map(ContentType -> CoreQuota.HeaderValue("application/pdf")), HttpAnswer.Body(("%PDF-1.4\n" + ("p" * 20480)).getBytes(UTF_8))))
    }

  private val Text =
    """histmersion.acquire.contact-email = "host@example.org"
      |""".stripMargin

  test("archive search, info and import send the same requests and answer the same"):
    val loaded: LoadedConfig =
      AppConfig
        .loadWithUserText(ConfigFactory.parseMap(Map.empty[String, String].asJava), Text)
        .fold(e => fail(e.value), identity)
    val ds = scratch("run")
    val spool = Files.createTempDirectory("histmersion-archive-spool-")
    val app = loaded.config.modify(
      _.db).setTo(loaded.config.db.modify(_.url).setTo(scratch.urlOf("archive_traffic_run_test"))).modify(_.acquire).setTo(loaded.config.acquire.modify(_.spoolDir).setTo(AcquireConfig.SpoolDir(spool.toString))
    )
    val wire = Wire(routes(app.acquire))
    val g: Generation = ServerPlugins.load(loaded, transport = _ => wire, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
    val surface = ArchiveTrafficSuite.surface(app, ds, g, wire)
    val sb = StringBuilder()
    def step(name: String)(answer: => String): Unit =
      wire.out.clear()
      val a = answer
      sb ++= s"## $name\n" + wire.out.result() + a + "\n\n"
    def show[A: io.circe.Encoder](r: Either[Api.Error, A]): String =
      r.fold(e => s"refused: ${e.message}", _.asJson.spaces2)
    step("archive search bronze")(show(surface.search(Search.Query("bronze"), None)))
    step("archive search bronze --rows 2")(show(surface.search(Search.Query("bronze"), Some(CollectionsService.Limit(2)))))
    step("archive search (blank)")(show(surface.search(Search.Query("  "), None)))
    step("archive info zqbook")(show(surface.info(ArchiveId("zqbook"))))
    step("archive info https://archive.org/details/zqbook/page/2")(
      show(surface.info(ArchiveId("https://archive.org/details/zqbook/page/2")))
    )
    step("archive info zqgone")(show(surface.info(ArchiveId("zqgone"))))
    step("archive import zqlent")(show(surface.importItem(ArchiveImportRequest(ArchiveImportRequest.Id("zqlent")))))
    step("archive import zqnofile")(show(surface.importItem(ArchiveImportRequest(ArchiveImportRequest.Id("zqnofile")))))
    step("archive import zqbook --format pdf --citekey zqarch_book")(
      show(surface.importItem(ArchiveImportRequest(ArchiveImportRequest.Id("zqbook"), Some(Citekey("zqarch_book")), Some(ArchiveImportRequest.Format("pdf")))))
    )
    step("archive import zqbook")(show(surface.importItem(ArchiveImportRequest(ArchiveImportRequest.Id("zqbook")))))
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
      search: (Search.Query, Option[CollectionsService.Limit]) => Either[Api.Error, ArchiveSearchResult],
      info: ArchiveId => Either[Api.Error, ArchiveItem],
      importItem: ArchiveImportRequest => Either[Api.Error, Ingest.Report]
  )

  def surface(app: AppConfig, ds: javax.sql.DataSource, g: Generation, wire: HttpTransport): Surface =
    val (chain, _) =
      FetchChain.of(
        ListMap.from(g.registries.sourceFetchers.toList.flatMap((k, f) => Plugin.Id.option(k.value).map(_ -> f))),
        app.acquire.chainIds.flatMap(c => Plugin.Id.option(c.value)),
        FetchChain.AllowUnauthorised(app.acquire.allowUnauthorised.value),
        plugins = g.registered.flatMap(i => Plugin.Id.option(i.value))
      )
    val none = new DiscoveryClient:
      def search(query: Search.Query, since: Option[Year], sort: Option[DiscoveryClient.SearchSort], limit: DiscoveryClient.SearchLimit) = Left(DiscoveryClient.Problem("not used"))
      def meta(doi: Doi) = Left(DiscoveryClient.Problem("not used"))
      def references(doi: Doi) = Left(DiscoveryClient.Problem("not used"))
      def updates(doi: Doi) = Left(DiscoveryClient.Problem("not used"))
    val deps = Deps(app, ds)
    val h = AcquisitionTesting.controllers(
      deps,
      none,
      new CitationsClient { def citing(w: WorkRef, s: Option[Year], l: CitationsClient.CitingLimit) = Left(CitationsClient.Problem("not used")) },
      chain,
      new WikiClient { def refs(url: WikiClient.ArticleUrl) = Left(WikiClient.Problem("not used")) },
      new RetractionWatchClient { def csv() = Left(RetractionWatchClient.Problem("not used")) },
      AcquisitionTesting.skeleton(deps)
    )
    val library = new RefusingCollectionLibrary:
      override def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest) =
        h.archiveImport(item, file, req)
    val service = CollectionsService.InMemory(
      java.util.concurrent.atomic.AtomicReference(Nil),
      library,
      () => g.registries.sourceCollections,
      CollectionsService.Limit(app.acquire.defaults.archiveRows.value)
    )
    Surface(
      service.archiveSearch,
      service.archiveInfo,
      req => service.archiveImport(Caller.Local, req)
    )
