package com.histmersion.util.text

class FingerprintSuite extends munit.FunSuite:

  test("folding: lower case, diacritics and the letters NFD keeps removed, typographic quotes made ASCII"):
    assertEquals(Fingerprint.fold(com.histmersion.util.text.Fingerprint.FoldS("Güterbock’s Łódź")), "guterbock's lodz")

  test("tokens: the folded letters and digits, in order"):
    assertEquals(
      Fingerprint.tokens(com.histmersion.util.text.Fingerprint.TokensS("The Fall of Ḫattuša, 1180 BC!")),
      List("the", "fall", "of", "hattusa", "1180", "bc")
    )

  test("a fingerprint ignores order, repetition, case and punctuation"):
    assertEquals(Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("Fall of Hattusa")).value, "fall hattusa of")
    assertEquals(Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("hattusa, FALL of — of")), Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("Fall of Hattusa")))
    assertNotEquals(Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("Sack of Hattusa")), Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("Fall of Hattusa")))
    assertEquals(Fingerprint.of(com.histmersion.util.text.Fingerprint.OfS("  ")).value, "")
