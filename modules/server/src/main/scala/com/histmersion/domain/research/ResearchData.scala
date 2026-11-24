package com.histmersion
package domain.research

import com.histmersion.domain.research.articles.ArticlesData
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.mirror.MirrorPlacement
import com.augustnagro.magnum.DbCon
import com.histmersion.util.data.mirror.Mirror
import com.histmersion.lint.{LintRule, tolerated}

/** What research says about the data it owns: its tables, what a bundle carries of it and how a merge treats it, the
  * articles' notes in the mirror, its counts and its checks.
  */
object ResearchData extends DataContribution:
  val owner: DataOwner = DataOwner("research")

  val tables: List[OwnedTable] = OwnedTable.all(
    "articles",
    "article_aliases",
    "article_critique_reasons",
    "article_links",
    "article_versions",
    "review_ledgers"
  )

  /** The articles and what they own. */
  override val carried: List[CarriedKind] = ResearchExchange.carried

  /** How a merge treats articles and their versions. */
  override val mergeRules: List[MergeRules] = ResearchExchange.mergeRules

  /** The articles' records in the mirror. */
  val ArticleNotes: Mirror.Records = Mirror.Records("articles")

  /** The articles' notes (each with its generated coverage sections, `ArticleNotes`), by their kind. */
  override val mirror: List[Mirror.Export] = List(new Mirror.Export:
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    val owner: DataOwner = ResearchData.owner
    val records: Mirror.Records = ArticleNotes
    val placement: MirrorPlacement = MirrorPlacement.ArticleFiles)

  override val checks: List[CheckRule] = ArticlesData.checks(owner)

  def figures(using DbCon): DataFigures = DataFigures(ArticlesData.figures)
