package com.histmersion
package cli

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.{Citekey, NoteImport}
import com.histmersion.api.media.{AssetInput, AssetLinks}
import com.histmersion.api.Api
import com.histmersion.api.ByteCount
import com.histmersion.api.Paging
import com.histmersion.lint.{tolerated, LintRule}

/** Host-side note handling for `histmersion seed` / `put` / `export` (plan §1.9a, §2.5).
  *
  * The CLI is a pure HTTP client: it never touches the database. But the corpus usually lives on the HOST
  * (`~/…/wiki/sources`), where the containerised server cannot see it — so the CLI READS the files and posts their text
  * to `POST /api/sources/import`, which parses and stores them. Seeding a path the server CAN see (a bind-mounted
  * corpus) still goes through `POST /api/seed`.
  */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
object Notes:

  def markdownFiles(dir: Path): List[Path] =
    val s = Files.list(dir)
    try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".md")).toList.sortBy(_.toString)
    finally s.close()

  /** Package one note for the server: its text, the converted body it points at, and its mtime (so the server can apply
    * the same "stored row is newer than the file" guard as a server-side seed).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def toImport(file: Path, force: Api.Overwrite): NoteImport =
    val text = Files.readString(file)
    NoteImport(
      text = NoteImport.Text(text),
      citekey = Some(Citekey(file.getFileName.toString.stripSuffix(".md"))),
      body = body(file, text),
      mtime = Some(NoteImport.Mtime(Files.getLastModifiedTime(file).toMillis)),
      force = NoteImport.Force(force.value)
    )

  /** The converted full text this note points at via `md_path`, resolved relative to the repo root = three parents up
    * from `wiki/sources/<citekey>.md` — the same rule `Frontmatter.parseFile` uses.
    */
  private def body(file: Path, text: String): Option[NoteImport.Body] =
    mdPath(text).flatMap { rel =>
      Option(file.toAbsolutePath.getParent)
        .flatMap(p => Option(p.getParent))
        .flatMap(p => Option(p.getParent))
        .map(_.resolve(rel))
        .filter(Files.isRegularFile(_))
        .map(f => NoteImport.Body(Files.readString(f)))
    }

  /** The note's figures on disk — `<md_path's dir>/figures/…`, or the `md/<citekey>/figures` convention when the note
    * has no `md_path` (plan §1.8). The server cannot see the host's corpus, so `histmersion seed` uploads them; they
    * are sent in bounded batches through `POST /api/sources/{ck}/assets` rather than inside the note, which keeps
    * request sizes sane. Sorted and capped, so one pathological directory cannot stall a seed.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(file: Path, citekey: Citekey, maxBytes: ByteCount, maxCount: Paging.Limit): List[AssetInput] =
    val text = scala.util.Try(Files.readString(file)).getOrElse("")
    val root = Option(file.toAbsolutePath.getParent)
      .flatMap(p => Option(p.getParent))
      .flatMap(p => Option(p.getParent))
    val dir = root.map(r =>
      mdPath(text)
        .map(rel => r.resolve(rel).getParent.resolve("figures"))
        .getOrElse(r.resolve("md").resolve(citekey.value).resolve("figures"))
    )
    dir.filter(Files.isDirectory(_)).toList.flatMap { d =>
      val s = Files.list(d)
      val files =
        try s.iterator.asScala.filter(Files.isRegularFile(_)).toList.sortBy(_.getFileName.toString)
        finally s.close()
      files.take(maxCount.value).flatMap { f =>
        val name = AssetLinks.safeName(f.getFileName.toString)
        val mime = AssetLinks.mimeOf(Bundle.Asset.Extension(name.value))
        if !mime.value.startsWith("image/") then None
        else
          scala.util
            .Try(Files.readAllBytes(f))
            .toOption
            .filter(b => b.nonEmpty && b.length <= maxBytes.value)
            .map(b =>
              AssetInput(
                Bundle.Asset.FileName(name.value),
                mime,
                AssetInput.Base64(java.util.Base64.getEncoder.encodeToString(b))
              )
            )
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
      val end = rest.indexOf("\n---")
      if end < 0 then None else Some(rest.substring(0, end))
