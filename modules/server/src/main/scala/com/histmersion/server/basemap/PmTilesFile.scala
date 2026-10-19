package com.histmersion.server.basemap

import io.circe.Json

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap

/** A PMTiles archive on the local disk, opened once and read by many requests at a time: positional
  * reads on one [[FileChannel]] are thread-safe, and the leaf directories already read are kept
  * (bounded by `maxLeaves`) so a pan never re-reads the same directory. */
final class PmTilesFile private (val path: Path, ch: FileChannel, val header: PmTiles.Header,
    root: Vector[PmTiles.Entry], val metadata: Json, maxLeaves: Int) extends AutoCloseable:

  private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()

  private def read(offset: Long, length: Int): Array[Byte] =
    val buf = ByteBuffer.allocate(length)
    var at  = offset // positional reads may return short
    while buf.hasRemaining do
      val n = ch.read(buf, at)
      if n < 0 then throw java.io.EOFException(s"$path ends before byte ${offset + length}")
      at += n
    buf.array()

  private def leaf(e: PmTiles.Entry): Either[String, Vector[PmTiles.Entry]] =
    Option(leaves.get(e.offset)).map(Right(_)).getOrElse {
      PmTiles.decompress(read(header.leafOffset + e.offset, e.length), header.internalCompression)
        .flatMap(PmTiles.decodeDirectory).map { d =>
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
          case None                    => Right(None)
          case Some(e) if !e.isLeaf    => Right(Some(e))
          case Some(_) if depth >= 3   => Left(s"$path: directories nested too deep")
          case Some(e)                 => leaf(e).flatMap(walk(_, depth + 1))
      walk(root, 0)

  /** The stored bytes of `z/x/y` (still compressed as [[PmTiles.Header.tileCompression]] says), or
    * `None` when the archive has no such tile. */
  def tile(z: Int, x: Long, y: Long): Either[String, Option[Array[Byte]]] =
    entry(z, x, y).map(_.map(e => read(header.dataOffset + e.offset, e.length)))

  def size: Long = ch.size()

  def close(): Unit = ch.close()

object PmTilesFile:

  def open(path: Path, maxLeaves: Int = 256): Either[String, PmTilesFile] =
    val ch = FileChannel.open(path, StandardOpenOption.READ)
    val opened =
      try
        val first = ByteBuffer.allocate(math.min(PmTiles.RootBytes.toLong, ch.size()).toInt)
        var at = 0L // positional reads may return short
        while first.hasRemaining && ch.read(first, at) > 0 do at = first.position().toLong
        val bytes = first.array()
        for
          h    <- PmTiles.parseHeader(bytes)
          _    <- Either.cond(h.rootOffset + h.rootLength <= bytes.length, (), s"$path: root directory outside the first 16 KiB")
          root <- PmTiles.decompress(bytes.slice(h.rootOffset.toInt, (h.rootOffset + h.rootLength).toInt), h.internalCompression)
                    .flatMap(PmTiles.decodeDirectory)
          meta <- metadata(ch, h)
        yield new PmTilesFile(path, ch, h, root, meta, maxLeaves)
      catch case e: java.io.IOException => Left(s"$path: ${e.getMessage}")
    if opened.isLeft then ch.close()
    opened.left.map(e => if e.startsWith(path.toString) then e else s"$path: $e")

  private def metadata(ch: FileChannel, h: PmTiles.Header): Either[String, Json] =
    if h.metadataLength <= 0 then Right(Json.obj())
    else if h.metadataLength > 16L * 1024 * 1024 then Left("metadata over 16 MiB")
    else
      val buf = ByteBuffer.allocate(h.metadataLength.toInt)
      var at  = h.metadataOffset // positional reads may return short
      while buf.hasRemaining && ch.read(buf, at) > 0 do at = h.metadataOffset + buf.position()
      PmTiles.decompress(buf.array(), h.internalCompression)
        .flatMap(b => io.circe.parser.parse(String(b, "UTF-8")).left.map(e => s"metadata is not JSON: ${e.getMessage}"))
