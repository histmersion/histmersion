package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Year
import com.histmersion.plugin.Doi
import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class RefRow(
    title: Option[RefRow.Title] = None,
    authors: List[RefRow.Authors] = Nil,
    year: Option[Year] = None,
    doi: Option[Doi] = None,
    raw: Option[RefRow.Raw] = None
)

object RefRow:
  type Title = WorkKey.Title
  val Title: WorkKey.Title.type = WorkKey.Title
  type Authors = Authors.T
  object Authors extends RefinedType[String, Pure]
  type Raw = Raw.T
  object Raw extends RefinedType[String, Pure]
