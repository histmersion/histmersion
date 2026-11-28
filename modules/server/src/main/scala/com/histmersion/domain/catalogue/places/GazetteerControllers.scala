package com.histmersion
package domain.catalogue.places

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.GazetteerSource
import com.histmersion.util.problem.ProblemLog.line
import io.scalaland.chimney.dsl.*
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobKinds}
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, Job, WorkCarrier}

import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.PlacesJobKinds
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The gazetteer's background work as REST, MCP and the CLI reach it: a source a plugin registers fetched, the land
  * fetched, the hierarchy built from spatial containment — one at a time (each rewrites places the others read), each a
  * job whose id the answer carries; a dry run answers its counts at once.
  */
final class GazetteerControllers(
    imports: GazetteerImports,
    geometry: PlaceGeometry,
    ds: DataSource,
    pins: WorkCarrier,
    maintenance: WriteGate
):
  private val slot = BackgroundJob.Slot()

  /** A places job: `about` says what it does (the jobs list shows it), `run` answers its summary or why it failed. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def job(kind: Job.Kind, about: String)(
      // runs on the job's thread, under the job's unit permit (never the caller's, whose block has ended by then)
      run: WritePermit ?=> Either[Problem, String]
  )(using WritePermit)(using Creator.New): Either[Api.Error, JobInfo] =
    BackgroundJob.start(
      ds,
      maintenance.workers,
      kind,
      Some(JobDetail.Purpose(Some(JobKinds.Subject(about)))),
      slot,
      BackgroundJob.Work("a places job"),
      pins = pins
    )((_, progress) =>
      progress(JobInfo.Progress(about))
      scribe.info(s"[import] $about…")
      val r = run
      r.fold(e => scribe.error(s"[import] $about failed: ${e.line}"), ok => scribe.info(s"[import] $about: $ok"))
      BackgroundJob.Outcome.of(r.map(BackgroundJob.Outcome.Summary(_)))
    )

  /** `POST /api/places/fetch?source=cliopatria` — download, transform and upsert an external gazetteer. Idempotent:
    * re-running upserts over existing entries. A real run is a background job (the download and its upserts exceed any
    * reasonable HTTP timeout).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def fetch(
      caller: Caller,
      source: Api.ExternalPlaces.Source,
      apply: Api.Apply
  )(using Creator.New): Either[Api.Error, Place.ImportResult] =
    maintenance.guard(WriteGate.OperationName("places/fetch")):
      val name = source.value.trim.toLowerCase
      if name == NaturalEarthLand.name then land(GazetteerControllers.DryRun(!apply.applies))
      else
        imports
          .source(
            BuiltinGazetteers.Entry.Name(name),
            Problem.Invalid.Unknown.here(
              Problem.TypeName.named[GazetteerSource],
              Problem.Raw.of(source),
              imports.available.toVector.map(Problem.Raw.of(_)) :+ Problem.Raw(NaturalEarthLand.name)
            )
          )
          .left
          .map(refused)
          .flatMap { src =>
            given Creator.New = imports.importerOf(src)
            if !apply.applies then imports.fetch(src, dryRun = GazetteerControllers.DryRun(true)).left.map(refused)
            else
              job(Job.Kind.assume(PlacesJobKinds.PlacesFetch.kind.value), s"fetch $name")(
                imports
                  .fetch(src, dryRun = GazetteerControllers.DryRun(false))
                  .map(r =>
                    s"${r.places} places, ${r.names} names, ${r.relations} relations" +
                      (if r.failed.nonEmpty then s", ${r.failed.size} failed" else "")
                  )
              ).map(j => importStarted(j))
          }

  /** `places fetch land`: Natural Earth land for clipping polities to the coast (V025); like the gazetteers, a real run
    * is a background job — two downloads and a re-clip of every polity.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def land(
      dryRun: GazetteerControllers.DryRun
  )(using WritePermit)(using Creator.New): Either[Api.Error, Place.ImportResult] =
    if dryRun.value then imports.land(dryRun = GazetteerControllers.DryRun(true)).left.map(refused)
    else
      job(Job.Kind.assume(PlacesJobKinds.PlacesFetch.kind.value), "fetch land")(
        imports
          .land(dryRun = GazetteerControllers.DryRun(false))
          .map(r => s"${r.places} polities clipped${r.failed.mkString(" ", "; ", "")}".trim)
      ).map(j => importStarted(j))

  private def refused(problem: Problem): Api.Error = Api.Error(problem)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def importStarted(j: JobInfo): Place.ImportResult =
    Place.ImportResult(
      Place.ImportResult.Places(0),
      Place.ImportResult.Names(0),
      Place.ImportResult.Relations(0),
      job = Some(Place.ImportResult.Job(j.id.value))
    )

  /** `POST /api/places/build-hierarchy` — parent-child edges from spatial containment, as a background job (ST_Contains
    * across thousands of polygons is slow): the answer carries its id, the finished job's summary the counts.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def buildHierarchy(caller: Caller)(using Creator.New): Either[Api.Error, SpatialHierarchyResult] =
    maintenance.guard(WriteGate.OperationName("places/build-hierarchy")):
      job(
        Job.Kind.assume(PlacesJobKinds.PlacesHierarchy.kind.value),
        "build the spatial hierarchy"
      ) {
        val r = geometry.buildHierarchy()
        Right(s"${r.edgesCreated} edges from ${r.polygonPlaces} polygons × ${r.pointPlaces} points")
      }.map(j =>
        SpatialHierarchyResult(
          edgesCreated = SpatialHierarchyResult.EdgesCreated(0),
          polygonPlaces = SpatialHierarchyResult.PolygonPlaces(0),
          pointPlaces = SpatialHierarchyResult.PointPlaces(0),
          job = Some(j.id)
        )
      )

object GazetteerControllers:
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
