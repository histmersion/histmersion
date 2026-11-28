package com.histmersion
package api.instance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

/** `latest` and `seen` are ISO-8601 UTC strings in one fixed format (`YYYY-MM-DDTHH:MM:SS.ffffffZ`, as the store
  * renders them), so they compare as strings.
  */
object UiNotices:
  enum Board(val wire: Board.Wire, val counted: Board.Counted) derives CanEqual:
    case Goals extends Board(Board.Wire("goals"), counted = Board.Counted.Uncounted)
    case Tasks extends Board(Board.Wire("tasks"), counted = Board.Counted.Counted)
    case Approvals extends Board(Board.Wire("approvals"), counted = Board.Counted.Counted)
    case Acquire extends Board(Board.Wire("acquire"), counted = Board.Counted.Counted)
    case Log extends Board(Board.Wire("log"), counted = Board.Counted.Uncounted)

  object Board:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    enum Counted derives CanEqual:
      case Counted, Uncounted
    object Counted extends TwoStates[Counted]:
      extension (counted: Counted) def counted: Boolean = counted == Counted

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Board] =
      values.find(_.wire == Wire(Option(s).map((_).value.trim.toLowerCase).getOrElse("")))
    def wires: List[Wire] = values.toList.map(_.wire)

  final case class Counter(
      key: Board.Wire,
      count: Option[Counter.Count] = None,
      latest: Option[Counter.Latest] = None,
      seen: Option[Counter.Seen] = None,
      fresh: Counter.Fresh = Counter.Fresh.Seen
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Counter:
    type Count = Count.T
    object Count extends Newtype[Int, Pure]
    type Latest = Latest.T
    object Latest extends Newtype[String, Pure]
    type Seen = Seen.T
    object Seen extends Newtype[String, Pure]
    enum Fresh derives CanEqual:
      case Fresh, Seen
    object Fresh extends TwoStates[Fresh]:
      extension (fresh: Fresh) def fresh: Boolean = fresh == Fresh

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class AgentsGlance(
      running: AgentsGlance.Running = AgentsGlance.Running(0),
      queued: AgentsGlance.Queued = AgentsGlance.Queued(0),
      on: AgentsGlance.On = AgentsGlance.On.Off,
      draining: AgentsGlance.Draining = AgentsGlance.Draining.Running
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:

    /** The agents at a glance, as the top bar says it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wording: String =
      def runs(n: AgentsGlance.Running) = if n == AgentsGlance.Running(1) then "1 run" else s"$n runs"
      if !(on).on || (((draining) == AgentsGlance.Draining.Draining) && running == AgentsGlance.Running(0)) then
        ("Agents paused")
      else if (draining).draining then s"Agents finishing current work · ${runs(running)}"
      else if (running).value > 0 then s"Agents working · ${runs(running)}"
      else if (queued).value > 0 then (s"Agents working · $queued waiting")
      else ("Agents on · nothing to do")

  object AgentsGlance:

    type Running = Running.T
    object Running extends Newtype[Int, Pure]
    type Queued = Queued.T
    object Queued extends Newtype[Int, Pure]
    enum On derives CanEqual:
      case On, Off
    object On extends TwoStates[On]:
      extension (on: On) def on: Boolean = on == On
    enum Draining derives CanEqual:
      case Draining, Running
    object Draining extends TwoStates[Draining]:
      extension (draining: Draining) def draining: Boolean = draining == Draining

  final case class Counters(boards: List[Counter], agents: Option[AgentsGlance] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  final case class SeenMark(at: Option[SeenMark.At] = None) derives JsonCodec, KindlingsSchema, api.json.Schema

  object SeenMark:
    export Counter.Seen as At

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fresh(latest: Option[Counter.Latest], seen: Option[Counter.Seen]): Boolean =
    (latest.exists(l => seen.forall(s => (l).value > (s).value)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def counter(
      board: Board,
      count: Option[Counter.Count],
      latest: Option[Counter.Latest],
      seen: Option[Counter.Seen]
  ): Counter =
    Counter(
      board.wire,
      count.filter(_ => ((board.counted) == Board.Counted.Counted)),
      latest,
      seen,
      (if fresh(latest, seen) then Counter.Fresh.Fresh else Counter.Fresh.Seen)
    )

  def advance(held: Option[Counter.Seen], at: SeenMark.At): Counter.Seen = held.filter((_) > (at)).getOrElse(at)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def badge(count: Option[Counter.Count]): Option[String] =
    count.filter((_).value > 0).map(n => if (n).value > 99 then ("99+") else (n.toString))
