package com.histmersion
package util.basemap
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Raised
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.storage.{ByteSource, Reservation, Store}
import com.histmersion.{ByteCount, ByteOffset}
import hearth.kindlings.optics.syntax.*

import java.io.{BufferedOutputStream, IOException, OutputStream}
import java.security.MessageDigest
import scala.collection.mutable
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Writes a clustered PMTiles v3 archive: tiles are [[add]]ed in ascending tile-id order, identical contents are stored
  * once (a sea of identical ocean tiles costs one tile), consecutive ids that share a content become one run, and
  * [[finish]] completes the file `out` is reserved for, replacing what is at its key in one step.
  *
  * ONE file, written once: the first 16 KiB are reserved for the header and the root directory (where the spec requires
  * them), the tile data follows as tiles arrive, and the metadata and leaf directories go after it — the spec lets
  * those sections lie anywhere. So a multi-gigabyte extract never needs twice its size on disk, and memory holds only
  * the entries and one digest per content.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class PmTilesWriter(out: Reservation):

  private val data: OutputStream = BufferedOutputStream(out.output(), 1 << 16)
  data.write(new Array[Byte](PmTiles.RootBytes)) // the header and root directory's place
  // `var`s and mutable collections: the writer's running state, never visible outside it
  private var dataLength = 0L
  private val entries = mutable.ArrayBuffer.empty[PmTiles.Entry]
  private val seen = mutable.HashMap.empty[String, (ByteOffset, PmTiles.Entry.Length)]
  private var lastId = -1L
  private var addressed = 0L

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tileCount: PmTiles.Header.AddressedTiles = PmTiles.Header.AddressedTiles(addressed)

  /** Add tile `id` (strictly greater than the last one added) with its stored bytes. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def add(id: PmTiles.Entry.TileId, bytes: PmTiles.TileBytes): Unit =
    val raw: Array[Byte] = bytes.value
    require(id.value > lastId, s"tiles must be added in ascending id order (${id.value} after $lastId)")
    lastId = id.value
    addressed += 1
    val key = MessageDigest.getInstance("SHA-1").digest(raw).map(b => f"$b%02x").mkString
    val (offset, length) = seen.getOrElseUpdate(
      key, {
        data.write(raw)
        val at = dataLength
        dataLength += raw.length
        (ByteOffset.applyUnsafe(at), PmTiles.Entry.Length(raw.length))
      }
    )
    entries.lastOption match
      case Some(e) if e.offset == offset && e.length == length && e.tileId.value + e.runLength.value == id.value =>
        entries(entries.size - 1) = e.modify(_.runLength).using(r => PmTiles.Entry.RunLength(r.value + 1))
      case _ => entries += PmTiles.Entry(id, offset, length, PmTiles.Entry.RunLength(1))

  /** Complete the archive and return its size. `template` supplies the tile type, compression, zooms, bounds and
    * centre; the offsets and counts are this writer's.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def finish(template: PmTiles.Header, metadata: Json): ByteCount =
    val ic = PmTiles.Compression.Gzip
    val (root, leaves) = PmTilesWriter.directories(entries.toVector, ic)
    val meta = PmTiles.compress(PmTiles.PlainBytes(metadata.noSpaces.getBytes("UTF-8")), ic)
    val rootBytes: Array[Byte] = root.value
    val leafBytes: Array[Byte] = leaves.value
    val metaBytes: Array[Byte] = meta.value
    val dataOffset = PmTiles.RootBytes.toLong
    val metaOffset = dataOffset + dataLength
    val leafOffset = metaOffset + metaBytes.length
    data.write(metaBytes)
    data.write(leafBytes)
    data.close()
    val header = template
      .modify(_.rootOffset)
      .setTo(ByteOffset.applyUnsafe(PmTiles.HeaderLength.toLong))
      .modify(_.rootLength)
      .setTo(ByteCount.applyUnsafe(rootBytes.length.toLong))
      .modify(_.metadataOffset)
      .setTo(ByteOffset.applyUnsafe(metaOffset))
      .modify(_.metadataLength)
      .setTo(ByteCount.applyUnsafe(metaBytes.length.toLong))
      .modify(_.leafOffset)
      .setTo(ByteOffset.applyUnsafe(leafOffset))
      .modify(_.leafLength)
      .setTo(ByteCount.applyUnsafe(leafBytes.length.toLong))
      .modify(_.dataOffset)
      .setTo(ByteOffset.applyUnsafe(dataOffset))
      .modify(_.dataLength)
      .setTo(ByteCount.applyUnsafe(dataLength))
      .modify(_.addressedTiles)
      .setTo(PmTiles.Header.AddressedTiles(addressed))
      .modify(_.tileEntries)
      .setTo(PmTiles.Header.TileEntries(entries.size.toLong))
      .modify(_.tileContents)
      .setTo(PmTiles.Header.TileContents(seen.size.toLong))
      .modify(_.clustered)
      .setTo(PmTiles.Header.Clustered(true))
      .modify(_.internalCompression)
      .setTo(ic)
    val headerBytes: Array[Byte] = PmTiles.encodeHeader(header).value
    out.writeAt(ByteOffset.applyUnsafe(0L), ByteSource.Content(headerBytes ++ rootBytes))
    out.commit().fold(e => throw Raised(e.message), _.size)

  /** Give up: remove what was written. */
  def abort(): Unit =
    scala.util.Try(data.close())
    out.discard()

object PmTilesWriter:
  type EntriesPerLeaf = EntriesPerLeaf.T
  object EntriesPerLeaf extends Newtype[Int, Pure]

  /** A writer of the archive `key` of `store`, written beside it until [[PmTilesWriter.finish]]. */
  def apply(store: Store, key: Store.Key): PmTilesWriter =
    new PmTilesWriter(store.reserve(key).fold(e => throw Raised(e.message), identity))

  /** The root directory and the leaf section for `entries`: one root when it fits in the first 16 KiB, else leaves of 4
    * 096 entries (doubled until their index fits). Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def directories(
      entries: Vector[PmTiles.Entry],
      c: PmTiles.Compression
  ): (PmTiles.CompressedBytes, PmTiles.CompressedBytes) =
    val whole = PmTiles.compress(PmTiles.encodeDirectory(entries), c)
    if whole.value.length <= PmTiles.RootBytes - PmTiles.HeaderLength then
      (whole, PmTiles.CompressedBytes(Array.emptyByteArray))
    else
      @annotation.tailrec
      def tryLeaves(size: EntriesPerLeaf): (PmTiles.CompressedBytes, PmTiles.CompressedBytes) =
        val out = java.io.ByteArrayOutputStream()
        val index = entries
          .grouped(size.value)
          .map { chunk =>
            val b: Array[Byte] = PmTiles.compress(PmTiles.encodeDirectory(chunk), c).value
            val at = out.size().toLong
            out.write(b)
            PmTiles.Entry(
              chunk.head.tileId,
              ByteOffset.applyUnsafe(at),
              PmTiles.Entry.Length(b.length),
              PmTiles.Entry.RunLength(0)
            )
          }
          .toVector
        val root = PmTiles.compress(PmTiles.encodeDirectory(index), c)
        if root.value.length <= PmTiles.RootBytes - PmTiles.HeaderLength then
          (root, PmTiles.CompressedBytes(out.toByteArray))
        else tryLeaves(EntriesPerLeaf(size.value * 2))
      tryLeaves(EntriesPerLeaf(4096))
