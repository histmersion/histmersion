package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.aliases.{InstanceInfo, LabelRename, LabelRenamed}
import com.histmersion.api.automation.{
  Activity,
  AgentsApi,
  AgentStateDeleted,
  AgentStateEntry,
  AgentStatePut,
  Goal,
  ListFilters,
  ManagersApi
}
import com.histmersion.api.backup.{Backup, RestoreRequest, RestoreResult}
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{
  Attestation,
  EventDeleted,
  EventList,
  EventPatch,
  EventPut,
  EventsApi,
  HistoricalPeriod,
  ManualPlaceName,
  TilesApi
}
import com.histmersion.api.checks.FormatReport
import com.histmersion.api.exchange.{
  MergeApplyJob,
  MergeApplyRequest,
  MergeDetail,
  MergeImportRequest,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.instance.{About, Health, UiApi, UiConfig}
import com.histmersion.api.jobs.{JobActions, JobInfo}
import com.histmersion.api.library.{
  AcquireApi,
  ContradictionsResult,
  EdgesResolved,
  Facet,
  Filter,
  GeoHit,
  GeoHitPage,
  Ingest,
  InstanceLabel,
  NeighborRow,
  NoteImport,
  PassageResult,
  SearchHit,
  SearchResult,
  SeedRequest,
  SeedResult,
  TimeHistogram
}
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetInput,
  AssetLink,
  AssetPatch,
  AssetsStored,
  FigureLicenceFill,
  MediaCoversReport,
  MediaCoversRequest,
  MediaFacets,
  MediaGeoHit,
  MediaImportReport,
  MediaImportRequest,
  MediaItem,
  MediaList,
  MediaProvider,
  SourceCoverParams
}
import com.histmersion.api.research.{Backlink, Coverage}
import com.histmersion.api.settings.{ConfigEditApi, ReloadReport}
import com.histmersion.api.views.{AutopilotApi, EventDetail, PlaceDetail, ResolvedId}
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.instance.Version
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.research.Article
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.AnyEndpoint
import sttp.tapir.Schema

/** Every endpoint here and in the other `*Api` objects is a `lazy val`: initialising an object runs all of its strict
  * vals, so Scala.js would link every endpoint and every codec it names into the web bundle as soon as the page used
  * one of them.
  */
object Api:

  /** `region` is still sent by older links and clients; it is one more place name. */
  type AssetPart3Part1 = AssetPart3Part1.T
  object AssetPart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSourceCoverPart1Part2 = PutSourceCoverPart1Part2.T
  object PutSourceCoverPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaThumbPart3Part1 = MediaThumbPart3Part1.T
  object MediaThumbPart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaFilePart3Part1 = MediaFilePart3Part1.T
  object MediaFilePart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart3Part1 = IiifImagePart3Part1.T
  object IiifImagePart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type QAnonymousPart1 = QAnonymousPart1.T
  object QAnonymousPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part1 = SearchPart1Part1.T
  object SearchPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part2 = SearchPart1Part2.T
  object SearchPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdPart1 = ResolveIdPart1.T
  object ResolveIdPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSourcePart1Part1 = GetSourcePart1Part1.T
  object GetSourcePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSourcePart1Part2 = GetSourcePart1Part2.T
  object GetSourcePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceArticlesPart1 = SourceArticlesPart1.T
  object SourceArticlesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceViewPart3 = SourceViewPart3.T
  object SourceViewPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsPart1Part1 = NeighborsPart1Part1.T
  object NeighborsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsPart1Part2 = NeighborsPart1Part2.T
  object NeighborsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphPart1Part1 = GraphPart1Part1.T
  object GraphPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphPart1Part2 = GraphPart1Part2.T
  object GraphPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContradictionsPart1Part1 = ContradictionsPart1Part1.T
  object ContradictionsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContradictionsPart1Part2 = ContradictionsPart1Part2.T
  object ContradictionsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexPart1Part1 = ReindexPart1Part1.T
  object ReindexPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexPart1Part2 = ReindexPart1Part2.T
  object ReindexPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsPart1Part1 = FacetsPart1Part1.T
  object FacetsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsPart1Part2 = FacetsPart1Part2.T
  object FacetsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSourcePart1Part1 = PutSourcePart1Part1.T
  object PutSourcePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSourcePart1Part3 = PutSourcePart1Part3.T
  object PutSourcePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSourcePart1Part1 = PatchSourcePart1Part1.T
  object PatchSourcePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSourcePart1Part3 = PatchSourcePart1Part3.T
  object PatchSourcePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintSourcePart1 = LintSourcePart1.T
  object LintSourcePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixSourceFormatPart1 = FixSourceFormatPart1.T
  object FixSourceFormatPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameSourcePart1Part1 = RenameSourcePart1Part1.T
  object RenameSourcePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSourcePart1Part1 = DeleteSourcePart1Part1.T
  object DeleteSourcePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportSourcePart1 = ExportSourcePart1.T
  object ExportSourcePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportSourcePart3 = ExportSourcePart3.T
  object ExportSourcePart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeysPart3 = CitekeysPart3.T
  object CitekeysPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadBackupPart1 = DownloadBackupPart1.T
  object DownloadBackupPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundlePart1 = ExportBundlePart1.T
  object ExportBundlePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundlePart3Part2 = ExportBundlePart3Part2.T
  object ExportBundlePart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportBundlePart1Part1 = ImportBundlePart1Part1.T
  object ImportBundlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeApplyStatusPart1 = MergeApplyStatusPart1.T
  object MergeApplyStatusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeSessionPart1 = MergeSessionPart1.T
  object MergeSessionPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutMergeSessionPart1Part1 = PutMergeSessionPart1Part1.T
  object PutMergeSessionPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResolutionsPart1 = ParseResolutionsPart1.T
  object ParseResolutionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergesPart1 = MergesPart1.T
  object MergesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerPart1Part1 = MergeLedgerPart1Part1.T
  object MergeLedgerPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerPart1Part2 = MergeLedgerPart1Part2.T
  object MergeLedgerPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerPart1Part3 = MergeLedgerPart1Part3.T
  object MergeLedgerPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeDetailPart1 = MergeDetailPart1.T
  object MergeDetailPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameArticlePart1Part1 = RenameArticlePart1Part1.T
  object RenameArticlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetArticlePart1Part1 = GetArticlePart1Part1.T
  object GetArticlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetArticlePart1Part2 = GetArticlePart1Part2.T
  object GetArticlePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutArticlePart1Part1 = PutArticlePart1Part1.T
  object PutArticlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutArticlePart1Part3 = PutArticlePart1Part3.T
  object PutArticlePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchArticlePart1Part1 = PatchArticlePart1Part1.T
  object PatchArticlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchArticlePart1Part3 = PatchArticlePart1Part3.T
  object PatchArticlePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintArticlePart1 = LintArticlePart1.T
  object LintArticlePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixArticleFormatPart1 = FixArticleFormatPart1.T
  object FixArticleFormatPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckArticlePart1Part1 = CheckArticlePart1Part1.T
  object CheckArticlePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCoveragePart1 = ArticleCoveragePart1.T
  object ArticleCoveragePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleReviewPart1 = ArticleReviewPart1.T
  object ArticleReviewPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteArticlePart1 = DeleteArticlePart1.T
  object DeleteArticlePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportArticlePart1 = ExportArticlePart1.T
  object ExportArticlePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportArticlePart3 = ExportArticlePart3.T
  object ExportArticlePart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionsPart1 = ArticleVersionsPart1.T
  object ArticleVersionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionPart1Part1 = ArticleVersionPart1Part1.T
  object ArticleVersionPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionPart1Part2 = ArticleVersionPart1Part2.T
  object ArticleVersionPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSlugsPart3 = ArticleSlugsPart3.T
  object ArticleSlugsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesPart1Part1 = PlacesPart1Part1.T
  object PlacesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesPart1Part2 = PlacesPart1Part2.T
  object PlacesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesPart1Part3 = PlacesPart1Part3.T
  object PlacesPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesGeoPart3 = PlacesGeoPart3.T
  object PlacesGeoPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceChildrenPart1Part1 = PlaceChildrenPart1Part1.T
  object PlaceChildrenPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceChildrenPart1Part2 = PlaceChildrenPart1Part2.T
  object PlaceChildrenPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreePart1Part1 = PlaceTreePart1Part1.T
  object PlaceTreePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreePart1Part2 = PlaceTreePart1Part2.T
  object PlaceTreePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreePart1Part3 = PlaceTreePart1Part3.T
  object PlaceTreePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacePart1Part1 = PlacePart1Part1.T
  object PlacePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacePart1Part2 = PlacePart1Part2.T
  object PlacePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacePart1Part3 = PlacePart1Part3.T
  object PlacePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFootprintsPart1Part1 = PlaceFootprintsPart1Part1.T
  object PlaceFootprintsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFootprintsPart1Part2 = PlaceFootprintsPart1Part2.T
  object PlaceFootprintsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLinksPart1 = PlaceLinksPart1.T
  object PlaceLinksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsPart1Part1 = PeriodsPart1Part1.T
  object PeriodsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsPart1Part2 = PeriodsPart1Part2.T
  object PeriodsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsPart1Part3 = PeriodsPart1Part3.T
  object PeriodsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsPart1Part4 = PeriodsPart1Part4.T
  object PeriodsPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPeriodsPart1 = SeedPeriodsPart1.T
  object SeedPeriodsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPeriodsPart1Part1 = ImportPeriodsPart1Part1.T
  object ImportPeriodsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPeriodsPart1Part2 = ImportPeriodsPart1Part2.T
  object ImportPeriodsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPeriodsPart1Part3 = ImportPeriodsPart1Part3.T
  object ImportPeriodsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchPeriodsPart1Part1 = FetchPeriodsPart1Part1.T
  object FetchPeriodsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchPeriodsPart1Part2 = FetchPeriodsPart1Part2.T
  object FetchPeriodsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitPart1 = InitPart1.T
  object InitPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesPart1Part1 = ImportPlacesPart1Part1.T
  object ImportPlacesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesPart1Part2 = ImportPlacesPart1Part2.T
  object ImportPlacesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesPart1Part3 = ImportPlacesPart1Part3.T
  object ImportPlacesPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchExternalPlacesPart1Part1 = FetchExternalPlacesPart1Part1.T
  object FetchExternalPlacesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchExternalPlacesPart1Part2 = FetchExternalPlacesPart1Part2.T
  object FetchExternalPlacesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadPlacesPart1Part1 = LoadPlacesPart1Part1.T
  object LoadPlacesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadPlacesPart1Part2 = LoadPlacesPart1Part2.T
  object LoadPlacesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceNamesPart1 = PlaceNamesPart1.T
  object PlaceNamesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddPlaceNamePart1Part1 = AddPlaceNamePart1Part1.T
  object AddPlaceNamePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemovePlaceNamePart1Part1 = RemovePlaceNamePart1Part1.T
  object RemovePlaceNamePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceOverridesPart1 = PlaceOverridesPart1.T
  object PlaceOverridesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlaceOverridePart1Part1 = SetPlaceOverridePart1Part1.T
  object SetPlaceOverridePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergePlacePart1Part1 = MergePlacePart1Part1.T
  object MergePlacePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemovePlacePart1Part1 = RemovePlacePart1Part1.T
  object RemovePlacePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameEventPart1Part1 = RenameEventPart1Part1.T
  object RenameEventPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetEventPart1 = GetEventPart1.T
  object GetEventPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutEventPart1Part1 = PutEventPart1Part1.T
  object PutEventPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchEventPart1Part1 = PatchEventPart1Part1.T
  object PatchEventPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteEventPart1 = DeleteEventPart1.T
  object DeleteEventPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetAttestationPart1 = GetAttestationPart1.T
  object GetAttestationPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAttestationPart1Part1 = PatchAttestationPart1Part1.T
  object PatchAttestationPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteAttestationPart1 = DeleteAttestationPart1.T
  object DeleteAttestationPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameGoalPart1Part1 = RenameGoalPart1Part1.T
  object RenameGoalPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamePlacePart1Part1 = RenamePlacePart1Part1.T
  object RenamePlacePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetGoalPart1 = GetGoalPart1.T
  object GetGoalPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutGoalPart1Part1 = PutGoalPart1Part1.T
  object PutGoalPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchGoalPart1Part1 = PatchGoalPart1Part1.T
  object PatchGoalPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteGoalPart1 = DeleteGoalPart1.T
  object DeleteGoalPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateGetPart1 = AgentStateGetPart1.T
  object AgentStateGetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateSetPart1Part1 = AgentStateSetPart1Part1.T
  object AgentStateSetPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateDeletePart1 = AgentStateDeletePart1.T
  object AgentStateDeletePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrPart1Part1 = ReocrPart1Part1.T
  object ReocrPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrPart1Part2 = ReocrPart1Part2.T
  object ReocrPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalPart1 = OriginalPart1.T
  object OriginalPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalPart3Part2 = OriginalPart3Part2.T
  object OriginalPart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobPart1 = JobPart1.T
  object JobPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobControlPart1Part1 = JobControlPart1Part1.T
  object JobControlPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobControlPart1Part2 = JobControlPart1Part2.T
  object JobControlPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityPart1Part1 = ActivityPart1Part1.T
  object ActivityPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityPart1Part2 = ActivityPart1Part2.T
  object ActivityPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityPart1Part3 = ActivityPart1Part3.T
  object ActivityPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityPart1Part4 = ActivityPart1Part4.T
  object ActivityPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityPart1Part5 = ActivityPart1Part5.T
  object ActivityPart1Part5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsPart1Part2 = JobsPart1Part2.T
  object JobsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsPart1Part3 = JobsPart1Part3.T
  object JobsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsPart1Part4 = JobsPart1Part4.T
  object JobsPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsPart1 = AssetsPart1.T
  object AssetsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutAssetsPart1Part1 = PutAssetsPart1Part1.T
  object PutAssetsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetPart1Part1 = AssetPart1Part1.T
  object AssetPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetPart1Part2 = AssetPart1Part2.T
  object AssetPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetPart3Part2 = AssetPart3Part2.T
  object AssetPart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetDetailPart1Part1 = AssetDetailPart1Part1.T
  object AssetDetailPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetDetailPart1Part2 = AssetDetailPart1Part2.T
  object AssetDetailPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAssetPart1Part1 = PatchAssetPart1Part1.T
  object PatchAssetPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAssetPart1Part2 = PatchAssetPart1Part2.T
  object PatchAssetPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaShowPart1 = MediaShowPart1.T
  object MediaShowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillFigureLicencesPart1 = FillFigureLicencesPart1.T
  object FillFigureLicencesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaThumbPart1 = MediaThumbPart1.T
  object MediaThumbPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaThumbPart3Part2 = MediaThumbPart3Part2.T
  object MediaThumbPart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaFilePart1 = MediaFilePart1.T
  object MediaFilePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaFilePart3Part2 = MediaFilePart3Part2.T
  object MediaFilePart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksPart1Part1 = AssetLinksPart1Part1.T
  object AssetLinksPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksPart1Part2 = AssetLinksPart1Part2.T
  object AssetLinksPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksPart1Part3 = AssetLinksPart1Part3.T
  object AssetLinksPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksPart1Part4 = AssetLinksPart1Part4.T
  object AssetLinksPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteAssetLinkPart1 = DeleteAssetLinkPart1.T
  object DeleteAssetLinkPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifInfoPart1Part1 = IiifInfoPart1Part1.T
  object IiifInfoPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifInfoPart1Part2 = IiifInfoPart1Part2.T
  object IiifInfoPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part1 = IiifImagePart1Part1.T
  object IiifImagePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part2 = IiifImagePart1Part2.T
  object IiifImagePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part3 = IiifImagePart1Part3.T
  object IiifImagePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part4 = IiifImagePart1Part4.T
  object IiifImagePart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part5 = IiifImagePart1Part5.T
  object IiifImagePart1Part5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart1Part6 = IiifImagePart1Part6.T
  object IiifImagePart1Part6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IiifImagePart3Part2 = IiifImagePart3Part2.T
  object IiifImagePart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GenerateApiIndexRepo = GenerateApiIndexRepo.T
  object GenerateApiIndexRepo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class FindingsQuery(filter: Filter, region: Option[FindingsQuery.Region]):
    def toFilter: Filter =
      region.map((_).value.trim).filter(_.nonEmpty).fold(filter)(r => filter.copy(places = filter.places :+ _root_.com.histmersion.api.library.Filter.Places(r)))

  object FindingsQuery:
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

  private lazy val base = endpoint.in("api").errorOut(jsonBody[com.histmersion.api.Api.Error])

  /** A `def`, not a `val`: endpoints defined above `writeErrors` use it too. */
  private def writeBase = endpoint.in("api").errorOut(writeErrors)

  /** The parameter names are fixed: saved links, the web UI and the CLI rely on them. */
  lazy val filterQuery: EndpointInput[Filter] =
    import com.histmersion.api.library.Filter.Docs as D
    def q[A](p: D.Param)(using Codec[List[com.histmersion.api.Api.QAnonymousPart1], A, CodecFormat.TextPlain]) =
      query[A](p.rest).description(p.doc)
    q[Option[String]](D.query)
      .and(q[List[String]](D.tags))
      .and(q[List[String]](D.authors))
      .and(q[List[String]](D.places))
      .and(q[List[String]](D.topics))
      .and(q[List[String]](D.status))
      .and(q[Option[Long]](D.yearFrom))
      .and(q[Option[Long]](D.yearTo))
      .and(q[Option[Long]](D.from))
      .and(q[Option[Long]](D.to))
      .and(q[Option[Double]](D.minLon))
      .and(q[Option[Double]](D.minLat))
      .and(q[Option[Double]](D.maxLon))
      .and(q[Option[Double]](D.maxLat))
      .and(q[Option[String]](D.sort))
      .and(q[Option[Int]](D.limit))
      .and(q[Option[Int]](D.offset))
      .and(q[Option[String]](D.title))
      .and(q[Option[String]](D.goal))
      .mapTo[Filter.Query]
      .map(_.toFilter)(Filter.Query.from)

  lazy val version: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Version.Info, Any] =
    base.get.in("version").out(jsonBody[Version.Info])

  lazy val health: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  lazy val about: PublicEndpoint[Unit, com.histmersion.api.Api.Error, About, Any] =
    base.get.in("about").out(jsonBody[About])

  lazy val config: PublicEndpoint[Unit, com.histmersion.api.Api.Error, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  lazy val search: PublicEndpoint[(Option[com.histmersion.api.Api.SearchPart1Part1], Option[com.histmersion.api.Api.SearchPart1Part2]), com.histmersion.api.Api.Error, List[SearchHit], Any] =
    base.get
      .in("search")
      .in(query[Option[String]]("q"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])

  lazy val resolveId: PublicEndpoint[com.histmersion.api.Api.ResolveIdPart1, com.histmersion.api.Api.Error, ResolvedId, Any] =
    base.get.in("resolve" / path[String]("id")).out(jsonBody[ResolvedId])

  lazy val getSource: PublicEndpoint[(com.histmersion.api.Api.GetSourcePart1Part1, com.histmersion.api.Api.GetSourcePart1Part2), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    base.get
      .in("sources" / path[String]("citekey"))
      .in(query[Boolean]("body").default(true))
      .out(jsonBody[Source.WithFindings])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val sourceArticles: PublicEndpoint[com.histmersion.api.Api.SourceArticlesPart1, com.histmersion.api.Api.Error, List[Backlink], Any] =
    base.get
      .in("sources" / path[String]("citekey") / "articles")
      .out(jsonBody[List[Backlink]])
      .errorOut(statusCode(StatusCode.NotFound))

  final case class SourceViewQuery(
      citekey: com.histmersion.api.library.Citekey,
      fullText: SourceViewQuery.FullText = _root_.com.histmersion.api.Api.SourceViewQuery.FullText(false),
      section: Option[SourceViewQuery.Section] = None,
      heading: Option[SourceViewQuery.Heading] = None,
      offset: Option[SourceViewQuery.Offset] = None,
      chars: Option[SourceViewQuery.Chars] = None
  )

  object SourceViewQuery:
    type FullText = FullText.T
    object FullText extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Heading = Heading.T
    object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Chars = Chars.T
    object Chars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val sourceView: PublicEndpoint[SourceViewQuery, com.histmersion.api.Api.Error, com.histmersion.api.Api.SourceViewPart3, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "view")
      .in(query[Boolean]("fullText").default(false))
      .in(query[Option[String]]("section"))
      .in(query[Option[String]]("heading"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("chars"))
      .mapInTo[SourceViewQuery]
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(stringBody)

  final case class PassagesQuery(
      citekey: com.histmersion.api.library.Citekey,
      q: PassagesQuery.Q,
      regex: PassagesQuery.Regex = _root_.com.histmersion.api.Api.PassagesQuery.Regex(false),
      limit: Option[PassagesQuery.Limit] = None,
      context: Option[PassagesQuery.Context] = None
  )

  object PassagesQuery:
    type Q = Q.T
    object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regex = Regex.T
    object Regex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val passages: PublicEndpoint[PassagesQuery, com.histmersion.api.Api.Error, PassageResult, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "passages")
      .in(
        query[String]("q").description(
          "the text to find (literal, case- and accent-insensitive), or a regex with regex=true"
        )
      )
      .in(query[Boolean]("regex").default(false))
      .in(query[Option[Int]]("limit").description("passages shown (default and cap from config)"))
      .in(query[Option[Int]]("context").description("characters of context around a match"))
      .mapInTo[PassagesQuery]
      .errorOut(writeErrors)
      .out(jsonBody[PassageResult])

  lazy val findings: PublicEndpoint[FindingsQuery, com.histmersion.api.Api.Error, List[GeoHit], Any] =
    base.get
      .in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  lazy val findingsPage: PublicEndpoint[FindingsQuery, com.histmersion.api.Api.Error, GeoHitPage, Any] =
    base.get
      .in("findings" / "page")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[GeoHitPage])

  final case class AggregateQuery(
      by: Option[AggregateQuery.By],
      unit: Option[AggregateQuery.Unit],
      filter: Filter,
      depth: Option[AggregateQuery.Depth] = None,
      asOf: Option[AggregateQuery.AsOf] = None,
      samples: Option[AggregateQuery.Samples] = None,
      size: Option[AggregateQuery.Size] = None
  )

  object AggregateQuery:
    type TimeUnit = TimeUnit.T
    object TimeUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeBy = TreeBy.T
    object TreeBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeUnit = TreeUnit.T
    object TreeUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeDepth = TreeDepth.T
    object TreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeAsOf = TreeAsOf.T
    object TreeAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeSamples = TreeSamples.T
    object TreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unit = Unit.T
    object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOf = AsOf.T
    object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Samples = Samples.T
    object Samples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Size = Size.T
    object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def time(filter: Filter, unit: Option[com.histmersion.api.Api.AggregateQuery.TimeUnit] = None): AggregateQuery =
      AggregateQuery(Some(_root_.com.histmersion.api.Api.AggregateQuery.By("time")), unit, filter)

    def tree(
        by: com.histmersion.api.Api.AggregateQuery.TreeBy,
        filter: Filter,
        unit: Option[com.histmersion.api.Api.AggregateQuery.TreeUnit] = None,
        depth: Option[com.histmersion.api.Api.AggregateQuery.TreeDepth] = None,
        asOf: Option[com.histmersion.api.Api.AggregateQuery.TreeAsOf] = None,
        samples: Option[com.histmersion.api.Api.AggregateQuery.TreeSamples] = None
    ): AggregateQuery =
      AggregateQuery(Some(by), unit, filter, depth.orElse(Some(0)), asOf, samples)

  lazy val aggregate: PublicEndpoint[AggregateQuery, com.histmersion.api.Api.Error, TimeHistogram, Any] =
    base.get
      .in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .in(query[Option[Int]]("depth"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("samples"))
      .in(query[Option[Long]]("size"))
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  lazy val neighbors: PublicEndpoint[(com.histmersion.api.Api.NeighborsPart1Part1, List[com.histmersion.api.Api.NeighborsPart1Part2]), com.histmersion.api.Api.Error, List[NeighborRow], Any] =
    base.get
      .in("neighbors" / path[String]("citekey"))
      .in(query[List[String]]("kind"))
      .out(jsonBody[List[NeighborRow]])

  lazy val graph: PublicEndpoint[(com.histmersion.api.Api.GraphPart1Part1, Option[com.histmersion.api.Api.GraphPart1Part2]), com.histmersion.api.Api.Error, Source.Graph, Any] =
    base.get
      .in("graph" / path[String]("citekey"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[Source.Graph])

  lazy val contradictions
      : PublicEndpoint[(Option[com.histmersion.api.Api.ContradictionsPart1Part1], Option[com.histmersion.api.Api.ContradictionsPart1Part2]), com.histmersion.api.Api.Error, ContradictionsResult, Any] =
    base.get
      .in("contradictions")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[Long]]("since"))
      .out(jsonBody[ContradictionsResult])

  lazy val seed: PublicEndpoint[SeedRequest, com.histmersion.api.Api.Error, SeedResult, Any] =
    writeBase.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  lazy val reindex: PublicEndpoint[(Option[com.histmersion.api.Api.ReindexPart1Part1], Option[com.histmersion.api.Api.ReindexPart1Part2]), com.histmersion.api.Api.Error, ReindexResult, Any] =
    writeBase.post
      .in("reindex")
      .in(query[Option[String]]("after"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult])

  lazy val sources: PublicEndpoint[Filter, com.histmersion.api.Api.Error, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  lazy val facets: PublicEndpoint[(com.histmersion.api.Api.FacetsPart1Part1, Option[com.histmersion.api.Api.FacetsPart1Part2], Filter), com.histmersion.api.Api.Error, List[Facet], Any] =
    base.get
      .in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]])

  private[api] def writeErrors =
    oneOf[com.histmersion.api.Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
        com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Write.CodeNotFound))
      ),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[com.histmersion.api.Api.Error])(
        com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Backup.CodeMaintenance))
      ),
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[com.histmersion.api.Api.Error])(
        com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Write.CodePrecondition))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
    )

  private def strictQuery = query[Boolean]("strict")
    .default(false)
    .description("refuse the write when its Markdown has a formatting error (default: report only)")

  lazy val putSource
      : PublicEndpoint[(com.histmersion.api.Api.PutSourcePart1Part1, Source.Put, com.histmersion.api.Api.PutSourcePart1Part3), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    endpoint.put
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Put])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Source.WithFindings])

  lazy val patchSource
      : PublicEndpoint[(com.histmersion.api.Api.PatchSourcePart1Part1, Source.Patch, com.histmersion.api.Api.PatchSourcePart1Part3), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    endpoint.patch
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Patch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Source.WithFindings])

  lazy val lintSource: PublicEndpoint[com.histmersion.api.Api.LintSourcePart1, com.histmersion.api.Api.Error, FormatReport, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val fixSourceFormat: PublicEndpoint[com.histmersion.api.Api.FixSourceFormatPart1, com.histmersion.api.Api.Error, FormatReport, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val renameSource: PublicEndpoint[(com.histmersion.api.Api.RenameSourcePart1Part1, Rename.Request), com.histmersion.api.Api.Error, Source.Renamed, Any] =
    writeBase.post
      .in("sources" / path[String]("citekey") / "rename")
      .in(jsonBody[Rename.Request])
      .out(jsonBody[Source.Renamed])

  lazy val deleteSource
      : PublicEndpoint[(com.histmersion.api.Api.DeleteSourcePart1Part1, Source.DeleteRequest), com.histmersion.api.Api.Error, Source.Deletion, Any] =
    writeBase.post
      .in("sources" / path[String]("citekey") / "delete")
      .in(jsonBody[Source.DeleteRequest])
      .out(jsonBody[Source.Deletion])

  lazy val sourceAliases: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Source.Alias], Any] =
    base.get.in("source-aliases").out(jsonBody[List[Source.Alias]])

  lazy val importNote: PublicEndpoint[NoteImport, com.histmersion.api.Api.Error, SeedResult, Any] =
    writeBase.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  lazy val exportSource: PublicEndpoint[com.histmersion.api.Api.ExportSourcePart1, com.histmersion.api.Api.Error, com.histmersion.api.Api.ExportSourcePart3, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors)
      .out(stringBody)

  lazy val citekeys: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[com.histmersion.api.Api.CitekeysPart3], Any] =
    base.get.in("citekeys").out(jsonBody[List[String]])

  lazy val resolveEdges: PublicEndpoint[Unit, com.histmersion.api.Api.Error, EdgesResolved, Any] =
    writeBase.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  lazy val configReload: PublicEndpoint[Unit, com.histmersion.api.Api.Error, ReloadReport, Any] =
    writeBase.post.in("config" / "reload").out(jsonBody[ReloadReport])

  lazy val backup: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Backup.Status, Any] =
    writeBase.post.in("backup").out(jsonBody[Backup.Status])

  /** A job, not a request: Postgres cannot run `VACUUM` inside a transaction. */
  lazy val maintenanceVacuum: PublicEndpoint[Vacuum.Request, com.histmersion.api.Api.Error, JobInfo, Any] =
    writeBase.post.in("maintenance" / "vacuum").in(jsonBody[Vacuum.Request]).out(jsonBody[JobInfo])

  lazy val backupStatus: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Backup.Status, Any] =
    endpoint.get.in("api" / "backup" / "status").errorOut(writeErrors).out(jsonBody[Backup.Status])

  lazy val backups: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Backup.Info], Any] =
    base.get.in("backups").out(jsonBody[List[Backup.Info]])

  lazy val downloadBackup: PublicEndpoint[com.histmersion.api.Api.DownloadBackupPart1, com.histmersion.api.Api.Error, TapirFile, Any] =
    endpoint.get.in("api" / "backups" / path[String]("file")).errorOut(writeErrors).out(fileBody)

  lazy val restore: PublicEndpoint[RestoreRequest, com.histmersion.api.Api.Error, RestoreResult, Any] =
    writeBase.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  lazy val instance: PublicEndpoint[Unit, com.histmersion.api.Api.Error, InstanceInfo, Any] =
    base.get.in("instance").out(jsonBody[InstanceInfo])

  lazy val setInstance: PublicEndpoint[InstanceLabel, com.histmersion.api.Api.Error, InstanceInfo, Any] =
    writeBase.put.in("instance").in(jsonBody[InstanceLabel]).out(jsonBody[InstanceInfo])

  lazy val exportBundle: PublicEndpoint[Option[com.histmersion.api.Api.ExportBundlePart1], com.histmersion.api.Api.Error, (TapirFile, com.histmersion.api.Api.ExportBundlePart3Part2), Any] =
    endpoint.get
      .in("api" / "bundle")
      .in(query[Option[String]]("since"))
      .errorOut(writeErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val verifyBundle: PublicEndpoint[TapirFile, com.histmersion.api.Api.Error, Bundle.VerifyResult, Any] =
    writeBase.post.in("bundle" / "verify").in(fileBody).out(jsonBody[Bundle.VerifyResult])

  /** Not an MCP tool: its input is a file upload, which an MCP call cannot carry. */
  lazy val importBundle: PublicEndpoint[(com.histmersion.api.Api.ImportBundlePart1Part1, TapirFile), com.histmersion.api.Api.Error, Bundle.ImportResult, Any] =
    writeBase.post
      .in("bundle" / "import")
      .in(query[Boolean]("dryRun").default(false))
      .in(fileBody)
      .out(jsonBody[Bundle.ImportResult])

  /** Kept on the server under its sha256 so `merge plan` and `merge import` can name it: their body is the resolution
    * set, and a request carries one body.
    */
  lazy val uploadBundle: PublicEndpoint[TapirFile, com.histmersion.api.Api.Error, Bundle.Upload, Any] =
    writeBase.post.in("bundle" / "upload").in(fileBody).out(jsonBody[Bundle.Upload])

  lazy val mergePlan: PublicEndpoint[MergePlan.Request, com.histmersion.api.Api.Error, MergePlan, Any] =
    writeBase.post.in("merge" / "plan").in(jsonBody[MergePlan.Request]).out(jsonBody[MergePlan])

  lazy val mergeImport: PublicEndpoint[MergeImportRequest, com.histmersion.api.Api.Error, Bundle.ImportResult, Any] =
    writeBase.post.in("merge" / "import").in(jsonBody[MergeImportRequest]).out(jsonBody[Bundle.ImportResult])

  lazy val mergeApply: PublicEndpoint[MergeApplyRequest, com.histmersion.api.Api.Error, MergeApplyJob, Any] =
    writeBase.post.in("merge" / "apply").in(jsonBody[MergeApplyRequest]).out(jsonBody[MergeApplyJob])

  lazy val mergeApplyStatus: PublicEndpoint[com.histmersion.api.Api.MergeApplyStatusPart1, com.histmersion.api.Api.Error, MergeApplyJob, Any] =
    writeBase.get.in("merge" / "apply" / path[String]("job")).out(jsonBody[MergeApplyJob])

  lazy val mergeSession: PublicEndpoint[com.histmersion.api.Api.MergeSessionPart1, com.histmersion.api.Api.Error, MergeSession, Any] =
    writeBase.get.in("merge" / "sessions" / path[String]("bundle")).out(jsonBody[MergeSession])

  lazy val putMergeSession
      : PublicEndpoint[(com.histmersion.api.Api.PutMergeSessionPart1Part1, MergeResolutions), com.histmersion.api.Api.Error, MergeSession, Any] =
    writeBase.put
      .in("merge" / "sessions" / path[String]("bundle"))
      .in(jsonBody[MergeResolutions])
      .out(jsonBody[MergeSession])

  lazy val parseResolutions: PublicEndpoint[com.histmersion.api.Api.ParseResolutionsPart1, com.histmersion.api.Api.Error, MergeResolutions, Any] =
    writeBase.post.in("merge" / "resolutions").in(stringBody).out(jsonBody[MergeResolutions])

  lazy val merges: PublicEndpoint[Option[com.histmersion.api.Api.MergesPart1], com.histmersion.api.Api.Error, List[MergeInfo], Any] =
    base.get.in("merges").in(query[Option[Int]]("limit")).out(jsonBody[List[MergeInfo]])

  lazy val mergeLedger
      : PublicEndpoint[(Option[com.histmersion.api.Api.MergeLedgerPart1Part1], Option[com.histmersion.api.Api.MergeLedgerPart1Part2], Option[com.histmersion.api.Api.MergeLedgerPart1Part3]), com.histmersion.api.Api.Error, MergeLedger, Any] =
    writeBase.get
      .in("merges" / "ledger")
      .in(query[Option[String]]("uid"))
      .in(query[Option[String]]("merge"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[MergeLedger])

  lazy val mergeDetail: PublicEndpoint[com.histmersion.api.Api.MergeDetailPart1, com.histmersion.api.Api.Error, MergeDetail, Any] =
    writeBase.get.in("merges" / path[String]("id")).out(jsonBody[MergeDetail])

  final case class ArticleQuery(
      filter: Filter = Filter(),
      kind: Option[ArticleQuery.Kind] = None,
      text: Option[ArticleQuery.Text] = None
  )

  object ArticleQuery:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val articles: PublicEndpoint[ArticleQuery, com.histmersion.api.Api.Error, Article.List, Any] =
    base.get
      .in("articles")
      .in(filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("text"))
      .mapInTo[ArticleQuery]
      .out(jsonBody[Article.List])

  lazy val renameArticle: PublicEndpoint[(com.histmersion.api.Api.RenameArticlePart1Part1, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    writeBase.post
      .in("articles" / path[String]("slug") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed])

  lazy val getArticle: PublicEndpoint[(com.histmersion.api.Api.GetArticlePart1Part1, com.histmersion.api.Api.GetArticlePart1Part2), com.histmersion.api.Api.Error, Article.View, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug"))
      .in(
        query[Boolean]("brief")
          .default(false)
          .description("leave out the formatting lint and the coverage (a hover card's read)")
      )
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])

  lazy val putArticle
      : PublicEndpoint[(com.histmersion.api.Api.PutArticlePart1Part1, Article.Put, com.histmersion.api.Api.PutArticlePart1Part3), com.histmersion.api.Api.Error, Article.View, Any] =
    endpoint.put
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Put])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])

  lazy val patchArticle
      : PublicEndpoint[(com.histmersion.api.Api.PatchArticlePart1Part1, Article.Patch, com.histmersion.api.Api.PatchArticlePart1Part3), com.histmersion.api.Api.Error, Article.View, Any] =
    endpoint.patch
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Patch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])

  lazy val lintArticle: PublicEndpoint[com.histmersion.api.Api.LintArticlePart1, com.histmersion.api.Api.Error, FormatReport, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val fixArticleFormat: PublicEndpoint[com.histmersion.api.Api.FixArticleFormatPart1, com.histmersion.api.Api.Error, FormatReport, Any] =
    endpoint.post
      .in("api" / "articles" / path[String]("slug") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val checkArticle: PublicEndpoint[
    (com.histmersion.api.Api.CheckArticlePart1Part1, Article.Edits.ArticleCheckRequest),
    com.histmersion.api.Api.Error,
    Article.Edits.ArticleCheck,
    Any
  ] =
    endpoint.post
      .in("api" / "articles" / path[String]("slug") / "check")
      .in(jsonBody[Article.Edits.ArticleCheckRequest])
      .errorOut(writeErrors)
      .out(jsonBody[Article.Edits.ArticleCheck])

  lazy val articleCoverage: PublicEndpoint[com.histmersion.api.Api.ArticleCoveragePart1, com.histmersion.api.Api.Error, Coverage, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "coverage")
      .errorOut(writeErrors)
      .out(jsonBody[Coverage])

  lazy val articleReview: PublicEndpoint[com.histmersion.api.Api.ArticleReviewPart1, com.histmersion.api.Api.Error, Article.ReviewView, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "review")
      .errorOut(writeErrors)
      .out(jsonBody[Article.ReviewView])

  lazy val deleteArticle: PublicEndpoint[com.histmersion.api.Api.DeleteArticlePart1, com.histmersion.api.Api.Error, Article.Deleted, Any] =
    endpoint.delete
      .in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Article.Deleted])

  lazy val exportArticle: PublicEndpoint[com.histmersion.api.Api.ExportArticlePart1, com.histmersion.api.Api.Error, com.histmersion.api.Api.ExportArticlePart3, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(writeErrors)
      .out(stringBody)

  lazy val importArticle: PublicEndpoint[Article.Import, com.histmersion.api.Api.Error, SeedResult, Any] =
    base.post.in("articles" / "import").in(jsonBody[Article.Import]).out(jsonBody[SeedResult])

  lazy val articleVersions: PublicEndpoint[com.histmersion.api.Api.ArticleVersionsPart1, com.histmersion.api.Api.Error, List[Article.Version], Any] =
    base.get.in("articles" / path[String]("slug") / "versions").out(jsonBody[List[Article.Version]])

  lazy val articleVersion: PublicEndpoint[(com.histmersion.api.Api.ArticleVersionPart1Part1, com.histmersion.api.Api.ArticleVersionPart1Part2), com.histmersion.api.Api.Error, Article.VersionFull, Any] =
    base.get
      .in("articles" / path[String]("slug") / "versions" / path[Long]("versionId"))
      .out(jsonBody[Article.VersionFull])

  lazy val articleSlugs: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[com.histmersion.api.Api.ArticleSlugsPart3], Any] =
    base.get.in("article-slugs").out(jsonBody[List[String]])

  // lazy: [[EventsApi]] builds on this object's inputs, so the list waits until both exist
  lazy val all: List[AnyEndpoint] = List(
    version,
    health,
    about,
    config,
    search,
    sources,
    facets,
    // Tapir tries endpoints in order: the longer `sources/{ck}/…` paths go before `sources/{ck}`
    sourceArticles,
    sourceView,
    passages,
    lintSource,
    fixSourceFormat,
    getSource,
    findingsPage,
    findings,
    aggregate,
    neighbors,
    graph,
    contradictions,
    seed,
    putSource,
    patchSource,
    renameSource,
    deleteSource,
    sourceAliases,
    importNote,
    exportSource,
    citekeys,
    resolveEdges,
    reindex,
    configReload,
    backup,
    backups,
    downloadBackup,
    restore,
    maintenanceVacuum,
    instance,
    setInstance,
    exportBundle,
    verifyBundle,
    importBundle,
    uploadBundle,
    mergePlan,
    mergeImport,
    mergeApply,
    mergeApplyStatus,
    mergeSession,
    putMergeSession,
    parseResolutions,
    // `merges/ledger` before `merges/{id}`
    merges,
    mergeLedger,
    mergeDetail,
    renameArticle,
    renameEvent,
    renameGoal,
    renamePlace,
    // `articles/import` and the longer `articles/{slug}/…` paths go before `articles/{slug}`
    articles,
    articleSlugs,
    importArticle,
    exportArticle,
    articleVersions,
    articleVersion,
    lintArticle,
    fixArticleFormat,
    checkArticle,
    articleCoverage,
    articleReview,
    getArticle,
    putArticle,
    patchArticle,
    deleteArticle,
    // `events/geo` before `events/{id}`
    events,
    eventsGeo,
    getEvent,
    putEvent,
    patchEvent,
    deleteEvent,
    attestations,
    getAttestation,
    createAttestation,
    patchAttestation,
    deleteAttestation,
    goals,
    getGoal,
    putGoal,
    patchGoal,
    deleteGoal,
    agentStateList,
    agentStateGet,
    agentStateSet,
    agentStateDelete,
    // `/{id}/children` and `/{id}/tree` before `/{id}`
    places,
    placesGeo,
    placeNames,
    placeOverrides,
    placeSources,
    placeChildren,
    placeTree,
    placeFootprints,
    placeLinks,
    place,
    addPlaceName,
    removePlaceName,
    setPlaceOverride,
    mergePlace,
    removePlace,
    addPlace,
    importPlaces,
    rebuildPlaces,
    fetchExternalPlaces,
    loadPlaces,
    buildHierarchy,
    periods,
    // `periods/seed|import|fetch` are POSTs, so the GET `periods/{id}` never shadows them
    period,
    seedPeriods,
    importPeriods,
    fetchPeriods,
    init,
    // `sources/{ck}/assets` before `sources/{ck}`
    ingest,
    reocr,
    original,
    job,
    jobControl,
    jobs,
    activity,
    assets,
    putAssets,
    assetDetail,
    patchAsset,
    asset,
    mediaGeo,
    assetLinks,
    createAssetLink,
    deleteAssetLink,
    // `media/facets|import|fill-licences` before `media/{uid}`
    mediaFacets,
    mediaProviders,
    mediaImport,
    mediaImportJob,
    mediaCovers,
    mediaCoversJob,
    putSourceCover,
    fillFigureLicences,
    mediaList,
    mediaShow,
    mediaThumb,
    mediaFile,
    // IIIF `info.json` before the image endpoint
    iiifInfo,
    iiifImage,
    resolveId,
    backupStatus
  ) ++ OpsApi.all ++ AgentsApi.all ++ ManagersApi.all ++ AutopilotApi.switches ++ library.AcquisitionWorkApi.all ++
    ManagersApi.steering ++ AutopilotApi.draining ++ ManagersApi.stalled ++ UiApi.all ++ EventsApi.all ++ AcquireApi.all ++ BasemapApi.all ++ TilesApi.all ++ ConfigEditApi.all ++ personal.PersonalApi.all ++
    library.CollectionsApi.all

  lazy val places: PublicEndpoint[(Option[com.histmersion.api.Api.PlacesPart1Part1], Option[com.histmersion.api.Api.PlacesPart1Part2], Option[com.histmersion.api.Api.PlacesPart1Part3]), com.histmersion.api.Api.Error, List[
    Place.Hit
  ], Any] =
    base.get
      .in("places")
      .in(query[Option[String]]("q"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[Place.Hit]])

  final case class PlacesGeoQuery(
      kind: Option[PlacesGeoQuery.Kind] = None,
      asOf: Option[PlacesGeoQuery.AsOf] = None,
      limit: Option[PlacesGeoQuery.Limit] = None,
      from: Option[PlacesGeoQuery.From] = None,
      to: Option[PlacesGeoQuery.To] = None,
      pick: Option[PlacesGeoQuery.Pick] = None
  )

  object PlacesGeoQuery:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOf = AsOf.T
    object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pick = Pick.T
    object Pick extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val placesGeo: PublicEndpoint[PlacesGeoQuery, com.histmersion.api.Api.Error, com.histmersion.api.Api.PlacesGeoPart3, Any] =
    base.get
      .in("places" / "geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("asOf"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[String]]("pick"))
          .mapTo[PlacesGeoQuery]
      )
      .out(stringBody)

  lazy val placeChildren
      : PublicEndpoint[(com.histmersion.api.Api.PlaceChildrenPart1Part1, Option[com.histmersion.api.Api.PlaceChildrenPart1Part2]), com.histmersion.api.Api.Error, List[Place.Summary], Any] =
    base.get
      .in("places" / path[String]("id") / "children")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Summary]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeTree
      : PublicEndpoint[(com.histmersion.api.Api.PlaceTreePart1Part1, Option[com.histmersion.api.Api.PlaceTreePart1Part2], Option[com.histmersion.api.Api.PlaceTreePart1Part3]), com.histmersion.api.Api.Error, List[Place.TreeRow], Any] =
    base.get
      .in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[List[Place.TreeRow]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val place
      : PublicEndpoint[(com.histmersion.api.Api.PlacePart1Part1, Option[com.histmersion.api.Api.PlacePart1Part2], Option[com.histmersion.api.Api.PlacePart1Part3]), com.histmersion.api.Api.Error, PlaceDetail, Any] =
    base.get
      .in("places" / path[String]("id"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Boolean]]("counts"))
      .out(jsonBody[PlaceDetail])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeFootprints
      : PublicEndpoint[(com.histmersion.api.Api.PlaceFootprintsPart1Part1, Option[com.histmersion.api.Api.PlaceFootprintsPart1Part2]), com.histmersion.api.Api.Error, List[Place.Footprint], Any] =
    base.get
      .in("places" / path[String]("id") / "footprints")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Footprint]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeLinks: PublicEndpoint[com.histmersion.api.Api.PlaceLinksPart1, com.histmersion.api.Api.Error, List[Place.Link], Any] =
    base.get
      .in("places" / path[String]("id") / "links")
      .out(jsonBody[List[Place.Link]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val periods: PublicEndpoint[
    (Option[com.histmersion.api.Api.PeriodsPart1Part1], Option[com.histmersion.api.Api.PeriodsPart1Part2], Option[com.histmersion.api.Api.PeriodsPart1Part3], Option[com.histmersion.api.Api.PeriodsPart1Part4]),
    com.histmersion.api.Api.Error,
    List[
      HistoricalPeriod
    ],
    Any
  ] =
    base.get
      .in("periods")
      .in(query[Option[String]]("q"))
      .in(query[Option[String]]("region"))
      .in(query[Option[Long]]("from"))
      .in(query[Option[Long]]("to"))
      .out(jsonBody[List[HistoricalPeriod]])

  lazy val period: PublicEndpoint[com.histmersion.api.Api.PeriodPart1, com.histmersion.api.Api.Error, Period.Detail, Any] =
    base.get
      .in("periods" / path[String]("id"))
      .out(jsonBody[Period.Detail])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val seedPeriods: PublicEndpoint[com.histmersion.api.Api.SeedPeriodsPart1, com.histmersion.api.Api.Error, Period.ImportResult, Any] =
    writeBase.post
      .in("periods" / "seed")
      .in(query[Boolean]("replace").default(false))
      .out(jsonBody[Period.ImportResult])

  lazy val importPeriods: PublicEndpoint[
    (List[com.histmersion.api.Api.ImportPeriodsPart1Part1], Option[com.histmersion.api.Api.ImportPeriodsPart1Part2], com.histmersion.api.Api.ImportPeriodsPart1Part3),
    com.histmersion.api.Api.Error,
    Period.ImportResult,
    Any
  ] =
    writeBase.post
      .in("periods" / "import")
      .in(query[List[String]]("collection"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Period.ImportResult])

  lazy val fetchPeriods
      : PublicEndpoint[(com.histmersion.api.Api.FetchPeriodsPart1Part1, List[com.histmersion.api.Api.FetchPeriodsPart1Part2]), com.histmersion.api.Api.Error, Period.ImportResult, Any] =
    writeBase.post
      .in("periods" / "fetch")
      .in(query[String]("source"))
      .in(query[List[String]]("collection"))
      .out(jsonBody[Period.ImportResult])

  lazy val init: PublicEndpoint[com.histmersion.api.Api.InitPart1, com.histmersion.api.Api.Error, JobInfo, Any] =
    writeBase.post.in("init").in(query[Boolean]("fetch").default(false)).out(jsonBody[JobInfo])

  lazy val importPlaces: PublicEndpoint[
    (Option[com.histmersion.api.Api.ImportPlacesPart1Part1], Option[com.histmersion.api.Api.ImportPlacesPart1Part2], com.histmersion.api.Api.ImportPlacesPart1Part3),
    com.histmersion.api.Api.Error,
    Place.ImportResult,
    Any
  ] =
    writeBase.post
      .in("places" / "import")
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Place.ImportResult])

  lazy val rebuildPlaces: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Place.RebuildResult, Any] =
    writeBase.post.in("places" / "rebuild").out(jsonBody[Place.RebuildResult])

  lazy val fetchExternalPlaces
      : PublicEndpoint[(com.histmersion.api.Api.FetchExternalPlacesPart1Part1, com.histmersion.api.Api.FetchExternalPlacesPart1Part2), com.histmersion.api.Api.Error, Place.ImportResult, Any] =
    writeBase.post
      .in("places" / "fetch")
      .in(query[String]("source"))
      .in(query[Boolean]("dryRun"))
      .out(jsonBody[Place.ImportResult])

  /** Declared before `places/{id}` so `sources` is never read as an id. */
  lazy val placeSources: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Gazetteer.SourceInfo], Any] =
    base.get.in("places" / "sources").out(jsonBody[List[Gazetteer.SourceInfo]])

  lazy val loadPlaces: PublicEndpoint[(com.histmersion.api.Api.LoadPlacesPart1Part1, com.histmersion.api.Api.LoadPlacesPart1Part2), com.histmersion.api.Api.Error, JobInfo, Any] =
    writeBase.post
      .in("places" / "load")
      .in(query[String]("source"))
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[JobInfo])

  /** Declared before `places/{id}` so `names` is never read as an id. */
  lazy val placeNames: PublicEndpoint[Option[com.histmersion.api.Api.PlaceNamesPart1], com.histmersion.api.Api.Error, List[ManualPlaceName], Any] =
    base.get.in("places" / "names").in(query[Option[String]]("place")).out(jsonBody[List[ManualPlaceName]])

  lazy val addPlaceName
      : PublicEndpoint[(com.histmersion.api.Api.AddPlaceNamePart1Part1, Place.NameAdd), com.histmersion.api.Api.Error, Place.NameChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "names")
      .in(jsonBody[Place.NameAdd])
      .out(jsonBody[Place.NameChanged])

  lazy val removePlaceName
      : PublicEndpoint[(com.histmersion.api.Api.RemovePlaceNamePart1Part1, Place.NameRemove), com.histmersion.api.Api.Error, Place.NameChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "names" / "remove")
      .in(jsonBody[Place.NameRemove])
      .out(jsonBody[Place.NameChanged])

  /** Declared before `places/{id}` so `overrides` is never read as an id. */
  lazy val placeOverrides: PublicEndpoint[Option[com.histmersion.api.Api.PlaceOverridesPart1], com.histmersion.api.Api.Error, List[Place.Override], Any] =
    base.get.in("places" / "overrides").in(query[Option[String]]("place")).out(jsonBody[List[Place.Override]])

  lazy val setPlaceOverride
      : PublicEndpoint[(com.histmersion.api.Api.SetPlaceOverridePart1Part1, Place.OverrideSet), com.histmersion.api.Api.Error, Place.OverrideChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "override")
      .in(jsonBody[Place.OverrideSet])
      .out(jsonBody[Place.OverrideChanged])

  lazy val mergePlace: PublicEndpoint[(com.histmersion.api.Api.MergePlacePart1Part1, Place.MergeIn), com.histmersion.api.Api.Error, Place.Gone, Any] =
    writeBase.post.in("places" / path[String]("id") / "merge").in(jsonBody[Place.MergeIn]).out(jsonBody[Place.Gone])

  lazy val removePlace: PublicEndpoint[(com.histmersion.api.Api.RemovePlacePart1Part1, Place.RemoveIn), com.histmersion.api.Api.Error, Place.Gone, Any] =
    writeBase.post.in("places" / path[String]("id") / "remove").in(jsonBody[Place.RemoveIn]).out(jsonBody[Place.Gone])

  lazy val addPlace: PublicEndpoint[Place.Add, com.histmersion.api.Api.Error, Place.Summary, Any] =
    writeBase.post.in("places").in(jsonBody[Place.Add]).out(jsonBody[Place.Summary])

  lazy val buildHierarchy: PublicEndpoint[Unit, com.histmersion.api.Api.Error, SpatialHierarchyResult, Any] =
    writeBase.post.in("places" / "build-hierarchy").out(jsonBody[SpatialHierarchyResult])

  final case class EventQuery(
      filter: Filter = Filter(),
      text: Option[EventQuery.Text] = None,
      kind: Option[EventQuery.Kind] = None,
      historicity: Option[EventQuery.Historicity] = None,
      placeId: Option[EventQuery.PlaceId] = None,
      tier: Option[EventQuery.Tier] = None,
      context: Option[EventQuery.Context] = None,
      area: Option[EventQuery.Area] = None
  )

  object EventQuery:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Historicity = Historicity.T
    object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceId = PlaceId.T
    object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Area = Area.T
    object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val events: PublicEndpoint[EventQuery, com.histmersion.api.Api.Error, EventList, Any] =
    base.get
      .in("events")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventList])

  lazy val eventsGeo: PublicEndpoint[EventQuery, com.histmersion.api.Api.Error, EventGeo.List, Any] =
    base.get
      .in("events" / "geo")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventGeo.List])

  lazy val renameEvent: PublicEndpoint[(com.histmersion.api.Api.RenameEventPart1Part1, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    writeBase.post.in("events" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val getEvent: PublicEndpoint[com.histmersion.api.Api.GetEventPart1, com.histmersion.api.Api.Error, EventDetail, Any] =
    endpoint.get
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val putEvent: PublicEndpoint[(com.histmersion.api.Api.PutEventPart1Part1, EventPut), com.histmersion.api.Api.Error, EventDetail, Any] =
    endpoint.put
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPut])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val patchEvent: PublicEndpoint[(com.histmersion.api.Api.PatchEventPart1Part1, EventPatch), com.histmersion.api.Api.Error, EventDetail, Any] =
    endpoint.patch
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPatch])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val deleteEvent: PublicEndpoint[com.histmersion.api.Api.DeleteEventPart1, com.histmersion.api.Api.Error, EventDeleted, Any] =
    endpoint.delete
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDeleted])

  final case class AttestationQuery(
      citekey: Option[com.histmersion.api.library.Citekey] = None,
      targetType: Option[AttestationQuery.TargetType] = None,
      targetId: Option[AttestationQuery.TargetId] = None,
      limit: Option[AttestationQuery.Limit] = None,
      offset: Option[AttestationQuery.Offset] = None
  )

  object AttestationQuery:
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val attestations: PublicEndpoint[AttestationQuery, com.histmersion.api.Api.Error, Attestation.List, Any] =
    base.get
      .in("attestations")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("targetType"))
      .in(query[Option[String]]("targetId"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[AttestationQuery]
      .out(jsonBody[Attestation.List])

  lazy val getAttestation: PublicEndpoint[com.histmersion.api.Api.GetAttestationPart1, com.histmersion.api.Api.Error, Attestation, Any] =
    endpoint.get
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val createAttestation: PublicEndpoint[Attestation.Put, com.histmersion.api.Api.Error, Attestation, Any] =
    endpoint.post
      .in("api" / "attestations")
      .in(jsonBody[Attestation.Put])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val patchAttestation
      : PublicEndpoint[(com.histmersion.api.Api.PatchAttestationPart1Part1, Attestation.Patch), com.histmersion.api.Api.Error, Attestation, Any] =
    endpoint.patch
      .in("api" / "attestations" / path[Long]("id"))
      .in(jsonBody[Attestation.Patch])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val deleteAttestation: PublicEndpoint[com.histmersion.api.Api.DeleteAttestationPart1, com.histmersion.api.Api.Error, Attestation.Deleted, Any] =
    endpoint.delete
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[Attestation.Deleted])

  final case class GoalQuery(
      status: Option[GoalQuery.Status] = None,
      limit: Option[GoalQuery.Limit] = None,
      offset: Option[GoalQuery.Offset] = None
  )

  object GoalQuery:
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val goals: PublicEndpoint[GoalQuery, com.histmersion.api.Api.Error, Goal.List, Any] =
    base.get
      .in("goals")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[GoalQuery]
      .out(jsonBody[Goal.List])

  lazy val renameGoal: PublicEndpoint[(com.histmersion.api.Api.RenameGoalPart1Part1, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    writeBase.post.in("goals" / path[String]("slug") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val renamePlace: PublicEndpoint[(com.histmersion.api.Api.RenamePlacePart1Part1, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    writeBase.post.in("places" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val getGoal: PublicEndpoint[com.histmersion.api.Api.GetGoalPart1, com.histmersion.api.Api.Error, Goal, Any] =
    endpoint.get
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val putGoal: PublicEndpoint[(com.histmersion.api.Api.PutGoalPart1Part1, Goal.Put), com.histmersion.api.Api.Error, Goal, Any] =
    endpoint.put
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Put])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val patchGoal: PublicEndpoint[(com.histmersion.api.Api.PatchGoalPart1Part1, Goal.Patch), com.histmersion.api.Api.Error, Goal, Any] =
    endpoint.patch
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Patch])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val deleteGoal: PublicEndpoint[com.histmersion.api.Api.DeleteGoalPart1, com.histmersion.api.Api.Error, Goal.Deleted, Any] =
    endpoint.delete
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Goal.Deleted])

  lazy val agentStateList: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[AgentStateEntry], Any] =
    base.get.in("agent-state").out(jsonBody[List[AgentStateEntry]])

  lazy val agentStateGet: PublicEndpoint[com.histmersion.api.Api.AgentStateGetPart1, com.histmersion.api.Api.Error, AgentStateEntry, Any] =
    endpoint.get
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])

  lazy val agentStateSet: PublicEndpoint[(com.histmersion.api.Api.AgentStateSetPart1Part1, AgentStatePut), com.histmersion.api.Api.Error, AgentStateEntry, Any] =
    endpoint.put
      .in("api" / "agent-state" / path[String]("key"))
      .in(jsonBody[AgentStatePut])
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])

  lazy val agentStateDelete: PublicEndpoint[com.histmersion.api.Api.AgentStateDeletePart1, com.histmersion.api.Api.Error, AgentStateDeleted, Any] =
    endpoint.delete
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateDeleted])

  private lazy val ingestErrors =
    oneOf[com.histmersion.api.Api.Error](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[com.histmersion.api.Api.Error])(
        com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Ingest.CodeConflict))
      ),
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
        com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Write.CodeNotFound))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
    )

  /** Query parameters, not a multipart part list: tapir's netty server has no multipart request-body support
    * (`RawBodyType.MultipartBody` throws `UnsupportedOperationException`), so the document travels as the raw body.
    */
  lazy val ingestParams: EndpointInput[Ingest.Params] =
    query[String]("citekey")
      .and(query[Option[String]]("filename"))
      .and(query[Boolean]("quality").default(false))
      .and(query[Boolean]("force").default(false))
      .and(query[Option[String]]("title"))
      .and(query[List[String]]("author"))
      .and(query[Option[Int]]("year"))
      .and(query[Option[String]]("doi"))
      .and(query[Option[String]]("provenance"))
      .and(
        query[Option[Int]]("acquireItem")
          .description("id of the acquisition item this upload fulfils; the OCR job ticks it off when it succeeds")
      )
      .mapTo[Ingest.Params]

  lazy val ingest: PublicEndpoint[(Ingest.Params, TapirFile), com.histmersion.api.Api.Error, Ingest.Accepted, Any] =
    endpoint.post
      .in("api" / "ingest")
      .in(ingestParams)
      .in(fileBody)
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted])

  lazy val reocr: PublicEndpoint[(com.histmersion.api.Api.ReocrPart1Part1, com.histmersion.api.Api.ReocrPart1Part2), com.histmersion.api.Api.Error, Ingest.Accepted, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "reocr")
      .in(query[Boolean]("quality").default(false))
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted])

  lazy val original: PublicEndpoint[com.histmersion.api.Api.OriginalPart1, com.histmersion.api.Api.Error, (TapirFile, com.histmersion.api.Api.OriginalPart3Part2), Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "original")
      .errorOut(ingestErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val job: PublicEndpoint[com.histmersion.api.Api.JobPart1, com.histmersion.api.Api.Error, JobInfo, Any] =
    base.get
      .in("jobs" / path[String]("id"))
      .out(jsonBody[JobInfo])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val jobControl: PublicEndpoint[(com.histmersion.api.Api.JobControlPart1Part1, com.histmersion.api.Api.JobControlPart1Part2), com.histmersion.api.Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "jobs" / path[String]("id") / path[String]("action"))
      .errorOut(
        oneOf[com.histmersion.api.Api.Error](
          oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
            com.histmersion.api.Api.Error.hasCode(Write.CodeNotFound)
          ),
          oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[com.histmersion.api.Api.Error])(
            com.histmersion.api.Api.Error.hasCode(JobActions.CodeConflict)
          ),
          oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
        )
      )
      .out(jsonBody[JobInfo])

  lazy val activity: PublicEndpoint[
    (Option[com.histmersion.api.Api.ActivityPart1Part1], Option[com.histmersion.api.Api.ActivityPart1Part2], Option[com.histmersion.api.Api.ActivityPart1Part3], Option[com.histmersion.api.Api.ActivityPart1Part4], Option[com.histmersion.api.Api.ActivityPart1Part5]),
    com.histmersion.api.Api.Error,
    Activity.Page,
    Any
  ] =
    base.get
      .in("activity")
      .in(
        query[Option[String]]("show").description(
          s"one of: ${Activity.Show.values.map(_.wire).mkString(", ")} (default all)"
        )
      )
      .in(query[Option[String]]("since").description("from: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .in(query[Option[String]]("goal").description("only what concerns this research goal (its slug)"))
      .in(
        query[Option[Int]]("limit")
          .description(s"entries per page (default ${Activity.DefaultLimit}, at most ${Activity.MaxLimit})")
      )
      .in(query[Option[String]]("before").description("the next older page: the cursor the last page gave"))
      .out(jsonBody[Activity.Page])

  lazy val jobs: PublicEndpoint[
    (ListFilters.JobFilter, Option[com.histmersion.api.Api.JobsPart1Part2], Option[com.histmersion.api.Api.JobsPart1Part3], Option[com.histmersion.api.Api.JobsPart1Part4]),
    com.histmersion.api.Api.Error,
    (List[JobInfo], Paging.Page),
    Any
  ] =
    base.get
      .in("jobs")
      .in(ListFilters.jobQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[JobInfo]])
      .out(Paging.headers)

  lazy val assets: PublicEndpoint[com.histmersion.api.Api.AssetsPart1, com.histmersion.api.Api.Error, List[AssetInfo], Any] =
    base.get.in("sources" / path[String]("citekey") / "assets").out(jsonBody[List[AssetInfo]])

  lazy val putAssets: PublicEndpoint[(com.histmersion.api.Api.PutAssetsPart1Part1, List[AssetInput]), com.histmersion.api.Api.Error, AssetsStored, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "assets")
      .in(jsonBody[List[AssetInput]])
      .errorOut(ingestErrors)
      .out(jsonBody[AssetsStored])

  lazy val asset: PublicEndpoint[(com.histmersion.api.Api.AssetPart1Part1, com.histmersion.api.Api.AssetPart1Part2), com.histmersion.api.Api.Error, (com.histmersion.api.Api.AssetPart3Part1, com.histmersion.api.Api.AssetPart3Part2), Any] =
    endpoint.get
      .in("source" / path[String]("citekey") / "assets" / path[String]("name"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.AssetCacheControl)))

  lazy val assetDetail: PublicEndpoint[(com.histmersion.api.Api.AssetDetailPart1Part1, com.histmersion.api.Api.AssetDetailPart1Part2), com.histmersion.api.Api.Error, AssetDetail, Any] =
    base.get
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name") / "detail")
      .out(jsonBody[AssetDetail])

  lazy val patchAsset: PublicEndpoint[(com.histmersion.api.Api.PatchAssetPart1Part1, com.histmersion.api.Api.PatchAssetPart1Part2, AssetPatch), com.histmersion.api.Api.Error, AssetDetail, Any] =
    base.patch
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name"))
      .in(jsonBody[AssetPatch])
      .out(jsonBody[AssetDetail])

  final case class MediaGeoQuery(
      kind: Option[MediaGeoQuery.Kind] = None,
      from: Option[MediaGeoQuery.From] = None,
      to: Option[MediaGeoQuery.To] = None,
      limit: Option[MediaGeoQuery.Limit] = None,
      provider: Option[MediaGeoQuery.Provider] = None,
      license: Option[MediaGeoQuery.License] = None
  )

  object MediaGeoQuery:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val mediaGeo: PublicEndpoint[MediaGeoQuery, com.histmersion.api.Api.Error, List[MediaGeoHit], Any] =
    base.get
      .in("media-geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[String]]("provider"))
          .and(query[Option[String]]("license"))
          .mapTo[MediaGeoQuery]
      )
      .out(jsonBody[List[MediaGeoHit]])

  final case class MediaQuery(
      provider: Option[MediaQuery.Provider] = None,
      license: Option[MediaQuery.License] = None,
      place: Option[MediaQuery.Place] = None,
      event: Option[MediaQuery.Event] = None,
      q: Option[MediaQuery.Q] = None,
      limit: Option[MediaQuery.Limit] = None,
      offset: Option[MediaQuery.Offset] = None
  )

  object MediaQuery:
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Place = Place.T
    object Place extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Event = Event.T
    object Event extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Q = Q.T
    object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val mediaList: PublicEndpoint[MediaQuery, com.histmersion.api.Api.Error, MediaList, Any] =
    base.get
      .in("media")
      .in(
        query[Option[String]]("provider")
          .and(query[Option[String]]("license"))
          .and(query[Option[String]]("place"))
          .and(query[Option[String]]("event"))
          .and(query[Option[String]]("q"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Int]]("offset"))
          .mapTo[MediaQuery]
      )
      .out(jsonBody[MediaList])

  lazy val mediaFacets: PublicEndpoint[Unit, com.histmersion.api.Api.Error, MediaFacets, Any] =
    base.get.in("media" / "facets").out(jsonBody[MediaFacets])

  lazy val mediaProviders: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[MediaProvider.Info], Any] =
    base.get.in("media" / "providers").out(jsonBody[List[MediaProvider.Info]])

  lazy val mediaShow: PublicEndpoint[com.histmersion.api.Api.MediaShowPart1, com.histmersion.api.Api.Error, MediaItem, Any] =
    base.get
      .in("media" / path[String]("uid"))
      .out(jsonBody[MediaItem])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val mediaImport: PublicEndpoint[MediaImportRequest, com.histmersion.api.Api.Error, MediaImportReport, Any] =
    base.post.in("media" / "import").in(jsonBody[MediaImportRequest]).out(jsonBody[MediaImportReport])

  lazy val mediaImportJob: PublicEndpoint[MediaImportRequest, com.histmersion.api.Api.Error, JobInfo, Any] =
    base.post
      .in("media" / "import" / "job")
      .in(jsonBody[MediaImportRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val mediaCovers: PublicEndpoint[MediaCoversRequest, com.histmersion.api.Api.Error, MediaCoversReport, Any] =
    base.post.in("media" / "covers").in(jsonBody[MediaCoversRequest]).out(jsonBody[MediaCoversReport])

  lazy val mediaCoversJob: PublicEndpoint[MediaCoversRequest, com.histmersion.api.Api.Error, JobInfo, Any] =
    base.post
      .in("media" / "covers" / "job")
      .in(jsonBody[MediaCoversRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val putSourceCover
      : PublicEndpoint[(SourceCoverParams, com.histmersion.api.Api.PutSourceCoverPart1Part2), com.histmersion.api.Api.Error, MediaItem, Any] =
    writeBase.put
      .in("media" / "sources" / path[String]("citekey") / "cover")
      .in(query[String]("provider").default(MediaProvider.Calibre))
      .in(query[String]("key"))
      .in(query[Option[String]]("mime"))
      .mapInTo[SourceCoverParams]
      .in(byteArrayBody)
      .out(jsonBody[MediaItem])

  lazy val fillFigureLicences: PublicEndpoint[com.histmersion.api.Api.FillFigureLicencesPart1, com.histmersion.api.Api.Error, FigureLicenceFill, Any] =
    base.post
      .in("media" / "fill-licences")
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[FigureLicenceFill])

  lazy val mediaThumb: PublicEndpoint[com.histmersion.api.Api.MediaThumbPart1, com.histmersion.api.Api.Error, (com.histmersion.api.Api.MediaThumbPart3Part1, com.histmersion.api.Api.MediaThumbPart3Part2), Any] =
    endpoint.get
      .in("media" / path[String]("uid") / "thumb")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))

  lazy val mediaFile: PublicEndpoint[com.histmersion.api.Api.MediaFilePart1, com.histmersion.api.Api.Error, (com.histmersion.api.Api.MediaFilePart3Part1, com.histmersion.api.Api.MediaFilePart3Part2), Any] =
    endpoint.get
      .in("media" / path[String]("uid") / "file")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))

  lazy val assetLinks: PublicEndpoint[
    (Option[com.histmersion.api.Api.AssetLinksPart1Part1], Option[com.histmersion.api.Api.AssetLinksPart1Part2], Option[com.histmersion.api.Api.AssetLinksPart1Part3], Option[com.histmersion.api.Api.AssetLinksPart1Part4]),
    com.histmersion.api.Api.Error,
    AssetLink.List,
    Any
  ] =
    base.get
      .in("asset-links")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("asset"))
      .in(query[Option[String]]("target_type"))
      .in(query[Option[String]]("target_id"))
      .out(jsonBody[AssetLink.List])

  lazy val createAssetLink: PublicEndpoint[AssetLink.Put, com.histmersion.api.Api.Error, AssetLink, Any] =
    base.post.in("asset-links").in(jsonBody[AssetLink.Put]).out(jsonBody[AssetLink])

  lazy val deleteAssetLink: PublicEndpoint[com.histmersion.api.Api.DeleteAssetLinkPart1, com.histmersion.api.Api.Error, AssetLink.Deleted, Any] =
    base.delete.in("asset-links" / path[Long]("id")).out(jsonBody[AssetLink.Deleted])

  /** Registered before the image endpoint so Tapir matches it first. */
  lazy val iiifInfo: PublicEndpoint[(com.histmersion.api.Api.IiifInfoPart1Part1, com.histmersion.api.Api.IiifInfoPart1Part2), com.histmersion.api.Api.Error, io.circe.Json, Any] =
    endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") / "info.json")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(jsonBody[io.circe.Json])
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))

  /** IIIF Image API Level 1: region `full|x,y,w,h`, size `max|^max|w,|,h|w,h|!w,h`, rotation `0` only, quality
    * `default`, format `jpg|png`.
    */
  lazy val iiifImage: PublicEndpoint[
    (com.histmersion.api.Api.IiifImagePart1Part1, com.histmersion.api.Api.IiifImagePart1Part2, com.histmersion.api.Api.IiifImagePart1Part3, com.histmersion.api.Api.IiifImagePart1Part4, com.histmersion.api.Api.IiifImagePart1Part5, com.histmersion.api.Api.IiifImagePart1Part6),
    com.histmersion.api.Api.Error,
    (com.histmersion.api.Api.IiifImagePart3Part1, com.histmersion.api.Api.IiifImagePart3Part2),
    Any
  ] =
    endpoint.get
      .in(
        "iiif" / path[String]("citekey") / path[String]("name") /
          path[String]("region") / path[String]("size") / path[String]("rotation") /
          path[String]("filename")
      )
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, "public, max-age=31536000, immutable")))

  final case class Error(message: Error.Message, code: Option[Error.Code] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
  object Error:


    /** Tapir hands the matcher an `Any`, and under `-source:future` a constructor pattern on `Any` warns (it is not
      * `Matchable`), so the value is checked with a type test.
      */
    type HasCodeResultPart2 = HasCodeResultPart2.T
    object HasCodeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasCodeCode = HasCodeCode.T
    object HasCodeCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def hasCode(code: com.histmersion.api.Api.Error.HasCodeCode): PartialFunction[Any, com.histmersion.api.Api.Error.HasCodeResultPart2] =
      case e if e.isInstanceOf[Error] && e.asInstanceOf[Error].code.contains(code) => _root_.com.histmersion.api.Api.Error.HasCodeResultPart2(true)

  object Index:

    type PathResult = PathResult.T
    object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type MethodResult = MethodResult.T
    object MethodResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpliceResultPart1 = SpliceResultPart1.T
    object SpliceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpliceResultPart2 = SpliceResultPart2.T
    object SpliceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpliceReadme = SpliceReadme.T
    object SpliceReadme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpliceIndex = SpliceIndex.T
    object SpliceIndex extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentResult = CurrentResult.T
    object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentReadme = CurrentReadme.T
    object CurrentReadme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Begin = "<!-- BEGIN API INDEX (generated by `just api-docs` — do not edit by hand) -->"
    val End = "<!-- END API INDEX -->"

    def path(e: AnyEndpoint): com.histmersion.api.Api.Index.PathResult = _root_.com.histmersion.api.Api.Index.PathResult(e.showPathTemplate(showQueryParam = None))

    def method(e: AnyEndpoint): com.histmersion.api.Api.Index.MethodResult = _root_.com.histmersion.api.Api.Index.MethodResult(e.method.map(_.method).getOrElse("GET"))

    def markdown(endpoints: List[AnyEndpoint]): com.histmersion.api.Api.Index.MarkdownResult =
      val order = List("GET", "POST", "PUT", "PATCH", "DELETE")
      val byPath = endpoints.groupBy(path).view.mapValues(_.map(method).distinct.sortBy(m => order.indexOf(m))).toMap
      val rows = byPath.keys.toList.sorted.map(p => s"| ${byPath(p).map(m => s"`$m`").mkString(" · ")} | `$p` |")
      (s"${Plural.count(endpoints.size, "endpoint")} on ${Plural.count(byPath.size, "path")}:" ::
        "" :: "| methods | path |" :: "|---|---|" :: rows).mkString("\n")

    val Page = "docs/user-guide/reference/rest-api.md"

    def splice(readme: com.histmersion.api.Api.Index.SpliceReadme, index: com.histmersion.api.Api.Index.SpliceIndex): Either[com.histmersion.api.Api.Index.SpliceResultPart1, com.histmersion.api.Api.Index.SpliceResultPart2] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      if b < 0 || e < b then Left(_root_.com.histmersion.api.Api.Index.SpliceResultPart1(s"$Page has no $Begin … $End block"))
      else Right(_root_.com.histmersion.api.Api.Index.SpliceResultPart2((readme).value.substring(0, b + Begin.length) + "\n" + index + "\n" + (readme).value.substring(e)))

    def current(readme: com.histmersion.api.Api.Index.CurrentReadme): Option[com.histmersion.api.Api.Index.CurrentResult] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      Option.when(b >= 0 && e > b)(_root_.com.histmersion.api.Api.Index.CurrentResult((readme).value.substring(b + Begin.length, e).trim))

  @main def generateApiIndex(repo: com.histmersion.api.Api.GenerateApiIndexRepo): Unit =
    val p = java.nio.file.Path.of((repo).value).resolve(Index.Page)
    Index.splice(_root_.com.histmersion.api.Api.Index.SpliceReadme(java.nio.file.Files.readString(p)), Index.markdown(Api.all)) match
      case Left(e)  => throw IllegalStateException(e)
      case Right(s) => val _ = java.nio.file.Files.writeString(p, s)
