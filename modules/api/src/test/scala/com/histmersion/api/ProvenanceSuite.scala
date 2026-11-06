package com.histmersion.api

/** A provenance is free text; a file brought in from elsewhere classifies as a book. */

import com.histmersion.api.library.Provenance
import com.histmersion.api.library.Source
class ProvenanceSuite extends munit.FunSuite:
  test("a file brought in from elsewhere is a book when nothing else says what it is"):
    def kind(p: String) = Source.Kinds.of(None, None, Some(p), Nil, Nil, hasCover = false)
    assertEquals(kind(Provenance.ExternalFile), Source.Kinds.Book)
    assertEquals(kind(" External-File "), Source.Kinds.Book, "compared trimmed and case-insensitively")
    assertEquals(kind("oa"), Source.Kinds.Unknown)
    assertEquals(kind("unheard-of"), Source.Kinds.Unknown, "an unknown value says nothing about the kind")
    assertEquals(
      Source.Kinds.of(Some(com.histmersion.api.library.Doi("10.1/x")), None, Some(Provenance.ExternalFile), Nil, Nil, false),
      Source.Kinds.Paper
    )
