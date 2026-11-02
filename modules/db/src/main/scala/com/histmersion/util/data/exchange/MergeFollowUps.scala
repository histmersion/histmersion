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
      from: String,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: String
  )(using DbCon): (List[String], Int)
