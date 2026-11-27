package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.api.given

import java.io.{EOFException, FilterInputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets.{US_ASCII, UTF_8}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

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
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Json, "reads and writes the tar stream of a `.hmx` bundle")
object BundleTar:

  val Block = 512
  val MaxName = 100

  /** 11 octal digits. */
  val MaxSize: ByteCount = ByteCount.applyUnsafe((1L << 33) - 1)

  /** One ustar header for a regular file. Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def header(name: Entry.Name, size: ByteCount): Either[Problem, Array[Byte]] =
    val nameBytes = name.value.getBytes(UTF_8)
    if nameBytes.length > MaxName then
      Left(damaged(s"$name", Problem.Io.Damage.Length(Problem.Count(MaxName), Problem.Count(nameBytes.length))))
    else if size.value < 0 || size > MaxSize then
      Left(
        Problem.Invalid.OutOfRange(
          Problem.TypeName.of(ByteCount),
          Problem.Raw(s"$size"),
          Problem.Invalid.Bounds(Some(Problem.Raw("0")), Some(Problem.Raw(s"$MaxSize"))),
          Vector(Problem.Field(s"$name"))
        )
      )
    else
      val h = new Array[Byte](Block)
      def put(off: Int, s: String): Unit =
        val b = s.getBytes(US_ASCII)
        System.arraycopy(b, 0, h, off, b.length)
      System.arraycopy(nameBytes, 0, h, 0, nameBytes.length)
      put(100, "0000644\u0000") // mode
      put(108, "0000000\u0000") // uid
      put(116, "0000000\u0000") // gid
      put(124, f"${size.value}%011o\u0000") // size
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
    object Name extends Newtype[String, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Size = ByteCount
    val Size: ByteCount.type = ByteCount

  /** Parse a header block. `Right(None)` is the end-of-archive (an all-zero block). Pure. */
  def parseHeader(h: Array[Byte]): Either[Problem, Option[Entry]] =
    if h.length != Block then
      Left(damaged("a header", Problem.Io.Damage.Length(Problem.Count(Block), Problem.Count(h.length))))
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
      if !stored.contains(actual) then Left(damaged(name, Problem.Io.Damage.Checksum))
      else if typeflag != '0' && typeflag != '\u0000' then
        Left(Problem.Io.Wrong(Problem.Io.Path(s"$name"), Problem.Io.Kind.File))
      else
        octal(124, 12)
          .flatMap(ByteCount.option(_))
          .toRight(damaged(name, Problem.Io.Damage.Undecodable))
          .map(size => Some(Entry(Entry.Name(name), size)))

  private def damaged(entry: String, damage: Problem.Io.Damage): Problem =
    Problem.Io.Corrupt(Problem.Io.Format.Tar, Problem.Where(s"$entry"), damage)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def padding(size: ByteCount): Int = ((Block - (size.value % Block)) % Block).toInt

  // ── writing ────────────────────────────────────────────────────────────────────────────────

  /** A tar.gz being written to `out`. Each [[entry]] must write exactly the size it declared. */
  final class Writer(out: OutputStream):
    private val gz = GZIPOutputStream(out, 1 << 16)

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def entry(name: Entry.Name, size: ByteCount)(body: OutputStream => Unit): Either[Problem, Unit] =
      header(name, size).flatMap { h =>
        gz.write(h)
        val counted = Counting(gz)
        body(counted)
        val declared = size.value
        if counted.count != declared then
          Left(damaged(s"$name", Problem.Io.Damage.Length(Problem.Count(declared), Problem.Count(counted.count))))
        else
          gz.write(new Array[Byte](padding(size)))
          Right(())
      }

    def bytes(name: Entry.Name, data: Array[Byte]): Either[Problem, Unit] =
      entry(name, ByteCount.applyUnsafe(data.length.toLong))(_.write(data))

    /** Two zero blocks end the archive; then the gzip trailer. Does not close `out`. */
    def finish(): Unit =
      gz.write(new Array[Byte](Block * 2))
      gz.finish()
      gz.flush()

  /** Counts what passes through, and never closes the stream underneath. */
  final private class Counting(underlying: OutputStream) extends OutputStream:
    // a var: the running byte count of a stream, read once after the body has written
    var count: Long = 0L
    override def write(b: Int): Unit = { underlying.write(b); count += 1 }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = { underlying.write(b, off, len); count += len }
    override def close(): Unit = ()

  // ── reading ────────────────────────────────────────────────────────────────────────────────

  /** A tar.gz being read from `in`, entry by entry. `next` skips whatever the previous entry's reader left unread.
    */
  final class Reader(in: InputStream):
    private val gz = GZIPInputStream(in, 1 << 16)
    // the entry being read and how much of it (plus padding) is still ahead
    private var remaining: Long = 0L
    private var pad: Int = 0

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def next(): Either[Problem, Option[Entry]] =
      scala.util
        .Try {
          skipFully(remaining + pad)
          remaining = 0L; pad = 0
          val h = readBlock()
          h
        }
        .toEither
        .left
        .map(e =>
          Problem.Io.Corrupt(
            Problem.Io.Format.Bundle,
            Problem.Where("the bundle"),
            Problem.Io.Damage.Unreadable(External.Reported.of(e))
          )
        )
        .flatMap {
          case None    => Right(None) // the stream ended without the end blocks — tolerated as EOF
          case Some(h) =>
            parseHeader(h).map(_.map { e =>
              remaining = e.size.value; pad = padding(e.size)
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

    def readAll(): Array[Byte] = stream.readAllBytes()

    private def readBlock(): Option[Array[Byte]] =
      val h = new Array[Byte](Block)
      val n = gz.readNBytes(h, 0, Block)
      if n == 0 then None
      else if n < Block then throw EOFException(s"partial header ($n bytes)")
      else Some(h)

    private def skipFully(n: Long): Unit =
      var left = n
      while left > 0 do
        val k = gz.skip(left)
        if k <= 0 then
          if gz.read() < 0 then throw EOFException("bundle ends inside an entry")
          left -= 1
        else left -= k
