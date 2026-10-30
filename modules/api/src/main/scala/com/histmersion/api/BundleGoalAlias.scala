package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An old slug of a goal (`goal_aliases`, V053) and the goal it names now. */
final case class BundleGoalAlias(alias: String, goalUid: UUID, createdAt: String) derives KindlingsCodecAsObject
