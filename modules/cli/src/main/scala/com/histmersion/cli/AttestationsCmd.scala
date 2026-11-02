package com.histmersion.cli

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.catalogue.AttestationsMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.AttestationPut

object AttestationsCmd:

  private val search = Opts.subcommand("search", "Find attestations by source or target") {
    (
      Opts.option[String]("citekey", "Filter by source citekey").orNone,
      Opts.option[String]("target-type", s"Filter by target type: ${AttestationTarget.all.mkString("|")}").orNone,
      Opts.option[String]("target-id", "Filter by target entity id").orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone
    )
      .mapN { (ck, tt, ti, limit, offset) => (c: Client) =>
        c.listAttestations(Api.AttestationQuery(ck, tt, ti, limit, offset))
          .map(r => println(AttestationsMarkdown.attestationList(r)))
      }
  }

  private val showCmd = Opts.subcommand("show", "One attestation by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.getAttestation(id).map(a => println(AttestationsMarkdown.attestation(a)))
    }
  }

  private val addCmd = Opts.subcommand("add", "Create an attestation linking an entity to a source passage") {
    (
      Opts.option[String]("citekey", "Source citekey"),
      Opts.option[String]("target-type", s"Target type: ${AttestationTarget.all.mkString("|")}"),
      Opts.option[String]("target-id", "Target entity id"),
      Opts.option[String]("quote", "Exact quoted passage").orNone,
      Opts.option[String]("page", "Page/folio label").orNone,
      Opts.option[Int]("chunk", "source_chunks ordinal").orNone,
      Opts.option[String]("note", "Note").orNone
    )
      .mapN { (ck, tt, ti, quote, page, chunk, note) => (c: Client) =>
        c.createAttestation(AttestationPut(ck, tt, ti, quote = quote, page = page, chunkOrd = chunk, note = note))
          .map(a => println(AttestationsMarkdown.attestation(a)))
      }
  }

  private val rmCmd = Opts.subcommand("rm", "Delete an attestation by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.deleteAttestation(id).map(d => println(s"Deleted attestation #${d.id}."))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("attestations", "Granular source references: search, show, add, rm") {
      search orElse showCmd orElse addCmd orElse rmCmd
    }
