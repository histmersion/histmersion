package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** `latest` and `seen` are ISO-8601 UTC strings in one fixed format (`YYYY-MM-DDTHH:MM:SS.ffffffZ`, as the store
  * renders them), so they compare as strings.
  */
object UiNotices:
  enum Board(val wire: Board.Wire, val counted: Board.Counted):
    case Goals extends Board(_root_.com.histmersion.api.instance.UiNotices.Board.Wire("goals"), counted = _root_.com.histmersion.api.instance.UiNotices.Board.Counted(false))
    case Tasks extends Board(_root_.com.histmersion.api.instance.UiNotices.Board.Wire("tasks"), counted = _root_.com.histmersion.api.instance.UiNotices.Board.Counted(true))
    case Approvals extends Board(_root_.com.histmersion.api.instance.UiNotices.Board.Wire("approvals"), counted = _root_.com.histmersion.api.instance.UiNotices.Board.Counted(true))
    case Acquire extends Board(_root_.com.histmersion.api.instance.UiNotices.Board.Wire("acquire"), counted = _root_.com.histmersion.api.instance.UiNotices.Board.Counted(true))
    case Log extends Board(_root_.com.histmersion.api.instance.UiNotices.Board.Wire("log"), counted = _root_.com.histmersion.api.instance.UiNotices.Board.Counted(false))

  object Board:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Counted = Counted.T
    object Counted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type WiresResult = _root_.com.histmersion.api.instance.UiNotices.Board.Wire
    val WiresResult: _root_.com.histmersion.api.instance.UiNotices.Board.Wire.type = _root_.com.histmersion.api.instance.UiNotices.Board.Wire

    def parse(s: com.histmersion.api.instance.UiNotices.Board.Wire): Option[Board] = values.find(_.wire == _root_.com.histmersion.api.instance.UiNotices.Board.Wire(Option(s).map((_).value.trim.toLowerCase).getOrElse("")))
    def wires: List[com.histmersion.api.instance.UiNotices.Board.WiresResult] = values.toList.map(_.wire)

  final case class Counter(
      key: Board.Wire,
      count: Option[Counter.Count] = None,
      latest: Option[Counter.Latest] = None,
      seen: Option[Counter.Seen] = None,
      fresh: Counter.Fresh = _root_.com.histmersion.api.instance.UiNotices.Counter.Fresh(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Counter:
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Latest = Latest.T
    object Latest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seen = Seen.T
    object Seen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fresh = Fresh.T
    object Fresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class AgentsGlance(running: AgentsGlance.Running = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Running(0), queued: AgentsGlance.Queued = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Queued(0), on: AgentsGlance.On = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.On(false), draining: AgentsGlance.Draining = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Draining(false))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:

    /** The agents at a glance, as the top bar says it. */
    def wording: String =
      def runs(n: com.histmersion.api.instance.UiNotices.AgentsGlance.Running) = if n == _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Running(1) then "1 run" else s"$n runs"
      if !(on).value || (((draining) == _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Draining(true)) && running == _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Running(0)) then ("Agents paused")
      else if (draining).value then s"Agents finishing current work · ${runs(running)}"
      else if (running).value > 0 then s"Agents working · ${runs(running)}"
      else if (queued).value > 0 then (s"Agents working · $queued waiting")
      else ("Agents on · nothing to do")

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
    type At = com.histmersion.api.instance.UiNotices.Counter.Seen
    val At: _root_.com.histmersion.api.instance.UiNotices.Counter.Seen.type = _root_.com.histmersion.api.instance.UiNotices.Counter.Seen


  def fresh(latest: Option[com.histmersion.api.instance.UiNotices.Counter.Latest], seen: Option[com.histmersion.api.instance.UiNotices.Counter.Seen]): Boolean =
    (latest.exists(l => seen.forall(s => (l).value > (s).value)))

  def counter(board: Board, count: Option[com.histmersion.api.instance.UiNotices.Counter.Count], latest: Option[com.histmersion.api.instance.UiNotices.Counter.Latest], seen: Option[com.histmersion.api.instance.UiNotices.Counter.Seen]): Counter =
    Counter(board.wire, count.filter(_ => ((board.counted) == _root_.com.histmersion.api.instance.UiNotices.Board.Counted(true))), latest, seen, _root_.com.histmersion.api.instance.UiNotices.Counter.Fresh(fresh(latest, seen)))

  def advance(held: Option[com.histmersion.api.instance.UiNotices.Counter.Seen], at: com.histmersion.api.instance.UiNotices.SeenMark.At): com.histmersion.api.instance.UiNotices.Counter.Seen = held.filter((_).value > (at).value).getOrElse(at)

  def badge(count: Option[com.histmersion.api.instance.UiNotices.Counter.Count]): Option[String] =
    count.filter((_).value > 0).map(n => if (n).value > 99 then ("99+") else (n.toString))
