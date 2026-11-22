package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Access, Doi, Fetch, FetchedFile, SourceFetcher, WorkKey}
import io.github.iltotore.iron.constraint.any.Pure
import ByDoi.{noIdentifier, notHeld}

/** An open-access step that reads a work by its DOI: no DOI is a miss saying so, no copy a miss saying which step had
  * none.
  */
final class ByDoi(step: ByDoi.Step, copy: (Fetch.Context, Doi) => Iterator[Fetch]) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.doi
      .fold[Fetch](noIdentifier(Problem.Field("doi")))(d =>
        Fetch.firstFound(copy(ctx, d), notHeld(Problem.Raw(s"${d.value}")))
      )

object ByDoi:
  type Step = Step.T
  object Step extends Newtype[String, Pure]

  def noIdentifier(field: Problem.Field): Fetch =
    Fetch.Miss(Fetch.Absence.NoIdentifier, Problem.Invalid.Missing.here(field))

  def notHeld(key: Problem.Raw): Fetch =
    Fetch.Miss(Fetch.Absence.NotHeld, Problem.NotFound.Entity(Problem.TypeName("Copy"), key))
