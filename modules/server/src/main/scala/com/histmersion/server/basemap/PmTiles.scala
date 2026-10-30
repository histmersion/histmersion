package com.histmersion.server.basemap

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Using

/** The PMTiles v3 archive format (https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md): one file holding a
  * tile pyramid, addressed by a Hilbert-curve tile id, readable with HTTP range requests. The terrain base map's two
  * layers are stored in it (docs/research-terrain-basemap.md).
  *
  * Everything here is a PURE transformation of bytes — the header, the varint directories, the tile id — so the reader
  * ([[PmTilesFile]]), the writer ([[PmTilesWriter]]) and the remote extract ([[BasemapFetch]]) share one tested codec.
  */
object PmTiles:

  val HeaderBytes = 127

  /** The root directory must fit, with the header, in the first 16 KiB: one request reads both. */
  val RootBytes = 16384

  enum Compression(val code: Int):
    case Unknown extends Compression(0)
    case None extends Compression(1)
    case Gzip extends Compression(2)
    case Brotli extends Compression(3)
    case Zstd extends Compression(4)

  object Compression:
    def of(code: Int): Compression = values.find(_.code == code).getOrElse(Unknown)

  enum TileType(val code: Int, val mime: String):
    case Unknown extends TileType(0, "application/octet-stream")
    case Mvt extends TileType(1, "application/vnd.mapbox-vector-tile")
    case Png extends TileType(2, "image/png")
    case Jpeg extends TileType(3, "image/jpeg")
    case Webp extends TileType(4, "image/webp")
    case Avif extends TileType(5, "image/avif")

  object TileType:
    def of(code: Int): TileType = values.find(_.code == code).getOrElse(Unknown)

  final case class Header(
      rootOffset: Long,
      rootLength: Long,
      metadataOffset: Long,
      metadataLength: Long,
      leafOffset: Long,
      leafLength: Long,
      dataOffset: Long,
      dataLength: Long,
      addressedTiles: Long,
      tileEntries: Long,
      tileContents: Long,
      clustered: Boolean,
      internalCompression: Compression,
      tileCompression: Compression,
      tileType: TileType,
      minZoom: Int,
      maxZoom: Int,
      minLon: Double,
      minLat: Double,
      maxLon: Double,
      maxLat: Double,
      centerZoom: Int,
      centerLon: Double,
      centerLat: Double
  )

  /** One directory entry: `runLength` consecutive tile ids share the content at `offset`/`length` (relative to the tile
    * data section); `runLength == 0` points to a LEAF directory instead (relative to the leaf section).
    */
  final case class Entry(tileId: Long, offset: Long, length: Int, runLength: Int):
    def isLeaf: Boolean = runLength == 0

  private val Magic = "PMTiles".getBytes(StandardCharsets.US_ASCII)

  // ── the header ──────────────────────────────────────────────────────────────────────────────────

  def parseHeader(b: Array[Byte]): Either[String, Header] =
    if b.length < HeaderBytes then Left(s"a PMTiles header is $HeaderBytes bytes, got ${b.length}")
    else if !b.take(7).sameElements(Magic) then Left("not a PMTiles archive (no magic number)")
    else if b(7) != 3 then Left(s"PMTiles spec version ${b(7)} is not supported (only 3)")
    else
      val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
      def l(at: Int) = bb.getLong(at)
      def e7(at: Int) = bb.getInt(at) / 1e7
      def u8(at: Int) = b(at) & 0xff
      Right(
        Header(
          l(8),
          l(16),
          l(24),
          l(32),
          l(40),
          l(48),
          l(56),
          l(64),
          l(72),
          l(80),
          l(88),
          u8(96) == 1,
          Compression.of(u8(97)),
          Compression.of(u8(98)),
          TileType.of(u8(99)),
          u8(100),
          u8(101),
          e7(102),
          e7(106),
          e7(110),
          e7(114),
          u8(118),
          e7(119),
          e7(123)
        )
      )

  def encodeHeader(h: Header): Array[Byte] =
    val bb = ByteBuffer.allocate(HeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
    bb.put(Magic).put(3.toByte)
    List(
      h.rootOffset,
      h.rootLength,
      h.metadataOffset,
      h.metadataLength,
      h.leafOffset,
      h.leafLength,
      h.dataOffset,
      h.dataLength,
      h.addressedTiles,
      h.tileEntries,
      h.tileContents
    ).foreach(bb.putLong)
    bb.put((if h.clustered then 1 else 0).toByte)
      .put(h.internalCompression.code.toByte)
      .put(h.tileCompression.code.toByte)
      .put(h.tileType.code.toByte)
      .put(h.minZoom.toByte)
      .put(h.maxZoom.toByte)
    def e7(d: Double) = bb.putInt(math.round(d * 1e7).toInt)
    e7(h.minLon); e7(h.minLat); e7(h.maxLon); e7(h.maxLat)
    bb.put(h.centerZoom.toByte)
    e7(h.centerLon); e7(h.centerLat)
    bb.array()

  // ── tile ids ────────────────────────────────────────────────────────────────────────────────────

  /** The first tile id of zoom `z`: the number of tiles on all zooms below it, (4^z − 1) / 3. */
  def zoomBase(z: Int): Long = ((1L << (2 * z)) - 1) / 3

  /** The tile id of `z/x/y`: [[zoomBase]] plus the tile's position on the zoom's Hilbert curve. */
  def tileId(z: Int, x: Long, y: Long): Long =
    require(z >= 0 && z <= 26, s"zoom $z out of range")
    val n = 1L << z
    require(x >= 0 && x < n && y >= 0 && y < n, s"tile $z/$x/$y out of range")
    // `var`s: the curve is walked bit by bit, rotating the remaining coordinates as it goes
    var tx = x
    var ty = y
    var d = 0L
    var s = n >> 1
    while s > 0 do
      val rx = if (tx & s) != 0 then 1L else 0L
      val ry = if (ty & s) != 0 then 1L else 0L
      d += s * s * ((3L * rx) ^ ry)
      if ry == 0 then
        if rx == 1 then
          tx = s - 1 - tx
          ty = s - 1 - ty
        val t = tx
        tx = ty
        ty = t
      s >>= 1
    zoomBase(z) + d

  /** The inverse of [[tileId]]: `(z, x, y)`. */
  def zxy(id: Long): (Int, Long, Long) =
    var z = 0 // the zoom whose id range holds `id`
    while zoomBase(z + 1) <= id do z += 1
    val n = 1L << z
    var t = id - zoomBase(z) // the remaining Hilbert distance
    var tx = 0L
    var ty = 0L
    var s = 1L
    while s < n do
      val rx = 1L & (t / 2)
      val ry = 1L & (t ^ rx)
      if ry == 0 then
        if rx == 1 then
          tx = s - 1 - tx
          ty = s - 1 - ty
        val tmp = tx
        tx = ty
        ty = tmp
      tx += s * rx
      ty += s * ry
      t /= 4
      s <<= 1
    (z, tx, ty)

  // ── varints and directories ─────────────────────────────────────────────────────────────────────

  private def writeVarint(out: ByteArrayOutputStream, v: Long): Unit =
    var x = v // the bits still to write, seven at a time
    while (x & ~0x7fL) != 0 do
      out.write(((x & 0x7f) | 0x80).toInt)
      x >>>= 7
    out.write(x.toInt)

  /** A cursor over varints. */
  final private class Varints(b: Array[Byte]):
    private var pos = 0 // the next unread byte
    def next(): Long =
      var result = 0L
      var shift = 0
      var more = true
      while more do
        if pos >= b.length then throw IllegalArgumentException("truncated PMTiles directory")
        val byte = b(pos) & 0xff
        pos += 1
        result |= (byte & 0x7fL) << shift
        shift += 7
        more = (byte & 0x80) != 0
        if shift > 63 && more then throw IllegalArgumentException("varint too long in PMTiles directory")
      result

  /** A directory's bytes, uncompressed: the count, then the id deltas, run lengths, lengths and offsets column by
    * column (an offset of 0 means "right after the previous entry").
    */
  def encodeDirectory(entries: IndexedSeq[Entry]): Array[Byte] =
    val out = ByteArrayOutputStream()
    writeVarint(out, entries.size.toLong)
    var last = 0L // the previous tile id: ids are stored as deltas
    entries.foreach { e => writeVarint(out, e.tileId - last); last = e.tileId }
    entries.foreach(e => writeVarint(out, e.runLength.toLong))
    entries.foreach(e => writeVarint(out, e.length.toLong))
    entries.indices.foreach { i =>
      val e = entries(i)
      if i > 0 && e.offset == entries(i - 1).offset + entries(i - 1).length then writeVarint(out, 0)
      else writeVarint(out, e.offset + 1)
    }
    out.toByteArray

  def decodeDirectory(b: Array[Byte]): Either[String, Vector[Entry]] =
    try
      val v = Varints(b)
      val n = v.next()
      if n < 0 || n > 50_000_000 then Left(s"implausible PMTiles directory size $n")
      else
        val k = n.toInt
        val ids = new Array[Long](k)
        var last = 0L // the running tile id
        for i <- 0 until k do
          last += v.next()
          ids(i) = last
        val runs = Array.fill(k)(v.next().toInt)
        val lens = Array.fill(k)(v.next().toInt)
        val offs = new Array[Long](k)
        for i <- 0 until k do
          val raw = v.next()
          offs(i) = if raw == 0 && i > 0 then offs(i - 1) + lens(i - 1) else raw - 1
        Right(Vector.tabulate(k)(i => Entry(ids(i), offs(i), lens(i), runs(i))))
    catch case e: IllegalArgumentException => Left(e.getMessage)

  /** The entry that holds `id` in one sorted directory: the last whose id is ≤ `id` — a leaf pointer (to look further
    * in) or a run that covers it. `None`: not in this directory.
    */
  def find(entries: IndexedSeq[Entry], id: Long): Option[Entry] =
    var lo = 0
    var hi = entries.size - 1
    var at = -1 // the last entry with tileId ≤ id so far
    while lo <= hi do
      val mid = (lo + hi) >>> 1
      if entries(mid).tileId <= id then
        at = mid
        lo = mid + 1
      else hi = mid - 1
    if at < 0 then None
    else
      val e = entries(at)
      if e.isLeaf || id < e.tileId + e.runLength then Some(e) else None

  // ── compression ─────────────────────────────────────────────────────────────────────────────────

  def gzip(b: Array[Byte]): Array[Byte] =
    val out = ByteArrayOutputStream()
    Using.resource(GZIPOutputStream(out))(_.write(b))
    out.toByteArray

  def gunzip(b: Array[Byte]): Either[String, Array[Byte]] =
    try Right(Using.resource(GZIPInputStream(ByteArrayInputStream(b)))(_.readAllBytes()))
    catch case e: java.io.IOException => Left(s"corrupt gzip data: ${e.getMessage}")

  def isGzip(b: Array[Byte]): Boolean = b.length >= 2 && (b(0) & 0xff) == 0x1f && (b(1) & 0xff) == 0x8b

  def decompress(b: Array[Byte], c: Compression): Either[String, Array[Byte]] = c match
    case Compression.None | Compression.Unknown => Right(b)
    case Compression.Gzip                       => gunzip(b)
    case other => Left(s"PMTiles ${other.toString.toLowerCase} compression is not supported (only gzip)")

  def compress(b: Array[Byte], c: Compression): Array[Byte] = c match
    case Compression.Gzip => gzip(b)
    case _                => b
