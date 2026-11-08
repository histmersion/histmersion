package com.histmersion
package util.database
import com.augustnagro.magnum.*
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import io.github.iltotore.iron.constraint.any.Pure

/** A `jsonb` column holding a wire DTO, crossing through that DTO's own circe codec (#1946) — the Vocab.codec rule for
  * JSON: select the column as `col::text`, bind the value as `$v::jsonb`, and a stored document this build cannot read
  * (NULL, or written by a newer build) reads as `fallback` ([[codec]]) or as absent ([[optional]]), never as an
  * exception. `dropNulls` leaves absent fields out of what is written, for the columns whose readers treat a missing
  * field as its default. [[read]], [[decode]] and [[write]] are the same rules for a repo that holds the text itself.
  */
object Jsonb:
  type DropNulls = DropNulls.T
  object DropNulls extends Newtype[Boolean, Pure]
  type JsonText = JsonText.T
  object JsonText extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  def codec[A: {Encoder, Decoder}](fallback: A, dropNulls: DropNulls = DropNulls(false)): DbCodec[A] =
    DbCodec[Option[String]]
      .biMap(_.flatMap(t => read[A](JsonText(t))).getOrElse(fallback), a => Some(write(a, dropNulls).value))

  def optional[A: {Encoder, Decoder}](dropNulls: DropNulls = DropNulls(false)): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(t => read[A](JsonText(t))), _.map(a => write(a, dropNulls).value))

  /** The stored text as an `A`; `None` when it does not decode. */
  def read[A: Decoder](text: JsonText): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  def decode[A: Decoder](text: JsonText): Either[Problem, A] =
    io.circe.parser.decode[A](text.value).left.map(e => Problem(e.getMessage))

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  def write[A: Encoder](a: A, dropNulls: DropNulls = DropNulls(false)): JsonText =
    val j = a.asJson
    JsonText((if dropNulls.value then j.dropNullValues else j).noSpaces)
