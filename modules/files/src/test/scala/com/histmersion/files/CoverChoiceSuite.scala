package com.histmersion.files

import com.histmersion.domain.media.covers.{CoverChoice, OpenLibrary}

/** Where a book's cover comes from ([[CoverChoice]], [[OpenLibrary]]). */
class CoverChoiceSuite extends munit.FunSuite:

  test("a book's cover: Calibre's own file first, then Open Library by ISBN, then by OCLC"):
    val exists = Set("/lib/A/cover.jpg")
    assertEquals(
      CoverChoice.choose(Some(com.histmersion.domain.media.covers.CoverChoice.Pick.Calibre.Path("/lib/A/cover.jpg")), exists, List(com.histmersion.domain.library.Isbn("978-0-691-16838-8")), Some(com.histmersion.domain.media.covers.OpenLibrary.Oclc("123"))),
      Some(CoverChoice.Pick.Calibre(com.histmersion.domain.media.covers.CoverChoice.Pick.Calibre.Path("/lib/A/cover.jpg")))
    )
    assertEquals(
      CoverChoice.choose(Some(com.histmersion.domain.media.covers.CoverChoice.Pick.Calibre.Path("/lib/B/cover.jpg")), exists, List(com.histmersion.domain.library.Isbn("bad"), com.histmersion.domain.library.Isbn("978-0-691-16838-8")), Some(com.histmersion.domain.media.covers.OpenLibrary.Oclc("123"))),
      Some(CoverChoice.Pick.OpenLibrary(com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind("isbn"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id("9780691168388"))),
      "the file is gone: the first usable ISBN"
    )
    assertEquals(
      CoverChoice.choose(None, exists, Nil, Some(com.histmersion.domain.media.covers.OpenLibrary.Oclc("(OCoLC)00012345"))),
      Some(CoverChoice.Pick.OpenLibrary(com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind("oclc"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id("12345")))
    )
    assertEquals(CoverChoice.choose(None, exists, List(com.histmersion.domain.library.Isbn("12")), None), None)

  test("Open Library: identifiers normalised, URLs as the API documents them"):
    assertEquals(OpenLibrary.isbn(com.histmersion.domain.library.Isbn("0-306-40615-x")), Some("030640615X"))
    assertEquals(OpenLibrary.isbn(com.histmersion.domain.library.Isbn("97803064061")), None)
    assertEquals(OpenLibrary.oclc(com.histmersion.domain.media.covers.OpenLibrary.Oclc("ocm00012345")), Some("12345"))
    assertEquals(OpenLibrary.key(Some(com.histmersion.domain.library.Isbn("x")), Some(com.histmersion.domain.media.covers.OpenLibrary.Oclc("ocm9"))), Some("oclc" -> "9"))
    assertEquals(
      OpenLibrary.coverUrl(com.histmersion.domain.media.covers.OpenLibrary.BaseUrl("https://covers.openlibrary.org/"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind("isbn"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id("9780691168388"), com.histmersion.domain.media.covers.OpenLibrary.CoverSize("l")),
      "https://covers.openlibrary.org/b/isbn/9780691168388-L.jpg?default=false"
    )
    assertEquals(
      OpenLibrary.pageUrl(com.histmersion.domain.media.covers.OpenLibrary.BaseUrl("https://openlibrary.org"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind("isbn"), com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id("9780691168388")),
      "https://openlibrary.org/isbn/9780691168388"
    )
