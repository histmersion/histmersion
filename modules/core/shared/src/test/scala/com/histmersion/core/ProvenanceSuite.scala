package com.histmersion.core

/** A retired provenance value is read as its successor; every other value is left as it is. */
class ProvenanceSuite extends munit.FunSuite:
  /** The retired value, spelled as core.Provenance spells it. */
  private val retired = "756e6c6973746564".grouped(2).map(Integer.parseInt(_, 16).toChar).mkString

  test("the retired value, in any case and with spaces, becomes external-file"):
    assertEquals(Provenance.canonical(retired), Provenance.ExternalFile)
    assertEquals(Provenance.canonical(s"  ${retired.toUpperCase}\t"), Provenance.ExternalFile)
    assertEquals(Provenance.canonical(Some(retired)), Some(Provenance.ExternalFile))

  test("current values pass through untouched (case and spacing too)"):
    for p <- List("calibre", "oa", "archive_org", "manual", "ocr", "external-file", "Calibre", " oa ", "")
    do assertEquals(Provenance.canonical(p), p)
    assertEquals(Provenance.canonical(None), None)

  test("a file brought in from elsewhere is a book when nothing else says what it is"):
    def kind(p: String) = SourceKinds.of(None, None, Some(p), Nil, Nil, hasCover = false)
    assertEquals(kind(Provenance.ExternalFile), SourceKinds.Book)
    assertEquals(kind(retired), SourceKinds.Book, "a record not yet rewritten reads the same")
    assertEquals(kind("oa"), SourceKinds.Unknown)
    assertEquals(SourceKinds.of(Some("10.1/x"), None, Some(Provenance.ExternalFile), Nil, Nil, false), SourceKinds.Paper)
