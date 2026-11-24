package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{LintRule, tolerated}

/** What a bundle carries of the named periods: this collection's own, after the places and before the events. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object PeriodsCarried:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val periods: CarriedKind = CarriedKind.records[Bundle.Period](
    DataOwner("catalogue"),
    Bundle.Kind.Period,
    TableName("periods"),
    List(RowsIn(ImportStage.Rows, ImportRank(32), PeriodBundleRepo.periodsIn))
  )(scope => PeriodBundleRepo.periods(scope).toList)(
    p => CarriedKind.RecordsClock(p.updatedAt.value),
    (es, ls) => es.modify(_.periods).setTo(ls)
  )

  /** Every kind of the periods. */
  val all: List[CarriedKind] = List(periods)
