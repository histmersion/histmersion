package com.histmersion
package domain.library.collections

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.AuthorName

/** A source linked to an item of the Calibre library (`sources.calibre_id`), with what an audit compares. */
final case class LinkedSource(
    citekey: Citekey,
    id: LinkedSource.Id,
    title: LinkedSource.Title,
    authors: List[LinkedSource.Authors],
    year: Option[Year]
)

object LinkedSource:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
  type Authors = AuthorName
  val Authors: AuthorName.type = AuthorName
