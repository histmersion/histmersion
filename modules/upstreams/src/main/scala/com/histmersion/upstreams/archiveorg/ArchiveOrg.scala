package com.histmersion.upstreams.archiveorg
import com.histmersion.plugin.FetchedFile
import com.histmersion.domain.library.acquisition.Endpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.landing.RepositoryLanding.Candidate
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.json
import com.histmersion.plugin.Plugin

/** archive.org's search, an item's metadata and a file of a public item. */
final class ArchiveOrg(archive: Endpoint, http: Plugin.Http):

  /** `https://archive.org/details/<id>` or a bare identifier. */
  private def identifier(s: com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierS): com.histmersion.upstreams.ArchiveId =
    val t = (s).value.trim
    _root_.com.histmersion.upstreams.ArchiveId((if t.contains("/details/") then t.split("/details/").last else t).split("[/?#]").head)

  def search(query: com.histmersion.upstreams.archiveorg.ArchiveOrg.SearchQuery, rows: com.histmersion.upstreams.PageSize): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.Problem, List[ArchiveItem]] =
    http
      .json(
        Url(
          Candidate.Url(archive.url.value),
          _root_.com.histmersion.upstreams.http.Url.UrlPath("/advancedsearch.php"),
          "q" -> s"($query) AND mediatype:texts",
          "fl[]" -> "identifier",
          "rows" -> rows.toString,
          "output" -> "json",
          "sort[]" -> "downloads desc"
        ).value
      )
      .left
      .map(ArchiveOrg.Problem(_))
      .map(AcquireParse.archiveSearch)
      // the search index returns only identifiers reliably; the item metadata (title, creator,
      // restriction) comes from /metadata, one call per hit
      .map(_.map(i => info(i.identifier).getOrElse(i)))

  def info(id: com.histmersion.upstreams.ArchiveId): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.Problem, ArchiveItem] =
    val ident = identifier(_root_.com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierS((id).value))
    http.json(Url(Candidate.Url(archive.url.value), _root_.com.histmersion.upstreams.http.Url.UrlPath(s"/metadata/$ident")).value).left.map(ArchiveOrg.Problem(_)).map(AcquireParse.archiveItem(ident, _))

  def download(id: com.histmersion.upstreams.ArchiveId, file: com.histmersion.upstreams.archiveorg.ArchiveOrg.DownloadFile): Either[com.histmersion.upstreams.archiveorg.ArchiveOrg.Problem, FetchedFile] =
    val ident = identifier(_root_.com.histmersion.upstreams.archiveorg.ArchiveOrg.IdentifierS((id).value))
    val url = Url(
      Candidate.Url(archive.url.value),
      _root_.com.histmersion.upstreams.http.Url.UrlPath(
        s"/download/$ident/" + java.net.URLEncoder
          .encode(file.value, "UTF-8")
          .replace("+", "%20")
      )
    )
    http.get(Plugin.Http.GetUrl(url.value)).left.map(e => ArchiveOrg.Problem(e.value)).flatMap { b =>
      if b.value.length < 10240 then Left(ArchiveOrg.Problem(s"archive.org returned only ${b.value.length} bytes for $file"))
      else Right(FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("archive_org"), Some(FetchedFile.Url(url.value)), FetchedFile.Bytes(b.value), _root_.com.histmersion.plugin.FetchedFile.Extension((file).value.toLowerCase.split("\\.").lastOption.getOrElse("pdf"))))
    }

object ArchiveOrg:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifierS = IdentifierS.T
  object IdentifierS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadFile = DownloadFile.T
  object DownloadFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
