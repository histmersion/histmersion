package com.histmersion.api

import com.histmersion.util.jobs.JobDetailField
import io.circe.{Codec, Decoder, DecodingFailure, HCursor, Json, JsonObject}
import sttp.tapir.Schema
import sttp.tapir.SchemaType.SProduct

/** What a job records about itself beyond its kind and state: each field its kind's owner declares
  * ([[JobDetailField]]), kept as JSON. The wire shows every field the kinds' registry lists, in its order, each job's
  * value or the field's default; a detail holds only the values that differ from their default.
  */
final case class JobDetail private (values: Map[String, Json]):
  def json(name: String): Option[Json] = values.get(name)

  /** The detail with `field` set to `a`. */
  def set[A](field: JobDetailField[A], a: A): JobDetail =
    JobDetail(field.jsonOf(a).fold(values - field.name)(j => values.updated(field.name, j)))

  /** The detail with `field` changed by `f`. */
  def change[A](field: JobDetailField[A])(f: A => A): JobDetail = set(field, f(field.of(this)))

object JobDetail:
  val empty: JobDetail = JobDetail(Map.empty)

  /** What a job is about, in a person's words (a places or base-map job's). */
  val Purpose: JobDetailField[Option[String]] = JobDetailField("about", None)

  private def fields: List[JobDetailField[?]] = RegisteredJobKinds.all.details

  given Codec.AsObject[JobDetail] = new Codec.AsObject[JobDetail]:
    def encodeObject(d: JobDetail): JsonObject =
      JsonObject.fromIterable(fields.map(f => f.name -> d.values.getOrElse(f.name, f.defaultJson)))
    def apply(c: HCursor): Decoder.Result[JobDetail] =
      if !c.value.isObject then Left(DecodingFailure("JobDetail", c.history))
      else
        val read = fields.map(f =>
          c.downField(f.name).focus.filterNot(_.isNull).fold(Right(None))(j => f.canonical(j)).map(f.name -> _)
        )
        read
          .collectFirst { case Left(e) => e }
          .toLeft(JobDetail(read.collect { case Right((n, Some(j))) => n -> j }.toMap))

  given Schema[JobDetail] =
    Schema(SProduct(fields.map(_.schemaField)), Some(Schema.SName("com.histmersion.api.JobDetail")))

  /** What a job is about: its source, else what its detail says (each kind's owner says how). */
  def subject(j: JobInfo): Option[String] = RegisteredJobKinds.all.subject(j)
