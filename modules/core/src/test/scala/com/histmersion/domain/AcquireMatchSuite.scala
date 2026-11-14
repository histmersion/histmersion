package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AcquireMatch.Decision
import com.histmersion.api.automation.Queue.QueueItem
import com.histmersion.api.automation.Queue
import com.histmersion.domain.library.Doi

/** #970: which open acquisition items an import closes (its DOI, exactly) and which it only proposes (a similar title
  * and first author) — with the false positives seen in the real queue.
  */
class AcquireMatchSuite extends munit.FunSuite:

  private def item(
      id: QueueItem.Id,
      text: QueueItem.Text,
      status: QueueItem.Status = QueueItem.Status(Queue.Open),
      list: QueueItem.List = Queue.Kind.Acquire.label
  ) =
    QueueItem(id, list, text, Some(QueueItem.Topic("Books")), status)

  private val Brill = item(
    QueueItem.Id(1),
    QueueItem.Text("**Kristiansen, The Rise of Bronze Age Society** (Brill, 2021; 10.1163/9789004441385)")
  )

  test("a DOI is a key: case, resolver prefix, `doi:` scheme and trailing punctuation do not matter"):
    List(
      "10.1163/9789004441385",
      "10.1163/9789004441385.",
      "10.1163/9789004441385);",
      "https://doi.org/10.1163/9789004441385",
      "doi:10.1163/9789004441385",
      "DOI: 10.1163/9789004441385,",
      "https://dx.doi.org/10.1163/9789004441385)"
    )
      .foreach(raw => assertEquals(AcquireMatch.doiKey(AcquireMatch.DoiText(raw)), Some("10.1163/9789004441385"), raw))
    assertEquals(
      AcquireMatch.doiKey(AcquireMatch.DoiText("10.1017/S0003598X00012345")),
      Some("10.1017/s0003598x00012345")
    )
    // a parenthesis that belongs to the DOI is kept
    assertEquals(
      AcquireMatch.doiKey(AcquireMatch.DoiText("10.1002/(SICI)1097-4679(199806)54:4<431::AID-JCLP3>3.0.CO;2-I).")),
      Some("10.1002/(sici)1097-4679(199806)54:4<431::aid-jclp3>3.0.co;2-i")
    )
    assertEquals(AcquireMatch.doiKey(AcquireMatch.DoiText("no doi here")), None)

  test("every DOI written in an item's text is found"):
    assertEquals(
      AcquireMatch.doisIn(AcquireMatch.DoiText("see 10.1163/9789004441385 and (10.1017/ABC.2020.1).")),
      Set("10.1163/9789004441385", "10.1017/abc.2020.1")
    )

  test("an item naming the source's DOI is CLOSED — whatever case or prefix either side writes it in"):
    val ds = AcquireMatch.decide(
      List(Brill),
      Some(Doi.assume("https://doi.org/10.1163/9789004441385")),
      AcquireMatch.DecideTitle("The Rise of Bronze Age Society"),
      List(AuthorName("Kristiansen, Kristian")),
      AcquireMatch.ProposeThreshold(0.6)
    )
    assertEquals(ds, List(Decision.Close(Brill)))
    val upper = item(QueueItem.Id(2), QueueItem.Text("Kristiansen 2021 (DOI 10.1163/9789004441385.)"))
    assertEquals(
      AcquireMatch.decide(
        List(upper),
        Some(Doi("10.1163/9789004441385")),
        AcquireMatch.DecideTitle("x"),
        Nil,
        AcquireMatch.ProposeThreshold(0.6)
      ),
      List(Decision.Close(upper))
    )

  test("a DOI in the item's reason closes it too"):
    val withReason = item(QueueItem.Id(3), QueueItem.Text("Kristiansen's Brill volume"))
      .modify(_.reason)
      .setTo(Some(Queue.Rationale("doi 10.1163/9789004441385")))
    assertEquals(
      AcquireMatch.decide(
        List(withReason),
        Some(Doi("10.1163/9789004441385")),
        AcquireMatch.DecideTitle("x"),
        Nil,
        AcquireMatch.ProposeThreshold(0.6)
      ),
      List(Decision.Close(withReason))
    )

  test("a similar title is only PROPOSED, with its score, and never closed"):
    val noDoi = item(QueueItem.Id(4), QueueItem.Text("Anthony — The Horse, the Wheel, and Language (Princeton 2007)"))
    AcquireMatch.decide(
      List(noDoi),
      Some(Doi("10.1515/9781400831104")),
      AcquireMatch.DecideTitle("The Horse, the Wheel, and Language"),
      List(AuthorName("Anthony, David W.")),
      AcquireMatch.ProposeThreshold(0.6)
    ) match
      case List(Decision.Propose(i, score, other)) =>
        assertEquals(i, noDoi); assertEquals(score, 1.0); assertEquals(other, None)
      case other => fail(s"expected one proposal, got $other")

  test("a preprint is not its published version: a different DOI with a near-identical title is proposed, not closed"):
    val published = item(
      QueueItem.Id(5),
      QueueItem.Text(
        "Haak et al., Massive migration from the steppe was a source for Indo-European languages " +
          "in Europe (Nature 2015; 10.1038/nature14317)"
      )
    )
    val ds = AcquireMatch.decide(
      List(published),
      Some(Doi("10.1101/013433")),
      AcquireMatch.DecideTitle("Massive migration from the steppe is a source for Indo-European languages in Europe"),
      List(AuthorName("Haak, Wolfgang")),
      AcquireMatch.ProposeThreshold(0.6)
    )
    ds match
      case List(Decision.Propose(_, score, Some(other))) if other.value == "10.1038/nature14317" =>
        assert(score.value >= 0.6, score.toString)
      case other => fail(s"expected a proposal naming the other DOI, got $other")
    assert(
      AcquireMatch.describe(ds.head).value.contains("another DOI, 10.1038/nature14317"),
      AcquireMatch.describe(ds.head)
    )

  test("an article and the author's later book with a similar title: proposed at most, never closed"):
    val book =
      item(QueueItem.Id(6), QueueItem.Text("Cline — 1177 B.C.: The Year Civilization Collapsed (Princeton, 2014)"))
    val ds = AcquireMatch.decide(
      List(book),
      Some(Doi("10.2307/j.ctt5hhn5x")),
      AcquireMatch.DecideTitle("1177 BC: the year civilisation collapsed revisited"),
      List(AuthorName("Cline, Eric H.")),
      AcquireMatch.ProposeThreshold(0.6)
    )
    assert(ds.forall { case Decision.Close(_) => false; case _ => true }, ds.toString)

  test("unrelated items, done items, notes and other lists are left alone"):
    val ds = AcquireMatch.decide(
      List(
        item(QueueItem.Id(7), QueueItem.Text("Burkert, Greek Religion")),
        item(QueueItem.Id(8), QueueItem.Text("10.1163/9789004441385"), status = QueueItem.Status(Queue.Done)),
        item(QueueItem.Id(9), QueueItem.Text("10.1163/9789004441385"), status = QueueItem.Status(Queue.Note)),
        item(QueueItem.Id(10), QueueItem.Text("10.1163/9789004441385"), list = Queue.Kind.Tasks.label)
      ),
      Some(Doi("10.1163/9789004441385")),
      AcquireMatch.DecideTitle("The Rise of Bronze Age Society"),
      List(AuthorName("Kristiansen, Kristian")),
      AcquireMatch.ProposeThreshold(0.6)
    )
    assertEquals(ds, Nil)

  test("proposals come best first; the threshold is respected"):
    val a = item(QueueItem.Id(11), QueueItem.Text("Rise of Bronze Age society, Kristiansen"))
    val b = item(QueueItem.Id(12), QueueItem.Text("Bronze Age society in Scandinavia"))
    val ds = AcquireMatch.decide(
      List(b, a),
      None,
      AcquireMatch.DecideTitle("The Rise of Bronze Age Society"),
      List(AuthorName("Kristiansen, K.")),
      AcquireMatch.ProposeThreshold(0.3)
    )
    assertEquals(ds.collect { case Decision.Propose(i, _, _) => i.id }, List(11, 12))
    assertEquals(
      AcquireMatch.decide(
        List(b),
        None,
        AcquireMatch.DecideTitle("The Rise of Bronze Age Society"),
        List(AuthorName("Kristiansen, K.")),
        AcquireMatch.ProposeThreshold(0.9)
      ),
      Nil
    )

  test("score: title words share, the surname, accents folded"):
    assertEquals(
      AcquireMatch.score(
        AcquireMatch.MatchText("Güterbock, Hittite myths"),
        Title("Hittite Myths"),
        List(AuthorName("Guterbock, Hans"))
      ),
      1.0
    )
    assertEquals(
      AcquireMatch
        .score(AcquireMatch.MatchText("Hittite myths"), Title("Hittite Myths"), List(AuthorName("Guterbock, Hans"))),
      0.75
    )
    assertEquals(AcquireMatch.score(AcquireMatch.MatchText("anything"), Title(""), Nil), 0.0)
    assertEquals(AcquireMatch.surname(List(AuthorName("Clunies Ross, Margaret"))), Some("cluniesross"))
    assertEquals(AcquireMatch.surname(List(AuthorName("David W. Anthony"))), Some("anthony"))

  test("a score renders with a dot whatever the locale"):
    assertEquals(AcquireMatch.decimal(Decision.Propose.Score(0.8)), "0.80")
    assertEquals(AcquireMatch.decimal(Decision.Propose.Score(1.0)), "1.00")
    assertEquals(AcquireMatch.decimal(Decision.Propose.Score(0.05)), "0.05")

  test("describe: a closed item, one that could not be closed, a proposal"):
    assertEquals(AcquireMatch.describe(Decision.Close(Brill)), "ticked off acquisition item #1 (same DOI)")
    assert(
      AcquireMatch
        .describe(Decision.Close(Brill), _ => Left(Problem.Unclassified(Legacy.Text("gone"))))
        .value
        .contains("NOT ticked off: gone")
    )
    val line = AcquireMatch.describe(
      Decision.Propose(item(QueueItem.Id(4), QueueItem.Text("Anthony — The Horse")), Decision.Propose.Score(0.83), None)
    )
    assert(line.value.startsWith("possible acquisition item #4 (similarity 0.83)"), line)
    assert(line.value.contains("histmersion queue done 4"), line)
