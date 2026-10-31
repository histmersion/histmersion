package com.histmersion.util.data.checks

import com.histmersion.api.Lint
import com.histmersion.api.Lint.*
import com.histmersion.db.LintRepo
import com.histmersion.util.data.{DataOwner, TablesAdapter}
import com.augustnagro.magnum.DbCon

/** Today's lint rules, registered by owner until each owner registers its own: each rule's report and safe fix are
  * `LintRepo`'s per-kind SQL and the api's pure rules.
  */
object CheckRulesAdapter:

  /** Today's order: the report lists the rules as `Lint.all` does (the CLI help and the MCP tool name them so), and the
    * fixes run as `lint --fix` has always applied them.
    */
  val order: CheckOrder = CheckOrder(
    Lint.names,
    List(
      UnresolvedShortCitekeys,
      StaleChunks,
      FindingGeocode,
      FindingUnplaced,
      PlaceKindRule,
      PlaceGridPoint,
      PlaceImportedPointArea,
      ArticleCritiquePairing
    )
  )

  final private class Today(val owner: DataOwner, name: String, override val fix: Option[SafeFix]) extends CheckRule:
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, name))

    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      LintRepo.report(rule, LintRepo.Context.of(context.config, context.format(LintRepo.storedCitekey)), maxItems)

  private def listed(owner: DataOwner, names: String*): List[CheckRule] = names.toList.map(Today(owner, _, None))

  private def fixed(owner: DataOwner, name: String, when: FixWhen)(run: CheckContext => LintFixResult): CheckRule =
    Today(owner, name, Some(SafeFix(when, run)))

  /** The paragraphs that name a disagreement partner they do not cite get its citekey, one guarded write per article (a
    * concurrent writer wins; the next lint lists it again).
    */
  private def pairCritiques(context: CheckContext): LintFixResult =
    val written = LintRepo.pairingRepairs(context.dataSource).filter(r => context.write(r.slug, r.body, r.updatedAt))
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

  def of(owner: DataOwner): List[CheckRule] = owner match
    case TablesAdapter.LibraryOwner =>
      listed(owner, DanglingEdges) ++
        List(fixed(owner, UnresolvedShortCitekeys, FixWhen.EveryFix)(c => LintRepo.resolveEdgesFix(c.dataSource))) ++
        listed(
          owner,
          NoFindings,
          NoPeriod,
          NoRegions,
          EmptySummary,
          UnassessedStatus,
          DuplicateDoi,
          DuplicateSha256,
          OrphanSources
        ) ++
        List(fixed(owner, StaleChunks, FixWhen.EveryFix)(c => LintRepo.reindexFix(c.dataSource, c.config))) ++
        listed(
          owner,
          UndatedFindings,
          UnlocatedFindings,
          LegacyFigureLinks,
          NonCanonicalTag,
          MissingYear,
          YearCitekeyMismatch,
          AuthorPipe,
          SupersededWithoutEdge,
          NoteFormat
        ) ++
        List(
          fixed(owner, FindingGeocode, FixWhen.WhenNamed)(c => LintRepo.regeocodeFix(c.dataSource, c.config)),
          fixed(owner, FindingUnplaced, FixWhen.WhenNamed)(c => LintRepo.placeFix(c.dataSource, c.config))
        ) ++
        listed(owner, FindingPlaceholder)
    case TablesAdapter.ResearchOwner =>
      listed(owner, DanglingArticleLinks, ArticleFiller, ArticleUncitedParagraph, ArticleThinSummary) ++
        List(fixed(owner, ArticleCritiquePairing, FixWhen.WhenNamed)(pairCritiques)) ++
        listed(owner, ArticleFormat)
    case TablesAdapter.CatalogueOwner =>
      List(
        fixed(owner, PlaceKindRule, FixWhen.WhenNamed)(c => LintRepo.reclassifyFix(c.dataSource, c.config)),
        fixed(owner, PlaceGridPoint, FixWhen.WhenNamed)(c => LintRepo.relocateFix(c.dataSource, c.config)),
        fixed(owner, PlaceImportedPointArea, FixWhen.WhenNamed)(c =>
          LintRepo.removePointAreasFix(c.dataSource, c.config)
        )
      ) ++ listed(owner, EventCorrectionConflict)
    case TablesAdapter.MediaOwner => listed(owner, MediaLicenceRule)
    case _                        => Nil
