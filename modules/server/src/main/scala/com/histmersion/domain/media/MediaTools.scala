package com.histmersion.domain.media

import com.histmersion.api.media.MediaMarkdown

import chimp.server.*
import com.histmersion.api.Markdown
import com.histmersion.api.media.{AssetPatch, AssetsMarkdown}
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.domain.media.links.AssetLinksControllers
import com.histmersion.api.StrictJson
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.media.AssetLink

/** Media's agent tools, in their place among `/mcp`'s: a source's figures, a figure's description, its links. The
  * imports and the sources' pictures are not tools, on purpose (an operator's decision; a picture is for the reader).
  * `sourceExists` says whether a citekey names a source (the library's), for an empty answer's wording.
  */
object MediaTools:

  import com.histmersion.util.mcp.ToolInputs.given

  final case class AssetsInput(citekey: com.histmersion.domain.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class PatchAssetInput(
      citekey: com.histmersion.domain.library.Citekey,
      name: PatchAssetInput.Name,
      kind: Option[PatchAssetInput.Kind] = None,
      caption: Option[PatchAssetInput.Caption] = None,
      license: Option[PatchAssetInput.License] = None,
      lat: Option[com.histmersion.Latitude] = None,
      lon: Option[com.histmersion.Longitude] = None,
      periodStart: Option[com.histmersion.Year] = None,
      periodEnd: Option[com.histmersion.Year] = None
  )
  object PatchAssetInput:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Caption = Caption.T
    object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PatchAssetInput] = KindlingsSchema
      .derived[PatchAssetInput]
      .schema
      .modify(_.citekey)(_.description("Source citekey"))
      .modify(_.name)(_.description("Asset file name"))
      .modify(_.kind)(_.description("Asset kind: figure, photo, map, plan, scan"))
    given Codec.AsObject[PatchAssetInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchAssetInput]) // #813

  final case class AssetLinksInput(
      citekey: Option[com.histmersion.domain.library.Citekey] = None,
      asset: Option[AssetLinksInput.Asset] = None,
      targetType: Option[AssetLinksInput.TargetType] = None,
      targetId: Option[AssetLinksInput.TargetId] = None,
      relation: Option[AssetLinksInput.Relation] = None,
      xywh: Option[AssetLinksInput.Xywh] = None,
      notes: Option[AssetLinksInput.Notes] = None
  ) derives KindlingsCodecAsObject
  object AssetLinksInput:
    type Asset = Asset.T
    object Asset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Relation = Relation.T
    object Relation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Xywh = Xywh.T
    object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[AssetLinksInput] = KindlingsSchema
      .derived[AssetLinksInput]
      .schema
      .modify(_.citekey)(_.description("Filter by citekey (or required for create)"))
      .modify(_.asset)(_.description("Filter by asset name (or required for create)"))
      .modify(_.targetType)(_.description("Filter by target type: event, finding, place, source, article"))
      .modify(_.targetId)(_.description("Filter by target id"))
      .modify(_.relation)(_.description("Set to create a link: depicts, taken_at, illustrates, page_of"))

  final case class DeleteAssetLinkInput(id: DeleteAssetLinkInput.Id) derives KindlingsCodecAsObject
  object DeleteAssetLinkInput:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[DeleteAssetLinkInput] = KindlingsSchema
      .derived[DeleteAssetLinkInput]
      .schema
      .modify(_.id)(_.description("Asset link id to delete"))

  def apply(assets: AssetsControllers, links: AssetLinksControllers, sourceExists: com.histmersion.domain.library.Citekey => Boolean) =
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
            ToolResult.text(s"_No source '${in.citekey}' — find a citekey with search._")
          else ToolResult.text(AssetsMarkdown.assets(com.histmersion.api.library.Citekey(in.citekey.value), rows.toVector))
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
            com.histmersion.domain.media.assets.AssetService.AssetName(in.name.value),
            AssetPatch(
              kind = in.kind.map(k => com.histmersion.api.media.MediaItem.Kind(k.value)),
              caption = in.caption.map(c => com.histmersion.api.media.MediaItem.Caption(c.value)),
              license = in.license.map(l => com.histmersion.api.media.MediaItem.License(l.value)),
              lat = in.lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
              lon = in.lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
              periodStart = in.periodStart.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
              periodEnd = in.periodEnd.map(y => com.histmersion.api.catalogue.Year(y.value.toLong))
            )
          ) match
            case Right(d) => ToolResult.text(MediaMarkdown.assetDetail(com.histmersion.api.library.Citekey(in.citekey.value), d))
            case Left(e)  => McpOut.error(e)
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
                assetName = com.histmersion.api.exchange.Bundle.Asset.FileName(in.asset.fold("")(_.value)),
                relation = com.histmersion.api.media.AssetRelation.Wire(rel.value),
                targetType = com.histmersion.api.media.MediaTarget.TargetType(in.targetType.fold("")(_.value)),
                targetId = com.histmersion.api.media.MediaTarget.TargetId(in.targetId.fold("")(_.value)),
                xywh = in.xywh.map(x => AssetLink.Xywh(x.value)),
                notes = in.notes.map(n => AssetLink.Notes(n.value))
              )
              links.create(put) match
                case Right(l) => ToolResult.text(MediaMarkdown.assetLink(l))
                case Left(e)  => McpOut.error(e)
            case None =>
              ToolResult.text(MediaMarkdown.assetLinkList(links.list(in.citekey, in.asset.map(a => com.histmersion.domain.media.assets.AssetService.AssetUid(a.value)), in.targetType.map(t => com.histmersion.domain.media.assets.AssetService.TargetType(t.value)), in.targetId.map(t => com.histmersion.domain.media.assets.AssetService.TargetId(t.value)))))
        },
      tool("delete_asset_link")
        .description("Delete an asset link by id.")
        .input[DeleteAssetLinkInput]
        .handle { in =>
          links.delete(com.histmersion.domain.media.links.AssetLinkService.LinkId(in.id.value)) match
            case Right(d) => ToolResult.text(s"Deleted asset link #${d.id}.")
            case Left(e)  => McpOut.error(e)
        }
    )
