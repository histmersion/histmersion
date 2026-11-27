package com.histmersion
package api.jobs
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.{Json, JsonObject}
import sttp.tapir.SchemaType.SProduct
import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson.equality
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.{FieldName, Schema}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a job records about itself beyond its kind and state: each field its kind's owner declares
  * ([[JobDetailField]]), kept as JSON. The wire shows every field the kinds' registry lists, in its order, each job's
  * value or the field's default; a detail holds only the values that differ from their default.
  */
final case class JobDetail private (values: Map[JobDetail.Field.Name, Json]) derives CanEqual:
  def json(name: JobDetail.Field.Name): Option[Json] = values.get(name)

  /** The detail with `field` set to `a`. */
  def set[A](field: JobDetail.Field[A], a: A): JobDetail =
    JobDetail(field.jsonOf(a).fold(values - field.name)(j => values.updated(field.name, j)))

  /** The detail with `field` changed by `f`. */
  def change[A](field: JobDetail.Field[A])(f: A => A): JobDetail =
    set(field, f(field.of(this)))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object JobDetail:
  val empty: JobDetail = JobDetail(Map.empty)

  /** What a job is about, in a person's words (a places or base-map job's). */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  val Purpose: Field[Option[JobKinds.Subject]] =
    new com.histmersion.api.jobs.JobDetail.Field(Field.Name("about"), None)

  private def fields: List[Field[?]] = RegisteredJobKinds.all.details

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  given JsonValueCodec[JobDetail] = JsonCodec.mapped[JobDetail, Json](
    d => Json.Obj(JsonObject.fromIterable(fields.map(f => f.name.value -> d.values.getOrElse(f.name, f.defaultJson)))),
    json =>
      json.asObject.toRight("JobDetail").flatMap { o =>
        val read =
          fields.map(f => o(f.name.value).filterNot(_.isNull).fold(Right(None))(j => f.canonical(j)).map(f.name -> _))
        read
          .collectFirst { case Left(e) => e }
          .toLeft(JobDetail(read.collect { case Right((n, Some(j))) => n -> j }.toMap))
      }
  )

  given Schema[JobDetail] =
    Schema(SProduct(fields.map(_.schemaField)), Some(Schema.SName("com.histmersion.api.jobs.JobDetail")))

  /** What a job is about: its source, else what its detail says (each kind's owner says how). */
  def subject(j: JobInfo): Option[JobKinds.Subject] = RegisteredJobKinds.all.subject(j)

  /** One field of a job's detail, as the owner of the kinds that record it declares it: its name on the wire, its value
    * when a job records none, and how it reads and writes. The jobs keep a detail as these fields' JSON
    * ([[JobDetail]]); the kinds' registry lists every field in the order the detail shows them.
    */

  final class Field[A](val name: Field.Name, val default: A)(using
      codec: JsonValueCodec[A],
      schema: Schema[A]
  ):
    private[histmersion] lazy val defaultJson: Json = default.asJson

    /** The field's value in `d`: what the job recorded, else the default. */
    def of(d: JobDetail): A = d.json(name).flatMap(_.as[A].toOption).getOrElse(default)

    /** A detail holding this field alone. */
    def apply(a: A): JobDetail = empty.set(this, a)

    /** `a` as the detail keeps it: `None` for the default, which a detail does not hold. */
    private[histmersion] def jsonOf(a: A): Option[Json] = Option(a.asJson).filterNot(_ == defaultJson)

    /** The value `json` holds, read and written again in this field's terms — or why it is not one. */
    private[histmersion] def canonical(json: Json): Either[String, Option[Json]] = json.as[A].map(jsonOf)

    /** The field in the detail's schema: optional, as a field with a default is. */
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private[histmersion] def schemaField: SProductField[JobDetail] =
      SProductField[JobDetail, A](FieldName((name).value), schema.copy(isOptional = true), d => Some(of(d)))

  object Field:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
