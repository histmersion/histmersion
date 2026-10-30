package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** What a goal is linked to (`goal_links`, V046). `ref` is the label (a citekey, a slug, the query text); a queue
  * item's serial id is local, so its `ref` is the item's uid. `refUid` is the uid of a linked source, article or queue
  * item, so the link follows a rename or a merge.
  */
final case class BundleGoalLink(
    kind: String,
    ref: String,
    refUid: Option[UUID],
    note: Option[String],
    createdAt: String
) derives KindlingsCodecAsObject
