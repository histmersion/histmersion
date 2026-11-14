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
import Answer.Entry
import util.problem.Text.sentence

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

  /** The English sentence of the problem. */
  type Sentence = Sentence.T
  object Sentence extends Newtype[String, Pure]

  extension (problem: Problem)
    /** The status of a response that fails with this problem. */
    def status: StatusCode = problem match
      case _: Invalid                => StatusCode.BadRequest
      case _: NotFound               => StatusCode.NotFound
      case _: Conflict.Stale         => StatusCode.PreconditionFailed
      case _: Conflict.Busy          => StatusCode.ServiceUnavailable
      case _: Conflict.AlreadyExists => StatusCode.Conflict
      case _: Conflict.Renamed       => StatusCode.Conflict
      case _: Conflict.WrongState    => StatusCode.Conflict
      case _: Conflict.Referenced    => StatusCode.Conflict
      case _: Conflict.Held          => StatusCode.Conflict
      case _: Conflict.Job           => StatusCode.Conflict
      case _: Refused.Policy         => StatusCode.Forbidden
      case _: Refused.Quota          => StatusCode.TooManyRequests
      case _: Refused.Disabled       => StatusCode.Forbidden
      case _: Upstream.Http          => StatusCode.BadGateway
      case _: Upstream.Unreachable   => StatusCode.BadGateway
      case _: Upstream.Challenged    => StatusCode.BadGateway
      case _: Upstream.Limited       => StatusCode.TooManyRequests
      case _: Upstream.BadReply      => StatusCode.BadGateway
      case _: Upstream.Timeout       => StatusCode.GatewayTimeout
      case _: Io                     => StatusCode.InternalServerError
      case _: Config                 => StatusCode.ServiceUnavailable
      case _: Plugin.Load            => StatusCode.InternalServerError
      case _: Plugin.Vault           => StatusCode.InternalServerError
      case _: Plugin.Rejected        => StatusCode.UnprocessableEntity
      case _: Plugin.Dependency      => StatusCode.Conflict
      case Plugin.Said(_, claim, _)  => claim.status
      case _: Problem.Run            => StatusCode.Conflict
      case _: Content                => StatusCode.UnprocessableEntity
      case _: Internal               => StatusCode.InternalServerError
      case _: Unclassified           => StatusCode.InternalServerError
      case _: Stored                 => StatusCode.InternalServerError

    /** When a refused request may be sent again: the end of a spent allowance, when it is known. */
    def retryAfter: Option[EpochMs] = problem match
      case Refused.Quota(_, _, _, until) => until
      case Upstream.Limited(_, until)    => until
      case _                             => None

    /** The problem as the body lists it: its group, its case, its fields and its sentence. */
    def entry: Entry =
      val (name, data) = problem.encoded
      Entry(Group(s"${problem.group.productPrefix}"), Case(s"$name"), data, Sentence(s"${problem.sentence}"))

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

  extension (entry: Entry)
    /** The problem one entry of the body names. */
    def problem: Either[String, Problem] =
      val wrapped = Json.obj(s"${entry.`case`}" -> entry.data)
      Problem.Group.values
        .find(g => g.productPrefix == s"${entry.group}")
        .toRight(s"no group ${entry.group}")
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
          case Problem.Group.Content      => wrapped.as(using content)
          case Problem.Group.Internal     => wrapped.as(using internal)
          case Problem.Group.Unclassified => entry.data.as(using unclassified)
          case Problem.Group.Stored       => wrapped.as(using stored)
        }

  // ---- the derived codecs of the cases' fields ----------------------------------------------------------------

  // code words as strings (`"Read"`), a case's defaults honoured

  private val invalid = JsonCodec.derivedWords[Invalid]
  private val notFound = JsonCodec.derivedWords[NotFound]
  private val conflict = JsonCodec.derivedWords[Conflict]
  private val refused = JsonCodec.derivedWords[Refused]
  private val upstream = JsonCodec.derivedWords[Upstream]
  private val io = JsonCodec.derivedWords[Io]
  private val config = JsonCodec.derivedWords[Config]
  private val plugin = JsonCodec.derivedWords[Plugin]
  private val run = JsonCodec.derivedWords[Problem.Run]
  private val content = JsonCodec.derivedWords[Content]
  private val internal = JsonCodec.derivedWords[Internal]
  private val unclassified = JsonCodec.derivedWords[Unclassified]
  private val stored = JsonCodec.derivedWords[Stored]

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
        case p: Content      => p.asJson(using content).asObject.getOrElse(JsonObject.empty)
        case p: Internal     => p.asJson(using internal).asObject.getOrElse(JsonObject.empty)
        case p: Unclassified => JsonObject("Unclassified" -> p.asJson(using unclassified))
        case p: Stored       => p.asJson(using stored).asObject.getOrElse(JsonObject.empty)
      wrapped.toList.headOption.getOrElse(("", Json.obj()))
