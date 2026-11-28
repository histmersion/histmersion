package com.histmersion
package api.library.repairacquire

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Acquire

final case class RepairRow(
    item: RepairRow.Item,
    doi: Doi,
    goals: List[RepairRow.Goals] = Nil,
    watcher: RepairRow.Watcher = RepairRow.Watcher(""),
    action: RepairRow.Action = RepairRow.Action(""),
    detail: RepairRow.Detail = RepairRow.Detail(""),
    backlogItem: Option[RepairRow.BacklogItem] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RepairRow:
  export Acquire.ItemNumber as Item
  type Goals = Goals.T
  object Goals extends Newtype[String, Pure]
  type Watcher = Watcher.T
  object Watcher extends Newtype[String, Pure]
  type Action = Action.T
  object Action extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
  type BacklogItem = BacklogItem.T
  object BacklogItem extends Newtype[Long, Pure]
