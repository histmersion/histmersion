package com.histmersion
package domain.library.acquisition
import com.histmersion.api.library.{Candidates, WorkRef}
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure

/** The works an acquisition search found, one per work: keyed by DOI, the richer record kept. */
object CandidateDedupe:

  /** The search that found a work. */
  type FoundBy = FoundBy.T
  object FoundBy extends Newtype[String, Pure]
  type SkipCount = SkipCount.T
  object SkipCount extends Newtype[Int, Pure]

  /** Why a found work is left out: the account of the dedupe's choice, not a failure. */
  enum LeftOut derives CanEqual:
    case NoDoi, Retracted, Known

    /** The reason as the search's report counts it. */
    def said: String = this match
      case NoDoi     => "no DOI"
      case Retracted => "retracted"
      case Known     => "in the corpus or queued"

  /** An arXiv preprint without a DOI gets arXiv's DataCite DOI (`10.48550/arxiv.<id>`), so the same preprint from two
    * indexes is one.
    */
  def keyOf(w: WorkRef): Option[Doi] =
    w.doi
      .flatMap(d => Ident.doi(Ident.Written(d.value)))
      .orElse(
        w.arxivId.flatMap(a => Doi.option(s"10.48550/arxiv.${a.value.trim.toLowerCase.replaceAll("v\\d+$", "")}"))
      )

  def dedupe(
      found: List[(FoundBy, WorkRef)],
      known: Set[Doi]
  ): (List[(Doi, WorkRef, List[FoundBy])], Map[LeftOut, SkipCount]) =
    val kept = scala.collection.mutable.LinkedHashMap.empty[Doi, (WorkRef, List[FoundBy])]
    val skipped = scala.collection.mutable.Map.empty[LeftOut, Int].withDefaultValue(0)
    found.foreach { (by, w) =>
      keyOf(w) match
        case None                           => skipped(LeftOut.NoDoi) += 1
        case Some(_) if w.isRetracted.value => skipped(LeftOut.Retracted) += 1
        case Some(k) if known.contains(k)   => skipped(LeftOut.Known) += 1
        case Some(k)                        =>
          kept.get(k) match
            case Some((first, bys)) =>
              kept(k) = (Candidates.richer(first, w), if bys.contains(by) then bys else bys :+ by)
            case None => kept(k) = (w, List(by))
    }
    (
      kept.toList.map { case (k, (w, bys)) => (k, w, bys) },
      skipped.toMap.map((reason, n) => reason -> SkipCount(n))
    )
