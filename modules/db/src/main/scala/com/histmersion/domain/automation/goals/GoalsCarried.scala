package com.histmersion.domain.automation.goals

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the goals: the goals (with their links and schedules) and their old slugs.
  *
  * The ranks are the import's order among every owner's steps: every rename before the rows that carry the new label;
  * the goals before their old slugs.
  */
object GoalsCarried:
  type RowsRank = RowsRank.T
  object RowsRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Automation = DataOwner("automation")

  private def rows(rank: com.histmersion.domain.automation.goals.GoalsCarried.RowsRank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  val goals: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Goal](
    Automation,
    Bundle.Kind.Goal,
    TableName("goals"),
    List(rows(42)(GoalBundleRepo.goalRenamesIn), rows(48)(GoalBundleRepo.goalsIn))
  )(scope => GoalBundleRepo.goals(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.goals).setTo(ls))

  val aliases: CarriedKind =
    CarriedKind.owned[com.histmersion.api.exchange.Bundle.GoalAlias](
      Automation,
      BundleEntry("entities/goal-aliases.ndjson"),
      List(rows(49)(GoalBundleRepo.goalAliasesIn))
    )(scope => GoalBundleRepo.goalAliases(scope).toList)(
      a => Some(a.createdAt),
      (es, ls) => es.modify(_.goalAliases).setTo(ls)
    )
