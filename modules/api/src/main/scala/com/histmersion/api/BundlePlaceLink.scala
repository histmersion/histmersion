package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** An external identifier of a place (`place_links`: Pleiades, Wikidata…). */
final case class BundlePlaceLink(scheme: String, externalId: String, relation: String) derives KindlingsCodecAsObject
