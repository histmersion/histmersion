package com.histmersion.domain.research.articles

import com.histmersion.util.database.Transactor

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon

/** The articles' share of research's data contribution: their count and their checks, with the one repair safe to make
  * without a person when it is named — the citekey of a disagreement partner a paragraph names and does not cite.
  */
object ArticlesData:

  type FixedRank = FixedRank.T
  object FixedRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def figures(using DbCon): List[DataCount] = List(DataCount(com.histmersion.util.data.diagnostics.DataCount.Noun("articles"), ArticleRepo.count()))

  final private class Check(val owner: DataOwner, name: Check.Name, override val fix: Option[SafeFix]) extends CheckRule:
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, name))

    def report(context: CheckContext, maxItems: com.histmersion.domain.research.articles.ArticlesData.Check.ReportMaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      ArticleCheckRepo.report(rule, context.format(ArticleCheckRepo.storedCitekey), maxItems)

  object Check:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ReportMaxItems = ReportMaxItems.T
    object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def listed(owner: DataOwner, names: String*): List[CheckRule] = names.toList.map(Check(owner, _, None))

  private def fixed(owner: DataOwner, name: com.histmersion.domain.research.articles.ArticlesData.Check.Name, when: FixWhen, rank: com.histmersion.domain.research.articles.ArticlesData.FixedRank)(
      run: CheckContext => LintFixResult
  ): CheckRule =
    Check(owner, name, Some(SafeFix(when, FixRank(rank), run)))

  /** The paragraphs that name a disagreement partner they do not cite get its citekey, one guarded write per article (a
    * concurrent writer wins; the next lint lists it again).
    */
  private def pairCritiques(context: CheckContext): LintFixResult =
    val written =
      Transactor(context.dataSource)
        .read(ArticleCheckRepo.pairingRepairs)
        .filter(r => context.write(r.slug, r.body, r.updatedAt))
    val citations = written.map(_.applied.size).sum
    LintFixResult(
      List(
        s"$ArticleCritiquePairing: $citations citation(s) added to the paragraphs that name them, in ${written.size} article(s)"
      ),
      com.histmersion.api.checks.Lint.LintFixResult.EdgesResolved(0),
      com.histmersion.api.checks.Lint.LintFixResult.SourcesReindexed(0),
      com.histmersion.api.checks.Lint.LintFixResult.ChunksWritten(0),
      com.histmersion.api.checks.Lint.LintFixResult.Remaining(0)
    )

  /** Research's rules, in the order the report has always listed them. */
  def checks(owner: DataOwner): List[CheckRule] =
    listed(owner, DanglingArticleLinks, ArticleFiller, ArticleUncitedParagraph, ArticleThinSummary) ++
      List(fixed(owner, ArticleCritiquePairing, FixWhen.WhenNamed, com.histmersion.domain.research.articles.ArticlesData.FixedRank(80))(pairCritiques)) ++
      listed(owner, ArticleFormat)
