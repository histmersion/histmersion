package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventKind:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Event = "event"
  val Battle = "battle"
  val Migration = "migration"
  val Construction = "construction"
  val Destruction = "destruction"
  val Founding = "founding"
  val Treaty = "treaty"
  val Reign = "reign"
  val EventPeriod = "period"
  val all: List[com.histmersion.api.catalogue.EventKind.All] =
    List(Event, Battle, Migration, Construction, Destruction, Founding, Treaty, Reign, EventPeriod)
