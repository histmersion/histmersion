package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEventCheck(eventUid: UUID, checkedAt: String) derives KindlingsCodecAsObject
