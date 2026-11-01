package com.histmersion.domain.media.assets

import java.time.Instant

/** A picture as the in-memory store holds it: a row of `assets` — a source's figure (its `citekey`) or a provider's
  * picture (its `provider` and key) — with its point and inclusive span when it has them.
  */
final case class HeldAsset(
    uid: String,
    citekey: Option[String],
    name: String,
    mime: String,
    bytes: Array[Byte],
    sha256: String,
    width: Option[Int],
    height: Option[Int],
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    licenseUrl: Option[String] = None,
    author: Option[String] = None,
    credit: Option[String] = None,
    provider: Option[String] = None,
    providerKey: Option[String] = None,
    sourceUrl: Option[String] = None,
    originalUrl: Option[String] = None,
    retrievedAt: Option[Instant] = None,
    thumb: Option[Array[Byte]] = None,
    thumbMime: Option[String] = None,
    thumbWidth: Option[Int] = None,
    thumbHeight: Option[Int] = None,
    point: Option[(Double, Double)] = None,
    period: Option[(Option[Long], Option[Long])] = None
)
