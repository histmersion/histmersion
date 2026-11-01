package com.histmersion.domain.catalogue.maptiles

import com.histmersion.api.{Filter, TileLayer, TilesApi}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object MapTiles:
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
  def key(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, versions: Map[String, Long]): String =
    val f = q.filter
    val time = f.period.fold("")(p => s"${p.start}..${p.end}")
    val undated = if q.withUndated then "undated" else ""
    val params = layer match
      case TileLayer.Sites  => List(time, q.siteType.getOrElse(""), undated)
      case TileLayer.Media  => List(time, q.kind.getOrElse(""), q.provider.getOrElse(""), q.license.getOrElse(""))
      case TileLayer.Events =>
        List(
          f.copy(limit = 0, offset = 0, sort = Filter().sort).toString,
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
  def etag(key: String): String =
    val d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))
    "\"" + d.take(12).map(b => f"${b & 0xff}%02x").mkString + "\""

  /** Pure: does the browser's `If-None-Match` name `tag` (one of a list, or `*`)? */
  def matches(ifNoneMatch: Option[String], tag: String): Boolean =
    ifNoneMatch.exists(h => h.split(",").map(_.trim.stripPrefix("W/")).exists(t => t == tag || t == "*"))
