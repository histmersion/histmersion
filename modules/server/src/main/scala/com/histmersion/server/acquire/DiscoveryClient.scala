package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
trait DiscoveryClient:
  def search(query: String, since: Option[Int], sort: Option[String], limit: Int): Either[String, List[WorkRef]]
  def meta(doi: String): Either[String, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: String): Either[String, (String, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: String): Either[String, List[UpdateNotice]]
