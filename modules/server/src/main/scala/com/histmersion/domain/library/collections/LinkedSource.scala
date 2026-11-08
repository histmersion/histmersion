package com.histmersion
package domain.library.collections

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.AuthorName

/** A source linked to an item of the Calibre library (`sources.calibre_id`), with what an audit compares. */
final case class LinkedSource(citekey: Citekey, id: LinkedSource.Id, title: LinkedSource.Title, authors: List[LinkedSource.Authors], year: Option[Year])

object LinkedSource:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Authors = AuthorName
  val Authors: AuthorName.type = AuthorName
