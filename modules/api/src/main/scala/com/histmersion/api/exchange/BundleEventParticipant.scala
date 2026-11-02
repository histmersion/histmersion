package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleEventParticipant(actor: String, role: String, citekey: Option[String], sourceUid: Option[UUID])
    derives KindlingsCodecAsObject
