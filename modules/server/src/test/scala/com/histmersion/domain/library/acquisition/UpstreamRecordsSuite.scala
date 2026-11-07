package com.histmersion.domain.library.acquisition

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.identifiers.Identifiers

import io.circe.parser.parse

/** The upstreams' records as the wire shows them: a work keyed by the citekey the corpus gives it, a citing work and a
  * reference with the wire's defaults; and the upstreams' identifier rules equal to the wire's own over the same
  * inputs.
  */
class UpstreamRecordsSuite extends munit.FunSuite:

  private def fixture(name: String) =
    parse(scala.io.Source.fromResource(s"acquire/$name").mkString).fold(throw _, identity)

  test("an OpenAlex or Crossref work gets the citekey the corpus already uses"):
    assertEquals(AcquireParse.openalexWork(fixture("openalex_work.json")).wire.citekey, Some("haak2015_massive"))
    assertEquals(AcquireParse.crossrefWork(fixture("crossref_work.json")).wire.citekey, Some("haak2015_massive"))
    val ws = AcquireParse.openalexWorks(fixture("openalex_search.json")).map(_.wire)
    assert(ws.nonEmpty && ws.forall(_.citekey.isDefined) && ws.forall(!_.inDb.value))

  test("a work goes to the upstreams and back unchanged but for the corpus's own fields"):
    val w = AcquireParse.openalexWork(fixture("openalex_work.json"))
    assertEquals(w.wire.upstream, w)

  test("a citing work and a reference carry the wire's defaults"):
    val c = AcquireParse.s2Citations(fixture("s2_citations.json")).map(_.wire)
    assert(c.nonEmpty && c.forall(r => r.proposedKind.value == "cites" && r.citekey.isEmpty), c.toString)
    val r = AcquireParse.crossrefReferences(fixture("crossref_work.json")).map(_.wire)
    assert(r.forall(_.citekey.isEmpty))

  test("a retraction notice reads as a retraction; the real citation contexts classify"):
    val ups = com.histmersion.domain.library.acquisition.DiscoveryClient
      .notices(AcquireParse.crossrefUpdates(fixture("crossref_retracted.json")))
    assertEquals(
      api.library.Acquire.verdict(Nil, ups, false, Some(com.histmersion.api.library.Acquire.CitedByCount(5)), Some(com.histmersion.api.catalogue.Year(2019)), com.histmersion.api.library.Acquire.CitationThreshold(50), com.histmersion.api.library.Acquire.CitationThreshold(3), com.histmersion.api.catalogue.Year(2018)),
      api.library.Acquire.Verdicts.Retracted
    )
    val rows = AcquireParse.s2Citations(fixture("s2_citations.json")).map(_.wire)
    val kinds = rows.map(r => api.library.Acquire.classify(r.intents.map(i => api.library.Acquire.Intent.Wire(i.value)), r.contexts.map(c => api.library.Acquire.CitationContext(c.value)), r.isInfluential.value, r.year, Some(com.histmersion.api.catalogue.Year(2015))))
    assert(kinds.forall(api.library.Acquire.EdgeKinds.all.contains), kinds)

  test("the upstreams' DOI and arXiv rules are the wire's"):
    val inputs = List(
      "10.1038/NATURE14317",
      "https://doi.org/10.1038/nature14317.",
      "http://dx.doi.org/10.1000/xyz)",
      "doi: 10.5281/zenodo.3695550;",
      "see 10.1234/abc|",
      "not a doi",
      "",
      "arXiv:2101.00001v3",
      "2101.00001",
      "hep-th/9901001",
      "arxiv:hep-th/9901001v2",
      "math.AG/0309136"
    )
    inputs.foreach { s =>
      assertEquals(Identifiers.doi(Identifiers.DoiText(s)), com.histmersion.domain.library.Doi.normalize(s), s)
      assertEquals(Identifiers.arxiv(Identifiers.ArxivText(s)), Ident.arxiv(Ident.Written(s)), s)
    }
