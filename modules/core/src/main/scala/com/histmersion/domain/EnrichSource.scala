package com.histmersion
package domain

import com.histmersion.domain.enrichsource.FindingProposal
import com.histmersion.domain.enrichsource.Window
import com.histmersion.domain.enrichsource.FindingGuards
import com.histmersion.domain.enrichsource.Loaded

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Content, Field, Invalid, Raw, Share, Where}
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import scala.annotation.tailrec
import com.histmersion.api.ListPatch
import com.histmersion.api.library.{FindingsPatch, MetadataPatch, WorkRef}
import com.histmersion.api.catalogue.{Geocode, Period, Year}
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.research.Slug
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The `enrich-source` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.2): what each
  * LLM step must return, how the body is cut into windows, how an answer is verified, and how the verified result
  * becomes ONE `SourcePatch`. The server runs the steps; every function here is pure and unit-tested.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryWorkflows)
object EnrichSource:
  type Century = Century.T
  object Century extends Newtype[Int, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type FindingCount = FindingCount.T
  object FindingCount extends Newtype[Int, Pure]
  type Last = Last.T
  object Last extends Newtype[Boolean, Pure]
  type MaxWindows = MaxWindows.T
  object MaxWindows extends Newtype[Int, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MaxAbsYear = AgentVerify.MaxAbsYear
  val MaxAbsYear: AgentVerify.MaxAbsYear.type = AgentVerify.MaxAbsYear
  export AgentVerify.{MinRatio, SourceBody}
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  export FindingProposal.Place as PlaceQuery
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type WindowChars = WindowChars.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object WindowChars extends Newtype[Int, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckClassificationMaxAbsYear = MaxAbsYear
  val CheckClassificationMaxAbsYear: MaxAbsYear.type = MaxAbsYear
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MentionsCenturyText = Text
  val MentionsCenturyText: Text.type = Text
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type StatesYearText = Text
  val StatesYearText: Text.type = Text
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CenturyAsYearText = Text
  val CenturyAsYearText: Text.type = Text
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckFindingsMaxAbsYear = MaxAbsYear
  val CheckFindingsMaxAbsYear: MaxAbsYear.type = MaxAbsYear
  type PatchNotes = PatchNotes.T
  object PatchNotes extends Newtype[String, Pure]

  import AgentVerify.*

  // ── pure logic ─────────────────────────────────────────────────────────────────────────────────

  private val Heading = "^#{1,6}\\s+(.+)$".r

  /** Cut `body` into paragraph-aligned windows of at most `windowChars`; when there are more than `maxWindows`, keep
    * `maxWindows` of them spread evenly from the first to the last (a book's argument is not all in chapter one). Each
    * window remembers the heading it falls under.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def windows(body: SourceBody, windowChars: WindowChars, maxWindows: MaxWindows): List[Window] =
    val size = math.max(500, windowChars.value)
    val paras = body.value
      .split("\\n\\s*\\n")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(p => if p.length <= size then List(p) else p.grouped(size).toList)
    // fold paragraphs into windows, tracking the last heading seen
    @tailrec def windows(
        rest: List[String],
        acc: Vector[(Option[String], String)],
        buf: String,
        bufHead: Option[String],
        lastHead: Option[String]
    ): List[(Option[String], String)] =
      rest match
        case Nil       => (if buf.isEmpty then acc else acc :+ (bufHead, buf)).toList
        case p :: tail =>
          val head = p.linesIterator.nextOption().collect { case Heading(h) => h.trim }.orElse(lastHead)
          if buf.isEmpty then windows(tail, acc, p, head, head)
          else if buf.length + 2 + p.length <= size then windows(tail, acc, buf + "\n\n" + p, bufHead, head)
          else windows(tail, acc :+ (bufHead, buf), p, head, head)
    val all = windows(paras, Vector.empty, "", None, None)
    val n = math.max(1, maxWindows.value)
    val picked =
      if all.size <= n then all
      else if n == 1 then List(all.head)
      else (0 until n).map(i => all(math.round(i.toDouble * (all.size - 1) / (n - 1)).toInt)).distinct.toList
    picked.zipWithIndex.map { case ((h, t), i) =>
      Window(Window.Index(i + 1), h.map(Window.Heading(_)), Window.Text(t))
    }

  /** Tags are lowercase_snake_case in this corpus (the `non-canonical-tag` lint rule). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseTag(t: Tag): Tag =
    Tag(t.value.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", ""))

  private val CenturyMention = "(?i)\\bcentur(?:y|ies)\\b|\\b\\d{1,2}\\s*(?:st|nd|rd|th)\\s*c\\.".r
  private val OrdinalDigits = "(?i)\\b(\\d{1,2})\\s*(?:st|nd|rd|th)\\b".r
  private val OrdinalWords = List(
    "first",
    "second",
    "third",
    "fourth",
    "fifth",
    "sixth",
    "seventh",
    "eighth",
    "ninth",
    "tenth",
    "eleventh",
    "twelfth",
    "thirteenth",
    "fourteenth",
    "fifteenth",
    "sixteenth",
    "seventeenth",
    "eighteenth",
    "nineteenth",
    "twentieth",
    "twenty-first"
  ).zipWithIndex.map((w, i) => w -> (i + 1)).toMap
  private val OrdinalWord = {
    val w = OrdinalWords.keys.toList.sortBy(-_.length).mkString("|")
    s"(?i)\\b($w)(?=(?:\\s*(?:-|–|and|or|to)\\s*(?:$w))?\\s+centur)".r
  }
  private val Era = "(?:AD|A\\.D\\.|CE|C\\.E\\.|BCE|B\\.C\\.E\\.|BC|B\\.C\\.)"

  /** The centuries `text` mentions ("11th century", "15th c.", "eleventh and twelfth centuries"), as their ordinal
    * numbers; empty when it mentions no century.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def centuriesMentioned(text: Text): List[Century] =
    val words = OrdinalWord
      .findAllMatchIn(text.value)
      .flatMap(m => OrdinalWords.get(m.group(1).toLowerCase(java.util.Locale.ROOT)))
      .toList
    val digits =
      if CenturyMention.findFirstIn(text.value).isDefined then
        OrdinalDigits.findAllMatchIn(text.value).map(_.group(1).toInt).toList
      else Nil
    (digits ++ words).filter(_ > 0).distinct.map(Century(_))

  /** Whether `text` mentions a century at all (an ordinal or just the word). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mentionsCentury(text: MentionsCenturyText): Boolean =
    CenturyMention.findFirstIn(text.value).isDefined || OrdinalWord.findFirstIn(text.value).isDefined

  /** Whether `text` states `year` (its absolute value) as a year with its era: "79 CE", "AD 79", "79 B.C.". */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statesYear(text: StatesYearText, year: Year): Boolean =
    val n = math.abs(year.value).toString
    s"(?i)(?:\\b$Era\\s*$n(?!\\d)|(?<!\\d)$n\\s*$Era(?![A-Za-z]))".r.findFirstIn(text.value).isDefined

  /** A year below 100 that is almost surely a century stored as a year (#1424): the finding mentions a century and the
    * year is not inside one it mentions ("1st century" = 1..100 is right), or the source's own period starts at least
    * `gap` years after it. A year the text states with its era ("79 CE") is valid. The problem names the century as
    * years, e.g. "'11th century' is the years 1001–1100 (BCE negative)".
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def centuryAsYear(
      start: Year,
      end: Year,
      text: CenturyAsYearText,
      within: Option[Period],
      gap: FindingGuards.CenturyGapYears
  ): Option[Content.Requirement] =
    val centuries = centuriesMentioned(text)
    def inMentioned(y: Year) = centuries.exists { c =>
      val a = math.abs(y.value)
      a >= (c.value - 1L) * 100 + 1 && a <= c.value * 100L
    }
    val suspicious = List(start, end).distinct.filter { y =>
      math.abs(y.value) < 100 && !statesYear(text, y) &&
      ((mentionsCentury(text) && !inMentioned(y)) || within.exists(p => p.start.value - math.abs(y.value) >= gap.value))
    }
    suspicious.headOption.map { y =>
      val c = centuries
        .find(_.value == math.abs(y.value))
        .orElse(centuries.headOption)
        .map(_.value.toLong)
        .getOrElse(math.abs(y.value))
      Content.Requirement.CenturyAsYear(Raw.of(y), Problem.Count(c))
    }

  /** The placement guard: of the proposals that name a place, how many resolve; a problem when fewer than `minShare`
    * do, listing the names that did not.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def placementProblem(
      named: List[(Problem.Count, FindingProposal.Place, Boolean)],
      minShare: FindingGuards.MinPlacedShare
  ): Option[Problem] =
    val total = named.size
    val resolved = named.count(_._3)
    val least = minShare.value
    Option.when(total > 0 && least > 0 && resolved.toDouble < least * total) {
      val missing = named.filterNot(_._3).toVector.map((i, p, _) => Content.Unplaced(Raw.of(p), i))
      Content.Answer(
        Content.Requirement.PlacesResolve(
          Problem.Count(resolved.toLong),
          Problem.Count(total.toLong),
          Problem.Count(math.round(least * 100)),
          missing
        )
      )
    }

  /** Our record against the upstream one (OpenAlex/Crossref by DOI): title, year, first author, and the citekey's own
    * year (the `year-citekey-mismatch` lint rule). Each difference is a sentence a person can act on; an empty list =
    * they agree. Nothing is written from here — a rename or a metadata fix stays a person's call.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def compareMetadata(
      citekey: Citekey,
      title: Title,
      authors: List[AuthorName],
      year: Option[com.histmersion.Year],
      upstream: WorkRef
  ): List[Description] =
    def surname(a: String): String =
      val t = a.trim
      val s = if t.contains(",") then t.takeWhile(_ != ',') else t.split("\\s+").lastOption.getOrElse(t)
      AgentVerify.normalise(AgentVerify.Text(s)).value
    val ourYear = year.map(y => Year(y.value.toLong))
    val titleDiff = upstream.title
      .filter(t => !AgentVerify.sameTitle(Title(t.value), title))
      .map(t => s"title differs from the upstream record: \"$t\"")
    val yearDiff = for u <- upstream.year; y <- ourYear if u != y yield s"year $y differs from the upstream record's $u"
    val yearMissing =
      Option.when(year.isEmpty)(upstream.year.map(u => s"no year recorded; the upstream record says $u")).flatten
    val authorDiff = upstream.authors.headOption
      .filter(a => authors.nonEmpty && !authors.map(x => surname(x.value)).contains(surname(a.value)))
      .map(a => s"the upstream first author ${a.value.trim} is not among the recorded authors")
    val keyYear = Lint
      .yearCitekeyMismatch(List(com.histmersion.api.library.Citekey(citekey.value) -> upstream.year.orElse(ourYear)))
      .map(i => s"citekey year: ${i.detail} — rename the source by hand if the record is right")
    ((titleDiff ++ yearDiff ++ yearMissing ++ authorDiff).toList ++ keyYear).map(Description(_))

  /** The lint rules `close` runs for this one source (`no-findings`, `no-period`, `unassessed-status`): each hit is a
    * run warning.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def closeLint(findings: FindingCount, hasPeriod: Loaded.HasPeriod, status: Source.Status): List[Description] =
    List(
      Option.when(findings.value == 0)(s"${Lint.NoFindings}: the source has no findings"),
      Option.when(!hasPeriod.value)(s"${Lint.NoPeriod}: the source has no period"),
      Option.when(status == Source.Status.Unassessed)(s"${Lint.UnassessedStatus}: the status is still unassessed")
    ).flatten.map(Description(_))
