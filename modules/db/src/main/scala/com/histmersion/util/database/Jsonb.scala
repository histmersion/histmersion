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
  type DropNulls = DropNulls.T
  object DropNulls extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonText = JsonText.T
  object JsonText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def codec[A: {Encoder, Decoder}](fallback: A, dropNulls: com.histmersion.util.database.Jsonb.DropNulls = com.histmersion.util.database.Jsonb.DropNulls(false)): DbCodec[A] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]).getOrElse(fallback), a => Some(write(a, dropNulls)))

  def optional[A: {Encoder, Decoder}](dropNulls: com.histmersion.util.database.Jsonb.DropNulls = com.histmersion.util.database.Jsonb.DropNulls(false)): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]), _.map(write(_, dropNulls)))

  /** The stored text as an `A`; `None` when it does not decode. */
  def read[A: Decoder](text: com.histmersion.util.database.Jsonb.JsonText): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  def decode[A: Decoder](text: com.histmersion.util.database.Jsonb.JsonText): Either[com.histmersion.util.database.Jsonb.Problem, A] =
    io.circe.parser.decode[A](text).left.map(_.getMessage)

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  def write[A: Encoder](a: A, dropNulls: com.histmersion.util.database.Jsonb.DropNulls = com.histmersion.util.database.Jsonb.DropNulls(false)): com.histmersion.util.database.Jsonb.JsonText =
    val j = a.asJson
    (if dropNulls then j.dropNullValues else j).noSpaces
