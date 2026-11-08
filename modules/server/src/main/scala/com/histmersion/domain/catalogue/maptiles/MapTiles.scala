package com.histmersion
package domain.catalogue.maptiles

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.{TileLayer, TilesApi}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import io.github.iltotore.iron.constraint.any.Pure

object MapTiles:
  type Etag = Etag.T
  object Etag extends Newtype[String, Pure]
  type TileKey = TileKey.T
  object TileKey extends Newtype[String, Pure]
  /** A tile's caching rule: the browser asks again each time and the server answers 304 while the data's version is
    * unchanged — never a stale tile after a write, a few bytes when nothing changed.
    */

  val CacheControl = "no-cache"

  /** A group's change rows above which they are folded into one ([[TileRepo.compact]]). */
  val CompactAbove = 256

  /** Pure: what a tile request is, as a cache key and an ETag: the layer, the tile, every parameter the layer reads
    * (the rest — a filter's paging, another layer's options — left out, so they never split one tile into many) and the
    * versions of the groups it reads.
    */
  def key(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, versions: Map[TileRepo.GroupName, UndatedBuild.Version]): TileKey =
    val f = q.filter
    val time = f.period.fold("")(p => s"${p.start}..${p.end}")
    val undated = if q.withUndated then "undated" else ""
    val params = layer match
      case TileLayer.Sites  => List(time, q.siteType.fold("")(_.value), undated)
      case TileLayer.Media  =>
        List(time, q.kind.fold("")(_.value), q.provider.fold("")(_.value), q.license.fold("")(_.value))
      case TileLayer.Events =>
        List(
          f.modify(_.limit).setTo(Filter.Limit(0)).modify(_.offset).setTo(Filter.Offset(0)).modify(_.sort).setTo(Filter().sort).toString,
          q.kind.fold("")(_.value),
          q.tier.fold("")(_.value),
          q.historicity.fold("")(_.value),
          q.context.fold("")(_.value.toString),
          q.text.fold("")(_.value),
          q.placeId.fold("")(_.value),
          undated
        )
    val v = TileRepo.Groups(layer).map(g => s"$g=${versions.get(g).fold(0L)(_.value)}")
    TileKey((List(layer.wire.value, s"${c.z}/${c.x}/${c.y}") ++ params ++ v).mkString("|"))

  /** Pure: a key's ETag (a strong one: the tile's bytes follow from the key). */
  def etag(key: TileKey): Etag =
    val d = MessageDigest.getInstance("SHA-256").digest(key.value.getBytes(StandardCharsets.UTF_8))
    Etag("\"" + d.take(12).map(b => f"${b & 0xff}%02x").mkString + "\"")

  /** Pure: does the browser's `If-None-Match` name `tag` (one of a list, or `*`)? */
  def matches(ifNoneMatch: Option[Etag], tag: Etag): Boolean =
    ifNoneMatch.exists(h => h.value.split(",").map(_.trim.stripPrefix("W/")).exists(t => t == tag.value || t == "*"))
