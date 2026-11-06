package com.histmersion.api.jobs
import io.circe.{Codec, Decoder, DecodingFailure, HCursor, Json, JsonObject}
import sttp.tapir.Schema
import sttp.tapir.SchemaType.SProduct
import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.circe.{Decoder, Encoder, Json}
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.{FieldName, Schema}

/** What a job records about itself beyond its kind and state: each field its kind's owner declares
  * ([[JobDetailField]]), kept as JSON. The wire shows every field the kinds' registry lists, in its order, each job's
  * value or the field's default; a detail holds only the values that differ from their default.
  */
final case class JobDetail private (values: Map[JobDetail.Field.Name, Json]):
  def json(name: com.histmersion.api.jobs.JobDetail.Field.Name): Option[Json] = values.get(name)

  /** The detail with `field` set to `a`. */
  def set[A](field: com.histmersion.api.jobs.JobDetail.Field[A], a: A): JobDetail =
    JobDetail(field.jsonOf(a).fold(values - field.name)(j => values.updated(field.name, j)))

  /** The detail with `field` changed by `f`. */
  def change[A](field: com.histmersion.api.jobs.JobDetail.Field[A])(f: A => A): JobDetail =
    set(field, f(field.of(this)))

object JobDetail:
  val empty: JobDetail = JobDetail(Map.empty)

  /** What a job is about, in a person's words (a places or base-map job's). */
  val Purpose: com.histmersion.api.jobs.JobDetail.Field[Option[com.histmersion.api.jobs.JobKinds.Subject]] =
    new com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("about"), None)

  private def fields: List[com.histmersion.api.jobs.JobDetail.Field[?]] = RegisteredJobKinds.all.details

  given Codec.AsObject[JobDetail] = new Codec.AsObject[JobDetail]:
    def encodeObject(d: JobDetail): JsonObject =
      JsonObject.fromIterable(fields.map(f => f.name.value -> d.values.getOrElse(f.name, f.defaultJson)))
    def apply(c: HCursor): Decoder.Result[JobDetail] =
      if !c.value.isObject then Left(DecodingFailure("JobDetail", c.history))
      else
        val read = fields.map(f =>
          c.downField(f.name.value).focus.filterNot(_.isNull).fold(Right(None))(j => f.canonical(j)).map(f.name -> _)
        )
        read
          .collectFirst { case Left(e) => e }
          .toLeft(JobDetail(read.collect { case Right((n, Some(j))) => n -> j }.toMap))

  given Schema[JobDetail] =
    Schema(SProduct(fields.map(_.schemaField)), Some(Schema.SName("com.histmersion.api.jobs.JobDetail")))

  /** What a job is about: its source, else what its detail says (each kind's owner says how). */
  def subject(j: JobInfo): Option[com.histmersion.api.jobs.JobKinds.Subject] = RegisteredJobKinds.all.subject(j)

  /** One field of a job's detail, as the owner of the kinds that record it declares it: its name on the wire, its value
    * when a job records none, and how it reads and writes. The jobs keep a detail as these fields' JSON
    * ([[JobDetail]]); the kinds' registry lists every field in the order the detail shows them.
    */
  final class Field[A](val name: Field.Name, val default: A)(using
      encoder: Encoder[A],
      decoder: Decoder[A],
      schema: Schema[A]
  ):
    private[histmersion] lazy val defaultJson: Json = encoder(default)

    /** The field's value in `d`: what the job recorded, else the default. */
    def of(d: JobDetail): A = d.json(name).flatMap(decoder.decodeJson(_).toOption).getOrElse(default)

    /** A detail holding this field alone. */
    def apply(a: A): JobDetail = JobDetail.empty.set(this, a)

    /** `a` as the detail keeps it: `None` for the default, which a detail does not hold. */
    private[histmersion] def jsonOf(a: A): Option[Json] = Option(encoder(a)).filterNot(_ == defaultJson)

    /** The value `json` holds, read and written again in this field's terms — or why it is not one. */
    private[histmersion] def canonical(json: Json): Decoder.Result[Option[Json]] = decoder.decodeJson(json).map(jsonOf)

    /** The field in the detail's schema: optional, as a field with a default is. */
    private[histmersion] def schemaField: SProductField[JobDetail] =
      SProductField[JobDetail, A](FieldName((name).value), schema.copy(isOptional = true), d => Some(of(d)))

  object Field:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
