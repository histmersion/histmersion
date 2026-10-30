package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** A parent of a place (`place_relations`, this place the child), by the parent's gazetteer id. */
final case class BundlePlaceRelation(parentId: String, kind: String, period: Option[BundleRange])
    derives KindlingsCodecAsObject
