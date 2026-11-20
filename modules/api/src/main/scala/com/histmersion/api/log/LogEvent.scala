package com.histmersion
package api.log

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** One step the server took, as the structured log keeps it: in its table and, under the same id, in its rolling
  * JSON-lines file. `trace` links the steps of one request or job across threads, jobs, provider calls and agent steps;
  * `label` is the caller's own name for its calls, kept beside the trace and never used as one; `subjects` are what the
  * step concerned. Every field is typed: no free text, no body, no secret.
  */
final case class LogEvent(
    id: LogEvent.Id,
    at: LogEvent.At,
    trace: LogEvent.Trace,
    label: Option[LogEvent.Label],
    actor: LogEvent.Actor,
    subjects: Vector[LogEvent.Subject],
    what: LogEvent.What
) derives JsonCodec,
      KindlingsSchema:

  /** Kept as long as problems are (the longer retention): a problem, a failed answer, a failed provider call. */
  def problem: Boolean = what.failing

  /** The name of the event's case, as queries filter on it (`kind=`). */
  def kind: LogEvent.Kind = what.kind

object LogEvent:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  /** An ISO-8601 instant. */
  type At = At.T
  object At extends Newtype[String, Pure]

  type Trace = Trace.T
  object Trace extends Newtype[String, Pure]

  type Label = Label.T
  object Label extends Newtype[String, Pure]

  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]

  /** A route template (`GET /api/sources/{citekey}`), never the raw path with its query. */
  type Operation = Operation.T
  object Operation extends Newtype[String, Pure]

  type Method = Method.T
  object Method extends Newtype[String, Pure]

  type Status = Status.T
  object Status extends Newtype[Int, Pure]

  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]

  type Count = Count.T
  object Count extends Newtype[Long, Pure]

  /** The address the request came from. */
  type Client = Client.T
  object Client extends Newtype[String, Pure]

  /** What the caller searched for (the `q` of a search). */
  type Search = Search.T
  object Search extends Newtype[String, Pure]

  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]

  type Host = Host.T
  object Host extends Newtype[String, Pure]

  type Table = Table.T
  object Table extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Pure]

  type ProblemId = ProblemId.T
  object ProblemId extends Newtype[String, Pure]

  /** A problem's `<group>.<case>`. */
  type ProblemCase = ProblemCase.T
  object ProblemCase extends Newtype[String, Pure]

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
    case Problem(key: Key)
    case Request(key: Key)

    /** The subject as the table's `subjects` column holds it: `<kind>:<key>`. */
    def ref: String = this match
      case Source(k)   => s"source:${k.value}"
      case Job(k)      => s"job:${k.value}"
      case AgentRun(k) => s"run:${k.value}"
      case Problem(k)  => s"problem:${k.value}"
      case Request(k)  => s"request:${k.value}"

  /** A write statement's change. */
  enum Change derives CanEqual:
    case Insert, Update, Delete, Truncate

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

    def kind: Kind = Kind(productPrefix)

    /** Kept as long as problems are. */
    def failing: Boolean = this match
      case r: RequestAnswered => r.status.value >= 500
      case p: ProviderCalled  => p.status.forall(_.value >= 500)
      case _: ProblemRaised   => true
      case _: Lost            => true
      case _                  => false

  /** Every case's name, for the `kind` filter's description and check. */
  val Kinds: List[String] = List(
    "RequestAnswered",
    "RequestsCounted",
    "ProviderCalled",
    "Written",
    "ProblemRaised",
    "Lost"
  )
