package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.WorkRef

/** A gold open-access work whose publisher refuses scripts, as an acquisition item: which works qualify and what the
  * item says.
  */
class BrowserDownloadSuite extends munit.FunSuite:

  private val RefusingHosts = List("degruyter.com", "mdpi.com", "stockholmuniversitypress.se")
    .map(com.histmersion.upstreams.landing.RepositoryLanding.HostPattern(_))
  private def refusing(
      hosts: List[com.histmersion.upstreams.landing.RepositoryLanding.HostPattern]
  ): BrowserDownload.Location => Option[BrowserDownload.Host] =
    location =>
      com.histmersion.upstreams.landing.RepositoryLanding
        .onHost(com.histmersion.upstreams.landing.RepositoryLanding.OnHostUrl(location.value), hosts)
        .map(host => BrowserDownload.Host(host.value))
  private val Refusing: BrowserDownload.Location => Option[BrowserDownload.Host] = refusing(RefusingHosts)
  private val ck = Citekey.parse("greener2019_archaeology").toOption.get
  private val mdpi = WorkRef(
    doi = (Some("10.3390/REL10040258")).map(com.histmersion.api.library.Doi.apply),
    title = (Some("Archaeology and the Bible")).map(com.histmersion.api.library.Title.apply),
    year = (Some(2019)).map(com.histmersion.api.catalogue.Year.apply),
    authors = (List("Greener", "Smith")).map(com.histmersion.api.library.AuthorName.apply),
    isOa = com.histmersion.api.library.WorkRef.IsOa(true),
    oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("gold")),
    oaUrl = Some(com.histmersion.api.library.WorkRef.OaUrl("https://www.mdpi.com/2077-1444/10/4/258/pdf")),
    oaLocations = List(com.histmersion.api.library.WorkRef.OaLocations("https://www.mdpi.com/2077-1444/10/4/258"), com.histmersion.api.library.WorkRef.OaLocations("https://www.mdpi.com/2077-1444/10/4/258/pdf")),
    oaLicence = Some(com.histmersion.api.library.WorkRef.OaLicence("cc-by"))
  )

  test("a gold work on a refusing host: the landing page, the DOI, the licence and the ingest command"):
    val b = BrowserDownload.of(mdpi, ck, Refusing).getOrElse(fail("not a browser download"))
    assertEquals(b.publisher.value, "mdpi.com")
    assertEquals(b.landing.value, "https://www.mdpi.com/2077-1444/10/4/258")
    assertEquals(
      b.text,
      "Greener et al. (2019). Archaeology and the Bible. https://doi.org/10.3390/rel10040258 — gold open access, licence cc-by, " +
        "at https://www.mdpi.com/2077-1444/10/4/258; mdpi.com refuses downloads by scripts: download it in the browser, " +
        "then histmersion ingest <file> --citekey greener2019_archaeology"
    )
    assertEquals(Scheduling.dois(b.text), Set("10.3390/rel10040258"), "the DOI the acquisition list finds it by")

  test("diamond counts as gold; no licence, no title: the item says what it knows"):
    val w = mdpi.modify(_.oaStatus).setTo(Some("Diamond")).modify(_.oaLicence).setTo(None).modify(_.title).setTo(None).modify(_.authors).setTo(Nil).modify(_.year).setTo(None)
    val text = BrowserDownload.of(w, ck, Refusing).map(_.text).getOrElse(fail("not a browser download"))
    assert(
      text.startsWith(
        "10.3390/rel10040258. https://doi.org/10.3390/rel10040258 — diamond open access, at https://www.mdpi.com/"
      ),
      text
    )

  test("not a browser download: hybrid, green or bronze; no DOI; no open location on a refusing host; no hosts"):
    List("hybrid", "green", "bronze", "closed").foreach(s =>
      assertEquals(BrowserDownload.of(mdpi.modify(_.oaStatus).setTo(Some(s)), ck, Refusing), None, s)
    )
    assertEquals(BrowserDownload.of(mdpi.modify(_.oaStatus).setTo(None), ck, Refusing), None)
    assertEquals(BrowserDownload.of(mdpi.modify(_.doi).setTo(None), ck, Refusing), None)
    assertEquals(
      BrowserDownload.of(
        mdpi.modify(
          _.oaUrl).setTo(Some("https://doi.org/10.3390/rel10040258")).modify(_.oaLocations).setTo(List("https://zenodo.org/records/1")
        ),
        ck,
        Refusing
      ),
      None
    )
    assertEquals(BrowserDownload.of(mdpi, ck, refusing(Nil)), None)
    assertEquals(
      BrowserDownload.of(mdpi.modify(_.oaUrl).setTo(Some("https://notmdpi.com/x")).copy(oaLocations = Nil), ck, Refusing),
      None,
      "a host matches itself and its subdomains only"
    )

  test("a refusing host found among later locations; the index's oa_url alone is enough"):
    val sup = WorkRef(
      doi = (Some("10.16993/bbk")).map(com.histmersion.api.library.Doi.apply),
      title = (Some("Book")).map(com.histmersion.api.library.Title.apply),
      oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("gold")),
      oaLocations =
        List(com.histmersion.api.library.WorkRef.OaLocations("https://doi.org/10.16993/bbk"), com.histmersion.api.library.WorkRef.OaLocations("https://www.stockholmuniversitypress.se/site/books/m/10.16993/bbk/"))
    )
    assertEquals(
      BrowserDownload.of(sup, ck, Refusing).map(b => b.landing.value: String),
      Some("https://www.stockholmuniversitypress.se/site/books/m/10.16993/bbk/")
    )
    val dg = WorkRef(
      doi = (Some("10.1515/zzz-2020-0001")).map(com.histmersion.api.library.Doi.apply),
      title = (Some("Article")).map(com.histmersion.api.library.Title.apply),
      oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("gold")),
      oaUrl = Some(com.histmersion.api.library.WorkRef.OaUrl("https://www.degruyter.com/document/doi/10.1515/zzz-2020-0001/pdf"))
    )
    assertEquals(BrowserDownload.of(dg, ck, Refusing).map(b => b.publisher.value: String), Some("degruyter.com"))
