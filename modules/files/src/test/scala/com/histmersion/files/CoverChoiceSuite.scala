package com.histmersion.files

import com.histmersion.domain.media.covers.{CoverChoice, OpenLibrary}

/** Where a book's cover comes from ([[CoverChoice]], [[OpenLibrary]]). */
class CoverChoiceSuite extends munit.FunSuite:

  test("a book's cover: Calibre's own file first, then Open Library by ISBN, then by OCLC"):
    val exists = Set("/lib/A/cover.jpg")
    assertEquals(
      CoverChoice.choose(Some("/lib/A/cover.jpg"), exists, List("978-0-691-16838-8"), Some("123")),
      Some(CoverChoice.Pick.Calibre("/lib/A/cover.jpg"))
    )
    assertEquals(
      CoverChoice.choose(Some("/lib/B/cover.jpg"), exists, List("bad", "978-0-691-16838-8"), Some("123")),
      Some(CoverChoice.Pick.OpenLibrary("isbn", "9780691168388")),
      "the file is gone: the first usable ISBN"
    )
    assertEquals(
      CoverChoice.choose(None, exists, Nil, Some("(OCoLC)00012345")),
      Some(CoverChoice.Pick.OpenLibrary("oclc", "12345"))
    )
    assertEquals(CoverChoice.choose(None, exists, List("12"), None), None)

  test("Open Library: identifiers normalised, URLs as the API documents them"):
    assertEquals(OpenLibrary.isbn("0-306-40615-x"), Some("030640615X"))
    assertEquals(OpenLibrary.isbn("97803064061"), None)
    assertEquals(OpenLibrary.oclc("ocm00012345"), Some("12345"))
    assertEquals(OpenLibrary.key(Some("x"), Some("ocm9")), Some("oclc" -> "9"))
    assertEquals(
      OpenLibrary.coverUrl("https://covers.openlibrary.org/", "isbn", "9780691168388", "l"),
      "https://covers.openlibrary.org/b/isbn/9780691168388-L.jpg?default=false"
    )
    assertEquals(
      OpenLibrary.pageUrl("https://openlibrary.org", "isbn", "9780691168388"),
      "https://openlibrary.org/isbn/9780691168388"
    )
