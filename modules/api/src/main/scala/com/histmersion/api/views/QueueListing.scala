package com.histmersion
package api.views

import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.ItemGoals
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.automation.Queue
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Acquire
import com.histmersion.lint.{tolerated, LintRule}

/** A work list as `GET /api/queue/{list}` shows it: its items as the automation keeps them, the library's last legal
  * check of each ACQUIRE item, and the goals each item belongs to or is linked from.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueueListing(
    list: Kind.Label,
    items: List[QueueItem],
    checks: List[Acquire.Checks.AcquireCheck] = Nil,
    goals: List[ItemGoals] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:

  /** The list in Markdown, each ACQUIRE item with its last check's reason; `None` for a list no kind names. */
  def markdown: Option[Queue.Markdown] =
    Kind.parse(list).map(k => k.markdown(items, checks.map(c => c.item -> c.reason).toMap))
