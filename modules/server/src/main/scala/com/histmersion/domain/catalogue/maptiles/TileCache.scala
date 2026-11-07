package com.histmersion.domain.catalogue.maptiles

/** The tiles a server keeps, by key, the least recently asked for going first once they hold more than `maxBytes`. A
  * tile's key carries the versions of what it was cut from, so a write makes the tiles it touched unreachable at once;
  * they age out. Thread-safe.
  */
final class TileCache(maxBytes: () => TileCache.MaxBytesResult):
  private val tiles = new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true)
  // `var`: the bytes held
  private var held = 0L

  def get(key: com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey): Option[com.histmersion.domain.catalogue.maptiles.VectorTileWriter.VectorTile] = synchronized(Option(tiles.get(key)))

  def put(key: com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey, bytes: com.histmersion.domain.catalogue.maptiles.VectorTileWriter.VectorTile): Unit = synchronized:
    Option(tiles.put(key, bytes)).foreach(old => held -= old.length)
    held += bytes.length
    val it = tiles.entrySet.iterator
    while held > maxBytes() && it.hasNext do
      val e = it.next()
      held -= e.getValue.length
      it.remove()

  def clear(): Unit = synchronized:
    tiles.clear()
    held = 0L

  def stats: (com.histmersion.domain.catalogue.maptiles.TileCache.EntryCount, com.histmersion.ByteCount) = synchronized((tiles.size, held))

object TileCache:
  type EntryCount = EntryCount.T
  object EntryCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBytesResult = com.histmersion.ByteCount
  val MaxBytesResult: _root_.com.histmersion.ByteCount.type = _root_.com.histmersion.ByteCount

