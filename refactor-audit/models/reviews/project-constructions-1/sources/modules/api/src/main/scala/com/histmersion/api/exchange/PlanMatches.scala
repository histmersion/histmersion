package com.histmersion.api.exchange
import com.histmersion.api.RegisteredPlanMatches
import io.circe.{Decoder, Encoder, Json}
import sttp.tapir.Schema

/** How each of the bundle's records of the kind that matches records across collections was matched, as a merge plan
  * lists it: kept as the kind's JSON ([[PlanMatchKind]]), which the kind writes and reads.
  */
final case class PlanMatches(rows: List[Json]):
  def isEmpty: com.histmersion.api.exchange.PlanMatches.IsEmptyResult = _root_.com.histmersion.api.exchange.PlanMatches.IsEmptyResult(rows.isEmpty)

object PlanMatches:
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: PlanMatches = PlanMatches(Nil)

  given Encoder[PlanMatches] = Encoder.encodeList[Json].contramap(_.rows)
  given Decoder[PlanMatches] = Decoder.decodeList[Json].map(PlanMatches(_))
  given Schema[PlanMatches] = RegisteredPlanMatches.kind.schema
