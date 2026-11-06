package com.histmersion.domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.{AuthorName, Bounded}
import MergeText.AuthorAgreement

/** The matcher's normalisers, on names and titles shaped like the live corpus's. */
class MergeTextSuite extends munit.FunSuite with Bounded:

  test("fold removes diacritics and the letters NFD cannot split"):
    assertEquals(MergeText.fold(com.histmersion.domain.library.sources.MergeText.Text("Güterbock")), "guterbock")
    assertEquals(MergeText.fold(com.histmersion.domain.library.sources.MergeText.Text("López-Ruiz")), "lopez-ruiz")
    assertEquals(MergeText.fold(com.histmersion.domain.library.sources.MergeText.Text("Łukasz Ståhl Æsir Straße")), "lukasz stahl aesir strasse")
    assertEquals(MergeText.fold(com.histmersion.domain.library.sources.MergeText.Text("Ḫattuša")), "hattusa")

  test("fingerprint: case, accents, punctuation and word order do not matter"):
    assertEquals(
      MergeText.fingerprint(com.histmersion.domain.library.sources.MergeText.Text("Perspectives on Hittite Civilization")),
      MergeText.fingerprint(com.histmersion.domain.library.sources.MergeText.Text("perspectives on hittite civilization."))
    )
    assertEquals(
      MergeText.fingerprint(com.histmersion.domain.library.sources.MergeText.Text("The Ancient Near East, c. 3000–330 BC")),
      MergeText.fingerprint(com.histmersion.domain.library.sources.MergeText.Text("the ancient near east c 3000 330 bc"))
    )

  test("mainTitle cuts the subtitle at a colon, a dash or a full stop"):
    assertEquals(
      MergeText.mainTitle(com.histmersion.domain.Title("Perspectives on Hittite Civilization: Selected Writings")),
      "Perspectives on Hittite Civilization"
    )
    assertEquals(MergeText.mainTitle(com.histmersion.domain.Title("Troy and Homer — Towards a Solution")), "Troy and Homer")
    assertEquals(MergeText.mainTitle(com.histmersion.domain.Title("Themis. A Study of the Social Origins")), "Themis")
    assertEquals(MergeText.mainTitle(com.histmersion.domain.Title("No subtitle")), "No subtitle")

  test("titleKey: the first three significant words of the main title, sorted"):
    assertEquals(MergeText.titleKey(com.histmersion.domain.Title("The Ancient Near East: c. 3000-330 BC")), "ancient east near")
    assertEquals(
      MergeText.titleKey(com.histmersion.domain.Title("Perspectives on Hittite civilization")),
      "hittite perspectives civilization".split(" ").sorted.mkString(" ")
    )

  private def same(a: AuthorName, b: AuthorName): Boolean =
    (MergeText.person(a), MergeText.person(b)) match
      case (Some(x), Some(y)) => MergeText.samePerson(x, y)
      case _                  => false

  test("names: Surname, Given and Given Surname; initials; diacritics; life dates"):
    assert(same(AuthorName("Güterbock, Hans G."), AuthorName("Hans G. Guterbock")))
    assert(same(AuthorName("Güterbock, Hans G."), AuthorName("H. G. Güterbock")))
    assert(same(AuthorName("Harrison, Jane Ellen, 1850-1928"), AuthorName("Jane Harrison")))
    assert(same(AuthorName("Harrison, Jane Ellen, 1850-1928"), AuthorName("J. E. Harrison")))
    assert(same(AuthorName("Beekes, R. S. P."), AuthorName("Robert S. P. Beekes")))
    assert(same(AuthorName("Francis Young"), AuthorName("Young, Francis")))
    assert(same(AuthorName("Kuhrt, Amelie"), AuthorName("Amélie Kuhrt")))
    assert(!same(AuthorName("Smith, M."), AuthorName("J. Smith")), "different initials are different people")
    assert(!same(AuthorName("Harrison, Jane Ellen"), AuthorName("Thomas Harrison")))
    assert(!same(AuthorName("Hoffner, Harry A."), AuthorName("Hans G. Güterbock")))

  test("names: multi-word surnames — hyphenated or not, with particles"):
    assert(same(AuthorName("López-Ruiz, Carolina"), AuthorName("Carolina Lopez Ruiz")))
    assert(same(AuthorName("López-Ruiz, Carolina"), AuthorName("Carolina López-Ruiz")))
    assert(same(AuthorName("Lopez Ruiz, Carolina"), AuthorName("C. López-Ruiz")))
    assert(same(AuthorName("van der Toorn, Karel"), AuthorName("Karel van der Toorn")))
    assert(same(AuthorName("de Martino, Stefano"), AuthorName("Stefano de Martino")))
    assert(!same(AuthorName("López-Ruiz, Carolina"), AuthorName("Carolina Ruiz")), "half a double surname is not the surname")

  test("surname blocking keys cover both halves of a double surname"):
    assert(MergeText.surnameKeys(com.histmersion.domain.AuthorName("López-Ruiz, Carolina")).contains(com.histmersion.domain.library.sources.MergeText.SurnameKey("lopezruiz")))
    assert(MergeText.surnameKeys(com.histmersion.domain.AuthorName("Carolina Lopez Ruiz")).contains(com.histmersion.domain.library.sources.MergeText.SurnameKey("ruiz")))
    assert(MergeText.surnameKeys(com.histmersion.domain.AuthorName("López-Ruiz, Carolina")).contains(com.histmersion.domain.library.sources.MergeText.SurnameKey("ruiz")))
    assertEquals(
      MergeText.surnameKeys(com.histmersion.domain.AuthorName("Güterbock, Hans G.")).intersect(MergeText.surnameKeys(com.histmersion.domain.AuthorName("H. G. Guterbock"))),
      Set("guterbock")
    )

  test("author lists: order, name forms, overlap and disjointness"):
    assertEquals(
      MergeText.authors(List(com.histmersion.domain.AuthorName("H. G. Güterbock"), com.histmersion.domain.AuthorName("Harry A. Hoffner")), List(com.histmersion.domain.AuthorName("Hoffner, Harry A."), com.histmersion.domain.AuthorName("Guterbock, Hans G."))),
      AuthorAgreement.Same
    )
    assertEquals(
      MergeText.authors(List(com.histmersion.domain.AuthorName("Güterbock, Hans G."), com.histmersion.domain.AuthorName("Hoffner, Harry A.")), List(com.histmersion.domain.AuthorName("Hans G. Guterbock"))),
      AuthorAgreement.FirstAuthor
    )
    assertEquals(
      MergeText.authors(List(com.histmersion.domain.AuthorName("A. Kuhrt"), com.histmersion.domain.AuthorName("B. Bryce"), com.histmersion.domain.AuthorName("C. Cline")), List(com.histmersion.domain.AuthorName("D. Dodds"), com.histmersion.domain.AuthorName("Bryce, B."), com.histmersion.domain.AuthorName("Cline, C."))),
      AuthorAgreement.Overlap
    )
    assertEquals(MergeText.authors(List(com.histmersion.domain.AuthorName("M. Smith")), List(com.histmersion.domain.AuthorName("Smith, J."))), AuthorAgreement.Disjoint)
    assertEquals(MergeText.authors(Nil, List(com.histmersion.domain.AuthorName("Smith, J."))), AuthorAgreement.Unknown)

  test("Jaro-Winkler: the textbook values"):
    assertEqualsDouble(MergeText.jaroWinkler(com.histmersion.domain.library.sources.MergeText.Words("martha"), com.histmersion.domain.library.sources.MergeText.Words("marhta")).value, 0.961, 0.001)
    assertEqualsDouble(MergeText.jaroWinkler(com.histmersion.domain.library.sources.MergeText.Words("dwayne"), com.histmersion.domain.library.sources.MergeText.Words("duane")).value, 0.84, 0.001)
    assertEqualsDouble(MergeText.jaroWinkler(com.histmersion.domain.library.sources.MergeText.Words("dixon"), com.histmersion.domain.library.sources.MergeText.Words("dicksonx")).value, 0.813, 0.001)
    assertEquals(MergeText.jaroWinkler(com.histmersion.domain.library.sources.MergeText.Words(""), com.histmersion.domain.library.sources.MergeText.Words("x")), 0.0)
    assertEquals(MergeText.jaroWinkler(com.histmersion.domain.library.sources.MergeText.Words("same"), com.histmersion.domain.library.sources.MergeText.Words("same")), 1.0)

  test("identifiers: a DOI key as V032 makes it; ISBN-10 to ISBN-13; a bad check digit is no ISBN"):
    assertEquals(MergeText.doiKey(com.histmersion.domain.library.sources.MergeText.WrittenDoi("https://doi.org/10.4324/9780203431566")), Some("10.4324/9780203431566"))
    assertEquals(MergeText.doiKey(com.histmersion.domain.library.sources.MergeText.WrittenDoi("doi: 10.1017/S0003")), Some("10.1017/s0003"))
    assertEquals(MergeText.doiKey(com.histmersion.domain.library.sources.MergeText.WrittenDoi("  ")), None)
    assertEquals(MergeText.isbn13(com.histmersion.domain.library.Isbn("0-415-01353-4")), Some("9780415013536"))
    assertEquals(MergeText.isbn13(com.histmersion.domain.library.Isbn("978-0-415-01353-6")), Some("9780415013536"))
    assertEquals(MergeText.isbn13(com.histmersion.domain.library.Isbn("978-0-415-01353-7")), None)
    assertEquals(MergeText.isbn13(com.histmersion.domain.library.Isbn("080442957X")), Some("9780804429573"))

  test("MinHash: identical text 1, a light OCR difference high, an unrelated text low; deterministic"):
    val rnd = scala.util.Random(7)
    val base = (1 to 2000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(6)).mkString).mkString(" ")
    val ocr = base.split(" ").zipWithIndex.map((w, i) => if i % 97 == 0 then w.reverse else w).mkString(" ")
    val other = (1 to 2000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(6)).mkString).mkString(" ")
    val (a, b, c) = (
      com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(base)),
      com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(ocr)),
      com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(other))
    )
    assertEquals(a.similarity(com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(base))), 1.0)
    assert(a.similarity(b).value >= 0.8, a.similarity(b).toString)
    assert(a.similarity(c).value < 0.1, a.similarity(c).toString)
    assertEquals(com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text(base)), a)
    // a text shorter than a shingle is still a signature
    assertEquals(
      com.histmersion.util.text.MinHash
        .of(com.histmersion.util.text.MinHash.Text("short text"))
        .similarity(com.histmersion.util.text.MinHash.of(com.histmersion.util.text.MinHash.Text("Short, text!"))),
      1.0
    )
