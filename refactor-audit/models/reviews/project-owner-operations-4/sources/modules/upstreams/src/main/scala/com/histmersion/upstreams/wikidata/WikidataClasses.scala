package com.histmersion.upstreams.wikidata
object WikidataClasses:
  /** Pure: `(qid, kind)` per well-formed entry, in order, each class once; the kind as written. */
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart2 = ParseResultPart2.T
  object ParseResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.upstreams.wikidata.WikidataClasses.ParseS): List[(com.histmersion.upstreams.wikidata.WikidataClasses.ParseResultPart1, com.histmersion.upstreams.wikidata.WikidataClasses.ParseResultPart2)] =
    (Option(s).toList
      .flatMap((_).value.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(q, k) if q.matches("Q[1-9][0-9]*") && k.nonEmpty => Some(q -> k)
          case _                                                      => None
      }
      .distinctBy(_._1)).map(refactorValue => { val refactorTuple = refactorValue; (_root_.com.histmersion.upstreams.wikidata.WikidataClasses.ParseResultPart1(refactorTuple._1), _root_.com.histmersion.upstreams.wikidata.WikidataClasses.ParseResultPart2(refactorTuple._2)) })
