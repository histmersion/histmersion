package com.histmersion
package cli

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.config.*
import com.histmersion.api.media.AssetRelation
import com.histmersion.domain.media.Asset
import com.histmersion.api.media.AssetLink
import com.histmersion.util.console.Console
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.media.MediaTarget
import com.histmersion.lint.{atBoundary, BoundaryKind}

@atBoundary(BoundaryKind.CliArgs, "parses `histmersion asset-links` arguments")
object AssetLinksCmd:

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Search(ck: Option[String], asset: Option[String], tt: Option[String], ti: Option[String])
    case Add(
        ck: String,
        asset: String,
        rel: String,
        tt: String,
        ti: String,
        xywh: Option[String],
        notes: Option[String]
    )
    case Rm(id: Long)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Search(ck, asset, tt, ti) =>
          c.assetLinks(
            ck.map(Citekey(_)),
            asset.map(Bundle.Asset.Variant(_)),
            tt.map(Attestation.TargetType(_)),
            ti.map(Attestation.TargetId(_))
          ).map(r => Cli.out.println(MediaMarkdown.assetLinkList(r)))
        case Command.Add(ck, asset, rel, tt, ti, xywh, notes) =>
          c.createAssetLink(
            AssetLink.Put(
              Citekey(ck),
              Bundle.Asset.FileName(asset),
              AssetRelation.Wire(rel),
              MediaTarget.TargetType(tt),
              MediaTarget.TargetId(ti),
              xywh.map(AssetLink.Xywh(_)),
              notes.map(AssetLink.Notes(_))
            )
          ).map(l => Cli.out.println(MediaMarkdown.assetLink(l)))
        case Command.Rm(id) =>
          c.deleteAssetLink(AssetLink.Id(id)).map(d => Cli.out.println(shown"Deleted asset link #${d.id}."))
