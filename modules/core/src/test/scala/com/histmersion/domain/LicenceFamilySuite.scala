package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

/** The shared licence families: their wire spellings, which an allowlist may name, which need the author named. */
class LicenceFamilySuite extends munit.FunSuite:

  test("every family is spelled as the allowlists and the stored counts spell it"):
    assertEquals(
      Licence.Family.values.toList.map(_.wire),
      List(
        "cc0",
        "public-domain",
        "cc-by",
        "cc-by-sa",
        "cc-by-nc",
        "cc-by-nc-sa",
        "cc-by-nd",
        "cc-by-nc-nd",
        "gfdl",
        "all-rights-reserved",
        "unknown"
      )
    )
    Licence.Family.values.foreach(f => assertEquals(Licence.Family.fromWire(f.wire), Some(f)))
    assertEquals(Licence.Family.fromWire(Licence.Family.Wire("CC-BY")), None)

  test("an allowlist may name every family but the two never stored"):
    assertEquals(Licence.Family.allowable.size, 9)
    assert(Licence.Family.AllRightsReserved.never && Licence.Family.Unknown.never)
    assert(Licence.Family.allowable.forall(!_.never))

  test("who must be named, and who needs no credit"):
    assertEquals(
      Licence.Family.values.toList.filter(_.needsAttribution),
      List(
        Licence.Family.CcBy,
        Licence.Family.CcBySa,
        Licence.Family.CcByNc,
        Licence.Family.CcByNcSa,
        Licence.Family.CcByNd,
        Licence.Family.CcByNcNd,
        Licence.Family.Gfdl
      )
    )
    assertEquals(
      Licence.Family.values.toList.filter(_.isPublicDomain),
      List(Licence.Family.Cc0, Licence.Family.PublicDomain)
    )

  test("a statement is a URL or a name, read by the one classification"):
    assertEquals(
      Licence.ofStatement(Licence.Statement("https://creativecommons.org/licenses/by/4.0/")),
      Licence.ofUrl(Licence.Url("https://creativecommons.org/licenses/by/4.0/"))
    )
    assertEquals(
      Licence.ofStatement(Licence.Statement("CC BY 4.0")),
      Licence(Licence.Id("CC-BY-4.0"), Licence.Family.CcBy)
    )
    assertEquals(Licence.unknown(Licence.Statement("odd")), Licence(Licence.Id("odd"), Licence.Family.Unknown))

  test("an attribution the host states itself"):
    assertEquals(Attribution.cc0(None, Attribution.Cc0Credit("c")).licence.family, Licence.Family.Cc0)
    assertEquals(
      Attribution.publicDomain(Some(AuthorName("a")), Attribution.PublicDomainCredit("c")).licenceUrl,
      Some(Attribution.PdmUrl)
    )
