package com.histmersion.domain.automation.worklists

import com.histmersion.api.exchange.BundleQueueItem
import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.data.exchange.Bundle

/** What a bundle carries of the work lists: their items, written after every goal (rank 50). */
object QueueCarried:
  val items: CarriedKind = CarriedKind.records[BundleQueueItem](
    DataOwner("automation"),
    Bundle.Kind.QueueItem,
    TableName("queue_items"),
    List(RowsIn(ImportStage.Rows, ImportRank(50), QueueBundleRepo.queueItemsIn))
  )(scope => QueueBundleRepo.queue(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.queue).setTo(ls))
