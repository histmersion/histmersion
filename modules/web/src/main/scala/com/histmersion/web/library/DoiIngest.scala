package com.histmersion
package web.library
import com.histmersion.util.problem.{Legacy, Problem}

/** "Add by DOI": what a person types — a DOI, a doi.org link, `arxiv:<id>` or a bare arXiv id — as the ingest the
  * server is asked for. The web UI asks the LEGAL open-access chain only, always: the server's configuration may allow
  * fetchers without the rights holder's authorisation, and nothing a page does may set them off. With no legal copy the
  * server still records the source from its metadata. Pure.
  */
import com.histmersion.api.library.Ingest
import com.histmersion.web.ErrorLine
import com.histmersion.lint.{tolerated, LintRule}
object DoiIngest:
  private val DoiLink = "(?i)^(https?://)?(dx\\.)?doi\\.org/"
  private val Doi = "(?i)^10\\.\\d{4,9}/\\S+$"
  private val Arxiv = "(?i)^(arxiv:)?(\\d{4}\\.\\d{4,5}(v\\d+)?|[a-z-]+(\\.[a-z]{2})?/\\d{7}(v\\d+)?)$"

  /** The request for `typed`, or what is wrong with it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def request(typed: String): Either[Problem, Ingest.Request] =
    val t = typed.trim.replaceFirst(DoiLink, "").replaceFirst("(?i)^doi:\\s*", "")
    if t.isEmpty then
      Left(
        Problem.Unclassified(
          Legacy.Text("Type a DOI (e.g. 10.1017/S0003598X00000001) or an arXiv id (e.g. arxiv:2101.01234).")
        )
      )
    else if t.matches(Doi) then
      Right(Ingest.Request(id = Ingest.Request.Id(t), legalOnly = Ingest.Request.LegalOnly(true)))
    else if t.matches(Arxiv) then
      Right(
        Ingest.Request(
          id = Ingest.Request.Id(if t.toLowerCase.startsWith("arxiv:") then t else s"arxiv:$t"),
          legalOnly = Ingest.Request.LegalOnly(true)
        )
      )
    else Left(Problem.Unclassified(Legacy.Text(s"“$typed” is neither a DOI (10.…/…) nor an arXiv id.")))
