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
  val all: List[String] =
    List(Event, Battle, Migration, Construction, Destruction, Founding, Treaty, Reign, EventPeriod)
