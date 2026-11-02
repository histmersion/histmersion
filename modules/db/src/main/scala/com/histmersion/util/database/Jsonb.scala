package com.histmersion.util.database
import com.augustnagro.magnum.*
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*

/** A `jsonb` column holding a wire DTO, crossing through that DTO's own circe codec (#1946) — the Vocab.codec rule for
  * JSON: select the column as `col::text`, bind the value as `$v::jsonb`, and a stored document this build cannot read
  * (NULL, or written by a newer build) reads as `fallback` ([[codec]]) or as absent ([[optional]]), never as an
  * exception. `dropNulls` leaves absent fields out of what is written, for the columns whose readers treat a missing
  * field as its default. [[read]], [[decode]] and [[write]] are the same rules for a repo that holds the text itself.
  */
object Jsonb:
  def codec[A: {Encoder, Decoder}](fallback: A, dropNulls: Boolean = false): DbCodec[A] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]).getOrElse(fallback), a => Some(write(a, dropNulls)))

  def optional[A: {Encoder, Decoder}](dropNulls: Boolean = false): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]), _.map(write(_, dropNulls)))

  /** The stored text as an `A`; `None` when it does not decode. */
  def read[A: Decoder](text: String): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  def decode[A: Decoder](text: String): Either[String, A] =
    io.circe.parser.decode[A](text).left.map(_.getMessage)

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  def write[A: Encoder](a: A, dropNulls: Boolean = false): String =
    val j = a.asJson
    (if dropNulls then j.dropNullValues else j).noSpaces
