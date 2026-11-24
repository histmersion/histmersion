package com.histmersion
package domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun, FixRank, FixWhen, SafeFix}
import com.histmersion.util.data.diagnostics.DataCount
import com.histmersion.util.data.maintenance.{MaintenanceSite, MaintenanceSlice, MaintenanceTask}
import com.histmersion.domain.library.Citekey
import com.histmersion.util.ocr.Chunker
import com.augustnagro.magnum.DbCon
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** The sources' share of the library's data contribution: their counts, the rebuild of their full-text chunks, and
  * their checks with the repairs safe to make without a person — the short edge targets unified, the stale chunks
  * rebuilt, the old geocoder's points moved and the unplaced findings given a point when named.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SourcesData:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(using DbCon): List[DataCount] =
    val (sources, findings) = SourceRepo.counts()
    List(
      DataCount(DataCount.Noun("sources"), DataCount.Count(sources.value)),
      DataCount(DataCount.Noun("findings"), DataCount.Count(findings.value))
    )

  /** The rebuild of every source's full-text chunks from its OCR'd body: at most `limit` sources after `after` (and at
    * most `limits.reindex-chars` characters) per slice.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def reindex(of: DataOwner): MaintenanceTask = new MaintenanceTask:
    val owner: DataOwner = of
    val name: MaintenanceTask.Name = MaintenanceTask.Name("reindex")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def run(slice: MaintenanceSlice, site: MaintenanceSite)(using WritePermit): ReindexResult =
      SourceRepairs(Transactor(site.dataSource)).reindexChunks(
        Chunker.ChunkMaxChars(site.settings.limits.chunkChars.value),
        slice.after.flatMap(a => Citekey.option(a.value)),
        slice.limit.map(l => SourceRepairs.MaxSources(l.value)),
        SourceRepairs.MaxChars(site.settings.limits.reindexChars.value)
      )

  final private class Check(val owner: DataOwner, name: Rule.Name, override val fix: Option[SafeFix]) extends CheckRule:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val rule: Rule = Lint.rule(name).getOrElse(Rule(name, Rule.Description(name.value)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      val stored = SourceCheckRepo.storedCitekey
      SourceCheckRepo.report(
        rule,
        SourceCheckRepo.Context.of(
          context.config,
          context.format(ck => Citekey.option(ck.value).exists(stored))
        ),
        Lint.ItemLimit(maxItems.value)
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def listed(owner: DataOwner, names: String*): List[CheckRule] =
    names.toList.map(n => Check(owner, Rule.Name(n), None))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fixed(owner: DataOwner, name: String, when: FixWhen, rank: Int)(
      run: CheckContext => WritePermit ?=> LintFixResult
  ): CheckRule =
    Check(owner, Rule.Name(name), Some(SafeFix(when, FixRank(rank), run)))

  /** The library's rules, in the order the report has always listed them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checks(owner: DataOwner)(using Creator.New): List[CheckRule] =
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
