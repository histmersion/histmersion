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
  type CodecDropNulls = CodecDropNulls.T
  object CodecDropNulls extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionalDropNulls = OptionalDropNulls.T
  object OptionalDropNulls extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadText = ReadText.T
  object ReadText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResultPart1 = DecodeResultPart1.T
  object DecodeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeText = DecodeText.T
  object DecodeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResult = WriteResult.T
  object WriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteDropNulls = WriteDropNulls.T
  object WriteDropNulls extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def codec[A: {Encoder, Decoder}](fallback: A, dropNulls: com.histmersion.util.database.Jsonb.CodecDropNulls = false): DbCodec[A] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]).getOrElse(fallback), a => Some(write(a, dropNulls)))

  def optional[A: {Encoder, Decoder}](dropNulls: com.histmersion.util.database.Jsonb.OptionalDropNulls = false): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]), _.map(write(_, dropNulls)))

  /** The stored text as an `A`; `None` when it does not decode. */
  def read[A: Decoder](text: com.histmersion.util.database.Jsonb.ReadText): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  def decode[A: Decoder](text: com.histmersion.util.database.Jsonb.DecodeText): Either[com.histmersion.util.database.Jsonb.DecodeResultPart1, A] =
    io.circe.parser.decode[A](text).left.map(_.getMessage)

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  def write[A: Encoder](a: A, dropNulls: com.histmersion.util.database.Jsonb.WriteDropNulls = false): com.histmersion.util.database.Jsonb.WriteResult =
    val j = a.asJson
    (if dropNulls then j.dropNullValues else j).noSpaces
