package com.histmersion.util.data.checks

import com.histmersion.api.Lint
import com.histmersion.server.Deps
import com.histmersion.util.data.TablesAdapter

/** Today's 34 rules, each registered once by the owner whose data it checks, reported in `Lint.all`'s order; the safe
  * fixes in the order `lint --fix` applies them.
  */
class CheckRulesAdapterSuite extends munit.FunSuite:
  private val rules = Deps.data.checks
  private val runner = CheckRunner(rules, CheckRulesAdapter.order, null)

  test("every rule registered once, reported in Lint.all's order"):
    assertEquals(rules.map(_.rule.name).sorted, Lint.names.sorted)
    assertEquals(runner.names, Lint.names)
    assertEquals(runner.rules.map(_.rule), Lint.all)

  test("the owners"):
    def of(owner: com.histmersion.util.data.DataOwner) = runner.rules.filter(_.owner == owner).map(_.rule.name)
    assertEquals(
      of(TablesAdapter.ResearchOwner),
      List(
        Lint.DanglingArticleLinks,
        Lint.ArticleFiller,
        Lint.ArticleUncitedParagraph,
        Lint.ArticleThinSummary,
        Lint.ArticleCritiquePairing,
        Lint.ArticleFormat
      )
    )
    assertEquals(
      of(TablesAdapter.CatalogueOwner),
      List(Lint.PlaceKindRule, Lint.PlaceGridPoint, Lint.PlaceImportedPointArea, Lint.EventCorrectionConflict)
    )
    assertEquals(of(TablesAdapter.MediaOwner), List(Lint.MediaLicenceRule))
    assertEquals(of(TablesAdapter.LibraryOwner).size, 23)

  test("the safe fixes: two on every call, the rest when named, in today's order"):
    val fixes = CheckRulesAdapter.order.fixing(runner.rules).flatMap(r => r.fix.map(f => r.rule.name -> f.when))
    assertEquals(
      fixes,
      List(
        Lint.UnresolvedShortCitekeys -> FixWhen.EveryFix,
        Lint.StaleChunks -> FixWhen.EveryFix,
        Lint.FindingGeocode -> FixWhen.WhenNamed,
        Lint.FindingUnplaced -> FixWhen.WhenNamed,
        Lint.PlaceKindRule -> FixWhen.WhenNamed,
        Lint.PlaceGridPoint -> FixWhen.WhenNamed,
        Lint.PlaceImportedPointArea -> FixWhen.WhenNamed,
        Lint.ArticleCritiquePairing -> FixWhen.WhenNamed
      )
    )
