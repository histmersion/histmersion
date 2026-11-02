package com.histmersion.domain.library.sources

import com.histmersion.domain.MergeSettings
import com.histmersion.util.data.exchange.MatchWeights

import scala.annotation.tailrec
import com.histmersion.api.given

import MergeText.AuthorAgreement
import com.histmersion.util.text.MinHash

import java.util.UUID
import com.histmersion.domain.library.EditionsPolicy
import com.histmersion.api.exchange.Bundle

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
object SourceMatcher:

  /** A source with the old citekeys (aliases) that still name it. */
  final case class Side(source: Bundle.Source, aliases: Set[String]):
    def uid: UUID = source.uid
    def labels: Set[String] = aliases + source.citekey

  /** The evidence about one pair. `parts` is the score's waterfall, in the order it was added up. */
  final case class Evidence(
      strong: List[String],
      doiConflict: Boolean,
      yearGap: Boolean,
      titleDiffers: Boolean,
      authors: AuthorAgreement,
      parts: List[(String, Double)]
  ):
    def score: Double = parts.map(_._2).sum
    def strongKey: Boolean = strong.nonEmpty
    def why: List[String] =
      strong.map(k => s"$k equal (strong key)") ++ parts.map((l, w) => s"$l (${signed(w)})")

  /** `x` with `places` decimals, a dot whatever the JVM's locale. */
  def fixed(x: Double, places: Int): String = s"%.${places}f".formatLocal(java.util.Locale.ROOT, x)

  def signed(w: Double): String =
    val s = if w == math.rint(w) then w.toLong.toString else fixed(w, 1)
    if w >= 0 then s"+$s" else s

  // ── blocking ────────────────────────────────────────────────────────────────────────────────

  private val Year = "(1[0-9]{3}|20[0-9]{2})".r

  /** The letters of a citekey before its year (`guterbock1997_perspectives` → `guterbock`). */
  def citekeyStem(ck: String): String = ck.takeWhile(c => !c.isDigit).filter(_.isLetter)

  /** The keys a pair must share one of to be compared. */
  def blockingKeys(s: Side): Set[String] =
    val src = s.source
    Set("t:" + MergeText.titleKey(src.title)).filter(_.length > 2) ++
      src.authors.headOption.toSet.flatMap(a => MergeText.surnameKeys(a).map("a:" + _)) ++
      s.labels.map(citekeyStem).filter(_.length >= 3).map("c:" + _) ++
      s.labels.map("k:" + _) ++
      doi(src).map("d:" + _) ++ sha(src).map("h:" + _) ++ isbns(src).map("i:" + _)

  private def doi(s: Bundle.Source): Option[String] = s.doi.flatMap(MergeText.doiKey)
  private def sha(s: Bundle.Source): Option[String] = s.originalSha256.map(_.trim.toLowerCase).filter(_.nonEmpty)
  private def isbns(s: Bundle.Source): Set[String] = s.isbns.getOrElse(Nil).flatMap(MergeText.isbn13).toSet

  // ── stage B + D ─────────────────────────────────────────────────────────────────────────────

  /** Words that say a source is a reprint, an edition or a translation of an earlier work. */
  private val reprintWords = List("reprint", "edition", "transl", "neuauflage", "nachdruck", "facsimile")

  private def years(s: Side): Set[Int] =
    s.labels.flatMap(l => Year.findAllIn(l).map(_.toInt))

  /** One side says the other year: its citekey or an alias carries it, or the title/notes/venue speak of a reprint, an
    * edition or a translation.
    */
  private def reprintHint(a: Side, b: Side): Boolean =
    def says(s: Side) =
      val text = MergeText.fold(List(Some(s.source.title), s.source.venue, s.source.notes).flatten.mkString(" "))
      reprintWords.exists(text.contains)
    b.source.year.exists(years(a)) || a.source.year.exists(years(b)) || says(a) || says(b)

  /** The evidence about `right` (the bundle's) and `left` (this collection's). `sketches` are the MinHash signatures of
    * OCR bodies by their sha256, when they were computed.
    */
  def evidence(right: Side, left: Side, sketches: Map[String, MinHash], w: MatchWeights): Evidence =
    val (r, l) = (right.source, left.source)
    val strong =
      (if doi(r).isDefined && doi(r) == doi(l) then List("DOI") else Nil) ++
        (if sha(r).isDefined && sha(r) == sha(l) then List("original file sha256") else Nil) ++
        (if isbns(r).intersect(isbns(l)).nonEmpty then List("ISBN") else Nil)
    val doiConflict = doi(r).isDefined && doi(l).isDefined && doi(r) != doi(l)

    // title
    val (fr, fl) = (MergeText.fingerprint(r.title), MergeText.fingerprint(l.title))
    val (mr, ml) =
      (MergeText.fingerprint(MergeText.mainTitle(r.title)), MergeText.fingerprint(MergeText.mainTitle(l.title)))
    val jw = MergeText.jaroWinkler(MergeText.tokens(r.title).mkString(" "), MergeText.tokens(l.title).mkString(" "))
    val tj = MergeText.jaccard(MergeText.tokens(r.title).toSet, MergeText.tokens(l.title).toSet)
    val title: (String, Double) =
      if fr.nonEmpty && fr == fl then "title the same" -> w.titleFingerprint
      else if mr.nonEmpty && (mr == ml || mr == fl || fr == ml) then
        "main title the same, the subtitle differs" -> w.titleMain
      else if jw >= w.titleSimilarAtLeast then s"titles similar (Jaro-Winkler ${fixed(jw, 2)})" -> w.titleSimilar
      else if tj >= w.titleTokensAtLeast then s"title words overlap (Jaccard ${fixed(tj, 2)})" -> w.titleTokens
      else "titles differ" -> w.titleDiffers

    // authors
    val ag = MergeText.authors(r.authors, l.authors)
    val authors: Option[(String, Double)] = ag match
      case AuthorAgreement.Same        => Some("authors the same" -> w.authorsSame)
      case AuthorAgreement.FirstAuthor => Some("first author the same" -> w.authorsFirst)
      case AuthorAgreement.Overlap     => Some("authors overlap" -> w.authorsOverlap)
      case AuthorAgreement.Disjoint    => Some("no author in common" -> w.authorsDisjoint)
      case AuthorAgreement.Unknown     => None

    // year
    val gap = r.year.zip(l.year).map((a, b) => math.abs(a - b))
    val year: Option[(String, Double)] = gap.map {
      case 0                             => "year the same" -> w.yearEqual
      case 1                             => "years one apart" -> w.yearNear
      case _ if reprintHint(right, left) =>
        s"years ${r.year.get} and ${l.year.get}, a reprint or edition" -> w.yearReprint
      case _ => s"years ${r.year.get} and ${l.year.get}" -> w.yearFar
    }

    // venue
    val venue: Option[(String, Double)] =
      r.venue
        .map(MergeText.fingerprint)
        .filter(_.nonEmpty)
        .zip(l.venue.map(MergeText.fingerprint).filter(_.nonEmpty))
        .map((a, b) => if a == b then "venue the same" -> w.venueEqual else "venues differ" -> w.venueDiffers)

    // body
    val body: Option[(String, Double)] =
      (r.bodySha256, l.bodySha256) match
        case (Some(a), Some(b)) if a == b => Some("the same OCR body" -> w.bodySame)
        case (Some(a), Some(b))           =>
          sketches.get(a).zip(sketches.get(b)).map((x, y) => x.similarity(y)).flatMap { j =>
            if j >= w.bodySameAtLeast then Some(s"OCR bodies alike (MinHash ${fixed(j, 2)})" -> w.bodySame)
            else if j >= w.bodyEditionAtLeast then
              Some(s"OCR bodies partly alike (MinHash ${fixed(j, 2)})" -> w.bodyEdition)
            else if j < w.bodyDiffersBelow then Some(s"OCR bodies differ (MinHash ${fixed(j, 2)})" -> w.bodyDiffers)
            else None
          }
        case _ => None

    // a citekey of one side is an old citekey of the other
    val alias: Option[(String, Double)] =
      val shared = (right.aliases.intersect(left.labels) ++ left.aliases.intersect(right.labels)).toList.sorted
      shared.headOption.map(a => s"'$a' is an old citekey of the other" -> w.alias)

    Evidence(
      strong,
      doiConflict,
      gap.exists(_ > 1),
      title._2 < 0,
      ag,
      List(Some(title), authors, year, venue, body, alias).flatten
    )

  // ── assignment ──────────────────────────────────────────────────────────────────────────────

  enum Outcome:
    case AutoMerge, NeedsDecision

  final case class Pair(
      right: Side,
      left: Side,
      evidence: Evidence,
      outcome: Outcome,
      /** Hard flags that make it a question whatever the score. */
      flags: List[String],
      /** Other sources here this one also resembles (best first), for the record. */
      alsoLike: List[(String, Double)]
  )

  final case class Matching(pairs: List[Pair], unmatched: List[Side])

  /** Pair every `right` with at most one `left`. `editions` answers the year-gap question for all: `Merge` drops the
    * flag, `Separate` makes such a pair two works.
    */
  def assign(
      rights: List[Side],
      lefts: List[Side],
      sketches: Map[String, MinHash],
      cfg: MergeSettings,
      editions: Option[EditionsPolicy]
  ): Matching =
    val byKey: Map[String, List[Side]] =
      lefts.flatMap(l => blockingKeys(l).map(_ -> l)).groupMap(_._1)(_._2)
    final case class Scored(right: Side, left: Side, ev: Evidence, rank: Int, flags: List[String])
    val scored: List[Scored] = rights.flatMap { r =>
      val candidates = blockingKeys(r).toList.flatMap(k => byKey.getOrElse(k, Nil)).distinctBy(_.uid)
      candidates.flatMap { l =>
        val ev = evidence(r, l, sketches, cfg.weights)
        val yearFlag = ev.yearGap && !ev.strongKey && editions.isEmpty
        val flags =
          (if ev.doiConflict && !ev.strongKey then
             List(
               s"two different DOIs: ${l.source.doi.getOrElse("")} here, ${r.source.doi.getOrElse("")} in the bundle"
             )
           else Nil) ++
            (if yearFlag then
               List(
                 s"years ${l.source.year.getOrElse(0)} here and ${r.source.year.getOrElse(0)} in the bundle: another edition?"
               )
             else Nil)
        val separate = ev.yearGap && !ev.strongKey && editions.contains(EditionsPolicy.Separate)
        val rank =
          if ev.strongKey then 0
          else if separate || ev.score <= cfg.newAtMost then 3
          else if ev.score >= cfg.autoMerge && flags.isEmpty then 1
          else 2
        Option.when(rank < 3)(Scored(r, l, ev, rank, flags))
      }
    }
    val order = scored.sortBy(s => (s.rank, -s.ev.score, s.right.uid.toString, s.left.uid.toString))
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
        .map(o => o.left.source.citekey -> o.ev.score)
      Pair(s.right, s.left, s.ev, if s.rank <= 1 then Outcome.AutoMerge else Outcome.NeedsDecision, s.flags, others)
    }
    val paired = pairs.map(_.right.uid).toSet
    Matching(pairs, rights.filterNot(r => paired(r.uid)))
