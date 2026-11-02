package com.histmersion.api

/** The diacritic folding table and the citekeys built on it (#878). Shared, so it runs on the JVM AND on Scala.js — the
  * reason the table exists is that Scala.js has no `java.text.Normalizer`.
  */

import com.histmersion.api.library.Acquire
class LatinFoldSuite extends munit.FunSuite:

  test("Latin-1, Extended-A/B and Extended Additional letters fold to their base letter, case kept"):
    assertEquals(LatinFold.fold("Güterbock"), "Guterbock")
    assertEquals(LatinFold.fold("López-Ruiz"), "Lopez-Ruiz")
    assertEquals(LatinFold.fold("Łukasz Ćwikła"), "Lukasz Cwikla")
    assertEquals(LatinFold.fold("Ḫattuša"), "Hattusa")
    assertEquals(LatinFold.fold("Ṛgveda Śiva ṣaṭ"), "Rgveda Siva sat")
    assertEquals(LatinFold.fold("Dumézil Ǧ ǧ"), "Dumezil G g")
    assertEquals(LatinFold.fold("Nguyễn"), "Nguyen")

  test("letters NFD cannot decompose are mapped by hand, some to two letters"):
    assertEquals(LatinFold.fold("Straße Ærø Œuvre þing Đorđe"), "Strasse AEro OEuvre thing Dorde")
    assertEquals(LatinFold.fold("Baʿal ʾEl"), "Baal El")

  test("already-decomposed input loses its combining marks"):
    assertEquals(LatinFold.fold("Güterbock"), "Guterbock")

  test("everything else passes through unchanged"):
    val other = "Ζεύς Перун 123 _-/ abc ABC"
    assertEquals(LatinFold.fold(other), other)

  test("a folded letter is always plain ASCII"):
    val latin = ('À' to 'ɏ') ++ ('Ḁ' to 'ỿ')
    latin.foreach { c =>
      val f = LatinFold.fold(c.toString)
      assert(f == c.toString || f.forall(ch => ch < 128 && ch.isLetter) || f.isEmpty, s"$c → $f")
    }

  test("citekeys keep accented letters instead of dropping them (#878)"):
    assertEquals(Acquire.citekey(List("Hans Gustav Güterbock"), Some(1946), Some("Kumarbi")), "guterbock1946_kumarbi")
    assertEquals(
      Acquire.citekey(List("López-Ruiz, Carolina"), Some(2021), Some("Phoenicians")),
      "lopezruiz2021_phoenicians"
    )
    // the topic word used to be `tter` — the tail of `Götter` after the dropped `ö`
    assertEquals(Acquire.citekey(List("Walter Burkert"), Some(1977), Some("Über die Götter")), "burkert1977_uber")
    assertEquals(
      Acquire.citekey(List("Jaan Puhvel"), Some(1987), Some("Comparative Mythology")),
      "puhvel1987_comparative"
    )

  test("the pre-#878 key is still derivable, so records created under it are found"):
    assertEquals(
      Acquire.legacyCitekey(List("Hans Gustav Güterbock"), Some(1946), Some("Kumarbi")),
      "gterbock1946_kumarbi"
    )
    assertEquals(
      Acquire.citekeyCandidates(List("Güterbock"), Some(1946), Some("Kumarbi")),
      List("guterbock1946_kumarbi", "gterbock1946_kumarbi")
    )
    // unaccented metadata has one key, not two
    assertEquals(
      Acquire.citekeyCandidates(List("Haak"), Some(2015), Some("Massive migration")),
      List("haak2015_massive")
    )
