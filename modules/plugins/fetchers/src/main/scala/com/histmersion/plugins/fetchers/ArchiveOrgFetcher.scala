package com.histmersion
package plugins.fetchers

import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.ArchiveOrg
import com.histmersion.upstreams.ArchiveId

/** The `archive-org` plugin's fetcher: a PUBLIC archive.org item's file, by its identifier — the format the caller
  * prefers (`WorkKey.format`), else an EPUB, a text PDF, a PDF. A lending-only (access-restricted) item is refused,
  * never worked around: it is borrowed on archive.org, not downloaded here (no DRM circumvention).
  */
final class ArchiveOrgFetcher(client: () => ArchiveOrg) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.archive match
      case None     => Fetch.Miss(Fetch.Miss.Why("archive-org needs an archive.org identifier"))
      case Some(id) =>
        val archive = client()
        archive.info(ArchiveId(id.value)) match
          case Left(e)                               => Fetch.Miss(Fetch.Miss.Why(e.value))
          case Right(item) if item.lendingOnly.value =>
            Fetch.Refused(Fetch.Refused.Why(s"'${item.identifier}' is a lending-only item — borrow it on archive.org"))
          case Right(item) =>
            AcquireParse.pickArchiveFile(item.files, work.format.map(f => AcquireParse.WantedFileKind(f.value))) match
              case None       => Fetch.Miss(Fetch.Miss.Why(s"'${item.identifier}' has no epub/pdf file to download"))
              case Some(file) =>
                archive
                  .download(ArchiveId(item.identifier.value), ArchiveOrg.DownloadFile(file.name.value))
                  .fold[Fetch](e => Fetch.Miss(Fetch.Miss.Why(e.value)), Fetch.Found(_))
