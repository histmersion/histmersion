package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.upstreams.archiveorg.ArchiveOrg
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry

/** `archive-org`: a public archive.org item's file, by its identifier (lending-only items are refused), and
  * archive.org's texts as a read-only source collection (`histmersion archive search|info|import`).
  */
final class ArchiveOrgPlugin extends FetcherPlugin("archive-org"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[Problems, Unit] =
    lazy val archive = ArchiveOrg(c.acquire.archiveOrg, ctx.http)
    ctx.sourceFetchers
      .add(Registry.AddId("archive-org"), ArchiveOrgFetcher(() => archive))
      .flatMap(_ => ctx.sourceCollections.add(Registry.AddId("archive-org"), ArchiveOrgCollection(() => archive)))
