package com.histmersion
package upstreams.wikidata

import io.github.iltotore.iron.constraint.any.Pure
object WikidataClasses:
  type ClassLabel = ClassLabel.T
  object ClassLabel extends Newtype[String, Pure]
  type WikidataText = WikidataText.T
  object WikidataText extends Newtype[String, Pure]

  /** Pure: `(qid, kind)` per well-formed entry, in order, each class once; the kind as written. */

  def parse(s: WikidataText): List[(WikidataItem.Id, ClassLabel)] =
    (Option(s).toList
      .flatMap((_).value.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(q, k) if q.matches("Q[1-9][0-9]*") && k.nonEmpty => Some(q -> k)
          case _                                                      => None
      }
      .distinctBy(_._1))
      .map { refactorValue =>
        val refactorTuple = refactorValue; (WikidataItem.Id(refactorTuple._1), ClassLabel(refactorTuple._2))
      }
