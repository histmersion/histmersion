package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Host-side note handling for `histmersion seed` / `put` / `export` (plan §1.9a, §2.5).
  *
  * The CLI is a pure HTTP client: it never touches the database. But the corpus usually lives on the
  * HOST (`~/…/wiki/sources`), where the containerised server cannot see it — so the CLI READS the
  * files and posts their text to `POST /api/sources/import`, which parses and stores them. Seeding a
  * path the server CAN see (a bind-mounted corpus) still goes through `POST /api/seed`. */
object Notes:

  def markdownFiles(dir: Path): List[Path] =
    val s = Files.list(dir)
    try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".md")).toList.sortBy(_.toString)
    finally s.close()

  /** Package one note for the server: its text, the converted body it points at, and its mtime (so
    * the server can apply the same "stored row is newer than the file" guard as a server-side seed). */
  def toImport(file: Path, force: Boolean): NoteImport =
    val text = Files.readString(file)
    NoteImport(
      text = text,
      citekey = Some(file.getFileName.toString.stripSuffix(".md")),
      body = body(file, text),
      mtime = Some(Files.getLastModifiedTime(file).toMillis),
      force = force
    )

  /** The converted full text this note points at via `md_path`, resolved relative to the repo root =
    * three parents up from `wiki/sources/<citekey>.md` — the same rule `Frontmatter.parseFile` uses. */
  private def body(file: Path, text: String): Option[String] =
    mdPath(text).flatMap { rel =>
      Option(file.toAbsolutePath.getParent)
        .flatMap(p => Option(p.getParent))
        .flatMap(p => Option(p.getParent))
        .map(_.resolve(rel))
        .filter(Files.isRegularFile(_))
        .map(Files.readString)
    }

  /** The note's figures on disk — `<md_path's dir>/figures/…`, or the `md/<citekey>/figures`
    * convention when the note has no `md_path` (plan §1.8). The server cannot see the host's corpus,
    * so `histmersion seed` uploads them; they are sent in bounded batches through
    * `POST /api/sources/{ck}/assets` rather than inside the note, which keeps request sizes sane.
    * Sorted and capped, so one pathological directory cannot stall a seed. */
  def figures(file: Path, citekey: String, maxBytes: Long, maxCount: Int): List[AssetInput] =
    val text = scala.util.Try(Files.readString(file)).getOrElse("")
    val root = Option(file.toAbsolutePath.getParent)
      .flatMap(p => Option(p.getParent))
      .flatMap(p => Option(p.getParent))
    val dir = root.map(r =>
      mdPath(text).map(rel => r.resolve(rel).getParent.resolve("figures"))
        .getOrElse(r.resolve("md").resolve(citekey).resolve("figures")))
    dir.filter(Files.isDirectory(_)).toList.flatMap { d =>
      val s = Files.list(d)
      val files =
        try s.iterator.asScala.filter(Files.isRegularFile(_)).toList.sortBy(_.getFileName.toString)
        finally s.close()
      files.take(maxCount).flatMap { f =>
        val name = AssetLinks.safeName(f.getFileName.toString)
        val mime = AssetLinks.mimeOf(name)
        if !mime.startsWith("image/") then None
        else
          scala.util.Try(Files.readAllBytes(f)).toOption
            .filter(b => b.nonEmpty && b.length <= maxBytes)
            .map(b => AssetInput(name, mime, java.util.Base64.getEncoder.encodeToString(b)))
      }
    }

  private val MdPath = """(?m)^md_path:\s*["']?([^"'\r\n]+)["']?\s*$""".r

  private def mdPath(text: String): Option[String] =
    frontmatter(text).flatMap(MdPath.findFirstMatchIn(_)).map(_.group(1).trim).filter(_.nonEmpty)

  private def frontmatter(text: String): Option[String] =
    val t = text.stripPrefix("﻿")
    if !t.startsWith("---") then None
    else
      val rest = t.drop(3)
      val end  = rest.indexOf("\n---")
      if end < 0 then None else Some(rest.substring(0, end))
