package com.histmersion.values

/** What a goal is linked to. */
enum GoalLinkKind(val wire: String):
  case Source extends GoalLinkKind("source")
  case Article extends GoalLinkKind("article")
  case Query extends GoalLinkKind("query")
  case QueueItem extends GoalLinkKind("queue-item")

object GoalLinkKind:

  /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
  val SearchQuery: GoalLinkKind = Query

  def parse(s: String): Option[GoalLinkKind] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[GoalLinkKind]:
    def all: Array[GoalLinkKind] = GoalLinkKind.values
    def wire(e: GoalLinkKind): String = e.wire
    def fromWire(s: String): Option[GoalLinkKind] = parse(s)
