package com.histmersion
package domain.catalogue.places

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.api.library.Citekey
import com.histmersion.domain.catalogue.Place
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.DbCon
import com.histmersion.lint.{LintRule, tolerated}

/** The places' share of the catalogue's data contribution: their count, and their checks with the repairs safe to make
  * without a person — a kind the record contradicts, a point at a grid square's centre, an imported area known only by
  * a point.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object PlacesData:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(using DbCon): List[DataCount] = List(
    DataCount(DataCount.Noun("places"), DataCount.Count(PlaceRepo.count().value.toLong))
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checks(owner: DataOwner): List[CheckRule] = List(Kind(owner), GridPoint(owner), PointArea(owner))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ruleOf(name: String): Rule =
    Lint.rule(Rule.Name(name)).getOrElse(Rule(Rule.Name(name), Rule.Description(name)))

  /** `place-imported-point-area`'s knobs (`places.sources.point-area-*`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pointAreaOf(cfg: AppConfig): PointAreaRepo.Settings =
    val src = cfg.places.sources
    PointAreaRepo.Settings(
      src.pointAreaKindSet.toList.map(_.value).sorted.map(PointAreaRepo.Settings.Kinds(_)),
      PointAreaRepo.Settings.Batch(src.pointAreaFixBatch.value),
      PointAreaRepo.Settings.BudgetMs(src.pointAreaFixSeconds.value.toLong * 1000L),
      PointAreaRepo.Settings.ToleranceKm(cfg.places.pointToleranceKm.value)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def geometry(c: CheckContext): PlaceGeometry = PlaceGeometry(Transactor(c.dataSource))(using c.timer)

  /** `place-kind`: what the record says a place is, where its source filed it otherwise. */
  final private class Kind(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceKindRule)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(
        rule,
        PlaceReviewRepo.proposalsIn().map(p => LintItem(Citekey(p.id.value), LintItem.Detail(p.detail.value))),
        Lint.ItemLimit(maxItems.value)
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(50),
        c =>
          val (reclassified, merged) =
            geometry(c).reclassify(GeocodeSettings.ToleranceKm(c.config.places.pointToleranceKm.value))
          LintFixResult(
            List(
              LintFixResult.Applied(
                s"$PlaceKindRule: $reclassified place(s) reclassified, $merged merged into their replacement"
              )
            ),
            LintFixResult.EdgesResolved(0),
            LintFixResult.SourcesReindexed(0),
            LintFixResult.ChunksWritten(0),
            LintFixResult.Remaining(0),
            placesReclassified = LintFixResult.PlacesReclassified(reclassified.value),
            placesMerged = LintFixResult.PlacesMerged(merged.value)
          )
      )
    )

  /** `place-grid-point`: a place at the centre of an atlas grid square whose linked record gives its point. */
  final private class GridPoint(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceGridPoint)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(
        rule,
        PlaceReviewRepo.relocationsIn().map(r => LintItem(Citekey(r.id.value), LintItem.Detail(r.detail.value))),
        Lint.ItemLimit(maxItems.value)
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(60),
        c =>
          val relocated = geometry(c).relocate(GeocodeSettings.ToleranceKm(c.config.places.pointToleranceKm.value))
          LintFixResult(
            List(
              LintFixResult.Applied(
                s"$PlaceGridPoint: $relocated place(s) moved from a grid square's centre to their linked record's point"
              )
            ),
            LintFixResult.EdgesResolved(0),
            LintFixResult.SourcesReindexed(0),
            LintFixResult.ChunksWritten(0),
            LintFixResult.Remaining(0),
            placesRelocated = LintFixResult.PlacesRelocated(relocated.value)
          )
      )
    )

  /** `place-imported-point-area`: an imported region, polity or people stored with only a point. */
  final private class PointArea(val owner: DataOwner) extends CheckRule:
    val rule: Rule = ruleOf(PlaceImportedPointArea)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      PointAreaRepo.reportIn(
        rule,
        pointAreaOf(context.config).kinds.map(k => Place.Kind.Wire(k.value)),
        PointAreas.ReportMaxItems(maxItems.value)
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override val fix: Option[SafeFix] = Some(
      SafeFix(
        FixWhen.WhenNamed,
        FixRank(70),
        c =>
          val r = geometry(c).removePointAreas(pointAreaOf(c.config))
          LintFixResult(
            List(LintFixResult.Applied(r.line.value)),
            LintFixResult.EdgesResolved(0),
            LintFixResult.SourcesReindexed(0),
            LintFixResult.ChunksWritten(0),
            LintFixResult.Remaining(0),
            pointAreasRemoved = LintFixResult.PointAreasRemoved(r.removed.value),
            pointAreasRemaining = LintFixResult.PointAreasRemaining(r.remaining.value)
          )
      )
    )
