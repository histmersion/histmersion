package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleGoalAlias(alias: String, goalUid: UUID, createdAt: String) derives KindlingsCodecAsObject
