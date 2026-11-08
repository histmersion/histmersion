package com.histmersion
package cli

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.catalogue.AttestationsMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.console.Console
import com.histmersion.api.library.Citekey

object AttestationsCmd:

  private def search(using console: Console) = Opts.subcommand("search", "Find attestations by source or target") {
    (
      Opts.option[String]("citekey", "Filter by source citekey").orNone,
      Opts.option[String]("target-type", s"Filter by target type: ${AttestationTarget.all.mkString("|")}").orNone,
      Opts.option[String]("target-id", "Filter by target entity id").orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone
    )
      .mapN { (ck, tt, ti, limit, offset) => (c: Client) =>
        c.listAttestations(
          Api.AttestationQuery(
            ck.map(Citekey(_)),
            tt.map(Api.AttestationQuery.TargetType(_)),
            ti.map(Api.AttestationQuery.TargetId(_)),
            limit.map(Api.AttestationQuery.Limit(_)),
            offset.map(Api.AttestationQuery.Offset(_))
          )
        ).map(r => Cli.out.println(AttestationsMarkdown.attestationList(r)))
      }
  }

  private def showCmd(using console: Console) = Opts.subcommand("show", "One attestation by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.getAttestation(Attestation.Id(id)).map(a => Cli.out.println(AttestationsMarkdown.attestation(a)))
    }
  }

  private def addCmd(using console: Console) =
    Opts.subcommand("add", "Create an attestation linking an entity to a source passage") {
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
          c.createAttestation(
            Attestation.Put(
              Citekey(ck),
              Attestation.TargetType(tt),
              Attestation.TargetId(ti),
              quote = quote.map(Attestation.Quote(_)),
              page = page.map(Attestation.Page(_)),
              chunkOrd = chunk.map(Attestation.ChunkOrd(_)),
              note = note.map(Attestation.Note(_))
            )
          ).map(a => Cli.out.println(AttestationsMarkdown.attestation(a)))
        }
    }

  private def rmCmd(using console: Console) = Opts.subcommand("rm", "Delete an attestation by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.deleteAttestation(Attestation.Id(id)).map(d => Cli.out.println(s"Deleted attestation #${d.id}."))
    }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("attestations", "Granular source references: search, show, add, rm") {
      search orElse showCmd orElse addCmd orElse rmCmd
    }
