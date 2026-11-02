package com.histmersion.cli

import com.histmersion.api.media.MediaMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.media.AssetRelation
import com.histmersion.domain.media.Asset
import com.histmersion.api.media.AssetLink

object AssetLinksCmd:

  private val search = Opts.subcommand("search", "Find asset links") {
    (
      Opts.option[String]("citekey", "Filter by citekey").orNone,
      Opts.option[String]("asset", "Filter by asset name").orNone,
      Opts.option[String]("target-type", s"Target type: ${Asset.LinkTarget.all.mkString("|")}").orNone,
      Opts.option[String]("target-id", "Filter by target id").orNone
    )
      .mapN { (ck, asset, tt, ti) => (c: Client) =>
        c.assetLinks(ck, asset, tt, ti).map(r => println(MediaMarkdown.assetLinkList(r)))
      }
  }

  private val addCmd = Opts.subcommand("add", "Create an asset link") {
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
        c.createAssetLink(AssetLink.Put(ck, asset, rel, tt, ti, xywh, notes))
          .map(l => println(MediaMarkdown.assetLink(l)))
      }
  }

  private val rmCmd = Opts.subcommand("rm", "Delete an asset link by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.deleteAssetLink(id).map(d => println(s"Deleted asset link #${d.id}."))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("asset-links", "Semantic links between assets and entities: search, add, rm") {
      search orElse addCmd orElse rmCmd
    }
