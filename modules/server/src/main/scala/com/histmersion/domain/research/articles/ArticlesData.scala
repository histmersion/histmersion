package com.histmersion.domain.research.articles

import com.histmersion.util.database.Transactor

import com.histmersion.api.Lint
import com.histmersion.api.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon

/** The articles' share of research's data contribution: their count and their checks, with the one repair safe to make
  * without a person when it is named — the citekey of a disagreement partner a paragraph names and does not cite.
  */
object ArticlesData:

  def figures(using DbCon): List[DataCount] = List(DataCount("articles", ArticleRepo.count()))

  final private class Check(val owner: DataOwner, name: String, override val fix: Option[SafeFix]) extends CheckRule:
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, name))

    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      ArticleCheckRepo.report(rule, context.format(ArticleCheckRepo.storedCitekey), maxItems)

  private def listed(owner: DataOwner, names: String*): List[CheckRule] = names.toList.map(Check(owner, _, None))

  private def fixed(owner: DataOwner, name: String, when: FixWhen, rank: Int)(
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
      0,
      0,
      0,
      0
    )

  /** Research's rules, in the order the report has always listed them. */
  def checks(owner: DataOwner): List[CheckRule] =
    listed(owner, DanglingArticleLinks, ArticleFiller, ArticleUncitedParagraph, ArticleThinSummary) ++
      List(fixed(owner, ArticleCritiquePairing, FixWhen.WhenNamed, 80)(pairCritiques)) ++
      listed(owner, ArticleFormat)
