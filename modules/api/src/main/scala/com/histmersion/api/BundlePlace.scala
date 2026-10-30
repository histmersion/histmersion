package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID

/** A place added here (`places.dataset = 'local'`), with the sets it owns. Events point at places by `id`, so a place
  * matched to another one here under a different id has its events re-pointed.
  */
final case class BundlePlace(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    /** Hex EWKB, as events' and assets' geometries. */
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
