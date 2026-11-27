package com.histmersion
package domain.automation.worklists

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{tolerated, LintRule}

/** What a bundle carries of the work lists: their items, written after every goal (rank 50). */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object QueueCarried:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val items: CarriedKind = CarriedKind.records[Bundle.QueueItem](
    DataOwner("automation"),
    Bundle.Kind.QueueItem,
    TableName("queue_items"),
    List(RowsIn(ImportStage.Rows, ImportRank(50), QueueBundleRepo.queueItemsIn))
  )(scope => QueueBundleRepo.queue(scope).toList)(
    q => CarriedKind.RecordsClock(q.updatedAt.value),
    (es, ls) => es.modify(_.queue).setTo(ls)
  )
