package com.histmersion
package api.exchange
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure

/** The kind of record a merge plan's matches are of, as its owner declares it: how one is written and read, which ones
  * the plan's Markdown lists, and how a line of it reads.
  */
final class PlanMatchKind[A](listed: A => Boolean, line: A => PlanMatchKind.MatchLine)(using
    encoder: Encoder[A],
    decoder: Decoder[A],
    schemaOf: Schema[List[A]]
):
  def schema: Schema[PlanMatches] = schemaOf.as[PlanMatches]

  /** The matches, as the plan keeps them. */
  def matches(as: List[A]): PlanMatches = PlanMatches(as.map(_.asJson))

  /** Every match, as the kind reads it. */
  def all(m: PlanMatches): List[A] = m.rows.flatMap(decoder.decodeJson(_).toOption)

  /** The matches the plan's Markdown lists (at most `limit`), one line each. */
  def lines(m: PlanMatches, limit: PlanMatchKind.LineLimit): List[PlanMatchKind.MatchLine] =
    m.rows
      .flatMap(decoder.decodeJson(_).toOption)
      .filter(listed)
      .take((limit).value)
      .map {
        val refactorFunction3 = line;
        (refactorValue3) => PlanMatchKind.MatchLine((refactorFunction3(refactorValue3)).value)
      }

object PlanMatchKind:
  type LineLimit = LineLimit.T
  object LineLimit extends Newtype[Int, Pure]
  type MatchLine = MatchLine.T
  object MatchLine extends Newtype[String, Pure]

