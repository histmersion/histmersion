package com.histmersion
package domain.research.articles

import com.histmersion.api.checks.lint.Rule
import com.histmersion.api.checks.lint.LintRuleReport
import com.histmersion.api.checks.lint.LintFixResult

import com.histmersion.util.database.{Transactor, WritePermit}

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, GuardedWrite, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** The articles' share of research's data contribution: their count and their checks, with the one repair safe to make
  * without a person when it is named — the citekey of a disagreement partner a paragraph names and does not cite.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object ArticlesData:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(using DbCon): List[DataCount] = List(
    DataCount(DataCount.Noun("articles"), DataCount.Count(ArticleRepo.count().value))
  )

  final private class Check(val owner: DataOwner, name: Rule.Name, override val fix: Option[SafeFix]) extends CheckRule:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val rule: Rule = Rule.rule(name).getOrElse(Rule(name, Rule.Description(name.value)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      val stored = ArticleCheckRepo.storedCitekey
      ArticleCheckRepo.report(
        rule,
        context.format(ck => Citekey.option(ck.value).exists(stored)),
        ArticleCheckRepo.ReportMaxItems(maxItems.value)
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def listed(owner: DataOwner, names: String*): List[CheckRule] =
    names.toList.map(n => Check(owner, Rule.Name(n), None))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fixed(owner: DataOwner, name: String, when: FixWhen, rank: FixRank)(
      run: CheckContext => WritePermit ?=> LintFixResult
  ): CheckRule =
    Check(owner, Rule.Name(name), Some(SafeFix(when, rank, run)))

  /** The paragraphs that name a disagreement partner they do not cite get its citekey, one guarded write per article (a
    * concurrent writer wins; the next lint lists it again).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pairCritiques(context: CheckContext)(using WritePermit): LintFixResult =
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
      LintFixResult.EdgesResolved(0),
      LintFixResult.SourcesReindexed(0),
      LintFixResult.ChunksWritten(0),
      LintFixResult.Remaining(0)
    )

  /** Research's rules, in the order the report has always listed them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checks(owner: DataOwner): List[CheckRule] =
    listed(owner, DanglingArticleLinks, ArticleFiller, ArticleUncitedParagraph, ArticleThinSummary) ++
      List(fixed(owner, ArticleCritiquePairing, FixWhen.WhenNamed, FixRank(80))(pairCritiques)) ++
      listed(owner, ArticleFormat)
