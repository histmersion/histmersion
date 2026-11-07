package com.histmersion.domain.catalogue.places

import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, WorkCarrier}

import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place

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
  private def job(kind: com.histmersion.util.jobs.Job.Kind, about: com.histmersion.domain.catalogue.places.GazetteerSourceRow.Key)(run: => Either[String, String]): Either[Api.Error, JobInfo] =
    BackgroundJob.start(ds, kind, Some(JobDetail.Purpose(Some(about))), slot, com.histmersion.util.jobs.BackgroundJob.Work("a places job"), pins = pins)(
      (_, progress) =>
        progress(about)
        scribe.info(s"[import] $about…")
        val r = run
        r.fold(e => scribe.error(s"[import] $about failed: $e"), ok => scribe.info(s"[import] $about: $ok"))
        BackgroundJob.Outcome.of(r)
    )

  /** `POST /api/places/fetch?source=cliopatria` — download, transform and upsert an external gazetteer. Idempotent:
    * re-running upserts over existing entries. A real run is a background job (the download and its upserts exceed any
    * reasonable HTTP timeout).
    */
  def fetch(caller: Caller, source: com.histmersion.domain.catalogue.places.GazetteerSourceRow.Key, dryRun: com.histmersion.domain.catalogue.places.GazetteerControllers.DryRun): Either[Api.Error, Place.ImportResult] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("places/fetch")):
      if source.trim.toLowerCase == NaturalEarthLand.name then land(dryRun)
      else
        val name = source.trim.toLowerCase
        imports
          .source(
            name,
            s"unknown source '$source' (available: ${(imports.available :+ NaturalEarthLand.name).mkString(", ")})"
          )
          .left
          .map(Api.Error(_))
          .flatMap { src =>
            if dryRun then imports.fetch(src, dryRun = true).left.map(Api.Error(_))
            else
              job(com.histmersion.api.catalogue.PlacesJobKinds.PlacesFetch.kind.value, s"fetch $name")(
                imports
                  .fetch(src, dryRun = false)
                  .map(r =>
                    s"${r.places} places, ${r.names} names, ${r.relations} relations" +
                      (if r.failed.nonEmpty then s", ${r.failed.size} failed" else "")
                  )
              ).map(j => Place.ImportResult(0, 0, 0, job = Some(j.id.value)))
          }

  /** `places fetch land`: Natural Earth land for clipping polities to the coast (V025); like the gazetteers, a real run
    * is a background job — two downloads and a re-clip of every polity.
    */
  private def land(dryRun: com.histmersion.domain.catalogue.places.GazetteerControllers.DryRun): Either[Api.Error, Place.ImportResult] =
    if dryRun then imports.land(dryRun = com.histmersion.domain.catalogue.places.GazetteerControllers.DryRun(true)).left.map(Api.Error(_))
    else
      job(com.histmersion.api.catalogue.PlacesJobKinds.PlacesFetch.kind.value, com.histmersion.domain.catalogue.places.GazetteerSourceRow.Key("fetch land"))(
        imports
          .land(dryRun = com.histmersion.domain.catalogue.places.GazetteerControllers.DryRun(false))
          .map(r => s"${r.places} polities clipped${r.failed.mkString(" ", "; ", "")}".trim)
      ).map(j => Place.ImportResult(0, 0, 0, job = Some(j.id.value)))

  /** `POST /api/places/build-hierarchy` — parent-child edges from spatial containment, as a background job (ST_Contains
    * across thousands of polygons is slow): the answer carries its id, the finished job's summary the counts.
    */
  def buildHierarchy(caller: Caller): Either[Api.Error, SpatialHierarchyResult] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("places/build-hierarchy")):
      job(
        com.histmersion.api.catalogue.PlacesJobKinds.PlacesHierarchy.kind.value,
        com.histmersion.domain.catalogue.places.GazetteerSourceRow.Key("build the spatial hierarchy")
      ) {
        val r = geometry.buildHierarchy()
        Right(s"${r.edgesCreated} edges from ${r.polygonPlaces} polygons × ${r.pointPlaces} points")
      }.map(j => SpatialHierarchyResult(edgesCreated = 0, polygonPlaces = 0, pointPlaces = 0, job = Some(j.id.value)))

object GazetteerControllers:
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
