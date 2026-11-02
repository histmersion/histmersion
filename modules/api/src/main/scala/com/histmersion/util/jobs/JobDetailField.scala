package com.histmersion.util.jobs

import com.histmersion.api.JobDetail
import io.circe.{Decoder, Encoder, Json}
import sttp.tapir.{FieldName, Schema}
import sttp.tapir.SchemaType.SProductField

/** One field of a job's detail, as the owner of the kinds that record it declares it: its name on the wire, its value
  * when a job records none, and how it reads and writes. The jobs keep a detail as these fields' JSON ([[JobDetail]]);
  * the kinds' registry lists every field in the order the detail shows them.
  */
final class JobDetailField[A](val name: String, val default: A)(using
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
    SProductField[JobDetail, A](FieldName(name), schema.copy(isOptional = true), d => Some(of(d)))
