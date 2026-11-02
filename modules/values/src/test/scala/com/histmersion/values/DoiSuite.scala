package com.histmersion.values

/** A DOI read out of a text: the resolver URL and `doi:` stripped, lower case, the sentence's punctuation left out. */

import com.histmersion.domain.library.Doi
class DoiSuite extends munit.FunSuite:

  test("a DOI in any of its written forms, lower case"):
    assertEquals(Doi.normalize("https://doi.org/10.1038/Nature14317"), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("http://dx.doi.org/10.1038/nature14317"), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("DOI: 10.1038/nature14317"), Some("10.1038/nature14317"))
    assertEquals(
      Doi.normalize("see 10.1093/acprof:oso/9780199279081.001.0001)."),
      Some("10.1093/acprof:oso/9780199279081.001.0001")
    )

  test("the punctuation a sentence leaves after it is not part of it; no DOI is none"):
    assertEquals(Doi.normalize("  10.1038/nature14317.  "), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("10.1038/nature14317;"), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("no identifier here"), None)
    assertEquals(Doi.normalize(""), None)
    assertEquals(Doi.normalize(null), None)
