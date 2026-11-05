package com.histmersion.util.basemap

import com.histmersion.util.storage.Store

import java.io.IOException
import java.time.Instant

/** A store of tiles bounded in bytes, least recently used dropped first — an on-demand layer's cache. A tile is a file
  * `<z>/<x>/<y>.<ext>`; a tile the provider does not have is an empty `<z>/<x>/<y>.none`, so it is not asked for again.
  * Use is the file's modification time (set on each read), so the order survives a restart; the index is read from the
  * store on first use.
  *
  * Thread-safe: every operation holds the instance's lock (they are file operations of a few KB).
  */
final class DiskLru(store: Store, maxBytes: () => DiskLru.MaxBytesResult, ext: DiskLru.Ext = "webp")(using clock: java.time.Clock):

  // the index (access-ordered: the eldest is the least recently used) and its byte total, loaded once;
  // `var`s under the instance lock
  private val index = new java.util.LinkedHashMap[String, Long](256, 0.75f, true)
  private var total = 0L
  private var loaded = false

  private def file(key: com.histmersion.util.basemap.DiskLru.FileKey, absent: com.histmersion.util.basemap.DiskLru.FileAbsent): Store.Key =
    Store.Key.applyUnsafe(key + (if absent then ".none" else s".$ext"))

  private def done(written: Either[Store.Error, ?]): Unit = written.left.foreach(e => throw IOException(e.message))

  private def load(): Unit = if !loaded then
    loaded = true
    if store.isDirectory(Store.Key.Root) then
      val found = store.walk().flatMap { s =>
        val rel = s.key.value
        val key = rel.stripSuffix(s".$ext").stripSuffix(".none")
        Option.when(key != rel && !rel.endsWith(".tmp"))((key, s.size.value, s.modified.toEpochMilli))
      }
      found.sortBy(_._3).foreach { (k, n, _) => index.put(k, n); total += n }
      evict()

  private def evict(): Unit =
    val it = index.entrySet().iterator()
    while total > maxBytes() && it.hasNext do
      val e = it.next()
      it.remove()
      total -= e.getValue
      val _ = store.delete(file(e.getKey, absent = false))
      val _ = store.delete(file(e.getKey, absent = true))

  /** `Some(Some(bytes))` a cached tile, `Some(None)` one known to be missing, `None` not cached. */
  def get(key: com.histmersion.util.basemap.DiskLru.GetKey): Option[Option[com.histmersion.util.basemap.DiskLru.GetResult]] = synchronized {
    load()
    if !index.containsKey(key) then None
    else
      val _ = index.get(key) // marks it used
      val f = file(key, absent = false)
      store.get(f) match
        case Some(b) =>
          store.touch(f, Instant.ofEpochMilli(clock.millis()))
          Some(Some(b.bytes()))
        case None if store.isFile(file(key, absent = true)) => Some(None)
        case None => total -= index.remove(key); None // removed behind our back
  }

  /** Keep `bytes` (`None`: the provider has no such tile) as `key`, then drop the least recently used until the cache
    * is within its ceiling.
    */
  def put(key: com.histmersion.util.basemap.DiskLru.PutKey, bytes: Option[com.histmersion.util.basemap.DiskLru.PutBytes]): Unit = synchronized {
    load()
    done(store.put(file(key, bytes.isEmpty), bytes.getOrElse(Array.emptyByteArray)))
    Option(index.put(key, bytes.fold(0L)(_.length.toLong))).foreach(total -= _)
    total += bytes.fold(0L)(_.length.toLong)
    evict()
  }

  def entries: com.histmersion.util.basemap.DiskLru.EntriesResult = synchronized { load(); index.size.toLong }
  def bytes: com.histmersion.util.basemap.DiskLru.BytesResult = synchronized { load(); total }

  /** Everything removed; answers the bytes it held. */
  def clear(): com.histmersion.util.basemap.DiskLru.ClearResult = synchronized {
    val was = store.deleteTree(Store.Key.Root).fold(0L)(_.value)
    index.clear(); total = 0; loaded = false
    was
  }

object DiskLru:
  type GetResult = GetResult.T
  object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutBytes = PutBytes.T
  object PutBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxBytesResult = MaxBytesResult.T
  object MaxBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ext = Ext.T
  object Ext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FileKey = FileKey.T
  object FileKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileAbsent = FileAbsent.T
  object FileAbsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetKey = GetKey.T
  object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutKey = PutKey.T
  object PutKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntriesResult = EntriesResult.T
  object EntriesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResult = BytesResult.T
  object BytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClearResult = ClearResult.T
  object ClearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
