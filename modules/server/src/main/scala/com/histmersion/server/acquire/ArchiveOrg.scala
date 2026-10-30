package com.histmersion.server.acquire

import com.histmersion.pluginhost.{AcquireParse, Url}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

final class ArchiveOrg(cfg: AcquireConfig, http: Http) extends ArchiveClient:

  /** `https://archive.org/details/<id>` or a bare identifier. */
  private def identifier(s: String): String =
    val t = s.trim
    (if t.contains("/details/") then t.split("/details/").last else t).split("[/?#]").head

  def search(query: String, rows: Int): Either[String, List[ArchiveItem]] =
    http
      .json(
        Url(
          cfg.archiveOrg.url,
          "/advancedsearch.php",
          "q" -> s"($query) AND mediatype:texts",
          "fl[]" -> "identifier",
          "rows" -> rows.toString,
          "output" -> "json",
          "sort[]" -> "downloads desc"
        )
      )
      .map(AcquireParse.archiveSearch)
      // the search index returns only identifiers reliably; the item metadata (title, creator,
      // restriction) comes from /metadata, one call per hit
      .map(_.map(i => info(i.identifier).getOrElse(i)))

  def info(id: String): Either[String, ArchiveItem] =
    val ident = identifier(id)
    http.json(Url(cfg.archiveOrg.url, s"/metadata/$ident")).map(AcquireParse.archiveItem(ident, _))

  def download(id: String, file: String): Either[String, FetchedFile] =
    val ident = identifier(id)
    val url = Url(
      cfg.archiveOrg.url,
      s"/download/$ident/" + java.net.URLEncoder
        .encode(file, "UTF-8")
        .replace("+", "%20")
    )
    http.bytes(url).flatMap { b =>
      if b.length < 10240 then Left(s"archive.org returned only ${b.length} bytes for $file")
      else Right(FetchedFile("archive_org", Some(url), b, file.toLowerCase.split("\\.").lastOption.getOrElse("pdf")))
    }
