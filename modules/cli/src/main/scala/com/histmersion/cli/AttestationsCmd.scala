package com.histmersion
package cli

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.catalogue.AttestationsMarkdown

import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.console.Console
import com.histmersion.api.library.Citekey

object AttestationsCmd:

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Search(ck: Option[String], tt: Option[String], ti: Option[String], limit: Option[Int], offset: Option[Int])
    case Show(id: Long)
    case Add(
        ck: String,
        tt: String,
        ti: String,
        quote: Option[String],
        page: Option[String],
        chunk: Option[Int],
        note: Option[String]
    )
    case Rm(id: Long)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Search(ck, tt, ti, limit, offset) =>
          c.listAttestations(
            Api.AttestationQuery(
              ck.map(Citekey(_)),
              tt.map(Api.AttestationQuery.TargetType(_)),
              ti.map(Api.AttestationQuery.TargetId(_)),
              limit.map(Api.AttestationQuery.Limit(_)),
              offset.map(Api.AttestationQuery.Offset(_))
            )
          ).map(r => Cli.out.println(AttestationsMarkdown.attestationList(r)))
        case Command.Show(id) =>
          c.getAttestation(Attestation.Id(id)).map(a => Cli.out.println(AttestationsMarkdown.attestation(a)))
        case Command.Add(ck, tt, ti, quote, page, chunk, note) =>
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
        case Command.Rm(id) =>
          c.deleteAttestation(Attestation.Id(id)).map(d => Cli.out.println(s"Deleted attestation #${d.id}."))
