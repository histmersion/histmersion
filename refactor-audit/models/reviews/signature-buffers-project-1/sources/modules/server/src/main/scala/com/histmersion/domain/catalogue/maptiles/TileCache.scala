package com.histmersion.domain.catalogue.maptiles

/** The tiles a server keeps, by key, the least recently asked for going first once they hold more than `maxBytes`. A
  * tile's key carries the versions of what it was cut from, so a write makes the tiles it touched unreachable at once;
  * they age out. Thread-safe.
  */
final class TileCache(maxBytes: () => TileCache.MaxBytesResult):
  private val tiles = new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true)
  // `var`: the bytes held
  private var held = 0L

  def get(key: com.histmersion.domain.catalogue.maptiles.TileCache.GetKey): Option[com.histmersion.domain.catalogue.maptiles.TileCache.GetResult] = synchronized(Option(tiles.get(key)))

  def put(key: com.histmersion.domain.catalogue.maptiles.TileCache.PutKey, bytes: com.histmersion.domain.catalogue.maptiles.TileCache.PutBytes): Unit = synchronized:
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

  def stats: (com.histmersion.domain.catalogue.maptiles.TileCache.StatsResultPart1, com.histmersion.domain.catalogue.maptiles.TileCache.StatsResultPart2) = synchronized((tiles.size, held))

object TileCache:
  type MaxBytesResult = MaxBytesResult.T
  object MaxBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GetResult = GetResult.T
  object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetKey = GetKey.T
  object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutKey = PutKey.T
  object PutKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutBytes = PutBytes.T
  object PutBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatsResultPart1 = StatsResultPart1.T
  object StatsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatsResultPart2 = StatsResultPart2.T
  object StatsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
