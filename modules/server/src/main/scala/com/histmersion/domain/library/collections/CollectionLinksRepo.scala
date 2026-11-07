package com.histmersion.domain.library.collections

import com.augustnagro.magnum.*
import com.histmersion.util.database.given

/** The sources linked to Calibre entries: a numeric `calibre_id`, by citekey. Takes the connection it runs on. */
private[collections] object CollectionLinksRepo:

  def linked()(using DbCon): List[LinkedSource] =
    sql"""SELECT citekey, calibre_id, title, authors, year FROM sources
          WHERE calibre_id ~ '^[0-9]+$$' ORDER BY citekey"""
      .query[(String, String, Option[String], List[String], Option[Int])]
      .run()
      .toList
      .map((ck, id, title, authors, year) =>
        LinkedSource(
          com.histmersion.domain.library.Citekey.assume(ck),
          LinkedSource.Id(id),
          LinkedSource.Title(title.getOrElse("")),
          authors.map(_.trim).filter(_.nonEmpty).map(LinkedSource.Authors(_)),
          year.map(com.histmersion.Year(_))
        )
      )
