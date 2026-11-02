package com.histmersion.upstreams.archiveorg
import com.histmersion.plugin.{FetchedFile, PluginHttp}
import com.histmersion.domain.library.acquisition.Endpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.json

/** archive.org's search, an item's metadata and a file of a public item. */
final class ArchiveOrg(archive: Endpoint, http: PluginHttp):

  /** `https://archive.org/details/<id>` or a bare identifier. */
  private def identifier(s: String): String =
    val t = s.trim
    (if t.contains("/details/") then t.split("/details/").last else t).split("[/?#]").head

  def search(query: String, rows: Int): Either[String, List[ArchiveItem]] =
    http
      .json(
        Url(
          archive.url,
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
    http.json(Url(archive.url, s"/metadata/$ident")).map(AcquireParse.archiveItem(ident, _))

  def download(id: String, file: String): Either[String, FetchedFile] =
    val ident = identifier(id)
    val url = Url(
      archive.url,
      s"/download/$ident/" + java.net.URLEncoder
        .encode(file, "UTF-8")
        .replace("+", "%20")
    )
    http.get(url).flatMap { b =>
      if b.length < 10240 then Left(s"archive.org returned only ${b.length} bytes for $file")
      else Right(FetchedFile("archive_org", Some(url), b, file.toLowerCase.split("\\.").lastOption.getOrElse("pdf")))
    }
