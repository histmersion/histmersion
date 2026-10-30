package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

trait ArchiveClient:
  def search(query: String, rows: Int): Either[String, List[ArchiveItem]]
  def info(identifier: String): Either[String, ArchiveItem]
  def download(identifier: String, file: String): Either[String, FetchedFile]
