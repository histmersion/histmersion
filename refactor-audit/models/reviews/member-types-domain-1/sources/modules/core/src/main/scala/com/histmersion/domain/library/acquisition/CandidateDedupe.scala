package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{Candidates, WorkRef}

/** The works an acquisition search found, one per work: keyed by DOI, the richer record kept. */
object CandidateDedupe:

  /** An arXiv preprint without a DOI gets arXiv's DataCite DOI (`10.48550/arxiv.<id>`), so the same preprint from two
    * indexes is one.
    */
  type KeyOfResult = KeyOfResult.T
  object KeyOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeResultPart1Part1 = DedupeResultPart1Part1.T
  object DedupeResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeResultPart1Part3 = DedupeResultPart1Part3.T
  object DedupeResultPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeResultPart2Part1 = DedupeResultPart2Part1.T
  object DedupeResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeResultPart2Part2 = DedupeResultPart2Part2.T
  object DedupeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeFoundPart1 = DedupeFoundPart1.T
  object DedupeFoundPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DedupeKnown = DedupeKnown.T
  object DedupeKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def keyOf(w: WorkRef): Option[com.histmersion.domain.library.acquisition.CandidateDedupe.KeyOfResult] =
    w.doi.flatMap(d => Ident.doi(d.value))
      .orElse(w.arxivId.map(a => s"10.48550/arxiv.${a.trim.toLowerCase.replaceAll("v\\d+$", "")}"))

  def dedupe(
      found: List[(com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeFoundPart1, WorkRef)],
      known: Set[com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeKnown]
  ): (List[(com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeResultPart1Part1, WorkRef, List[com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeResultPart1Part3])], Map[com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeResultPart2Part1, com.histmersion.domain.library.acquisition.CandidateDedupe.DedupeResultPart2Part2]) =
    val kept = scala.collection.mutable.LinkedHashMap.empty[String, (WorkRef, List[String])]
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (by, w) =>
      keyOf(w) match
        case None                         => skipped("no DOI") += 1
        case Some(_) if w.isRetracted     => skipped("retracted") += 1
        case Some(k) if known.contains(k) => skipped("in the corpus or queued") += 1
        case Some(k)                      =>
          kept.get(k) match
            case Some((first, bys)) => kept(k) = (Candidates.richer(first, w), if bys.contains(by) then bys else bys :+ by)
            case None               => kept(k) = (w, List(by))
    }
    (kept.toList.map { case (k, (w, bys)) => (k, w, bys) }, skipped.toMap)
