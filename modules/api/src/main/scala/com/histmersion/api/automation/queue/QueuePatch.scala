package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Queue.Rationale

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueuePatch(
    status: Option[QueueItem.Status] = None,
    text: Option[QueueItem.Text] = None,
    topic: Option[QueueItem.Topic] = None,
    reason: Option[Rationale] = None,
    goal: Option[Goal.Slug] = None,
    closedBy: Option[QueuePatch.ClosedBy] = None,
    actor: Option[Creation.Actor] = None
) derives StrictJson,
      KindlingsSchema,
      api.json.Schema

object QueuePatch:

  export QueueItem.ClosedBy
