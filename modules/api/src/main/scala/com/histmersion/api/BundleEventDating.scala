package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEventDating(
    citekey: String,
    sourceUid: Option[UUID],
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    method: Option[String],
    note: Option[String]
) derives KindlingsCodecAsObject
