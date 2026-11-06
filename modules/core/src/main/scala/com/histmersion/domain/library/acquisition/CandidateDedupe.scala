package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{Candidates, WorkRef}
import com.histmersion.domain.library.Doi

/** The works an acquisition search found, one per work: keyed by DOI, the richer record kept. */
object CandidateDedupe:

  /** The search that found a work. */
  type FoundBy = FoundBy.T
  object FoundBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipReason = SkipReason.T
  object SkipReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipCount = SkipCount.T
  object SkipCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An arXiv preprint without a DOI gets arXiv's DataCite DOI (`10.48550/arxiv.<id>`), so the same preprint from two
    * indexes is one.
    */
  def keyOf(w: WorkRef): Option[Doi] =
    w.doi.flatMap(d => Ident.doi(Ident.Written(d.value)))
      .orElse(w.arxivId.flatMap(a => Doi.option(s"10.48550/arxiv.${a.value.trim.toLowerCase.replaceAll("v\\d+$", "")}")))

  def dedupe(
      found: List[(FoundBy, WorkRef)],
      known: Set[Doi]
  ): (List[(Doi, WorkRef, List[FoundBy])], Map[SkipReason, SkipCount]) =
    val kept = scala.collection.mutable.LinkedHashMap.empty[Doi, (WorkRef, List[FoundBy])]
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (by, w) =>
      keyOf(w) match
        case None                         => skipped("no DOI") += 1
        case Some(_) if w.isRetracted.value => skipped("retracted") += 1
        case Some(k) if known.contains(k) => skipped("in the corpus or queued") += 1
        case Some(k)                      =>
          kept.get(k) match
            case Some((first, bys)) => kept(k) = (Candidates.richer(first, w), if bys.contains(by) then bys else bys :+ by)
            case None               => kept(k) = (w, List(by))
    }
    (kept.toList.map { case (k, (w, bys)) => (k, w, bys) }, skipped.toMap.map((reason, n) => SkipReason(reason) -> SkipCount(n)))
