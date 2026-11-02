package com.histmersion.domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.histmersion.util.data.maintenance.{MaintenanceSite, MaintenanceSlice, MaintenanceTask, MaintenanceTaskName}
import com.augustnagro.magnum.DbCon

/** The sources' share of the library's data contribution: their counts, the rebuild of their full-text chunks, and
  * their checks with the repairs safe to make without a person — the short edge targets unified, the stale chunks
  * rebuilt, the old geocoder's points moved and the unplaced findings given a point when named.
  */
object SourcesData:

  def figures(using DbCon): List[DataCount] =
    val (sources, findings) = SourceRepo.counts()
    List(DataCount("sources", sources), DataCount("findings", findings))

  /** The rebuild of every source's full-text chunks from its OCR'd body: at most `limit` sources after `after` (and at
    * most `limits.reindex-chars` characters) per slice.
    */
  def reindex(of: DataOwner): MaintenanceTask = new MaintenanceTask:
    val owner: DataOwner = of
    val name: MaintenanceTaskName = MaintenanceTaskName("reindex")
    def run(slice: MaintenanceSlice, site: MaintenanceSite): ReindexResult =
      SourceRepairs(Transactor(site.dataSource)).reindexChunks(
        site.settings.limits.chunkChars,
        slice.after,
        slice.limit,
        site.settings.limits.reindexChars
      )

  final private class Check(val owner: DataOwner, name: String, override val fix: Option[SafeFix]) extends CheckRule:
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, name))

    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      SourceCheckRepo.report(
        rule,
        SourceCheckRepo.Context.of(context.config, context.format(SourceCheckRepo.storedCitekey)),
        maxItems
      )

  private def listed(owner: DataOwner, names: String*): List[CheckRule] = names.toList.map(Check(owner, _, None))

  private def fixed(owner: DataOwner, name: String, when: FixWhen, rank: Int)(
      run: CheckContext => LintFixResult
  ): CheckRule =
    Check(owner, name, Some(SafeFix(when, FixRank(rank), run)))

  /** The library's rules, in the order the report has always listed them. */
  def checks(owner: DataOwner): List[CheckRule] =
    listed(owner, DanglingEdges) ++
      List(
        fixed(owner, UnresolvedShortCitekeys, FixWhen.EveryFix, 10)(c =>
          SourceRepairs(Transactor(c.dataSource)).resolveEdgesFix()
        )
      ) ++
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
      List(
        fixed(owner, StaleChunks, FixWhen.EveryFix, 20)(c =>
          SourceRepairs(Transactor(c.dataSource)).reindexFix(c.config)
        )
      ) ++
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
        fixed(owner, FindingGeocode, FixWhen.WhenNamed, 30)(c =>
          SourceRepairs(Transactor(c.dataSource)).regeocodeFix(c.config)
        ),
        fixed(owner, FindingUnplaced, FixWhen.WhenNamed, 40)(c =>
          SourceRepairs(Transactor(c.dataSource)).placeFix(c.config)
        )
      ) ++
      listed(owner, FindingPlaceholder)
