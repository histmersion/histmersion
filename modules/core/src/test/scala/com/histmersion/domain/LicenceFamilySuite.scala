package com.histmersion.domain

import com.histmersion.values.LicenceId

/** The shared licence families: their wire spellings, which an allowlist may name, which need the author named. */
class LicenceFamilySuite extends munit.FunSuite:

  test("every family is spelled as the allowlists and the stored counts spell it"):
    assertEquals(
      LicenceFamily.values.toList.map(_.wire),
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
    LicenceFamily.values.foreach(f => assertEquals(LicenceFamily.fromWire(f.wire), Some(f)))
    assertEquals(LicenceFamily.fromWire("CC-BY"), None)

  test("an allowlist may name every family but the two never stored"):
    assertEquals(LicenceFamily.allowable.size, 9)
    assert(LicenceFamily.AllRightsReserved.never && LicenceFamily.Unknown.never)
    assert(LicenceFamily.allowable.forall(!_.never))

  test("who must be named, and who needs no credit"):
    assertEquals(
      LicenceFamily.values.toList.filter(_.needsAttribution),
      List(
        LicenceFamily.CcBy,
        LicenceFamily.CcBySa,
        LicenceFamily.CcByNc,
        LicenceFamily.CcByNcSa,
        LicenceFamily.CcByNd,
        LicenceFamily.CcByNcNd,
        LicenceFamily.Gfdl
      )
    )
    assertEquals(
      LicenceFamily.values.toList.filter(_.isPublicDomain),
      List(LicenceFamily.Cc0, LicenceFamily.PublicDomain)
    )

  test("a statement is a URL or a name, read by the one classification"):
    assertEquals(
      Licence.ofStatement("https://creativecommons.org/licenses/by/4.0/"),
      Licence.ofUrl("https://creativecommons.org/licenses/by/4.0/")
    )
    assertEquals(Licence.ofStatement("CC BY 4.0"), Licence(LicenceId("CC-BY-4.0"), LicenceFamily.CcBy))
    assertEquals(Licence.unknown("odd"), Licence(LicenceId("odd"), LicenceFamily.Unknown))

  test("an attribution the host states itself"):
    assertEquals(Attribution.cc0(None, "c").licence.family, LicenceFamily.Cc0)
    assertEquals(Attribution.publicDomain(Some("a"), "c").licenceUrl, Some(Attribution.PdmUrl))
