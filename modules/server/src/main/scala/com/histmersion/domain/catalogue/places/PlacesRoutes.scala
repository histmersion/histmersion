package com.histmersion
package domain.catalogue.places

import com.histmersion.api.{Api, Dataset, Write}
import com.histmersion.api.catalogue.{ManualPlaceName, Place as ApiPlace}
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.api.catalogue.Gazetteer

/** The gazetteer's endpoints bound to their controllers, in the order route matching needs: the `/{id}` route is a
  * prefix of `/{id}/children` and `/{id}/tree`, so the longer paths come first. `card` is a place's page (`GET
  * /api/places/{id}`) with what the corpus holds for it and its pictures; `sources` and `load` are the init job's (the
  * pinned extracts and the drop folder).
  */
object PlacesRoutes:
  private def missing(id: ApiPlace.Id): Api.Error =
    Api.Error(Api.Error.Message(s"No place '$id'"), Some(Api.Error.Code(Write.CodeNotFound)))

  private def exact(id: ApiPlace.Id): Either[Api.Error, Place.Id] = Place.Id.either(id.value).left.map(_ => missing(id))

  private def trimmed(id: ApiPlace.Id): Either[Api.Error, Place.Id] =
    Place.Id.either(id.value.trim).left.map(_ => missing(id))

  private def year(asOf: Option[Api.AggregateQuery.AsOf]): Option[DeepTime.SignedYear] =
    asOf.map(y => DeepTime.SignedYear(y.value))

  def apply(
      places: PlacesControllers,
      gazetteer: GazetteerControllers,
      card: ServerEndpoint[Any, Identity],
      sources: () => List[Gazetteer.SourceInfo],
      load: (Api.ExternalPlaces.Source, Api.Apply) => Either[Api.Error, JobInfo]
  ): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.places.serverLogicSuccess[Identity]((q, asOf, limit) =>
        places.search(
          q.map(t => PlaceService.SearchQ(t.value)),
          year(asOf),
          limit.flatMap(l => PageSize.option(l.value))
        )
      ),
      Api.placesGeo.serverLogic[Identity](q => places.shapes(q)),
      Api.placeNames.serverLogicSuccess[Identity](place =>
        place.map(_.value.trim).filter(_.nonEmpty) match
          case None      => places.manualNames(None)
          case Some(raw) => Place.Id.option(raw).fold(List.empty[ManualPlaceName])(i => places.manualNames(Some(i)))
      ),
      Api.placeOverrides.serverLogicSuccess[Identity](id =>
        id.map(_.value.trim).filter(_.nonEmpty) match
          case None      => places.overrides(None)
          case Some(raw) => Place.Id.option(raw).fold(List.empty[ApiPlace.Override])(i => places.overrides(Some(i)))
      ),
      Api.placeSources.serverLogic[Identity](_ => Right(sources())),
      Api.placeChildren.serverLogic[Identity]((id, asOf) => exact(id).flatMap(i => places.children(i, year(asOf)))),
      Api.placeTree.serverLogic[Identity]((id, asOf, depth) =>
        exact(id).flatMap(i => places.tree(i, year(asOf), depth.map(d => PlaceService.DepthLimit(d.value))))
      ),
      Api.placeFootprints.serverLogic[Identity]((id, asOf) => exact(id).flatMap(i => places.footprints(i, year(asOf)))),
      Api.placeLinks.serverLogic[Identity](id => exact(id).flatMap(places.links)),
      card,
      Api.addPlaceName.serverLogic[Identity]((id, in) => trimmed(id).flatMap(i => places.addName(me, i, in))),
      Api.removePlaceName.serverLogic[Identity]((id, in) => trimmed(id).flatMap(i => places.removeName(me, i, in))),
      Api.setPlaceOverride.serverLogic[Identity]((id, in) => trimmed(id).flatMap(i => places.setOverride(me, i, in))),
      Api.mergePlace.serverLogic[Identity]((id, in) => trimmed(id).flatMap(i => places.merge(me, i, in))),
      Api.removePlace.serverLogic[Identity]((id, in) => trimmed(id).flatMap(i => places.remove(me, i, in))),
      Api.addPlace.serverLogic[Identity](in => places.add(me, in)),
      Api.importPlaces.serverLogic[Identity]((kind, dataset, body) => 
        places.importPlaces(
          me,
          kind.map(k => Place.Kind.Wire(k.value)),
          dataset.map(d => Dataset.Wire(d.value)),
          PlacesControllers.DatasetBody(body.value)
        )),
      Api.rebuildPlaces.serverLogic[Identity](_ => places.rebuild(me)),
      Api.fetchExternalPlaces.serverLogic[Identity]((source, dryRun) => gazetteer.fetch(me, source, dryRun)),
      Api.loadPlaces.serverLogic[Identity]((source, apply) => load(source, apply)),
      Api.buildHierarchy.serverLogic[Identity](_ => gazetteer.buildHierarchy(me))
    )
