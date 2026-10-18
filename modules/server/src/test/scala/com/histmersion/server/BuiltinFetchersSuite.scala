package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.plugin.{Fetch, FetchContext, PluginHttp, PluginLog, WorkKey}
import com.histmersion.server.acquire.*
import com.histmersion.server.acquire.fetchers.{ArchiveOrgFetcher, BuiltinFetchers}
import com.histmersion.server.plugins.{Generation, LegacyPlugins, PluginHost, Plugins}
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

  private def generation(user: String = "", env: Map[String, String] = Map.empty, acquire: AcquireConfig = cfg,
      http: Http = Offline): Generation =
    val plugins = BuiltinFetchers.plugins(acquire, () => http, () => fail("openalex asked"), () => fail("archive asked"))
    PluginHost.build(plugins, ConfigFactory.parseString(user), ConfigFactory.parseMap(env.asJava),
      Plugins.services(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
        ConfigFactory.parseMap(env.asJava)))

  /** As the server builds it: a former plugin's settings (oa-index) mapped first ([[LegacyPlugins]]). */
  private def migrated(user: String = "", env: Map[String, String] = Map.empty): Generation =
    val plugins = BuiltinFetchers.plugins(cfg, () => Offline, () => fail("openalex asked"), () => fail("archive asked"))
    val m = LegacyPlugins.migrate(ConfigFactory.parseString(user), ConfigFactory.parseMap(env.asJava), Plugins.defaultKeys(plugins))
    PluginHost.build(plugins, m.user, m.env,
      Plugins.services(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
        ConfigFactory.parseMap(env.asJava)), m.warnings)

  private def chain(g: Generation): List[String] =
    FetchChain.of(g.registries.sourceFetchers, cfg.chainIds, cfg.allowUnauthorised)._1.plan.map(_._1)

  private def state(g: Generation, id: String): Option[String] = g.report.plugins.find(_.id == id).map(_.state)

  /** Today's order, which the default `acquire.chain` keeps. */
  private val Today = List("archive-org", "unpaywall", "core", "openalex", "pmc", "arxiv", "landing", "pmc-text")

  /** The open-access index plugins (D4) and the one fetcher each registers. */
  private val OaPlugins = List("oa-unpaywall" -> "unpaywall", "oa-core" -> "core", "oa-openalex" -> "openalex",
    "oa-landing" -> "landing")

  test("by default the built-in plugins register, without asking any upstream, and the chain is today's"):
    val g = generation()
    assertEquals(g.report.plugins.map(p => p.id -> p.state).sortBy(_._1),
      List("archive-org", "arxiv", "oa-core", "oa-landing", "oa-openalex", "oa-unpaywall", "pmc").map(_ -> "registered"))
    OaPlugins.foreach { (id, fetcher) =>
      assertEquals(g.report.plugins.find(_.id == id).map(_.provides), Some(List(s"source-fetcher:$fetcher")))
    }
    assertEquals(g.report.plugins.find(_.id == "pmc").map(_.provides),
      Some(List("source-fetcher:pmc", "source-fetcher:pmc-text")))
    assertEquals(chain(g), Today)
    assert(g.report.ok, g.report.toString)
    assertEquals(BuiltinFetchers.OaIndexes, OaPlugins.map(_._1))

  test("each open-access index is switched off on its own; the others keep their place in the chain"):
    OaPlugins.foreach { (id, fetcher) =>
      val g = generation(s"histmersion.plugins.$id.enabled = false")
      assertEquals(state(g, id), Some("disabled"))
      assertEquals(chain(g), Today.filterNot(_ == fetcher))
      val e = generation(env = Map(LegacyPlugins.enabledEnv(id) -> "false"))
      assertEquals(chain(e), Today.filterNot(_ == fetcher), id)
    }

  test("each open-access index has only its own settings: a setting of another fails it alone"):
    val g = generation("histmersion.plugins.oa-openalex.contact-email = \"x@example.org\"")
    assertEquals(state(g, "oa-openalex"), Some("failed"))
    assertEquals(state(g, "oa-unpaywall"), Some("registered"))
    assertEquals(chain(g), Today.filterNot(_ == "openalex"))

  /** Records every request; Unpaywall and CORE answer with no OA location, so nothing is downloaded. */
  private final class Recording extends Http:
    var asked: List[(String, Map[String, String])] = Nil // a test double's record of calls
    def json(url: String, headers: Map[String, String]): Either[String, Json] =
      asked = asked :+ (url -> headers); Right(Json.obj())
    def text(url: String, headers: Map[String, String]): Either[String, String]       = Left("no")
    def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]] = Left("no")

  private def emailOf(url: String): Option[String] =
    java.net.URI(url).getRawQuery.split("&").toList.collectFirst { case q if q.startsWith("email=") =>
      java.net.URLDecoder.decode(q.drop("email=".length), "UTF-8") }

  private def fetch(g: Generation, fetcher: String): Fetch =
    g.registries.sourceFetchers(fetcher).fetch(WorkKey(doi = Some("10.1/x")), Ctx)

  test("oa-unpaywall asks with its own contact-email, else Histmersion's acquire.contact-email, else not at all"):
    val host = cfg.copy(contactEmail = "host@example.org")
    val own = Recording()
    fetch(generation("histmersion.plugins.oa-unpaywall.contact-email = \"plugin@example.org\"", acquire = host, http = own), "unpaywall")
    assertEquals(own.asked.map(u => emailOf(u._1)), List(Some("plugin@example.org")))
    val fallback = Recording()
    fetch(generation(acquire = host, http = fallback), "unpaywall")
    assertEquals(fallback.asked.map(u => emailOf(u._1)), List(Some("host@example.org")))
    val env = Recording()
    fetch(generation(env = Map("HISTMERSION_PLUGIN_OA_UNPAYWALL_CONTACT_EMAIL" -> "env@example.org"), http = env), "unpaywall")
    assertEquals(env.asked.map(u => emailOf(u._1)), List(Some("env@example.org")))
    val none = Recording()
    assert(fetch(generation(http = none), "unpaywall").isInstanceOf[Fetch.Miss])
    assertEquals(none.asked, Nil)

  test("oa-core's api-key is a secret reference: CORE gets the value, the report only the reference"):
    val rec = Recording()
    val g = generation("histmersion.plugins.oa-core.api-key = \"env:TEST_CORE_KEY\"",
      env = Map("TEST_CORE_KEY" -> "k-secret"), http = rec)
    fetch(g, "core")
    assertEquals(rec.asked.flatMap(_._2.get("Authorization")), List("Bearer k-secret"))
    val shown = g.report.plugins.find(_.id == "oa-core").toList.flatMap(_.settings)
    assertEquals(shown.map(s => s.key -> s.value), List("api-key" -> "env:TEST_CORE_KEY"))
    assert(!g.report.toString.contains("k-secret"))

  test("oa-core without a key of its own uses acquire.core.api-key; with none, CORE is never asked"):
    val rec = Recording()
    fetch(generation(acquire = cfg.copy(core = cfg.core.copy(apiKey = "k-host")), http = rec), "core")
    assertEquals(rec.asked.flatMap(_._2.get("Authorization")), List("Bearer k-host"))
    val none = Recording()
    assert(fetch(generation(http = none), "core").isInstanceOf[Fetch.Miss])
    assertEquals(none.asked, Nil)

  test("oa-core whose secret cannot be resolved fails alone, naming the reference and never a value"):
    val g = generation("histmersion.plugins.oa-core.api-key = \"env:NOT_SET_CORE_KEY\"")
    assertEquals(state(g, "oa-core"), Some("failed"))
    assert(g.report.plugins.find(_.id == "oa-core").flatMap(_.error).exists(_.contains("NOT_SET_CORE_KEY")))
    assertEquals(chain(g), Today.filterNot(_ == "core"))

  // ── the former oa-index plugin's settings (LegacyPlugins) ───────────────────────────────────────

  test("no oa-index setting: nothing mapped, no warning"):
    val g = migrated()
    assertEquals(g.report.warnings, Nil)
    assertEquals(chain(g), Today)

  test("oa-index.enabled = false switches all four off, with a deprecation warning"):
    val g = migrated("histmersion.plugins.oa-index.enabled = false")
    OaPlugins.foreach((id, _) => assertEquals(state(g, id), Some("disabled"), id))
    assertEquals(chain(g), List("archive-org", "pmc", "arxiv", "pmc-text"))
    assert(g.report.warnings.exists(w => w.contains("histmersion.plugins.oa-index is deprecated") &&
      w.contains("oa-unpaywall, oa-core, oa-openalex, oa-landing")), g.report.warnings.toString)
    assert(!g.report.warnings.exists(_.contains("configures no plugin")), g.report.warnings.toString)

  test("a successor's own enabled wins over oa-index's"):
    val g = migrated("histmersion.plugins { oa-index.enabled = false, oa-core.enabled = true }")
    assertEquals(state(g, "oa-core"), Some("registered"))
    assertEquals(state(g, "oa-unpaywall"), Some("disabled"))

  test("HISTMERSION_PLUGIN_OA_INDEX_ENABLED stands for each successor's variable that is not set"):
    val g = migrated(env = Map("HISTMERSION_PLUGIN_OA_INDEX_ENABLED" -> "false", "HISTMERSION_PLUGIN_OA_CORE_ENABLED" -> "true"))
    assertEquals(state(g, "oa-core"), Some("registered"))
    List("oa-unpaywall", "oa-openalex", "oa-landing").foreach(id => assertEquals(state(g, id), Some("disabled"), id))
    assert(g.report.warnings.exists(_.contains("HISTMERSION_PLUGIN_OA_INDEX_ENABLED is deprecated")), g.report.warnings.toString)
    // the environment over the file, as before the split
    assertEquals(state(migrated("histmersion.plugins.oa-landing.enabled = true",
      Map("HISTMERSION_PLUGIN_OA_INDEX_ENABLED" -> "false")), "oa-landing"), Some("disabled"))

  test("oa-index's other keys go to the successor that has them; a key none has is reported, not fatal"):
    val g = migrated("""histmersion.plugins.oa-index { api-key = "env:K", contact-email = "a@example.org", url = "x" }""",
      Map("K" -> "v"))
    assertEquals(g.report.plugins.find(_.id == "oa-core").toList.flatMap(_.settings).map(s => s.key -> s.value),
      List("api-key" -> "env:K"))
    assertEquals(g.report.plugins.find(_.id == "oa-unpaywall").toList.flatMap(_.settings).map(s => s.key -> s.value),
      List("contact-email" -> "a@example.org"))
    assert(g.report.warnings.exists(_.contains("histmersion.plugins.oa-index.url: none of")), g.report.warnings.toString)
    OaPlugins.foreach((id, _) => assertEquals(state(g, id), Some("registered"), id))

  test("oa-index in plugins.order is replaced by the four, in place"):
    val g = migrated("histmersion.plugins.order = [pmc, oa-index, arxiv]")
    assertEquals(g.report.plugins.map(_.id).take(6), List("pmc", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "arxiv"))
    assert(!g.report.warnings.exists(_.contains("which is no plugin")), g.report.warnings.toString)
    assert(g.report.warnings.exists(_.contains("names 'oa-index', which is deprecated")), g.report.warnings.toString)

  test("reload: a change of an oa-index key rebuilds the four successors, and only them"):
    val g = generation()
    val carried = Plugins.carried(g, List("histmersion.plugins.oa-index.enabled"), independent = Set.empty)
    assertEquals(carried.keySet, Set("archive-org", "pmc", "arxiv"))

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
