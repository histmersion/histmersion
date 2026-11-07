package com.histmersion.util.data.exchange

import com.histmersion.domain.MergePlanner

import com.augustnagro.magnum.DbCon
import java.util.UUID

/** What a merge leaves to do, written in the merge's own transaction by the owner of the work lists: a review of each
  * article the merge changed, and a line in the worklog.
  */
trait MergeFollowUps:
  /** The follow-ups of the merge `mergeId` of `from`'s bundle `m`, after `ap` with `counts`, backed up as `dump`: the
    * articles to review, and the worklog line's id.
    */
  def recordIn(
      m: com.histmersion.api.exchange.Bundle.Manifest,
      from: com.histmersion.util.data.exchange.MergeFollowUps.FromRecordRef,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: com.histmersion.util.data.exchange.MergeFollowUps.DumpRecordRef
  )(using DbCon): (List[com.histmersion.api.exchange.Bundle.Article.Slug], com.histmersion.api.automation.Queue.QueueItem.Id)

object MergeFollowUps:
  type DumpRecordRef = DumpRecordRef.T
  object DumpRecordRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromRecordRef = FromRecordRef.T
  object FromRecordRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
