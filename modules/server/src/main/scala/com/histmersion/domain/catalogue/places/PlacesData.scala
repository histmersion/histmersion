package com.histmersion.domain.catalogue.places

import com.histmersion.api.Lint
import com.histmersion.api.Lint.*
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.DbCon

/** The places' share of the catalogue's data contribution: their count, and their checks with the repairs safe to make
  * without a person — a kind the record contradicts, a point at a grid square's centre, an imported area known only by
  * a point.
  */
object PlacesData:

  def figures(using DbCon): List[DataCount] = List(DataCount("places", PlaceRepo.count().toLong))

  def checks(owner: DataOwner): List[CheckRule] = List(Kind(owner), GridPoint(owner), PointArea(owner))

  private def ruleOf(name: String): Rule = Lint.rule(name).getOrElse(Rule(name, name))

  /** `place-imported-point-area`'s knobs (`places.sources.point-area-*`). */
  def pointAreaOf(cfg: AppConfig): PointAreaRepo.Settings =
    val src = cfg.places.sources
    PointAreaRepo.Settings(
      src.pointAreaKindSet.toList.sorted,
      src.pointAreaFixBatch,
      src.pointAreaFixSeconds.toLong * 1000L,
      cfg.places.pointToleranceKm
    )

  private def geometry(c: CheckContext): PlaceGeometry = PlaceGeometry(Transactor(c.dataSource))

  /** `place-kind`: what the record says a place is, where its source filed it otherwise. */
  final private class Kind(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceKindRule)
    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, PlaceReviewRepo.proposalsIn().map(p => LintItem(p.id, p.detail)), maxItems)
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(50),
        c =>
          val (reclassified, merged) = geometry(c).reclassify(c.config.places.pointToleranceKm)
          LintFixResult(
            List(s"$PlaceKindRule: $reclassified place(s) reclassified, $merged merged into their replacement"),
            0,
            0,
            0,
            0,
            placesReclassified = reclassified,
            placesMerged = merged
          )
      )
    )

  /** `place-grid-point`: a place at the centre of an atlas grid square whose linked record gives its point. */
  final private class GridPoint(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceGridPoint)
    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, PlaceReviewRepo.relocationsIn().map(r => LintItem(r.id, r.detail)), maxItems)
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(60),
        c =>
          val relocated = geometry(c).relocate(c.config.places.pointToleranceKm)
          LintFixResult(
            List(
              s"$PlaceGridPoint: $relocated place(s) moved from a grid square's centre to their linked record's point"
            ),
            0,
            0,
            0,
            0,
            placesRelocated = relocated
          )
      )
    )

  /** `place-imported-point-area`: an imported region, polity or people stored with only a point. */
  final private class PointArea(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceImportedPointArea)
    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      PointAreaRepo.reportIn(rule, pointAreaOf(context.config).kinds, maxItems)
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(70),
        c =>
          val r = geometry(c).removePointAreas(pointAreaOf(c.config))
          LintFixResult(List(r.line), 0, 0, 0, 0, pointAreasRemoved = r.removed, pointAreasRemaining = r.remaining)
      )
    )
