package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The tiles a server keeps, by key, the least recently asked for going first once they hold more than `maxBytes`. A
  * tile's key carries the versions of what it was cut from, so a write makes the tiles it touched unreachable at once;
  * they age out. Thread-safe.
  */
final class TileCache(maxBytes: () => ByteCount):
  private val tiles = new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true)
  // `var`: the bytes held
  private var held = 0L

  def get(key: MapTiles.TileKey): Option[VectorTileWriter.VectorTile] = synchronized(Option(tiles.get(key.value)).map(VectorTileWriter.VectorTile(_)))

  def put(key: MapTiles.TileKey, bytes: VectorTileWriter.VectorTile): Unit = synchronized:
    val raw = bytes.value
    Option(tiles.put(key.value, raw)).foreach(old => held -= old.length)
    held += raw.length
    val it = tiles.entrySet.iterator
    while held > maxBytes().value && it.hasNext do
      val e = it.next()
      held -= e.getValue.length
      it.remove()

  def clear(): Unit = synchronized:
    tiles.clear()
    held = 0L

  def stats: (TileCache.EntryCount, ByteCount) = synchronized((TileCache.EntryCount(tiles.size), ByteCount.assume(held)))

object TileCache:
  type EntryCount = EntryCount.T
  object EntryCount extends RefinedType[Int, Pure]
