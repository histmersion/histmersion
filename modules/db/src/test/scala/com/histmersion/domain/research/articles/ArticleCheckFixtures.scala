package com.histmersion.domain.research.articles

import com.histmersion.api.checks.Lint.{FormatChecker, LintRuleReport, Rule}
import com.histmersion.util.data.checks.CheckRun
import com.augustnagro.magnum.DbCon

/** The articles' lint rules as the db suites run them: a rule's report on the suite's connection and the SQL each rule
  * reads.
  */
object ArticleCheckFixtures:
  def report(r: Rule, format: FormatChecker, maxItems: ArticleCheckRepo.ReportMaxItems)(using CheckRun)(using
      DbCon
  ): LintRuleReport =
    ArticleCheckRepo.report(r, format, maxItems)

  def sqlOf(rule: String): List[String] = ArticleCheckRepo.sqlOf(rule)
