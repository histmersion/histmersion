package com.histmersion
package cli
package commandline

import com.histmersion.domain.catalogue.AttestationTarget
import com.histmersion.api.catalogue.AttestationsMarkdown
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.console.Console
import com.histmersion.api.library.Citekey
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[AttestationsCmd]]. */
object AttestationsArgs:
  object AttestationsEntry extends CommandLine.Entry:
    val name = "attestations"
    def help: String = "Granular source references: search, show, add, rm"
    def conf(): Subcommand = GroupAttestations()

  final private class LeafSearch() extends Leaf("histmersion attestations", "search", "Find attestations by source or target") with SharedFlags:
    private val ck_ = value[String]("citekey", "Filter by source citekey")
    private val tt_ = value[String]("target-type", s"Filter by target type: ${AttestationTarget.all.mkString("|")}")
    private val ti_ = value[String]("target-id", "Filter by target entity id")
    private val limit_ = value[Int]("limit", "Max results")
    private val offset_ = value[Int]("offset", "Skip this many (the list's footer names the next offset)")

    def command: Either[Problem, Cli.Command] = Right(AttestationsCmd.Command.Search(ck_.toOption, tt_.toOption, ti_.toOption, limit_.toOption, offset_.toOption))

  final private class LeafShow() extends Leaf("histmersion attestations", "show", "One attestation by id") with SharedFlags:
    private val id_ = arg[Long]("id")

    def command: Either[Problem, Cli.Command] = Right(AttestationsCmd.Command.Show(id_()))

  final private class LeafAdd() extends Leaf("histmersion attestations", "add", "Create an attestation linking an entity to a source passage") with SharedFlags:
    private val ck_ = value[String]("citekey", "Source citekey", required = true)
    private val tt_ = value[String]("target-type", s"Target type: ${AttestationTarget.all.mkString("|")}", required = true)
    private val ti_ = value[String]("target-id", "Target entity id", required = true)
    private val quote_ = value[String]("quote", "Exact quoted passage")
    private val page_ = value[String]("page", "Page/folio label")
    private val chunk_ = value[Int]("chunk", "source_chunks ordinal")
    private val note_ = value[String]("note", "Note")

    def command: Either[Problem, Cli.Command] = Right(AttestationsCmd.Command.Add(ck_(), tt_(), ti_(), quote_.toOption, page_.toOption, chunk_.toOption, note_.toOption))

  final private class LeafRm() extends Leaf("histmersion attestations", "rm", "Delete an attestation by id") with SharedFlags:
    private val id_ = arg[Long]("id")

    def command: Either[Problem, Cli.Command] = Right(AttestationsCmd.Command.Rm(id_()))

  final private class GroupAttestations() extends Group("histmersion", "attestations", "Granular source references: search, show, add, rm"):
    holds(LeafSearch(), LeafShow(), LeafAdd(), LeafRm())
