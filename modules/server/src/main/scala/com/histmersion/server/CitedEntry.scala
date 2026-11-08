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
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: com.histmersion.domain.library.Citekey.type = com.histmersion.domain.library.Citekey
  type Doi = com.histmersion.domain.library.Doi
  val Doi: com.histmersion.domain.library.Doi.type = com.histmersion.domain.library.Doi
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Year = com.histmersion.Year
  val Year: com.histmersion.Year.type = com.histmersion.Year
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
