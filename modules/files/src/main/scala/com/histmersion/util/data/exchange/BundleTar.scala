package com.histmersion.util.data.exchange

import com.histmersion.api.given

import java.io.{EOFException, FilterInputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets.{US_ASCII, UTF_8}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

/** The container of a `.hmx` bundle: a POSIX ustar archive, gzip-compressed — written and read as a STREAM, so 1.6 GB
  * of assets never sit in memory, and DETERMINISTIC, so two exports of an unchanged database are byte-identical: every
  * header has mode 0644, owner 0/0, mtime 0 and no user names, entries come in the order the writer is given them, and
  * gzip's own header carries no timestamp (`GZIPOutputStream` writes MTIME 0).
  *
  * Hand-written rather than a dependency: ustar is 512-byte blocks of fixed-width ASCII fields, and a new library would
  * bring its own native-image metadata. Only what a bundle needs is supported — regular files, names up to 100 bytes (a
  * blob path is 80), sizes under 8 GiB.
  *
  * zstd (which the design named) would need a JNI library; gzip is in the JDK.
  */
object BundleTar:

  type HeaderResultPart2 = HeaderResultPart2.T
  object HeaderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseHeaderH = ParseHeaderH.T
  object ParseHeaderH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxSize = MaxSize.T
  object MaxSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderResultPart1 = HeaderResultPart1.T
  object HeaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderSize = HeaderSize.T
  object HeaderSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseHeaderResultPart1 = ParseHeaderResultPart1.T
  object ParseHeaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaddingResult = PaddingResult.T
  object PaddingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaddingSize = PaddingSize.T
  object PaddingSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Block = 512
  val MaxName = 100

  /** 11 octal digits. */
  val MaxSize: com.histmersion.util.data.exchange.BundleTar.MaxSize = (1L << 33) - 1

  /** One ustar header for a regular file. Pure. */
  def header(name: com.histmersion.util.data.exchange.BundleTar.HeaderName, size: com.histmersion.util.data.exchange.BundleTar.HeaderSize): Either[com.histmersion.util.data.exchange.BundleTar.HeaderResultPart1, com.histmersion.util.data.exchange.BundleTar.HeaderResultPart2] =
    val nameBytes = name.getBytes(UTF_8)
    if nameBytes.length > MaxName then Left(s"$name: name longer than $MaxName bytes")
    else if size < 0 || size > MaxSize then Left(s"$name: size $size is outside 0..$MaxSize")
    else
      val h = new Array[Byte](Block)
      def put(off: Int, s: String): Unit =
        val b = s.getBytes(US_ASCII)
        System.arraycopy(b, 0, h, off, b.length)
      System.arraycopy(nameBytes, 0, h, 0, nameBytes.length)
      put(100, "0000644\u0000") // mode
      put(108, "0000000\u0000") // uid
      put(116, "0000000\u0000") // gid
      put(124, f"$size%011o\u0000") // size
      put(136, "00000000000\u0000") // mtime
      put(148, "        ") // checksum, as spaces while it is summed
      h(156) = '0'.toByte // typeflag: regular file
      put(257, "ustar\u0000") // magic
      put(263, "00") // version
      val sum = h.foldLeft(0)((acc, b) => acc + (b & 0xff))
      put(148, f"$sum%06o\u0000 ")
      Right(h)

  final case class Entry(name: Entry.Name, size: Entry.Size)

  object Entry:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Size = Size.T
    object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Parse a header block. `Right(None)` is the end-of-archive (an all-zero block). Pure. */
  def parseHeader(h: com.histmersion.util.data.exchange.BundleTar.ParseHeaderH): Either[com.histmersion.util.data.exchange.BundleTar.ParseHeaderResultPart1, Option[Entry]] =
    if h.length != Block then Left(s"short header (${h.length} bytes)")
    else if h.forall(_ == 0) then Right(None)
    else
      def field(off: Int, len: Int): String =
        String(h.slice(off, off + len).takeWhile(_ != 0), UTF_8)
      def octal(off: Int, len: Int): Option[Long] =
        val s = String(h.slice(off, off + len), US_ASCII).takeWhile(c => c != '\u0000').trim
        if s.isEmpty then Some(0L) else scala.util.Try(java.lang.Long.parseLong(s, 8)).toOption
      val stored = octal(148, 8)
      val actual = h.zipWithIndex.foldLeft(0L) { case (acc, (b, i)) =>
        acc + (if i >= 148 && i < 156 then ' '.toInt else b & 0xff)
      }
      val typeflag = h(156).toChar
      val prefix = field(345, 155)
      val name = if prefix.isEmpty then field(0, 100) else s"$prefix/${field(0, 100)}"
      if !stored.contains(actual) then Left(s"$name: header checksum mismatch")
      else if typeflag != '0' && typeflag != '\u0000' then Left(s"$name: not a regular file (type '$typeflag')")
      else octal(124, 12).toRight(s"$name: unreadable size").map(size => Some(Entry(name, size)))

  def padding(size: com.histmersion.util.data.exchange.BundleTar.PaddingSize): com.histmersion.util.data.exchange.BundleTar.PaddingResult = ((Block - (size % Block)) % Block).toInt

  // ── writing ────────────────────────────────────────────────────────────────────────────────

  /** A tar.gz being written to `out`. Each [[entry]] must write exactly the size it declared. */
  final class Writer(out: OutputStream):
    private val gz = GZIPOutputStream(out, 1 << 16)

    def entry(name: com.histmersion.util.data.exchange.BundleTar.Writer.EntryName, size: com.histmersion.util.data.exchange.BundleTar.Writer.EntrySize)(body: OutputStream => Unit): Either[com.histmersion.util.data.exchange.BundleTar.Writer.EntryResultPart1, Unit] =
      header(name, size).flatMap { h =>
        gz.write(h)
        val counted = Counting(gz)
        body(counted)
        if counted.count != size then Left(s"$name: wrote ${counted.count} bytes, declared $size")
        else
          gz.write(new Array[Byte](padding(size)))
          Right(())
      }

    def bytes(name: com.histmersion.util.data.exchange.BundleTar.Writer.BytesName, data: com.histmersion.util.data.exchange.BundleTar.Writer.BytesData): Either[com.histmersion.util.data.exchange.BundleTar.Writer.BytesResultPart1, Unit] =
      entry(name, data.length.toLong)(_.write(data))

    /** Two zero blocks end the archive; then the gzip trailer. Does not close `out`. */
    def finish(): Unit =
      gz.write(new Array[Byte](Block * 2))
      gz.finish()
      gz.flush()

  object Writer:
    type BytesData = BytesData.T
    object BytesData extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type EntryResultPart1 = EntryResultPart1.T
    object EntryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntryName = EntryName.T
    object EntryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EntrySize = EntrySize.T
    object EntrySize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BytesResultPart1 = BytesResultPart1.T
    object BytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BytesName = BytesName.T
    object BytesName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Counts what passes through, and never closes the stream underneath. */
  final private class Counting(underlying: OutputStream) extends OutputStream:
    // a var: the running byte count of a stream, read once after the body has written
    var count: com.histmersion.util.data.exchange.BundleTar.Counting.Count = 0L
    override def write(b: Int): Unit = { underlying.write(b); count += 1 }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = { underlying.write(b, off, len); count += len }
    override def close(): Unit = ()

  object Counting:
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── reading ────────────────────────────────────────────────────────────────────────────────

  /** A tar.gz being read from `in`, entry by entry. `next` skips whatever the previous entry's reader left unread.
    */
  final class Reader(in: InputStream):
    private val gz = GZIPInputStream(in, 1 << 16)
    // the entry being read and how much of it (plus padding) is still ahead
    private var remaining: com.histmersion.util.data.exchange.BundleTar.Reader.Remaining = 0L
    private var pad: com.histmersion.util.data.exchange.BundleTar.Reader.Pad = 0

    def next(): Either[com.histmersion.util.data.exchange.BundleTar.Reader.NextResultPart1, Option[Entry]] =
      scala.util
        .Try {
          skipFully(remaining + pad)
          remaining = 0L; pad = 0
          val h = readBlock()
          h
        }
        .toEither
        .left
        .map(e => s"truncated bundle: ${e.getMessage}")
        .flatMap {
          case None    => Right(None) // the stream ended without the end blocks — tolerated as EOF
          case Some(h) =>
            parseHeader(h).map(_.map { e =>
              remaining = e.size; pad = padding(e.size)
              e
            })
        }

    /** The current entry's bytes. Closing it does not close the archive. */
    def stream: InputStream = new FilterInputStream(gz):
      override def read(): Int =
        if remaining <= 0 then -1
        else
          val b = gz.read()
          if b < 0 then throw EOFException("bundle ends inside an entry")
          remaining -= 1
          b
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        if remaining <= 0 then -1
        else
          val n = gz.read(b, off, math.min(len.toLong, remaining).toInt)
          if n < 0 then throw EOFException("bundle ends inside an entry")
          remaining -= n
          n
      override def skip(n: Long): Long =
        val k = math.min(n, remaining)
        val skipped = gz.skip(k)
        remaining -= skipped
        skipped
      override def available(): Int = 0
      override def close(): Unit = ()

    def readAll(): com.histmersion.util.data.exchange.BundleTar.Reader.ReadAllResult = stream.readAllBytes()

    private def readBlock(): Option[com.histmersion.util.data.exchange.BundleTar.Reader.ReadBlockResult] =
      val h = new Array[Byte](Block)
      val n = gz.readNBytes(h, 0, Block)
      if n == 0 then None
      else if n < Block then throw EOFException(s"partial header ($n bytes)")
      else Some(h)

    private def skipFully(n: com.histmersion.util.data.exchange.BundleTar.Reader.SkipFullyN): Unit =
      var left = n
      while left > 0 do
        val k = gz.skip(left)
        if k <= 0 then
          if gz.read() < 0 then throw EOFException("bundle ends inside an entry")
          left -= 1
        else left -= k

  object Reader:
    type ReadAllResult = ReadAllResult.T
    object ReadAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadBlockResult = ReadBlockResult.T
    object ReadBlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Remaining = Remaining.T
    object Remaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pad = Pad.T
    object Pad extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NextResultPart1 = NextResultPart1.T
    object NextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SkipFullyN = SkipFullyN.T
    object SkipFullyN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
