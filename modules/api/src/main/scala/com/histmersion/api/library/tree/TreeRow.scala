package com.histmersion
package api.library.tree

import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class TreeRow(
    key: TreeRow.Key,
    citekey: Citekey,
    title: Title,
    start: Option[Year] = None,
    end: Option[Year] = None,
    placeIds: List[RegionNode.Id] = Nil
):
  def toPeriodRow: Period.Row = Period.Row(start, end)

object TreeRow:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
