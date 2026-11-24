package com.histmersion
package domain.media
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.media.MediaMarkdown

import chimp.server.*
import com.histmersion.api.Markdown
import com.histmersion.api.media.{AssetPatch, AssetsMarkdown}
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.domain.media.links.AssetLinksControllers
import com.histmersion.api.StrictJson
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.problem.Problem
import com.histmersion.api.Api
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.AssetLink
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.api.media.MediaItem
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.AssetRelation
import com.histmersion.api.media.MediaTarget
import com.histmersion.domain.media.links.AssetLinkService
import com.histmersion.domain.Creator

/** Media's agent tools, in their place among `/mcp`'s: a source's figures, a figure's description, its links. The
  * imports and the sources' pictures are not tools, on purpose (an operator's decision; a picture is for the reader).
  * `sourceExists` says whether a citekey names a source (the library's), for an empty answer's wording.
  */
object MediaTools:
  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)

  import com.histmersion.util.mcp.ToolInputs.given

  final case class AssetsInput(citekey: Citekey) derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  final case class PatchAssetInput(
      citekey: Citekey,
      name: PatchAssetInput.Name,
      kind: Option[PatchAssetInput.Kind] = None,
      caption: Option[PatchAssetInput.Caption] = None,
      license: Option[PatchAssetInput.License] = None,
      lat: Option[Latitude] = None,
      lon: Option[Longitude] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None
  ) derives StrictJson
  object PatchAssetInput:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Caption = Caption.T
    object Caption extends Newtype[String, Pure]
    type License = License.T
    object License extends Newtype[String, Pure]

    given Schema[PatchAssetInput] = KindlingsSchema
      .derived[PatchAssetInput]
      .schema
      .modify(_.citekey)(_.description("Source citekey"))
      .modify(_.name)(_.description("Asset file name"))
      .modify(_.kind)(_.description("Asset kind: figure, photo, map, plan, scan"))

  final case class AssetLinksInput(
      citekey: Option[Citekey] = None,
      asset: Option[AssetLinksInput.Asset] = None,
      targetType: Option[AssetLinksInput.TargetType] = None,
      targetId: Option[AssetLinksInput.TargetId] = None,
      relation: Option[AssetLinksInput.Relation] = None,
      xywh: Option[AssetLinksInput.Xywh] = None,
      notes: Option[AssetLinksInput.Notes] = None
  ) derives JsonCodec
  object AssetLinksInput:
    type Asset = Asset.T
    object Asset extends Newtype[String, Pure]
    export assets.PictureTarget.Wire as TargetType
    type TargetId = TargetId.T
    object TargetId extends Newtype[String, Pure]
    type Relation = Relation.T
    object Relation extends Newtype[String, Pure]
    type Xywh = Xywh.T
    object Xywh extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]

    given Schema[AssetLinksInput] = KindlingsSchema
      .derived[AssetLinksInput]
      .schema
      .modify(_.citekey)(_.description("Filter by citekey (or required for create)"))
      .modify(_.asset)(_.description("Filter by asset name (or required for create)"))
      .modify(_.targetType)(_.description("Filter by target type: event, finding, place, source, article"))
      .modify(_.targetId)(_.description("Filter by target id"))
      .modify(_.relation)(_.description("Set to create a link: depicts, taken_at, illustrates, page_of"))

  final case class DeleteAssetLinkInput(id: DeleteAssetLinkInput.Id) derives JsonCodec
  object DeleteAssetLinkInput:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]

    given Schema[DeleteAssetLinkInput] = KindlingsSchema
      .derived[DeleteAssetLinkInput]
      .schema
      .modify(_.id)(_.description("Asset link id to delete"))

  def apply(assets: AssetsControllers, links: AssetLinksControllers, sourceExists: Citekey => Boolean) =
    List(
      tool("assets")
        .description(
          "The figures stored with a source — name, mime, pixel size and the URL each is served at. " +
            "The Markdown body links them as `assets/<name>`; `get_source` rewrites those to absolute " +
            "URLs you can fetch. Use to check what figures are available — not to read the source."
        )
        .input[AssetsInput]
        .handle { in =>
          val rows = assets.figures(in.citekey)
          if rows.isEmpty && !sourceExists(in.citekey) then
            McpOut.error(
              Api.Error(Problem.NotFound.Entity(Problem.TypeName("Citekey"), Problem.Raw.of(in.citekey))),
              "Find a citekey with search."
            )
          else
            ToolResult.text(AssetsMarkdown.assets(com.histmersion.api.library.Citekey(in.citekey.value), rows.toVector))
        },
      tool("patch_asset")
        .description(
          "Update metadata on an asset — kind (figure/photo/map/plan/scan), caption, license, " +
            "geom (lat/lon) and period (periodStart/periodEnd as signed years)."
        )
        .input[PatchAssetInput]
        .handle { in =>
          assets.patch(
            in.citekey,
            AssetService.AssetName(in.name.value),
            AssetPatch(
              kind = in.kind.map(k => MediaItem.Kind(k.value)),
              caption = in.caption.map(c => MediaItem.Caption(c.value)),
              license = in.license.map(l => MediaItem.License(l.value)),
              lat = in.lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
              lon = in.lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
              periodStart = in.periodStart.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
              periodEnd = in.periodEnd.map(y => com.histmersion.api.catalogue.Year(y.value.toLong))
            )
          ) match
            case Right(d) =>
              ToolResult.text(MediaMarkdown.assetDetail(com.histmersion.api.library.Citekey(in.citekey.value), d))
            case Left(e) => McpOut.error(e)
        },
      tool("asset_links")
        .description(
          "Search/show ASSET LINKS — semantic relationships between assets and entities. " +
            "Filter by citekey, asset name, target_type (event/finding/place/source/article), target_id. " +
            "With `create` fields, creates a new link instead."
        )
        .input[AssetLinksInput]
        .handle { in =>
          in.relation match
            case Some(rel) =>
              val put = AssetLink.Put(
                citekey = com.histmersion.api.library.Citekey(in.citekey.fold("")(_.value)),
                assetName = Bundle.Asset.FileName(in.asset.fold("")(_.value)),
                relation = AssetRelation.Wire(rel.value),
                targetType = MediaTarget.TargetType(in.targetType.fold("")(_.value)),
                targetId = MediaTarget.TargetId(in.targetId.fold("")(_.value)),
                xywh = in.xywh.map(x => AssetLink.Xywh(x.value)),
                notes = in.notes.map(n => AssetLink.Notes(n.value))
              )
              links.create(put) match
                case Right(l) => ToolResult.text(MediaMarkdown.assetLink(l))
                case Left(e)  => McpOut.error(e)
            case None =>
              ToolResult.text(
                MediaMarkdown.assetLinkList(
                  links.list(
                    in.citekey,
                    in.asset.map(a => AssetService.AssetUid(a.value)),
                    in.targetType.map(t => AssetService.TargetType(t.value)),
                    in.targetId.map(t => AssetService.TargetId(t.value))
                  )
                )
              )
        },
      tool("delete_asset_link")
        .description("Delete an asset link by id.")
        .input[DeleteAssetLinkInput]
        .handle { in =>
          links.delete(AssetLinkService.LinkId(in.id.value)) match
            case Right(d) => ToolResult.text(s"Deleted asset link #${d.id}.")
            case Left(e)  => McpOut.error(e)
        }
    )
