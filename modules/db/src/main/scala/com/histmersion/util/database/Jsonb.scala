package com.histmersion
package util.database
import com.histmersion.util.problem.External
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Problem

import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** A `jsonb` column holding a wire DTO, crossing through that DTO's own JSON codec (#1946) — the Vocab.codec rule for
  * JSON: select the column as `col::text`, bind the value as `$v::jsonb`, and a stored document this build cannot read
  * (NULL, or written by a newer build) reads as `fallback` ([[codec]]) or as absent ([[optional]]), never as an
  * exception. `dropNulls` leaves absent fields out of what is written, for the columns whose readers treat a missing
  * field as its default. [[read]], [[decode]] and [[write]] are the same rules for a repo that holds the text itself.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes jsonb columns through the DTO's JSON codec")
object Jsonb:
  type DropNulls = DropNulls.T
  object DropNulls extends Newtype[Boolean, Pure]
  type JsonText = JsonText.T
  object JsonText extends Newtype[String, Pure]

  /** A NULL column reads as `fallback`, and as `None` where the column is read as an `Option` (an outer join's miss).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def codec[A: JsonValueCodec](fallback: A, dropNulls: DropNulls = DropNulls(false)): DbCodec[A] = new DbCodec[A]:
    val cols: IArray[Int] = IArray(java.sql.Types.VARCHAR)
    def queryRepr: String = "?"
    def readSingle(rs: java.sql.ResultSet, pos: Int): A = readSingleOption(rs, pos).getOrElse(fallback)
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def readSingleOption(rs: java.sql.ResultSet, pos: Int): Option[A] =
      Option(rs.getString(pos)).map(t => Jsonb.read[A](JsonText(t)).getOrElse(fallback))
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def writeSingle(a: A, ps: java.sql.PreparedStatement, pos: Int): Unit =
      ps.setString(pos, Jsonb.write(a, dropNulls).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def optional[A: JsonValueCodec](dropNulls: DropNulls = DropNulls(false)): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(t => read[A](JsonText(t))), _.map(a => write(a, dropNulls).value))

  /** The stored text as an `A`; `None` when it does not decode. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def read[A: JsonValueCodec](text: JsonText): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def decode[A: JsonValueCodec](text: JsonText): Either[Problem, A] =
    RawJson
      .decode[A](text.value)
      .left
      .map(e =>
        Problem.Io.Corrupt(
          Problem.Io.Format.Json,
          Problem.Where("a stored JSON column"),
          Problem.Io.Damage.Unreadable(External.Reported.of(e))
        )
      )

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def write[A: JsonValueCodec](a: A, dropNulls: DropNulls = DropNulls(false)): JsonText =
    val j = a.asJson
    JsonText((if dropNulls.value then j.dropNullValues else j).noSpaces)
