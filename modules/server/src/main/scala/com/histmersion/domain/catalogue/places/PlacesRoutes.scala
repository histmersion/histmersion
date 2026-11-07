package com.histmersion.domain.catalogue.places

import com.histmersion.api.Api
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
  type ApplyLoadArg2 = ApplyLoadArg2.T
  object ApplyLoadArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      places: PlacesControllers,
      gazetteer: GazetteerControllers,
      card: ServerEndpoint[Any, Identity],
      sources: () => List[Gazetteer.SourceInfo],
      load: (com.histmersion.domain.catalogue.places.GazetteerSourceRow.Key, com.histmersion.domain.catalogue.places.PlacesRoutes.ApplyLoadArg2) => Either[Api.Error, JobInfo]
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
      card,
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
