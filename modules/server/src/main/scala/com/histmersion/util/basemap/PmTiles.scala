package com.histmersion
package util.basemap
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Using
import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.util.storage.{Blob, RandomReader}
import io.circe.Json
import java.util.concurrent.ConcurrentHashMap
import io.github.iltotore.iron.constraint.any.Pure

/** The PMTiles v3 archive format (https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md): one file holding a
  * tile pyramid, addressed by a Hilbert-curve tile id, readable with HTTP range requests. The terrain base map's two
  * layers are stored in it (docs/research-terrain-basemap.md).
  *
  * Everything here is a PURE transformation of bytes — the header, the varint directories, the tile id — so the reader
  * ([[PmTilesFile]]), the writer ([[PmTilesWriter]]) and the remote extract ([[BasemapFetch]]) share one tested codec.
  */
object PmTiles:
  type ByteLength = ByteLength.T
  object ByteLength extends Newtype[Int, Pure]
  type CompressedBytes = CompressedBytes.T
  object CompressedBytes extends Newtype[Array[Byte], Pure]
  type HeaderBytes = HeaderBytes.T
  object HeaderBytes extends Newtype[Array[Byte], Pure]
  type PlainBytes = PlainBytes.T
  object PlainBytes extends Newtype[Array[Byte], Pure]
  type TileBytes = TileBytes.T
  object TileBytes extends Newtype[Array[Byte], Pure]
  type TileColumn = TileColumn.T
  object TileColumn extends Newtype[Long, Pure]
  type TileRow = TileRow.T
  object TileRow extends Newtype[Long, Pure]
  type Zoom = Zoom.T
  object Zoom extends Newtype[Int, Pure]

  /** A header's length in bytes. */
  val HeaderLength = 127

  /** The root directory must fit, with the header, in the first 16 KiB: one request reads both. */
  val RootBytes = 16384

  enum Compression(val code: Compression.Code) derives CanEqual:
    case Unknown extends Compression(Compression.Code(0))
    case None extends Compression(Compression.Code(1))
    case Gzip extends Compression(Compression.Code(2))
    case Brotli extends Compression(Compression.Code(3))
    case Zstd extends Compression(Compression.Code(4))

  object Compression:
    type Code = Code.T
    object Code extends Newtype[Int, Pure]

    def of(code: Code): Compression = values.find(_.code == code).getOrElse(Unknown)

  enum TileType(val code: TileType.Code, val mime: TileType.Mime) derives CanEqual:
    case Unknown extends TileType(TileType.Code(0), TileType.Mime("application/octet-stream"))
    case Mvt extends TileType(TileType.Code(1), TileType.Mime("application/vnd.mapbox-vector-tile"))
    case Png extends TileType(TileType.Code(2), TileType.Mime("image/png"))
    case Jpeg extends TileType(TileType.Code(3), TileType.Mime("image/jpeg"))
    case Webp extends TileType(TileType.Code(4), TileType.Mime("image/webp"))
    case Avif extends TileType(TileType.Code(5), TileType.Mime("image/avif"))

  object TileType:
    type Code = Code.T
    object Code extends Newtype[Int, Pure]
    type Mime = Mime.T
    object Mime extends Newtype[String, Pure]

    def of(code: Code): TileType = values.find(_.code == code).getOrElse(Unknown)

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
    type RootOffset = ByteOffset
    val RootOffset: ByteOffset.type = ByteOffset
    type RootLength = ByteCount
    val RootLength: ByteCount.type = ByteCount
    type MetadataOffset = ByteOffset
    val MetadataOffset: ByteOffset.type = ByteOffset
    type MetadataLength = ByteCount
    val MetadataLength: ByteCount.type = ByteCount
    type LeafOffset = ByteOffset
    val LeafOffset: ByteOffset.type = ByteOffset
    type LeafLength = ByteCount
    val LeafLength: ByteCount.type = ByteCount
    type DataOffset = ByteOffset
    val DataOffset: ByteOffset.type = ByteOffset
    type DataLength = ByteCount
    val DataLength: ByteCount.type = ByteCount
    type AddressedTiles = AddressedTiles.T
    object AddressedTiles extends Newtype[Long, Pure]
    type TileEntries = TileEntries.T
    object TileEntries extends Newtype[Long, Pure]
    type TileContents = TileContents.T
    object TileContents extends Newtype[Long, Pure]
    type Clustered = Clustered.T
    object Clustered extends Newtype[Boolean, Pure]
    type MinZoom = Zoom
    val MinZoom: Zoom.type = Zoom
    type MaxZoom = Zoom
    val MaxZoom: Zoom.type = Zoom
    type MinLon = MinLon.T
    object MinLon extends Newtype[Double, Pure]
    type MinLat = MinLat.T
    object MinLat extends Newtype[Double, Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends Newtype[Double, Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends Newtype[Double, Pure]
    type CenterZoom = Zoom
    val CenterZoom: Zoom.type = Zoom
    type CenterLon = CenterLon.T
    object CenterLon extends Newtype[Double, Pure]
    type CenterLat = CenterLat.T
    object CenterLat extends Newtype[Double, Pure]

  /** One directory entry: `runLength` consecutive tile ids share the content at `offset`/`length` (relative to the tile
    * data section); `runLength == 0` points to a LEAF directory instead (relative to the leaf section).
    */
  final case class Entry(tileId: Entry.TileId, offset: Entry.Offset, length: Entry.Length, runLength: Entry.RunLength):
    def isLeaf: Boolean = runLength.value == 0

  object Entry:

    type TileId = TileId.T
    object TileId extends Newtype[Long, Pure]
    type Offset = ByteOffset
    val Offset: ByteOffset.type = ByteOffset
    type Length = Length.T
    object Length extends Newtype[Int, Pure]
    type RunLength = RunLength.T
    object RunLength extends Newtype[Int, Pure]

  private val Magic = "PMTiles".getBytes(StandardCharsets.US_ASCII)

  // ── the header ──────────────────────────────────────────────────────────────────────────────────

  def parseHeader(b: HeaderBytes): Either[Problem, Header] =
    val a: Array[Byte] = b.value
    if a.length < HeaderLength then
      Left(Problem.Unclassified(Legacy.Text(s"a PMTiles header is $HeaderLength bytes, got ${a.length}")))
    else if !a.take(7).sameElements(Magic) then
      Left(Problem.Unclassified(Legacy.Text("not a PMTiles archive (no magic number)")))
    else if a(7) != 3 then
      Left(Problem.Unclassified(Legacy.Text(s"PMTiles spec version ${a(7)} is not supported (only 3)")))
    else
      val bb = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN)
      def l(at: Int) = bb.getLong(at)
      def e7(at: Int) = bb.getInt(at) / 1e7
      def u8(at: Int) = a(at) & 0xff
      def offset(at: Int): Either[Problem, ByteOffset] =
        ByteOffset
          .either(l(at))
          .left
          .map(_ => Problem.Unclassified(Legacy.Text(s"a PMTiles header holds a negative offset at byte $at")))
      def count(at: Int): Either[Problem, ByteCount] =
        ByteCount
          .either(l(at))
          .left
          .map(_ => Problem.Unclassified(Legacy.Text(s"a PMTiles header holds a negative length at byte $at")))
      for
        rootOffset <- offset(8)
        rootLength <- count(16)
        metadataOffset <- offset(24)
        metadataLength <- count(32)
        leafOffset <- offset(40)
        leafLength <- count(48)
        dataOffset <- offset(56)
        dataLength <- count(64)
      yield Header(
        rootOffset,
        rootLength,
        metadataOffset,
        metadataLength,
        leafOffset,
        leafLength,
        dataOffset,
        dataLength,
        Header.AddressedTiles(l(72)),
        Header.TileEntries(l(80)),
        Header.TileContents(l(88)),
        Header.Clustered(u8(96) == 1),
        Compression.of(Compression.Code(u8(97))),
        Compression.of(Compression.Code(u8(98))),
        TileType.of(TileType.Code(u8(99))),
        Header.MinZoom(u8(100)),
        Header.MaxZoom(u8(101)),
        Header.MinLon(e7(102)),
        Header.MinLat(e7(106)),
        Header.MaxLon(e7(110)),
        Header.MaxLat(e7(114)),
        Header.CenterZoom(u8(118)),
        Header.CenterLon(e7(119)),
        Header.CenterLat(e7(123))
      )

  def encodeHeader(h: Header): HeaderBytes =
    val bb = ByteBuffer.allocate(HeaderLength).order(ByteOrder.LITTLE_ENDIAN)
    bb.put(Magic).put(3.toByte)
    List[Long](
      h.rootOffset.value,
      h.rootLength.value,
      h.metadataOffset.value,
      h.metadataLength.value,
      h.leafOffset.value,
      h.leafLength.value,
      h.dataOffset.value,
      h.dataLength.value,
      h.addressedTiles.value,
      h.tileEntries.value,
      h.tileContents.value
    ).foreach(bb.putLong)
    bb.put((if h.clustered.value then 1 else 0).toByte)
      .put(h.internalCompression.code.value.toByte)
      .put(h.tileCompression.code.value.toByte)
      .put(h.tileType.code.value.toByte)
      .put(h.minZoom.value.toByte)
      .put(h.maxZoom.value.toByte)
    def e7(d: Double) = bb.putInt(math.round(d * 1e7).toInt)
    e7(h.minLon.value); e7(h.minLat.value); e7(h.maxLon.value); e7(h.maxLat.value)
    bb.put(h.centerZoom.value.toByte)
    e7(h.centerLon.value); e7(h.centerLat.value)
    HeaderBytes(bb.array())

  // ── tile ids ────────────────────────────────────────────────────────────────────────────────────

  /** The first tile id of zoom `z`: the number of tiles on all zooms below it, (4^z − 1) / 3. */
  def zoomBase(z: Zoom): Entry.TileId = Entry.TileId(((1L << (2 * z.value)) - 1) / 3)

  /** The tile id of `z/x/y`: [[zoomBase]] plus the tile's position on the zoom's Hilbert curve. */
  def tileId(z: Zoom, x: TileColumn, y: TileRow): Entry.TileId =
    val zv: Int = z.value
    val xv: Long = x.value
    val yv: Long = y.value
    require(zv >= 0 && zv <= 26, s"zoom $zv out of range")
    val n = 1L << zv
    require(xv >= 0 && xv < n && yv >= 0 && yv < n, s"tile $zv/$xv/$yv out of range")
    // `var`s: the curve is walked bit by bit, rotating the remaining coordinates as it goes
    var tx = xv
    var ty = yv
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
    Entry.TileId(zoomBase(z).value + d)

  /** The inverse of [[tileId]]: `(z, x, y)`. */
  def zxy(id: Entry.TileId): (Zoom, TileColumn, TileRow) =
    val idv: Long = id.value
    var z = 0 // the zoom whose id range holds `id`
    while zoomBase(Zoom(z + 1)).value <= idv do z += 1
    val n = 1L << z
    var t: Long = idv - zoomBase(Zoom(z)).value // the remaining Hilbert distance
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
    (Zoom(z), TileColumn(tx), TileRow(ty))

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
  def encodeDirectory(entries: IndexedSeq[Entry]): PlainBytes =
    val out = ByteArrayOutputStream()
    writeVarint(out, entries.size.toLong)
    var last = 0L // the previous tile id: ids are stored as deltas
    entries.foreach { e => writeVarint(out, e.tileId.value - last); last = e.tileId.value }
    entries.foreach(e => writeVarint(out, e.runLength.value.toLong))
    entries.foreach(e => writeVarint(out, e.length.value.toLong))
    entries.indices.foreach { i =>
      val e = entries(i)
      if i > 0 && e.offset.value == entries(i - 1).offset.value + entries(i - 1).length.value then writeVarint(out, 0)
      else writeVarint(out, e.offset.value + 1)
    }
    PlainBytes(out.toByteArray)

  def decodeDirectory(b: PlainBytes): Either[Problem, Vector[Entry]] =
    try
      val v = Varints(b.value)
      val n = v.next()
      if n < 0 || n > 50_000_000 then Left(Problem.Unclassified(Legacy.Text(s"implausible PMTiles directory size $n")))
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
        val negative = offs.indexWhere(_ < 0)
        if negative >= 0 then
          Left(Problem.Unclassified(Legacy.Text(s"PMTiles directory entry $negative has a negative offset")))
        else
          Right(
            Vector.tabulate(k)(i =>
              Entry(
                Entry.TileId(ids(i)),
                ByteOffset.applyUnsafe(offs(i)),
                Entry.Length(lens(i)),
                Entry.RunLength(runs(i))
              )
            )
          )
    catch case e: IllegalArgumentException => Left(Problem.Unclassified(Legacy.Text(e.getMessage)))

  /** The entry that holds `id` in one sorted directory: the last whose id is ≤ `id` — a leaf pointer (to look further
    * in) or a run that covers it. `None`: not in this directory.
    */
  def find(entries: IndexedSeq[Entry], id: Entry.TileId): Option[Entry] =
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
      if e.isLeaf || id.value < e.tileId.value + e.runLength.value then Some(e) else None

  /** Where entry `e` lies in the file, in the section that starts at `section`: the offset to read from and the number
    * of bytes.
    */
  def range(section: ByteOffset, e: Entry): Either[Problem, (ByteOffset, ByteCount)] =
    for
      at <- ByteOffset
        .either(section.value + e.offset.value)
        .left
        .map(_ => Problem.Unclassified(Legacy.Text(s"PMTiles entry offset ${e.offset.value} lies outside its file")))
      n <- ByteCount
        .either(e.length.value.toLong)
        .left
        .map(_ => Problem.Unclassified(Legacy.Text(s"PMTiles entry length ${e.length.value} is negative")))
    yield (at, n)

  // ── compression ─────────────────────────────────────────────────────────────────────────────────

  def gzip(b: PlainBytes): CompressedBytes =
    val out = ByteArrayOutputStream()
    Using.resource(GZIPOutputStream(out))(_.write(b.value: Array[Byte]))
    CompressedBytes(out.toByteArray)

  def gunzip(b: CompressedBytes): Either[Problem, PlainBytes] =
    try Right(PlainBytes(Using.resource(GZIPInputStream(ByteArrayInputStream(b.value: Array[Byte])))(_.readAllBytes())))
    catch case e: java.io.IOException => Left(Problem.Unclassified(Legacy.Text(s"corrupt gzip data: ${e.getMessage}")))

  def isGzip(b: TileBytes): Boolean =
    val a: Array[Byte] = b.value
    a.length >= 2 && (a(0) & 0xff) == 0x1f && (a(1) & 0xff) == 0x8b

  def decompress(b: CompressedBytes, c: Compression): Either[Problem, PlainBytes] = c match
    case Compression.None | Compression.Unknown => Right(PlainBytes(b.value))
    case Compression.Gzip                       => gunzip(b)
    case other                                  =>
      Left(
        Problem.Unclassified(
          Legacy.Text(s"PMTiles ${other.toString.toLowerCase} compression is not supported (only gzip)")
        )
      )

  def compress(b: PlainBytes, c: Compression): CompressedBytes = c match
    case Compression.Gzip => gzip(b)
    case _                => CompressedBytes(b.value)

  /** A PMTiles archive in a store, opened once and read by many requests at a time: positional reads on one
    * [[RandomReader]] are thread-safe (one interrupted read fails alone), and the leaf directories already read are
    * kept (bounded by `maxLeaves`) so a pan never re-reads the same directory.
    */
  final class File private (
      /** Where the archive is, for messages. */
      val where: File.Where,
      reader: RandomReader,
      val header: Header,
      root: Vector[Entry],
      val metadata: Json,
      maxLeaves: File.MaxLeaves
  ) extends AutoCloseable:

    private val leaves = ConcurrentHashMap[Long, Vector[Entry]]()

    private def read(at: ByteOffset, e: Entry): Array[Byte] = reader.read(at, ByteLength(e.length.value)).value

    private def leaf(e: Entry): Either[Problem, Vector[Entry]] =
      Option(leaves.get(e.offset.value)).map(Right(_)).getOrElse {
        range(header.leafOffset, e)
          .flatMap((at, _) => decompress(CompressedBytes(read(at, e)), header.internalCompression))
          .flatMap(decodeDirectory)
          .map { d =>
            if leaves.size >= maxLeaves.value then leaves.clear()
            leaves.put(e.offset.value, d)
            d
          }
      }

    /** The entry for `z/x/y`, walking leaf directories (at most four levels, as the spec allows). */
    def entry(z: Zoom, x: TileColumn, y: TileRow): Either[Problem, Option[Entry]] =
      val zoom: Int = z.value
      if zoom < header.minZoom.value || zoom > header.maxZoom.value || x.value < 0 || y.value < 0 || x.value >= (1L << zoom) || y.value >= (1L << zoom)
      then Right(None)
      else
        val id = tileId(z, x, y)
        def walk(dir: Vector[Entry], depth: Int): Either[Problem, Option[Entry]] =
          find(dir, id) match
            case None                  => Right(None)
            case Some(e) if !e.isLeaf  => Right(Some(e))
            case Some(_) if depth >= 3 =>
              Left(Problem.Unclassified(Legacy.Text(s"${where.value}: directories nested too deep")))
            case Some(e) => leaf(e).flatMap(walk(_, depth + 1))
        walk(root, 0)

    /** The stored bytes of `z/x/y` (still compressed as [[PmTiles.Header.tileCompression]] says), or `None` when the
      * archive has no such tile.
      */
    def tile(z: Zoom, x: TileColumn, y: TileRow): Either[Problem, Option[TileBytes]] =
      entry(z, x, y).flatMap {
        case None    => Right(None)
        case Some(e) => range(header.dataOffset, e).map((at, _) => Some(TileBytes(read(at, e))))
      }

    def size: ByteCount = reader.size

    def close(): Unit = reader.close()

  object File:
    type Where = Blob.Location
    val Where: Blob.Location.type = Blob.Location
    type MaxLeaves = MaxLeaves.T
    object MaxLeaves extends Newtype[Int, Pure]

    def open(blob: Blob, maxLeaves: MaxLeaves = MaxLeaves(256)): Either[Problem, File] =
      val where = blob.where
      val opened: Either[Problem, File] =
        try
          val reader = blob.reader()
          val read: Either[Problem, File] =
            try
              val bytes: Array[Byte] = reader.readUpTo(ByteOffset.applyUnsafe(0L), ByteLength(RootBytes)).value
              for
                h <- parseHeader(HeaderBytes(bytes))
                _ <- Either.cond(
                  h.rootOffset.value + h.rootLength.value <= bytes.length,
                  (),
                  Problem.Unclassified(Legacy.Text("root directory outside the first 16 KiB"))
                )
                root <- decompress(
                  CompressedBytes(
                    bytes.slice(h.rootOffset.value.toInt, (h.rootOffset.value + h.rootLength.value).toInt)
                  ),
                  h.internalCompression
                )
                  .flatMap(decodeDirectory)
                meta <- metadata(reader, h)
              yield new File(where, reader, h, root, meta, maxLeaves)
            catch case e: java.io.IOException => Left(Problem.Unclassified(Legacy.Text(s"${e.getMessage}")))
          if read.isLeft then reader.close()
          read
        catch case e: java.io.IOException => Left(Problem.Unclassified(Legacy.Text(s"${e.getMessage}")))
      opened.left.map(e => Problem.Unclassified(Legacy.Text(s"${where.value}: ${e.sentence}")))

    private def metadata(reader: RandomReader, h: Header): Either[Problem, Json] =
      if h.metadataLength.value <= 0 then Right(Json.obj())
      else if h.metadataLength.value > 16L * 1024 * 1024 then
        Left(Problem.Unclassified(Legacy.Text("metadata over 16 MiB")))
      else
        // a file that ends early leaves the rest zeros, as a buffer read up to the end does
        val length = h.metadataLength.value.toInt
        val bytes = java.util.Arrays.copyOf(
          (reader.readUpTo(h.metadataOffset, ByteLength(length)).value: Array[Byte]),
          length
        )
        decompress(CompressedBytes(bytes), h.internalCompression)
          .flatMap(b =>
            io.circe.parser
              .parse(String(b.value: Array[Byte], "UTF-8"))
              .left
              .map(e => Problem.Unclassified(Legacy.Text(s"metadata is not JSON: ${e.getMessage}")))
          )
