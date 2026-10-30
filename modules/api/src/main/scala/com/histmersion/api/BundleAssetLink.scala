package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleAssetLink(
    uid: UUID,
    origin: UUID,
    assetUid: UUID,
    relation: String,
    targetType: String,
    targetId: String,
    targetUid: Option[UUID],
    xywh: Option[String],
    notes: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
