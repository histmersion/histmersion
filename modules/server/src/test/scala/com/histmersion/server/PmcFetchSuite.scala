package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.acquire.*
import io.circe.Json

import java.nio.file.Files

/** The Europe PMC / PMC Article Datasets step of the OA chain (#1523), against a fake upstream that answers from the
  * responses captured on 2026-10-11 — no network.
  */
class PmcFetchSuite extends munit.FunSuite:

  private type Routes = PartialFunction[String, Either[String, Array[Byte]]]
  private def route(pf: Routes): Routes = pf

  private def fixture(name: String) =
    scala.io.Source.fromResource(s"acquire/$name").mkString

  private val Pdf: Array[Byte] = ("%PDF-1.4\n" + ("x" * 2048)).getBytes("ISO-8859-1")
  private val Xml: Array[Byte] = scala.io.Source.fromResource("jats/article.xml").mkString.getBytes("UTF-8")

  /** A fake upstream: answers by URL prefix, records every request and the headers it carried. */
  final private class FakeUpstream(cfg: AcquireConfig, routes: PartialFunction[String, Either[String, Array[Byte]]])
      extends Http:
    var asked: List[(String, Map[String, String])] = Nil // a test double: the simplest record of calls
    private def answer(url: String, headers: Map[String, String]): Either[String, Array[Byte]] =
      asked = asked :+ (url -> headers)
      routes.applyOrElse(url, (u: String) => Left(s"$u: offline"))
    def bytes(url: String, headers: Map[String, String]) = answer(url, headers)
    def text(url: String, headers: Map[String, String]) = answer(url, headers).map(new String(_, "UTF-8"))
    def json(url: String, headers: Map[String, String]): Either[String, Json] =
      text(url, headers).flatMap(s => io.circe.parser.parse(s).left.map(_.getMessage))

  private def cfg = AppConfig.load().acquire.copy(contactEmail = "someone@example.org")

  private def routes(
      c: AcquireConfig,
      search: String,
      record: String,
      pdf: Either[String, Array[Byte]],
      xml: Either[String, Array[Byte]] = Right(Xml)
  ): PartialFunction[String, Either[String, Array[Byte]]] =
    case u if u.startsWith(c.europepmc.url + "/search") => Right(fixture(search).getBytes("UTF-8"))
    case u if u.startsWith(c.pmcDataset.url + "/?")     =>
      Right(fixture("pmc_dataset_list.xml").replace("PMC6091220", recordId(record)).getBytes("UTF-8"))
    case u if u.startsWith(c.pmcDataset.url + "/metadata/")        => Right(fixture(record).getBytes("UTF-8"))
    case u if u.startsWith(c.pmcDataset.url) && u.endsWith(".pdf") => pdf
    case u if u.startsWith(c.pmcDataset.url) && u.endsWith(".xml") => xml
    case u if u.startsWith("https://europepmc.org/")               =>
      Left(s"$u: HTTP 403 bot-protection challenge — treated as no copy")

  private def recordId(record: String) = if record.contains("manuscript") then "PMC6748862" else "PMC6091220"

  /** The record's MD5s are of the REAL files; the fake serves its own bytes, so swap in their MD5. */
  private def withMd5(record: String, bytes: Array[Byte], ext: String): String =
    val md5 = OaFetch.md5(bytes).getOrElse(fail("no MD5 in this runtime"))
    fixture(record).replaceAll("(\\." + ext + "\\?md5=)[0-9a-f]{32}", "$1" + md5)

  private def fetch(c: AcquireConfig, http: Http, doi: String, arxiv: Option[String] = None) =
    OaFetch(loaded(s"histmersion.acquire.contact-email = \"${c.contactEmail}\""), http).fetch(Some(doi), arxiv)

  private def loaded(text: String): com.histmersion.config.LoadedConfig =
    AppConfig.loadWithUserText(org.ekrich.config.ConfigFactory.empty, text).fold(e => fail(e), identity)

  test("Europe PMC's PDF link is a challenge page → the PMC dataset PDF, verified, provenance pmc"):
    val c = cfg
    val record = withMd5("pmc_dataset_oa.json", Pdf, "pdf")
    val http = FakeUpstream(
      c,
      route {
        case u if u.startsWith(c.pmcDataset.url + "/metadata/") => Right(record.getBytes("UTF-8"))
      }.orElse(routes(c, "europepmc_oa.json", "pmc_dataset_oa.json", Right(Pdf)))
    )
    val got =
      fetch(c, http, "10.1038/nature25778").fold(e => fail(e + "\n" + http.asked.map(_._1).mkString("\n")), identity)
    assertEquals(got.source, "pmc")
    assertEquals(got.extension, "pdf")
    assertEquals(got.url, Some(s"${c.pmcDataset.url}/PMC6091220.1/PMC6091220.1.pdf"))
    val urls = http.asked.map(_._1)
    assert(urls.exists(_.startsWith("https://europepmc.org/articles/PMC6091220?pdf=render")), urls.mkString("\n"))
    assert(
      urls.exists(u => u.contains("prefix=metadata%2FPMC6091220.") || u.contains("prefix=metadata/PMC6091220.")),
      urls.mkString("\n")
    )

  test("the PMC hosts see the project's public User-Agent and never the contact e-mail"):
    val c = cfg
    val http = FakeUpstream(c, routes(c, "europepmc_oa.json", "pmc_dataset_oa.json", Right(Pdf)))
    val _ = fetch(c, http, "10.1038/nature25778")
    val pmc = http.asked.filter((u, _) =>
      u.startsWith(c.europepmc.url) || u.startsWith(c.pmcDataset.url) ||
        u.startsWith("https://europepmc.org")
    )
    assert(pmc.nonEmpty)
    pmc.foreach { (u, h) =>
      assertEquals(h.get(Http.UserAgent), Some(c.publicUserAgent), u)
      assert(!u.contains("someone") && !u.contains("mailto") && !u.contains("email"), u)
    }
    assert(!c.publicUserAgent.contains("@"), c.publicUserAgent)

  test("Europe PMC's search asks for the exact DOI, core result type, JSON"):
    val c = cfg
    val http = FakeUpstream(c, routes(c, "europepmc_none.json", "pmc_dataset_oa.json", Right(Pdf)))
    assert(fetch(c, http, "10.15184/aqy.2017.21").isLeft)
    val search = http.asked.map(_._1).find(_.startsWith(c.europepmc.url)).getOrElse(fail("no search"))
    assert(
      search.contains("query=DOI:%2210.15184/aqy.2017.21%22") || search.contains(
        "query=DOI%3A%2210.15184%2Faqy.2017.21%22"
      ),
      search
    )
    assert(search.contains("resultType=core") && search.contains("format=json"), search)
    // no PMCID: the dataset is never asked
    assert(!http.asked.exists(_._1.startsWith(c.pmcDataset.url)), http.asked.mkString("\n"))

  test("a PDF whose MD5 does not match the record is not taken"):
    val c = cfg
    val http = FakeUpstream(c, routes(c, "europepmc_oa.json", "pmc_dataset_oa.json", Right(Pdf), xml = Left("gone")))
    assertEquals(fetch(c, http, "10.1038/nature25778").map(_.source), Left("no legal open-access PDF found"))

  test("an author manuscript with no PDF: arXiv is tried first, then the JATS full text is the copy"):
    val c = cfg
    val record = withMd5("pmc_dataset_manuscript.json", Xml, "xml")
    val http = FakeUpstream(
      c,
      route {
        case u if u.startsWith(c.pmcDataset.url + "/metadata/") => Right(record.getBytes("UTF-8"))
      }.orElse(routes(c, "europepmc_free.json", "pmc_dataset_manuscript.json", Left("no pdf")))
    )
    val got = fetch(c, http, "10.1126/science.aar7711", arxiv = Some("1805.00001")).fold(fail(_), identity)
    assertEquals(got.source, "pmc")
    assertEquals(got.extension, "xml")
    assertEquals(got.url, Some(s"${c.pmcDataset.url}/PMC6748862.1/PMC6748862.1.xml"))
    val urls = http.asked.map(_._1)
    val arxivAt = urls.indexWhere(_.startsWith(c.arxiv.url))
    val xmlAt = urls.indexWhere(_.endsWith(".xml"))
    assert(arxivAt >= 0 && arxivAt < xmlAt, urls.mkString("\n"))
    // the record and the search are asked once, shared by the PDF and the text steps
    assertEquals(urls.count(_.startsWith(c.europepmc.url)), 1)
    assertEquals(urls.count(_.startsWith(c.pmcDataset.url + "/metadata/")), 1)

  test("a dataset 'XML' that is not a JATS article is not taken"):
    val c = cfg
    val bogus = "<html>Just a moment...</html>".getBytes("UTF-8")
    val http = FakeUpstream(
      c,
      route {
        case u if u.startsWith(c.pmcDataset.url + "/metadata/") =>
          Right(
            fixture("pmc_dataset_manuscript.json")
              .replaceAll("md5=[0-9a-f]{32}", "md5=" + OaFetch.md5(bogus).get)
              .getBytes("UTF-8")
          )
      }.orElse(routes(c, "europepmc_free.json", "pmc_dataset_manuscript.json", Left("no pdf"), xml = Right(bogus)))
    )
    assert(fetch(c, http, "10.1126/science.aar7711").isLeft)

  test("an earlier source that answers wins: the PMC hosts are not asked"):
    val c = cfg
    val http = FakeUpstream(
      c,
      route {
        case u if u.startsWith(c.unpaywall.url) =>
          Right("""{"best_oa_location":{"url_for_pdf":"https://repo.example.org/a.pdf"}}""".getBytes("UTF-8"))
        case "https://repo.example.org/a.pdf" => Right(Pdf)
      }.orElse(routes(c, "europepmc_oa.json", "pmc_dataset_oa.json", Right(Pdf)))
    )
    assertEquals(fetch(c, http, "10.1038/nature25778").map(_.source), Right("unpaywall"))
    assert(!http.asked.exists(_._1.startsWith(c.europepmc.url)))

  test("the fetch step names the host a copy came from"):
    assertEquals(
      AcquireHandlers.hostOf(Some("https://pmc-oa-opendata.s3.amazonaws.com/PMC1.1/PMC1.1.pdf")),
      Some("pmc-oa-opendata.s3.amazonaws.com")
    )
    assertEquals(AcquireHandlers.hostOf(None), None)
    assertEquals(AcquireHandlers.hostOf(Some("not a url")), None)

  // ── the JATS original in the ingest job ───────────────────────────────────────────────────────

  final private class Sidecar extends ConverterClient:
    var calls: List[String] = Nil // a test double: which files reached the sidecar
    def convert(file: java.nio.file.Path, filename: String, quality: Boolean) =
      calls = calls :+ filename
      Right(ConvertedDoc("from the sidecar", pages = 3))
    def health = Right("ok")

  test("a JATS original is rendered to Markdown without the sidecar; anything else goes to it"):
    val dir = Files.createTempDirectory("jats-conv")
    try
      val xml = Files.write(dir.resolve("paper.xml"), Xml)
      val pdf = Files.write(dir.resolve("paper.pdf"), Pdf)
      val other = Files.write(dir.resolve("list.xml"), "<?xml version=\"1.0\"?><ListBucketResult/>".getBytes("UTF-8"))
      val side = Sidecar()
      val conv = JatsConverter(side)
      val doc = conv.convert(xml, "paper.xml", quality = false).fold(fail(_), identity)
      assert(doc.markdown.startsWith("# The first horse herders"), doc.markdown.take(200))
      assertEquals(doc.images, Nil)
      assertEquals(conv.convert(pdf, "paper.pdf", quality = false).map(_.markdown), Right("from the sidecar"))
      assertEquals(conv.convert(other, "list.xml", quality = false).map(_.markdown), Right("from the sidecar"))
      assertEquals(side.calls, List("paper.pdf", "list.xml"))
    finally
      Files.list(dir).forEach(Files.delete)
      Files.delete(dir)

  test("the configured converter is the JATS-aware one"):
    assert(ConverterClient.fromConfig(AppConfig.load().converter).isInstanceOf[JatsConverter])
