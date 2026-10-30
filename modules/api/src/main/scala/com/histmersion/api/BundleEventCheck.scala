package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** When an event's candidates were last computed (`event_link_checks`, V085). */
final case class BundleEventCheck(eventUid: UUID, checkedAt: String) derives KindlingsCodecAsObject
