package com.histmersion.web

import scala.collection.mutable

/** A cache with a ceiling (#1949): at most `maxEntries` entries and at most `maxWeight` of their
  * summed weight, the least recently used going first. Every cache the page keeps for its life is one
  * of these — a hover card per source, place GeoJSON per year, a label per gazetteer id — so a long
  * session costs a bounded amount of memory, however much it hovers, drags or types.
  *
  * A value's weight is `weigh(value)` when it is put; a value whose size is known only later (a
  * `Future` still on its way) is weighed again with [[reweigh]]. One entry heavier than the whole
  * budget is still kept — it is what was just asked for — and alone. Not thread-safe: the page has one
  * thread. Pure apart from its own state; no browser API. */
final class BoundedCache[K, V](val maxEntries: Int, val maxWeight: Long = Long.MaxValue,
    weigh: V => Long = (_: V) => 0L):
  require(maxEntries > 0, "a cache holds at least one entry")
  // insertion order is recency: an entry read or written moves to the end
  private val entries = mutable.LinkedHashMap.empty[K, (V, Long)]
  // `var`: the summed weight of `entries`, kept with every change
  private var total = 0L

  def size: Int      = entries.size
  def weight: Long   = total
  def keys: List[K]  = entries.keys.toList
  def contains(k: K): Boolean = entries.contains(k)

  /** The value of `k`, now the most recently used. */
  def get(k: K): Option[V] =
    entries.remove(k).map { e =>
      entries(k) = e
      e._1
    }

  /** The value of `k` without touching its recency (a read that must not keep an entry alive). */
  def peek(k: K): Option[V] = entries.get(k).map(_._1)

  def put(k: K, v: V): Unit =
    drop(k)
    val w = math.max(0L, weigh(v))
    entries(k) = (v, w)
    total += w
    evict(k)

  def getOrElseUpdate(k: K, v: => V): V =
    get(k) match
      case Some(x) => x
      case None =>
        val x = v
        put(k, x)
        x

  /** `k`'s weight is now `w` (its value arrived); over budget, the least recently used others go. */
  def reweigh(k: K, w: Long): Unit =
    entries.get(k).foreach { (v, old) =>
      entries(k) = (v, math.max(0L, w))
      total += math.max(0L, w) - old
      evict(k)
    }

  def remove(k: K): Option[V] = drop(k)

  def clear(): Unit =
    entries.clear()
    total = 0L

  private def drop(k: K): Option[V] =
    entries.remove(k).map { (v, w) =>
      total -= w
      v
    }

  /** Over either ceiling: the least recently used go first, never `keep` (the entry just written). */
  private def evict(keep: K): Unit =
    val it = entries.keysIterator
    val victims = mutable.ListBuffer.empty[K]
    // `var`s: what would be left once the victims so far are gone
    var n = entries.size
    var t = total
    while (n > maxEntries || t > maxWeight) && it.hasNext do
      val k = it.next()
      if k != keep then
        victims += k
        n -= 1
        t -= entries(k)._2
    victims.foreach(drop)

object BoundedCache:
  /** The figures a diagnostics read reports for one cache. */
  final case class Stats(entries: Int, maxEntries: Int, weight: Long, maxWeight: Long)

  def stats(c: BoundedCache[?, ?]): Stats = Stats(c.size, c.maxEntries, c.weight, c.maxWeight)
