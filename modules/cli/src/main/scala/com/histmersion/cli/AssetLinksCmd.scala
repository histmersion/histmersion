package com.histmersion
package cli

import com.histmersion.api.media.MediaMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.media.AssetRelation
import com.histmersion.domain.media.Asset
import com.histmersion.api.media.AssetLink
import com.histmersion.util.console.Console
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.media.MediaTarget

object AssetLinksCmd:

  private def search(using console: Console) = Opts.subcommand("search", "Find asset links") {
    (
      Opts.option[String]("citekey", "Filter by citekey").orNone,
      Opts.option[String]("asset", "Filter by asset name").orNone,
      Opts.option[String]("target-type", s"Target type: ${Asset.LinkTarget.all.mkString("|")}").orNone,
      Opts.option[String]("target-id", "Filter by target id").orNone
    )
      .mapN { (ck, asset, tt, ti) => (c: Client) =>
        c.assetLinks(
          ck.map(Citekey(_)),
          asset.map(Bundle.Asset.Variant(_)),
          tt.map(Attestation.TargetType(_)),
          ti.map(Attestation.TargetId(_))
        ).map(r => Cli.out.println(MediaMarkdown.assetLinkList(r)))
      }
  }

  private def addCmd(using console: Console) = Opts.subcommand("add", "Create an asset link") {
    (
      Opts.option[String]("citekey", "Source citekey"),
      Opts.option[String]("asset", "Asset file name"),
      Opts.option[String]("relation", s"Relation: ${AssetRelation.all.mkString("|")}"),
      Opts.option[String]("target-type", s"Target type: ${Asset.LinkTarget.all.mkString("|")}"),
      Opts.option[String]("target-id", "Target entity id"),
      Opts.option[String]("xywh", "Region within the asset (x,y,w,h)").orNone,
      Opts.option[String]("notes", "Free-text notes").orNone
    )
      .mapN { (ck, asset, rel, tt, ti, xywh, notes) => (c: Client) =>
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
      }
  }

  private def rmCmd(using console: Console) = Opts.subcommand("rm", "Delete an asset link by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.deleteAssetLink(AssetLink.Id(id)).map(d => Cli.out.println(s"Deleted asset link #${d.id}."))
    }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("asset-links", "Semantic links between assets and entities: search, add, rm") {
      search orElse addCmd orElse rmCmd
    }
