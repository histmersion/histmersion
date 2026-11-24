package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector
import util.json.RawJson.*
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.model.StatusCode

import json.NewtypeCodecs.given
import util.problem.{Problem, Problems}
import util.problem.Problem.*
import util.problem.Text.sentence
import Answer.Entry

/** The body of every failed request: `{"problems": [{"group", "case", "data", "text"}]}`, always a list. `data` is the
  * case's fields as its derived codec writes them; `text` is its English sentence, for `curl` and for clients that do
  * not render problems themselves. The companion maps a problem to its HTTP status, by group (and by case where a group
  * mixes them).
  */
final case class Answer(problems: Vector[Entry]) derives JsonCodec, KindlingsSchema

object Answer:
  /** One problem on the wire. */
  final case class Entry(group: Group, `case`: Case, data: Json, text: Sentence) derives JsonCodec, KindlingsSchema

  /** The group a problem belongs to, by its name (`Invalid`). */
  type Group = Group.T
  object Group extends Newtype[String, Pure]

  /** The case within the group, by its name (`Malformed`). */
  type Case = Case.T
  object Case extends Newtype[String, Pure]

  extension (problem: Problem)
    /** The status of a response that fails with this problem. A path outside the directory it is confined to is one the
      * caller chose: refused (403), not a failure of the server.
      */
    def status: StatusCode = problem match
      case _: Invalid                     => StatusCode.BadRequest
      case _: NotFound                    => StatusCode.NotFound
      case _: Conflict.Stale              => StatusCode.PreconditionFailed
      case _: Conflict.Busy               => StatusCode.ServiceUnavailable
      case _: Conflict.ServerHeld         => StatusCode.Conflict
      case _: Conflict.Undrained          => StatusCode.ServiceUnavailable
      case _: Conflict.AlreadyExists      => StatusCode.Conflict
      case _: Conflict.Renamed            => StatusCode.Conflict
      case _: Conflict.Aliased            => StatusCode.Conflict
      case _: Conflict.OldKey             => StatusCode.Conflict
      case _: Conflict.Collides           => StatusCode.Conflict
      case _: Conflict.Claimed            => StatusCode.Conflict
      case _: Conflict.WrongState         => StatusCode.Conflict
      case _: Conflict.Referenced         => StatusCode.Conflict
      case _: Conflict.Referred           => StatusCode.Conflict
      case _: Conflict.Held               => StatusCode.Conflict
      case _: Conflict.Job                => StatusCode.Conflict
      case _: Refused.Policy              => StatusCode.Forbidden
      case _: Refused.Disabled            => StatusCode.Forbidden
      case _: Refused.Space               => StatusCode.Forbidden
      case _: Refused.Unauthenticated     => StatusCode.Unauthorized
      case _: Refused.Misdirected         => StatusCode.MisdirectedRequest
      case _: Upstream.Http               => StatusCode.BadGateway
      case _: Upstream.Unreachable        => StatusCode.BadGateway
      case _: Upstream.Challenged         => StatusCode.BadGateway
      case _: Upstream.Limited            => StatusCode.TooManyRequests
      case _: Upstream.Offline            => StatusCode.ServiceUnavailable
      case _: Upstream.Changed            => StatusCode.BadGateway
      case _: Upstream.Rebuilt            => StatusCode.BadGateway
      case _: Upstream.Mislisted          => StatusCode.BadGateway
      case Upstream.Unasked(why)          => why.status
      case _: Upstream.BadReply           => StatusCode.BadGateway
      case _: Upstream.CallFailed         => StatusCode.BadGateway
      case _: Upstream.CallThrew          => StatusCode.BadGateway
      case _: Upstream.Replied            => StatusCode.BadGateway
      case _: Upstream.McpServer          => StatusCode.BadGateway
      case _: Upstream.Timeout            => StatusCode.GatewayTimeout
      case _: Upstream.Unanswered         => StatusCode.GatewayTimeout
      case Io.Wrong(_, Io.Kind.Inside(_)) => StatusCode.Forbidden
      case _: Io                          => StatusCode.InternalServerError
      case _: Config                      => StatusCode.ServiceUnavailable
      case _: Plugin.Load                 => StatusCode.InternalServerError
      case _: Plugin.Vault                => StatusCode.InternalServerError
      case _: Plugin.Rejected             => StatusCode.UnprocessableEntity
      case _: Plugin.Dependency           => StatusCode.Conflict
      case Plugin.Said(_, claim, _)       => claim.status
      case _: Problem.Run                 => StatusCode.Conflict
      case _: Content                     => StatusCode.UnprocessableEntity
      case _: Internal                    => StatusCode.InternalServerError
      case _: Unclassified                => StatusCode.BadRequest
      case _: Stored                      => StatusCode.InternalServerError
      case _: Relayed                     => StatusCode.InternalServerError

    /** When a refused request may be sent again: the end of a spent allowance, when it is known. */
    def retryAfter: Option[EpochMs] = problem match
      case Upstream.Limited(_, until) => until
      case Upstream.Offline(_, until) => until
      case Upstream.Unasked(why)      => why.retryAfter
      case _                          => None

    /** The problem as the body lists it: its group, its case, its fields and its sentence. A [[Relayed]] problem, known
      * by its sentence alone, is the `Relayed` group and case with no fields, which reads back as the same problem. The
      * server, the CLI and the browser write every problem so (`NewtypeCodecs.problemCodec`).
      */
    def entry: Entry = problem match
      case relayed: Relayed => entryOf(relayed.group, relayed.productPrefix, Json.obj(), relayed.sentence)
      case typed            =>
        val (name, data) = typed.encoded
        entryOf(typed.group, name, data, Sentence(s"${typed.sentence}"))

  /** An entry of the body: the group and the case by their names. */
  private def entryOf(group: Problem.Group, name: String, data: Json, sentence: Sentence): Entry =
    Entry(Group(s"${group.productPrefix}"), Case(s"$name"), data, sentence)

  extension (claim: Plugin.Claim)
    private def status: StatusCode = claim match
      case Plugin.Claim.Unavailable   => StatusCode.ServiceUnavailable
      case Plugin.Claim.Rejected      => StatusCode.UnprocessableEntity
      case Plugin.Claim.Misconfigured => StatusCode.ServiceUnavailable
      case Plugin.Claim.Failed        => StatusCode.BadGateway

  extension (problems: Problems)
    /** The status of a response that fails with these problems: the first one's. */
    def status: StatusCode = problems.toNonEmpty.head.status

    /** The body of a response that fails with these problems. */
    def answer: Answer = Answer(problems.toVector.map(_.entry))

  extension (answer: Answer)
    /** The problems a body lists; a body that lists none, or a case this client does not know, is a failure. */
    def decoded: Either[String, Problems] =
      val read = answer.problems.map(_.problem)
      read
        .collectFirst { case Left(failure) => failure }
        .toLeft(read.collect { case Right(problem) => problem })
        .flatMap(all => Problems.from(all).toRight("no problems"))

  final case class Former(group: Group, `case`: Case)

  private def into(group: Group, name: Case, path: (String, String)*): Entry => Entry = entry =>
    val data = path.foldRight(entry.data)((step, inner) => Json.obj(step._1 -> Json.obj(step._2 -> inner)))
    Entry(group, name, data, entry.text)

  private def atStep(failure: String): Entry => Entry = entry =>
    val fields = entry.data.asObject.getOrElse(JsonObject.empty)
    if fields("failure").isDefined then entry
    else
      val step = fields("step").toList.map("step" -> _)
      val rest = Json.fromJsonObject(fields.remove("step"))
      Entry(runs, Case("AtStep"), Json.obj(step :+ ("failure" -> Json.obj(failure -> rest))*), entry.text)

  private def asStoredText: Entry => Entry = entry =>
    Entry(Group("Stored"), Case("Unrecognised"), Json.obj("text" -> entry.text.asJson), entry.text)

  private val runs = Group("Run")
  private val stopped = Case("Stopped")
  private val needs = Case("Needs")

  val translations: Map[Former, Entry => Entry] = Map(
    Former(runs, Case("Stalled")) -> into(runs, stopped, "stop" -> "Stalled"),
    Former(runs, Case("Silent")) -> into(runs, stopped, "stop" -> "Silent"),
    Former(runs, Case("NoPipeline")) -> into(runs, stopped, "stop" -> "NoPipeline"),
    Former(runs, Case("PipelineChanged")) -> into(runs, stopped, "stop" -> "PipelineChanged"),
    Former(runs, Case("Reordered")) -> into(runs, stopped, "stop" -> "Reordered"),
    Former(runs, Case("CheckpointUnread")) -> into(runs, stopped, "stop" -> "CheckpointUnread"),
    Former(runs, Case("Vanished")) -> into(runs, stopped, "stop" -> "Vanished"),
    Former(runs, Case("Overspent")) -> into(runs, stopped, "stop" -> "Over", "budget" -> "Usd"),
    Former(runs, Case("OverTokens")) -> into(runs, stopped, "stop" -> "Over", "budget" -> "Tokens"),
    Former(runs, Case("OverCap")) -> into(runs, stopped, "stop" -> "Over", "budget" -> "Capped"),
    Former(runs, Case("MovedOn")) -> into(runs, stopped, "stop" -> "MovedOn"),
    Former(runs, Case("Withdrawn")) -> into(runs, stopped, "stop" -> "Withdrawn"),
    Former(runs, Case("GuardStopped")) -> into(runs, stopped, "stop" -> "Guard"),
    Former(runs, Case("Unrecorded")) -> into(runs, stopped, "stop" -> "Unrecorded"),
    Former(runs, Case("NoText")) -> into(runs, needs, "need" -> "Text"),
    Former(runs, Case("NoBody")) -> into(runs, needs, "need" -> "Body"),
    Former(runs, Case("NoLogin")) -> into(runs, needs, "need" -> "Login"),
    Former(runs, Case("ProbeUnread")) -> into(runs, needs, "need" -> "Probe"),
    Former(runs, Case("AtStep")) -> atStep("Failed"),
    Former(runs, Case("Unsettled")) -> atStep("Unsettled"),
    Former(runs, Case("ProviderDown")) -> atStep("ProviderDown"),
    Former(runs, Case("BudgetSpent")) -> atStep("BudgetSpent"),
    Former(runs, Case("NoValidAnswer")) -> atStep("NoValidAnswer"),
    Former(runs, Case("OutputCapped")) -> atStep("OutputCapped"),
    Former(runs, Case("Declined")) -> asStoredText
  )

  extension (entry: Entry)
    def current: Entry = translations.get(Former(entry.group, entry.`case`)).fold(entry)(_(entry))

    /** The problem one entry of the body names. */
    def problem: Either[String, Problem] =
      val now = entry.current
      val wrapped = Json.obj(s"${now.`case`}" -> now.data)
      Problem.Group.values
        .find(g => g.productPrefix == s"${now.group}")
        .toRight(s"no group ${now.group}")
        .flatMap[String, Problem] {
          case Problem.Group.Invalid      => wrapped.as(using invalid)
          case Problem.Group.NotFound     => wrapped.as(using notFound)
          case Problem.Group.Conflict     => wrapped.as(using conflict)
          case Problem.Group.Refused      => wrapped.as(using refused)
          case Problem.Group.Upstream     => wrapped.as(using upstream)
          case Problem.Group.Io           => wrapped.as(using io)
          case Problem.Group.Config       => wrapped.as(using config)
          case Problem.Group.Plugin       => wrapped.as(using plugin)
          case Problem.Group.Run          => wrapped.as(using run)
          case Problem.Group.Content      => wrapped.as(using ContentCodec.content)
          case Problem.Group.Internal     => wrapped.as(using internal)
          case Problem.Group.Unclassified => now.data.as(using unclassified)
          case Problem.Group.Stored       => wrapped.as(using stored)
          case Problem.Group.Relayed      => Right(Relayed(now.text))
        }

  extension (json: Json)
    /** The problem a wire record or a stored column carries: an entry as its case ([[problem]]), on the JVM and in the
      * browser alike; a bare string as this platform reads one ([[ProblemJson.bare]]); an entry of a case this build
      * does not know as this platform reads one ([[ProblemJson.unknown]]: the browser shows its sentence, E19).
      */
    def asProblem: Either[String, Problem] =
      json.asString.fold(
        json.as[Entry].flatMap(e => e.problem.left.flatMap(why => ProblemJson.unknown(e.text).toRight(why)))
      )(text => Right(ProblemJson.bare(text)))

  // ---- the derived codecs of the cases' fields ----------------------------------------------------------------

  // code words as strings (`"Read"`), a case's defaults honoured; one derivation for the JVM and the browser alike.
  // Lazy: a case holding a problem writes it through `NewtypeCodecs.problemCodec`, which comes back here

  private lazy val invalid = JsonCodec.derivedWords[Invalid]
  private lazy val notFound = JsonCodec.derivedWords[NotFound]
  private lazy val conflict = JsonCodec.derivedWords[Conflict]
  private lazy val refused = JsonCodec.derivedWords[Refused]
  private lazy val upstream = JsonCodec.derivedWords[Upstream]
  private lazy val io = JsonCodec.derivedWords[Io]
  private lazy val config = JsonCodec.derivedWords[Config]
  private lazy val plugin = JsonCodec.derivedWords[Plugin]
  private lazy val run = JsonCodec.derivedWords[Problem.Run]
  private lazy val internal = JsonCodec.derivedWords[Internal]
  private lazy val unclassified = JsonCodec.derivedWords[Unclassified]
  private lazy val stored = JsonCodec.derivedWords[Stored]

  extension (problem: Problem)
    /** The case's name and its fields, as the group's derived codec writes them. */
    private def encoded: (String, Json) =
      val wrapped: JsonObject = problem match
        case p: Invalid      => p.asJson(using invalid).asObject.getOrElse(JsonObject.empty)
        case p: NotFound     => p.asJson(using notFound).asObject.getOrElse(JsonObject.empty)
        case p: Conflict     => p.asJson(using conflict).asObject.getOrElse(JsonObject.empty)
        case p: Refused      => p.asJson(using refused).asObject.getOrElse(JsonObject.empty)
        case p: Upstream     => p.asJson(using upstream).asObject.getOrElse(JsonObject.empty)
        case p: Io           => p.asJson(using io).asObject.getOrElse(JsonObject.empty)
        case p: Config       => p.asJson(using config).asObject.getOrElse(JsonObject.empty)
        case p: Plugin       => p.asJson(using plugin).asObject.getOrElse(JsonObject.empty)
        case p: Problem.Run  => p.asJson(using run).asObject.getOrElse(JsonObject.empty)
        case p: Content      => p.asJson(using ContentCodec.content).asObject.getOrElse(JsonObject.empty)
        case p: Internal     => p.asJson(using internal).asObject.getOrElse(JsonObject.empty)
        case p: Unclassified => JsonObject("Unclassified" -> p.asJson(using unclassified))
        case p: Stored       => p.asJson(using stored).asObject.getOrElse(JsonObject.empty)
        case _: Relayed      => JsonObject.empty // never asked: [[entry]] writes a relayed problem itself
      wrapped.toList.headOption.getOrElse(("", Json.obj()))
