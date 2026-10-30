package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

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
