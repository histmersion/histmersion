package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/articles/{slug}/coverage`: the article's filter against the live corpus.
  *
  * @param total
  *   how many sources the filter returns
  * @param checked
  *   how many of them were read (`total` unless it passed `limits.coverage-sources`; the first ones in citekey order
  *   are read)
  * @param cited
  *   the filter's sources the body cites
  * @param notUsed
  *   the filter's (read) sources the body does not cite, each with its reason
  * @param citedOutside
  *   sources the body cites that the filter does not return
  * @param statuses
  *   the status breakdown of the read sources, in taxonomy order
  * @param edges
  *   typed disagreement edges touching a cited source
  * @param searches
  *   the writer's further searches
  * @param reasons
  *   every stored reason (also those that drifted)
  */
final case class Coverage(
    slug: String,
    filter: ArticleFilter,
    filterLine: String,
    total: Int,
    checked: Int,
    cited: List[CoverageSource],
    notUsed: List[CoverageEntry],
    citedOutside: List[String],
    statuses: List[StatusCount],
    edges: List[CoverageEdge],
    searches: List[CoverageSearch],
    reasons: List[CoverageReason],
    drift: List[CoverageDrift],
    /** Catalogue events in the filter's period and region the body never names (#1449) — leads for the writer, never
      * prose.
      */
    eventLeads: List[ArticleEventLead] = Nil,
    /** The critique checklist (§6.8 of docs/research-review-convergence.md): every disagreement edge touching a cited
      * source, addressed, reasoned or open.
      */
    critiques: List[CritiquePair] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def capped: Boolean = checked < total
  def unplaced: List[String] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:
  given Schema[Coverage] = summon[KindlingsSchema[Coverage]].schema
