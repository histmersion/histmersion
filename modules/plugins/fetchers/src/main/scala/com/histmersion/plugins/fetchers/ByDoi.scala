package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Access, Doi, Fetch, FetchedFile, SourceFetcher, WorkKey}
import io.github.iltotore.iron.constraint.any.Pure

/** An open-access step that reads a work by its DOI: no DOI is a miss saying so, no copy a miss saying which step had
  * none.
  */
final class ByDoi(step: ByDoi.Step, copy: (Fetch.Context, Doi) => Option[FetchedFile]) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.doi
      .fold[Fetch](Fetch.Miss(Problem.Invalid.Missing.here(Problem.Field("doi"))))(d =>
        copy(ctx, d).fold[Fetch](
          Fetch.Miss(
            Problem.NotFound.Entity(Problem.TypeName("com.histmersion.plugin.Copy"), Problem.Raw(s"${d.value}"))
          )
        )(Fetch.Found(_))
      )

object ByDoi:
  type Step = Step.T
  object Step extends Newtype[String, Pure]
