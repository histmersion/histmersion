package com.histmersion
package domain.activity

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}
import com.histmersion.util.database.{Param, Sql}

import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*

import java.time.Instant
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Citekey

/** One row of the merged list, in the order of [[ActivityRepo.Merged]]'s columns. */
final private[activity] case class ActivityRow(
    key: Entry.Key,
    kind: Entry.Kind,
    at: Entry.At,
    state: Entry.State,
    what: Entry.What,
    text: Entry.Text,
    error: Option[Entry.Error],
    endedAt: Option[Entry.EndedAt],
    goal: Option[Goal.Slug],
    job: Option[Entry.Job],
    by: Option[Entry.By],
    citekey: Option[Citekey],
    article: Option[Entry.Article],
    queueItem: Option[Entry.QueueItem],
    queueList: Option[Entry.QueueList],
    approval: Option[Entry.Approval],
    target: Option[Entry.Target],
    targetKind: Option[Entry.TargetKind]
) derives DbCodec:
  def entry: Entry = Entry(
    key,
    kind,
    at,
    state,
    what,
    text,
    error,
    endedAt,
    goal,
    job,
    by,
    citekey,
    article,
    queueItem,
    queueList,
    approval,
    target,
    targetKind
  )
