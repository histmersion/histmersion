package com.histmersion
package domain.library.collections

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{atBoundary, BoundaryKind}

/** The sources linked to Calibre entries: a numeric `calibre_id`, by citekey. Takes the connection it runs on. */
@atBoundary(BoundaryKind.Sql, "reads and writes the collection links tables and their row codecs")
private[collections] object CollectionLinksRepo:

  def linked()(using DbCon): List[LinkedSource] =
    sql"""SELECT citekey, calibre_id, title, authors, year FROM sources
          WHERE calibre_id ~ '^[0-9]+$$' ORDER BY citekey"""
      .query[(String, String, Option[String], List[String], Option[Int])]
      .run()
      .toList
      .map((ck, id, title, authors, year) =>
        LinkedSource(
          Citekey.assume(ck),
          LinkedSource.Id(id),
          LinkedSource.Title(title.getOrElse("")),
          authors.map(_.trim).filter(_.nonEmpty).map(LinkedSource.Authors(_)),
          year.map(Year(_))
        )
      )
