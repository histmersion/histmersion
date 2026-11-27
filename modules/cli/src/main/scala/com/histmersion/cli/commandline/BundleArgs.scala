package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.*
import java.nio.file.{Files, Path, StandardCopyOption}
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}
import com.histmersion.api.exchange.{Bundle, BundleMarkdown, MergeImportRequest}
import com.histmersion.util.console.Console
import com.histmersion.api.library.InstanceLabel
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}

/** The command line of [[BundleCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion bundle`: declares its options, parses them into typed commands"
)
object BundleArgs:
  object BundleEntry extends CommandLine.Entry:
    val name = "bundle"
    def help: String = "Portable .hmx bundles: export, verify and import a whole collection, carrying its identities"
    def conf(): Subcommand = GroupBundle()

  final private class LeafExport()
      extends Leaf(
        "histmersion bundle",
        "export",
        "Write the whole database (or, with --since, what changed after a moment, deletions included) to a portable .hmx bundle"
      )
      with SharedFlags:
    private val file_ = arg[String]("file.hmx")
    private val since_ =
      value[String]("since", "Only what changed after this moment (ISO-8601, e.g. a previous bundle's asOf)")

    def command: Either[Problem, Cli.Command] = Right(BundleCmd.Command.Export(file_(), since_.toOption))

  final private class LeafVerify()
      extends Leaf(
        "histmersion bundle",
        "verify",
        "Check a bundle against this server: format, schema, every checksum, record and blob — every problem listed"
      )
      with SharedFlags:
    private val file_ = arg[String]("file.hmx")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(BundleCmd.Command.Verify(file_(), json_()))

  final private class LeafImport()
      extends Leaf(
        "histmersion bundle",
        "import",
        "Import a bundle: what the merge plan says is new or changed, after every open issue is decided (--resolutions); refused while one is open"
      )
      with SharedFlags:
    private val file_ = arg[String]("file.hmx")
    private val writes_ = dryRun("Report what the import would do, and write nothing")
    private val res_ = resolutionFlags()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      BundleCmd.Command.Import(file_(), writes_(), res_(), json_())
    )

  final private class LeafInstance()
      extends Leaf(
        "histmersion bundle",
        "instance",
        "This collection's identity (the origin of every row it creates); --label names it, e.g. work or home"
      )
      with SharedFlags:
    private val label_ = value[String]("label", "Name this instance; an empty string clears the name")

    def command: Either[Problem, Cli.Command] = Right(BundleCmd.Command.Instance(label_.toOption))

  final private class GroupBundle()
      extends Group(
        "histmersion",
        "bundle",
        "Portable .hmx bundles: export, verify and import a whole collection, carrying its identities"
      ):
    holds(LeafExport(), LeafVerify(), LeafImport(), LeafInstance())
