package com.histmersion.upstreams.wikidata
object WikidataClasses:
  type ClassLabel = ClassLabel.T
  object ClassLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataText = WikidataText.T
  object WikidataText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Pure: `(qid, kind)` per well-formed entry, in order, each class once; the kind as written. */

  def parse(s: com.histmersion.upstreams.wikidata.WikidataClasses.WikidataText): List[(com.histmersion.upstreams.wikidata.WikidataItem.Id, com.histmersion.upstreams.wikidata.WikidataClasses.ClassLabel)] =
    (Option(s).toList
      .flatMap((_).value.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(q, k) if q.matches("Q[1-9][0-9]*") && k.nonEmpty => Some(q -> k)
          case _                                                      => None
      }
      .distinctBy(_._1)).map(refactorValue => { val refactorTuple = refactorValue; (_root_.com.histmersion.upstreams.wikidata.WikidataItem.Id(refactorTuple._1), _root_.com.histmersion.upstreams.wikidata.WikidataClasses.ClassLabel(refactorTuple._2)) })
