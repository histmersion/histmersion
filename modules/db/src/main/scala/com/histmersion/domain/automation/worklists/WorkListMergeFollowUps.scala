package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.Plural
import com.histmersion.api.automation.Queue
import com.histmersion.domain.{CreatedAt, Creator, MergePlanner}
import com.histmersion.util.data.exchange.MergeFollowUps

import com.augustnagro.magnum.DbCon
import java.util.UUID
import com.histmersion.lint.{tolerated, LintRule}

/** What a merge leaves on the work lists: a `/review-article` task per merged article (a merged text is not reviewed),
  * and a worklog line.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object WorkListMergeFollowUps extends MergeFollowUps:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def recordIn(
      m: Bundle.Manifest,
      from: MergeFollowUps.FromRecordRef,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[Bundle.KindCounts],
      dump: MergeFollowUps.DumpRecordRef
  )(using DbCon, CreatedAt): (List[Bundle.Article.Slug], QueueItem.Id) =
    val at = summon[CreatedAt]
    val short = mergeId.toString.take(8)
    val reviews = ap.reviews.map { slug =>
      QueueRepo.insert(
        api.automation.queue.Docket.Tasks,
        QueueAdd(
          QueueItem.Text(s"/review-article ${slug.value} --fix — merged with ${from.value}'s (merge $short)"),
          topic = Some(QueueItem.Topic("merge")),
          reason = Some(Queue.Rationale(s"`merge apply` changed the article; a merged text is not reviewed"))
        ),
        ByMerge,
        at
      )
      slug
    }
    val t = ap.tally
    val log = QueueRepo.insert(
      api.automation.queue.Docket.Log,
      QueueAdd(
        QueueItem.Text(
          s"Merged the bundle of ${from.value} (${m.bundleId.toString.take(8)}…) as merge $short: " +
            s"${Plural.count(Plural.CountN(t.sourcesMerged.value.toLong), Plural.CountSingular("source"))} merged, ${counts.find(_.kind == Bundle.Kind.Source).fold(0)(_.inserted.value)} new, " +
            s"${Plural.count(Plural.CountN(reviews.size.toLong), Plural.CountSingular("article"))} to review; backup ${dump.value}"
        ),
        topic = Some(QueueItem.Topic("merge"))
      ),
      ByMerge,
      at
    )
    (reviews, log.id)

  private val ByMerge: Creator.New = Creator.New.Manual(Creator.Surface.Internal)
