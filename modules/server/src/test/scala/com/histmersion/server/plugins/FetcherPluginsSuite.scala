package com.histmersion
package server.plugins

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.{LegacyPlugins, Plugins}

import com.histmersion.pluginhost.{FetchChain, Generation, HttpAnswer, Registries}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.plugin.{Fetch, WorkKey}
import com.histmersion.domain.library.acquisition.*
import io.circe.Json
import org.ekrich.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.plugin.Doi
import com.histmersion.config.ConfigReload

/** Histmersion's own acquisition plugins, found on the class path and registered through the plugin host like any
  * plugin, with the host's `histmersion.acquire` settings supplied: on by default in today's order, each one switched
  * off by config.
  */
class FetcherPluginsSuite extends munit.FunSuite:

  private val cfg = AppConfig.load().acquire

  /** Never asked: registering opens nothing. */
  private object Offline extends Http:
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Json] = fail(s"asked $url")
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Http.Text] = fail(s"asked $url")
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, HttpAnswer.Body] = fail(s"asked $url")

  private def loaded(user: String, env: Map[String, String]): LoadedConfig =
    AppConfig.loadWithUserText(ConfigFactory.parseMap(env.asJava), user).fold(e => fail(e.value), identity)

  /** As the server builds it: discovered, a former plugin's settings (oa-index) mapped first ([[LegacyPlugins]]), the
    * host's settings supplied.
    */
  private def generation(user: String = "", env: Map[String, String] = Map.empty, http: Http = Offline): Generation =
    ServerPlugins.fetchers(loaded(user, env), () => HttpPlugin(http), core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))

  private def migrated(user: String = "", env: Map[String, String] = Map.empty): Generation = generation(user, env)

  private val HostContact = "histmersion.acquire.contact-email = \"host@example.org\"\n"

  private def fetchers(g: Generation) =
    scala.collection.immutable.ListMap.from(
      g.registries.sourceFetchers.iterator.map((k, v) => com.histmersion.util.plugins.Plugin.Id.assume(k.value) -> v)
    )

  private def chain(g: Generation): List[String] =
    FetchChain
      .of(
        fetchers(g),
        cfg.chainIds.flatMap(c => com.histmersion.util.plugins.Plugin.Id.option(c.value)),
        FetchChain.AllowUnauthorised(cfg.allowUnauthorised.value)
      )
      ._1
      .plan
      .map(_._1.value)

  private def state(g: Generation, id: String): Option[String] = g.report.plugins.find(_.id.value == id).map(_.state.value)

  /** Today's order, which the default `acquire.chain` keeps. */
  private val Today = List("archive-org", "unpaywall", "core", "openalex", "pmc", "arxiv", "landing", "pmc-text")

  /** The open-access index plugins (D4) and the one fetcher each registers. */
  private val OaPlugins =
    List("oa-unpaywall" -> "unpaywall", "oa-core" -> "core", "oa-openalex" -> "openalex", "oa-landing" -> "landing")

  test("by default the built-in plugins register, without asking any upstream, and the chain is today's"):
    val g = generation()
    assertEquals(
      g.report.plugins.map(p => p.id.value -> p.state.value).sortBy(_._1),
      List("archive-org", "arxiv", "oa-core", "oa-landing", "oa-openalex", "oa-unpaywall", "pmc").map(_ -> "registered")
    )
    OaPlugins.foreach { (id, fetcher) =>
      assertEquals(g.report.plugins.find(_.id.value == id).map(_.provides), Some(List(s"source-fetcher:$fetcher")))
    }
    assertEquals(
      g.report.plugins.find(_.id.value == "pmc").map(_.provides),
      Some(List("source-fetcher:pmc", "source-fetcher:pmc-text"))
    )
    assertEquals(chain(g), Today)
    assert(g.report.ok, g.report.toString)
    assertEquals(LegacyPlugins.OaIndex.into, OaPlugins.map(_._1))

  test("each open-access index is switched off on its own; the others keep their place in the chain"):
    OaPlugins.foreach { (id, fetcher) =>
      val g = generation(s"histmersion.plugins.$id.enabled = false")
      assertEquals(state(g, id), Some("disabled"))
      assertEquals(chain(g), Today.filterNot(_ == fetcher))
      val e = generation(env = Map(LegacyPlugins.enabledEnv(com.histmersion.util.plugins.Plugin.Id.assume(id)).value -> "false"))
      assertEquals(chain(e), Today.filterNot(_ == fetcher), id)
    }

  test("each open-access index has only its own settings: a setting of another fails it alone"):
    val g = generation("histmersion.plugins.oa-openalex.contact-email = \"x@example.org\"")
    assertEquals(state(g, "oa-openalex"), Some("failed"))
    assertEquals(state(g, "oa-unpaywall"), Some("registered"))
    assertEquals(chain(g), Today.filterNot(_ == "openalex"))

  /** Records every request; Unpaywall and CORE answer with no OA location, so nothing is downloaded. */
  final private class Recording extends Http:
    var asked: List[(String, Map[String, String])] = Nil // a test double's record of calls
    private def record(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Unit =
      asked = asked :+ (url.value -> headers.map((k, v) => k.value -> v.value))
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Json] =
      record(url, headers); Right(Json.obj())
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Http.Text] = Left(Http.Problem("no"))
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, HttpAnswer.Body] =
      record(url, headers); Right(HttpAnswer.Body("{}".getBytes("UTF-8")))
    // CORE is asked once, not retried, through its budget
    override def once(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      record(url, headers);
      Right(HttpAnswer(HttpAnswer.Code(200), Map.empty, HttpAnswer.Body("{}".getBytes("UTF-8"))))

  private def emailOf(url: String): Option[String] =
    java.net.URI(url).getRawQuery.split("&").toList.collectFirst {
      case q if q.startsWith("email=") =>
        java.net.URLDecoder.decode(q.drop("email=".length), "UTF-8")
    }

  private val UnpaywallKey = Registries.SourceFetchersKey("unpaywall")
  private val CoreKey = Registries.SourceFetchersKey("core")

  private def fetch(g: Generation, fetcher: Registries.SourceFetchersKey): Fetch =
    g.registries.sourceFetchers(fetcher).fetch(WorkKey(doi = Some(Doi("10.1/x"))), Ctx)

  test("oa-unpaywall asks with its own contact-email, else Histmersion's acquire.contact-email, else not at all"):
    val own = Recording()
    fetch(
      generation(HostContact + "histmersion.plugins.oa-unpaywall.contact-email = \"plugin@example.org\"", http = own),
      UnpaywallKey
    )
    assertEquals(own.asked.map(u => emailOf(u._1)), List(Some("plugin@example.org")))
    val fallback = Recording()
    fetch(generation(HostContact, http = fallback), UnpaywallKey)
    assertEquals(fallback.asked.map(u => emailOf(u._1)), List(Some("host@example.org")))
    val env = Recording()
    fetch(
      generation(env = Map("HISTMERSION_PLUGIN_OA_UNPAYWALL_CONTACT_EMAIL" -> "env@example.org"), http = env),
      UnpaywallKey
    )
    assertEquals(env.asked.map(u => emailOf(u._1)), List(Some("env@example.org")))
    val none = Recording()
    assert(fetch(generation(http = none), UnpaywallKey).isInstanceOf[Fetch.Miss])
    assertEquals(none.asked, Nil)

  test("oa-core's api-key is a secret reference: CORE gets the value, the report only the reference"):
    val rec = Recording()
    val g = generation(
      "histmersion.plugins.oa-core.api-key = \"env:TEST_CORE_KEY\"",
      env = Map("TEST_CORE_KEY" -> "k-secret"),
      http = rec
    )
    fetch(g, CoreKey)
    assertEquals(rec.asked.flatMap(_._2.get("Authorization")), List("Bearer k-secret"))
    val shown = g.report.plugins.find(_.id.value == "oa-core").toList.flatMap(_.settings)
    assertEquals(shown.map(s => s.key -> s.value), List("api-key" -> "env:TEST_CORE_KEY"))
    assert(!g.report.toString.contains("k-secret"))

  test("oa-core without a key of its own uses acquire.core.api-key; with none, CORE is never asked"):
    val rec = Recording()
    fetch(generation("histmersion.acquire.core.api-key = \"k-host\"", http = rec), CoreKey)
    assertEquals(rec.asked.flatMap(_._2.get("Authorization")), List("Bearer k-host"))
    val none = Recording()
    assert(fetch(generation(http = none), CoreKey).isInstanceOf[Fetch.Miss])
    assertEquals(none.asked, Nil)

  test("oa-core whose secret cannot be resolved fails alone, naming the reference and never a value"):
    val g = generation("histmersion.plugins.oa-core.api-key = \"env:NOT_SET_CORE_KEY\"")
    assertEquals(state(g, "oa-core"), Some("failed"))
    assert(g.report.plugins.find(_.id.value == "oa-core").flatMap(_.error).exists(_.value.contains("NOT_SET_CORE_KEY")))
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
    assert(
      g.report.warnings.exists(w =>
        w.value.contains("histmersion.plugins.oa-index is deprecated") &&
          w.value.contains("oa-unpaywall, oa-core, oa-openalex, oa-landing")
      ),
      g.report.warnings.toString
    )
    assert(!g.report.warnings.exists(_.value.contains("configures no plugin")), g.report.warnings.toString)

  test("a successor's own enabled wins over oa-index's"):
    val g = migrated("histmersion.plugins { oa-index.enabled = false, oa-core.enabled = true }")
    assertEquals(state(g, "oa-core"), Some("registered"))
    assertEquals(state(g, "oa-unpaywall"), Some("disabled"))

  test("HISTMERSION_PLUGIN_OA_INDEX_ENABLED stands for each successor's variable that is not set"):
    val g = migrated(env =
      Map("HISTMERSION_PLUGIN_OA_INDEX_ENABLED" -> "false", "HISTMERSION_PLUGIN_OA_CORE_ENABLED" -> "true")
    )
    assertEquals(state(g, "oa-core"), Some("registered"))
    List("oa-unpaywall", "oa-openalex", "oa-landing").foreach(id => assertEquals(state(g, id), Some("disabled"), id))
    assert(
      g.report.warnings.exists(_.value.contains("HISTMERSION_PLUGIN_OA_INDEX_ENABLED is deprecated")),
      g.report.warnings.toString
    )
    // the environment over the file, as before the split
    assertEquals(
      state(
        migrated(
          "histmersion.plugins.oa-landing.enabled = true",
          Map("HISTMERSION_PLUGIN_OA_INDEX_ENABLED" -> "false")
        ),
        "oa-landing"
      ),
      Some("disabled")
    )

  test("oa-index's other keys go to the successor that has them; a key none has is reported, not fatal"):
    val g = migrated(
      """histmersion.plugins.oa-index { api-key = "env:K", contact-email = "a@example.org", url = "x" }""",
      Map("K" -> "v")
    )
    assertEquals(
      g.report.plugins.find(_.id.value == "oa-core").toList.flatMap(_.settings).map(s => s.key -> s.value),
      List("api-key" -> "env:K")
    )
    assertEquals(
      g.report.plugins.find(_.id.value == "oa-unpaywall").toList.flatMap(_.settings).map(s => s.key -> s.value),
      List("contact-email" -> "a@example.org")
    )
    assert(
      g.report.warnings.exists(_.value.contains("histmersion.plugins.oa-index.url: none of")),
      g.report.warnings.toString
    )
    OaPlugins.foreach((id, _) => assertEquals(state(g, id), Some("registered"), id))

  test("oa-index in plugins.order is replaced by the four, in place"):
    val g = migrated("histmersion.plugins.order = [pmc, oa-index, arxiv]")
    assertEquals(
      g.report.plugins.map(_.id).take(6),
      List("pmc", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "arxiv")
    )
    assert(!g.report.warnings.exists(_.value.contains("which is no plugin")), g.report.warnings.toString)
    assert(g.report.warnings.exists(_.value.contains("names 'oa-index', which is deprecated")), g.report.warnings.toString)

  test("reload: a change of an oa-index key rebuilds the four successors, and only them"):
    val g = generation()
    val carried = Plugins.carried(g, List(ConfigReload.ChangedKey("histmersion.plugins.oa-index.enabled")))
    assertEquals(carried.keySet, Set("archive-org", "pmc", "arxiv"))

  test("the shipped default chain names exactly the built-in fetchers, in today's order"):
    assertEquals(cfg.chainIds, Today)
    assertEquals(cfg.allowUnauthorised, false)

  test("a plugin switched off takes all its fetchers out of the chain; the rest keep their order"):
    val g = generation("histmersion.plugins.pmc.enabled = false")
    assertEquals(g.report.plugins.find(_.id.value == "pmc").map(_.state), Some("disabled"))
    assertEquals(chain(g), Today.filterNot(Set("pmc", "pmc-text")))

  test("the environment switches one off too (HISTMERSION_PLUGIN_<ID>_ENABLED)"):
    val g = generation(env = Map("HISTMERSION_PLUGIN_ARXIV_ENABLED" -> "false"))
    assertEquals(g.report.plugins.find(_.id.value == "arxiv").map(_.state), Some("disabled"))
    assertEquals(chain(g), Today.filterNot(_ == "arxiv"))

  test("a built-in plugin has no settings of its own: an unknown key fails it alone"):
    val g = generation("histmersion.plugins.arxiv { enabled = true, url = \"https://example.org\" }")
    assertEquals(g.report.plugins.find(_.id.value == "arxiv").map(_.state), Some("failed"))
    assertEquals(chain(g), Today.filterNot(_ == "arxiv"))

  test("a reordered chain is honoured, unlisted fetchers following in registration order"):
    val g = generation()
    val (c, notes) = FetchChain.of(fetchers(g), List(com.histmersion.util.plugins.Plugin.Id("arxiv"), com.histmersion.util.plugins.Plugin.Id("pmc")), allowUnauthorised = FetchChain.AllowUnauthorised(false))
    assertEquals(c.plan.map(_._1.value).take(2), List("arxiv", "pmc"))
    assertEquals(c.plan.map(_._1.value).toSet, Today.toSet)
    assertEquals(notes, Nil)

  // ── the landing step reads an index only while its plugin is on (§20.2) ─────────────────────

  private val LandingDoi = com.histmersion.domain.library.Doi("10.9999/landing.1")
  private val UpPage = "https://repo-u.example.org/item/1" // Unpaywall's repository location
  private val OaPage = "https://repo-o.example.org/item/1" // OpenAlex's
  private val DcPage = "https://repo-d.example.org/item/1" // where DataCite says the DOI resolves
  private val host = cfg

  /** The three indexes answer with one repository location each; every page is a 404, so the chain reads each page it
    * is given and finds no copy — the requests are the whole record.
    */
  final private class Indexes(c: AcquireConfig) extends Http:
    var asked: List[String] = Nil // a test double's record of calls
    private def answer(request: Http.Url): Either[Http.Problem, HttpAnswer.Body] =
      val url = request.value
      asked = asked :+ url
      val body =
        if url.startsWith(s"${c.unpaywall.url}/") then
          Some(
            s"""{"doi":"$LandingDoi","oa_locations":[{"host_type":"repository","url_for_landing_page":"$UpPage"}]}"""
          )
        else if url.startsWith(s"${c.openalex.url}/") then
          Some(
            s"""{"id":"https://openalex.org/W1","locations":[{"landing_page_url":"$OaPage","source":{"type":"repository"}}]}"""
          )
        else if url == s"${c.datacite.url}/dois/$LandingDoi" then Some(s"""{"data":{"attributes":{"url":"$DcPage"}}}""")
        else None
      body.map(b => HttpAnswer.Body(b.getBytes("UTF-8"))).toRight(Http.Problem(s"$url: HTTP 404"))
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) = answer(url)
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) = answer(url).map(b => Http.Text(new String(b.value, "UTF-8")))
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Json] =
      text(url, headers).flatMap(t => io.circe.parser.parse(t.value).left.map(e => Http.Problem(e.getMessage)))

  /** The index steps and the landing step of a generation built over `http`, run in today's order with one memo for the
    * work, as an ingest runs them.
    */
  private def runIndexes(user: String, http: Indexes, hostSettings: String = HostContact): Generation =
    val g = generation(hostSettings + user, http = http)
    val steps = List("unpaywall", "openalex", "landing").flatMap(id =>
      g.registries.sourceFetchers.get(Registries.SourceFetchersKey(id)).map(FetchChain.Step.Id(id) -> _)
    )
    assertEquals(
      FetchChain(steps, registered = id => Fetch.Context.RegisteredResult(g.registered.exists(_.value == id.value)))
        .fetch(Some(LandingDoi), None),
      Left(FetchChain.NoCopy)
    )
    g

  private def unpaywallAsked(h: Indexes) = h.asked.filter(_.startsWith(s"${host.unpaywall.url}/"))
  private def openalexAsked(h: Indexes) = h.asked.filter(_.startsWith(s"${host.openalex.url}/"))
  private def pagesRead(h: Indexes) = h.asked.filter(u => Set(UpPage, OaPage, DcPage)(u))

  test("landing, all indexes on: each upstream asked ONCE for the work (records shared), every location read"):
    val h = Indexes(host)
    runIndexes("", h)
    // byte-identical to the requests before the landing step followed the plugin switches
    assertEquals(
      h.asked,
      List(
        s"${host.unpaywall.url}/$LandingDoi?email=host@example.org",
        openalexAsked(h).headOption.getOrElse(fail(h.asked.mkString("\n"))),
        UpPage,
        OaPage,
        s"${host.datacite.url}/dois/$LandingDoi",
        DcPage
      )
    )
    assertEquals(openalexAsked(h).size, 1)

  test("landing, oa-unpaywall off: Unpaywall is never asked, not even on the landing step's behalf"):
    val h = Indexes(host)
    runIndexes("histmersion.plugins.oa-unpaywall.enabled = false", h)
    assertEquals(unpaywallAsked(h), Nil)
    assertEquals(openalexAsked(h).size, 1)
    assertEquals(pagesRead(h), List(OaPage, DcPage))

  test("landing, oa-openalex off: OpenAlex is never asked; Unpaywall's locations and DataCite's page still read"):
    val h = Indexes(host)
    runIndexes("histmersion.plugins.oa-openalex.enabled = false", h)
    assertEquals(openalexAsked(h), Nil)
    assertEquals(unpaywallAsked(h).size, 1)
    assertEquals(pagesRead(h), List(UpPage, DcPage))

  test("landing, both indexes off: the DOI alone — DataCite's landing page, no index asked"):
    val h = Indexes(host)
    runIndexes("histmersion.plugins.oa-unpaywall.enabled = false\nhistmersion.plugins.oa-openalex.enabled = false", h)
    assertEquals(h.asked, List(s"${host.datacite.url}/dois/$LandingDoi", DcPage))

  test("landing, both indexes off: a Zenodo DOI still goes to Zenodo's API"):
    val h = Indexes(host)
    val g = generation(
      HostContact + "histmersion.plugins.oa-unpaywall.enabled = false\nhistmersion.plugins.oa-openalex.enabled = false",
      http = h
    )
    val got =
      FetchChain(List(FetchChain.Step.Id("landing") -> g.registries.sourceFetchers(Registries.SourceFetchersKey("landing"))), registered = id => Fetch.Context.RegisteredResult(g.registered.exists(_.value == id.value)))
        .fetch(Some(com.histmersion.domain.library.Doi("10.5281/zenodo.3695551")), None)
    assertEquals(got, Left(FetchChain.NoCopy))
    assert(h.asked.contains(s"${host.zenodo.url}/api/records/3695551"), h.asked.mkString("\n"))
    assert(!h.asked.exists(u => u.startsWith(s"${host.unpaywall.url}/") || u.startsWith(s"${host.openalex.url}/")))

  test("landing asks Unpaywall with oa-unpaywall's own contact: one request, not one per contact"):
    val h = Indexes(host)
    runIndexes("histmersion.plugins.oa-unpaywall.contact-email = \"plugin@example.org\"", h)
    assertEquals(unpaywallAsked(h).map(emailOf), List(Some("plugin@example.org")))

  test("landing with oa-unpaywall on but no contact anywhere: Unpaywall is not asked (it answers 422)"):
    val h = Indexes(host)
    runIndexes("", h, hostSettings = "")
    assertEquals(unpaywallAsked(h), Nil)
    assertEquals(pagesRead(h), List(OaPage, DcPage))

  test("reload: switching an index plugin on or off rebuilds it alone; oa-unpaywall's contact rebuilds oa-landing too"):
    val g = generation()
    // the landing step asks, fetch by fetch, which index plugins its generation registered
    List("oa-unpaywall", "oa-openalex", "oa-landing").foreach { id =>
      val carried = Plugins.carried(g, List(ConfigReload.ChangedKey(s"histmersion.plugins.$id.enabled")))
      assertEquals(
        carried.keySet,
        Set("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "pmc", "arxiv") - id,
        id
      )
    }
    // … but it asks Unpaywall with oa-unpaywall's own contact, which it is supplied at registration
    assertEquals(
      Plugins.carried(g, List(ConfigReload.ChangedKey("histmersion.plugins.oa-unpaywall.contact-email"))).keySet,
      Set("archive-org", "oa-core", "oa-openalex", "pmc", "arxiv")
    )
    // a host key every fetcher reads rebuilds them all; one no fetcher reads, none
    assertEquals(Plugins.carried(g, List(ConfigReload.ChangedKey("histmersion.acquire.landing.max-pages"))), Map.empty)
    assertEquals(Plugins.carried(g, Nil).keySet.size, 7)

  private object Ctx extends Fetch.Context:
    def memo[A](key: Fetch.Context.MemoKey)(compute: => A): A = compute
    val log: Plugin.Log = new Plugin.Log { def info(m: Plugin.Log.InfoMessage) = (); def warn(m: Plugin.Log.WarnMessage) = () }
