package com.histmersion
package api
package problem

import hearth.kindlings.circederivation.{Configuration, KindlingsCodecAsObject}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.github.iltotore.iron.constraint.any.Pure
import sttp.model.StatusCode
import sttp.tapir.json.circe.schemaForCirceJson

import json.NewtypeCodecs.given
import util.problem.{NonEmptyVector, Problem, Problems}
import util.problem.Problem.*
import Answer.Entry
import Text.sentence

/** The body of every failed request: `{"problems": [{"group", "case", "data", "text"}]}`, always a list. `data` is the
  * case's fields as its derived codec writes them; `text` is its English sentence, for `curl` and for clients that do not
  * render problems themselves. The companion maps a problem to its HTTP status, by group (and by case where a group
  * mixes them).
  */
final case class Answer(problems: Vector[Entry]) derives KindlingsCodecAsObject, KindlingsSchema

object Answer:
  /** One problem on the wire. */
  final case class Entry(group: Group, `case`: Case, data: Json, text: Sentence)
      derives KindlingsCodecAsObject,
        KindlingsSchema

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
      case _: Invalid                  => StatusCode.BadRequest
      case _: NotFound                 => StatusCode.NotFound
      case _: Conflict.Stale           => StatusCode.PreconditionFailed
      case _: Conflict.Busy            => StatusCode.ServiceUnavailable
      case _: Conflict.AlreadyExists   => StatusCode.Conflict
      case _: Conflict.Renamed         => StatusCode.Conflict
      case _: Conflict.WrongState      => StatusCode.Conflict
      case _: Conflict.Referenced      => StatusCode.Conflict
      case _: Refused.Policy           => StatusCode.Forbidden
      case _: Refused.Quota            => StatusCode.TooManyRequests
      case _: Refused.Disabled         => StatusCode.Forbidden
      case _: Upstream.Http            => StatusCode.BadGateway
      case _: Upstream.Unreachable     => StatusCode.BadGateway
      case _: Upstream.BadReply        => StatusCode.BadGateway
      case _: Upstream.Timeout         => StatusCode.GatewayTimeout
      case _: Io                       => StatusCode.InternalServerError
      case _: Config                   => StatusCode.ServiceUnavailable
      case _: Plugin.Load              => StatusCode.InternalServerError
      case _: Plugin.Vault             => StatusCode.InternalServerError
      case _: Plugin.Rejected          => StatusCode.UnprocessableEntity
      case Plugin.Said(_, claim, _)    => claim.status
      case _: Problem.Run                    => StatusCode.Conflict
      case _: Content                  => StatusCode.UnprocessableEntity
      case _: Internal                 => StatusCode.InternalServerError
      case _: Unclassified             => StatusCode.InternalServerError
      case _: Stored                   => StatusCode.InternalServerError

    /** When a refused request may be sent again: the end of a spent allowance, when it is known. */
    def retryAfter: Option[EpochMs] = problem match
      case Refused.Quota(_, _, _, until) => until
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
    def decoded: Either[DecodingFailure, Problems] =
      val read = answer.problems.map(_.problem)
      read
        .collectFirst { case Left(failure) => failure }
        .toLeft(read.collect { case Right(problem) => problem })
        .flatMap(all =>
          NonEmptyVector
            .from(all)
            .map[Problems](nev => if nev.size == 1 then nev.head else nev)
            .toRight(DecodingFailure("no problems", Nil))
        )

  extension (entry: Entry)
    /** The problem one entry of the body names. */
    def problem: Either[DecodingFailure, Problem] =
      val wrapped = Json.obj(s"${entry.`case`}" -> entry.data)
      Problem.Group.values
        .find(g => g.productPrefix == s"${entry.group}")
        .toRight(DecodingFailure(s"no group ${entry.group}", Nil))
        .flatMap[DecodingFailure, Problem] {
          case Problem.Group.Invalid      => invalid.decodeJson(wrapped)
          case Problem.Group.NotFound     => notFound.decodeJson(wrapped)
          case Problem.Group.Conflict     => conflict.decodeJson(wrapped)
          case Problem.Group.Refused      => refused.decodeJson(wrapped)
          case Problem.Group.Upstream     => upstream.decodeJson(wrapped)
          case Problem.Group.Io           => io.decodeJson(wrapped)
          case Problem.Group.Config       => config.decodeJson(wrapped)
          case Problem.Group.Plugin       => plugin.decodeJson(wrapped)
          case Problem.Group.Run          => run.decodeJson(wrapped)
          case Problem.Group.Content      => content.decodeJson(wrapped)
          case Problem.Group.Internal     => internal.decodeJson(wrapped)
          case Problem.Group.Unclassified => unclassified.decodeJson(entry.data)
          case Problem.Group.Stored       => stored.decodeJson(wrapped)
        }

  // ---- the derived codecs of the cases' fields ----------------------------------------------------------------

  /** Code words as strings (`"Read"`), a case's defaults honoured. */
  private given Configuration = Configuration.default.withDefaults.withEnumAsStrings

  private given [A] => (elements: Encoder[A]) => Encoder[NonEmptyVector[A]] =
    Encoder.encodeVector[A].contramap(_.toVector)

  private given [A] => (elements: Decoder[A]) => Decoder[NonEmptyVector[A]] =
    Decoder.decodeVector[A].emap(NonEmptyVector.from(_).toRight("an empty list"))

  private val invalid = summon[KindlingsCodecAsObject[Invalid]]
  private val notFound = summon[KindlingsCodecAsObject[NotFound]]
  private val conflict = summon[KindlingsCodecAsObject[Conflict]]
  private val refused = summon[KindlingsCodecAsObject[Refused]]
  private val upstream = summon[KindlingsCodecAsObject[Upstream]]
  private val io = summon[KindlingsCodecAsObject[Io]]
  private val config = summon[KindlingsCodecAsObject[Config]]
  private val plugin = summon[KindlingsCodecAsObject[Plugin]]
  private val run = summon[KindlingsCodecAsObject[Problem.Run]]
  private val content = summon[KindlingsCodecAsObject[Content]]
  private val internal = summon[KindlingsCodecAsObject[Internal]]
  private val unclassified = summon[KindlingsCodecAsObject[Unclassified]]
  private val stored = summon[KindlingsCodecAsObject[Stored]]

  extension (problem: Problem)
    /** The case's name and its fields, as the group's derived codec writes them. */
    private def encoded: (String, Json) =
      val wrapped: JsonObject = problem match
        case p: Invalid      => invalid.encodeObject(p)
        case p: NotFound     => notFound.encodeObject(p)
        case p: Conflict     => conflict.encodeObject(p)
        case p: Refused      => refused.encodeObject(p)
        case p: Upstream     => upstream.encodeObject(p)
        case p: Io           => io.encodeObject(p)
        case p: Config       => config.encodeObject(p)
        case p: Plugin       => plugin.encodeObject(p)
        case p: Problem.Run        => run.encodeObject(p)
        case p: Content      => content.encodeObject(p)
        case p: Internal     => internal.encodeObject(p)
        case p: Unclassified => JsonObject.singleton("Unclassified", Json.fromJsonObject(unclassified.encodeObject(p)))
        case p: Stored       => stored.encodeObject(p)
      wrapped.toList.headOption.getOrElse(("", Json.obj()))
