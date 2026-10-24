package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AcquireMatch.Decision
import com.histmersion.api.Queue.QueueItem

/** #970: which open acquisition items an import closes (its DOI, exactly) and which it only proposes
  * (a similar title and first author) — with the false positives seen in the real queue. */
class AcquireMatchSuite extends munit.FunSuite:

  private def item(id: Int, text: String, status: String = Queue.Open, list: String = "acquire") =
    QueueItem(id, list, text, Some("Books"), status)

  private val Brill = item(1, "**Kristiansen, The Rise of Bronze Age Society** (Brill, 2021; 10.1163/9789004441385)")

  test("a DOI is a key: case, resolver prefix, `doi:` scheme and trailing punctuation do not matter"):
    List("10.1163/9789004441385", "10.1163/9789004441385.", "10.1163/9789004441385);", "https://doi.org/10.1163/9789004441385",
      "doi:10.1163/9789004441385", "DOI: 10.1163/9789004441385,", "https://dx.doi.org/10.1163/9789004441385)")
      .foreach(raw => assertEquals(AcquireMatch.doiKey(raw), Some("10.1163/9789004441385"), raw))
    assertEquals(AcquireMatch.doiKey("10.1017/S0003598X00012345"), Some("10.1017/s0003598x00012345"))
    // a parenthesis that belongs to the DOI is kept
    assertEquals(AcquireMatch.doiKey("10.1002/(SICI)1097-4679(199806)54:4<431::AID-JCLP3>3.0.CO;2-I)."),
      Some("10.1002/(sici)1097-4679(199806)54:4<431::aid-jclp3>3.0.co;2-i"))
    assertEquals(AcquireMatch.doiKey("no doi here"), None)

  test("every DOI written in an item's text is found"):
    assertEquals(AcquireMatch.doisIn("see 10.1163/9789004441385 and (10.1017/ABC.2020.1)."),
      Set("10.1163/9789004441385", "10.1017/abc.2020.1"))

  test("an item naming the source's DOI is CLOSED — whatever case or prefix either side writes it in"):
    val ds = AcquireMatch.decide(List(Brill), Some("https://doi.org/10.1163/9789004441385"), "The Rise of Bronze Age Society",
      List("Kristiansen, Kristian"), 0.6)
    assertEquals(ds, List(Decision.Close(Brill)))
    val upper = item(2, "Kristiansen 2021 (DOI 10.1163/9789004441385.)")
    assertEquals(AcquireMatch.decide(List(upper), Some("10.1163/9789004441385"), "x", Nil, 0.6), List(Decision.Close(upper)))

  test("a DOI in the item's reason closes it too"):
    val withReason = item(3, "Kristiansen's Brill volume").copy(reason = Some("doi 10.1163/9789004441385"))
    assertEquals(AcquireMatch.decide(List(withReason), Some("10.1163/9789004441385"), "x", Nil, 0.6),
      List(Decision.Close(withReason)))

  test("a similar title is only PROPOSED, with its score, and never closed"):
    val noDoi = item(4, "Anthony — The Horse, the Wheel, and Language (Princeton 2007)")
    AcquireMatch.decide(List(noDoi), Some("10.1515/9781400831104"), "The Horse, the Wheel, and Language",
      List("Anthony, David W."), 0.6) match
      case List(Decision.Propose(i, score, other)) =>
        assertEquals(i, noDoi); assertEquals(score, 1.0); assertEquals(other, None)
      case other => fail(s"expected one proposal, got $other")

  test("a preprint is not its published version: a different DOI with a near-identical title is proposed, not closed"):
    val published = item(5, "Haak et al., Massive migration from the steppe was a source for Indo-European languages " +
      "in Europe (Nature 2015; 10.1038/nature14317)")
    val ds = AcquireMatch.decide(List(published), Some("10.1101/013433"),
      "Massive migration from the steppe is a source for Indo-European languages in Europe", List("Haak, Wolfgang"), 0.6)
    ds match
      case List(Decision.Propose(_, score, Some("10.1038/nature14317"))) => assert(score >= 0.6, score.toString)
      case other => fail(s"expected a proposal naming the other DOI, got $other")
    assert(AcquireMatch.describe(ds.head).contains("another DOI, 10.1038/nature14317"), AcquireMatch.describe(ds.head))

  test("an article and the author's later book with a similar title: proposed at most, never closed"):
    val book = item(6, "Cline — 1177 B.C.: The Year Civilization Collapsed (Princeton, 2014)")
    val ds = AcquireMatch.decide(List(book), Some("10.2307/j.ctt5hhn5x"), "1177 BC: the year civilisation collapsed revisited",
      List("Cline, Eric H."), 0.6)
    assert(ds.forall { case Decision.Close(_) => false; case _ => true }, ds.toString)

  test("unrelated items, done items, notes and other lists are left alone"):
    val ds = AcquireMatch.decide(
      List(item(7, "Burkert, Greek Religion"), item(8, "10.1163/9789004441385", status = Queue.Done),
        item(9, "10.1163/9789004441385", status = Queue.Note), item(10, "10.1163/9789004441385", list = "tasks")),
      Some("10.1163/9789004441385"), "The Rise of Bronze Age Society", List("Kristiansen, Kristian"), 0.6)
    assertEquals(ds, Nil)

  test("proposals come best first; the threshold is respected"):
    val a = item(11, "Rise of Bronze Age society, Kristiansen")
    val b = item(12, "Bronze Age society in Scandinavia")
    val ds = AcquireMatch.decide(List(b, a), None, "The Rise of Bronze Age Society", List("Kristiansen, K."), 0.3)
    assertEquals(ds.collect { case Decision.Propose(i, _, _) => i.id }, List(11, 12))
    assertEquals(AcquireMatch.decide(List(b), None, "The Rise of Bronze Age Society", List("Kristiansen, K."), 0.9), Nil)

  test("score: title words share, the surname, accents folded"):
    assertEquals(AcquireMatch.score("Güterbock, Hittite myths", "Hittite Myths", List("Guterbock, Hans")), 1.0)
    assertEquals(AcquireMatch.score("Hittite myths", "Hittite Myths", List("Guterbock, Hans")), 0.75)
    assertEquals(AcquireMatch.score("anything", "", Nil), 0.0)
    assertEquals(AcquireMatch.surname(List("Clunies Ross, Margaret")), Some("cluniesross"))
    assertEquals(AcquireMatch.surname(List("David W. Anthony")), Some("anthony"))

  test("a score renders with a dot whatever the locale"):
    assertEquals(AcquireMatch.decimal(0.8), "0.80")
    assertEquals(AcquireMatch.decimal(1.0), "1.00")
    assertEquals(AcquireMatch.decimal(0.05), "0.05")

  test("describe: a closed item, one that could not be closed, a proposal"):
    assertEquals(AcquireMatch.describe(Decision.Close(Brill)), "ticked off acquisition item #1 (same DOI)")
    assert(AcquireMatch.describe(Decision.Close(Brill), _ => Left("gone")).contains("NOT ticked off: gone"))
    val line = AcquireMatch.describe(Decision.Propose(item(4, "Anthony — The Horse"), 0.83, None))
    assert(line.startsWith("possible acquisition item #4 (similarity 0.83)"), line)
    assert(line.contains("histmersion queue done 4"), line)
