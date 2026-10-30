package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An old id of an event (`event_aliases`, V053) and the event it names now. */
final case class BundleEventAlias(alias: String, eventUid: UUID, createdAt: String) derives KindlingsCodecAsObject
