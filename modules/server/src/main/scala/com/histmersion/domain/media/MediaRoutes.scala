package com.histmersion
package domain.media

import com.histmersion.api.{Api, Write}
import com.histmersion.api.library.Citekey as ApiCitekey
import com.histmersion.api.media.AssetLink
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.RowCount as ApiRowCount
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.media.figures.FigureService
import com.histmersion.domain.media.links.AssetLinkService
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.domain.media.covers.CoversControllers
import com.histmersion.domain.media.figures.FiguresControllers
import com.histmersion.domain.media.imports.ImportsControllers
import com.histmersion.domain.media.links.AssetLinksControllers
import com.histmersion.domain.Creator
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** Media's endpoints bound to its features' controllers, in the order route matching needs: `sources/{ck}/assets`
  * before `sources/{ck}`, the media paths that are fixed before `media/{uid}`.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object MediaRoutes:
  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def missing(raw: String): Api.Error = Write.notFound(ApiCitekey(raw))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def citekey[A](raw: String)(use: Citekey => Either[Api.Error, A]): Either[Api.Error, A] =
    Citekey.option(raw).toRight(missing(raw)).flatMap(use)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def apply(
      assets: AssetsControllers,
      links: AssetLinksControllers,
      imports: ImportsControllers,
      covers: CoversControllers,
      figures: FiguresControllers
  ): List[ServerEndpoint[Any, Identity]] =
    List(
      Api.assets.serverLogicSuccess[Identity](ck => Citekey.option(ck.value).map(assets.figures).getOrElse(Nil)),
      Api.putAssets.serverLogic[Identity]((ck, in) => assets.putFiguresAsked(ApiCitekey(ck.value), in)),
      Api.assetDetail.serverLogic[Identity]((ck, name) =>
        citekey(ck.value)(k => assets.detail(k, AssetService.AssetName(name.value)))
      ),
      Api.patchAsset.serverLogic[Identity]((ck, name, p) =>
        citekey(ck.value)(k => assets.patch(k, AssetService.AssetName(name.value), p))
      ),
      Api.asset.serverLogic[Identity]((ck, name) =>
        citekey(ck.value)(k => assets.figure(k, AssetService.AssetName(name.value)))
          .map((bytes, mime) => (Api.FileBytes(bytes.value), Api.ContentType(mime.value)))
      ),
      // media geo + asset links (1.6)
      Api.mediaGeo.serverLogicSuccess[Identity](q => assets.mediaGeo(q)),
      Api.assetLinks.serverLogicSuccess[Identity]((ck, a, tt, ti) =>
        ck.map(k => Citekey.option(k.value)) match
          case Some(None) => AssetLink.List.of(ApiRowCount(0), Nil, UnreadPage.Offset(0))
          case keys       =>
            links.list(
              keys.flatten,
              a.map(x => AssetService.AssetUid(x.value)),
              tt.map(x => AssetService.TargetType(x.value)),
              ti.map(x => AssetService.TargetId(x.value))
            )
      ),
      Api.createAssetLink.serverLogic[Identity](in => CreatorControllers.rest(in)(links.create(in))),
      Api.deleteAssetLink.serverLogic[Identity](id => links.delete(AssetLinkService.LinkId(id.value))),
      // media from outside providers (#1446): the fixed paths before `media/{uid}`
      Api.mediaFacets.serverLogicSuccess[Identity](_ => assets.facets),
      Api.mediaProviders.serverLogicSuccess[Identity](_ => imports.providers),
      Api.mediaImport.serverLogic[Identity](req => CreatorControllers.rest(req)(imports.importMedia(req))),
      Api.mediaImportJob.serverLogic[Identity](req => CreatorControllers.rest(req)(imports.importJob(req))),
      Api.mediaCovers.serverLogic[Identity](req => CreatorControllers.rest(req)(covers.covers(req))),
      Api.mediaCoversJob.serverLogic[Identity](req => CreatorControllers.rest(req)(covers.coversJob(req))),
      Api.putSourceCover.serverLogic[Identity]((p, bytes) => covers.putCover(p, AssetService.Bytes(bytes.value))),
      Api.fillFigureLicences.serverLogic[Identity](apply => figures.fillLicences(FigureService.Apply(apply.applies))),
      Api.mediaList.serverLogicSuccess[Identity](q => assets.list(q)),
      Api.mediaShow.serverLogic[Identity](uid => assets.show(AssetService.AssetUid(uid.value))),
      Api.mediaThumb.serverLogic[Identity](uid =>
        assets
          .thumb(AssetService.AssetUid(uid.value))
          .map((bytes, mime) => (Api.FileBytes(bytes.value), Api.ContentType(mime.value)))
      ),
      Api.mediaFile.serverLogic[Identity](uid =>
        assets
          .file(AssetService.AssetUid(uid.value))
          .map((bytes, mime) => (Api.FileBytes(bytes.value), Api.ContentType(mime.value)))
      )
    )
