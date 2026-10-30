package com.histmersion.server.acquire.fetchers

import com.histmersion.pluginhost.FetchChain

import com.histmersion.plugin.*
import com.histmersion.server.acquire.{FetchedFile, Http}

import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** What the built-in fetchers share: one download, checked. */
private[acquire] object Fetching:

  /** Download `url`; a copy only when it is a PDF. Any other answer is logged and is no copy. */
  def tryUrl(http: Http, source: String, url: String, headers: Map[String, String] = Map.empty): Option[FetchedFile] =
    http.bytes(url, headers) match
      case Right(b) if FetchChain.looksLikePdf(b) => Some(FetchedFile(source, Some(url), b, "pdf"))
      case Right(b)                               =>
        scribe.info(s"acquire: $source: $url returned ${b.length} bytes that are not a PDF — skipping")
        None
      case Left(e) =>
        scribe.info(s"acquire: $source fetch failed ($e)")
        None

  /** A step's answer: the copy, or a miss saying which identifier it needed or that it found none. */
  def answer(found: Option[FetchedFile], none: => String): Fetch =
    found.fold(Fetch.Miss(none))(Fetch.Found(_))

  /** A fetcher from a function — the built-ins are all open access. */
  def open(f: (WorkKey, FetchContext) => Fetch): SourceFetcher = new SourceFetcher:
    val access: Access = Access.Open
    def fetch(work: WorkKey, ctx: FetchContext): Fetch = f(work, ctx)
