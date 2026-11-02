package com.histmersion.web.library

import com.histmersion.api.library.IngestRequest

class DoiIngestSuite extends munit.FunSuite:

  test("a DOI, as typed or as a doi.org link, asks the legal chain only"):
    List(
      "10.1017/S0003598X00000001",
      " https://doi.org/10.1017/S0003598X00000001 ",
      "doi:10.1017/S0003598X00000001",
      "http://dx.doi.org/10.1017/S0003598X00000001"
    ).foreach { t =>
      assertEquals(DoiIngest.request(t), Right(IngestRequest("10.1017/S0003598X00000001", legalOnly = true)), t)
    }

  test("an arXiv id, bare or prefixed, asks the legal chain only"):
    assertEquals(DoiIngest.request("2101.01234"), Right(IngestRequest("arxiv:2101.01234", legalOnly = true)))
    assertEquals(DoiIngest.request("arXiv:2101.01234v2"), Right(IngestRequest("arXiv:2101.01234v2", legalOnly = true)))
    assertEquals(DoiIngest.request("hep-th/9901001"), Right(IngestRequest("arxiv:hep-th/9901001", legalOnly = true)))

  test("every request the form can send is legal-only"):
    List("10.1/x", "10.12345/abc.def", "2101.01234")
      .flatMap(DoiIngest.request(_).toOption)
      .foreach(r => assert(r.legalOnly, r.toString))

  test("anything else is refused with what to type"):
    assert(DoiIngest.request("").isLeft)
    assert(DoiIngest.request("The Horse, the Wheel and Language").left.exists(_.contains("neither a DOI")))
    assert(DoiIngest.request("10.1017").isLeft)
