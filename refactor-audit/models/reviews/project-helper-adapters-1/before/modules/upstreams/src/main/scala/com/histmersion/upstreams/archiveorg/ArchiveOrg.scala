package com.histmersion.upstreams.archiveorg
import com.histmersion.plugin.FetchedFile
import com.histmersion.domain.library.acquisition.Endpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.json
import com.histmersion.plugin.Plugin

/** archive.org's search, an item's metadata and a file of a public item. */
final class ArchiveOrg(archive: Endpoint, http: Plugin.Http):

  /** `https://archive.org/details/<id>` or a bare identifier. */
  private def identifier(s: com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierS): com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierResult =
    val t = (s).value.trim
    _root_.com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierResult((if t.contains("/details/") then t.split("/details/").last else t).split("[/?#]").head)

  def search(query: com.histmersion.upstreams.archiveorg.ArchiveOrg.SearchQuery, rows: com.histmersion.upstreams.archiveorg.ArchiveOrg.SearchRows): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.SearchResultPart1, List[ArchiveItem]] =
    http
      .json(
        Url(
          archive.url,
          _root_.com.histmersion.upstreams.http.Url.ApplyPath("/advancedsearch.php"),
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

  def info(id: com.histmersion.upstreams.archiveorg.ArchiveOrg.InfoId): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.InfoResultPart1, ArchiveItem] =
    val ident = identifier(id)
    http.json(Url(archive.url, _root_.com.histmersion.upstreams.http.Url.ApplyPath(s"/metadata/$ident"))).map(AcquireParse.archiveItem(ident, _))

  def download(id: com.histmersion.upstreams.archiveorg.ArchiveOrg.DownloadId, file: com.histmersion.upstreams.archiveorg.ArchiveOrg.DownloadFile): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.DownloadResultPart1, FetchedFile] =
    val ident = identifier(id)
    val url = Url(
      archive.url,
      s"/download/$ident/" + java.net.URLEncoder
        .encode(file, "UTF-8")
        .replace("+", "%20")
    )
    http.get(url).flatMap { b =>
      if b.length < 10240 then Left(s"archive.org returned only ${b.length} bytes for $file")
      else Right(FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("archive_org"), Some(url), b, _root_.com.histmersion.plugin.FetchedFile.Extension((file).value.toLowerCase.split("\\.").lastOption.getOrElse("pdf"))))
    }

object ArchiveOrg:
  type IdentifierResult = IdentifierResult.T
  object IdentifierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifierS = IdentifierS.T
  object IdentifierS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchRows = SearchRows.T
  object SearchRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoResultPart1 = InfoResultPart1.T
  object InfoResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoId = InfoId.T
  object InfoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadId = DownloadId.T
  object DownloadId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadFile = DownloadFile.T
  object DownloadFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
