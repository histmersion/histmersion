package com.histmersion.util.basemap

import com.histmersion.util.storage.{Blob, RandomReader}
import com.histmersion.ByteOffset
import io.circe.Json

import java.util.concurrent.ConcurrentHashMap

/** A PMTiles archive in a store, opened once and read by many requests at a time: positional reads on one
  * [[RandomReader]] are thread-safe (one interrupted read fails alone), and the leaf directories already read are kept
  * (bounded by `maxLeaves`) so a pan never re-reads the same directory.
  */
final class PmTilesFile private (
    /** Where the archive is, for messages. */
    val where: String,
    reader: RandomReader,
    val header: PmTiles.Header,
    root: Vector[PmTiles.Entry],
    val metadata: Json,
    maxLeaves: Int
) extends AutoCloseable:

  private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()

  private def read(offset: Long, length: Int): Array[Byte] = reader.read(ByteOffset.applyUnsafe(offset), length)

  private def leaf(e: PmTiles.Entry): Either[String, Vector[PmTiles.Entry]] =
    Option(leaves.get(e.offset)).map(Right(_)).getOrElse {
      PmTiles
        .decompress(read(header.leafOffset + e.offset, e.length), header.internalCompression)
        .flatMap(PmTiles.decodeDirectory)
        .map { d =>
          if leaves.size >= maxLeaves then leaves.clear()
          leaves.put(e.offset, d)
          d
        }
    }

  /** The entry for `z/x/y`, walking leaf directories (at most four levels, as the spec allows). */
  def entry(z: Int, x: Long, y: Long): Either[String, Option[PmTiles.Entry]] =
    if z < header.minZoom || z > header.maxZoom || x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Right(None)
    else
      val id = PmTiles.tileId(z, x, y)
      def walk(dir: Vector[PmTiles.Entry], depth: Int): Either[String, Option[PmTiles.Entry]] =
        PmTiles.find(dir, id) match
          case None                  => Right(None)
          case Some(e) if !e.isLeaf  => Right(Some(e))
          case Some(_) if depth >= 3 => Left(s"$where: directories nested too deep")
          case Some(e)               => leaf(e).flatMap(walk(_, depth + 1))
      walk(root, 0)

  /** The stored bytes of `z/x/y` (still compressed as [[PmTiles.Header.tileCompression]] says), or `None` when the
    * archive has no such tile.
    */
  def tile(z: Int, x: Long, y: Long): Either[String, Option[Array[Byte]]] =
    entry(z, x, y).map(_.map(e => read(header.dataOffset + e.offset, e.length)))

  def size: Long = reader.size.value

  def close(): Unit = reader.close()

object PmTilesFile:

  def open(blob: Blob, maxLeaves: Int = 256): Either[String, PmTilesFile] =
    val where = blob.where
    val opened =
      try
        val reader = blob.reader()
        val read =
          try
            val bytes = reader.readUpTo(ByteOffset.applyUnsafe(0L), PmTiles.RootBytes)
            for
              h <- PmTiles.parseHeader(bytes)
              _ <- Either.cond(
                h.rootOffset + h.rootLength <= bytes.length,
                (),
                s"$where: root directory outside the first 16 KiB"
              )
              root <- PmTiles
                .decompress(
                  bytes.slice(h.rootOffset.toInt, (h.rootOffset + h.rootLength).toInt),
                  h.internalCompression
                )
                .flatMap(PmTiles.decodeDirectory)
              meta <- metadata(reader, h)
            yield new PmTilesFile(where, reader, h, root, meta, maxLeaves)
          catch case e: java.io.IOException => Left(s"$where: ${e.getMessage}")
        if read.isLeft then reader.close()
        read
      catch case e: java.io.IOException => Left(s"$where: ${e.getMessage}")
    opened.left.map(e => if e.startsWith(where) then e else s"$where: $e")

  private def metadata(reader: RandomReader, h: PmTiles.Header): Either[String, Json] =
    if h.metadataLength <= 0 then Right(Json.obj())
    else if h.metadataLength > 16L * 1024 * 1024 then Left("metadata over 16 MiB")
    else
      // a file that ends early leaves the rest zeros, as a buffer read up to the end does
      val bytes = java.util.Arrays.copyOf(
        reader.readUpTo(ByteOffset.applyUnsafe(h.metadataOffset), h.metadataLength.toInt),
        h.metadataLength.toInt
      )
      PmTiles
        .decompress(bytes, h.internalCompression)
        .flatMap(b => io.circe.parser.parse(String(b, "UTF-8")).left.map(e => s"metadata is not JSON: ${e.getMessage}"))
