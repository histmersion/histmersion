package com.histmersion
package domain.automation.worklists
import com.histmersion.util.problem.{Legacy, Problem}

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
      m: Bundle.Manifest,
      from: MergeFollowUps.FromRecordRef,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[Bundle.KindCounts],
      dump: MergeFollowUps.DumpRecordRef
  )(using DbCon): (List[Bundle.Article.Slug], Queue.QueueItem.Id) =
    val short = mergeId.toString.take(8)
    val reviews = ap.reviews.map { slug =>
      QueueRepo.insert(
        Queue.Kind.Tasks,
        Queue.QueueAdd(
          Queue.QueueItem.Text(s"/review-article ${slug.value} --fix — merged with ${from.value}'s (merge $short)"),
          topic = Some(Queue.QueueItem.Topic("merge")),
          reason =
            Some(Problem.Unclassified(Legacy.Text(s"`merge apply` changed the article; a merged text is not reviewed")))
        )
      )
      slug
    }
    val t = ap.tally
    val log = QueueRepo.insert(
      Queue.Kind.Log,
      Queue.QueueAdd(
        Queue.QueueItem.Text(
          s"Merged the bundle of ${from.value} (${m.bundleId.toString.take(8)}…) as merge $short: " +
            s"${Plural.count(Plural.CountN(t.sourcesMerged.value.toLong), Plural.CountSingular("source"))} merged, ${counts.find(_.kind == Bundle.Kind.Source).fold(0)(_.inserted.value)} new, " +
            s"${Plural.count(Plural.CountN(reviews.size.toLong), Plural.CountSingular("article"))} to review; backup ${dump.value}"
        ),
        topic = Some(Queue.QueueItem.Topic("merge"))
      )
    )
    (reviews, log.id)
