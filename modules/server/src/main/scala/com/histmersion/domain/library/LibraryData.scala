package com.histmersion.domain.library

import com.histmersion.domain.library.sources.SourcesData
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable, TableName}
import com.histmersion.util.data.backup.{BackupShare, GuardedTable}
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.maintenance.MaintenanceTask
import com.histmersion.util.data.mirror.{MirrorExport, MirrorPlacement, MirrorRecords}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** What the library says about the data it owns: its tables, the originals, the spool and the corpus folder it keeps
  * files in, the sources a restore must not drop unasked, what a bundle carries of it and how a merge treats it, the
  * sources' notes in the mirror, the rebuild of their chunks, its counts and its checks.
  */
object LibraryData extends DataContribution:
  val owner: DataOwner = DataOwner("library")

  val tables: List[OwnedTable] = OwnedTable.all(
    "sources",
    "source_aliases",
    "source_candidates",
    "source_chunks",
    "source_identifiers",
    "findings",
    "finding_places",
    "edges",
    "unresolved_edges",
    "assessments",
    "retraction_watch",
    "candidate_watches",
    "acquire_checks",
    "upstream_quota"
  )

  /** The ingested originals, the converter's spool, the corpus folder a seed reads. */
  override val stores: List[Location] = List(Location.IngestOriginals, Location.Spool, Location.CorpusFolder)

  /** A restore that would drop sources is refused unless asked. */
  override val backup: BackupShare = BackupShare(guarded = List(GuardedTable(TableName("sources"), "source")))

  /** The sources and what they own. */
  override val carried: List[CarriedKind] = LibraryExchange.carried

  /** How a merge treats sources and findings. */
  override val mergeRules: List[MergeRules] = LibraryExchange.mergeRules

  /** The sources' records in the mirror. */
  val SourceNotes: MirrorRecords = MirrorRecords("sources")

  /** The sources' notes (each its frontmatter and its notes, `SourceNotes`), in `sources/`. */
  override val mirror: List[MirrorExport] = List(new MirrorExport:
    val owner: DataOwner = LibraryData.owner
    val records: MirrorRecords = LibraryData.SourceNotes
    val placement: MirrorPlacement = MirrorPlacement.SourceFiles)

  override val maintenance: List[MaintenanceTask] = List(SourcesData.reindex(owner))

  override val checks: List[CheckRule] = SourcesData.checks(owner)

  def figures(using DbCon): DataFigures = DataFigures(SourcesData.figures)
