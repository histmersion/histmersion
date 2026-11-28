package com.histmersion
package api.catalogue
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventKind:

  val Event = "event"
  val Battle = "battle"
  val Migration = "migration"
  val Construction = "construction"
  val Destruction = "destruction"
  val Founding = "founding"
  val Treaty = "treaty"
  val Reign = "reign"
  val EventPeriod = "period"
  val all: List[CatalogRecord.Kind] = CatalogRecord.Kind.known
