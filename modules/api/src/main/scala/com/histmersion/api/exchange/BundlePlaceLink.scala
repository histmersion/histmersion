package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundlePlaceLink(scheme: String, externalId: String, relation: String) derives KindlingsCodecAsObject
