package com.histmersion.domain.library.sources

import com.histmersion.api.Lint.{FormatChecker, LintReport, Rule}
import com.histmersion.domain.research.articles.ArticleCheckFixtures
import com.histmersion.domain.catalogue.places.GeocodeSettings
import com.histmersion.util.data.checks.CheckRun
import com.augustnagro.magnum.connect

import javax.sql.DataSource

/** The library's and the articles' lint rules as the db suites run them: one connection for the lot, each rule by its
  * owner's repository; and the SQL each rule reads.
  */
object SourceCheckFixtures:
  export SourceCheckRepo.reindexStale

  def run(
      ds: DataSource,
      rules: List[Rule],
      maxItems: Int,
      suggestions: Int,
      format: FormatChecker,
      geocode: GeocodeSettings
  ): LintReport =
    connect(ds):
      given CheckRun = CheckRun()
      val context = SourceCheckRepo.Context(suggestions, format, geocode)
      LintReport(rules.map { r =>
        if SourceCheckRepo.sqlOf(r.name).nonEmpty then SourceCheckRepo.report(r, context, maxItems)
        else ArticleCheckFixtures.report(r, format, maxItems)
      })

  def sqlOf(rule: String): List[String] = SourceCheckRepo.sqlOf(rule) ++ ArticleCheckFixtures.sqlOf(rule)
