package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Source

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
  object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey
  type Doi = com.histmersion.domain.library.Doi
  val Doi: _root_.com.histmersion.domain.library.Doi.type = _root_.com.histmersion.domain.library.Doi
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = com.histmersion.Year
  val Year: _root_.com.histmersion.Year.type = _root_.com.histmersion.Year
  type Title = com.histmersion.domain.Title
  val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
