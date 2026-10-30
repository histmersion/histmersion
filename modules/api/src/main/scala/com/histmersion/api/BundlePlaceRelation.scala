package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundlePlaceRelation(parentId: String, kind: String, period: Option[BundleRange])
    derives KindlingsCodecAsObject
