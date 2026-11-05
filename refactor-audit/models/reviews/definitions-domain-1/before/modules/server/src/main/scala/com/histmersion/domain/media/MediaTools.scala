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

  final case class AssetsInput(citekey: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class PatchAssetInput(
      citekey: String,
      name: String,
      kind: Option[String] = None,
      caption: Option[String] = None,
      license: Option[String] = None,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None
  )
  object PatchAssetInput:
    given Schema[PatchAssetInput] = KindlingsSchema
      .derived[PatchAssetInput]
      .schema
      .modify(_.citekey)(_.description("Source citekey"))
      .modify(_.name)(_.description("Asset file name"))
      .modify(_.kind)(_.description("Asset kind: figure, photo, map, plan, scan"))
    given Codec.AsObject[PatchAssetInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchAssetInput]) // #813

  final case class AssetLinksInput(
      citekey: Option[String] = None,
      asset: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      relation: Option[String] = None,
      xywh: Option[String] = None,
      notes: Option[String] = None
  ) derives KindlingsCodecAsObject
  object AssetLinksInput:
    given Schema[AssetLinksInput] = KindlingsSchema
      .derived[AssetLinksInput]
      .schema
      .modify(_.citekey)(_.description("Filter by citekey (or required for create)"))
      .modify(_.asset)(_.description("Filter by asset name (or required for create)"))
      .modify(_.targetType)(_.description("Filter by target type: event, finding, place, source, article"))
      .modify(_.targetId)(_.description("Filter by target id"))
      .modify(_.relation)(_.description("Set to create a link: depicts, taken_at, illustrates, page_of"))

  final case class DeleteAssetLinkInput(id: Long) derives KindlingsCodecAsObject
  object DeleteAssetLinkInput:
    given Schema[DeleteAssetLinkInput] = KindlingsSchema
      .derived[DeleteAssetLinkInput]
      .schema
      .modify(_.id)(_.description("Asset link id to delete"))

  def apply(assets: AssetsControllers, links: AssetLinksControllers, sourceExists: String => Boolean) =
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
          else ToolResult.text(AssetsMarkdown.assets(in.citekey, rows.toVector))
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
            in.name,
            AssetPatch(
              kind = in.kind,
              caption = in.caption,
              license = in.license,
              lat = in.lat,
              lon = in.lon,
              periodStart = in.periodStart,
              periodEnd = in.periodEnd
            )
          ) match
            case Right(d) => ToolResult.text(MediaMarkdown.assetDetail(in.citekey, d))
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
                citekey = in.citekey.getOrElse(""),
                assetName = in.asset.getOrElse(""),
                relation = rel,
                targetType = in.targetType.getOrElse(""),
                targetId = in.targetId.getOrElse(""),
                xywh = in.xywh,
                notes = in.notes
              )
              links.create(put) match
                case Right(l) => ToolResult.text(MediaMarkdown.assetLink(l))
                case Left(e)  => McpOut.error(e)
            case None =>
              ToolResult.text(MediaMarkdown.assetLinkList(links.list(in.citekey, in.asset, in.targetType, in.targetId)))
        },
      tool("delete_asset_link")
        .description("Delete an asset link by id.")
        .input[DeleteAssetLinkInput]
        .handle { in =>
          links.delete(in.id) match
            case Right(d) => ToolResult.text(s"Deleted asset link #${d.id}.")
            case Left(e)  => McpOut.error(e)
        }
    )
