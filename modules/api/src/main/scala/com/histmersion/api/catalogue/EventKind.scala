package com.histmersion.api.catalogue
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
  val all: List[com.histmersion.api.catalogue.CatalogRecord.Kind] =
    List(_root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Event), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Battle), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Migration), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Construction), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Destruction), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Founding), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Treaty), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(Reign), _root_.com.histmersion.api.catalogue.CatalogRecord.Kind(EventPeriod))
