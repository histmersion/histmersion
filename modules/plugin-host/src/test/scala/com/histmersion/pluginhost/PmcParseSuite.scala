package com.histmersion.pluginhost

import com.histmersion.api.*
import com.histmersion.api.given

import io.circe.parser.parse

/** Europe PMC search hits and NCBI's PMC Article Datasets records (#1523), from responses captured on 2026-10-11
  * (trimmed, in `src/test/resources/acquire`).
  */
class PmcParseSuite extends munit.FunSuite:

  private def text(name: String) = scala.io.Source.fromResource(s"acquire/$name").mkString
  private def json(name: String) = parse(text(name)).fold(throw _, identity)

  test("an open-access hit: its PMCID and only the free PDF links"):
    val hit = AcquireParse.europepmcHit(json("europepmc_oa.json"), "10.1038/nature25778")
    assertEquals(hit.flatMap(_.pmcid), Some("PMC6091220"))
    assert(hit.exists(_.isOpenAccess))
    assertEquals(hit.map(_.pdfUrls), Some(List("https://europepmc.org/articles/PMC6091220?pdf=render")))

  test("an author manuscript: free (F), not open access, its PDF link still taken"):
    val hit = AcquireParse.europepmcHit(json("europepmc_free.json"), "https://doi.org/10.1126/SCIENCE.AAR7711")
    assertEquals(hit.flatMap(_.pmcid), Some("PMC6748862"))
    assert(!hit.exists(_.isOpenAccess))
    assertEquals(hit.map(_.pdfUrls), Some(List("https://europepmc.org/articles/PMC6748862?pdf=render")))

  test("a hit without a PMC copy has no PMCID and no links; no hit at all is None"):
    val hit = AcquireParse.europepmcHit(json("europepmc_nopmc.json"), "10.1016/j.cell.2025.07.013")
    assertEquals(hit, Some(EuropePmcHit(None, false, Nil)))
    assertEquals(AcquireParse.europepmcHit(json("europepmc_none.json"), "10.15184/aqy.2017.21"), None)

  test("a hit for ANOTHER DOI (a correction citing it) is not taken"):
    assertEquals(AcquireParse.europepmcHit(json("europepmc_oa.json"), "10.1038/nature99999"), None)
    assertEquals(AcquireParse.europepmcHit(json("europepmc_oa.json"), "not a doi"), None)

  test("the newest published version of a PMCID, from the bucket listing"):
    assertEquals(AcquireParse.pmcLatestVersion(text("pmc_dataset_list.xml"), "PMC6091220"), Some(1))
    val two = "<Key>metadata/PMC12.1.json</Key><Key>metadata/PMC12.2.json</Key><Key>metadata/PMC123.9.json</Key>"
    assertEquals(AcquireParse.pmcLatestVersion(two, "PMC12"), Some(2))
    assertEquals(AcquireParse.pmcLatestVersion("<ListBucketResult/>", "PMC12"), None)
    assertEquals(AcquireParse.pmcLatestVersion(two, "PMC.*"), None)

  test("an open-access record names its PDF and XML with their MD5s"):
    val r = AcquireParse.pmcRecord(json("pmc_dataset_oa.json")).getOrElse(fail("no record"))
    assertEquals(r.pmcid, "PMC6091220")
    assert(r.openAccess && r.manuscript)
    assertEquals(r.pdf, Some(PmcObject("PMC6091220.1/PMC6091220.1.pdf", Some("e06195e6382220ee664cb57bf77cdaa2"))))
    assertEquals(r.xml.map(_.key), Some("PMC6091220.1/PMC6091220.1.xml"))

  test("an author manuscript outside the OA subset has only its XML"):
    val r = AcquireParse.pmcRecord(json("pmc_dataset_manuscript.json")).getOrElse(fail("no record"))
    assert(!r.openAccess)
    assertEquals(r.license, Some("TDM"))
    assertEquals(r.pdf, None)
    assertEquals(r.xml, Some(PmcObject("PMC6748862.1/PMC6748862.1.xml", Some("54c09d202efe4376a7b22efe601ff2c3"))))

  test("a dataset URL outside an article's own folder is refused"):
    assertEquals(AcquireParse.pmcObject("s3://pmc-oa-opendata/PMC1.1/../secret"), None)
    assertEquals(AcquireParse.pmcObject("s3://pmc-oa-opendata/metadata/PMC1.1.json"), None)
    assertEquals(AcquireParse.pmcObject("https://example.org/PMC1.1/PMC1.1.pdf"), None)
    assertEquals(AcquireParse.pmcObject("s3://pmc-oa-opendata/PMC1.1/a/b.pdf"), None)
    assertEquals(AcquireParse.pmcObject("s3://pmc-oa-opendata/PMC1.1/.."), None)
    assertEquals(
      AcquireParse.pmcObject("s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf"),
      Some(PmcObject("PMC1.1/PMC1.1.pdf", None))
    )
