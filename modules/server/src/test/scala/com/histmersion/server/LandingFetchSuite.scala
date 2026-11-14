package com.histmersion
package server
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.pluginhost.{Challenge, HttpAnswer, OaFetcher}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.acquisition.*

/** Repository landing pages in the OA chain (#1580), against a fake upstream answering from records and pages captured
  * on 2026-10-16 (`acquire/landing/`; the publisher, journal and challenge pages are synthetic and say so) — no
  * network. Zenodo by its API; Apollo (DSpace 8) and TOBIAS-lib (DSpace 6, a DataCite DOI) by `citation_pdf_url`; a
  * publisher's page never read for one.
  */
class LandingFetchSuite extends munit.FunSuite:

  private def fixture(name: String) = scala.io.Source.fromResource(s"acquire/landing/$name").mkString

  private val Pdf: Array[Byte] = ("%PDF-1.5\n" + ("x" * 4096)).getBytes("ISO-8859-1")

  /** Answers by URL, records every request. */
  final private class FakeUpstream(routes: PartialFunction[String, Either[String, Array[Byte]]]) extends Http:
    var asked: List[String] = Nil // a test double: the simplest record of calls
    private def answer(url: Http.Url): Either[Problem, Array[Byte]] =
      asked = asked :+ url.value
      routes
        .applyOrElse(url.value, (u: String) => Left(s"$u: HTTP 404"))
        .left
        .map(why => Problem.Unclassified(Legacy.Text(why)))
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      answer(url).map(HttpAnswer.Body(_))
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      answer(url).map(b => Http.Text(new String(b, "UTF-8")))
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Problem, Json] =
      text(url, headers).flatMap(s =>
        RawJson
          .parse(s.value)
          .left
          .map(e => Problem.Unclassified(Legacy.Text(e)))
      )

  private def cfg = AppConfig.load().acquire

  private def bytes(s: String) = Right(s.getBytes("UTF-8"))

  private def openalexOf(c: AcquireConfig, doi: Doi) = s"${c.openalex.url}/works/https://doi.org/$doi"

  private def fetch(c: AcquireConfig, http: Http, doi: Doi, settings: String = "") =
    OaFetch(loaded(settings), http, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
      .fetch(Some(doi), None)

  private def loaded(text: String): LoadedConfig =
    AppConfig
      .loadWithUserText(org.ekrich.config.ConfigFactory.empty, text)
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  private def fail2(http: FakeUpstream)(e: Problems) = fail(
    e.sentences.mkString("; ") + "\n" + http.asked.mkString("\n")
  )

  test("Zenodo: the record page OpenAlex gives as the PDF is resolved by the API to the record's open PDF"):
    val c = cfg
    val md5 = OaFetch.md5(OaFetch.Bytes(Pdf)).getOrElse(fail("no MD5 in this runtime"))
    val record = fixture("zenodo_record_3695551.json").replaceAll("md5:[0-9a-f]{32}", s"md5:$md5")
    val file = "https://zenodo.org/api/records/3695551/files/Oreshko%20Ahhiyawa-Danuna-offprint.pdf/content"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, Doi("10.5281/zenodo.3695550"))) =>
        bytes(fixture("openalex_10_5281_zenodo_3695550.json"))
      case "https://zenodo.org/record/3695551"              => bytes("<!DOCTYPE html><html><head></head></html>")
      case u if u == s"${c.zenodo.url}/api/records/3695551" => bytes(record)
      case `file`                                           => Right(Pdf)
    }
    val got = fetch(c, http, Doi("10.5281/zenodo.3695550")).fold(fail2(http), identity)
    assertEquals((got.source, got.url), ("zenodo", Some(file)))
    // the concept DOI's record (3695550) was asked first and is not there; DataCite was not needed
    assert(http.asked.contains(s"${c.zenodo.url}/api/records/3695550"), http.asked.mkString("\n"))
    assert(!http.asked.exists(_.startsWith(c.datacite.url.value)), http.asked.mkString("\n"))

  test("Zenodo: a PDF that does not match the record's MD5, or a restricted record, is no copy"):
    val c = cfg
    val restricted =
      fixture("zenodo_record_3695551.json").replace("\"access_right\": \"open\"", "\"access_right\": \"restricted\"")
    def run(record: String) =
      val http = FakeUpstream {
        case u if u.startsWith(openalexOf(c, Doi("10.5281/zenodo.3695550"))) =>
          bytes(fixture("openalex_10_5281_zenodo_3695550.json"))
        case u if u == s"${c.zenodo.url}/api/records/3695551" => bytes(record)
        case u if u.contains("/files/")                       => Right(Pdf)
      }
      (fetch(c, http, Doi("10.5281/zenodo.3695550")), http)
    assert(run(fixture("zenodo_record_3695551.json"))._1.isLeft, "the recorded MD5 is the real file's, not the fake's")
    val (r, http) = run(restricted)
    assert(r.isLeft)
    assert(!http.asked.exists(_.contains("/files/")), "a restricted record's file is never asked for")

  test("Apollo (DSpace 8): OpenAlex's repository handle page names its citation_pdf_url; the bitstream is the copy"):
    val c = cfg
    val page = "https://www.repository.cam.ac.uk/handle/1810/265228"
    val pdf = "https://www.repository.cam.ac.uk/bitstreams/635fb0e4-fed6-4dd0-a369-16067170b87f/download"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, Doi("10.17863/CAM.11325"))) =>
        bytes(fixture("openalex_10_17863_CAM_11325.json"))
      case `page` => bytes(fixture("apollo_cam_item.html"))
      case `pdf`  => Right(Pdf)
    }
    val got = fetch(c, http, Doi("10.17863/CAM.11325")).fold(fail2(http), identity)
    assertEquals((got.source, got.url, got.extension), ("repository", Some(pdf), "pdf"))

  test("TOBIAS-lib (DSpace 6): a DataCite DOI's doi.org location is resolved through DataCite, never followed blind"):
    val c = cfg
    val doi = Doi("10.15496/publikation-14969")
    val page = "https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561"
    val pdf = "https://publikationen.uni-tuebingen.de/xmlui/bitstream/10900/73561/1/StTrMonograph5_Becks_Blum.pdf"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi))    => bytes(fixture("openalex_10_15496_publikation-14969.json"))
      case u if u == s"${c.datacite.url}/dois/$doi" => bytes(fixture("datacite_10_15496_publikation-14969.json"))
      case `page`                                   => bytes(fixture("tobias_lib_tuebingen_item.html"))
      case `pdf`                                    => Right(Pdf)
    }
    val got = fetch(c, http, doi).fold(fail2(http), identity)
    assertEquals((got.source, got.url), ("repository", Some(pdf)))
    // only the direct step's `oa_url` is the DOI link; the landing step resolves it through DataCite
    assertEquals(http.asked.count(_.startsWith("https://doi.org/")), 1, http.asked.mkString("\n"))
    assert(
      http.asked.indexWhere(_.startsWith(c.datacite.url.value)) < http.asked.indexOf(page),
      http.asked.mkString("\n")
    )
    assertEquals(http.asked.count(_.startsWith(c.datacite.url.value)), 1, "DataCite is asked once per fetch")

  test("a DOI known only to DataCite (OpenAlex has nothing) still reaches its repository page"):
    val c = cfg
    val doi = Doi("10.15496/publikation-14969")
    val http = FakeUpstream {
      case u if u == s"${c.datacite.url}/dois/$doi" => bytes(fixture("datacite_10_15496_publikation-14969.json"))
      case "https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561" =>
        bytes(fixture("tobias_lib_tuebingen_item.html"))
      case u if u.endsWith("StTrMonograph5_Becks_Blum.pdf") => Right(Pdf)
    }
    assertEquals(fetch(c, http, doi).map(_.source), Right("repository"))

  test(
    "never a publisher's page: a denied host or a journal location is not even read; an unknown host's citation_pdf_url is not taken"
  ):
    val c = cfg
    val doi = Doi("10.1080/00000000.2020.0000000")
    val work = s"""{"id":"https://openalex.org/W1","doi":"https://doi.org/$doi","locations":[
      {"landing_page_url":"https://www.tandfonline.com/doi/full/$doi","pdf_url":null,"source":{"type":"journal"}},
      {"landing_page_url":"https://www.tandfonline.com/doi/abs/$doi","pdf_url":null,"source":null},
      {"landing_page_url":"https://journal.example.org/a/1","pdf_url":null,"source":{"type":"journal"}},
      {"landing_page_url":"https://articles.example-publisher.com/a/1","pdf_url":null,"source":null},
      {"landing_page_url":"https://sa.example-uni.edu/index.php/sa/article/view/12","pdf_url":null,"source":null}]}"""
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi))        => bytes(work)
      case "https://articles.example-publisher.com/a/1" => bytes(fixture("publisher_article_synthetic.html"))
      case "https://sa.example-uni.edu/index.php/sa/article/view/12" => bytes(fixture("ojs_journal_synthetic.html"))
      case u if u.contains("/pdf/") || u.contains("/download/")      => Right(Pdf)
    }
    assertEquals(fetch(c, http, doi).left.map(_.toVector.map(_.isNotFound)), Left(Vector(true)))
    assert(
      !http.asked.exists(u => u.contains("tandfonline.com") || u.contains("journal.example.org")),
      http.asked.mkString("\n")
    )
    // the two pages with no word from the index were read, and neither gave up its PDF
    assert(
      http.asked.contains("https://articles.example-publisher.com/a/1") &&
        http.asked.contains("https://sa.example-uni.edu/index.php/sa/article/view/12"),
      http.asked.mkString("\n")
    )
    assert(!http.asked.exists(u => u.contains("/pdf/") || u.contains("/download/")), http.asked.mkString("\n"))

  // ── open-access publishers (#2550): pages and records captured 2026-10-23, trimmed ─────────────

  test("Open Research Europe (OJS 3.6): its doi.org location is resolved by the handle API, its PDF galley downloaded"):
    val c = cfg
    val doi = Doi("10.12688/openreseurope.20351.3")
    val page = "https://ore.eu/ore/article/view/1056/version/1810"
    val pdf = "https://ore.eu/ore/article/download/1056/3617"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi)) => bytes(fixture("openalex_10_12688_openreseurope.20351.3.json"))
      case u if u == s"${c.doiHandles.url}/$doi?type=URL" =>
        bytes(fixture("doi_handle_10_12688_openreseurope.20351.3.json"))
      case `page` => bytes(fixture("ore_article.html"))
      case `pdf`  => Right(Pdf)
    }
    val got = fetch(c, http, doi).fold(fail2(http), identity)
    assertEquals((got.source, got.url), ("publisher", Some(pdf)))
    // the viewer page (article/view/1056/3617) is never asked: the galley's download is
    assert(!http.asked.contains("https://ore.eu/ore/article/view/1056/3617"), http.asked.mkString("\n"))

  test("ejournals.eu: an open journal location's article page gives its citation_pdf_url"):
    val c = cfg
    val doi = Doi("10.4467/20844077sr.17.018.8459")
    val page =
      "https://ejournals.eu/czasopismo/studia-religiologica/artykul/descents-to-the-underworld-from-gilgamesh-to-christian-late-antiquity"
    val pdf = "https://ejournals.eu/pliki_artykulu_czasopisma/pelny_tekst/172279c9-e459-4289-b666-c8a869752e67/pobierz"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi)) => bytes(fixture("openalex_10_4467_20844077sr.17.018.8459.json"))
      case u if u == s"${c.doiHandles.url}/$doi?type=URL" =>
        bytes(fixture("doi_handle_10_4467_20844077sr.17.018.8459.json"))
      case `page` => bytes(fixture("ejournals_article.html"))
      case `pdf`  => Right(Pdf)
      // the index's old pdf_url no longer answers here (the direct step's miss)
    }
    val got = fetch(c, http, doi).fold(fail2(http), identity)
    assertEquals((got.source, got.url), ("publisher", Some(pdf)))

  test("an OA publisher's location is read only when open, and a host off the list never, open or not"):
    val c = cfg
    val doi = Doi("10.9999/zz-oa-pub")
    def run(isOa: Boolean, landing: String) =
      val work = s"""{"id":"https://openalex.org/W2","doi":"https://doi.org/$doi","locations":[
        {"is_oa":$isOa,"landing_page_url":"$landing","pdf_url":null,"source":{"type":"journal"}}]}"""
      val http = FakeUpstream {
        case u if u.startsWith(openalexOf(c, doi))                                            => bytes(work)
        case u if u.startsWith("https://ejournals.eu/") || u.startsWith("https://brill.com/") =>
          bytes(fixture("ejournals_article.html"))
      }
      (fetch(c, http, doi), http.asked)
    val (closed, askedClosed) = run(isOa = false, "https://ejournals.eu/czasopismo/x/artykul/y")
    assert(closed.isLeft && !askedClosed.exists(_.startsWith("https://ejournals.eu/")), askedClosed.mkString("\n"))
    val (offList, askedOff) = run(isOa = true, "https://brill.com/view/journals/x/1/1/article-p1_1.xml")
    assert(offList.isLeft && !askedOff.exists(_.startsWith("https://brill.com/")), askedOff.mkString("\n"))
    // and a DOI-only open location whose handle points off the list is not followed
    val handleOff = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi)) =>
        bytes(
          s"""{"id":"https://openalex.org/W2","locations":[{"is_oa":true,"landing_page_url":"https://doi.org/$doi","pdf_url":null,"source":{"type":"journal"}}]}"""
        )
      case u if u.startsWith(c.doiHandles.url.value) =>
        bytes(
          s"""{"responseCode":1,"values":[{"type":"URL","data":{"value":"https://www.mdpi.com/2077-1444/10/4/258"}}]}"""
        )
    }
    assert(fetch(c, handleOff, doi).isLeft)
    assert(!handleOff.asked.exists(_.contains("mdpi.com")), handleOff.asked.mkString("\n"))

  test("bot protection is no copy: a challenged page is given up, nothing behind it is tried"):
    val c = cfg
    val page = "https://www.repository.cam.ac.uk/handle/1810/265228"
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, Doi("10.17863/CAM.11325"))) =>
        bytes(fixture("openalex_10_17863_CAM_11325.json"))
      case `page` => Left(s"$page: HTTP 403 bot-protection challenge — treated as no copy")
    }
    assertEquals(
      fetch(c, http, Doi("10.17863/CAM.11325")).left.map(_.sentences.mkString("; ")),
      Left("no legal open-access PDF found for doi:10.17863/CAM.11325")
    )
    assert(!http.asked.exists(_.contains("/bitstreams/")), http.asked.mkString("\n"))
    assert(
      Challenge.isChallenge(
        HttpPolicy.StatusCode(403),
        Map.empty,
        Challenge.Body(fixture("challenge_synthetic.html").getBytes("UTF-8"))
      ),
      "the fixture is what the layer refuses"
    )

  test("at most max-pages landing pages are read, and none when the step is switched off"):
    val c = cfg
    val doi = Doi("10.9999/many")
    val locs = (1 to 9).map(i =>
      s"""{"landing_page_url":"https://eprints.example$i.ac.uk/$i/","pdf_url":null,"source":{"type":"repository"}}"""
    )
    val http = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi))        => bytes(s"""{"locations":[${locs.mkString(",")}]}""")
      case u if u.startsWith("https://eprints.example") => bytes("<html><head></head></html>")
    }
    assert(fetch(c, http, doi, "histmersion.acquire.landing.max-pages = 3").isLeft)
    assertEquals(http.asked.count(_.startsWith("https://eprints.example")), 3)
    val off = FakeUpstream {
      case u if u.startsWith(openalexOf(c, doi)) => bytes(s"""{"locations":[${locs.mkString(",")}]}""")
    }
    assert(fetch(c, off, doi, "histmersion.acquire.landing.enabled = false").isLeft)
    assert(
      !off.asked.exists(u => u.startsWith("https://eprints.example") || u.startsWith(c.datacite.url.value)),
      off.asked.mkString("\n")
    )
