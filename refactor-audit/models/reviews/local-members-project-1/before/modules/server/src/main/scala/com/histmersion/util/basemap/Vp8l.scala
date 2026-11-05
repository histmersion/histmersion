package com.histmersion.util.basemap

/** A decoder of LOSSLESS WebP (the `VP8L` bitstream, RFC 9649) to ARGB pixels — the format the relief's terrarium tiles
  * are stored in (Mapterhorn's 512-pixel tiles, a focus region's, the on-demand cache's). The server reads elevation
  * out of them to answer a relief tile deeper than any it holds ([[TerrainResample]]); `javax.imageio` has no WebP
  * reader (and is a native-image liability), so this is pure Scala over the bytes: no AWT, no reflection, no native
  * code.
  *
  * Pure: bytes in, pixels out (`Left` with the reason for anything it does not read: a lossy `VP8 ` tile, a corrupt
  * stream). Only what the format defines is implemented: the four transforms (predictor, colour, subtract-green,
  * colour-indexing), the colour cache, meta prefix codes and LZ77 backward references.
  */
object Vp8l:

  /** A decoded image: `pixels` row-major, each `0xAARRGGBB`. */
  type DecodeBytes = DecodeBytes.T
  object DecodeBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vp8lChunkB = Vp8lChunkB.T
  object Vp8lChunkB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildLengths = BuildLengths.T
  object BuildLengths extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceMap = DistanceMap.T
  object DistanceMap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictPx = PredictPx.T
  object PredictPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColourPx = ColourPx.T
  object ColourPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnindexResult = UnindexResult.T
  object UnindexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnindexPx = UnindexPx.T
  object UnindexPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DecodeResultPart1 = DecodeResultPart1.T
  object DecodeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailMsg = FailMsg.T
  object FailMsg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vp8lChunkResultPart1 = Vp8lChunkResultPart1.T
  object Vp8lChunkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vp8lChunkResultPart2Part1 = Vp8lChunkResultPart2Part1.T
  object Vp8lChunkResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vp8lChunkResultPart2Part2 = Vp8lChunkResultPart2Part2.T
  object Vp8lChunkResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReverseResult = ReverseResult.T
  object ReverseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReverseCode = ReverseCode.T
  object ReverseCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReverseLen = ReverseLen.T
  object ReverseLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildResultPart1 = BuildResultPart1.T
  object BuildResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceResult = DistanceResult.T
  object DistanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceCode = DistanceCode.T
  object DistanceCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceWidth = DistanceWidth.T
  object DistanceWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddPixelsResult = AddPixelsResult.T
  object AddPixelsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddPixelsA = AddPixelsA.T
  object AddPixelsA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddPixelsB = AddPixelsB.T
  object AddPixelsB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Average2Result = Average2Result.T
  object Average2Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Average2A = Average2A.T
  object Average2A extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Average2B = Average2B.T
  object Average2B extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChResult = ChResult.T
  object ChResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChP = ChP.T
  object ChP extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChS = ChS.T
  object ChS extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampResult = ClampResult.T
  object ClampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampV = ClampV.T
  object ClampV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectResult = SelectResult.T
  object SelectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectL = SelectL.T
  object SelectL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectT = SelectT.T
  object SelectT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectTl = SelectTl.T
  object SelectTl extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampFullResult = ClampFullResult.T
  object ClampFullResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampFullA = ClampFullA.T
  object ClampFullA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampFullB = ClampFullB.T
  object ClampFullB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampFullC = ClampFullC.T
  object ClampFullC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampHalfResult = ClampHalfResult.T
  object ClampHalfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampHalfA = ClampHalfA.T
  object ClampHalfA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampHalfB = ClampHalfB.T
  object ClampHalfB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorResult = PredictorResult.T
  object PredictorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorM = PredictorM.T
  object PredictorM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorL = PredictorL.T
  object PredictorL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorT = PredictorT.T
  object PredictorT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorTr = PredictorTr.T
  object PredictorTr extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictorTl = PredictorTl.T
  object PredictorTl extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PredictH = PredictH.T
  object PredictH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeltaResult = DeltaResult.T
  object DeltaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeltaT = DeltaT.T
  object DeltaT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeltaC = DeltaC.T
  object DeltaC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColourH = ColourH.T
  object ColourH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnindexH = UnindexH.T
  object UnindexH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Image(width: Image.Width, height: Image.Height, pixels: Image.Pixels)

  object Image:
    type Pixels = Pixels.T
    object Pixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Width = Width.T
    object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Height = Height.T
    object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A WebP file (`RIFF … WEBP VP8L …`) or a bare `VP8L` bitstream, decoded. */
  def decode(bytes: com.histmersion.util.basemap.Vp8l.DecodeBytes): Either[com.histmersion.util.basemap.Vp8l.DecodeResultPart1, Image] =
    vp8lChunk(bytes).flatMap { (off, len) =>
      try Right(Decoder(bytes, off, off + len).image())
      catch
        case e: Vp8lError                      => Left(e.getMessage)
        case e: ArrayIndexOutOfBoundsException => Left(s"corrupt VP8L stream (${e.getMessage})")
    }

  final private class Vp8lError(msg: Vp8lError.Msg) extends Exception(msg)

  object Vp8lError:
    type Msg = Msg.T
    object Msg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def fail(msg: com.histmersion.util.basemap.Vp8l.FailMsg): Nothing = throw Vp8lError(msg)

  /** Where the `VP8L` payload is: inside a RIFF container (its chunk, after any `VP8X`), or the whole input when it
    * starts with the VP8L signature. Pure.
    */
  private[basemap] def vp8lChunk(b: com.histmersion.util.basemap.Vp8l.Vp8lChunkB): Either[com.histmersion.util.basemap.Vp8l.Vp8lChunkResultPart1, (com.histmersion.util.basemap.Vp8l.Vp8lChunkResultPart2Part1, com.histmersion.util.basemap.Vp8l.Vp8lChunkResultPart2Part2)] =
    def le32(i: Int) = (b(i) & 0xff) | (b(i + 1) & 0xff) << 8 | (b(i + 2) & 0xff) << 16 | (b(i + 3) & 0xff) << 24
    def tag(i: Int) = new String(b, i, 4, "US-ASCII")
    if b.length >= 5 && (b(0) & 0xff) == 0x2f then Right((0, b.length))
    else if b.length < 20 || tag(0) != "RIFF" || tag(8) != "WEBP" then Left("not a WebP file")
    else
      // the chunks after the header: the first VP8L one is the image
      var i = 12
      var found: Either[String, (Int, Int)] = Left("no VP8L chunk: only lossless WebP is read")
      var done = false
      while !done && i + 8 <= b.length do
        val t = tag(i)
        val n = le32(i + 4)
        if n < 0 || i + 8 + n > b.length then { found = Left("truncated WebP chunk"); done = true }
        else if t == "VP8L" then { found = Right((i + 8, n)); done = true }
        else if t == "VP8 " then { found = Left("a lossy (VP8) WebP: only lossless WebP is read"); done = true }
        else i += 8 + n + (n & 1)
      found

  // ── the bit reader: least significant bit first ──────────────────────────────────────────────

  final private[basemap] class Bits(b: Array[Byte], from: Bits.From, until: Bits.Until):
    // `var`s: the reader's buffer, its valid bits and the next byte — a hot loop's state
    private var buf: com.histmersion.util.basemap.Vp8l.Bits.Buf = 0L
    private var nbits: com.histmersion.util.basemap.Vp8l.Bits.Nbits = 0
    private var pos: com.histmersion.util.basemap.Vp8l.Bits.Pos = from

    private def fill(): Unit =
      while nbits <= 56 do
        val byte = if pos < until then b(pos) & 0xff else 0
        pos += 1
        buf |= byte.toLong << nbits
        nbits += 8

    /** The next `n` (≤ 32) bits without consuming them. */
    def peek(n: com.histmersion.util.basemap.Vp8l.Bits.PeekN): com.histmersion.util.basemap.Vp8l.Bits.PeekResult =
      if nbits < n then fill()
      (buf & ((1L << n) - 1)).toInt

    def skip(n: com.histmersion.util.basemap.Vp8l.Bits.SkipN): Unit =
      buf >>>= n
      nbits -= n

    def read(n: com.histmersion.util.basemap.Vp8l.Bits.ReadN): com.histmersion.util.basemap.Vp8l.Bits.ReadResult =
      if n == 0 then 0
      else
        val v = peek(n)
        skip(n)
        v

    /** Past the end of the data by more than the reader's look-ahead: the stream is corrupt. */
    def overrun: com.histmersion.util.basemap.Vp8l.Bits.OverrunResult = pos - 8 > until

  object Bits:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Buf = Buf.T
    object Buf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Nbits = Nbits.T
    object Nbits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pos = Pos.T
    object Pos extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeekResult = PeekResult.T
    object PeekResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeekN = PeekN.T
    object PeekN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SkipN = SkipN.T
    object SkipN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadResult = ReadResult.T
    object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadN = ReadN.T
    object ReadN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OverrunResult = OverrunResult.T
    object OverrunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── prefix (Huffman) codes ───────────────────────────────────────────────────────────────────

  /** A canonical prefix code as a two-level lookup over the next bits (LSB first, codes bit-reversed as in deflate):
    * `root` holds `symbol << 4 | length` for codes of up to [[Root]] bits, or a negative index into `subs` whose table
    * is read with the bits after the root ones. A code of one symbol takes no bits.
    */
  final private[basemap] class Code(root: Array[Int], subs: Array[Array[Int]], subBits: Array[Int], val single: Code.Single):
    def read(r: Bits): com.histmersion.util.basemap.Vp8l.Code.ReadResult =
      if single >= 0 then single
      else
        val p = r.peek(15)
        val e = root(p & RootMask)
        if e >= 0 then
          r.skip(e & 0xf)
          e >>> 4
        else
          val k = -e - 1
          val s = subs(k)((p >>> Root) & ((1 << subBits(k)) - 1))
          if s < 0 then fail("invalid prefix code")
          r.skip(s & 0xf)
          s >>> 4

  object Code:
    type Single = Single.T
    object Single extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ReadResult = ReadResult.T
    object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Root = 8
  private val RootMask = (1 << Root) - 1

  /** The bits of `code` (`len` long) reversed. */
  private def reverse(code: com.histmersion.util.basemap.Vp8l.ReverseCode, len: com.histmersion.util.basemap.Vp8l.ReverseLen): com.histmersion.util.basemap.Vp8l.ReverseResult =
    var c = code
    var r = 0
    var i = 0
    while i < len do
      r = (r << 1) | (c & 1)
      c >>= 1
      i += 1
    r

  /** The canonical code of `lengths` (0: the symbol is not used). Pure. */
  private[basemap] def build(lengths: com.histmersion.util.basemap.Vp8l.BuildLengths): Either[com.histmersion.util.basemap.Vp8l.BuildResultPart1, Code] =
    val used = lengths.indices.filter(lengths(_) > 0)
    if used.isEmpty then Right(Code(Array.empty, Array.empty, Array.empty, single = 0))
    else if used.size == 1 then Right(Code(Array.empty, Array.empty, Array.empty, single = used.head))
    else
      val maxLen = used.map(lengths(_)).max
      if maxLen > 15 then Left("a prefix code longer than 15 bits")
      else
        // canonical codes: by length, then by symbol
        val count = new Array[Int](16)
        used.foreach(s => count(lengths(s)) += 1)
        val next = new Array[Int](16)
        var code = 0
        var l = 1
        while l <= 15 do
          code = (code + count(l - 1)) << 1
          next(l) = code
          l += 1
        // the Kraft sum must be exactly 1 for a complete code
        val kraft = used.map(s => 1L << (15 - lengths(s))).sum
        if kraft != (1L << 15) then Left("an incomplete or over-subscribed prefix code")
        else
          val root = Array.fill(1 << Root)(Int.MinValue)
          val subLen = scala.collection.mutable.HashMap.empty[Int, Int] // root prefix → deepest code under it
          val codes = used.map { s =>
            val len = lengths(s)
            val c = next(len)
            next(len) += 1
            (s, len, reverse(c, len))
          }
          codes.foreach { (_, len, rev) =>
            if len > Root then
              val p = rev & RootMask
              subLen(p) = math.max(subLen.getOrElse(p, 0), len - Root)
          }
          val prefixes = subLen.keys.toArray.sorted
          val subs = prefixes.map(p => Array.fill(1 << subLen(p))(-1))
          val subBits = prefixes.map(subLen(_))
          prefixes.zipWithIndex.foreach((p, k) => root(p) = -k - 1)
          codes.foreach { (s, len, rev) =>
            if len <= Root then
              var i = rev
              while i < (1 << Root) do
                root(i) = s << 4 | len
                i += 1 << len
            else
              val k = prefixes.indexOf(rev & RootMask)
              val bits = subBits(k)
              val rest = len - Root
              var i = rev >>> Root
              while i < (1 << bits) do
                subs(k)(i) = s << 4 | len
                i += 1 << rest
          }
          Right(Code(root, subs, subBits, single = -1))

  private val CodeLengthOrder = Array(17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

  /** The distance codes 1–120: (dx, dy) of the pixel referred to, nearest first (RFC 9649 §4.2.2). */
  private val DistanceMap: com.histmersion.util.basemap.Vp8l.DistanceMap = Array(0, 1, 1, 0, 1, 1, -1, 1, 0, 2, 2, 0, 1, 2, -1, 2, 2, 1, -2, 1, 2, 2, -2,
    2, 0, 3, 3, 0, 1, 3, -1, 3, 3, 1, -3, 1, 2, 3, -2, 3, 3, 2, -3, 2, 0, 4, 4, 0, 1, 4, -1, 4, 4, 1, -4, 1, 3, 3, -3,
    3, 2, 4, -2, 4, 4, 2, -4, 2, 0, 5, 3, 4, -3, 4, 4, 3, -4, 3, 5, 0, 1, 5, -1, 5, 5, 1, -5, 1, 2, 5, -2, 5, 5, 2, -5,
    2, 4, 4, -4, 4, 3, 5, -3, 5, 5, 3, -5, 3, 0, 6, 6, 0, 1, 6, -1, 6, 6, 1, -6, 1, 2, 6, -2, 6, 6, 2, -6, 2, 4, 5, -4,
    5, 5, 4, -5, 4, 3, 6, -3, 6, 6, 3, -6, 3, 0, 7, 7, 0, 1, 7, -1, 7, 5, 5, -5, 5, 7, 1, -7, 1, 4, 6, -4, 6, 6, 4, -6,
    4, 2, 7, -2, 7, 7, 2, -7, 2, 3, 7, -3, 7, 7, 3, -7, 3, 5, 6, -5, 6, 6, 5, -6, 5, 8, 0, 4, 7, -4, 7, 7, 4, -7, 4, 8,
    1, 8, 2, 6, 6, -6, 6, 8, 3, 5, 7, -5, 7, 7, 5, -7, 5, 8, 4, 6, 7, -6, 7, 7, 6, -7, 6, 8, 5, 7, 7, -7, 7, 8, 6, 8, 7)

  /** The distance in pixels of distance code `code` in an image `width` wide. Pure. */
  private[basemap] def distance(code: com.histmersion.util.basemap.Vp8l.DistanceCode, width: com.histmersion.util.basemap.Vp8l.DistanceWidth): com.histmersion.util.basemap.Vp8l.DistanceResult =
    if code > 120 then code - 120
    else
      val d = DistanceMap(2 * (code - 1)) + DistanceMap(2 * (code - 1) + 1) * width
      if d < 1 then 1 else d

  /** One group of prefix codes: green (with lengths and the cache), red, blue, alpha, distance. */
  final private class Group(val green: Code, val red: Code, val blue: Code, val alpha: Code, val dist: Code)

  final private case class Transform(kind: Transform.Kind, width: Transform.Width, bits: Transform.Bits, data: Transform.Data)

  object Transform:
    type Data = Data.T
    object Data extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Width = Width.T
    object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bits = Bits.T
    object Bits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private class Decoder(b: Array[Byte], from: Decoder.From, until: Decoder.Until):
    private val r = Bits(b, from, until)

    def image(): Image =
      if r.read(8) != 0x2f then fail("not a VP8L stream")
      val w = r.read(14) + 1
      val h = r.read(14) + 1
      val _ = r.read(1) // alpha is used: the pixels say so themselves
      if r.read(3) != 0 then fail("an unknown VP8L version")
      // the transforms, in the order they were applied (undone in reverse)
      var width = w
      val transforms = scala.collection.mutable.ListBuffer.empty[Transform]
      var seen = Set.empty[Int]
      while r.read(1) == 1 do
        val kind = r.read(2)
        if seen(kind) then fail("a transform used twice")
        seen += kind
        kind match
          case 0 | 1 =>
            val bits = r.read(3) + 2
            val data = entropyImage(sub(width, bits), sub(h, bits), top = false)
            transforms += Transform(kind, width, bits, data)
          case 2 => transforms += Transform(kind, width, 0, Array.emptyIntArray)
          case _ =>
            val size = r.read(8) + 1
            val table = entropyImage(size, 1, top = false)
            var i = 1
            while i < size do
              table(i) = addPixels(table(i), table(i - 1))
              i += 1
            val bits = if size <= 2 then 3 else if size <= 4 then 2 else if size <= 16 then 1 else 0
            transforms += Transform(kind, width, bits, table)
            width = sub(width, bits)
      var px = entropyImage(width, h, top = true)
      transforms.reverseIterator.foreach(t => px = undo(t, px, h))
      if r.overrun then fail("truncated VP8L stream")
      Image(w, h, px)

    private def sub(size: com.histmersion.util.basemap.Vp8l.Decoder.SubSize, bits: com.histmersion.util.basemap.Vp8l.Decoder.SubBits): com.histmersion.util.basemap.Vp8l.Decoder.SubResult = (size + (1 << bits) - 1) >> bits

    /** An entropy-coded image: the main one (`top`: it may have meta prefix codes) or a transform's or prefix image's.
      */
    private def entropyImage(w: com.histmersion.util.basemap.Vp8l.Decoder.EntropyImageW, h: com.histmersion.util.basemap.Vp8l.Decoder.EntropyImageH, top: com.histmersion.util.basemap.Vp8l.Decoder.EntropyImageTop): com.histmersion.util.basemap.Vp8l.Decoder.EntropyImageResult =
      val cacheBits = if r.read(1) == 1 then
        val c = r.read(4)
        if c < 1 || c > 11 then fail("a colour cache of an invalid size")
        c
      else 0
      val (metaBits, meta) =
        if top && r.read(1) == 1 then
          val bits = r.read(3) + 2
          (bits, entropyImage(sub(w, bits), sub(h, bits), top = false))
        else (0, Array.emptyIntArray)
      val groupCount = if meta.isEmpty then 1 else meta.iterator.map(m => (m >> 8) & 0xffff).max + 1
      val cacheSize = if cacheBits > 0 then 1 << cacheBits else 0
      val groups =
        Array.tabulate(groupCount)(_ => Group(code(256 + 24 + cacheSize), code(256), code(256), code(256), code(40)))
      pixels(w, h, groups, metaBits, meta, cacheBits)

    /** One prefix code of an alphabet of `size` symbols. */
    private def code(size: com.histmersion.util.basemap.Vp8l.Decoder.CodeSize): Code =
      val lengths = new Array[Int](size)
      if r.read(1) == 1 then
        // a simple code: one or two symbols
        val two = r.read(1) == 1
        val first = r.read(if r.read(1) == 1 then 8 else 1)
        if first >= size then fail("a symbol outside its alphabet")
        lengths(first) = 1
        if two then
          val second = r.read(8)
          if second >= size then fail("a symbol outside its alphabet")
          lengths(second) = 1
        else lengths(first) = 0
        if two then build(lengths).fold(fail, identity) else Code(Array.empty, Array.empty, Array.empty, single = first)
      else
        val clLengths = new Array[Int](19)
        val n = r.read(4) + 4
        var i = 0
        while i < n do
          clLengths(CodeLengthOrder(i)) = r.read(3)
          i += 1
        val cl = build(clLengths).fold(fail, identity)
        var max =
          if r.read(1) == 1 then
            val nbits = 2 + 2 * r.read(3)
            val m = 2 + r.read(nbits)
            if m > size then fail("more code lengths than symbols")
            m
          else size
        var s = 0
        var prev = 8
        while s < size && max > 0 do
          max -= 1
          val c = cl.read(r)
          if c < 16 then
            lengths(s) = c
            if c != 0 then prev = c
            s += 1
          else
            val (repeat, value) = c match
              case 16 => (3 + r.read(2), prev)
              case 17 => (3 + r.read(3), 0)
              case _  => (11 + r.read(7), 0)
            if s + repeat > size then fail("code lengths past the alphabet")
            var k = 0
            while k < repeat do
              lengths(s) = value
              s += 1
              k += 1
        build(lengths).fold(fail, identity)

    /** A length or distance from its prefix symbol and its extra bits. */
    private def prefixValue(symbol: com.histmersion.util.basemap.Vp8l.Decoder.PrefixValueSymbol): com.histmersion.util.basemap.Vp8l.Decoder.PrefixValueResult =
      if symbol < 4 then symbol + 1
      else
        val extra = (symbol - 2) >> 1
        val offset = (2 + (symbol & 1)) << extra
        offset + r.read(extra) + 1

    private def pixels(
        w: com.histmersion.util.basemap.Vp8l.Decoder.PixelsW,
        h: com.histmersion.util.basemap.Vp8l.Decoder.PixelsH,
        groups: Array[Group],
        metaBits: com.histmersion.util.basemap.Vp8l.Decoder.PixelsMetaBits,
        meta: com.histmersion.util.basemap.Vp8l.Decoder.PixelsMeta,
        cacheBits: com.histmersion.util.basemap.Vp8l.Decoder.PixelsCacheBits
    ): com.histmersion.util.basemap.Vp8l.Decoder.PixelsResult =
      val out = new Array[Int](w * h)
      val cache = if cacheBits > 0 then new Array[Int](1 << cacheBits) else null
      val cacheShift = 32 - cacheBits
      val metaW = if metaBits > 0 then sub(w, metaBits) else 0
      val total = w * h
      var pos = 0
      var cached = 0 // pixels before this one are in the cache
      def groupAt(p: Int): Group =
        if meta.isEmpty then groups(0)
        else
          val x = p % w
          val y = p / w
          groups((meta((y >> metaBits) * metaW + (x >> metaBits)) >> 8) & 0xffff)
      while pos < total do
        val g = groupAt(pos)
        val s = g.green.read(r)
        if s < 256 then
          val red = g.red.read(r)
          val blue = g.blue.read(r)
          val alpha = g.alpha.read(r)
          out(pos) = alpha << 24 | red << 16 | s << 8 | blue
          pos += 1
        else if s < 256 + 24 then
          val length = prefixValue(s - 256)
          val distCode = prefixValue(g.dist.read(r))
          val d = distance(distCode, w)
          if d > pos || pos + length > total then fail("a backward reference outside the image")
          var k = 0
          while k < length do
            out(pos) = out(pos - d)
            pos += 1
            k += 1
        else
          if cache == null then fail("a colour-cache code without a cache")
          val idx = s - 280
          if idx >= cache.length then fail("a colour-cache index outside the cache")
          out(pos) = cache(idx)
          pos += 1
        if cache != null then
          while cached < pos do
            val c = out(cached)
            cache((0x1e35a7bd * c) >>> cacheShift) = c
            cached += 1
        if r.overrun then fail("truncated VP8L stream")
      out

    /** Undo one transform over pixels `h` rows high. */
    private def undo(t: Transform, px: com.histmersion.util.basemap.Vp8l.Decoder.UndoPx, h: com.histmersion.util.basemap.Vp8l.Decoder.UndoH): com.histmersion.util.basemap.Vp8l.Decoder.UndoResult = t.kind match
      case 0 => predict(t, px, h); px
      case 1 => colour(t, px, h); px
      case 2 =>
        var i = 0
        while i < px.length do
          val p = px(i)
          val g = (p >> 8) & 0xff
          val red = (((p >> 16) & 0xff) + g) & 0xff
          val blue = ((p & 0xff) + g) & 0xff
          px(i) = (p & 0xff00ff00) | red << 16 | blue
          i += 1
        px
      case _ => unindex(t, px, h)

  object Decoder:
    type EntropyImageResult = EntropyImageResult.T
    object EntropyImageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsResult = PixelsResult.T
    object PixelsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsMeta = PixelsMeta.T
    object PixelsMeta extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UndoResult = UndoResult.T
    object UndoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UndoPx = UndoPx.T
    object UndoPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type SubResult = SubResult.T
    object SubResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubSize = SubSize.T
    object SubSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubBits = SubBits.T
    object SubBits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntropyImageW = EntropyImageW.T
    object EntropyImageW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntropyImageH = EntropyImageH.T
    object EntropyImageH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntropyImageTop = EntropyImageTop.T
    object EntropyImageTop extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CodeSize = CodeSize.T
    object CodeSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PrefixValueResult = PrefixValueResult.T
    object PrefixValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PrefixValueSymbol = PrefixValueSymbol.T
    object PrefixValueSymbol extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsW = PixelsW.T
    object PixelsW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsH = PixelsH.T
    object PixelsH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsMetaBits = PixelsMetaBits.T
    object PixelsMetaBits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PixelsCacheBits = PixelsCacheBits.T
    object PixelsCacheBits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UndoH = UndoH.T
    object UndoH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Per channel: `a + b` modulo 256. Pure. */
  private[basemap] def addPixels(a: com.histmersion.util.basemap.Vp8l.AddPixelsA, b: com.histmersion.util.basemap.Vp8l.AddPixelsB): com.histmersion.util.basemap.Vp8l.AddPixelsResult =
    (((a & 0xff00ff00) + (b & 0xff00ff00)) & 0xff00ff00) | (((a & 0x00ff00ff) + (b & 0x00ff00ff)) & 0x00ff00ff)

  /** Per channel: the floor of the mean. Pure. */
  private[basemap] def average2(a: com.histmersion.util.basemap.Vp8l.Average2A, b: com.histmersion.util.basemap.Vp8l.Average2B): com.histmersion.util.basemap.Vp8l.Average2Result =
    (((a ^ b) & 0xfefefefe) >>> 1) + (a & b)

  private def ch(p: com.histmersion.util.basemap.Vp8l.ChP, s: com.histmersion.util.basemap.Vp8l.ChS): com.histmersion.util.basemap.Vp8l.ChResult = (p >>> s) & 0xff

  private def clamp(v: com.histmersion.util.basemap.Vp8l.ClampV): com.histmersion.util.basemap.Vp8l.ClampResult = if v < 0 then 0 else if v > 255 then 255 else v

  private[basemap] def select(l: com.histmersion.util.basemap.Vp8l.SelectL, t: com.histmersion.util.basemap.Vp8l.SelectT, tl: com.histmersion.util.basemap.Vp8l.SelectTl): com.histmersion.util.basemap.Vp8l.SelectResult =
    var pl = 0
    var pt = 0
    var s = 0
    while s < 32 do
      val p = ch(l, s) + ch(t, s) - ch(tl, s)
      pl += math.abs(p - ch(l, s))
      pt += math.abs(p - ch(t, s))
      s += 8
    if pl < pt then l else t

  private[basemap] def clampFull(a: com.histmersion.util.basemap.Vp8l.ClampFullA, b: com.histmersion.util.basemap.Vp8l.ClampFullB, c: com.histmersion.util.basemap.Vp8l.ClampFullC): com.histmersion.util.basemap.Vp8l.ClampFullResult =
    var out = 0
    var s = 0
    while s < 32 do
      out |= clamp(ch(a, s) + ch(b, s) - ch(c, s)) << s
      s += 8
    out

  private[basemap] def clampHalf(a: com.histmersion.util.basemap.Vp8l.ClampHalfA, b: com.histmersion.util.basemap.Vp8l.ClampHalfB): com.histmersion.util.basemap.Vp8l.ClampHalfResult =
    var out = 0
    var s = 0
    while s < 32 do
      val x = ch(a, s)
      out |= clamp(x + (x - ch(b, s)) / 2) << s
      s += 8
    out

  /** The prediction of mode `m` from the left, top, top-right and top-left pixels. Pure. */
  private[basemap] def predictor(m: com.histmersion.util.basemap.Vp8l.PredictorM, l: com.histmersion.util.basemap.Vp8l.PredictorL, t: com.histmersion.util.basemap.Vp8l.PredictorT, tr: com.histmersion.util.basemap.Vp8l.PredictorTr, tl: com.histmersion.util.basemap.Vp8l.PredictorTl): com.histmersion.util.basemap.Vp8l.PredictorResult = m match
    case 0  => 0xff000000
    case 1  => l
    case 2  => t
    case 3  => tr
    case 4  => tl
    case 5  => average2(average2(l, tr), t)
    case 6  => average2(l, tl)
    case 7  => average2(l, t)
    case 8  => average2(tl, t)
    case 9  => average2(t, tr)
    case 10 => average2(average2(l, tl), average2(t, tr))
    case 11 => select(l, t, tl)
    case 12 => clampFull(l, t, tl)
    case 13 => clampHalf(average2(l, t), tl)
    case _  => 0xff000000 // modes 14 and 15 are not used; as 0

  private def predict(t: Transform, px: com.histmersion.util.basemap.Vp8l.PredictPx, h: com.histmersion.util.basemap.Vp8l.PredictH): Unit =
    val w = t.width
    val bw = (w + (1 << t.bits) - 1) >> t.bits
    // the first row: the first pixel from black, the rest from the left
    px(0) = addPixels(px(0), 0xff000000)
    var x = 1
    while x < w do
      px(x) = addPixels(px(x), px(x - 1))
      x += 1
    var y = 1
    while y < h do
      val row = y * w
      px(row) = addPixels(px(row), px(row - w))
      var x = 1
      while x < w do
        val i = row + x
        val m = (t.data((y >> t.bits) * bw + (x >> t.bits)) >> 8) & 0xf
        // the rightmost pixel's top-right is the row's first pixel: `i - w + 1` is that, row-major
        px(i) = addPixels(px(i), predictor(m, px(i - 1), px(i - w), px(i - w + 1), px(i - w - 1)))
        x += 1
      y += 1

  private def delta(t: com.histmersion.util.basemap.Vp8l.DeltaT, c: com.histmersion.util.basemap.Vp8l.DeltaC): com.histmersion.util.basemap.Vp8l.DeltaResult = (t.toByte * c.toByte) >> 5

  private def colour(t: Transform, px: com.histmersion.util.basemap.Vp8l.ColourPx, h: com.histmersion.util.basemap.Vp8l.ColourH): Unit =
    val w = t.width
    val bw = (w + (1 << t.bits) - 1) >> t.bits
    var y = 0
    while y < h do
      var x = 0
      while x < w do
        val e = t.data((y >> t.bits) * bw + (x >> t.bits))
        val greenToRed = e & 0xff
        val greenToBlue = (e >> 8) & 0xff
        val redToBlue = (e >> 16) & 0xff
        val i = y * w + x
        val p = px(i)
        val g = (p >> 8) & 0xff
        val red = (((p >> 16) & 0xff) + delta(greenToRed, g)) & 0xff
        val blue = ((p & 0xff) + delta(greenToBlue, g) + delta(redToBlue, red)) & 0xff
        px(i) = (p & 0xff00ff00) | red << 16 | blue
        x += 1
      y += 1

  private def unindex(t: Transform, px: com.histmersion.util.basemap.Vp8l.UnindexPx, h: com.histmersion.util.basemap.Vp8l.UnindexH): com.histmersion.util.basemap.Vp8l.UnindexResult =
    val w = t.width
    val packed = (w + (1 << t.bits) - 1) >> t.bits
    val out = new Array[Int](w * h)
    val perPixel = 8 >> t.bits
    val mask = (1 << perPixel) - 1
    var y = 0
    while y < h do
      var x = 0
      while x < w do
        val g = (px(y * packed + (x >> t.bits)) >> 8) & 0xff
        val idx = (g >> ((x & ((1 << t.bits) - 1)) * perPixel)) & mask
        out(y * w + x) = if idx < t.data.length then t.data(idx) else 0
        x += 1
      y += 1
    out
