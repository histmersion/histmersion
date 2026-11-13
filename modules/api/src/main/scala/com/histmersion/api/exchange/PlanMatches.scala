package com.histmersion
package api.exchange
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.api.RegisteredPlanMatches
import sttp.tapir.Schema

/** How each of the bundle's records of the kind that matches records across collections was matched, as a merge plan
  * lists it: kept as the kind's JSON ([[PlanMatchKind]]), which the kind writes and reads.
  */
final case class PlanMatches(rows: List[Json]):
  def isEmpty: Boolean = (rows.isEmpty)

object PlanMatches:

  val empty: PlanMatches = PlanMatches(Nil)

  given JsonValueCodec[PlanMatches] = JsonCodec.mapped[PlanMatches, List[Json]](_.rows, rows => Right(PlanMatches(rows)))
  given Schema[PlanMatches] = RegisteredPlanMatches.kind.schema
