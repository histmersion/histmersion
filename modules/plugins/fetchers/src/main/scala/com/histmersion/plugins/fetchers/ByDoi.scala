package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Access, Fetch, FetchContext, FetchedFile, SourceFetcher, WorkKey}

/** An open-access step that reads a work by its DOI: no DOI is a miss saying so, no copy a miss saying which step had
  * none.
  */
final class ByDoi(step: String, copy: (FetchContext, String) => Option[FetchedFile]) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    work.doi
      .map(_.value)
      .fold[Fetch](Fetch.Miss(s"$step needs a DOI"))(d =>
        copy(ctx, d).fold(Fetch.Miss(s"$step has no copy of $d"))(Fetch.Found(_))
      )
