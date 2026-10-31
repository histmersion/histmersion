package com.histmersion.util.data.exchange

import com.histmersion.util.data.TableName

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

object MergeRuleSet:
  /** Today's rules as the transition adapters register them, for the callers that are not given the registry. */
  lazy val transition: MergeRuleSet = MergeRuleSet(MergeRulesAdapter.all)
