package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}
import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*

import java.time.Instant

/** One row of the merged list, in the order of [[ActivityRepo.Merged]]'s columns. */
final private[activity] case class ActivityRow(
    key: String,
    kind: String,
    at: String,
    state: String,
    what: String,
    text: String,
    error: Option[String],
    endedAt: Option[String],
    goal: Option[String],
    job: Option[String],
    by: Option[String],
    citekey: Option[String],
    article: Option[String],
    queueItem: Option[Int],
    queueList: Option[String],
    approval: Option[Long],
    target: Option[String],
    targetKind: Option[String]
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
