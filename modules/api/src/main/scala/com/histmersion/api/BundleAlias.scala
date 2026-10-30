package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An old citekey (`source_aliases`) and the source it names now. */
final case class BundleAlias(alias: String, sourceUid: UUID, createdAt: String) derives KindlingsCodecAsObject
