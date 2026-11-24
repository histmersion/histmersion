package com.histmersion
package domain.library.sources

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.domain.{AuthorName, MergeSettings, Title}
import com.histmersion.domain.library.Isbn
import com.histmersion.Sha256
import com.histmersion.util.data.exchange.MatchWeights

import scala.annotation.tailrec
import com.histmersion.api.given

import MergeText.AuthorAgreement
import com.histmersion.util.text.{Fingerprint, MinHash}

import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.Bundle.uidEquality
import com.histmersion.lint.{LintRule, tolerated}

/** Is a bundle's source the same work as one here? (docs/research-merge-import.md §5.2, stages B–D.) Pure.
  *
  *   - **B, strong keys** decide: the same DOI, the same original file (sha256) or a shared ISBN is a match whatever
  *     the score. A DOI on one side only is neutral; two different DOIs are a hard flag.
  *   - **C, blocking**: only pairs that share a title key, a first-author surname key, a citekey stem or a label
  *     (citekey or alias) are scored.
  *   - **D, score**: a weight per field comparison ([[MatchWeights]]), plus one when a citekey of one side is an old
  *     citekey (alias) of the other.
  *
  * Then each bundle source is assigned at most one source here, best first (strong keys, then the highest score), and
  * classified: auto-merge, needs-decision, or new.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SourceMatcher:
  /** A number written out in the evidence text: a weight or a similarity. */
  type Figure = Figure.T
  object Figure extends Newtype[Double, Pure]

  /** How many decimals a figure is written with. */
  type Decimals = Decimals.T
  object Decimals extends Newtype[Int, Pure]

  /** A figure as it is written out. */
  type Written = Written.T
  object Written extends Newtype[String, Pure]

  /** A blocking key: the pairs that share one are compared. */
  type BlockingKey = BlockingKey.T
  object BlockingKey extends Newtype[String, Pure]

  /** The letters of a citekey before its year. */
  type Stem = Stem.T
  object Stem extends Newtype[String, Pure]

  /** A source with the old citekeys (aliases) that still name it. */
  final case class Side(source: Bundle.Source, aliases: Set[Citekey]):
    def uid: UUID = source.uid
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def aliasLabels: Set[Side.Label] = aliases.map(a => Side.Label(a.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def labels: Set[Side.Label] = aliasLabels + Side.Label(source.citekey.value)

  object Side:
    /** A citekey a source is known by: its own, or an old one. */
    type Label = Label.T
    object Label extends Newtype[String, Pure]

  import Evidence.{Rationale, Reason}

  /** The evidence about one pair. `parts` is the score's waterfall, in the order it was added up. */
  final case class Evidence(
      strong: List[Evidence.Strong],
      doiConflict: Evidence.DoiConflict,
      yearGap: Evidence.YearGap,
      titleDiffers: Evidence.TitleDiffers,
      authors: AuthorAgreement,
      parts: List[(Reason, Evidence.Weight)]
  ):
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def score: Evidence.Score = Evidence.Score(parts.map(_._2.value).sum)
    def strongKey: Boolean = strong.nonEmpty
    def why: List[Rationale] = strong.map(Rationale.StrongKey(_)) ++ parts.map((r, w) => Rationale.Part(r, w))

  object Evidence:
    /** The sum of the weights of the parts. */
    type Score = Score.T
    object Score extends Newtype[Double, Pure]

    /** The name of a strong key (`DOI`, `ISBN`). */
    type Strong = Strong.T
    object Strong extends Newtype[String, Pure]
    type DoiConflict = DoiConflict.T
    object DoiConflict extends Newtype[Boolean, Pure]
    type YearGap = YearGap.T
    object YearGap extends Newtype[Boolean, Pure]
    type TitleDiffers = TitleDiffers.T
    object TitleDiffers extends Newtype[Boolean, Pure]

    /** What a comparison adds to the score. */
    type Weight = Weight.T
    object Weight extends Newtype[Double, Pure]

    /** What one field comparison found: a part of the score's waterfall. */
    enum Reason:
      case TitleSame, MainTitleSame

      /** The titles' Jaro-Winkler similarity is `jaroWinkler`, at least the setting's. */
      case TitlesSimilar(jaroWinkler: Figure)

      /** The titles' words overlap by `jaccard` (Jaccard), at least the setting's. */
      case TitleWordsOverlap(jaccard: Figure)

      case TitlesDiffer, AuthorsSame, FirstAuthorSame, AuthorsOverlap, NoAuthorInCommon, YearSame, YearsOneApart

      /** The years are `right` (the bundle's) and `left` (here's), and one side speaks of a reprint or edition. */
      case YearsReprint(right: Year, left: Year)

      /** The years are `right` (the bundle's) and `left` (here's), two or more apart. */
      case YearsApart(right: Year, left: Year)

      case VenueSame, VenuesDiffer, BodySame

      /** The OCR bodies' MinHash similarity is `minHash`: alike, partly alike, or different. */
      case BodiesAlike(minHash: Figure)
      case BodiesPartlyAlike(minHash: Figure)
      case BodiesDiffer(minHash: Figure)

      /** `label`, a citekey of one side, is an old citekey (alias) of the other. */
      case OldCitekey(label: Side.Label)

      /** The part of the score this comparison is: it adds `weight`. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      private[SourceMatcher] def weighs(weight: Double): (Reason, Weight) = (this, Weight(weight))

      /** The comparison as the evidence lists it. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def said: String = this match
        case TitleSame                 => "title the same"
        case MainTitleSame             => "main title the same, the subtitle differs"
        case TitlesSimilar(jw)         => s"titles similar (Jaro-Winkler ${fixed(jw, Decimals(2))})"
        case TitleWordsOverlap(tj)     => s"title words overlap (Jaccard ${fixed(tj, Decimals(2))})"
        case TitlesDiffer              => "titles differ"
        case AuthorsSame               => "authors the same"
        case FirstAuthorSame           => "first author the same"
        case AuthorsOverlap            => "authors overlap"
        case NoAuthorInCommon          => "no author in common"
        case YearSame                  => "year the same"
        case YearsOneApart             => "years one apart"
        case YearsReprint(right, left) => s"years $right and $left, a reprint or edition"
        case YearsApart(right, left)   => s"years $right and $left"
        case VenueSame                 => "venue the same"
        case VenuesDiffer              => "venues differ"
        case BodySame                  => "the same OCR body"
        case BodiesAlike(j)            => s"OCR bodies alike (MinHash ${fixed(j, Decimals(2))})"
        case BodiesPartlyAlike(j)      => s"OCR bodies partly alike (MinHash ${fixed(j, Decimals(2))})"
        case BodiesDiffer(j)           => s"OCR bodies differ (MinHash ${fixed(j, Decimals(2))})"
        case OldCitekey(label)         => s"'$label' is an old citekey of the other"

    /** Why a pair matches or not, line by line: the account of the matcher's decision, not a failure. */
    enum Rationale:
      /** The two sides share the strong key `key`: a match whatever the score. */
      case StrongKey(key: Strong)

      /** A comparison found `reason`, which adds `weight` to the score. */
      case Part(reason: Reason, weight: Weight)

      /** The line as the merge plan shows it. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def said: String = this match
        case StrongKey(key)       => s"$key equal (strong key)"
        case Part(reason, weight) => s"${reason.said} (${signed(weight)})"

  /** `x` with `places` decimals, a dot whatever the JVM's locale. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fixed(x: Figure, places: Decimals): Written = Written(s"%.${places}f".formatLocal(java.util.Locale.ROOT, x.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def signed(w: Evidence.Weight): Written =
    val s = if w.value == math.rint(w.value) then w.value.toLong.toString else fixed(Figure(w.value), Decimals(1)).value
    Written(if w.value >= 0 then s"+$s" else s)

  // ── blocking ────────────────────────────────────────────────────────────────────────────────

  private val Year = "(1[0-9]{3}|20[0-9]{2})".r

  /** The letters of a citekey before its year (`guterbock1997_perspectives` → `guterbock`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citekeyStem(ck: Side.Label): Stem = Stem(ck.value.takeWhile(c => !c.isDigit).filter(_.isLetter))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def titleOf(s: Bundle.Source): Title = Title(s.title.value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def titleText(s: Bundle.Source): MergeText.Text = MergeText.Text(s.title.value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def authorsOf(s: Bundle.Source): List[AuthorName] = s.authors.map(a => AuthorName(a.value))

  /** The keys a pair must share one of to be compared. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def blockingKeys(s: Side): Set[BlockingKey] =
    val src = s.source
    val keys: Set[String] =
      Set("t:" + MergeText.titleKey(titleOf(src)).value).filter(_.length > 2) ++
        authorsOf(src).headOption.toSet.flatMap(a => MergeText.surnameKeys(a).map(k => "a:" + k.value)) ++
        s.labels.map(citekeyStem).filter(_.value.length >= 3).map(k => "c:" + k.value) ++
        s.labels.map(l => "k:" + l.value) ++
        doi(src).map(d => "d:" + d.value) ++ sha(src).map(h => "h:" + h.value) ++ isbns(src).map(i => "i:" + i.value)
    keys.map(k => BlockingKey(k))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def doi(s: Bundle.Source): Option[MergeText.DoiKey] =
    s.doi.flatMap(d => MergeText.doiKey(MergeText.WrittenDoi(d.value)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sha(s: Bundle.Source): Option[Sha256] =
    s.originalSha256.flatMap(h => Sha256.option(h.value.trim.toLowerCase))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def isbns(s: Bundle.Source): Set[Isbn] =
    s.isbns.getOrElse(Nil).flatMap(i => MergeText.isbn13(Isbn(i.value))).toSet

  // ── stage B + D ─────────────────────────────────────────────────────────────────────────────

  /** Words that say a source is a reprint, an edition or a translation of an earlier work. */
  private val reprintWords = List("reprint", "edition", "transl", "neuauflage", "nachdruck", "facsimile")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def years(s: Side): Set[Long] =
    s.labels.flatMap(l => Year.findAllIn(l.value).map(_.toLong))

  /** One side says the other year: its citekey or an alias carries it, or the title/notes/venue speak of a reprint, an
    * edition or a translation.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def reprintHint(a: Side, b: Side): Boolean =
    def says(s: Side) =
      val text = MergeText.fold(
        MergeText.Text(
          List(Some(s.source.title.value), s.source.venue.map(_.value), s.source.notes.map(_.value)).flatten
            .mkString(" ")
        )
      )
      reprintWords.exists(text.value.contains)
    b.source.year.exists(y => years(a).contains(y.value)) || a.source.year.exists(y =>
      years(b).contains(y.value)
    ) || says(a) || says(b)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def venueFingerprint(s: Bundle.Source): Option[Fingerprint] =
    s.venue.map(v => MergeText.fingerprint(MergeText.Text(v.value))).filter(_.value.nonEmpty)

  /** The evidence about `right` (the bundle's) and `left` (this collection's). `sketches` are the MinHash signatures of
    * OCR bodies by their sha256, when they were computed.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evidence(right: Side, left: Side, sketches: Map[Bundle.Source.BodySha256, MinHash], w: MatchWeights): Evidence =
    val (r, l) = (right.source, left.source)
    val strong =
      (if doi(r).isDefined && doi(r) == doi(l) then List(Evidence.Strong("DOI")) else Nil) ++
        (if sha(r).isDefined && sha(r) == sha(l) then List(Evidence.Strong("original file sha256")) else Nil) ++
        (if isbns(r).intersect(isbns(l)).nonEmpty then List(Evidence.Strong("ISBN")) else Nil)
    val doiConflict = doi(r).isDefined && doi(l).isDefined && doi(r) != doi(l)

    // title
    val (fr, fl) = (MergeText.fingerprint(titleText(r)), MergeText.fingerprint(titleText(l)))
    val (mr, ml) =
      (
        MergeText.fingerprint(MergeText.Text(MergeText.mainTitle(titleOf(r)).value)),
        MergeText.fingerprint(MergeText.Text(MergeText.mainTitle(titleOf(l)).value))
      )
    val jw = MergeText.jaroWinkler(MergeText.words(titleText(r)), MergeText.words(titleText(l)))
    val tj = MergeText.jaccard(MergeText.tokens(titleText(r)).toSet, MergeText.tokens(titleText(l)).toSet)
    val title: (Evidence.Reason, Evidence.Weight) =
      if fr.value.nonEmpty && fr == fl then Evidence.Reason.TitleSame.weighs(w.titleFingerprint.value)
      else if mr.value.nonEmpty && (mr == ml || mr == fl || fr == ml) then
        Evidence.Reason.MainTitleSame.weighs(w.titleMain.value)
      else if jw.value >= w.titleSimilarAtLeast.value then
        Evidence.Reason.TitlesSimilar(Figure(jw.value)).weighs(w.titleSimilar.value)
      else if tj.value >= w.titleTokensAtLeast.value then
        Evidence.Reason.TitleWordsOverlap(Figure(tj.value)).weighs(w.titleTokens.value)
      else Evidence.Reason.TitlesDiffer.weighs(w.titleDiffers.value)

    // authors
    val ag = MergeText.authors(authorsOf(r), authorsOf(l))
    val authors: Option[(Evidence.Reason, Evidence.Weight)] = ag match
      case AuthorAgreement.Same        => Some(Evidence.Reason.AuthorsSame.weighs(w.authorsSame.value))
      case AuthorAgreement.FirstAuthor => Some(Evidence.Reason.FirstAuthorSame.weighs(w.authorsFirst.value))
      case AuthorAgreement.Overlap     => Some(Evidence.Reason.AuthorsOverlap.weighs(w.authorsOverlap.value))
      case AuthorAgreement.Disjoint    => Some(Evidence.Reason.NoAuthorInCommon.weighs(w.authorsDisjoint.value))
      case AuthorAgreement.Unknown     => None

    // year
    val gap = r.year.zip(l.year).map((a, b) => math.abs(a.value - b.value))
    val year: Option[(Evidence.Reason, Evidence.Weight)] = gap.map {
      case 0L                            => Evidence.Reason.YearSame.weighs(w.yearEqual.value)
      case 1L                            => Evidence.Reason.YearsOneApart.weighs(w.yearNear.value)
      case _ if reprintHint(right, left) =>
        Evidence.Reason.YearsReprint(r.year.get, l.year.get).weighs(w.yearReprint.value)
      case _ => Evidence.Reason.YearsApart(r.year.get, l.year.get).weighs(w.yearFar.value)
    }

    // venue
    val venue: Option[(Evidence.Reason, Evidence.Weight)] =
      venueFingerprint(r)
        .zip(venueFingerprint(l))
        .map((a, b) =>
          if a == b then Evidence.Reason.VenueSame.weighs(w.venueEqual.value)
          else Evidence.Reason.VenuesDiffer.weighs(w.venueDiffers.value)
        )

    // body
    val body: Option[(Evidence.Reason, Evidence.Weight)] =
      (r.bodySha256, l.bodySha256) match
        case (Some(a), Some(b)) if a == b => Some(Evidence.Reason.BodySame.weighs(w.bodySame.value))
        case (Some(a), Some(b))           =>
          sketches.get(a).zip(sketches.get(b)).map((x, y) => x.similarity(y).value).flatMap { j =>
            if j >= w.bodySameAtLeast.value then Some(Evidence.Reason.BodiesAlike(Figure(j)).weighs(w.bodySame.value))
            else if j >= w.bodyEditionAtLeast.value then
              Some(Evidence.Reason.BodiesPartlyAlike(Figure(j)).weighs(w.bodyEdition.value))
            else if j < w.bodyDiffersBelow.value then
              Some(Evidence.Reason.BodiesDiffer(Figure(j)).weighs(w.bodyDiffers.value))
            else None
          }
        case _ => None

    // a citekey of one side is an old citekey of the other
    val alias: Option[(Evidence.Reason, Evidence.Weight)] =
      val shared = (right.aliasLabels.intersect(left.labels) ++ left.aliasLabels.intersect(right.labels)).toList.sorted
      shared.headOption.map(a => Evidence.Reason.OldCitekey(a).weighs(w.alias.value))

    Evidence(
      strong,
      Evidence.DoiConflict(doiConflict),
      Evidence.YearGap(gap.exists(_ > 1)),
      Evidence.TitleDiffers(title._2.value < 0),
      ag,
      List(Some(title), authors, year, venue, body, alias).flatten
    )

  // ── assignment ──────────────────────────────────────────────────────────────────────────────

  enum Outcome derives CanEqual:
    case AutoMerge, NeedsDecision

  final case class Pair(
      right: Side,
      left: Side,
      evidence: Evidence,
      outcome: Outcome,
      /** Hard flags that make it a question whatever the score. */
      flags: List[Pair.Flags],
      /** Other sources here this one also resembles (best first), for the record. */
      alsoLike: List[(Side.Label, Evidence.Score)]
  )

  object Pair:
    type Flags = Flags.T
    object Flags extends Newtype[String, Pure]

  final case class Matching(pairs: List[Pair], unmatched: List[Side])

  /** Pair every `right` with at most one `left`. `editions` answers the year-gap question for all: `Merge` drops the
    * flag, `Separate` makes such a pair two works.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def assign(
      rights: List[Side],
      lefts: List[Side],
      sketches: Map[Bundle.Source.BodySha256, MinHash],
      cfg: MergeSettings,
      editions: Option[EditionsPolicy]
  ): Matching =
    val byKey: Map[BlockingKey, List[Side]] =
      lefts.flatMap(l => blockingKeys(l).map(_ -> l)).groupMap(_._1)(_._2)
    final case class Scored(right: Side, left: Side, ev: Evidence, rank: Int, flags: List[Pair.Flags])
    val scored: List[Scored] = rights.flatMap { r =>
      val candidates = blockingKeys(r).toList.flatMap(k => byKey.getOrElse(k, Nil)).distinctBy(_.uid)
      candidates.flatMap { l =>
        val ev = evidence(r, l, sketches, cfg.weights)
        val yearFlag = ev.yearGap.value && !ev.strongKey && editions.isEmpty
        val flags =
          (if ev.doiConflict.value && !ev.strongKey then
             List(
               Pair.Flags(
                 s"two different DOIs: ${l.source.doi.getOrElse("")} here, ${r.source.doi.getOrElse("")} in the bundle"
               )
             )
           else Nil) ++
            (if yearFlag then
               List(
                 Pair.Flags(
                   s"years ${l.source.year.getOrElse(0)} here and ${r.source.year.getOrElse(0)} in the bundle: another edition?"
                 )
               )
             else Nil)
        val separate = ev.yearGap.value && !ev.strongKey && editions.contains(EditionsPolicy.Separate)
        val rank =
          if ev.strongKey then 0
          else if separate || ev.score.value <= cfg.newAtMost.value then 3
          else if ev.score.value >= cfg.autoMerge.value && flags.isEmpty then 1
          else 2
        Option.when(rank < 3)(Scored(r, l, ev, rank, flags))
      }
    }
    val order = scored.sortBy(s => (s.rank, -s.ev.score.value, s.right.uid.toString, s.left.uid.toString))
    // greedy one-to-one: the strongest pairs first
    @tailrec def greedy(rest: List[Scored], acc: List[Scored], rs: Set[UUID], ls: Set[UUID]): List[Scored] =
      rest match
        case Nil                                            => acc
        case s :: tail if rs(s.right.uid) || ls(s.left.uid) => greedy(tail, acc, rs, ls)
        case s :: tail                                      => greedy(tail, s :: acc, rs + s.right.uid, ls + s.left.uid)
    val taken = greedy(order, Nil, Set.empty, Set.empty)
    val pairs = taken.reverse.map { s =>
      val others = order
        .filter(o => o.right.uid == s.right.uid && o.left.uid != s.left.uid)
        .map(o => Side.Label(o.left.source.citekey.value) -> o.ev.score)
      Pair(s.right, s.left, s.ev, if s.rank <= 1 then Outcome.AutoMerge else Outcome.NeedsDecision, s.flags, others)
    }
    val paired = pairs.map(_.right.uid).toSet
    Matching(pairs, rights.filterNot(r => paired(r.uid)))
