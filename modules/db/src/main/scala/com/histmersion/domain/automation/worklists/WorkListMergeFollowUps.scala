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
  def recordIn(
      m: com.histmersion.api.exchange.Bundle.Manifest,
      from: String,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[com.histmersion.api.exchange.Bundle.KindCounts],
      dump: String
  )(using DbCon): (List[String], Int) =
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
