package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** One dataset. Called by the host from one import at a time; failures are values (`Left`), an exception is caught by
  * the host and reported as the import's failure.
  */
trait GazetteerSource:
  def title: GazetteerSource.Title

  /** One sentence: what it brings and on what terms. */
  def description: GazetteerSource.Description = GazetteerSource.Description("")

  /** The dataset's licence and credit, as its provider states them. The credit is the provenance line stored with each
    * place that does not state its own ([[GazetteerPlace.source]]).
    */
  def rights: GazetteerRights

  /** Every place of the dataset, in the order the host stores them (a place another one is part of first, so the
    * relation finds it). `http` is the host's client for gazetteer downloads (its User-Agent and timeouts); a dataset
    * is one large download, so [[Plugin.Http.fetch]] (one request, the answer's status as it came) suits it best.
    */
  def fetch(http: Plugin.Http): Either[Problem, List[GazetteerPlace]]

object GazetteerSource:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
