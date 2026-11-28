package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.GoalRef

final case class ItemGoals(item: QueueItem.Id, goals: List[GoalRef]) derives JsonCodec, KindlingsSchema, api.json.Schema
