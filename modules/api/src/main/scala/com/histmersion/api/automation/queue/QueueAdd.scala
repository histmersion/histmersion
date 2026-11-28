package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Queue.Rationale

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueueAdd(
    text: QueueItem.Text,
    topic: Option[QueueItem.Topic] = None,
    status: Option[QueueItem.Status] = None,
    reason: Option[Rationale] = None,
    createdAt: Option[QueueAdd.CreatedAt] = None,
    goal: Option[Goal.Slug] = None,
    job: Option[SpatialHierarchyResult.Job.Id] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) derives StrictJson,
      KindlingsSchema,
      api.json.Schema

object QueueAdd:

  export QueueItem.CreatedAt
