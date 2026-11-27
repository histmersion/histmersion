package com.histmersion
package util.data.exchange

import com.histmersion.domain.{CreatedAt, MergePlanner}

import com.augustnagro.magnum.DbCon
import java.util.UUID
import com.histmersion.api.automation.Queue
import io.github.iltotore.iron.constraint.any.Pure

/** What a merge leaves to do, written in the merge's own transaction by the owner of the work lists: a review of each
  * article the merge changed, and a line in the worklog.
  */
trait MergeFollowUps:
  /** The follow-ups of the merge `mergeId` of `from`'s bundle `m`, after `ap` with `counts`, backed up as `dump`: the
    * articles to review, and the worklog line's id.
    */
  def recordIn(
      m: com.histmersion.api.exchange.Bundle.Manifest,
      from: MergeFollowUps.FromRecordRef,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: MergeFollowUps.DumpRecordRef
  )(using DbCon, CreatedAt): (List[com.histmersion.api.exchange.Bundle.Article.Slug], Queue.QueueItem.Id)

object MergeFollowUps:
  type DumpRecordRef = DumpRecordRef.T
  object DumpRecordRef extends Newtype[String, Pure]
  type FromRecordRef = FromRecordRef.T
  object FromRecordRef extends Newtype[String, Pure]
