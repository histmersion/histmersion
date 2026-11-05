package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Access, Fetch, FetchedFile, SourceFetcher, WorkKey}

/** An open-access step that reads a work by its DOI: no DOI is a miss saying so, no copy a miss saying which step had
  * none.
  */
final class ByDoi(step: ByDoi.Step, copy: (Fetch.Context, ByDoi.CopyArg2) => Option[FetchedFile]) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.doi
      .map(_.value)
      .fold[Fetch](Fetch.Miss(s"$step needs a DOI"))(d =>
        copy(ctx, d).fold(Fetch.Miss(s"$step has no copy of $d"))(Fetch.Found(_))
      )

object ByDoi:
  type Step = Step.T
  object Step extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CopyArg2 = CopyArg2.T
  object CopyArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
