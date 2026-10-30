package com.histmersion.values

/** Whether a compile run writes a NEW article or APPENDS a section to an existing one. */
enum DraftMode(val wire: String):
  case Create extends DraftMode("create")
  case Extend extends DraftMode("extend")

object DraftMode:
  def parse(s: String): Option[DraftMode] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = DraftMode.values
    def wire(e: DraftMode): String = e.wire
    def fromWire(s: String): Option[DraftMode] = parse(s)

/** What a goal is linked to. */
enum GoalLinkKind(val wire: String):
  case Source extends GoalLinkKind("source")
  case Article extends GoalLinkKind("article")
  case Query extends GoalLinkKind("query")
  case QueueItem extends GoalLinkKind("queue-item")

object GoalLinkKind:
  def parse(s: String): Option[GoalLinkKind] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[GoalLinkKind]:
    def all: Array[GoalLinkKind] = GoalLinkKind.values
    def wire(e: GoalLinkKind): String = e.wire
    def fromWire(s: String): Option[GoalLinkKind] = parse(s)

/** What a goal's schedule starts: a currency check of its sources, or an acquisition sweep of its queries.
  */
enum ScheduleKind(val wire: String, val pipeline: Pipeline):
  case Currency extends ScheduleKind("currency", Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind("acquisition", Pipeline.AcquisitionSweep)

object ScheduleKind:
  def parse(s: String): Option[ScheduleKind] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = ScheduleKind.values
    def wire(e: ScheduleKind): String = e.wire
    def fromWire(s: String): Option[ScheduleKind] = parse(s)
