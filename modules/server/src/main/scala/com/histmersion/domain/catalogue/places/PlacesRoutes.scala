package com.histmersion.domain.catalogue.places

import com.histmersion.api.{Api, ApiError, GazetteerSourceInfo, JobInfo, PlaceDetail}
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The gazetteer's endpoints bound to their controllers, in the order route matching needs: the `/{id}` route is a
  * prefix of `/{id}/children` and `/{id}/tree`, so the longer paths come first. `card` is a place's page with what the
  * corpus holds for it; `sources` and `load` are the init job's (the pinned extracts and the drop folder).
  */
object PlacesRoutes:
  def apply(
      places: PlacesControllers,
      gazetteer: GazetteerControllers,
      card: (String, Option[Long], Boolean) => Either[ApiError, PlaceDetail],
      sources: () => List[GazetteerSourceInfo],
      load: (String, Boolean) => Either[ApiError, JobInfo]
  ): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.places.serverLogicSuccess[Identity]((q, asOf, limit) => places.search(q, asOf, limit)),
      Api.placesGeo.serverLogic[Identity](q => places.shapes(q)),
      Api.placeNames.serverLogicSuccess[Identity](place => places.manualNames(place)),
      Api.placeOverrides.serverLogicSuccess[Identity](id => places.overrides(id)),
      Api.placeSources.serverLogic[Identity](_ => Right(sources())),
      Api.placeChildren.serverLogic[Identity]((id, asOf) => places.children(id, asOf)),
      Api.placeTree.serverLogic[Identity]((id, asOf, depth) => places.tree(id, asOf, depth)),
      Api.placeFootprints.serverLogic[Identity]((id, asOf) => places.footprints(id, asOf)),
      Api.placeLinks.serverLogic[Identity](id => places.links(id)),
      Api.place.serverLogic[Identity]((id, asOf, counts) => card(id, asOf, counts.getOrElse(false))),
      Api.addPlaceName.serverLogic[Identity]((id, in) => places.addName(me, id, in)),
      Api.removePlaceName.serverLogic[Identity]((id, in) => places.removeName(me, id, in)),
      Api.setPlaceOverride.serverLogic[Identity]((id, in) => places.setOverride(me, id, in)),
      Api.mergePlace.serverLogic[Identity]((id, in) => places.merge(me, id, in)),
      Api.removePlace.serverLogic[Identity]((id, in) => places.remove(me, id, in)),
      Api.addPlace.serverLogic[Identity](in => places.add(me, in)),
      Api.importPlaces.serverLogic[Identity]((kind, dataset, body) => places.importPlaces(me, kind, dataset, body)),
      Api.rebuildPlaces.serverLogic[Identity](_ => places.rebuild(me)),
      Api.fetchExternalPlaces.serverLogic[Identity]((source, dryRun) => gazetteer.fetch(me, source, dryRun)),
      Api.loadPlaces.serverLogic[Identity]((source, apply) => load(source, apply)),
      Api.buildHierarchy.serverLogic[Identity](_ => gazetteer.buildHierarchy(me))
    )
