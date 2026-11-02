package com.histmersion.domain.media.assets

import com.histmersion.api.media.AssetKind
import com.histmersion.values.MediaRole

/** A provider's picture to store. `bytes` are the capped rendition, `thumb` the card's thumbnail. */
final case class ProviderAsset(
    provider: String,
    key: String,
    name: String,
    mime: String,
    bytes: Array[Byte],
    width: Option[Int],
    height: Option[Int],
    thumb: Option[Array[Byte]],
    thumbMime: Option[String],
    thumbWidth: Option[Int],
    thumbHeight: Option[Int],
    license: String,
    licenseUrl: Option[String],
    author: Option[String],
    credit: String,
    sourceUrl: String,
    originalUrl: Option[String],
    kind: Option[String]
)

object ProviderAsset:

  /** Pure: the media kind (the layer's colour) of a picture shown in `role`. */
  def kindOf(role: String): String = role match
    case MediaRole.LocatorMap | MediaRole.RouteMap | MediaRole.Map => AssetKind.Map
    case MediaRole.Plan                                            => AssetKind.Plan
    case _                                                         => AssetKind.Photo
