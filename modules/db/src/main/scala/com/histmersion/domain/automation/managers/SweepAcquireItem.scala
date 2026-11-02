package com.histmersion.domain.automation.managers

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

/** An open acquisition item a watcher's sweep created, found by structure: the sweep run that picked the DOI, the
  * watcher, the goal it linked the item to, and the pick as the ranking saw it (`isOa` = it marked the work open
  * access).
  */
final case class SweepAcquireItem(
    item: Int,
    text: String,
    topic: Option[String],
    goal: String,
    managerId: String,
    job: String,
    doi: String,
    isOa: Boolean,
    title: Option[String],
    year: Option[Int],
    authors: List[String],
    venue: Option[String],
    why: Option[String]
) derives DbCodec
