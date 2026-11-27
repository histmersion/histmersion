package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.ArchiveOrg
import com.histmersion.upstreams.ArchiveId
import com.histmersion.lint.{tolerated, LintRule}

/** The `archive-org` plugin's fetcher: a PUBLIC archive.org item's file, by its identifier — the format the caller
  * prefers (`WorkKey.format`), else an EPUB, a text PDF, a PDF. A lending-only (access-restricted) item is refused,
  * never worked around: it is borrowed on archive.org, not downloaded here (no DRM circumvention).
  */
final class ArchiveOrgFetcher(client: () => ArchiveOrg) extends SourceFetcher:
  val access: Access = Access.Open
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.archive match
      case None     => ByDoi.noIdentifier(Problem.Field("archive.org identifier"))
      case Some(id) =>
        val archive = client()
        archive.info(ArchiveId(id.value)) match
          case Left(e)                               => Fetch.failed(e)
          case Right(item) if item.lendingOnly.value =>
            Fetch.Refused(Fetch.Refusal.LendingOnly, Problem.Refused.Policy(Problem.Refused.Rule.LendingOnly))
          case Right(item) =>
            AcquireParse.pickArchiveFile(item.files, work.format.map(f => AcquireParse.WantedFileKind(f.value))) match
              case None       => ByDoi.notHeld(Problem.Raw(s"${item.identifier}"))
              case Some(file) =>
                archive
                  .download(ArchiveId(item.identifier.value), ArchiveOrg.DownloadFile(file.name.value))
                  .fold[Fetch](Fetch.failed, Fetch.Found(_))
