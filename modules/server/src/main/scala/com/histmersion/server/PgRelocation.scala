package com.histmersion.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Makes the bundled PostgreSQL work from wherever the bundle was put.
  *
  * conda-forge's PostgreSQL does not relocate at runtime: it looks for its libraries, extensions and share/ tree at the
  * absolute prefix it was installed at (the pixi environment on the build machine). conda solves this at install time
  * by rewriting that prefix inside every file, and so do we: `pg/.prefix` records the prefix the files currently carry
  * (written by `scripts/bundle/postgres.sh`), and when it differs from where `pg/` actually is, every file is patched
  * and the marker updated. A bundle that is moved again is simply relocated again.
  *
  * Text files get a plain replacement. In binaries the prefix sits inside NUL-terminated strings — conda pads the path
  * constants to fixed 260-byte slots — so a string is rewritten in place and NUL-padded, never moving a byte after it;
  * a string whose new form would not fit is left alone and counted. On macOS every patched Mach-O file is re-signed ad
  * hoc, or arm64 refuses to run it.
  */
object PgRelocation:

  val Marker = ".prefix"

  final case class Result(files: Int, signed: Int, tooLong: Int)

  /** Rewrite `from` → `to` in `bytes`. Binary data (it holds a NUL) is patched string by string in place; text is
    * replaced outright. Returns the new bytes and how many strings did not fit.
    */
  def patch(bytes: Array[Byte], from: Array[Byte], to: Array[Byte]): (Array[Byte], Int) =
    if !bytes.contains(0.toByte) then
      (String(bytes, UTF_8).replace(String(from, UTF_8), String(to, UTF_8)).getBytes(UTF_8), 0)
    else
      val d = bytes.clone()
      var tooLong = 0
      var i = indexOf(d, from, 0)
      while i >= 0 do
        val start = lastNul(d, i) + 1
        val end = { val e = d.indexOf(0.toByte, i); if e < 0 then d.length else e }
        var pad = end
        while pad < d.length && d(pad) == 0 do pad += 1
        val s = replaceAll(d.slice(start, end), from, to)
        if s.length <= pad - start - 1 then // at least one terminating NUL stays
          System.arraycopy(s, 0, d, start, s.length)
          java.util.Arrays.fill(d, start + s.length, pad, 0.toByte)
        else tooLong += 1
        i = indexOf(d, from, end)
      (d, tooLong)

  /** Relocate `pg` if its marker names another place. `None` when there is nothing to do: no marker (a pixi environment
    * used in place) or already at home.
    */
  def relocate(pg: Path, macOS: Boolean): Option[Result] =
    val marker = pg.resolve(Marker)
    val here = pg.toRealPath().toString
    val recorded = if Files.isRegularFile(marker) then Some(Files.readString(marker).trim).filter(_.nonEmpty) else None
    recorded.filter(_ != here).map { from =>
      val (fb, tb) = (from.getBytes(UTF_8), here.getBytes(UTF_8))
      var files, signed, tooLong = 0
      val walk = Files.walk(pg)
      try
        walk.iterator.asScala
          .filter(p => p != marker && Files.isRegularFile(p) && !Files.isSymbolicLink(p))
          .foreach { p =>
            val bytes = Files.readAllBytes(p)
            if indexOf(bytes, fb, 0) >= 0 then
              val (out, skipped) = patch(bytes, fb, tb)
              Files.write(p, out) // in place: keeps the file's permissions
              files += 1
              tooLong += skipped
              if macOS && isMachO(out) then
                val rc = ProcessBuilder("codesign", "--force", "--sign", "-", p.toString)
                  .redirectErrorStream(true)
                  .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                  .start()
                  .waitFor()
                if rc != 0 then throw IllegalStateException(s"codesign failed on $p (exit $rc)")
                signed += 1
          }
      finally walk.close()
      Files.writeString(marker, here)
      Result(files, signed, tooLong)
    }

  private val MachO = List(Array(0xcf, 0xfa, 0xed, 0xfe), Array(0xca, 0xfe, 0xba, 0xbe)).map(_.map(_.toByte))

  private def isMachO(b: Array[Byte]): Boolean = b.length >= 4 && MachO.exists(m => b.take(4).sameElements(m))

  private def lastNul(d: Array[Byte], before: Int): Int =
    var j = before - 1
    while j >= 0 && d(j) != 0 do j -= 1
    j

  private def indexOf(d: Array[Byte], p: Array[Byte], from: Int): Int =
    if p.isEmpty then -1
    else
      var i = math.max(0, from)
      val last = d.length - p.length
      var found = -1
      while found < 0 && i <= last do
        if d(i) == p(0) && java.util.Arrays.equals(d, i, i + p.length, p, 0, p.length) then found = i
        i += 1
      found

  private def replaceAll(s: Array[Byte], from: Array[Byte], to: Array[Byte]): Array[Byte] =
    val out = java.io.ByteArrayOutputStream(s.length + 64)
    var i = 0
    var j = indexOf(s, from, 0)
    while j >= 0 do
      out.write(s, i, j - i); out.write(to)
      i = j + from.length
      j = indexOf(s, from, i)
    out.write(s, i, s.length - i)
    out.toByteArray
