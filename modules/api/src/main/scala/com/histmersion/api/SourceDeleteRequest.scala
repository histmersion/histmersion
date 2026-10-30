package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** Deleting a source (#969): `POST /api/sources/{ck}/delete`, `histmersion delete <ck> [--apply]`.
  *
  * A dry run by default: the answer lists everything that would go and everything that refuses it. With `apply` the
  * delete is ONE transaction (`SourceDeleteRepo.apply`), and the stored original is removed after the commit. Not an
  * MCP tool, like `DELETE /api/articles/{slug}`: dropping a source is a person's decision, not something a model should
  * reach for while tidying.
  *
  * What it decides, once:
  *   - an article that cites the source (`[[ck]]`, an old citekey of it, or a short form unique to it) REFUSES the
  *     delete, with no override: the article has to be fixed first, or it would cite nothing. So do the events, event
  *     relations and participants recorded on its authority, and an ingest job or agent run still working on it (it
  *     would write the source back);
  *   - edges from OTHER sources to it are not lost: they are parked in `unresolved_edges`, exactly as an edge written
  *     to a work not in the corpus is (V026), and come back if it is ingested again;
  *   - everything that is the source's own (findings, outgoing edges, chunks, figures, attestations, the assessment,
  *     old citekeys, identifiers, its per-source event datings) goes with it, and so do the records that only make
  *     sense with it (other sources' attestations of its findings and edges, figure links to it, goal links, articles'
  *     reasons for leaving it out);
  *   - the `sources` row leaves a tombstone (V031), so a bundle merge does not bring it back;
  *   - Calibre is never touched; the linked Calibre id is reported so a person can decide (the CLI first checks that
  *     the entry is still in the library: [[CalibreEntryCheck]]);
  *   - the log queue records the delete.
  */
final case class SourceDeleteRequest(apply: Boolean = false) derives KindlingsSchema
object SourceDeleteRequest:
  given Schema[SourceDeleteRequest] = summon[KindlingsSchema[SourceDeleteRequest]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[SourceDeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[SourceDeleteRequest])
