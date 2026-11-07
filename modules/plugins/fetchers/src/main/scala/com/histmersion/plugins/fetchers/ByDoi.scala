package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Access, Doi, Fetch, FetchedFile, SourceFetcher, WorkKey}

/** An open-access step that reads a work by its DOI: no DOI is a miss saying so, no copy a miss saying which step had
  * none.
  */
final class ByDoi(step: ByDoi.Step, copy: (Fetch.Context, Doi) => Option[FetchedFile]) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.doi
      .fold[Fetch](Fetch.Miss(Fetch.Miss.Why(s"${step.value} needs a DOI")))(d =>
        copy(ctx, d).fold[Fetch](Fetch.Miss(Fetch.Miss.Why(s"${step.value} has no copy of ${d.value}")))(Fetch.Found(_))
      )

object ByDoi:
  type Step = Step.T
  object Step extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
