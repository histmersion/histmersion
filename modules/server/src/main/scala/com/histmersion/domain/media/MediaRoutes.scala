package com.histmersion.domain.media

import com.histmersion.api.Api
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.domain.media.covers.CoversControllers
import com.histmersion.domain.media.figures.FiguresControllers
import com.histmersion.domain.media.imports.ImportsControllers
import com.histmersion.domain.media.links.AssetLinksControllers
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** Media's endpoints bound to its features' controllers, in the order route matching needs: `sources/{ck}/assets`
  * before `sources/{ck}`, the media paths that are fixed before `media/{uid}`.
  */
object MediaRoutes:
  def apply(
      assets: AssetsControllers,
      links: AssetLinksControllers,
      imports: ImportsControllers,
      covers: CoversControllers,
      figures: FiguresControllers
  ): List[ServerEndpoint[Any, Identity]] =
    List(
      Api.assets.serverLogicSuccess[Identity](ck => assets.figures(ck)),
      Api.putAssets.serverLogic[Identity]((ck, in) => assets.putFigures(ck, in)),
      Api.assetDetail.serverLogic[Identity]((ck, name) => assets.detail(ck, name)),
      Api.patchAsset.serverLogic[Identity]((ck, name, p) => assets.patch(ck, name, p)),
      Api.asset.serverLogic[Identity]((ck, name) => assets.figure(ck, name)),
      // media geo + asset links (1.6)
      Api.mediaGeo.serverLogicSuccess[Identity](q => assets.mediaGeo(q)),
      Api.assetLinks.serverLogicSuccess[Identity]((ck, a, tt, ti) => links.list(ck, a, tt, ti)),
      Api.createAssetLink.serverLogic[Identity](in => links.create(in)),
      Api.deleteAssetLink.serverLogic[Identity](id => links.delete(id)),
      // media from outside providers (#1446): the fixed paths before `media/{uid}`
      Api.mediaFacets.serverLogicSuccess[Identity](_ => assets.facets),
      Api.mediaProviders.serverLogicSuccess[Identity](_ => imports.providers),
      Api.mediaImport.serverLogic[Identity](req => imports.importMedia(req)),
      Api.mediaImportJob.serverLogic[Identity](req => imports.importJob(req)),
      Api.mediaCovers.serverLogic[Identity](req => covers.covers(req)),
      Api.mediaCoversJob.serverLogic[Identity](req => covers.coversJob(req)),
      Api.putSourceCover.serverLogic[Identity]((p, bytes) => covers.putCover(p, bytes)),
      Api.fillFigureLicences.serverLogic[Identity](apply => figures.fillLicences(apply)),
      Api.mediaList.serverLogicSuccess[Identity](q => assets.list(q)),
      Api.mediaShow.serverLogic[Identity](uid => assets.show(uid)),
      Api.mediaThumb.serverLogic[Identity](uid => assets.thumb(uid)),
      Api.mediaFile.serverLogic[Identity](uid => assets.file(uid))
    )
