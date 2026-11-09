package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** Gazetteer sources, a part of the app that plugins extend: a dataset of historical places — polity outlines, a
  * classical atlas's regions, named periods — that `histmersion places fetch <id>` (and `init.fetch-sources`) imports
  * into the map. A plugin adds one or more [[GazetteerSource]]s to [[Plugin.Context.gazetteerSources]].
  *
  * The plugin DOWNLOADS and READS: it returns the dataset's places in the shape the host stores them. The host owns the
  * rest: the client the download goes through (its User-Agent and timeouts), checking every place, the dry run's
  * counts, merging the places into the store in batches (a place someone removed or merged away is never brought back),
  * matching the findings to the places again, and the background job.
  */

/** One dataset. Called by the host from one import at a time; failures are values (`Left`), an exception is caught by
  * the host and reported as the import's failure.
  */
trait GazetteerSource:
  /** What to call it: "Seshat Cliopatria polity polygons". */
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
