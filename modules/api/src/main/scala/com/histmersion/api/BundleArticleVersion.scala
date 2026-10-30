package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** One recorded previous version of an article; the body is a blob. */
final case class BundleArticleVersion(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha256: String,
    summary: Option[String],
    /** The `updated_at` of the version that was replaced. */
    versionOf: String,
    /** When the version was recorded; it never changes, so it is also the line's `updatedAt`. */
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
