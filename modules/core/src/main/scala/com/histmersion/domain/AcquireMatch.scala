package com.histmersion
package domain

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.LatinFold
import com.histmersion.api.automation.Queue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Which open acquisition items a source that just came in fulfils (#970).
  *
  * An import used to tick its item off only when the caller named it (`--acquire-item`). Every successful ingest/import
  * job now also looks at the open items itself, and the two kinds of evidence are treated very differently, because
  * real data has near-misses of both kinds:
  *
  *   - the source's DOI written in the item ("(Brill, 2021; 10.1163/9789004441385)") is the SAME work: the item is
  *     CLOSED, `ingested as [[citekey]]`;
  *   - a similar title (plus the first author's surname) is only PROPOSED, with its score, and never closes anything. A
  *     preprint and its published version share a title and have different DOIs; an article and the author's later book
  *     often share most title words. Both are listed for a person to decide, and an item that names a DIFFERENT DOI
  *     says so.
  *
  * Pure: the job (server) supplies the items and the source, and writes what this decides.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryAcquisition)
object AcquireMatch:
  type DecimalText = DecimalText.T
  object DecimalText extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type DoiText = DoiText.T
  object DoiText extends Newtype[String, Pure]
  type MatchText = MatchText.T
  object MatchText extends Newtype[String, Pure]
  type ProposeThreshold = ProposeThreshold.T
  object ProposeThreshold extends Newtype[Double, Pure]
  type Surname = Surname.T
  object Surname extends Newtype[String, Pure]
  type Token = Token.T
  object Token extends Newtype[String, Pure]

  /** What the job does about one item. */
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ScoreAuthors = AuthorName
  val ScoreAuthors: AuthorName.type = AuthorName
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type DecideTitle = Title
  val DecideTitle: Title.type = Title
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type DecideAuthors = AuthorName
  val DecideAuthors: AuthorName.type = AuthorName

  enum Decision:
    /** The item names the source's DOI: it is closed. */
    case Close(item: Queue.QueueItem)

    /** The item's text resembles the source: listed, never closed. `otherDoi` = the item names a DOI, and it is not the
      * source's (a preprint vs its published version, say).
      */
    case Propose(item: Queue.QueueItem, score: Decision.Propose.Score, otherDoi: Option[Decision.Propose.OtherDoi])

  object Decision:
    object Propose:
      type Score = Score.T
      object Score extends Newtype[Double, Pure]
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type OtherDoi = Doi
      val OtherDoi: Doi.type = Doi

  private val DoiIn = """(?i)10\.\d{4,9}/\S+""".r

  /** The trailing characters a DOI written in prose drags along: sentence and list punctuation, quotes, and a closing
    * bracket that is not part of the DOI itself.
    */
  private val Trailing = ".,;:'\"!?>]}|*_`"

  /** A DOI as a key: lower case, no resolver prefix or `doi:` scheme, no trailing punctuation, and a closing
    * parenthesis kept only when the DOI opened one (`10.1002/(SICI)1097-…` keeps its pair).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def doiKey(raw: DoiText): Option[Doi] = doiKeyOf(raw.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def doiKeyOf(raw: String): Option[Doi] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
    DoiIn.findFirstIn(s).map(trim).map(_.toLowerCase).filter(_.contains('/')).flatMap(Doi.option(_))

  @annotation.tailrec
  private def trim(d: String): String =
    if d.isEmpty then d
    else
      val last = d.last
      if Trailing.contains(last) then trim(d.dropRight(1))
      else if last == ')' && d.count(_ == ')') > d.count(_ == '(') then trim(d.dropRight(1))
      else d

  /** Every DOI written anywhere in `text`, as keys. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def doisIn(text: DoiText): Set[Doi] = doisInText(text.value)

  private def doisInText(text: String): Set[Doi] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(doiKeyOf).toSet

  /** Words too common to say anything about which work a title names. */
  private val Stop = Set(
    "the",
    "and",
    "for",
    "from",
    "with",
    "into",
    "upon",
    "about",
    "between",
    "its",
    "their",
    "der",
    "die",
    "das",
    "und",
    "den",
    "dem",
    "von",
    "zur",
    "zum",
    "les",
    "des",
    "une",
    "aux",
    "del",
    "della",
    "dei",
    "las",
    "los",
    "new",
    "study",
    "studies",
    "book",
    "paper",
    "edition",
    "vol",
    "volume"
  )

  /** The distinctive words of a title or item text: accents folded, lower case, three letters or more (or a number,
    * like a year or a volume), stop words dropped.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tokens(text: MatchText): Set[Token] =
    tokensOf(text.value).map(Token(_))

  private def tokensOf(text: String): Set[String] =
    LatinFold
      .fold(Option(text).getOrElse(""))
      .toLowerCase
      .split("[^a-z0-9]+")
      .iterator
      .filter(w => (w.length >= 3 || w.forall(_.isDigit) && w.nonEmpty) && !Stop.contains(w))
      .toSet

  /** The first author's surname, folded: the part before the comma of `Last, First`, else the last word. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def surname(authors: List[AuthorName]): Option[Surname] =
    authors
      .map(_.value.trim)
      .find(_.nonEmpty)
      .map { a =>
        val last = if a.contains(",") then a.takeWhile(_ != ',') else a.split("\\s+").lastOption.getOrElse(a)
        LatinFold.fold(last).toLowerCase.replaceAll("[^a-z]", "")
      }
      .filter(_.length >= 2)
      .map(Surname(_))

  /** How much `itemText` looks like a request for the work `title` by `authors`, 0…1: the share of the title's
    * distinctive words the item contains (three quarters of the weight), and whether it names the first author's
    * surname (the rest). Without authors the title words are the whole score. Two decimals, so a listing is stable.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def score(itemText: MatchText, title: Title, authors: List[ScoreAuthors]): Decision.Propose.Score =
    scoreOf(itemText.value, title.value, authors)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def scoreOf(itemText: String, title: String, authors: List[AuthorName]): Decision.Propose.Score =
    val want = tokensOf(title)
    if want.isEmpty then Decision.Propose.Score(0.0)
    else
      val have = tokensOf(itemText)
      val words = want.count(have.contains).toDouble / want.size
      val raw = surname(authors) match
        case Some(s) => 0.75 * words + (if have.contains(s.value) then 0.25 else 0.0)
        case None    => words
      Decision.Propose.Score(math.round(raw * 100) / 100.0)

  /** The decision for every open ACQUISITION item, given the source's DOI, title and authors: DOI-exact items are
    * closed; other items scoring at least `proposeAt` are proposed, best first. Items already done, notes and other
    * lists are never touched.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decide(
      items: List[Queue.QueueItem],
      doi: Option[Doi],
      title: DecideTitle,
      authors: List[DecideAuthors],
      proposeAt: ProposeThreshold
  ): List[Decision] =
    val key = doi.flatMap(d => doiKeyOf(d.value))
    val open = items.filter(i => i.list == Queue.Kind.Acquire.label && i.status == Queue.QueueItem.Status(Queue.Open))
    val (closes, rest) =
      open.partition(i => key.exists(doisInText(i.text.value + " " + i.reason.map(w => s"$w").getOrElse("")).contains))
    val proposals = rest
      .flatMap { i =>
        val s = scoreOf(i.text.value, title.value, authors)
        Option.when(s.value >= proposeAt.value) {
          val named = doisInText(i.text.value).toList.sorted
          Decision.Propose(i, s, named.find(d => !key.contains(d)))
        }
      }
      .sortBy { case Decision.Propose(i, s, _) => (-s.value, i.id.value); case _ => (0.0, 0) }
    closes.map(Decision.Close(_)) ++ proposals

  /** One line per decision, for a job's progress and an import's output. `closed` maps an item to the outcome of the
    * write that closed it (`Left(reason)` when it could not be).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(d: Decision, closed: Queue.QueueItem.Id => Either[Problem, Unit] = _ => Right(())): Description = d match
    case Decision.Close(i) =>
      closed(i.id).fold(
        e => Description(s"acquisition item #${i.id} matches by DOI but was NOT ticked off: ${e.sentence}"),
        _ => Description(s"ticked off acquisition item #${i.id} (same DOI)")
      )
    case Decision.Propose(i, s, other) =>
      val whole = i.text.value.linesIterator.mkString(" ")
      val text = if whole.length > 100 then whole.take(99) + "…" else whole
      val doiNote = other.fold("")(d => s"; it names another DOI, $d")
      Description(
        s"possible acquisition item #${i.id} (similarity ${decimal(s)}$doiNote): $text — tick it off with " +
          s"`histmersion queue done ${i.id}` if it is this work"
      )

  /** `0.83`, with a dot whatever the default locale (`f"%.2f"` writes `0,83` under en_PL). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decimal(x: Decision.Propose.Score): DecimalText =
    val cents = math.round(x.value * 100)
    DecimalText(f"${cents / 100}.${cents % 100}%02d")
end AcquireMatch
