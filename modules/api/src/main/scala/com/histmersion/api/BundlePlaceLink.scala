package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundlePlaceLink(scheme: String, externalId: String, relation: String) derives KindlingsCodecAsObject
