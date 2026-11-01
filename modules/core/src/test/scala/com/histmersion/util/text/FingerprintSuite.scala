package com.histmersion.util.text

class FingerprintSuite extends munit.FunSuite:

  test("folding: lower case, diacritics and the letters NFD keeps removed, typographic quotes made ASCII"):
    assertEquals(Fingerprint.fold("Güterbock’s Łódź"), "guterbock's lodz")

  test("tokens: the folded letters and digits, in order"):
    assertEquals(Fingerprint.tokens("The Fall of Ḫattuša, 1180 BC!"), List("the", "fall", "of", "hattusa", "1180", "bc"))

  test("a fingerprint ignores order, repetition, case and punctuation"):
    assertEquals(Fingerprint.of("Fall of Hattusa").value, "fall hattusa of")
    assertEquals(Fingerprint.of("hattusa, FALL of — of"), Fingerprint.of("Fall of Hattusa"))
    assertNotEquals(Fingerprint.of("Sack of Hattusa"), Fingerprint.of("Fall of Hattusa"))
    assertEquals(Fingerprint.of("  ").value, "")
