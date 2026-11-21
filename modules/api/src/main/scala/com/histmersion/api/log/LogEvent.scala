package com.histmersion
package api.log

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

import LogEvent.{Actor, At, Id, Kind, Label, Subject, Trace, What}

/** One step the server took, as the structured log keeps it: in its table and, under the same id, in its rolling
  * JSON-lines file. `trace` links the steps of one request or job across threads, jobs, provider calls and agent steps;
  * `label` is the caller's own name for its calls, kept beside the trace and never used as one; `subjects` are what the
  * step concerned. Every field is typed: no free text, no body, no secret.
  */
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

  /** Kept as long as problems are (the longer retention): a problem, a failed answer, a failed provider call. */
  def problem: Boolean = what.failing

  /** The name of the event's case, as queries filter on it (`kind=`). */
  def kind: Kind = what.kind

  /** The event as one line of Markdown: its time, its step, what it concerned, its trace and the caller's label. */
  def markdown: String =
    val named = if subjects.isEmpty then "" else subjects.map(_.ref).mkString(" (", ", ", ")")
    s"- ${at.shown} · ${what.markdown}$named · trace `$trace`${label.fold("")(l => s" [$l]")}"

object LogEvent:

  /** A UUIDv7 (RFC 9562: 48 bits of Unix milliseconds, the version, 74 random bits) in lower case, so the ids sort by
    * time: of `millis` and the random bits `high` (12 used) and `low` (62 used). The caller supplies the clock's
    * reading and the randomness.
    */
  private def uuidV7(millis: Long, high: Long, low: Long): String =
    val msb = ((millis & 0xffffffffffffL) << 16) | 0x7000L | (high & 0x0fffL)
    val lsb = (low & 0x3fffffffffffffffL) | Long.MinValue // the variant bits 10
    java.util.UUID(msb, lsb).toString

  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    /** A new id: the UUIDv7 of `millis` and the random bits `high` and `low`. */
    def v7(millis: Long, high: Long, low: Long): Id = Id(uuidV7(millis, high, low))

    extension (i: Id)
      /** Whether it sorts before `other` (as the table orders its UUIDs: byte by byte, as their lower-case text). */
      def sortsBefore(other: Id): Boolean = i.value < other.value

  /** An ISO-8601 instant: in UTC to the microsecond, as the database keeps it, so the table and the file compare the
    * same.
    */
  type At = At.T
  object At extends Newtype[String, Pure]:
    private val Iso =
      java.time.format.DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(java.time.ZoneOffset.UTC)

    /** `instant` to the microsecond, as `2026-11-20T09:15:02.123456Z`. */
    def of(instant: java.time.Instant): At = At(Iso.format(instant.truncatedTo(java.time.temporal.ChronoUnit.MICROS)))

    extension (a: At)
      /** The UTC day it falls on (`2026-11-20`): the log file it goes in. */
      def day: String = a.value.take(10)

      /** As a person reads it: to the millisecond, the date and the time apart (`2026-11-20 09:15:02.123`). */
      def shown: String = a.value.take(23).replace('T', ' ')

      /** The instant it names. */
      def instant: java.time.Instant = java.time.Instant.parse(a.value)

  type Trace = Trace.T
  object Trace extends Newtype[String, Pure]:
    /** A new trace, as the server mints one: the UUIDv7 of `millis` and the random bits `high` and `low`. */
    def v7(millis: Long, high: Long, low: Long): Trace = Trace(uuidV7(millis, high, low))

    /** The trace an answer's header names: trimmed and in lower case, none when blank. */
    def fromHeader(header: String): Option[Trace] = Trace(header).present

    extension (t: Trace)
      /** As a filter gives it: trimmed and in lower case (as the server mints it), none when blank. */
      def present: Option[Trace] = Option(t.value.trim.toLowerCase).filter(_.nonEmpty).map(Trace(_))

      /** The request the trace was opened for, as what events concern. */
      def request: Subject = Subject.Request(t)

      /** Whether it has the shape the server mints: a UUID in lower case. */
      def wellFormed: Boolean =
        t.value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    /** How long a client's label may be; a longer one is cut. */
    val MaxLength: Int = 200

    /** A client's label as a header gives it: trimmed and cut to [[MaxLength]], none when blank. */
    def fromHeader(header: String): Option[Label] = Label(header).present

    extension (l: Label)
      /** As a filter or a header gives it: trimmed and cut to [[MaxLength]], none when blank. */
      def present: Option[Label] = Option(l.value.trim).filter(_.nonEmpty).map(v => Label(v.take(MaxLength)))

  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]:
    /** The kind of `what`: its case's name. */
    def of(what: What): Kind = Kind(what.productPrefix)

    extension (k: Kind)
      /** The event case it names, whatever its case: none when it names none. */
      def known: Option[Kind] = Kinds.find(_.value.equalsIgnoreCase(k.value.trim))

      def present: Boolean = k.value.trim.nonEmpty

  /** A route template (`GET /api/sources/{citekey}`), never the raw path with its query. */
  type Operation = Operation.T
  object Operation extends Newtype[String, Pure]:
    /** The route an endpoint matches: its path with its parameters named (`/api/sources/{citekey}`), never the query.
      */
    def of(endpoint: sttp.tapir.Endpoint[?, ?, ?, ?, ?]): Operation =
      Operation(endpoint.showPathTemplate(showQueryParam = None))

    extension (o: Operation)
      /** The operation as one counted per minute names it: with its method (`GET /api/health`). */
      def by(method: Method): Operation = Operation(s"$method $o")

  type Method = Method.T
  object Method extends Newtype[String, Pure]:
    /** An HTTP request's method (`GET`). */
    def of(method: sttp.model.Method): Method = Method(method.method)

    extension (m: Method)
      /** A read: `GET` or `HEAD`. */
      def reads: Boolean = m.value == "GET" || m.value == "HEAD"

  type Status = Status.T
  object Status extends Newtype[Int, Pure]:
    /** An HTTP answer's status code. */
    def of(code: sttp.model.StatusCode): Status = Status(code.code)

    extension (s: Status) def serverError: Boolean = s.value >= 500

  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]:
    /** The whole milliseconds between two readings of the JVM's monotonic clock (`System.nanoTime`). */
    def between(startNanos: Long, endNanos: Long): Millis = Millis((endNanos - startNanos) / 1000000L)

  type Count = Count.T
  object Count extends Newtype[Long, Pure]:
    val zero: Count = Count(0L)
    val one: Count = Count(1L)

    /** How many `xs` holds. */
    def of(xs: Iterable[?]): Count = Count(xs.size.toLong)

    extension (c: Count)
      def +(other: Count): Count = Count(c.value + other.value)

      def positive: Boolean = c.value > 0

  /** The address the request came from. */
  type Client = Client.T
  object Client extends Newtype[String, Pure]

  /** What the caller searched for (the `q` of a search). */
  type Search = Search.T
  object Search extends Newtype[String, Pure]:
    /** How much of a search's text is kept; the rest is cut. */
    val MaxLength: Int = 500

    /** A search's text as its query parameter gives it: trimmed and cut to [[MaxLength]], none when blank. */
    def fromQuery(q: String): Option[Search] = Option(q.trim).filter(_.nonEmpty).map(s => Search(s.take(MaxLength)))

  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]:
    /** The provider a failed call would name. */
    def of(name: util.problem.Problem.Upstream.Name): Upstream = Upstream(s"$name")

  type Host = Host.T
  object Host extends Newtype[String, Pure]:
    /** The host a provider's name stands for (the URL's host). */
    def of(name: util.problem.Problem.Upstream.Name): Host = Host(s"$name")

  type Table = Table.T
  object Table extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Pure]:
    extension (k: Key)
      /** As a filter gives it: trimmed, none when blank. */
      def present: Option[Key] = Option(k.value.trim).filter(_.nonEmpty).map(Key(_))

  /** An MCP tool's name. */
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]

  type ProblemId = ProblemId.T
  object ProblemId extends Newtype[String, Pure]:
    /** The id an unexpected problem's answer named. */
    def of(id: util.problem.Problem.Internal.Id): ProblemId = ProblemId(s"$id")

    extension (p: ProblemId)
      /** The problem as what events concern. */
      def subject: Subject = Subject.Problem(p)

      /** As a filter gives it: trimmed, none when blank. */
      def present: Option[ProblemId] = Option(p.value.trim).filter(_.nonEmpty).map(ProblemId(_))

  /** A problem's `<group>.<case>`. */
  type ProblemCase = ProblemCase.T
  object ProblemCase extends Newtype[String, Pure]:
    /** The case of `problem`: `<group>.<case>` (`Internal.Unexpected`). */
    def of(problem: util.problem.Problem): ProblemCase =
      ProblemCase(s"${problem.group.productPrefix}.${problem.productPrefix}")

  /** Who took the step. */
  enum Actor derives CanEqual:
    /** A caller of the HTTP API, the MCP endpoint or the pages. */
    case Client

    /** A background job or agent run, on a worker thread. */
    case Worker

    /** The server itself: start-up, the log's own housekeeping. */
    case Server

    /** The database: a write statement its triggers recorded. */
    case Database

  /** What a step concerned, so "everything about job X" or "about source Y" finds it across traces. */
  enum Subject derives CanEqual:
    case Source(key: Key)
    case Job(key: Key)
    case AgentRun(key: Key)
    case Problem(key: ProblemId)
    case Request(key: Trace)

    /** The subject as the table's `subjects` column holds it: `<kind>:<key>`. */
    def ref: String = this match
      case Source(k)   => s"source:$k"
      case Job(k)      => s"job:$k"
      case AgentRun(k) => s"run:$k"
      case Problem(k)  => s"problem:$k"
      case Request(k)  => s"request:$k"

  /** A write statement's change. */
  enum Change derives CanEqual:
    case Insert, Update, Delete, Truncate

    /** As a sentence names it: `insert`, `update`, … */
    def word: String = toString.toLowerCase

  /** Why events were lost. */
  enum Loss derives CanEqual:
    /** The writer's queue was full: the events reached neither the table nor the file. */
    case QueueFull

    /** The database stayed unreachable longer than the backlog holds: the events are in the file only. */
    case DatabaseBacklog

  /** What happened. */
  enum What derives CanEqual:
    /** A request answered: its route template, method, status, time, client address and search text. */
    case RequestAnswered(
        operation: Operation,
        method: Method,
        status: Status,
        ms: Millis,
        client: Option[Client],
        search: Option[Search]
    )

    /** Requests counted rather than logged one by one (map tiles, static files, the health check), per minute. */
    case RequestsCounted(operation: Operation, count: Count, failed: Count)

    /** An outbound call to a provider: which, which host, its status (none: unreachable) and time. */
    case ProviderCalled(upstream: Upstream, host: Host, status: Option[Status], ms: Millis)

    /** A write statement, as the database's triggers recorded it: table, change, rows (none for a truncate) and the
      * first keys of the rows.
      */
    case Written(table: Table, change: Change, rows: Option[Count], keys: Vector[Key])

    /** A problem raised: its case, and for an unexpected one the id its caller was shown. */
    case ProblemRaised(id: Option[ProblemId], problem: ProblemCase)

    /** Events lost, and why. */
    case Lost(count: Count, loss: Loss)

    /** An MCP tool called, by its name (the request itself is `POST /mcp`). */
    case ToolCalled(tool: Tool)

    /** Write statements counted rather than recorded one by one (lease renewals, quota updates, job heartbeats), per
      * minute: table, change, how many statements.
      */
    case WritesCounted(table: Table, change: Change, count: Count)

    def kind: Kind = Kind.of(this)

    /** Kept as long as problems are. */
    def failing: Boolean = this match
      case r: RequestAnswered => r.status.serverError
      case p: ProviderCalled  => p.status.forall(_.serverError)
      case _: ProblemRaised   => true
      case _: Lost            => true
      case _                  => false

    /** The step as a person reads it, in one line of Markdown. */
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

  /** Every case's name, for the `kind` filter's description and check. */
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
