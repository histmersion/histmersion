package com.histmersion.web.library

/** "Add by DOI": what a person types — a DOI, a doi.org link, `arxiv:<id>` or a bare arXiv id — as the ingest the
  * server is asked for. The web UI asks the LEGAL open-access chain only, always: the server's configuration may allow
  * fetchers without the rights holder's authorisation, and nothing a page does may set them off. With no legal copy the
  * server still records the source from its metadata. Pure.
  */
import com.histmersion.api.library.Ingest
object DoiIngest:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RequestTyped = RequestTyped.T
  object RequestTyped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val DoiLink = "(?i)^(https?://)?(dx\\.)?doi\\.org/"
  private val Doi = "(?i)^10\\.\\d{4,9}/\\S+$"
  private val Arxiv = "(?i)^(arxiv:)?(\\d{4}\\.\\d{4,5}(v\\d+)?|[a-z-]+(\\.[a-z]{2})?/\\d{7}(v\\d+)?)$"

  /** The request for `typed`, or what is wrong with it. */
  def request(typed: com.histmersion.web.library.DoiIngest.RequestTyped): Either[com.histmersion.web.library.DoiIngest.Problem, Ingest.Request] =
    val t = typed.trim.replaceFirst(DoiLink, "").replaceFirst("(?i)^doi:\\s*", "")
    if t.isEmpty then Left("Type a DOI (e.g. 10.1017/S0003598X00000001) or an arXiv id (e.g. arxiv:2101.01234).")
    else if t.matches(Doi) then Right(Ingest.Request(id = t, legalOnly = com.histmersion.api.library.Ingest.Request.LegalOnly(true)))
    else if t.matches(Arxiv) then
      Right(Ingest.Request(id = if t.toLowerCase.startsWith("arxiv:") then t else s"arxiv:$t", legalOnly = true))
    else Left(s"“$typed” is neither a DOI (10.…/…) nor an arXiv id.")
