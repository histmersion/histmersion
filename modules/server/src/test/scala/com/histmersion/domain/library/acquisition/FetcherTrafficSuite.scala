package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.{Plugin, Plugins}

import com.histmersion.config.{AcquireConfig, AppConfig, LoadedConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.{Doi, WorkKey}
import com.histmersion.plugin.Plugin as SpiPlugin
import com.histmersion.pluginhost.{FetchChain, Generation, HttpAnswer, HttpTransport, PolicyHttp}
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDate}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.library.search.Search

/** The recorded traffic of the acquisition chain: every request the stock generation's source fetchers (and the
  * new-source search's CORE index, on the generation's CORE key) send for a set of works, on the wire below the
  * outbound policy — URL, every header, in order — with what each fetch found. The answers come from the fixtures the
  * fetcher suites use; nothing reaches the network. The transcript is `traffic/fetchers.txt`, compared byte for byte
  * (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the fetchers changes no request.
  */
class FetcherTrafficSuite extends munit.FunSuite:

  private val Pdf: HttpAnswer.Body = HttpAnswer.Body(("%PDF-1.5\n" + ("x" * 4096)).getBytes("ISO-8859-1"))
  private val Epub: HttpAnswer.Body = HttpAnswer.Body(("PK epub " + ("e" * 20480)).getBytes("ISO-8859-1"))
  private val Xml: HttpAnswer.Body =
    HttpAnswer.Body(scala.io.Source.fromResource("jats/article.xml").mkString.getBytes(UTF_8))

  private val ContentType = CoreQuota.HeaderName("content-type")
  private val PdfType = CoreQuota.HeaderValue("application/pdf")
  private val XmlType = CoreQuota.HeaderValue("application/xml")
  private val EpubType = CoreQuota.HeaderValue("application/epub+zip")

  private def fixture(name: String): String =
    scala.io.Source.fromResource(s"acquire/$name")(using scala.io.Codec.UTF8).mkString
  private def landing(name: String): String = fixture(s"landing/$name")

  private def md5(b: HttpAnswer.Body): String =
    OaFetch.md5(OaFetch.Bytes(b.value)).getOrElse(fail("no MD5 in this runtime")).value

  private def json(s: String): Either[SpiPlugin.Http.Failure, HttpAnswer] =
    Right(
      HttpAnswer(
        HttpAnswer.Code(200),
        Map(ContentType -> CoreQuota.HeaderValue("application/json")),
        HttpAnswer.Body(s.getBytes(UTF_8))
      )
    )
  private def html(s: String): Either[SpiPlugin.Http.Failure, HttpAnswer] =
    Right(
      HttpAnswer(
        HttpAnswer.Code(200),
        Map(ContentType -> CoreQuota.HeaderValue("text/html")),
        HttpAnswer.Body(s.getBytes(UTF_8))
      )
    )
  private def file(b: HttpAnswer.Body, mime: CoreQuota.HeaderValue): Either[SpiPlugin.Http.Failure, HttpAnswer] =
    Right(HttpAnswer(HttpAnswer.Code(200), Map(ContentType -> mime), b))
  private val notFound: Either[SpiPlugin.Http.Failure, HttpAnswer] =
    Right(
      HttpAnswer(
        HttpAnswer.Code(404),
        Map(ContentType -> CoreQuota.HeaderValue("text/plain")),
        HttpAnswer.Body("not found".getBytes(UTF_8))
      )
    )

  /** Answers from `routes` (matched on the URL's text), 404 otherwise; every request written to `out`. */
  final private class Wire(routes: PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]])
      extends HttpTransport:
    val out = StringBuilder()
    def get(
        url: HttpTransport.Url,
        headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
    ): Either[SpiPlugin.Http.Failure, HttpAnswer] =
      val answer = routes.applyOrElse(url.value, (_: String) => notFound)
      out ++= s"GET $url\n"
      headers.toList.sortBy(_._1.value.toLowerCase(java.util.Locale.ROOT)).foreach((k, v) => out ++= s"  $k: $v\n")
      out ++= s"  -> ${answer.fold(e => s"error ${e.problem.sentence}", a => a.code.toString)}\n"
      answer

  private val baseText =
    """histmersion.acquire.contact-email = "host@example.org"
      |histmersion.acquire.core.api-key = "k-host"
      |""".stripMargin

  private def loaded(text: String, env: Map[String, String]): LoadedConfig =
    AppConfig
      .loadWithUserText(ConfigFactory.parseMap(env.asJava), text)
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  private val t0 = Instant.parse("2026-10-26T18:50:00Z")

  private def budget(): CoreBudget = CoreBudget(() => t0, _ => ())

  /** One generation over `wire`, as the server loads it for `text`, and the fetch of each work through its chain. */
  private def scenario(
      name: String,
      works: List[WorkKey],
      routes: AcquireConfig => PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]],
      text: String = baseText,
      env: Map[String, String] = Map.empty,
      search: Boolean = false
  ): String =
    val l = loaded(text, env)
    val cfg = l.config.acquire
    val wire = Wire(routes(cfg))
    val core = budget()
    val g: Generation = ServerPlugins.load(l, core = core, transport = _ => wire)
    val (chain, _) =
      FetchChain.of(
        ListMap.from(g.registries.sourceFetchers.toList.flatMap((k, f) => Plugin.Id.option(k.value).map(_ -> f))),
        cfg.chainIds.flatMap(c => Plugin.Id.option(c.value)),
        FetchChain.AllowUnauthorised(cfg.allowUnauthorised.value),
        plugins = g.registered.flatMap(i => Plugin.Id.option(i.value))
      )
    val sb = StringBuilder(s"## $name\n")
    sb ++= s"chain: ${chain.plan.map(_._1).mkString(", ")}\n"
    works.foreach { w =>
      wire.out.clear()
      val outcome = chain.fetchWorkExplained(w, legalOnly = FetchChain.LegalOnly(false))
      sb ++= s"### ${FetchChain.describe(w)}\n"
      sb ++= wire.out.result()
      sb ++= (outcome.file match
        case Right(f) =>
          s"found by ${outcome.foundBy.fold("?")(_.value)}: ${f.source} ${f.extension} ${f.url.fold("-")(_.value)} " +
            s"${f.bytes.value.length} bytes${f.licence.fold("")(l => s" licence ${l.value}")}\n"
        case Left(why) => s"no copy: ${why.sentence}\n" + outcome.answers.map(a => s"  ${a.line}\n").mkString)
    }
    if search then
      wire.out.clear()
      val host = new SttpHttp(PolicyHttp.acquisition(l.config, wire))
      val (indexes, _) =
        Indexes.of(cfg, host, Plugins.coreKey(g, cfg.core.key.map(k => Plugins.CoreKey(k.value))), core)
      val got = indexes
        .find(_.name.value == "core")
        .map(_.search(Search.Query("bronze age"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(5)))
      sb ++= "### core index search\n"
      sb ++= wire.out.result()
      sb ++= s"core index: ${got.fold("none")(_.fold(e => s"failed ${e.sentence}", hits => s"${hits.size} hit(s)"))}\n"
    sb ++= s"budget: lookups ${core.state.lookupTokens} searches ${core.state.searchTokens}\n"
    sb.result()

  private def doi(d: String): WorkKey = WorkKey(doi = Some(Doi.applyUnsafe(d)))

  // ── the routes ──────────────────────────────────────────────────────────────────────────────

  private val PmcDoi = "10.1038/nature25778"

  private def pmc(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    val record = fixture("pmc_dataset_oa.json").replaceAll("(\\.pdf\\?md5=)[0-9a-f]{32}", "$1" + md5(Pdf))
    {
      case u if u.startsWith(c.europepmc.url.value + "/search") => json(fixture("europepmc_oa.json"))
      case u if u.startsWith("https://europepmc.org/")          =>
        Right(
          HttpAnswer(
            HttpAnswer.Code(403),
            Map(CoreQuota.HeaderName("cf-mitigated") -> CoreQuota.HeaderValue("challenge")),
            HttpAnswer.Body("Just a moment...".getBytes(UTF_8))
          )
        )
      case u if u.startsWith(c.pmcDataset.url.value + "/?") =>
        Right(
          HttpAnswer(
            HttpAnswer.Code(200),
            Map(ContentType -> XmlType),
            HttpAnswer.Body(fixture("pmc_dataset_list.xml").getBytes(UTF_8))
          )
        )
      case u if u.startsWith(c.pmcDataset.url.value + "/metadata/")        => json(record)
      case u if u.startsWith(c.pmcDataset.url.value) && u.endsWith(".pdf") => file(Pdf, PdfType)
    }

  private val ManuscriptDoi = "10.1126/science.aar7711"

  private def manuscript(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    val record = fixture("pmc_dataset_manuscript.json").replaceAll("(\\.xml\\?md5=)[0-9a-f]{32}", "$1" + md5(Xml))
    {
      case u if u.startsWith(c.europepmc.url.value + "/search") => json(fixture("europepmc_free.json"))
      case u if u.startsWith(c.pmcDataset.url.value + "/?")     =>
        Right(
          HttpAnswer(
            HttpAnswer.Code(200),
            Map(ContentType -> XmlType),
            HttpAnswer.Body(fixture("pmc_dataset_list.xml").replace("PMC6091220", "PMC6748862").getBytes(UTF_8))
          )
        )
      case u if u.startsWith(c.pmcDataset.url.value + "/metadata/")        => json(record)
      case u if u.startsWith(c.pmcDataset.url.value) && u.endsWith(".xml") => file(Xml, XmlType)
    }

  private val IndexDoi = "10.9999/landing.1"
  private val UpPage = "https://repo-u.example.org/item/1"
  private val OaPage = "https://repo-o.example.org/item/1"
  private val DcPage = "https://repo-d.example.org/item/1"

  private def indexes(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    case u if u.startsWith(s"${c.unpaywall.url}/$IndexDoi") =>
      json(s"""{"doi":"$IndexDoi","oa_locations":[{"host_type":"repository","url_for_landing_page":"$UpPage"}]}""")
    case u if u.startsWith(s"${c.openalex.url}/") && u.contains(IndexDoi) =>
      json(
        s"""{"id":"https://openalex.org/W1","locations":[{"landing_page_url":"$OaPage","source":{"type":"repository"}}]}"""
      )
    case u if u == s"${c.datacite.url}/dois/$IndexDoi" => json(s"""{"data":{"attributes":{"url":"$DcPage"}}}""")

  private def direct(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    case u if u.startsWith(s"${c.unpaywall.url}/10.1/up") =>
      json("""{"doi":"10.1/up","best_oa_location":{"url_for_pdf":"https://repo.example.org/up.pdf","license":"cc-by"},
             |"oa_locations":[{"url_for_pdf":"https://repo.example.org/up.pdf","license":"cc-by"}]}""".stripMargin)
    case "https://repo.example.org/up.pdf"                                                  => file(Pdf, PdfType)
    case u if u.startsWith(s"${c.core.url.value}/search/works/") && u.contains("10.1/core") =>
      Right(
        HttpAnswer(
          HttpAnswer.Code(200),
          Map(
            ContentType -> CoreQuota.HeaderValue("application/json"),
            CoreQuota.HeaderName("x-ratelimit-limit") -> CoreQuota.HeaderValue("25"),
            CoreQuota.HeaderName("x-ratelimit-remaining") -> CoreQuota.HeaderValue("20")
          ),
          HttpAnswer.Body(
            """{"results":[{"doi":"10.1/core","downloadUrl":"https://core.example.org/core.pdf"}]}""".getBytes(UTF_8)
          )
        )
      )
    case "https://core.example.org/core.pdf"                     => file(Pdf, PdfType)
    case u if u.startsWith(s"${c.core.url.value}/search/works/") =>
      Right(
        HttpAnswer(
          HttpAnswer.Code(200),
          Map(ContentType -> CoreQuota.HeaderValue("application/json")),
          HttpAnswer.Body("""{"results":[]}""".getBytes(UTF_8))
        )
      )
    case u if u.startsWith(s"${c.openalex.url}/") && u.contains("10.1/oa") =>
      json(
        """{"id":"https://openalex.org/W9","best_oa_location":{"pdf_url":"https://oa.example.org/oa.pdf"},
          |"locations":[{"pdf_url":"https://oa.example.org/oa.pdf","is_oa":true,"source":{"type":"repository"}}]}""".stripMargin
      )
    case "https://oa.example.org/oa.pdf"                           => file(Pdf, PdfType)
    case u if u.startsWith(s"${c.arxiv.url}/pdf/2101.00001")       => file(Pdf, PdfType)
    case u if u.startsWith(s"${c.archiveOrg.url}/metadata/zqbook") =>
      json(
        """{"metadata":{"identifier":"zqbook","title":"A Book","creator":"An Author","date":"1901"},
          |"files":[{"name":"zqbook.pdf","format":"Text PDF","size":"40000"},{"name":"zqbook.epub","format":"EPUB","size":"30000"}]}""".stripMargin
      )
    case u if u.startsWith(s"${c.archiveOrg.url}/metadata/zqlent") =>
      json(
        """{"metadata":{"identifier":"zqlent","title":"Lent","access-restricted-item":"true"},
          |"files":[{"name":"zqlent.pdf","format":"Text PDF","size":"40000"}]}""".stripMargin
      )
    case u if u.startsWith(s"${c.archiveOrg.url}/download/zqbook/zqbook.epub") => file(Epub, EpubType)
    case u if u.startsWith(s"${c.archiveOrg.url}/download/zqbook/zqbook.pdf")  =>
      file(HttpAnswer.Body(("%PDF-1.4\n" + ("p" * 20480)).getBytes("ISO-8859-1")), PdfType)

  private def zenodo(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    val record = landing("zenodo_record_3695551.json").replaceAll("md5:[0-9a-f]{32}", s"md5:${md5(Pdf)}")
    val pdf = "https://zenodo.org/api/records/3695551/files/Oreshko%20Ahhiyawa-Danuna-offprint.pdf/content"
    {
      case u if u.startsWith(s"${c.openalex.url}/works/https://doi.org/10.5281/zenodo.3695550") =>
        json(landing("openalex_10_5281_zenodo_3695550.json"))
      case "https://zenodo.org/record/3695551"              => html("<!DOCTYPE html><html><head></head></html>")
      case u if u == s"${c.zenodo.url}/api/records/3695551" => json(record)
      case `pdf`                                            => file(Pdf, PdfType)
    }

  private def repositories(c: AcquireConfig): PartialFunction[String, Either[SpiPlugin.Http.Failure, HttpAnswer]] =
    val apollo = "https://www.repository.cam.ac.uk/handle/1810/265228"
    val apolloPdf = "https://www.repository.cam.ac.uk/bitstreams/635fb0e4-fed6-4dd0-a369-16067170b87f/download"
    val tobias = "https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561"
    val oreDoi = "10.12688/openreseurope.20351.3"
    {
      case u if u.startsWith(s"${c.openalex.url}/works/https://doi.org/10.17863/CAM.11325") =>
        json(landing("openalex_10_17863_CAM_11325.json"))
      case `apollo`    => html(landing("apollo_cam_item.html"))
      case `apolloPdf` => file(Pdf, PdfType)
      case u if u.startsWith(s"${c.openalex.url}/works/https://doi.org/10.15496/publikation-14969") =>
        json(landing("openalex_10_15496_publikation-14969.json"))
      case u if u == s"${c.datacite.url}/dois/10.15496/publikation-14969" =>
        json(landing("datacite_10_15496_publikation-14969.json"))
      case `tobias` => html(landing("tobias_lib_tuebingen_item.html"))
      case u if u.startsWith(s"${c.openalex.url}/works/https://doi.org/$oreDoi") =>
        json(landing("openalex_10_12688_openreseurope.20351.3.json"))
      case u if u == s"${c.doiHandles.url}/$oreDoi?type=URL" =>
        json(landing("doi_handle_10_12688_openreseurope.20351.3.json"))
      case "https://ore.eu/ore/article/view/1056/version/1810" => html(landing("ore_article.html"))
      case "https://ore.eu/ore/article/download/1056/3617"     => file(Pdf, PdfType)
    }

  // ── the transcript ──────────────────────────────────────────────────────────────────────────

  private def transcript: String =
    List(
      scenario(
        "the stock chain: each fetcher's own find",
        List(
          doi("10.1/up"),
          doi("10.1/core"),
          doi("10.1/oa"),
          WorkKey(arxiv = Some(WorkKey.Arxiv("2101.00001"))),
          WorkKey(archive = Some(WorkKey.Archive("zqbook"))),
          WorkKey(archive = Some(WorkKey.Archive("zqbook")), format = Some(WorkKey.Format("pdf"))),
          WorkKey(archive = Some(WorkKey.Archive("zqlent"))),
          doi("10.1/none")
        ),
        direct
      ),
      scenario("pmc: Europe PMC's challenge, then the dataset's PDF", List(doi(PmcDoi)), pmc),
      scenario(
        "pmc-text: an author manuscript's JATS, after arXiv",
        List(WorkKey(doi = Some(Doi.applyUnsafe(ManuscriptDoi)), arxiv = Some(WorkKey.Arxiv("1805.00001")))),
        manuscript
      ),
      scenario("landing: every index on", List(doi(IndexDoi)), indexes),
      scenario(
        "landing: oa-unpaywall's own contact",
        List(doi(IndexDoi)),
        indexes,
        baseText + "histmersion.plugins.oa-unpaywall.contact-email = \"plugin@example.org\"\n"
      ),
      scenario(
        "landing: oa-unpaywall's contact from the environment",
        List(doi(IndexDoi)),
        indexes,
        env = Map("HISTMERSION_PLUGIN_OA_UNPAYWALL_CONTACT_EMAIL" -> "env@example.org")
      ),
      scenario(
        "landing: oa-unpaywall off",
        List(doi(IndexDoi)),
        indexes,
        baseText + "histmersion.plugins.oa-unpaywall.enabled = false\n"
      ),
      scenario(
        "landing: oa-openalex off",
        List(doi(IndexDoi)),
        indexes,
        baseText + "histmersion.plugins.oa-openalex.enabled = false\n"
      ),
      scenario(
        "landing: both indexes off",
        List(doi(IndexDoi), doi("10.5281/zenodo.3695551")),
        indexes,
        baseText + "histmersion.plugins.oa-unpaywall.enabled = false\nhistmersion.plugins.oa-openalex.enabled = false\n"
      ),
      scenario(
        "landing: no contact anywhere",
        List(doi(IndexDoi)),
        indexes,
        "histmersion.acquire.core.api-key = \"k-host\"\n"
      ),
      scenario(
        "landing: oa-index's former settings",
        List(doi(IndexDoi)),
        indexes,
        baseText + "histmersion.plugins.oa-index.contact-email = \"legacy@example.org\"\n"
      ),
      scenario("landing: Zenodo by its API", List(doi("10.5281/zenodo.3695550")), zenodo),
      scenario(
        "landing: repositories and an open-access publisher",
        List(doi("10.17863/CAM.11325"), doi("10.15496/publikation-14969"), doi("10.12688/openreseurope.20351.3")),
        repositories
      ),
      scenario(
        "landing: acquire.landing.max-pages = 1",
        List(doi(IndexDoi)),
        indexes,
        baseText + "histmersion.acquire.landing.max-pages = 1\n"
      ),
      scenario(
        "core: oa-core's api-key as a secret reference keys the lookup and the search",
        List(doi("10.1/core")),
        direct,
        baseText + "histmersion.plugins.oa-core.api-key = \"env:TEST_CORE_KEY\"\n",
        Map("TEST_CORE_KEY" -> "k-plugin"),
        search = true
      ),
      scenario("core: the plain acquire.core.api-key keys both", List(doi("10.1/core")), direct, search = true),
      scenario(
        "core: oa-core off, the plain key keeps the search",
        List(doi("10.1/core")),
        direct,
        baseText + "histmersion.plugins.oa-core.enabled = false\n",
        search = true
      ),
      scenario(
        "core: no key at all",
        List(doi("10.1/core")),
        direct,
        "histmersion.acquire.contact-email = \"host@example.org\"\n",
        search = true
      ),
      scenario(
        "a reordered chain and acquire.user-agent",
        List(doi("10.1/oa")),
        direct,
        baseText + "histmersion.acquire.chain = \"openalex,arxiv\"\nhistmersion.acquire.user-agent = \"zq-agent/1\"\n"
      ),
      scenario(
        "arxiv, pmc and archive-org switched off",
        List(WorkKey(arxiv = Some(WorkKey.Arxiv("2101.00001"))), doi(PmcDoi)),
        c => direct(c).orElse(pmc(c)),
        baseText + "histmersion.plugins { arxiv.enabled = false, pmc.enabled = false, archive-org.enabled = false }\n"
      )
    ).mkString("\n")

  private val transcriptFile = Path.of("modules/server/src/test/resources/traffic/fetchers.txt")

  test("the acquisition chain sends exactly the recorded requests, in order, with the recorded headers"):
    val now = transcript
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(transcriptFile.getParent)
      Files.writeString(transcriptFile, now, UTF_8)
    val recorded = Files.readString(transcriptFile, UTF_8)
    if recorded != now then
      val (a, b) = (recorded.linesIterator.toVector, now.linesIterator.toVector)
      val at = a.indices.find(i => b.lift(i) != Some(a(i))).getOrElse(a.size)
      fail(
        s"the traffic differs from $transcriptFile at line ${at + 1}:\n  recorded: ${a.lift(at).getOrElse("<end>")}\n" +
          s"  now:      ${b.lift(at).getOrElse("<end>")}"
      )
