package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.plugin.{Fetch, FetchContext, PluginHttp, PluginLog, WorkKey}
import com.histmersion.server.acquire.*
import com.histmersion.server.acquire.fetchers.{ArchiveOrgFetcher, BuiltinFetchers}
import com.histmersion.server.plugins.{Generation, PluginHost, Plugins}
import io.circe.Json
import org.ekrich.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.*

/** Today's acquisition steps as built-in plugins (docs/research-plugins.md §5.3): registered through
  * the plugin host like any plugin, on by default in today's order, each one switched off by config. */
class BuiltinFetchersSuite extends munit.FunSuite:

  private val cfg = AppConfig.load().acquire

  /** Never asked: registering opens nothing. */
  private object Offline extends Http:
    def json(url: String, headers: Map[String, String]): Either[String, Json]         = fail(s"asked $url")
    def text(url: String, headers: Map[String, String]): Either[String, String]       = fail(s"asked $url")
    def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]] = fail(s"asked $url")

  private def generation(user: String = "", env: Map[String, String] = Map.empty): Generation =
    val plugins = BuiltinFetchers.plugins(cfg, () => Offline, () => fail("openalex asked"), () => fail("archive asked"))
    PluginHost.build(plugins, ConfigFactory.parseString(user), ConfigFactory.parseMap(env.asJava),
      Plugins.services(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
        ConfigFactory.parseMap(env.asJava)))

  private def chain(g: Generation): List[String] =
    FetchChain.of(g.registries.sourceFetchers, cfg.chainIds, cfg.allowUnauthorised)._1.plan.map(_._1)

  /** Today's order, which the default `acquire.chain` keeps. */
  private val Today = List("archive-org", "unpaywall", "core", "openalex", "pmc", "arxiv", "landing", "pmc-text")

  test("by default the four plugins register, without asking any upstream, and the chain is today's"):
    val g = generation()
    assertEquals(g.report.plugins.map(p => p.id -> p.state).sortBy(_._1),
      List("archive-org", "arxiv", "oa-index", "pmc").map(_ -> "registered"))
    assertEquals(g.report.plugins.find(_.id == "oa-index").map(_.provides),
      Some(List("unpaywall", "core", "openalex", "landing").map(id => s"source-fetcher:$id")))
    assertEquals(g.report.plugins.find(_.id == "pmc").map(_.provides),
      Some(List("source-fetcher:pmc", "source-fetcher:pmc-text")))
    assertEquals(chain(g), Today)
    assert(g.report.ok, g.report.toString)

  test("the shipped default chain names exactly the built-in fetchers, in today's order"):
    assertEquals(cfg.chainIds, Today)
    assertEquals(cfg.allowUnauthorised, false)

  test("a plugin switched off takes all its fetchers out of the chain; the rest keep their order"):
    val g = generation("histmersion.plugins.pmc.enabled = false")
    assertEquals(g.report.plugins.find(_.id == "pmc").map(_.state), Some("disabled"))
    assertEquals(chain(g), Today.filterNot(Set("pmc", "pmc-text")))

  test("the environment switches one off too (HISTMERSION_PLUGIN_<ID>_ENABLED)"):
    val g = generation(env = Map("HISTMERSION_PLUGIN_ARXIV_ENABLED" -> "false"))
    assertEquals(g.report.plugins.find(_.id == "arxiv").map(_.state), Some("disabled"))
    assertEquals(chain(g), Today.filterNot(_ == "arxiv"))

  test("a built-in plugin has no settings of its own: an unknown key fails it alone"):
    val g = generation("histmersion.plugins.arxiv { enabled = true, url = \"https://example.org\" }")
    assertEquals(g.report.plugins.find(_.id == "arxiv").map(_.state), Some("failed"))
    assertEquals(chain(g), Today.filterNot(_ == "arxiv"))

  test("a reordered chain is honoured, unlisted fetchers following in registration order"):
    val g = generation()
    val (c, notes) = FetchChain.of(g.registries.sourceFetchers, List("arxiv", "pmc"), allowUnauthorised = false)
    assertEquals(c.plan.map(_._1).take(2), List("arxiv", "pmc"))
    assertEquals(c.plan.map(_._1).toSet, Today.toSet)
    assertEquals(notes, Nil)

  // ── archive-org ─────────────────────────────────────────────────────────────────────────────

  private object Ctx extends FetchContext:
    def memo[A](key: String)(compute: => A): A = compute
    val log: PluginLog = new PluginLog { def info(m: String) = (); def warn(m: String) = () }

  private final class FakeArchive(item: ArchiveItem) extends ArchiveClient:
    var downloaded: List[String] = Nil // a test double's record of calls
    def search(q: String, rows: Int) = Right(List(item))
    def info(id: String) = Right(item)
    def download(id: String, file: String) =
      downloaded = downloaded :+ file
      Right(FetchedFile("archive_org", Some(s"https://archive.org/download/$id/$file"),
        ("%PDF-1.4\n" + "y" * 20000).getBytes("UTF-8"), file.split("\\.").last))

  private val files = List(ArchiveFile("x.pdf", Some("Text PDF"), 4.0), ArchiveFile("x.epub", Some("EPUB"), 1.0))

  test("archive-org: no archive.org identifier is a miss, and nothing is asked"):
    val a = FakeArchive(ArchiveItem("x", files = files))
    assert(ArchiveOrgFetcher(() => a).fetch(WorkKey(doi = Some("10.1/x")), Ctx).isInstanceOf[Fetch.Miss])
    assertEquals(a.downloaded, Nil)

  test("archive-org: a lending-only item is REFUSED and never downloaded"):
    val a = FakeArchive(ArchiveItem("x", lendingOnly = true, files = files))
    ArchiveOrgFetcher(() => a).fetch(WorkKey(archive = Some("x")), Ctx) match
      case Fetch.Refused(why) => assert(why.contains("lending-only"), why)
      case other              => fail(s"not refused: $other")
    assertEquals(a.downloaded, Nil)

  test("archive-org: the preferred format, else an EPUB first"):
    val a = FakeArchive(ArchiveItem("x", files = files))
    val f = ArchiveOrgFetcher(() => a)
    assert(f.fetch(WorkKey(archive = Some("x"), format = Some("pdf")), Ctx).isInstanceOf[Fetch.Found])
    assert(f.fetch(WorkKey(archive = Some("x")), Ctx).isInstanceOf[Fetch.Found])
    assertEquals(a.downloaded, List("x.pdf", "x.epub"))

  test("archive-org: an item with no epub/pdf file is a miss"):
    val a = FakeArchive(ArchiveItem("x", files = List(ArchiveFile("x.djvu", None, 1.0))))
    ArchiveOrgFetcher(() => a).fetch(WorkKey(archive = Some("x")), Ctx) match
      case Fetch.Miss(why) => assert(why.contains("no epub/pdf"), why)
      case other           => fail(s"not a miss: $other")
