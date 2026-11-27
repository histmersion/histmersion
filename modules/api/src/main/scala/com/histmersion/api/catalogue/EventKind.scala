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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[CatalogRecord.Kind] =
    List(
      CatalogRecord.Kind(Event),
      CatalogRecord.Kind(Battle),
      CatalogRecord.Kind(Migration),
      CatalogRecord.Kind(Construction),
      CatalogRecord.Kind(Destruction),
      CatalogRecord.Kind(Founding),
      CatalogRecord.Kind(Treaty),
      CatalogRecord.Kind(Reign),
      CatalogRecord.Kind(EventPeriod)
    )
