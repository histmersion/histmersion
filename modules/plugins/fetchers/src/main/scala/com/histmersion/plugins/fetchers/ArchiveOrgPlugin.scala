package com.histmersion.plugins.fetchers

import com.histmersion.upstreams.archiveorg.ArchiveOrg
import com.histmersion.plugin.Plugin

/** `archive-org`: a public archive.org item's file, by its identifier (lending-only items are refused), and
  * archive.org's texts as a read-only source collection (`histmersion archive search|info|import`).
  */
final class ArchiveOrgPlugin extends FetcherPlugin("archive-org"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[String, Unit] =
    lazy val archive = ArchiveOrg(c.acquire.archiveOrg, ctx.http)
    ctx.sourceFetchers
      .add("archive-org", ArchiveOrgFetcher(() => archive))
      .flatMap(_ => ctx.sourceCollections.add("archive-org", ArchiveOrgCollection(() => archive)))
