package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{Candidates, WorkRef}

/** The works an acquisition search found, one per work: keyed by DOI, the richer record kept. */
object CandidateDedupe:

  /** An arXiv preprint without a DOI gets arXiv's DataCite DOI (`10.48550/arxiv.<id>`), so the same preprint from two
    * indexes is one.
    */
  def keyOf(w: WorkRef): Option[String] =
    w.doi
      .flatMap(Ident.doi)
      .orElse(w.arxivId.map(a => s"10.48550/arxiv.${a.trim.toLowerCase.replaceAll("v\\d+$", "")}"))

  def dedupe(
      found: List[(String, WorkRef)],
      known: Set[String]
  ): (List[(String, WorkRef, List[String])], Map[String, Int]) =
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
