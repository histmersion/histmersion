package com.histmersion.upstreams

/** `events.wikidata.classes`: the Wikidata classes whose instances are events, each with the kind its events are
  * (`Q178561:battle,Q188055:siege`, the most specific first). Read by the catalogue for its parts and by the host for
  * the order of the kinds.
  */
object WikidataClasses:
  /** Pure: `(qid, kind)` per well-formed entry, in order, each class once; the kind as written. */
  def parse(s: String): List[(String, String)] =
    Option(s).toList
      .flatMap(_.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(q, k) if q.matches("Q[1-9][0-9]*") && k.nonEmpty => Some(q -> k)
          case _                                                      => None
      }
      .distinctBy(_._1)
