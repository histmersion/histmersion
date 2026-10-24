package com.histmersion.core

/** A gold open-access work whose publisher refuses scripts, as an acquisition item: which works qualify and
  * what the item says. */
class BrowserDownloadSuite extends munit.FunSuite:

  private val Refusing = List("degruyter.com", "mdpi.com", "stockholmuniversitypress.se")
  private val ck = Citekey.parse("greener2019_archaeology").toOption.get
  private val mdpi = WorkRef(doi = Some("10.3390/REL10040258"), title = Some("Archaeology and the Bible"), year = Some(2019),
    authors = List("Greener", "Smith"), isOa = true, oaStatus = Some("gold"),
    oaUrl = Some("https://www.mdpi.com/2077-1444/10/4/258/pdf"),
    oaLocations = List("https://www.mdpi.com/2077-1444/10/4/258", "https://www.mdpi.com/2077-1444/10/4/258/pdf"),
    oaLicence = Some("cc-by"))

  test("a gold work on a refusing host: the landing page, the DOI, the licence and the ingest command"):
    val b = BrowserDownload.of(mdpi, ck, Refusing).getOrElse(fail("not a browser download"))
    assertEquals(b.publisher.value, "mdpi.com")
    assertEquals(b.landing.value, "https://www.mdpi.com/2077-1444/10/4/258")
    assertEquals(b.text,
      "Greener et al. (2019). Archaeology and the Bible. https://doi.org/10.3390/rel10040258 — gold open access, licence cc-by, " +
        "at https://www.mdpi.com/2077-1444/10/4/258; mdpi.com refuses downloads by scripts: download it in the browser, " +
        "then histmersion ingest <file> --citekey greener2019_archaeology")
    assertEquals(Scheduling.dois(b.text), Set("10.3390/rel10040258"), "the DOI the acquisition list finds it by")

  test("diamond counts as gold; no licence, no title: the item says what it knows"):
    val w = mdpi.copy(oaStatus = Some("Diamond"), oaLicence = None, title = None, authors = Nil, year = None)
    val text = BrowserDownload.of(w, ck, Refusing).map(_.text).getOrElse(fail("not a browser download"))
    assert(text.startsWith("10.3390/rel10040258. https://doi.org/10.3390/rel10040258 — diamond open access, at https://www.mdpi.com/"), text)

  test("not a browser download: hybrid, green or bronze; no DOI; no open location on a refusing host; no hosts"):
    List("hybrid", "green", "bronze", "closed").foreach(s => assertEquals(BrowserDownload.of(mdpi.copy(oaStatus = Some(s)), ck, Refusing), None, s))
    assertEquals(BrowserDownload.of(mdpi.copy(oaStatus = None), ck, Refusing), None)
    assertEquals(BrowserDownload.of(mdpi.copy(doi = None), ck, Refusing), None)
    assertEquals(BrowserDownload.of(mdpi.copy(oaUrl = Some("https://doi.org/10.3390/rel10040258"),
      oaLocations = List("https://zenodo.org/records/1")), ck, Refusing), None)
    assertEquals(BrowserDownload.of(mdpi, ck, Nil), None)
    assertEquals(BrowserDownload.of(mdpi.copy(oaUrl = Some("https://notmdpi.com/x")) .copy(oaLocations = Nil), ck, Refusing), None,
      "a host matches itself and its subdomains only")

  test("a refusing host found among later locations; the index's oa_url alone is enough"):
    val sup = WorkRef(doi = Some("10.16993/bbk"), title = Some("Book"), oaStatus = Some("gold"),
      oaLocations = List("https://doi.org/10.16993/bbk", "https://www.stockholmuniversitypress.se/site/books/m/10.16993/bbk/"))
    assertEquals(BrowserDownload.of(sup, ck, Refusing).map(b => b.landing.value: String), Some("https://www.stockholmuniversitypress.se/site/books/m/10.16993/bbk/"))
    val dg = WorkRef(doi = Some("10.1515/zzz-2020-0001"), title = Some("Article"), oaStatus = Some("gold"),
      oaUrl = Some("https://www.degruyter.com/document/doi/10.1515/zzz-2020-0001/pdf"))
    assertEquals(BrowserDownload.of(dg, ck, Refusing).map(b => b.publisher.value: String), Some("degruyter.com"))
