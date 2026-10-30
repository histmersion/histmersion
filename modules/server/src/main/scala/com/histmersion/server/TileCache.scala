package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.db.*
import com.histmersion.domain.*

/** The tiles a server keeps, by key, the least recently asked for going first once they hold more than `maxBytes`. A
  * tile's key carries the versions of what it was cut from, so a write makes the tiles it touched unreachable at once;
  * they age out. Thread-safe.
  */
final class TileCache(maxBytes: () => Long):
  private val tiles = new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true)
  // `var`: the bytes held
  private var held = 0L

  def get(key: String): Option[Array[Byte]] = synchronized(Option(tiles.get(key)))

  def put(key: String, bytes: Array[Byte]): Unit = synchronized:
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

  def stats: (Int, Long) = synchronized((tiles.size, held))
