package com.histmersion
package util.storage

import java.io.InputStream
import java.nio.file.{Files, Path, StandardCopyOption}

/** A [[ContentStore]] in a directory: `<dir>/<sha256>.<ext>`. */
final class ContentDirectory(dir: Path) extends ContentStore:

  def find(sha256: Sha256): Option[(ByteSource, ContentStore.Ext)] =
    scala.util
      .Try {
        val s = Files.list(dir)
        try s.filter(p => p.getFileName.toString.startsWith(sha256.value + ".")).findFirst().orElse(null)
        finally s.close()
      }
      .toOption
      .flatMap(Option(_))
      .map(p => ByteSource.file(p) -> ContentStore.Ext(p.getFileName.toString.drop(sha256.value.length + 1)))

  /** Written to a temporary file, hashed, and moved into place only when the content IS `sha256`: the directory is
    * content-addressed, and whoever reads it trusts the name.
    */
  def store(sha256: Sha256, ext: ContentStore.Ext, in: InputStream): Either[ContentStore.Problem, Unit] =
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
            val actual = ContentStore.hex(ContentStore.Digest(md.digest()))
            if actual != sha256 then Left(ContentStore.Problem(s"its content hashes to $actual"))
            else
              val _ =
                Files.move(tmp, dir.resolve(s"$sha256.${ContentStore.safeExt(ext)}"), StandardCopyOption.ATOMIC_MOVE)
              Right(())
          finally
            val _ = Files.deleteIfExists(tmp)
        }
        .toEither
        .left
        .map(e => ContentStore.Problem(s"cannot store it in $dir: ${e.getMessage}"))
        .flatten

