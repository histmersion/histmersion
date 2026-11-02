package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.upstreams

/** Discovery over OpenAlex and Crossref ([[upstreams.OpenAlexCrossref]]), its records as the wire shows them. */
final class OpenAlexDiscovery(client: upstreams.openalex.OpenAlexCrossref) extends DiscoveryClient:
  def search(query: String, since: Option[Int], sort: Option[String], limit: Int): Either[String, List[WorkRef]] =
    client.search(query, since, sort, limit).map(_.map(_.wire))
  def meta(doi: String): Either[String, WorkRef] = client.meta(doi).map(_.wire)
  def references(doi: String): Either[String, (String, List[RefRow])] =
    client.references(doi).map((backend, rows) => (backend, rows.map(_.wire)))
  def updates(doi: String) = client.updates(doi).map(DiscoveryClient.notices)
