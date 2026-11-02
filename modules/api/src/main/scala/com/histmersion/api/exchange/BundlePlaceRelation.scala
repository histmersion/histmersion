package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundlePlaceRelation(parentId: String, kind: String, period: Option[BundleRange])
    derives KindlingsCodecAsObject
