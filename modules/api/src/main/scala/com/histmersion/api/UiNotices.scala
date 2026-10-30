package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `latest` and `seen` are ISO-8601 UTC strings in one fixed format (`YYYY-MM-DDTHH:MM:SS.ffffffZ`, as the store
  * renders them), so they compare as strings.
  */
object UiNotices:

  enum Board(val wire: String, val counted: Boolean):
    case Goals extends Board("goals", counted = false)
    case Tasks extends Board("tasks", counted = true)
    case Approvals extends Board("approvals", counted = true)
    case Acquire extends Board("acquire", counted = true)
    case Log extends Board("log", counted = false)

  object Board:
    def parse(s: String): Option[Board] = values.find(_.wire == Option(s).map(_.trim.toLowerCase).getOrElse(""))
    def wires: List[String] = values.toList.map(_.wire)

  final case class Counter(
      key: String,
      count: Option[Int] = None,
      latest: Option[String] = None,
      seen: Option[String] = None,
      fresh: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counter:
    given Schema[Counter] = summon[KindlingsSchema[Counter]].schema

  final case class AgentsGlance(running: Int = 0, queued: Int = 0, on: Boolean = false, draining: Boolean = false)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object AgentsGlance:
    given Schema[AgentsGlance] = summon[KindlingsSchema[AgentsGlance]].schema

  final case class Counters(boards: List[Counter], agents: Option[AgentsGlance] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counters:
    given Schema[Counters] = summon[KindlingsSchema[Counters]].schema

  final case class SeenMark(at: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object SeenMark:
    given Schema[SeenMark] = summon[KindlingsSchema[SeenMark]].schema

  def fresh(latest: Option[String], seen: Option[String]): Boolean =
    latest.exists(l => seen.forall(s => l > s))

  def counter(board: Board, count: Option[Int], latest: Option[String], seen: Option[String]): Counter =
    Counter(board.wire, count.filter(_ => board.counted), latest, seen, fresh(latest, seen))

  def advance(held: Option[String], at: String): String = held.filter(_ > at).getOrElse(at)

  def badge(count: Option[Int]): Option[String] =
    count.filter(_ > 0).map(n => if n > 99 then "99+" else n.toString)
