package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Source
import io.github.iltotore.iron.constraint.any.Pure

/** One line of an article's "Sources cited" footer; `number` is its citation number in the prose (first appearance),
  * `None` for a source cited only outside it (e.g. under "Not used").
  */
final case class CitedEntry(
    number: Option[CitedEntry.Number],
    citekey: CitedEntry.Citekey,
    doi: Option[CitedEntry.Doi],
    authors: List[CitedEntry.Authors],
    year: Option[CitedEntry.Year],
    title: Option[CitedEntry.Title],
    status: Option[Source.Status]
)

object CitedEntry:
  type Number = Number.T
  object Number extends Newtype[Int, Pure]
  type Citekey = library.Citekey
  val Citekey: library.Citekey.type = library.Citekey
  type Doi = library.Doi
  val Doi: library.Doi.type = library.Doi
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Year = com.histmersion.Year
  val Year: com.histmersion.Year.type = com.histmersion.Year
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
