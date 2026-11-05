package com.histmersion.domain.automation.worklists

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.Plural
import com.histmersion.api.automation.Queue
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.MergeFollowUps

import com.augustnagro.magnum.DbCon
import java.util.UUID

/** What a merge leaves on the work lists: a `/review-article` task per merged article (a merged text is not reviewed),
  * and a worklog line.
  */
object WorkListMergeFollowUps extends MergeFollowUps:
  type RecordInResultPart1 = RecordInResultPart1.T
  object RecordInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInResultPart2 = RecordInResultPart2.T
  object RecordInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInFrom = RecordInFrom.T
  object RecordInFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordInDump = RecordInDump.T
  object RecordInDump extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def recordIn(
      m: com.histmersion.api.exchange.Bundle.Manifest,
      from: com.histmersion.domain.automation.worklists.WorkListMergeFollowUps.RecordInFrom,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: com.histmersion.domain.automation.worklists.WorkListMergeFollowUps.RecordInDump
  )(using DbCon): (List[com.histmersion.domain.automation.worklists.WorkListMergeFollowUps.RecordInResultPart1], com.histmersion.domain.automation.worklists.WorkListMergeFollowUps.RecordInResultPart2) =
    val short = mergeId.toString.take(8)
    val reviews = ap.reviews.map { slug =>
      QueueRepo.insert(
        Queue.Kind.Tasks,
        Queue.QueueAdd(
          s"/review-article $slug --fix — merged with $from's (merge $short)",
          topic = Some("merge"),
          reason = Some(s"`merge apply` changed the article; a merged text is not reviewed")
        )
      )
      slug
    }
    val t = ap.tally
    val log = QueueRepo.insert(
      Queue.Kind.Log,
      Queue.QueueAdd(
        s"Merged the bundle of $from (${m.bundleId.toString.take(8)}…) as merge $short: " +
          s"${Plural.count(t.sourcesMerged, "source")} merged, ${counts.find(_.kind == Bundle.Kind.Source).fold(0)(_.inserted)} new, " +
          s"${Plural.count(reviews.size, "article")} to review; backup $dump",
        topic = Some("merge")
      )
    )
    (reviews, log.id)
