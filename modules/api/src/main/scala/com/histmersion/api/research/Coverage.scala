package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StatusCount
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.given

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
    eventLeads: List[ArticleEventLead] = Nil,
    critiques: List[CritiquePair] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def capped: Boolean = checked < total
  def unplaced: List[String] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:
  given Schema[Coverage] = summon[KindlingsSchema[Coverage]].schema
