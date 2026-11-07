package com.histmersion.domain.research.articles

import com.histmersion.util.database.Transactor

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, GuardedWrite, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon

/** The articles' share of research's data contribution: their count and their checks, with the one repair safe to make
  * without a person when it is named — the citekey of a disagreement partner a paragraph names and does not cite.
  */
object ArticlesData:

  def figures(using DbCon): List[DataCount] = List(DataCount(com.histmersion.util.data.diagnostics.DataCount.Noun("articles"), DataCount.Count(ArticleRepo.count().value)))

  final private class Check(val owner: DataOwner, name: Rule.Name, override val fix: Option[SafeFix]) extends CheckRule:
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, Rule.Description(name.value)))

    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      val stored = ArticleCheckRepo.storedCitekey
      ArticleCheckRepo.report(
        rule,
        context.format(ck => com.histmersion.domain.library.Citekey.option(ck.value).exists(stored)),
        ArticleCheckRepo.ReportMaxItems(maxItems.value)
      )

  private def listed(owner: DataOwner, names: String*): List[CheckRule] =
    names.toList.map(n => Check(owner, Rule.Name(n), None))

  private def fixed(owner: DataOwner, name: String, when: FixWhen, rank: FixRank)(
      run: CheckContext => LintFixResult
  ): CheckRule =
    Check(owner, Rule.Name(name), Some(SafeFix(when, rank, run)))

  /** The paragraphs that name a disagreement partner they do not cite get its citekey, one guarded write per article (a
    * concurrent writer wins; the next lint lists it again).
    */
  private def pairCritiques(context: CheckContext): LintFixResult =
    val written =
      Transactor(context.dataSource)
        .read(ArticleCheckRepo.pairingRepairs)
        .filter(r =>
          context.write(
            GuardedWrite.RecordRef(r.slug.value),
            GuardedWrite.FileText(r.body.value),
            r.updatedAt.map(u => GuardedWrite.ConcurrencyToken(u.value))
          )
        )
    val citations = written.map(_.applied.size).sum
    LintFixResult(
      List(
        LintFixResult.Applied(
          s"$ArticleCritiquePairing: $citations citation(s) added to the paragraphs that name them, in ${written.size} article(s)"
        )
      ),
      com.histmersion.api.checks.Lint.LintFixResult.EdgesResolved(0),
      com.histmersion.api.checks.Lint.LintFixResult.SourcesReindexed(0),
      com.histmersion.api.checks.Lint.LintFixResult.ChunksWritten(0),
      com.histmersion.api.checks.Lint.LintFixResult.Remaining(0)
    )

  /** Research's rules, in the order the report has always listed them. */
  def checks(owner: DataOwner): List[CheckRule] =
    listed(owner, DanglingArticleLinks, ArticleFiller, ArticleUncitedParagraph, ArticleThinSummary) ++
      List(fixed(owner, ArticleCritiquePairing, FixWhen.WhenNamed, FixRank(80))(pairCritiques)) ++
      listed(owner, ArticleFormat)
