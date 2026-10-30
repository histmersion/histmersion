package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Wire DTOs of the acquisition & assessment surface (plan §5.2 stream 2.6) — the ports of `discover.py`, `refs.py`,
  * `assess.py`, `followups.py`, `fetch_oa.py`, `archive_org.py` and the Calibre tools. Circe codec and tapir Schema are
  * both derived from each case class, so the REST body, the MCP tool schema and the CLI's `--json` output are the same
  * shape by construction.
  *
  * These are DTOs only — every rule that operates on them is a pure function in [[Acquire]], and every network call
  * sits behind a trait in the server module.
  */

// ── discover (OpenAlex / Crossref / Wikipedia) ────────────────────────────────────────────────

/** One work as OpenAlex/Crossref describe it, normalised the way `discover.py normalize_work` did.
  *
  * @param citekey
  *   the citekey this work WOULD get (`Acquire.citekey`), so a caller can queue it
  * @param inDb
  *   whether a source with that DOI (or citekey) is already stored
  */
final case class WorkRef(
    doi: Option[String] = None,
    openalexId: Option[String] = None,
    arxivId: Option[String] = None,
    title: Option[String] = None,
    year: Option[Int] = None,
    authors: List[String] = Nil,
    venue: Option[String] = None,
    citedByCount: Option[Int] = None,
    isOa: Boolean = false,
    oaStatus: Option[String] = None,
    oaUrl: Option[String] = None,
    isRetracted: Boolean = false,
    workType: Option[String] = None,
    abstractText: Option[String] = None,
    referencedWorks: List[String] = Nil,
    citekey: Option[String] = None,
    inDb: Boolean = false,
    oaLocations: List[String] = Nil,
    oaLicence: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WorkRef:
  given Schema[WorkRef] = summon[KindlingsSchema[WorkRef]].schema
