package com.histmersion
package api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.automation.Queue
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Acquire

/** A work list as `GET /api/queue/{list}` shows it: its items as the automation keeps them, the library's last legal
  * check of each ACQUIRE item, and the goals each item belongs to or is linked from.
  */
final case class QueueListing(
    list: Queue.Kind.Label,
    items: List[Queue.QueueItem],
    checks: List[Acquire.Checks.AcquireCheck] = Nil,
    goals: List[Queue.ItemGoals] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The list in Markdown, each ACQUIRE item with its last check's reason; `None` for a list no kind names. */
  def markdown: Option[Queue.MarkdownResult] =
    Queue.Kind.parse(list).map(k => Queue.markdown(k, items, checks.map(c => c.item -> c.reason).toMap))
