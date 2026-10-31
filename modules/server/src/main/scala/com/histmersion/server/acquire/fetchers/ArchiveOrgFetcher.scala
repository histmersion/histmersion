package com.histmersion.server.acquire.fetchers

import com.histmersion.upstreams.{AcquireParse, ArchiveOrg}
import com.histmersion.plugin.{Access, Fetch, FetchContext, SourceFetcher, WorkKey}

/** The `archive-org` plugin's fetcher: a PUBLIC archive.org item's file, by its identifier — the format the caller
  * prefers (`WorkKey.format`), else an EPUB, a text PDF, a PDF. A lending-only (access-restricted) item is refused,
  * never worked around: it is borrowed on archive.org, not downloaded here (no DRM circumvention,
  * docs/research-plugins.md §11).
  */
final class ArchiveOrgFetcher(client: () => ArchiveOrg) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    work.archive match
      case None     => Fetch.Miss("archive-org needs an archive.org identifier")
      case Some(id) =>
        val archive = client()
        archive.info(id) match
          case Left(e)                         => Fetch.Miss(e)
          case Right(item) if item.lendingOnly =>
            Fetch.Refused(s"'${item.identifier}' is a lending-only item — borrow it on archive.org")
          case Right(item) =>
            AcquireParse.pickArchiveFile(item.files, work.format) match
              case None       => Fetch.Miss(s"'${item.identifier}' has no epub/pdf file to download")
              case Some(file) =>
                archive.download(item.identifier, file.name).fold(Fetch.Miss(_), Fetch.Found(_))
