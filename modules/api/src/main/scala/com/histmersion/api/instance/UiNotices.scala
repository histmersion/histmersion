package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Acquire
import com.histmersion.api.given

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
      key: Counter.Key,
      count: Option[Counter.Count] = None,
      latest: Option[Counter.Latest] = None,
      seen: Option[Counter.Seen] = None,
      fresh: Counter.Fresh = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Counter:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Latest = Latest.T
    object Latest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seen = Seen.T
    object Seen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fresh = Fresh.T
    object Fresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class AgentsGlance(running: AgentsGlance.Running = 0, queued: AgentsGlance.Queued = 0, on: AgentsGlance.On = false, draining: AgentsGlance.Draining = false)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:

    /** The agents at a glance, as the top bar says it. */
    def wording: String =
      def runs(n: Int) = if n == 1 then "1 run" else s"$n runs"
      if !on || (draining && running == 0) then "Agents paused"
      else if draining then s"Agents finishing current work · ${runs(running)}"
      else if running > 0 then s"Agents working · ${runs(running)}"
      else if queued > 0 then s"Agents working · $queued waiting"
      else "Agents on · nothing to do"

  object AgentsGlance:
    type Running = Running.T
    object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queued = Queued.T
    object Queued extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Draining = Draining.T
    object Draining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Counters(boards: List[Counter], agents: Option[AgentsGlance] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class SeenMark(at: Option[SeenMark.At] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object SeenMark:
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def fresh(latest: Option[String], seen: Option[String]): Boolean =
    latest.exists(l => seen.forall(s => l > s))

  def counter(board: Board, count: Option[Int], latest: Option[String], seen: Option[String]): Counter =
    Counter(board.wire, count.filter(_ => board.counted), latest, seen, fresh(latest, seen))

  def advance(held: Option[String], at: String): String = held.filter(_ > at).getOrElse(at)

  def badge(count: Option[Int]): Option[String] =
    count.filter(_ > 0).map(n => if n > 99 then "99+" else n.toString)
