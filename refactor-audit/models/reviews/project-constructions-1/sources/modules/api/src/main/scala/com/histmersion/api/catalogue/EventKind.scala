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
    List(_root_.com.histmersion.api.catalogue.EventKind.All(Event), _root_.com.histmersion.api.catalogue.EventKind.All(Battle), _root_.com.histmersion.api.catalogue.EventKind.All(Migration), _root_.com.histmersion.api.catalogue.EventKind.All(Construction), _root_.com.histmersion.api.catalogue.EventKind.All(Destruction), _root_.com.histmersion.api.catalogue.EventKind.All(Founding), _root_.com.histmersion.api.catalogue.EventKind.All(Treaty), _root_.com.histmersion.api.catalogue.EventKind.All(Reign), _root_.com.histmersion.api.catalogue.EventKind.All(EventPeriod))
