package com.histmersion.domain.catalogue.maptiles

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.{TileLayer, TilesApi}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object MapTiles:
  /** A tile's caching rule: the browser asks again each time and the server answers 304 while the data's version is
    * unchanged — never a stale tile after a write, a few bytes when nothing changed.
    */
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyVersionsPart1 = KeyVersionsPart1.T
  object KeyVersionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyVersionsPart2 = KeyVersionsPart2.T
  object KeyVersionsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtagResult = EtagResult.T
  object EtagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtagKey = EtagKey.T
  object EtagKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResult = MatchesResult.T
  object MatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesIfNoneMatch = MatchesIfNoneMatch.T
  object MatchesIfNoneMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesTag = MatchesTag.T
  object MatchesTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val CacheControl = "no-cache"

  /** A group's change rows above which they are folded into one ([[TileRepo.compact]]). */
  val CompactAbove = 256

  /** Pure: what a tile request is, as a cache key and an ETag: the layer, the tile, every parameter the layer reads
    * (the rest — a filter's paging, another layer's options — left out, so they never split one tile into many) and the
    * versions of the groups it reads.
    */
  def key(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, versions: Map[com.histmersion.domain.catalogue.maptiles.MapTiles.KeyVersionsPart1, com.histmersion.domain.catalogue.maptiles.MapTiles.KeyVersionsPart2]): com.histmersion.domain.catalogue.maptiles.MapTiles.KeyResult =
    val f = q.filter
    val time = f.period.fold("")(p => s"${p.start}..${p.end}")
    val undated = if q.withUndated then "undated" else ""
    val params = layer match
      case TileLayer.Sites  => List(time, q.siteType.getOrElse(""), undated)
      case TileLayer.Media  => List(time, q.kind.getOrElse(""), q.provider.getOrElse(""), q.license.getOrElse(""))
      case TileLayer.Events =>
        List(
          f.modify(_.limit).setTo(0).modify(_.offset).setTo(0).modify(_.sort).setTo(Filter().sort).toString,
          q.kind.getOrElse(""),
          q.tier.getOrElse(""),
          q.historicity.getOrElse(""),
          q.context.fold("")(_.toString),
          q.text.getOrElse(""),
          q.placeId.getOrElse(""),
          undated
        )
    val v = TileRepo.Groups(layer).map(g => s"$g=${versions.getOrElse(g, 0L)}")
    (List(layer.wire, s"${c.z}/${c.x}/${c.y}") ++ params ++ v).mkString("|")

  /** Pure: a key's ETag (a strong one: the tile's bytes follow from the key). */
  def etag(key: com.histmersion.domain.catalogue.maptiles.MapTiles.EtagKey): com.histmersion.domain.catalogue.maptiles.MapTiles.EtagResult =
    val d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))
    "\"" + d.take(12).map(b => f"${b & 0xff}%02x").mkString + "\""

  /** Pure: does the browser's `If-None-Match` name `tag` (one of a list, or `*`)? */
  def matches(ifNoneMatch: Option[com.histmersion.domain.catalogue.maptiles.MapTiles.MatchesIfNoneMatch], tag: com.histmersion.domain.catalogue.maptiles.MapTiles.MatchesTag): com.histmersion.domain.catalogue.maptiles.MapTiles.MatchesResult =
    ifNoneMatch.exists(h => h.split(",").map(_.trim.stripPrefix("W/")).exists(t => t == tag || t == "*"))
