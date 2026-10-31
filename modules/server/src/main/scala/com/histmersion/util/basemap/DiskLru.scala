package com.histmersion.util.basemap

import java.nio.file.{Files, Path, StandardCopyOption}
import java.nio.file.attribute.FileTime
import scala.util.Using

/** A directory of tiles bounded in bytes, least recently used dropped first — an on-demand layer's cache. A tile is a
  * file `<z>/<x>/<y>.<ext>`; a tile the provider does not have is an empty `<z>/<x>/<y>.none`, so it is not asked for
  * again. Use is the file's modification time (set on each read), so the order survives a restart; the index is read
  * from the disk on first use.
  *
  * Thread-safe: every operation holds the instance's lock (they are file operations of a few KB).
  */
final class DiskLru(val root: Path, maxBytes: () => Long, ext: String = "webp"):

  // the index (access-ordered: the eldest is the least recently used) and its byte total, loaded once;
  // `var`s under the instance lock
  private val index = new java.util.LinkedHashMap[String, Long](256, 0.75f, true)
  private var total = 0L
  private var loaded = false

  private def file(key: String, absent: Boolean): Path = root.resolve(key + (if absent then ".none" else s".$ext"))

  private def load(): Unit = if !loaded then
    loaded = true
    if Files.isDirectory(root) then
      val found = Using.resource(Files.walk(root)) { s =>
        s.filter(Files.isRegularFile(_)).toArray.toList.map(_.asInstanceOf[Path]).flatMap { p =>
          val rel = root.relativize(p).toString.replace('\\', '/')
          val key = rel.stripSuffix(s".$ext").stripSuffix(".none")
          Option.when(key != rel && !rel.endsWith(".tmp"))((key, Files.size(p), Files.getLastModifiedTime(p).toMillis))
        }
      }
      found.sortBy(_._3).foreach { (k, n, _) => index.put(k, n); total += n }
      evict()

  private def evict(): Unit =
    val it = index.entrySet().iterator()
    while total > maxBytes() && it.hasNext do
      val e = it.next()
      it.remove()
      total -= e.getValue
      val _ = Files.deleteIfExists(file(e.getKey, absent = false))
      val _ = Files.deleteIfExists(file(e.getKey, absent = true))

  /** `Some(Some(bytes))` a cached tile, `Some(None)` one known to be missing, `None` not cached. */
  def get(key: String): Option[Option[Array[Byte]]] = synchronized {
    load()
    if !index.containsKey(key) then None
    else
      val _ = index.get(key) // marks it used
      val f = file(key, absent = false)
      if Files.isRegularFile(f) then
        scala.util.Try(Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis())))
        Some(Some(Files.readAllBytes(f)))
      else if Files.isRegularFile(file(key, absent = true)) then Some(None)
      else { total -= index.remove(key); None } // removed behind our back
  }

  /** Keep `bytes` (`None`: the provider has no such tile) as `key`, then drop the least recently used until the cache
    * is within its ceiling.
    */
  def put(key: String, bytes: Option[Array[Byte]]): Unit = synchronized {
    load()
    val f = file(key, bytes.isEmpty)
    Files.createDirectories(f.getParent)
    val tmp = f.resolveSibling(f.getFileName.toString + ".tmp")
    Files.write(tmp, bytes.getOrElse(Array.emptyByteArray))
    val _ = Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    Option(index.put(key, bytes.fold(0L)(_.length.toLong))).foreach(total -= _)
    total += bytes.fold(0L)(_.length.toLong)
    evict()
  }

  def entries: Long = synchronized { load(); index.size.toLong }
  def bytes: Long = synchronized { load(); total }

  /** Everything removed; answers the bytes it held. */
  def clear(): Long = synchronized {
    val was = BasemapService.deleteTree(root).getOrElse(0L)
    index.clear(); total = 0; loaded = false
    was
  }
