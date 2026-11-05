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
      from: com.histmersion.util.data.exchange.MergeFollowUps.RecordInFrom,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: com.histmersion.util.data.exchange.MergeFollowUps.RecordInDump
  )(using DbCon): (List[com.histmersion.util.data.exchange.MergeFollowUps.RecordInResultPart1], com.histmersion.util.data.exchange.MergeFollowUps.RecordInResultPart2)

object MergeFollowUps:
  type RecordInResultPart1 = RecordInResultPart1.T
  object RecordInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInResultPart2 = RecordInResultPart2.T
  object RecordInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInFrom = RecordInFrom.T
  object RecordInFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInDump = RecordInDump.T
  object RecordInDump extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
