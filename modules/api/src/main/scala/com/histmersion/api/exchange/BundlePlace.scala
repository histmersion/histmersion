package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID
import com.histmersion.api.given

final case class BundlePlace(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    geom: Option[String],
    period: Option[BundleRange],
    source: Option[String],
    properties: Option[Json],
    names: List[BundlePlaceName],
    relations: List[BundlePlaceRelation],
    links: List[BundlePlaceLink],
    types: List[BundlePlaceType],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
