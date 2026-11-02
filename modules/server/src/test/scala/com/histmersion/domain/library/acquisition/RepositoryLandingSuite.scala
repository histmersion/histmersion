package com.histmersion.domain.library.acquisition

import com.histmersion.config.AppConfig
import com.histmersion.upstreams.landing.RepositoryLanding

import io.circe.parser.parse

/** Repository landing pages (#1580): which pages are a repository's, what their tags name, Zenodo's files. Pure; the
  * recorded pages and records are exercised end to end in the server's `LandingFetchSuite`.
  */
class RepositoryLandingSuite extends munit.FunSuite:
  import RepositoryLanding.*

  private val rules = AppConfig.load().acquire.landing.rules

  test("host: lower case, no www, http(s) only"):
    assertEquals(host("https://WWW.Repository.cam.ac.uk/handle/1810/265228"), Some("repository.cam.ac.uk"))
    assertEquals(host("http://zenodo.org:443/record/1"), Some("zenodo.org"))
    assertEquals(host("ftp://x.org/a"), None)
    assertEquals(host("not a url"), None)

  test("meta tags: any attribute order, either quote, self-closing or not, entities decoded"):
    val html = """<head><meta name="Generator" content="DSpace 6.3" />
      |<meta content="https://x.uni-a.de/bitstream/1/a.pdf?seq=1&amp;x=2" name="citation_pdf_url">
      |<meta property='og:title' content='T'><META NAME=citation_title CONTENT="Title"></head>""".stripMargin
    assertEquals(
      metaTags(html),
      List(
        "generator" -> "DSpace 6.3",
        "citation_pdf_url" -> "https://x.uni-a.de/bitstream/1/a.pdf?seq=1&x=2",
        "og:title" -> "T",
        "citation_title" -> "Title"
      )
    )
    assertEquals(generators(html), List("DSpace 6.3"))

  test("citation_pdf_url is made absolute against the page; other schemes are dropped"):
    def pdfs(href: String) =
      citationPdfUrls(s"""<meta name="citation_pdf_url" content="$href">""", "https://eprints.x.ac.uk/123/")
    assertEquals(pdfs("/123/1/paper.pdf"), List("https://eprints.x.ac.uk/123/1/paper.pdf"))
    assertEquals(pdfs("1/paper.pdf"), List("https://eprints.x.ac.uk/123/1/paper.pdf"))
    assertEquals(pdfs("//cdn.x.ac.uk/p.pdf"), List("https://cdn.x.ac.uk/p.pdf"))
    assertEquals(pdfs("javascript:alert(1)"), Nil)
    assertEquals(pdfs(""), Nil)

  test("a repository: the index says so, an allowed host, an academic host, or a repository platform"):
    val plain = "<html><head></head></html>"
    assert(isRepository("https://zenodo.org/record/1", false, plain, rules))
    assert(isRepository("https://researchprofiles.ku.dk/en/publications/x", false, plain, rules))
    assert(isRepository("https://www.repository.cam.ac.uk/items/1", false, plain, rules), "a .ac.uk host")
    assert(isRepository("https://scholarworks.umass.edu/x", false, plain, rules), "a .edu host")
    assert(isRepository("https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561", false, plain, rules))
    assert(isRepository("https://depot.example.pl/handle/1", true, plain, rules), "the index's word")
    assert(
      isRepository(
        "https://archive.example.org/item/1",
        false,
        """<meta name="generator" content="EPrints 3.4.3">""",
        rules
      ),
      "a repository platform's generator"
    )
    assert(!isRepository("https://blog.example.com/post", false, plain, rules), "nothing says repository")

  test("never a publisher: a denied host wins over the index, the host and the generator"):
    val dspace = """<meta name="generator" content="DSpace 7">"""
    assert(!isRepository("https://www.tandfonline.com/doi/full/10.1080/x", true, dspace, rules))
    assert(
      !isRepository("https://www.academia.edu/24835567/x", true, "", rules),
      "OpenAlex calls academia.edu a repository"
    )
    assert(!isRepository("https://journals.uchicago.edu/doi/10.1086/x", false, "", rules), "a press on a .edu")
    assert(!isRepository("https://press.princeton.edu/books/x", false, "", rules))
    assert(
      !isRepository(
        "https://sa.example-uni.edu/index.php/sa/article/view/12",
        false,
        """<meta name="generator" content="Open Journal Systems 3.3.0.8">""",
        rules
      ),
      "a journal platform"
    )
    assert(!isRepository("https://www.cambridge.org/core/x", false, "", rules))
    // a repository page pointing at a publisher's PDF: that PDF is not taken
    val off = """<meta name="citation_pdf_url" content="https://link.springer.com/content/pdf/10.1007/x.pdf">"""
    assertEquals(pdfsOf("https://repository.example.ac.uk/1", true, off, rules), Nil)

  test("host words are whole labels (or a prefix ending in '-'), never part of a longer word"):
    val r = Rules(Nil, Nil, List("uni-", "pure", "eprints", "opus"), Nil, denyHostWords = List("press"))
    assert(r.academic("publications.uni-tuebingen.de"))
    assert(r.academic("pure.mpg.de") && r.academic("opus4.kobv.de") && r.academic("pure-portal.example.dk"))
    assert(!r.academic("purepharma.com") && !r.academic("eprintsandmore.com"))
    assert(!r.academic("x.example.com"))
    assert(r.denied("press.example.edu") && !r.denied("pressreader.com"))
    assert(wordLabel("uni-koeln", "uni-") && !wordLabel("unicorn", "uni-"))
    assert(r.academic("x.ac.at") && r.academic("x.edu") && r.academic("x.edu.au"))

  test("candidates: OpenAlex's landing pages and record-page pdf_urls, repositories marked, distinct, best first"):
    val j = parse(
      """{"best_oa_location":{"landing_page_url":"https://zenodo.org/record/3695551","pdf_url":"https://zenodo.org/record/3695551","source":{"type":"repository"}},
      "locations":[{"landing_page_url":"https://zenodo.org/record/3695551","pdf_url":"https://zenodo.org/record/3695551","source":{"type":"repository"}},
        {"landing_page_url":"https://doi.org/10.5281/zenodo.3695551","pdf_url":null,"source":{"type":"repository"}},
        {"landing_page_url":"https://publisher.example.com/a","pdf_url":"https://publisher.example.com/a.pdf","source":{"type":"journal"}}]}"""
    ).toOption.get
    assertEquals(
      openalexCandidates(j),
      List(
        Candidate("https://zenodo.org/record/3695551", true),
        Candidate("https://doi.org/10.5281/zenodo.3695551", true),
        Candidate("https://publisher.example.com/a", false, publisher = true)
      )
    )
    val u = parse(
      """{"best_oa_location":{"url_for_pdf":null,"url_for_landing_page":"https://eprints.x.ac.uk/1/","host_type":"repository"},
      "oa_locations":[{"url_for_pdf":"https://eprints.x.ac.uk/1/1/a.pdf","url_for_landing_page":"https://eprints.x.ac.uk/1/","host_type":"repository"}]}"""
    ).toOption.get
    assertEquals(unpaywallCandidates(u), List(Candidate("https://eprints.x.ac.uk/1/", true, open = true)))
    val p = parse(
      """{"oa_locations":[{"url_for_landing_page":"https://pub.example.com/a","host_type":"publisher"}]}"""
    ).toOption.get
    assertEquals(
      unpaywallCandidates(p),
      List(Candidate("https://pub.example.com/a", false, publisher = true, open = true))
    )
    // one index's "repository" outweighs another's "publisher" for the same URL
    assertEquals(
      distinct(List(Candidate("https://x.org/1", false, true), Candidate("https://x.org/1", true))),
      List(Candidate("https://x.org/1", true, false))
    )

  test("open locations (#2550): OpenAlex's is_oa, every Unpaywall oa_location; any index's word makes it open"):
    val j =
      parse("""{"locations":[{"is_oa":true,"landing_page_url":"https://ejournals.eu/a","source":{"type":"journal"}},
      {"is_oa":false,"landing_page_url":"https://ejournals.eu/b","source":{"type":"journal"}},
      {"landing_page_url":"https://ejournals.eu/c","source":{"type":"journal"}}]}""").toOption.get
    assertEquals(
      openalexCandidates(j).map(c => c.url.takeRight(1) -> c.open),
      List("a" -> true, "b" -> false, "c" -> false)
    )
    assertEquals(
      distinct(List(Candidate("https://x.org/1", false, true), Candidate("https://x.org/1", false, true, open = true))),
      List(Candidate("https://x.org/1", false, true, open = true))
    )

  test("an OA publisher's page: read only when open and on the list; its own PDFs only"):
    assert(rules.oaPublisher("ejournals.eu") && rules.oaPublisher("www.ejournals.eu") && rules.oaPublisher("ore.eu"))
    assert(
      !rules.oaPublisher("brill.com") && !rules.oaPublisher("mdpi.com") && !rules.oaPublisher("ore.eu.example.com")
    )
    assert(oaPublisherPage(Candidate("https://ejournals.eu/a", false, publisher = true, open = true), rules))
    assert(!oaPublisherPage(Candidate("https://ejournals.eu/a", false, publisher = true), rules), "not said to be open")
    assert(
      !oaPublisherPage(Candidate("https://brill.com/a", false, publisher = true, open = true), rules),
      "not on the list"
    )
    val page = "https://ejournals.eu/czasopismo/x/artykul/y"
    val html =
      """<meta name="citation_pdf_url" content="https://ejournals.eu/pliki_artykulu_czasopisma/pelny_tekst/u/pobierz">
      |<meta name="citation_pdf_url" content="https://cdn.elsewhere.example/u.pdf">""".stripMargin
    assertEquals(
      pdfsOf(page, false, html, rules, oaPublisher = true),
      List("https://ejournals.eu/pliki_artykulu_czasopisma/pelny_tekst/u/pobierz"),
      "another host's PDF is not taken"
    )
    assertEquals(pdfsOf(page, false, html, rules), Nil, "without the OA publisher's word it is no repository")

  test(
    "OJS PDF galleys: the classic galley link and an OJS 3.6 theme's escaped JSON; a viewer link becomes its download"
  ):
    val classic =
      """<ul class="galleys_links"><li><a class="obj_galley_link pdf" href="https://j.example.org/index.php/j/article/view/12/34">PDF</a></li>
      |<li><a class="obj_galley_link file" href="https://j.example.org/index.php/j/article/view/12/35">XML</a></li></ul>""".stripMargin
    assertEquals(
      galleyPdfUrls(classic, "https://j.example.org/index.php/j/article/view/12"),
      List("https://j.example.org/index.php/j/article/view/12/34")
    )
    val ore =
      """<pkp-dropdown-menu :items="JSON.parse('[{\u0022name\u0022:\u0022galley-3617\u0022,\u0022label\u0022:\u0022PDF\u0022,\u0022href\u0022:\u0022https:\\\/\\\/ore.eu\\\/ore\\\/article\\\/view\\\/1056\\\/3617\u0022},{\u0022name\u0022:\u0022galley-3618\u0022,\u0022label\u0022:\u0022HTML\u0022,\u0022href\u0022:\u0022https:\\\/\\\/ore.eu\\\/ore\\\/article\\\/view\\\/1056\\\/3618\u0022}]')">"""
    assertEquals(
      galleyPdfUrls(ore, "https://ore.eu/ore/article/view/1056/version/1810"),
      List("https://ore.eu/ore/article/view/1056/3617")
    )
    assertEquals(
      ojsDownload("https://ore.eu/ore/article/view/1056/3617"),
      "https://ore.eu/ore/article/download/1056/3617"
    )
    assertEquals(
      ojsDownload("https://ore.eu/ore/article/view/1056/version/1810"),
      "https://ore.eu/ore/article/view/1056/version/1810",
      "an article's own page is not a galley"
    )
    assertEquals(
      pdfsOf("https://ore.eu/ore/article/view/1056/version/1810", false, ore, rules, oaPublisher = true),
      List("https://ore.eu/ore/article/download/1056/3617")
    )

  test("the DOI handle API: the first URL value"):
    assertEquals(
      handleUrl(
        parse(
          """{"responseCode":1,"values":[{"type":"HS_ADMIN","data":{}},
      {"type":"URL","data":{"format":"string","value":"https://ore.eu/ore/article/view/1056/version/1810"}}]}"""
        ).toOption.get
      ),
      Some("https://ore.eu/ore/article/view/1056/version/1810")
    )
    assertEquals(handleUrl(parse("""{"responseCode":100,"values":[]}""").toOption.get), None)

  test("Zenodo: a record id from a record URL or a Zenodo DOI; an open record's PDFs with their MD5"):
    assertEquals(zenodoRecordId("https://zenodo.org/record/3695551"), Some("3695551"))
    assertEquals(zenodoRecordId("https://zenodo.org/records/3695551?preview=1"), Some("3695551"))
    assertEquals(zenodoRecordId("10.5281/zenodo.3695551"), Some("3695551"))
    assertEquals(zenodoRecordId("https://doi.org/10.5281/zenodo.3695550"), Some("3695550"))
    assertEquals(zenodoRecordId("https://figshare.com/articles/1"), None)
    val rec = """{"metadata":{"access_right":"%s"},"files":[
      {"key":"Oreshko Ahhiyawa-Danuna-offprint.pdf","checksum":"md5:B0FEE7A942761B0DEDD5F16CB10B01E9",
       "links":{"self":"https://zenodo.org/api/records/3695551/files/Oreshko%%20Ahhiyawa-Danuna-offprint.pdf/content"}},
      {"key":"data.csv","links":{"self":"https://zenodo.org/api/records/3695551/files/data.csv/content"}}]}"""
    assertEquals(
      zenodoFiles(parse(rec.format("open")).toOption.get),
      List(
        ZenodoFile(
          "https://zenodo.org/api/records/3695551/files/Oreshko%20Ahhiyawa-Danuna-offprint.pdf/content",
          Some("b0fee7a942761b0dedd5f16cb10b01e9")
        )
      )
    )
    assertEquals(zenodoFiles(parse(rec.format("restricted")).toOption.get), Nil)
    assertEquals(zenodoFiles(parse(rec.format("embargoed")).toOption.get), Nil)
    assertEquals(
      zenodoFiles(parse("""{"files":[{"key":"a.pdf","links":{"self":"https://z/a"}}]}""").toOption.get),
      Nil,
      "no access stated: not taken"
    )
    assertEquals(
      zenodoFiles(
        parse("""{"access":{"files":"public"},"files":[{"key":"a.PDF","links":{"self":"https://z/a"}}]}""").toOption.get
      ),
      List(ZenodoFile("https://z/a", None))
    )

  test("DataCite: where a DOI resolves"):
    assertEquals(
      dataciteUrl(
        parse(
          """{"data":{"attributes":{"url":"https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561"}}}"""
        ).toOption.get
      ),
      Some("https://publikationen.uni-tuebingen.de/xmlui/handle/10900/73561")
    )
    assertEquals(dataciteUrl(parse("""{"errors":[{"status":"404"}]}""").toOption.get), None)

  test("an HTML answer is told from other bytes"):
    assert(looksLikeHtml("<!DOCTYPE html><html>".getBytes("UTF-8")))
    assert(looksLikeHtml("  <html lang=en>".getBytes("UTF-8")))
    assert(!looksLikeHtml("PK\u0003\u0004 zip".getBytes("UTF-8")))

  private def env(kv: (String, String)*): org.ekrich.config.Config =
    org.ekrich.config.ConfigFactory.parseMap(scala.jdk.CollectionConverters.MapHasAsJava(kv.toMap).asJava)

  private def landingConf(text: String): java.nio.file.Path =
    val d = java.nio.file.Files.createTempDirectory("histmersion-conf")
    java.nio.file.Files.writeString(d.resolve("histmersion.conf"), text)

  test("user file: deny-hosts-extra adds to the shipped deny-hosts instead of replacing them"):
    val f = landingConf("histmersion.acquire.landing.deny-hosts-extra = \"blocked.example, .Other.Example\"")
    val rules = AppConfig.loadWithUserFile(env(), Some(f)).fold(m => fail(m), identity).config.acquire.landing.rules
    assert(rules.denied("blocked.example") && rules.denied("www.other.example"), "the added hosts are denied")
    assert(rules.denied("sciencedirect.com"), "a shipped host is still denied")
    assertEquals(rules.denyHosts.size, AppConfig.load().acquire.landing.rules.denyHosts.size + 2)
    assert(!AppConfig.load().acquire.landing.rules.denied("blocked.example"), "nothing is added by default")
    val viaEnv = AppConfig.load(env("HISTMERSION_ACQUIRE_LANDING_DENY_HOSTS_EXTRA" -> "blocked.example"))
    assert(viaEnv.acquire.landing.rules.denied("blocked.example"), "the env override works too")
