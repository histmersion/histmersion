package com.histmersion.api.exchange
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import sttp.tapir.Schema

/** The kind of record a merge plan's matches are of, as its owner declares it: how one is written and read, which ones
  * the plan's Markdown lists, and how a line of it reads.
  */
final class PlanMatchKind[A](listed: A => PlanMatchKind.ListedResult, line: A => PlanMatchKind.LineResult)(using
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
  def lines(m: PlanMatches, limit: com.histmersion.api.exchange.PlanMatchKind.LinesLimit): List[com.histmersion.api.exchange.PlanMatchKind.LinesResult] =
    m.rows.flatMap(decoder.decodeJson(_).toOption).filter({ val refactorFunction = listed; (refactorValue) => ((refactorFunction(refactorValue)) == _root_.com.histmersion.api.exchange.PlanMatchKind.ListedResult(true)) }).take(limit).map(line)

object PlanMatchKind:
  type ListedResult = ListedResult.T
  object ListedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LinesResult = LinesResult.T
  object LinesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinesLimit = LinesLimit.T
  object LinesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
