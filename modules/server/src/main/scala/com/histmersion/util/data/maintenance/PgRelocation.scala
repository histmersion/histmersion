package com.histmersion
package util.data.maintenance

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput}
import com.histmersion.util.storage.{ByteSource, Store}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  type BytePattern = BytePattern.T
  object BytePattern extends RefinedType[Array[Byte], Pure]
  type FileBytes = FileBytes.T
  object FileBytes extends RefinedType[Array[Byte], Pure]
  type MacOsMode = MacOsMode.T
  object MacOsMode extends RefinedType[Boolean, Pure]
  type PatchCount = PatchCount.T
  object PatchCount extends RefinedType[Int, Pure]
  type Replacement = Replacement.T
  object Replacement extends RefinedType[Array[Byte], Pure]

  type PatchFrom = BytePattern
  val PatchFrom: BytePattern.type = BytePattern
  type PatchTo = Replacement
  val PatchTo: Replacement.type = Replacement


  val Marker = ".prefix"

  final case class Result(files: Result.Files, signed: Result.Signed, tooLong: Result.TooLong)

  object Result:
    type Files = Files.T
    object Files extends RefinedType[Int, Pure]
    type Signed = Signed.T
    object Signed extends RefinedType[Int, Pure]
    type TooLong = TooLong.T
    object TooLong extends RefinedType[Int, Pure]

  /** Rewrite `from` → `to` in `bytes`. Binary data (it holds a NUL) is patched string by string in place; text is
    * replaced outright. Returns the new bytes and how many strings did not fit.
    */
  def patch(bytes: FileBytes, from: PatchFrom, to: PatchTo): (FileBytes, PatchCount) =
    val b = bytes.value
    val f = from.value
    val t = to.value
    if !b.contains(0.toByte) then
      (FileBytes(String(b, UTF_8).replace(String(f, UTF_8), String(t, UTF_8)).getBytes(UTF_8)), PatchCount(0))
    else
      val d = b.clone()
      var tooLong = 0
      var i = indexOf(d, f, 0)
      while i >= 0 do
        val start = lastNul(d, i) + 1
        val end = { val e = d.indexOf(0.toByte, i); if e < 0 then d.length else e }
        var pad = end
        while pad < d.length && d(pad) == 0 do pad += 1
        val s = replaceAll(d.slice(start, end), f, t)
        if s.length <= pad - start - 1 then // at least one terminating NUL stays
          System.arraycopy(s, 0, d, start, s.length)
          java.util.Arrays.fill(d, start + s.length, pad, 0.toByte)
        else tooLong += 1
        i = indexOf(d, f, end)
      (FileBytes(d), PatchCount(tooLong))

  /** Relocate the PostgreSQL in `pg` if its marker names another place. `None` when there is nothing to do: no marker
    * (a pixi environment used in place) or already at home. The re-signing runs through `processes`.
    */
  def relocate(pg: Store, macOS: MacOsMode, processes: Processes): Option[Result] =
    val marker = Store.Key.applyUnsafe(Marker)
    val here = pg.onDisk(Store.Key.Root).fold(e => throw IllegalStateException(e.message.value), _.toRealPath().toString)
    val recorded = if pg.isFile(marker) then pg.get(marker).map(_.text().value.trim).filter(_.nonEmpty) else None
    recorded.filter(_ != here).map { from =>
      val (fb, tb) = (from.getBytes(UTF_8), here.getBytes(UTF_8))
      var files, signed, tooLong = 0
      pg.walk()
        .map(_.key)
        .filter(k => k != marker && !pg.isLink(k))
        .foreach { k =>
          val bytes = pg.get(k).fold(Array.emptyByteArray)(_.bytes().value)
          if indexOf(bytes, fb, 0) >= 0 then
            val (out, skipped) = patch(FileBytes(bytes), PatchFrom(fb), PatchTo(tb))
            pg.write(k, ByteSource.Content(out.value)).left.foreach(e => throw java.io.IOException(e.message.value)) // in place: keeps the permissions
            files += 1
            tooLong += skipped.value
            if macOS.value && isMachO(out.value) then
              val file = pg.onDisk(k).fold(e => throw java.io.IOException(e.message.value), _.toString)
              val rc = processes
                .start(
                  ProcessLaunch(
                    List("codesign", "--force", "--sign", "-", file).map(ProcessLaunch.Command(_)),
                    output = ProcessOutput.Discarded
                  )
                )
                .fold(e => throw java.io.IOException(e.value), _.waitFor())
              if rc.value != 0 then throw IllegalStateException(s"codesign failed on $file (exit $rc)")
              signed += 1
        }
      pg.write(marker, ByteSource.Content(here.getBytes(UTF_8))).left.foreach(e => throw java.io.IOException(e.message.value))
      Result(Result.Files(files), Result.Signed(signed), Result.TooLong(tooLong))
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
