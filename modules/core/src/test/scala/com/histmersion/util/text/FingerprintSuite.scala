package com.histmersion
package util.text

import com.histmersion.testsupport.ComparedByValue.given

class FingerprintSuite extends munit.FunSuite:

  test("folding: lower case, diacritics and the letters NFD keeps removed, typographic quotes made ASCII"):
    assertEquals(Fingerprint.fold(Fingerprint.FoldS("Güterbock’s Łódź")), "guterbock's lodz")

  test("tokens: the folded letters and digits, in order"):
    assertEquals(
      Fingerprint.tokens(Fingerprint.TokensS("The Fall of Ḫattuša, 1180 BC!")),
      List("the", "fall", "of", "hattusa", "1180", "bc")
    )

  test("a fingerprint ignores order, repetition, case and punctuation"):
    assertEquals(Fingerprint.of(Fingerprint.OfS("Fall of Hattusa")).value, "fall hattusa of")
    assertEquals(
      Fingerprint.of(Fingerprint.OfS("hattusa, FALL of — of")),
      Fingerprint.of(Fingerprint.OfS("Fall of Hattusa"))
    )
    assertNotEquals(
      Fingerprint.of(Fingerprint.OfS("Sack of Hattusa")),
      Fingerprint.of(Fingerprint.OfS("Fall of Hattusa"))
    )
    assertEquals(Fingerprint.of(Fingerprint.OfS("  ")).value, "")
