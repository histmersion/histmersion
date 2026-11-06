package com.histmersion.util.basemap

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Using
import com.histmersion.ByteOffset
import com.histmersion.util.storage.{Blob, RandomReader}
import io.circe.Json
import java.util.concurrent.ConcurrentHashMap

/** The PMTiles v3 archive format (https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md): one file holding a
  * tile pyramid, addressed by a Hilbert-curve tile id, readable with HTTP range requests. The terrain base map's two
  * layers are stored in it (docs/research-terrain-basemap.md).
  *
  * Everything here is a PURE transformation of bytes — the header, the varint directories, the tile id — so the reader
  * ([[PmTilesFile]]), the writer ([[PmTilesWriter]]) and the remote extract ([[BasemapFetch]]) share one tested codec.
  */
object PmTiles:

  type ParseHeaderLAt = ParseHeaderLAt.T
  object ParseHeaderLAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseHeaderE7At = ParseHeaderE7At.T
  object ParseHeaderE7At extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseHeaderU8At = ParseHeaderU8At.T
  object ParseHeaderU8At extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeHeaderE7D = EncodeHeaderE7D.T
  object EncodeHeaderE7D extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseHeaderB = ParseHeaderB.T
  object ParseHeaderB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeHeaderResult = EncodeHeaderResult.T
  object EncodeHeaderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeDirectoryResult = EncodeDirectoryResult.T
  object EncodeDirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeDirectoryB = DecodeDirectoryB.T
  object DecodeDirectoryB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GzipResult = GzipResult.T
  object GzipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GzipB = GzipB.T
  object GzipB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GunzipResultPart2 = GunzipResultPart2.T
  object GunzipResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GunzipB = GunzipB.T
  object GunzipB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsGzipB = IsGzipB.T
  object IsGzipB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecompressResultPart2 = DecompressResultPart2.T
  object DecompressResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecompressB = _root_.com.histmersion.util.basemap.PmTiles.GunzipB
  val DecompressB: _root_.com.histmersion.util.basemap.PmTiles.GunzipB.type = _root_.com.histmersion.util.basemap.PmTiles.GunzipB
  type CompressResult = CompressResult.T
  object CompressResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompressB = _root_.com.histmersion.util.basemap.PmTiles.GzipB
  val CompressB: _root_.com.histmersion.util.basemap.PmTiles.GzipB.type = _root_.com.histmersion.util.basemap.PmTiles.GzipB

  type ParseHeaderResultPart1 = ParseHeaderResultPart1.T
  object ParseHeaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomBaseResult = ZoomBaseResult.T
  object ZoomBaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomBaseZ = ZoomBaseZ.T
  object ZoomBaseZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileIdResult = TileIdResult.T
  object TileIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileIdZ = _root_.com.histmersion.util.basemap.PmTiles.ZoomBaseZ
  val TileIdZ: _root_.com.histmersion.util.basemap.PmTiles.ZoomBaseZ.type = _root_.com.histmersion.util.basemap.PmTiles.ZoomBaseZ
  type TileIdX = TileIdX.T
  object TileIdX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileIdY = TileIdY.T
  object TileIdY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZxyResultPart1 = ZxyResultPart1.T
  object ZxyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZxyResultPart2 = ZxyResultPart2.T
  object ZxyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZxyResultPart3 = ZxyResultPart3.T
  object ZxyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZxyId = ZxyId.T
  object ZxyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteVarintV = WriteVarintV.T
  object WriteVarintV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeDirectoryResultPart1 = DecodeDirectoryResultPart1.T
  object DecodeDirectoryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindId = FindId.T
  object FindId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GunzipResultPart1 = GunzipResultPart1.T
  object GunzipResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsGzipResult = IsGzipResult.T
  object IsGzipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecompressResultPart1 = DecompressResultPart1.T
  object DecompressResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val HeaderBytes = 127

  /** The root directory must fit, with the header, in the first 16 KiB: one request reads both. */
  val RootBytes = 16384

  enum Compression(val code: Compression.Code):
    case Unknown extends Compression(0)
    case None extends Compression(1)
    case Gzip extends Compression(2)
    case Brotli extends Compression(3)
    case Zstd extends Compression(4)

  object Compression:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type OfCode = OfCode.T
    object OfCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(code: com.histmersion.util.basemap.PmTiles.Compression.OfCode): Compression = values.find(_.code == code).getOrElse(Unknown)

  enum TileType(val code: TileType.Code, val mime: TileType.Mime):
    case Unknown extends TileType(0, "application/octet-stream")
    case Mvt extends TileType(1, "application/vnd.mapbox-vector-tile")
    case Png extends TileType(2, "image/png")
    case Jpeg extends TileType(3, "image/jpeg")
    case Webp extends TileType(4, "image/webp")
    case Avif extends TileType(5, "image/avif")

  object TileType:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mime = Mime.T
    object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type OfCode = OfCode.T
    object OfCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(code: com.histmersion.util.basemap.PmTiles.TileType.OfCode): TileType = values.find(_.code == code).getOrElse(Unknown)

  final case class Header(
      rootOffset: Header.RootOffset,
      rootLength: Header.RootLength,
      metadataOffset: Header.MetadataOffset,
      metadataLength: Header.MetadataLength,
      leafOffset: Header.LeafOffset,
      leafLength: Header.LeafLength,
      dataOffset: Header.DataOffset,
      dataLength: Header.DataLength,
      addressedTiles: Header.AddressedTiles,
      tileEntries: Header.TileEntries,
      tileContents: Header.TileContents,
      clustered: Header.Clustered,
      internalCompression: Compression,
      tileCompression: Compression,
      tileType: TileType,
      minZoom: Header.MinZoom,
      maxZoom: Header.MaxZoom,
      minLon: Header.MinLon,
      minLat: Header.MinLat,
      maxLon: Header.MaxLon,
      maxLat: Header.MaxLat,
      centerZoom: Header.CenterZoom,
      centerLon: Header.CenterLon,
      centerLat: Header.CenterLat
  )

  object Header:
    type RootOffset = RootOffset.T
    object RootOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RootLength = RootLength.T
    object RootLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MetadataOffset = MetadataOffset.T
    object MetadataOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MetadataLength = MetadataLength.T
    object MetadataLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeafOffset = LeafOffset.T
    object LeafOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeafLength = LeafLength.T
    object LeafLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DataOffset = DataOffset.T
    object DataOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DataLength = DataLength.T
    object DataLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddressedTiles = AddressedTiles.T
    object AddressedTiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileEntries = TileEntries.T
    object TileEntries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileContents = TileContents.T
    object TileContents extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Clustered = Clustered.T
    object Clustered extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLon = MinLon.T
    object MinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLat = MinLat.T
    object MinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CenterZoom = CenterZoom.T
    object CenterZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CenterLon = CenterLon.T
    object CenterLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CenterLat = CenterLat.T
    object CenterLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One directory entry: `runLength` consecutive tile ids share the content at `offset`/`length` (relative to the tile
    * data section); `runLength == 0` points to a LEAF directory instead (relative to the leaf section).
    */
  final case class Entry(tileId: Entry.TileId, offset: Entry.Offset, length: Entry.Length, runLength: Entry.RunLength):
    def isLeaf: com.histmersion.util.basemap.PmTiles.Entry.IsLeafResult = runLength == 0

  object Entry:
    type IsLeafResult = IsLeafResult.T
    object IsLeafResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type TileId = TileId.T
    object TileId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Length = Length.T
    object Length extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunLength = RunLength.T
    object RunLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Magic = "PMTiles".getBytes(StandardCharsets.US_ASCII)

  // ── the header ──────────────────────────────────────────────────────────────────────────────────

  def parseHeader(b: com.histmersion.util.basemap.PmTiles.ParseHeaderB): Either[com.histmersion.util.basemap.PmTiles.ParseHeaderResultPart1, Header] =
    if b.length < HeaderBytes then Left(s"a PMTiles header is $HeaderBytes bytes, got ${b.length}")
    else if !b.take(7).sameElements(Magic) then Left("not a PMTiles archive (no magic number)")
    else if b(7) != 3 then Left(s"PMTiles spec version ${b(7)} is not supported (only 3)")
    else
      val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
      def l(at: com.histmersion.util.basemap.PmTiles.ParseHeaderLAt) = bb.getLong(at)
      def e7(at: com.histmersion.util.basemap.PmTiles.ParseHeaderE7At) = bb.getInt(at) / 1e7
      def u8(at: com.histmersion.util.basemap.PmTiles.ParseHeaderU8At) = b(at) & 0xff
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

  def encodeHeader(h: Header): com.histmersion.util.basemap.PmTiles.EncodeHeaderResult =
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
    def e7(d: com.histmersion.util.basemap.PmTiles.EncodeHeaderE7D) = bb.putInt(math.round(d * 1e7).toInt)
    e7(h.minLon); e7(h.minLat); e7(h.maxLon); e7(h.maxLat)
    bb.put(h.centerZoom.toByte)
    e7(h.centerLon); e7(h.centerLat)
    bb.array()

  // ── tile ids ────────────────────────────────────────────────────────────────────────────────────

  /** The first tile id of zoom `z`: the number of tiles on all zooms below it, (4^z − 1) / 3. */
  def zoomBase(z: com.histmersion.util.basemap.PmTiles.ZoomBaseZ): com.histmersion.util.basemap.PmTiles.ZoomBaseResult = ((1L << (2 * z)) - 1) / 3

  /** The tile id of `z/x/y`: [[zoomBase]] plus the tile's position on the zoom's Hilbert curve. */
  def tileId(z: com.histmersion.util.basemap.PmTiles.TileIdZ, x: com.histmersion.util.basemap.PmTiles.TileIdX, y: com.histmersion.util.basemap.PmTiles.TileIdY): com.histmersion.util.basemap.PmTiles.TileIdResult =
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
  def zxy(id: com.histmersion.util.basemap.PmTiles.ZxyId): (com.histmersion.util.basemap.PmTiles.ZxyResultPart1, com.histmersion.util.basemap.PmTiles.ZxyResultPart2, com.histmersion.util.basemap.PmTiles.ZxyResultPart3) =
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

  private def writeVarint(out: ByteArrayOutputStream, v: com.histmersion.util.basemap.PmTiles.WriteVarintV): Unit =
    var x = v // the bits still to write, seven at a time
    while (x & ~0x7fL) != 0 do
      out.write(((x & 0x7f) | 0x80).toInt)
      x >>>= 7
    out.write(x.toInt)

  /** A cursor over varints. */
  final private class Varints(b: Array[Byte]):
    private var pos = 0 // the next unread byte
    def next(): com.histmersion.util.basemap.PmTiles.Varints.NextResult =
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

  object Varints:
    type NextResult = NextResult.T
    object NextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A directory's bytes, uncompressed: the count, then the id deltas, run lengths, lengths and offsets column by
    * column (an offset of 0 means "right after the previous entry").
    */
  def encodeDirectory(entries: IndexedSeq[Entry]): com.histmersion.util.basemap.PmTiles.EncodeDirectoryResult =
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

  def decodeDirectory(b: com.histmersion.util.basemap.PmTiles.DecodeDirectoryB): Either[com.histmersion.util.basemap.PmTiles.DecodeDirectoryResultPart1, Vector[Entry]] =
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
  def find(entries: IndexedSeq[Entry], id: com.histmersion.util.basemap.PmTiles.FindId): Option[Entry] =
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

  def gzip(b: com.histmersion.util.basemap.PmTiles.GzipB): com.histmersion.util.basemap.PmTiles.GzipResult =
    val out = ByteArrayOutputStream()
    Using.resource(GZIPOutputStream(out))(_.write(b))
    out.toByteArray

  def gunzip(b: com.histmersion.util.basemap.PmTiles.GunzipB): Either[com.histmersion.util.basemap.PmTiles.GunzipResultPart1, com.histmersion.util.basemap.PmTiles.GunzipResultPart2] =
    try Right(Using.resource(GZIPInputStream(ByteArrayInputStream(b)))(_.readAllBytes()))
    catch case e: java.io.IOException => Left(s"corrupt gzip data: ${e.getMessage}")

  def isGzip(b: com.histmersion.util.basemap.PmTiles.IsGzipB): com.histmersion.util.basemap.PmTiles.IsGzipResult = b.length >= 2 && (b(0) & 0xff) == 0x1f && (b(1) & 0xff) == 0x8b

  def decompress(b: com.histmersion.util.basemap.PmTiles.DecompressB, c: Compression): Either[com.histmersion.util.basemap.PmTiles.DecompressResultPart1, com.histmersion.util.basemap.PmTiles.DecompressResultPart2] = c match
    case Compression.None | Compression.Unknown => Right(b)
    case Compression.Gzip                       => gunzip(b)
    case other => Left(s"PMTiles ${other.toString.toLowerCase} compression is not supported (only gzip)")

  def compress(b: com.histmersion.util.basemap.PmTiles.CompressB, c: Compression): com.histmersion.util.basemap.PmTiles.CompressResult = c match
    case Compression.Gzip => gzip(b)
    case _                => b

  /** A PMTiles archive in a store, opened once and read by many requests at a time: positional reads on one
    * [[RandomReader]] are thread-safe (one interrupted read fails alone), and the leaf directories already read are
    * kept (bounded by `maxLeaves`) so a pan never re-reads the same directory.
    */
  final class File private (
      /** Where the archive is, for messages. */
      val where: File.Where,
      reader: RandomReader,
      val header: PmTiles.Header,
      root: Vector[PmTiles.Entry],
      val metadata: Json,
      maxLeaves: File.MaxLeaves
  ) extends AutoCloseable:

    private val leaves = ConcurrentHashMap[Long, Vector[PmTiles.Entry]]()

    private def read(offset: com.histmersion.util.basemap.PmTiles.File.ReadOffset, length: com.histmersion.util.basemap.PmTiles.File.ReadLength): com.histmersion.util.basemap.PmTiles.File.ReadResult = reader.read(ByteOffset.applyUnsafe(offset), length)

    private def leaf(e: PmTiles.Entry): Either[com.histmersion.util.basemap.PmTiles.File.LeafResultPart1, Vector[PmTiles.Entry]] =
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
    def entry(z: com.histmersion.util.basemap.PmTiles.File.EntryZ, x: com.histmersion.util.basemap.PmTiles.File.EntryX, y: com.histmersion.util.basemap.PmTiles.File.EntryY): Either[com.histmersion.util.basemap.PmTiles.File.EntryResultPart1, Option[PmTiles.Entry]] =
      if z < header.minZoom || z > header.maxZoom || x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Right(None)
      else
        val id = PmTiles.tileId(z, x, y)
        def walk(dir: Vector[PmTiles.Entry], depth: com.histmersion.util.basemap.PmTiles.File.EntryWalkDepth): Either[com.histmersion.util.basemap.PmTiles.File.EntryWalkResultPart1, Option[PmTiles.Entry]] =
          PmTiles.find(dir, id) match
            case None                  => Right(None)
            case Some(e) if !e.isLeaf  => Right(Some(e))
            case Some(_) if depth >= 3 => Left(s"$where: directories nested too deep")
            case Some(e)               => leaf(e).flatMap(walk(_, depth + 1))
        walk(root, 0)

    /** The stored bytes of `z/x/y` (still compressed as [[PmTiles.Header.tileCompression]] says), or `None` when the
      * archive has no such tile.
      */
    def tile(z: com.histmersion.util.basemap.PmTiles.File.TileZ, x: com.histmersion.util.basemap.PmTiles.File.TileX, y: com.histmersion.util.basemap.PmTiles.File.TileY): Either[com.histmersion.util.basemap.PmTiles.File.TileResultPart1, Option[com.histmersion.util.basemap.PmTiles.File.TileResultPart2]] =
      entry(z, x, y).map(_.map(e => read(header.dataOffset + e.offset, e.length)))

    def size: com.histmersion.util.basemap.PmTiles.File.SizeResult = reader.size.value

    def close(): Unit = reader.close()

  object File:

    type EntryWalkResultPart1 = EntryWalkResultPart1.T
    object EntryWalkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryWalkDepth = EntryWalkDepth.T
    object EntryWalkDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ReadResult = ReadResult.T
    object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileResultPart2 = TileResultPart2.T
    object TileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Where = Where.T
    object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLeaves = MaxLeaves.T
    object MaxLeaves extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ReadOffset = ReadOffset.T
    object ReadOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadLength = ReadLength.T
    object ReadLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeafResultPart1 = LeafResultPart1.T
    object LeafResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryResultPart1 = EntryResultPart1.T
    object EntryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryZ = EntryZ.T
    object EntryZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryX = EntryX.T
    object EntryX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryY = EntryY.T
    object EntryY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileResultPart1 = TileResultPart1.T
    object TileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileZ = TileZ.T
    object TileZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileX = TileX.T
    object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileY = TileY.T
    object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SizeResult = SizeResult.T
    object SizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenResultPart1 = OpenResultPart1.T
    object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenMaxLeaves = OpenMaxLeaves.T
    object OpenMaxLeaves extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MetadataResultPart1 = MetadataResultPart1.T
    object MetadataResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def open(blob: Blob, maxLeaves: com.histmersion.util.basemap.PmTiles.File.OpenMaxLeaves = 256): Either[com.histmersion.util.basemap.PmTiles.File.OpenResultPart1, File] =
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
              yield new File(where, reader, h, root, meta, maxLeaves)
            catch case e: java.io.IOException => Left(s"$where: ${e.getMessage}")
          if read.isLeft then reader.close()
          read
        catch case e: java.io.IOException => Left(s"$where: ${e.getMessage}")
      opened.left.map(e => if e.startsWith(where) then e else s"$where: $e")

    private def metadata(reader: RandomReader, h: PmTiles.Header): Either[com.histmersion.util.basemap.PmTiles.File.MetadataResultPart1, Json] =
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
          .flatMap(b =>
            io.circe.parser.parse(String(b, "UTF-8")).left.map(e => s"metadata is not JSON: ${e.getMessage}")
          )
