package com.histmersion.server.basemap

import io.circe.Json

import java.io.{BufferedOutputStream, OutputStream}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.mutable
import scala.util.Using

/** Writes a clustered PMTiles v3 archive: tiles are [[add]]ed in ascending tile-id order, identical
  * contents are stored once (a sea of identical ocean tiles costs one tile), consecutive ids that
  * share a content become one run, and [[finish]] completes the file at `out`.
  *
  * ONE file, written once: the first 16 KiB are reserved for the header and the root directory
  * (where the spec requires them), the tile data follows as tiles arrive, and the metadata and leaf
  * directories go after it — the spec lets those sections lie anywhere. So a multi-gigabyte extract
  * never needs twice its size on disk, and memory holds only the entries and one digest per content. */
final class PmTilesWriter(out: Path):

  private val dataPath = out.resolveSibling(out.getFileName.toString + ".tmp")
  private val data: OutputStream = BufferedOutputStream(Files.newOutputStream(dataPath), 1 << 16)
  data.write(new Array[Byte](PmTiles.RootBytes)) // the header and root directory's place
  // `var`s and mutable collections: the writer's running state, never visible outside it
  private var dataLength = 0L
  private val entries    = mutable.ArrayBuffer.empty[PmTiles.Entry]
  private val seen       = mutable.HashMap.empty[String, (Long, Int)]
  private var lastId     = -1L
  private var addressed  = 0L

  def tileCount: Long = addressed

  /** Add tile `id` (strictly greater than the last one added) with its stored bytes. */
  def add(id: Long, bytes: Array[Byte]): Unit =
    require(id > lastId, s"tiles must be added in ascending id order ($id after $lastId)")
    lastId = id
    addressed += 1
    val key = MessageDigest.getInstance("SHA-1").digest(bytes).map(b => f"$b%02x").mkString
    val (offset, length) = seen.getOrElseUpdate(key, {
      data.write(bytes)
      val at = dataLength
      dataLength += bytes.length
      (at, bytes.length)
    })
    entries.lastOption match
      case Some(e) if e.offset == offset && e.length == length && e.tileId + e.runLength == id =>
        entries(entries.size - 1) = e.copy(runLength = e.runLength + 1)
      case _ => entries += PmTiles.Entry(id, offset, length, 1)

  /** Complete the archive and return its size. `template` supplies the tile type, compression,
    * zooms, bounds and centre; the offsets and counts are this writer's. */
  def finish(template: PmTiles.Header, metadata: Json): Long =
    val ic = PmTiles.Compression.Gzip
    val (root, leaves) = PmTilesWriter.directories(entries.toVector, ic)
    val meta = PmTiles.compress(metadata.noSpaces.getBytes("UTF-8"), ic)
    val dataOffset = PmTiles.RootBytes.toLong
    val metaOffset = dataOffset + dataLength
    val leafOffset = metaOffset + meta.length
    data.write(meta)
    data.write(leaves)
    data.close()
    val header = template.copy(
      rootOffset = PmTiles.HeaderBytes.toLong, rootLength = root.length.toLong,
      metadataOffset = metaOffset, metadataLength = meta.length.toLong,
      leafOffset = leafOffset, leafLength = leaves.length.toLong,
      dataOffset = dataOffset, dataLength = dataLength,
      addressedTiles = addressed, tileEntries = entries.size.toLong, tileContents = seen.size.toLong,
      clustered = true, internalCompression = ic)
    Using.resource(java.nio.channels.FileChannel.open(dataPath, java.nio.file.StandardOpenOption.WRITE)) { ch =>
      val head = java.nio.ByteBuffer.wrap(PmTiles.encodeHeader(header) ++ root)
      var at = 0L // positional writes may be short
      while head.hasRemaining do at += ch.write(head, at)
    }
    val _ = Files.move(dataPath, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    Files.size(out)

  /** Give up: remove what was written. */
  def abort(): Unit =
    scala.util.Try(data.close())
    val _ = Files.deleteIfExists(dataPath)

object PmTilesWriter:

  /** The root directory and the leaf section for `entries`: one root when it fits in the first
    * 16 KiB, else leaves of 4 096 entries (doubled until their index fits). Pure. */
  def directories(entries: Vector[PmTiles.Entry], c: PmTiles.Compression): (Array[Byte], Array[Byte]) =
    val whole = PmTiles.compress(PmTiles.encodeDirectory(entries), c)
    if whole.length <= PmTiles.RootBytes - PmTiles.HeaderBytes then (whole, Array.emptyByteArray)
    else
      @annotation.tailrec
      def tryLeaves(size: Int): (Array[Byte], Array[Byte]) =
        val out   = java.io.ByteArrayOutputStream()
        val index = entries.grouped(size).map { chunk =>
          val b  = PmTiles.compress(PmTiles.encodeDirectory(chunk), c)
          val at = out.size().toLong
          out.write(b)
          PmTiles.Entry(chunk.head.tileId, at, b.length, 0)
        }.toVector
        val root = PmTiles.compress(PmTiles.encodeDirectory(index), c)
        if root.length <= PmTiles.RootBytes - PmTiles.HeaderBytes then (root, out.toByteArray)
        else tryLeaves(size * 2)
      tryLeaves(4096)
