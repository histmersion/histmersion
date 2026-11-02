package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleGoalAlias(alias: String, goalUid: UUID, createdAt: String) derives KindlingsCodecAsObject
