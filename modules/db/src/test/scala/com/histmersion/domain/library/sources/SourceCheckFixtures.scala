package com.histmersion
package domain.library.sources

import com.histmersion.api.checks.Lint.{FormatChecker, LintReport, Rule}
import com.histmersion.domain.research.articles.ArticleCheckFixtures
import com.histmersion.domain.catalogue.places.GeocodeSettings
import com.histmersion.util.data.checks.CheckRun
import com.augustnagro.magnum.{connect, Frag}

import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.database.Transactor
import javax.sql.DataSource
import com.histmersion.util.ocr.Chunker
import com.histmersion.api.checks.Lint
import com.histmersion.util.database.TestPermits.given

/** The library's and the articles' lint rules as the db suites run them: one connection for the lot, each rule by its
  * owner's repository; and the SQL each rule reads.
  */
object SourceCheckFixtures:
  def reindexStale(
      ds: DataSource,
      chunkChars: Chunker.ChunkMaxChars,
      maxChars: SourceRepairs.MaxChars
  ): ReindexResult =
    SourceRepairs(Transactor(ds)).reindexStale(chunkChars, maxChars)

  def run(
      ds: DataSource,
      rules: List[Rule],
      maxItems: Lint.ItemLimit,
      suggestions: Lint.ItemLimit,
      format: FormatChecker,
      geocode: GeocodeSettings
  ): LintReport =
    connect(ds):
      given CheckRun = CheckRun()
      val context = SourceCheckRepo.Context(suggestions, format, geocode)
      LintReport(rules.map { r =>
        if SourceCheckRepo.sqlOf(r.name.value).nonEmpty then SourceCheckRepo.report(r, context, maxItems)
        else ArticleCheckFixtures.report(r, format, ArticleCheckFixtures.ReportMaxItems(maxItems.value))
      })

  def sqlOf(rule: Rule.Name): List[Frag] = SourceCheckRepo.sqlOf(rule.value) ++ ArticleCheckFixtures.sqlOf(rule.value)
