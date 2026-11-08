package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given
/** A provenance is free text; a file brought in from elsewhere classifies as a book. */

import com.histmersion.api.library.Provenance
import com.histmersion.api.library.Source
import com.histmersion.api.library.Doi
class ProvenanceSuite extends munit.FunSuite:
  test("a file brought in from elsewhere is a book when nothing else says what it is"):
    def kind(p: Source.Provenance) = Source.Kinds.of(None, None, Some(p), Nil, Nil, hasCover = false)
    assertEquals(kind(Source.Provenance(Provenance.ExternalFile)), Source.Kinds.Book)
    assertEquals(kind(Source.Provenance(" External-File ")), Source.Kinds.Book, "compared trimmed and case-insensitively")
    assertEquals(kind(Source.Provenance("oa")), Source.Kinds.Unknown)
    assertEquals(kind(Source.Provenance("unheard-of")), Source.Kinds.Unknown, "an unknown value says nothing about the kind")
    assertEquals(
      Source.Kinds.of(Some(Doi("10.1/x")), None, Some(Source.Provenance(Provenance.ExternalFile)), Nil, Nil, false),
      Source.Kinds.Paper
    )
