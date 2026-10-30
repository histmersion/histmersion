package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** A named period added here (`periods.dataset = 'local'`). */
final case class BundlePeriod(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    authority: Option[String],
    period: Option[BundleRange],
    region: Option[String],
    parentId: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
