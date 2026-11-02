package com.histmersion.domain.catalogue.places

import com.histmersion.api.{ApiError, SpatialHierarchyResult}
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.catalogue.PlaceImportResult
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, WorkCarrier}

import javax.sql.DataSource

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
  private def job(kind: String, about: String)(run: => Either[String, String]): Either[ApiError, JobInfo] =
    BackgroundJob.start(ds, kind, Some(JobDetail.Purpose(Some(about))), slot, "a places job", pins = pins)(
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
  def fetch(caller: Caller, source: String, dryRun: Boolean): Either[ApiError, PlaceImportResult] =
    maintenance.guard("places/fetch"):
      if source.trim.toLowerCase == NaturalEarthLand.name then land(dryRun)
      else
        val name = source.trim.toLowerCase
        imports
          .source(
            name,
            s"unknown source '$source' (available: ${(imports.available :+ NaturalEarthLand.name).mkString(", ")})"
          )
          .left
          .map(ApiError(_))
          .flatMap { src =>
            if dryRun then imports.fetch(src, dryRun = true).left.map(ApiError(_))
            else
              job(com.histmersion.api.catalogue.PlacesJobKinds.PlacesFetch.kind.value, s"fetch $name")(
                imports
                  .fetch(src, dryRun = false)
                  .map(r =>
                    s"${r.places} places, ${r.names} names, ${r.relations} relations" +
                      (if r.failed.nonEmpty then s", ${r.failed.size} failed" else "")
                  )
              ).map(j => PlaceImportResult(0, 0, 0, job = Some(j.id)))
          }

  /** `places fetch land`: Natural Earth land for clipping polities to the coast (V025); like the gazetteers, a real run
    * is a background job — two downloads and a re-clip of every polity.
    */
  private def land(dryRun: Boolean): Either[ApiError, PlaceImportResult] =
    if dryRun then imports.land(dryRun = true).left.map(ApiError(_))
    else
      job(com.histmersion.api.catalogue.PlacesJobKinds.PlacesFetch.kind.value, "fetch land")(
        imports
          .land(dryRun = false)
          .map(r => s"${r.places} polities clipped${r.failed.mkString(" ", "; ", "")}".trim)
      ).map(j => PlaceImportResult(0, 0, 0, job = Some(j.id)))

  /** `POST /api/places/build-hierarchy` — parent-child edges from spatial containment, as a background job (ST_Contains
    * across thousands of polygons is slow): the answer carries its id, the finished job's summary the counts.
    */
  def buildHierarchy(caller: Caller): Either[ApiError, SpatialHierarchyResult] =
    maintenance.guard("places/build-hierarchy"):
      job(
        com.histmersion.api.catalogue.PlacesJobKinds.PlacesHierarchy.kind.value,
        "build the spatial hierarchy"
      ) {
        val r = geometry.buildHierarchy()
        Right(s"${r.edgesCreated} edges from ${r.polygonPlaces} polygons × ${r.pointPlaces} points")
      }.map(j => SpatialHierarchyResult(edgesCreated = 0, polygonPlaces = 0, pointPlaces = 0, job = Some(j.id)))
