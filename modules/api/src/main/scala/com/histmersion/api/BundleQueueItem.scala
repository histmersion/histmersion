package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** A queue row. Its serial `id` is a local label and is not carried. */
final case class BundleQueueItem(
    uid: UUID,
    origin: UUID,
    list: String,
    text: String,
    topic: Option[String],
    status: String,
    reason: Option[String],
    createdAt: String,
    doneAt: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
