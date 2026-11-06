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

  type FreshResult = FreshResult.T
  object FreshResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreshLatest = FreshLatest.T
  object FreshLatest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreshSeen = FreshSeen.T
  object FreshSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CounterCount = CounterCount.T
  object CounterCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CounterLatest = CounterLatest.T
  object CounterLatest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CounterSeen = CounterSeen.T
  object CounterSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdvanceResult = AdvanceResult.T
  object AdvanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdvanceHeld = AdvanceHeld.T
  object AdvanceHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdvanceAt = AdvanceAt.T
  object AdvanceAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BadgeResult = BadgeResult.T
  object BadgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BadgeCount = BadgeCount.T
  object BadgeCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WiresResult = WiresResult.T
    object WiresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.instance.UiNotices.Board.ParseS): Option[Board] = values.find(_.wire == Option(s).map(_.trim.toLowerCase).getOrElse(""))
    def wires: List[com.histmersion.api.instance.UiNotices.Board.WiresResult] = values.toList.map(_.wire)

  final case class Counter(
      key: Counter.Key,
      count: Option[Counter.Count] = None,
      latest: Option[Counter.Latest] = None,
      seen: Option[Counter.Seen] = None,
      fresh: Counter.Fresh = _root_.com.histmersion.api.instance.UiNotices.Counter.Fresh(false)
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


  final case class AgentsGlance(running: AgentsGlance.Running = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Running(0), queued: AgentsGlance.Queued = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Queued(0), on: AgentsGlance.On = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.On(false), draining: AgentsGlance.Draining = _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.Draining(false))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:

    /** The agents at a glance, as the top bar says it. */
    def wording: com.histmersion.api.instance.UiNotices.AgentsGlance.WordingResult =
      def runs(n: com.histmersion.api.instance.UiNotices.AgentsGlance.WordingRunsN) = if n == 1 then "1 run" else s"$n runs"
      if !on || (draining && running == 0) then _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.WordingResult("Agents paused")
      else if draining then s"Agents finishing current work · ${runs(running)}"
      else if running > 0 then s"Agents working · ${runs(running)}"
      else if queued > 0 then _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.WordingResult(s"Agents working · $queued waiting")
      else _root_.com.histmersion.api.instance.UiNotices.AgentsGlance.WordingResult("Agents on · nothing to do")

  object AgentsGlance:
    type WordingRunsN = WordingRunsN.T
    object WordingRunsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type WordingResult = WordingResult.T
    object WordingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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


  def fresh(latest: Option[com.histmersion.api.instance.UiNotices.FreshLatest], seen: Option[com.histmersion.api.instance.UiNotices.FreshSeen]): com.histmersion.api.instance.UiNotices.FreshResult =
    latest.exists(l => seen.forall(s => l > s))

  def counter(board: Board, count: Option[com.histmersion.api.instance.UiNotices.CounterCount], latest: Option[com.histmersion.api.instance.UiNotices.CounterLatest], seen: Option[com.histmersion.api.instance.UiNotices.CounterSeen]): Counter =
    Counter(board.wire, count.filter(_ => board.counted), latest, seen, fresh(latest, seen))

  def advance(held: Option[com.histmersion.api.instance.UiNotices.AdvanceHeld], at: com.histmersion.api.instance.UiNotices.AdvanceAt): com.histmersion.api.instance.UiNotices.AdvanceResult = held.filter(_ > at).getOrElse(at)

  def badge(count: Option[com.histmersion.api.instance.UiNotices.BadgeCount]): Option[com.histmersion.api.instance.UiNotices.BadgeResult] =
    count.filter(_ > 0).map(n => if n > 99 then _root_.com.histmersion.api.instance.UiNotices.BadgeResult("99+") else _root_.com.histmersion.api.instance.UiNotices.BadgeResult(n.toString))
