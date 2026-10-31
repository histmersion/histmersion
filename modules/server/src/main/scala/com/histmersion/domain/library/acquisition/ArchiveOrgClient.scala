package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.server.acquire.{ArchiveClient, FetchedFile}
import com.histmersion.upstreams

/** archive.org ([[upstreams.ArchiveOrg]]), its items as the wire shows them. */
final class ArchiveOrgClient(client: upstreams.ArchiveOrg) extends ArchiveClient:
  def search(query: String, rows: Int): Either[String, List[ArchiveItem]] = client.search(query, rows).map(_.map(_.wire))
  def info(identifier: String): Either[String, ArchiveItem] = client.info(identifier).map(_.wire)
  def download(identifier: String, file: String): Either[String, FetchedFile] = client.download(identifier, file)
