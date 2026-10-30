package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEventParticipant(actor: String, role: String, citekey: Option[String], sourceUid: Option[UUID])
    derives KindlingsCodecAsObject
