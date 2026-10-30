package com.histmersion.server

import com.histmersion.values.Citekey

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The files the server keeps of a source's original (#969), which a delete removes after its commit:
  *
  *   - the OCR spool's `<converter.spool-dir>/<sha256>.<ext>` — content-addressed, so it stays when another source
  *     still holds the same digest;
  *   - the acquisition spool's `<acquire.spool-dir>/<key>/paper.<ext>`, for the citekey and each old citekey of the
  *     source (a fetched file is kept under the key it arrived under).
  *
  * Calibre's copy is never among them: Calibre is the user's library, and `histmersion calibre` the only thing that
  * writes it.
  */
object StoredOriginals:

  /** The paths that exist now. A key that is not a valid citekey, or would resolve outside the acquisition spool, names
    * nothing.
    */
  def find(
      converterSpool: Path,
      acquireSpool: Path,
      sha256: Option[String],
      shared: Boolean,
      keys: List[String]
  ): List[Path] =
    val spooled = sha256
      .filterNot(_ => shared)
      .toList
      .flatMap(sha =>
        listed(converterSpool)
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.startsWith(sha + "."))
      )
    val base = acquireSpool.toAbsolutePath.normalize
    val acquired = keys.filter(Citekey.valid).flatMap { k =>
      val dir = base.resolve(k).normalize
      if dir.startsWith(base) && dir != base && Files.isDirectory(dir) then
        listed(dir).filter(Files.isRegularFile(_)) :+ dir
      else Nil
    }
    (spooled ++ acquired).distinct

  /** Remove `paths` (files first, then the directories they were in, when empty). Best effort: every failure is logged
    * and returned as `path: reason`, and never undoes the delete it follows.
    */
  def remove(paths: List[Path]): List[String] =
    val (dirs, files) = paths.partition(Files.isDirectory(_))
    (files ++ dirs).flatMap { p =>
      scala.util.Try(Files.deleteIfExists(p)).failed.toOption.map { e =>
        val why = s"$p: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}"
        scribe.warn(s"delete: the stored original could not be removed — $why")
        why
      }
    }

  private def listed(dir: Path): List[Path] =
    scala.util
      .Try {
        val s = Files.list(dir)
        try s.iterator.asScala.toList.sortBy(_.toString)
        finally s.close()
      }
      .getOrElse(Nil)
