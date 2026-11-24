package com.histmersion
package files

import com.histmersion.domain.media.covers.{CoverChoice, OpenLibrary}
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.domain.library.Isbn
import com.histmersion.lint.{LintRule, tolerated}

/** Where a book's cover comes from ([[CoverChoice]], [[OpenLibrary]]). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CoverChoiceSuite extends munit.FunSuite:

  test("a book's cover: Calibre's own file first, then Open Library by ISBN, then by OCLC"):
    val exists: CoverChoice.Pick.Calibre.Path => Boolean = p => Set("/lib/A/cover.jpg")(p.value)
    assertEquals(
      CoverChoice.choose(
        Some(CoverChoice.Pick.Calibre.Path("/lib/A/cover.jpg")),
        exists,
        List(Isbn("978-0-691-16838-8")),
        Some(OpenLibrary.Oclc("123"))
      ),
      Some(CoverChoice.Pick.Calibre(CoverChoice.Pick.Calibre.Path("/lib/A/cover.jpg")))
    )
    assertEquals(
      CoverChoice.choose(
        Some(CoverChoice.Pick.Calibre.Path("/lib/B/cover.jpg")),
        exists,
        List(Isbn("bad"), Isbn("978-0-691-16838-8")),
        Some(OpenLibrary.Oclc("123"))
      ),
      Some(
        CoverChoice.Pick
          .OpenLibrary(CoverChoice.Pick.OpenLibrary.Kind("isbn"), CoverChoice.Pick.OpenLibrary.Id("9780691168388"))
      ),
      "the file is gone: the first usable ISBN"
    )
    assertEquals(
      CoverChoice.choose(None, exists, Nil, Some(OpenLibrary.Oclc("(OCoLC)00012345"))),
      Some(
        CoverChoice.Pick
          .OpenLibrary(CoverChoice.Pick.OpenLibrary.Kind("oclc"), CoverChoice.Pick.OpenLibrary.Id("12345"))
      )
    )
    assertEquals(CoverChoice.choose(None, exists, List(Isbn("12")), None), None)

  test("Open Library: identifiers normalised, URLs as the API documents them"):
    assertEquals(OpenLibrary.isbn(Isbn("0-306-40615-x")), Some("030640615X"))
    assertEquals(OpenLibrary.isbn(Isbn("97803064061")), None)
    assertEquals(OpenLibrary.oclc(OpenLibrary.Oclc("ocm00012345")), Some("12345"))
    assertEquals(OpenLibrary.key(Some(Isbn("x")), Some(OpenLibrary.Oclc("ocm9"))), Some("oclc" -> "9"))
    assertEquals(
      OpenLibrary.coverUrl(
        OpenLibrary.BaseUrl("https://covers.openlibrary.org/"),
        CoverChoice.Pick.OpenLibrary.Kind("isbn"),
        CoverChoice.Pick.OpenLibrary.Id("9780691168388"),
        OpenLibrary.CoverSize("l")
      ),
      "https://covers.openlibrary.org/b/isbn/9780691168388-L.jpg?default=false"
    )
    assertEquals(
      OpenLibrary.pageUrl(
        OpenLibrary.BaseUrl("https://openlibrary.org"),
        CoverChoice.Pick.OpenLibrary.Kind("isbn"),
        CoverChoice.Pick.OpenLibrary.Id("9780691168388")
      ),
      "https://openlibrary.org/isbn/9780691168388"
    )
