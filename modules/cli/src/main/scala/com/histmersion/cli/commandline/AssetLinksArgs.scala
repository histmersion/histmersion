package com.histmersion
package cli
package commandline

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
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** The command line of [[AssetLinksCmd]]. */
@atBoundary(BoundaryKind.CliArgs, "the scallop adapter of `histmersion assetlinks`: declares its options, parses them into typed commands")
object AssetLinksArgs:
  object AssetLinksEntry extends CommandLine.Entry:
    val name = "asset-links"
    def help: String = "Semantic links between assets and entities: search, add, rm"
    def conf(): Subcommand = GroupAssetLinks()

  final private class LeafSearch()
      extends Leaf("histmersion asset-links", "search", "Find asset links")
      with SharedFlags:
    private val ck_ = value[String]("citekey", "Filter by citekey")
    private val asset_ = value[String]("asset", "Filter by asset name")
    private val tt_ = value[String]("target-type", s"Target type: ${Asset.LinkTarget.all.mkString("|")}")
    private val ti_ = value[String]("target-id", "Filter by target id")

    def command: Either[Problem, Cli.Command] = Right(
      AssetLinksCmd.Command.Search(ck_.toOption, asset_.toOption, tt_.toOption, ti_.toOption)
    )

  final private class LeafAdd() extends Leaf("histmersion asset-links", "add", "Create an asset link") with SharedFlags:
    private val ck_ = value[String]("citekey", "Source citekey", required = true)
    private val asset_ = value[String]("asset", "Asset file name", required = true)
    private val rel_ = value[String]("relation", s"Relation: ${AssetRelation.all.mkString("|")}", required = true)
    private val tt_ =
      value[String]("target-type", s"Target type: ${Asset.LinkTarget.all.mkString("|")}", required = true)
    private val ti_ = value[String]("target-id", "Target entity id", required = true)
    private val xywh_ = value[String]("xywh", "Region within the asset (x,y,w,h)")
    private val notes_ = value[String]("notes", "Free-text notes")

    def command: Either[Problem, Cli.Command] = Right(
      AssetLinksCmd.Command.Add(ck_(), asset_(), rel_(), tt_(), ti_(), xywh_.toOption, notes_.toOption)
    )

  final private class LeafRm()
      extends Leaf("histmersion asset-links", "rm", "Delete an asset link by id")
      with SharedFlags:
    private val id_ = arg[Long]("id")

    def command: Either[Problem, Cli.Command] = Right(AssetLinksCmd.Command.Rm(id_()))

  final private class GroupAssetLinks()
      extends Group("histmersion", "asset-links", "Semantic links between assets and entities: search, add, rm"):
    holds(LeafSearch(), LeafAdd(), LeafRm())
