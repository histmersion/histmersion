package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** A person's correction of one field of something an importer wrote (`corrections`, V088). The JSON values travel as
  * Postgres's text of them, so a JSON `null` (a cleared field) and an absent refresh stay apart. `targetUid` is the
  * event's uid, so the correction follows a rename; a place is named by its gazetteer id. An import keeps the newer
  * (`updatedAt`).
  */
final case class BundleCorrection(
    targetKind: String,
    targetId: String,
    targetUid: Option[UUID],
    field: String,
    value: String,
    upstream: Option[String],
    upstreamValue: String,
    upstreamNow: Option[String],
    status: String,
    createdAt: String,
    updatedAt: String,
    checkedAt: Option[String]
) derives KindlingsCodecAsObject
