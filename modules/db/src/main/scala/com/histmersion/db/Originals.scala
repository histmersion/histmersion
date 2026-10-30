package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.io.InputStream
import java.nio.file.{Files, Path, StandardCopyOption}

/** Where the server keeps the originals it stored at ingest — the converter's spool, `<dir>/<sha256>.<ext>`
  * (`IngestHandlers.spool`). The bundle code reaches it through this, so the `db` module needs no config.
  */
trait Originals:
  /** The stored original with this digest: its path and extension. */
  def find(sha256: String): Option[(Path, String)]

  /** Store an original as `<sha256>.<ext>` unless one is there already; `Left` with why when it could not be —
    * including content that does not hash to `sha256`.
    */
  def store(sha256: String, ext: String, in: InputStream): Either[String, Unit]

object Originals:
  val none: Originals = new Originals:
    def find(sha256: String): Option[(Path, String)] = None
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      Left("this server stores no originals")

  /** A content-addressed directory: `<dir>/<sha256>.<ext>` — the converter's spool on a server. */
  def inDir(dir: Path): Originals = new Originals:
    def find(sha256: String): Option[(Path, String)] =
      scala.util
        .Try {
          val s = Files.list(dir)
          try s.filter(p => p.getFileName.toString.startsWith(sha256 + ".")).findFirst().orElse(null)
          finally s.close()
        }
        .toOption
        .flatMap(Option(_))
        .map(p => p -> p.getFileName.toString.drop(sha256.length + 1))

    /** Written to a temporary file, hashed, and moved into place only when the content IS `sha256`: the directory is
      * content-addressed, and `reocr` trusts the name.
      */
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      if find(sha256).isDefined then
        val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
        Right(())
      else
        scala.util
          .Try {
            Files.createDirectories(dir)
            val tmp = Files.createTempFile(dir, "bundle-", ".part")
            try
              val md = java.security.MessageDigest.getInstance("SHA-256")
              val out = java.security.DigestOutputStream(Files.newOutputStream(tmp), md)
              try { val _ = in.transferTo(out) }
              finally out.close()
              val actual = BundleFormat.hex(md.digest())
              if actual != sha256 then Left(s"its content hashes to $actual")
              else
                val _ = Files.move(tmp, dir.resolve(s"$sha256.${safeExt(ext)}"), StandardCopyOption.ATOMIC_MOVE)
                Right(())
            finally
              val _ = Files.deleteIfExists(tmp)
          }
          .toEither
          .left
          .map(e => s"cannot store it in $dir: ${e.getMessage}")
          .flatten

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: String): String =
    val e = ext.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then e else "bin"
