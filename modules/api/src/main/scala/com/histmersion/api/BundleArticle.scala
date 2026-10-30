package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleArticle(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    period: Option[BundleRange],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
