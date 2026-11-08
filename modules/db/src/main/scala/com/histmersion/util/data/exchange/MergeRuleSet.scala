package com.histmersion
package util.data.exchange

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.TableName

import com.augustnagro.magnum.DbCon

/** The merge rules a plan and the ledger run over, from the registry. */
final class MergeRuleSet(val all: List[MergeRules]):

  /** The ledger's label of a row: `CASE l.kind WHEN … END` over every kind that names its records. */
  def ledgerLabelSql: String =
    all
      .flatMap(r => r.ledgerLabel.map(sql => s"WHEN '${r.kind.wire}' THEN ($sql)"))
      .mkString("CASE l.kind ", "\n                       ", " END")

  /** The tables of the kinds lines can refer to, in `carried`'s tables. */
  def referableTables(carried: CarriedKinds): List[TableName] =
    all.filter(_.referable).map(r => carried.tableOf(r.kind)).distinct

  /** The planners' rules the kinds register, each once, in registration order. */
  def planning: List[PlanRules] = all.flatMap(_.planning).distinct

  /** What the plan knows of this collection, every kind's share added in turn. */
  def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    @annotation.tailrec
    def loop(rest: List[MergeRules], acc: MergePlanner.Input): MergePlanner.Input = rest match
      case r :: tail => loop(tail, r.gather(bundle, localized, acc, settings))
      case Nil       => acc
    loop(all, in)

