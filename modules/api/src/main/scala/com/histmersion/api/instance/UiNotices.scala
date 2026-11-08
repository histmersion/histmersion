package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** `latest` and `seen` are ISO-8601 UTC strings in one fixed format (`YYYY-MM-DDTHH:MM:SS.ffffffZ`, as the store
  * renders them), so they compare as strings.
  */
object UiNotices:
  enum Board(val wire: Board.Wire, val counted: Board.Counted) derives CanEqual:
    case Goals extends Board(Board.Wire("goals"), counted = Board.Counted(false))
    case Tasks extends Board(Board.Wire("tasks"), counted = Board.Counted(true))
    case Approvals extends Board(Board.Wire("approvals"), counted = Board.Counted(true))
    case Acquire extends Board(Board.Wire("acquire"), counted = Board.Counted(true))
    case Log extends Board(Board.Wire("log"), counted = Board.Counted(false))

  object Board:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Counted = Counted.T
    object Counted extends Newtype[Boolean, Pure]

    type WiresResult = Wire
    val WiresResult: Wire.type = Wire

    def parse(s: Wire): Option[Board] = values.find(_.wire == Wire(Option(s).map((_).value.trim.toLowerCase).getOrElse("")))
    def wires: List[WiresResult] = values.toList.map(_.wire)

  final case class Counter(
      key: Board.Wire,
      count: Option[Counter.Count] = None,
      latest: Option[Counter.Latest] = None,
      seen: Option[Counter.Seen] = None,
      fresh: Counter.Fresh = Counter.Fresh(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Counter:
    type Count = Count.T
    object Count extends Newtype[Int, Pure]
    type Latest = Latest.T
    object Latest extends Newtype[String, Pure]
    type Seen = Seen.T
    object Seen extends Newtype[String, Pure]
    type Fresh = Fresh.T
    object Fresh extends Newtype[Boolean, Pure]


  final case class AgentsGlance(running: AgentsGlance.Running = AgentsGlance.Running(0), queued: AgentsGlance.Queued = AgentsGlance.Queued(0), on: AgentsGlance.On = AgentsGlance.On(false), draining: AgentsGlance.Draining = AgentsGlance.Draining(false))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:

    /** The agents at a glance, as the top bar says it. */
    def wording: String =
      def runs(n: AgentsGlance.Running) = if n == AgentsGlance.Running(1) then "1 run" else s"$n runs"
      if !(on).value || (((draining) == AgentsGlance.Draining(true)) && running == AgentsGlance.Running(0)) then ("Agents paused")
      else if (draining).value then s"Agents finishing current work · ${runs(running)}"
      else if (running).value > 0 then s"Agents working · ${runs(running)}"
      else if (queued).value > 0 then (s"Agents working · $queued waiting")
      else ("Agents on · nothing to do")

  object AgentsGlance:


    type Running = Running.T
    object Running extends Newtype[Int, Pure]
    type Queued = Queued.T
    object Queued extends Newtype[Int, Pure]
    type On = On.T
    object On extends Newtype[Boolean, Pure]
    type Draining = Draining.T
    object Draining extends Newtype[Boolean, Pure]


  final case class Counters(boards: List[Counter], agents: Option[AgentsGlance] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class SeenMark(at: Option[SeenMark.At] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object SeenMark:
    export Counter.{Seen as At}


  def fresh(latest: Option[Counter.Latest], seen: Option[Counter.Seen]): Boolean =
    (latest.exists(l => seen.forall(s => (l).value > (s).value)))

  def counter(board: Board, count: Option[Counter.Count], latest: Option[Counter.Latest], seen: Option[Counter.Seen]): Counter =
    Counter(board.wire, count.filter(_ => ((board.counted) == Board.Counted(true))), latest, seen, Counter.Fresh(fresh(latest, seen)))

  def advance(held: Option[Counter.Seen], at: SeenMark.At): Counter.Seen = held.filter((_) > (at)).getOrElse(at)

  def badge(count: Option[Counter.Count]): Option[String] =
    count.filter((_).value > 0).map(n => if (n).value > 99 then ("99+") else (n.toString))
