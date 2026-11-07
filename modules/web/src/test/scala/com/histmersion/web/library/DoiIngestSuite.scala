package com.histmersion.web.library

import com.histmersion.api.library.Ingest
class DoiIngestSuite extends munit.FunSuite:

  test("a DOI, as typed or as a doi.org link, asks the legal chain only"):
    List(
      "10.1017/S0003598X00000001",
      " https://doi.org/10.1017/S0003598X00000001 ",
      "doi:10.1017/S0003598X00000001",
      "http://dx.doi.org/10.1017/S0003598X00000001"
    ).foreach { t =>
      assertEquals(DoiIngest.request(t), Right(Ingest.Request(com.histmersion.api.library.Ingest.Request.Id("10.1017/S0003598X00000001"), legalOnly = com.histmersion.api.library.Ingest.Request.LegalOnly(true))), t)
    }

  test("an arXiv id, bare or prefixed, asks the legal chain only"):
    assertEquals(DoiIngest.request("2101.01234"), Right(Ingest.Request(com.histmersion.api.library.Ingest.Request.Id("arxiv:2101.01234"), legalOnly = com.histmersion.api.library.Ingest.Request.LegalOnly(true))))
    assertEquals(DoiIngest.request("arXiv:2101.01234v2"), Right(Ingest.Request(com.histmersion.api.library.Ingest.Request.Id("arXiv:2101.01234v2"), legalOnly = com.histmersion.api.library.Ingest.Request.LegalOnly(true))))
    assertEquals(DoiIngest.request("hep-th/9901001"), Right(Ingest.Request(com.histmersion.api.library.Ingest.Request.Id("arxiv:hep-th/9901001"), legalOnly = com.histmersion.api.library.Ingest.Request.LegalOnly(true))))

  test("every request the form can send is legal-only"):
    List("10.1/x", "10.12345/abc.def", "2101.01234")
      .flatMap(DoiIngest.request(_).toOption)
      .foreach(r => assert(r.legalOnly.value, r.toString))

  test("anything else is refused with what to type"):
    assert(DoiIngest.request("").isLeft)
    assert(DoiIngest.request("The Horse, the Wheel and Language").left.exists(_.value.contains("neither a DOI")))
    assert(DoiIngest.request("10.1017").isLeft)
