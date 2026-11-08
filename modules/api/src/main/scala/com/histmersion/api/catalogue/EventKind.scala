package com.histmersion
package api.catalogue
import com.histmersion.api.given
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
  val all: List[CatalogRecord.Kind] =
    List(CatalogRecord.Kind(Event), CatalogRecord.Kind(Battle), CatalogRecord.Kind(Migration), CatalogRecord.Kind(Construction), CatalogRecord.Kind(Destruction), CatalogRecord.Kind(Founding), CatalogRecord.Kind(Treaty), CatalogRecord.Kind(Reign), CatalogRecord.Kind(EventPeriod))
