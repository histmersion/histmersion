package com.histmersion
package api.log

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

import LogEvent.{Actor, At, Id, Kind, Label, Subject, Trace, What}
import com.histmersion.lint.{tolerated, LintRule}

final case class LogEvent(
    id: Id,
    at: At,
    trace: Trace,
    label: Option[Label],
    actor: Actor,
    subjects: Vector[Subject],
    what: What
) derives JsonCodec,
      KindlingsSchema:

  def problem: Boolean = what.failing

  def kind: Kind = what.kind

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown: String =
    val named = if subjects.isEmpty then "" else subjects.map(_.ref).mkString(" (", ", ", ")")
    s"- ${at.shown} · ${what.markdown}$named · trace `$trace`${label.fold("")(l => s" [$l]")}"

object LogEvent:

  private def uuidV7(millis: Long, high: Long, low: Long): String =
    val msb = ((millis & 0xffffffffffffL) << 16) | 0x7000L | (high & 0x0fffL)
    val lsb = (low & 0x3fffffffffffffffL) | Long.MinValue
    java.util.UUID(msb, lsb).toString

  private val Uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    def v7(millis: Long, high: Long, low: Long): Id = Id(uuidV7(millis, high, low))

    def parse(text: String): Option[Id] = Option.when(text.matches(Uuid))(Id(text))

    extension (i: Id) def sortsBefore(other: Id): Boolean = i.value < other.value

  type At = At.T
  object At extends Newtype[String, Pure]:
    private val Iso =
      java.time.format.DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(java.time.ZoneOffset.UTC)

    def of(instant: java.time.Instant): At = At(Iso.format(instant.truncatedTo(java.time.temporal.ChronoUnit.MICROS)))

    extension (a: At)
      def day: String = a.value.take(10)

      def shown: String = a.value.take(23).replace('T', ' ')

      def instant: java.time.Instant = java.time.Instant.parse(a.value)

  type Trace = Trace.T
  object Trace extends Newtype[String, Pure]:
    def v7(millis: Long, high: Long, low: Long): Trace = Trace(uuidV7(millis, high, low))

    def fromHeader(header: String): Option[Trace] = Trace(header).present

    extension (t: Trace)
      def present: Option[Trace] = Option(t.value.trim.toLowerCase).filter(_.nonEmpty).map(Trace(_))

      def request: Subject = Subject.Request(t)

      def wellFormed: Boolean = t.value.matches(Uuid)

  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    val MaxLength: Int = 200

    def fromHeader(header: String): Option[Label] = Label(header).present

    extension (l: Label)
      def present: Option[Label] = Option(l.value.trim).filter(_.nonEmpty).map(v => Label(v.take(MaxLength)))

  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]:
    def of(what: What): Kind = Kind(what.productPrefix)

    extension (k: Kind)
      def known: Option[Kind] = Kinds.find(_.value.equalsIgnoreCase(k.value.trim))

      def present: Boolean = k.value.trim.nonEmpty

  type Operation = Operation.T
  object Operation extends Newtype[String, Pure]:
    def of(endpoint: sttp.tapir.Endpoint[?, ?, ?, ?, ?]): Operation =
      Operation(endpoint.showPathTemplate(showQueryParam = None))

    extension (o: Operation) def by(method: Method): Operation = Operation(s"$method $o")

  type Method = Method.T
  object Method extends Newtype[String, Pure]:
    def of(method: sttp.model.Method): Method = Method(method.method)

    extension (m: Method) def reads: Boolean = m.value == "GET" || m.value == "HEAD"

  type Status = Status.T
  object Status extends Newtype[Int, Pure]:
    def of(code: sttp.model.StatusCode): Status = Status(code.code)

    extension (s: Status) def serverError: Boolean = s.value >= 500

  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]:
    def between(startNanos: Long, endNanos: Long): Millis = Millis((endNanos - startNanos) / 1000000L)

  type Count = Count.T
  object Count extends Newtype[Long, Pure]:
    val zero: Count = Count(0L)
    val one: Count = Count(1L)

    def of(xs: Iterable[?]): Count = Count(xs.size.toLong)

    extension (c: Count)
      def +(other: Count): Count = Count(c.value + other.value)

      def positive: Boolean = c.value > 0

  type Client = Client.T
  object Client extends Newtype[String, Pure]:
    def address(hostAddress: String): Client = Client(hostAddress)

  type Search = Search.T
  object Search extends Newtype[String, Pure]:
    val MaxLength: Int = 500

    def fromQuery(q: String): Option[Search] = Option(q.trim).filter(_.nonEmpty).map(s => Search(s.take(MaxLength)))

  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]:
    def of(name: util.problem.Problem.Upstream.Name): Upstream = Upstream(s"$name")

  type Host = Host.T
  object Host extends Newtype[String, Pure]:
    def of(name: util.problem.Problem.Upstream.Name): Host = Host(s"$name")

  type Table = Table.T
  object Table extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Pure]:
    extension (k: Key) def present: Option[Key] = Option(k.value.trim).filter(_.nonEmpty).map(Key(_))

  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]:
    def named(name: String): Tool = Tool(name)

  type ProblemId = ProblemId.T
  object ProblemId extends Newtype[String, Pure]:
    def of(id: util.problem.Problem.Internal.Id): ProblemId = ProblemId(s"$id")

    extension (p: ProblemId)
      def subject: Subject = Subject.Problem(p)

      def present: Option[ProblemId] = Option(p.value.trim).filter(_.nonEmpty).map(ProblemId(_))

  type ProblemCase = ProblemCase.T
  object ProblemCase extends Newtype[String, Pure]:
    def of(problem: util.problem.Problem): ProblemCase =
      ProblemCase(s"${problem.group.productPrefix}.${problem.productPrefix}")

  enum Actor derives CanEqual:
    case Client

    case Worker

    case Server

    case Database

  enum Subject derives CanEqual:
    case Source(key: Key)
    case Job(key: Key)
    case AgentRun(key: Key)
    case Problem(key: ProblemId)
    case Request(key: Trace)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def ref: String = this match
      case Source(k)   => s"source:$k"
      case Job(k)      => s"job:$k"
      case AgentRun(k) => s"run:$k"
      case Problem(k)  => s"problem:$k"
      case Request(k)  => s"request:$k"

  enum Change derives CanEqual:
    case Insert, Update, Delete, Truncate

    def word: String = toString.toLowerCase

  enum Loss derives CanEqual:
    case QueueFull

    case DatabaseBacklog

  enum What derives CanEqual:
    case RequestAnswered(
        operation: Operation,
        method: Method,
        status: Status,
        ms: Millis,
        client: Option[Client],
        search: Option[Search]
    )

    case RequestsCounted(operation: Operation, count: Count, failed: Count)

    case ProviderCalled(upstream: Upstream, host: Host, status: Option[Status], ms: Millis)

    case Written(table: Table, change: Change, rows: Option[Count], keys: Vector[Key])

    case ProblemRaised(id: Option[ProblemId], problem: ProblemCase)

    case Lost(count: Count, loss: Loss)

    case ToolCalled(tool: Tool)

    case WritesCounted(table: Table, change: Change, count: Count)

    def kind: Kind = Kind.of(this)

    def failing: Boolean = this match
      case r: RequestAnswered => r.status.serverError
      case p: ProviderCalled  => p.status.forall(_.serverError)
      case _: ProblemRaised   => true
      case _: Lost            => true
      case _                  => false

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown: String = this match
      case r: RequestAnswered =>
        val client = r.client.fold("")(c => s" from $c")
        val search = r.search.fold("")(s => s" searching “$s”")
        s"${r.method} ${r.operation} → ${r.status} in ${r.ms} ms$client$search"
      case c: RequestsCounted => s"${c.operation}: ${c.count} request(s), ${c.failed} failed"
      case p: ProviderCalled  =>
        s"${p.upstream} (${p.host}) → ${p.status.fold("unreachable")(s => s"$s")} in ${p.ms} ms"
      case w: Written =>
        val rows = w.rows.fold("")(n => s" $n row(s)")
        val keys = if w.keys.isEmpty then "" else w.keys.mkString(": ", ", ", "")
        s"${w.change.word} ${w.table}$rows$keys"
      case p: ProblemRaised => s"problem ${p.problem}${p.id.fold("")(i => s" (id `$i`)")}"
      case t: ToolCalled    => s"MCP tool `${t.tool}`"
      case w: WritesCounted => s"${w.count} ${w.change.word} statement(s) on ${w.table}, counted"
      case l: Lost          =>
        l.loss match
          case Loss.QueueFull       => s"${l.count} event(s) lost: the log's queue was full"
          case Loss.DatabaseBacklog => s"${l.count} event(s) kept in the log file only: the database stayed unreachable"

  val Kinds: List[Kind] = List(
    Kind("RequestAnswered"),
    Kind("RequestsCounted"),
    Kind("ProviderCalled"),
    Kind("Written"),
    Kind("ProblemRaised"),
    Kind("Lost"),
    Kind("ToolCalled"),
    Kind("WritesCounted")
  )
