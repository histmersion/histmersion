package com.histmersion.api

/** A provenance is free text; a file brought in from elsewhere classifies as a book. */
class ProvenanceSuite extends munit.FunSuite:
  test("a file brought in from elsewhere is a book when nothing else says what it is"):
    def kind(p: String) = SourceKinds.of(None, None, Some(p), Nil, Nil, hasCover = false)
    assertEquals(kind(Provenance.ExternalFile), SourceKinds.Book)
    assertEquals(kind(" External-File "), SourceKinds.Book, "compared trimmed and case-insensitively")
    assertEquals(kind("oa"), SourceKinds.Unknown)
    assertEquals(kind("unheard-of"), SourceKinds.Unknown, "an unknown value says nothing about the kind")
    assertEquals(
      SourceKinds.of(Some("10.1/x"), None, Some(Provenance.ExternalFile), Nil, Nil, false),
      SourceKinds.Paper
    )
