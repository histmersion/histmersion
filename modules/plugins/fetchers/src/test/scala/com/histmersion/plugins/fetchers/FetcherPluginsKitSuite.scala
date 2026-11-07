package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Doi, Plugin, Secret, WorkKey}
import com.histmersion.plugin.testkit.{FetchOutcome, HttpMode, PluginHarness, StubAnswer}
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport}
import com.histmersion.testsupport.ComparedByValue.given

import java.nio.charset.StandardCharsets.UTF_8

/** Histmersion's own acquisition plugins run as any plugin author runs theirs: through the plugin testkit, which loads
  * a plugin from the class path as the server does (the host's own `histmersion.acquire` settings supplied to a
  * first-party plugin) and answers its requests from canned answers — never the network.
  */
class FetcherPluginsKitSuite extends munit.FunSuite:

  private val Pdf: Array[Byte] = ("%PDF-1.5\n" + ("x" * 4096)).getBytes("ISO-8859-1")

  private def load(
      id: Plugin.Info.Id,
      stubs: Map[HttpTransport.Url, StubAnswer] = Map.empty,
      config: String = "",
      secrets: Map[Secret.Ref.ParseText, Secret.Vault.ResolveResultPart2] = Map.empty
  ) =
    PluginHarness
      .load(id, config, http = HttpMode.Stub(stubs), secrets = secrets, env = Map.empty)
      .fold(e => fail(e.value), identity)

  private def json(s: HttpAnswer.Text) = StubAnswer.text(s, com.histmersion.domain.CoreQuota.HeaderValue("application/json"))

  test("each plugin loads from the class path with its defaults and the host's settings, and registers its steps"):
    val steps = Map(
      Plugin.Info.Id("archive-org") -> List("archive-org"),
      Plugin.Info.Id("oa-unpaywall") -> List("unpaywall"),
      Plugin.Info.Id("oa-core") -> List("core"),
      Plugin.Info.Id("oa-openalex") -> List("openalex"),
      Plugin.Info.Id("oa-landing") -> List("landing"),
      Plugin.Info.Id("pmc") -> List("pmc", "pmc-text"),
      Plugin.Info.Id("arxiv") -> List("arxiv")
    )
    steps.foreach { (id, fetchers) =>
      val h = load(id)
      assertEquals(h.contributions.sourceFetchers, fetchers, id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a fetcher plugin has no setting the host owns: an endpoint in its block is refused"):
    val refused = PluginHarness.load(
      com.histmersion.plugin.Plugin.Info.Id("arxiv"),
      "url = \"https://elsewhere.example\"",
      http = HttpMode.Offline,
      env = Map.empty
    )
    assert(refused.left.exists(_.value.contains("unknown setting")), refused.toString)

  // ── archive-org ─────────────────────────────────────────────────────────────────────────────

  private val metadata = HttpTransport.Url("https://archive.org/metadata/zqbook")
  private val item =
    """{"metadata":{"identifier":"zqbook","title":"A Book"},
      |"files":[{"name":"zqbook.pdf","format":"Text PDF","size":"40000"},{"name":"zqbook.epub","format":"EPUB","size":"30000"}]}""".stripMargin
  private val book = ("PK " + "e" * 20480).getBytes(UTF_8)

  test("archive-org: no identifier is a miss, and nothing is asked"):
    val h = load(Plugin.Info.Id("archive-org"))
    val r = h.fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.1/x"))))
    assert(r.outcome.isInstanceOf[FetchOutcome.Miss], r.toString)
    assertEquals(r.requests, Nil)

  test("archive-org: the preferred format, else an EPUB first"):
    val h = load(
      Plugin.Info.Id("archive-org"),
      Map(
        metadata -> json(HttpAnswer.Text(item)),
        HttpTransport.Url("https://archive.org/download/zqbook/zqbook.epub") -> StubAnswer.ok(HttpAnswer.Body(book), com.histmersion.domain.CoreQuota.HeaderValue("application/epub+zip")),
        HttpTransport.Url("https://archive.org/download/zqbook/zqbook.pdf") -> StubAnswer.ok(HttpAnswer.Body(Pdf ++ book), com.histmersion.domain.CoreQuota.HeaderValue("application/pdf"))
      )
    )
    assertEquals(h.fetch(WorkKey(archive = Some(WorkKey.Archive("zqbook")))).file.map(_.extension), Some("epub"))
    assertEquals(h.fetch(WorkKey(archive = Some(WorkKey.Archive("zqbook")), format = Some(WorkKey.Format("pdf")))).file.map(_.extension), Some("pdf"))

  test("archive-org: a lending-only item is REFUSED and never downloaded"):
    val lent =
      """{"metadata":{"identifier":"zqlent","access-restricted-item":"true"},"files":[{"name":"zqlent.pdf"}]}"""
    val h = load(Plugin.Info.Id("archive-org"), Map(HttpTransport.Url("https://archive.org/metadata/zqlent") -> json(HttpAnswer.Text(lent))))
    val r = h.fetch(WorkKey(archive = Some(WorkKey.Archive("zqlent"))))
    r.outcome match
      case FetchOutcome.Refused(why) => assert(why.value.contains("lending-only"), why)
      case other                     => fail(s"not refused: $other")
    assertEquals(r.requests, List("GET https://archive.org/metadata/zqlent"))

  test("archive-org: an item with no epub/pdf file is a miss"):
    val h = load(Plugin.Info.Id("archive-org"), Map(metadata -> json(HttpAnswer.Text("""{"metadata":{},"files":[{"name":"zqbook.djvu"}]}"""))))
    assert(h.fetch(WorkKey(archive = Some(WorkKey.Archive("zqbook")))).answers.exists(_.why.value.contains("no epub/pdf")))

  // ── the open-access indexes ─────────────────────────────────────────────────────────────────

  test("oa-unpaywall asks with its own contact-email, as its `email` parameter only"):
    val h = load(
      Plugin.Info.Id("oa-unpaywall"),
      Map(
        HttpTransport.Url("https://api.unpaywall.org/v2/10.1/up?email=plugin@example.org") ->
          json(HttpAnswer.Text("""{"best_oa_location":{"url_for_pdf":"https://repo.example.org/up.pdf"}}""")),
        HttpTransport.Url("https://repo.example.org/up.pdf") -> StubAnswer.ok(HttpAnswer.Body(Pdf), com.histmersion.domain.CoreQuota.HeaderValue("application/pdf"))
      ),
      "contact-email = \"plugin@example.org\""
    )
    val r = h.fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.1/up"))))
    assertEquals(r.file.map(_.source), Some("unpaywall"), r.toString)

  test("oa-unpaywall without any contact does not ask Unpaywall (it answers 422)"):
    val h = load(Plugin.Info.Id("oa-unpaywall"))
    val r = h.fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.1/up"))))
    assertEquals(r.requests, Nil)

  test("oa-core's api-key is a secret reference: CORE gets the value, a recording only the placeholder"):
    val h = load(
      Plugin.Info.Id("oa-core"),
      Map(
        HttpTransport.Url("https://api.core.ac.uk/v3/search/works/?q=doi:%2210.1/core%22&limit=1") ->
          json(HttpAnswer.Text("""{"results":[{"downloadUrl":"https://core.example.org/core.pdf"}]}""")),
        HttpTransport.Url("https://core.example.org/core.pdf") -> StubAnswer.ok(HttpAnswer.Body(Pdf), com.histmersion.domain.CoreQuota.HeaderValue("application/pdf"))
      ),
      "api-key = \"env:ZQ_CORE_KEY\"",
      Map(Secret.Ref.ParseText("env:ZQ_CORE_KEY") -> Secret.Vault.ResolveResultPart2("k-secret"))
    )
    val r = h.fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.1/core"))))
    assertEquals(r.file.map(_.source), Some("core"), r.toString)
    assert(!h.log.exists(_.message.value.contains("k-secret")))

  test("oa-core with no key at all does not ask CORE, and says which settings would key it"):
    val r = load(Plugin.Info.Id("oa-core")).fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.1/core"))))
    assertEquals(r.requests, Nil)
    assert(r.answers.exists(_.why.value.contains("plugins.oa-core.api-key or acquire.core.api-key")), r.toString)

  test("oa-landing alone (no index plugin registered): the DOI's DataCite page, no index asked"):
    val h = load(
      Plugin.Info.Id("oa-landing"),
      Map(
        HttpTransport.Url("https://api.datacite.org/dois/10.9999/zq") -> json(
          HttpAnswer.Text("""{"data":{"attributes":{"url":"https://repo.example.ac.uk/item/1"}}}""")
        ),
        HttpTransport.Url("https://repo.example.ac.uk/item/1") -> StubAnswer.text(
          HttpAnswer.Text("""<html><head><meta name="citation_pdf_url" content="https://repo.example.ac.uk/item/1.pdf"></head></html>"""),
          com.histmersion.domain.CoreQuota.HeaderValue("text/html")
        ),
        HttpTransport.Url("https://repo.example.ac.uk/item/1.pdf") -> StubAnswer.ok(HttpAnswer.Body(Pdf), com.histmersion.domain.CoreQuota.HeaderValue("application/pdf"))
      )
    )
    val r = h.fetch(WorkKey(doi = Some(Doi.applyUnsafe("10.9999/zq"))))
    assertEquals(r.file.map(_.source), Some("repository"), r.toString)
    assert(!r.requests.exists(u => u.value.contains("unpaywall") || u.value.contains("openalex")), r.requests.toString)

  test("arxiv: the preprint's PDF by its id"):
    val h = load(Plugin.Info.Id("arxiv"), Map(HttpTransport.Url("https://arxiv.org/pdf/2101.00001") -> StubAnswer.ok(HttpAnswer.Body(Pdf), com.histmersion.domain.CoreQuota.HeaderValue("application/pdf"))))
    assertEquals(h.fetch(WorkKey(arxiv = Some(WorkKey.Arxiv("2101.00001")))).file.map(_.source), Some("arxiv"))
