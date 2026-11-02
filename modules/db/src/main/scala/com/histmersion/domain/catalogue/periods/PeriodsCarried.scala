package com.histmersion.domain.catalogue.periods

import com.histmersion.api.exchange.BundlePeriod
import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.data.exchange.Bundle

/** What a bundle carries of the named periods: this collection's own, after the places and before the events. */
object PeriodsCarried:

  val periods: CarriedKind = CarriedKind.records[BundlePeriod](
    DataOwner("catalogue"),
    Bundle.Kind.Period,
    TableName("periods"),
    List(RowsIn(ImportStage.Rows, ImportRank(32), PeriodBundleRepo.periodsIn))
  )(scope => PeriodBundleRepo.periods(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.periods).setTo(ls))

  /** Every kind of the periods. */
  val all: List[CarriedKind] = List(periods)
