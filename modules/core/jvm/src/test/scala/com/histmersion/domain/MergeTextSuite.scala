package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import MergeText.AuthorAgreement

/** The matcher's normalisers, on names and titles shaped like the live corpus's. */
class MergeTextSuite extends munit.FunSuite with Bounded:

  test("fold removes diacritics and the letters NFD cannot split"):
    assertEquals(MergeText.fold("Güterbock"), "guterbock")
    assertEquals(MergeText.fold("López-Ruiz"), "lopez-ruiz")
    assertEquals(MergeText.fold("Łukasz Ståhl Æsir Straße"), "lukasz stahl aesir strasse")
    assertEquals(MergeText.fold("Ḫattuša"), "hattusa")

  test("fingerprint: case, accents, punctuation and word order do not matter"):
    assertEquals(MergeText.fingerprint("Perspectives on Hittite Civilization"),
      MergeText.fingerprint("perspectives on hittite civilization."))
    assertEquals(MergeText.fingerprint("The Ancient Near East, c. 3000–330 BC"),
      MergeText.fingerprint("the ancient near east c 3000 330 bc"))

  test("mainTitle cuts the subtitle at a colon, a dash or a full stop"):
    assertEquals(MergeText.mainTitle("Perspectives on Hittite Civilization: Selected Writings"), "Perspectives on Hittite Civilization")
    assertEquals(MergeText.mainTitle("Troy and Homer — Towards a Solution"), "Troy and Homer")
    assertEquals(MergeText.mainTitle("Themis. A Study of the Social Origins"), "Themis")
    assertEquals(MergeText.mainTitle("No subtitle"), "No subtitle")

  test("titleKey: the first three significant words of the main title, sorted"):
    assertEquals(MergeText.titleKey("The Ancient Near East: c. 3000-330 BC"), "ancient east near")
    assertEquals(MergeText.titleKey("Perspectives on Hittite civilization"), "hittite perspectives civilization".split(" ").sorted.mkString(" "))

  private def same(a: String, b: String): Boolean =
    (MergeText.person(a), MergeText.person(b)) match
      case (Some(x), Some(y)) => MergeText.samePerson(x, y)
      case _                  => false

  test("names: Surname, Given and Given Surname; initials; diacritics; life dates"):
    assert(same("Güterbock, Hans G.", "Hans G. Guterbock"))
    assert(same("Güterbock, Hans G.", "H. G. Güterbock"))
    assert(same("Harrison, Jane Ellen, 1850-1928", "Jane Harrison"))
    assert(same("Harrison, Jane Ellen, 1850-1928", "J. E. Harrison"))
    assert(same("Beekes, R. S. P.", "Robert S. P. Beekes"))
    assert(same("Francis Young", "Young, Francis"))
    assert(same("Kuhrt, Amelie", "Amélie Kuhrt"))
    assert(!same("Smith, M.", "J. Smith"), "different initials are different people")
    assert(!same("Harrison, Jane Ellen", "Thomas Harrison"))
    assert(!same("Hoffner, Harry A.", "Hans G. Güterbock"))

  test("names: multi-word surnames — hyphenated or not, with particles"):
    assert(same("López-Ruiz, Carolina", "Carolina Lopez Ruiz"))
    assert(same("López-Ruiz, Carolina", "Carolina López-Ruiz"))
    assert(same("Lopez Ruiz, Carolina", "C. López-Ruiz"))
    assert(same("van der Toorn, Karel", "Karel van der Toorn"))
    assert(same("de Martino, Stefano", "Stefano de Martino"))
    assert(!same("López-Ruiz, Carolina", "Carolina Ruiz"), "half a double surname is not the surname")

  test("surname blocking keys cover both halves of a double surname"):
    assert(MergeText.surnameKeys("López-Ruiz, Carolina").contains("lopezruiz"))
    assert(MergeText.surnameKeys("Carolina Lopez Ruiz").contains("ruiz"))
    assert(MergeText.surnameKeys("López-Ruiz, Carolina").contains("ruiz"))
    assertEquals(MergeText.surnameKeys("Güterbock, Hans G.").intersect(MergeText.surnameKeys("H. G. Guterbock")), Set("guterbock"))

  test("author lists: order, name forms, overlap and disjointness"):
    assertEquals(MergeText.authors(List("H. G. Güterbock", "Harry A. Hoffner"), List("Hoffner, Harry A.", "Guterbock, Hans G.")),
      AuthorAgreement.Same)
    assertEquals(MergeText.authors(List("Güterbock, Hans G.", "Hoffner, Harry A."), List("Hans G. Guterbock")),
      AuthorAgreement.FirstAuthor)
    assertEquals(MergeText.authors(List("A. Kuhrt", "B. Bryce", "C. Cline"), List("D. Dodds", "Bryce, B.", "Cline, C.")),
      AuthorAgreement.Overlap)
    assertEquals(MergeText.authors(List("M. Smith"), List("Smith, J.")), AuthorAgreement.Disjoint)
    assertEquals(MergeText.authors(Nil, List("Smith, J.")), AuthorAgreement.Unknown)

  test("Jaro-Winkler: the textbook values"):
    assertEqualsDouble(MergeText.jaroWinkler("martha", "marhta"), 0.961, 0.001)
    assertEqualsDouble(MergeText.jaroWinkler("dwayne", "duane"), 0.84, 0.001)
    assertEqualsDouble(MergeText.jaroWinkler("dixon", "dicksonx"), 0.813, 0.001)
    assertEquals(MergeText.jaroWinkler("", "x"), 0.0)
    assertEquals(MergeText.jaroWinkler("same", "same"), 1.0)

  test("identifiers: a DOI key as V032 makes it; ISBN-10 to ISBN-13; a bad check digit is no ISBN"):
    assertEquals(MergeText.doiKey("https://doi.org/10.4324/9780203431566"), Some("10.4324/9780203431566"))
    assertEquals(MergeText.doiKey("doi: 10.1017/S0003"), Some("10.1017/s0003"))
    assertEquals(MergeText.doiKey("  "), None)
    assertEquals(MergeText.isbn13("0-415-01353-4"), Some("9780415013536"))
    assertEquals(MergeText.isbn13("978-0-415-01353-6"), Some("9780415013536"))
    assertEquals(MergeText.isbn13("978-0-415-01353-7"), None)
    assertEquals(MergeText.isbn13("080442957X"), Some("9780804429573"))

  test("MinHash: identical text 1, a light OCR difference high, an unrelated text low; deterministic"):
    val rnd  = scala.util.Random(7)
    val base = (1 to 2000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(6)).mkString).mkString(" ")
    val ocr  = base.split(" ").zipWithIndex.map((w, i) => if i % 97 == 0 then w.reverse else w).mkString(" ")
    val other = (1 to 2000).map(_ => rnd.alphanumeric.take(3 + rnd.nextInt(6)).mkString).mkString(" ")
    val (a, b, c) = (MergeText.minHash(base), MergeText.minHash(ocr), MergeText.minHash(other))
    assertEquals(a.similarity(MergeText.minHash(base)), 1.0)
    assert(a.similarity(b) >= 0.8, a.similarity(b).toString)
    assert(a.similarity(c) < 0.1, a.similarity(c).toString)
    assertEquals(MergeText.minHash(base), a)
    // a text shorter than a shingle is still a signature
    assertEquals(MergeText.minHash("short text").similarity(MergeText.minHash("Short, text!")), 1.0)
