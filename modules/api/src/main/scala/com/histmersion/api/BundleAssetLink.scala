package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleAssetLink(
    uid: UUID,
    origin: UUID,
    assetUid: UUID,
    relation: String,
    targetType: String,
    /** The target's label (a finding id, a citekey, a slug, an event or place id). */
    targetId: String,
    /** The target's uid when it is an entity that has one. */
    targetUid: Option[UUID],
    xywh: Option[String],
    notes: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
