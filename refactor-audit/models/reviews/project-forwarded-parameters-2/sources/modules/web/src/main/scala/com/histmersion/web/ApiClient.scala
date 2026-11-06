package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Promise
import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import sttp.client4.fetch.FetchBackend
import sttp.model.Method
import sttp.tapir.{DecodeResult, PublicEndpoint}
import sttp.tapir.client.sttp4.SttpClientInterpreter

import com.histmersion.api.{personal, Api, OpsApi, Paging}
import com.histmersion.api.automation.{
  Activity,
  AgentMode,
  Agents,
  AgentsApi,
  AgentScheduler,
  ApprovalDigest,
  Autopilot,
  Goal,
  ListFilters,
  Managers,
  ManagersApi,
  ModelChains,
  PlanQuota,
  Queue,
  Stalls
}
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  EventImportRequest,
  EventList,
  EventsApi,
  HistoricalPeriod,
  TilesApi
}
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyRequest, MergePlan, MergeResolutions, MergeSession}
import com.histmersion.api.instance.{About, UiApi, UiConfig, UiNotices}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{
  AcquireApi,
  DiscoverResult,
  Facet,
  Filter,
  GeoHit,
  NeighborRow,
  SearchResult,
  TimeHistogram
}
import com.histmersion.api.media.{MediaFacets, MediaGeoHit, MediaImportRequest}
import com.histmersion.api.research.Backlink
import com.histmersion.api.settings.{ConfigEdit, ConfigEditApi}
import com.histmersion.api.views.{AutopilotApi, AutopilotStatus, EventDetail, PlaceDetail, QueueListing, ResolvedId}
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.research.Article

object ApiClient:

  type IsWriteResult = IsWriteResult.T
  object IsWriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileTemplateResult = TileTemplateResult.T
  object TileTemplateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsField = FacetsField.T
  object FacetsField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsPrefix = FacetsPrefix.T
  object FacetsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdId = ResolveIdId.T
  object ResolveIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSourceCk = GetSourceCk.T
  object GetSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSourceBody = GetSourceBody.T
  object GetSourceBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceArticlesCk = SourceArticlesCk.T
  object SourceArticlesCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsCk = NeighborsCk.T
  object NeighborsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsKinds = NeighborsKinds.T
  object NeighborsKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetArticleSlug = GetArticleSlug.T
  object GetArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetArticleBrief = GetArticleBrief.T
  object GetArticleBrief extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesQ = PlacesQ.T
  object PlacesQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesAsOf = PlacesAsOf.T
  object PlacesAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesLimit = PlacesLimit.T
  object PlacesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAsOf = PlaceAsOf.T
  object PlaceAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceCounts = PlaceCounts.T
  object PlaceCounts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkSeenBoard = MarkSeenBoard.T
  object MarkSeenBoard extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkSeenAt = MarkSeenAt.T
  object MarkSeenAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListList = QueueListList.T
  object QueueListList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListAll = QueueListAll.T
  object QueueListAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueForGoalList = QueueForGoalList.T
  object QueueForGoalList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueForGoalGoal = QueueForGoalGoal.T
  object QueueForGoalGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueForGoalAll = QueueForGoalAll.T
  object QueueForGoalAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueOfJobList = QueueOfJobList.T
  object QueueOfJobList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueOfJobJob = QueueOfJobJob.T
  object QueueOfJobJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityShow = ActivityShow.T
  object ActivityShow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivitySince = ActivitySince.T
  object ActivitySince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityGoal = ActivityGoal.T
  object ActivityGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityLimit = ActivityLimit.T
  object ActivityLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivityBefore = ActivityBefore.T
  object ActivityBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobControlId = JobControlId.T
  object JobControlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobControlAction = JobControlAction.T
  object JobControlAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditBookmarkId = EditBookmarkId.T
  object EditBookmarkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveBookmarkId = RemoveBookmarkId.T
  object RemoveBookmarkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditNoteId = EditNoteId.T
  object EditNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveNoteId = RemoveNoteId.T
  object RemoveNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadKind = UnreadKind.T
  object UnreadKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadLimit = UnreadLimit.T
  object UnreadLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadOffset = UnreadOffset.T
  object UnreadOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsPrefix = SettingsPrefix.T
  object SettingsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitDataFetch = InitDataFetch.T
  object InitDataFetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadPlacesSource = LoadPlacesSource.T
  object LoadPlacesSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportEventsProvider = ImportEventsProvider.T
  object ImportEventsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportMediaProvider = ImportMediaProvider.T
  object ImportMediaProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueuePatchId = QueuePatchId.T
  object QueuePatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueAddList = QueueAddList.T
  object QueueAddList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueDeleteId = QueueDeleteId.T
  object QueueDeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphCitekey = GraphCitekey.T
  object GraphCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphDepth = GraphDepth.T
  object GraphDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsQ = PeriodsQ.T
  object PeriodsQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsRegion = PeriodsRegion.T
  object PeriodsRegion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsFrom = PeriodsFrom.T
  object PeriodsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsTo = PeriodsTo.T
  object PeriodsTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesGeoResult = PlacesGeoResult.T
  object PlacesGeoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetEventId = GetEventId.T
  object GetEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverQ = DiscoverQ.T
  object DiscoverQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverLimit = DiscoverLimit.T
  object DiscoverLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsLimit = JobsLimit.T
  object JobsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsBefore = JobsBefore.T
  object JobsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsUpTo = JobsUpTo.T
  object JobsUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSourceCk = PatchSourceCk.T
  object PatchSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentRunsLimit = AgentRunsLimit.T
  object AgentRunsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentRunsBefore = AgentRunsBefore.T
  object AgentRunsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentRunsUpTo = AgentRunsUpTo.T
  object AgentRunsUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentRunId = AgentRunId.T
  object AgentRunId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentControlId = AgentControlId.T
  object AgentControlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentControlAction = AgentControlAction.T
  object AgentControlAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentControlBudgetUsd = AgentControlBudgetUsd.T
  object AgentControlBudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetChainRole = SetChainRole.T
  object SetChainRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetChainModels = SetChainModels.T
  object SetChainModels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResetChainRole = ResetChainRole.T
  object ResetChainRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveAllowanceTarget = RemoveAllowanceTarget.T
  object RemoveAllowanceTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentDoctorOffline = AgentDoctorOffline.T
  object AgentDoctorOffline extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPoolPool = SetPoolPool.T
  object SetPoolPool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshQuotaPool = RefreshQuotaPool.T
  object RefreshQuotaPool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsLimit = ApprovalsLimit.T
  object ApprovalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsBefore = ApprovalsBefore.T
  object ApprovalsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsUpTo = ApprovalsUpTo.T
  object ApprovalsUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideId = DecideId.T
  object DecideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideNote = DecideNote.T
  object DecideNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsLimit = GoalsLimit.T
  object GoalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalViewSlug = GoalViewSlug.T
  object GoalViewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetGoalSlug = GetGoalSlug.T
  object GetGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutGoalSlug = PutGoalSlug.T
  object PutGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddGoalLinkSlug = AddGoalLinkSlug.T
  object AddGoalLinkSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveGoalLinkSlug = RemoveGoalLinkSlug.T
  object RemoveGoalLinkSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveGoalLinkRef = RemoveGoalLinkRef.T
  object RemoveGoalLinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleSlug = SetScheduleSlug.T
  object SetScheduleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveScheduleSlug = RemoveScheduleSlug.T
  object RemoveScheduleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestProgressArg1 = IngestProgressArg1.T
  object IngestProgressArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeSessionBundle = MergeSessionBundle.T
  object MergeSessionBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutMergeSessionBundle = PutMergeSessionBundle.T
  object PutMergeSessionBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResolutionsYaml = ParseResolutionsYaml.T
  object ParseResolutionsYaml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeApplyStatusJob = MergeApplyStatusJob.T
  object MergeApplyStatusJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadBundleProgressArg1 = UploadBundleProgressArg1.T
  object UploadBundleProgressArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetAutopilotOn = SetAutopilotOn.T
  object SetAutopilotOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFocusAction = SetFocusAction.T
  object SetFocusAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFocusGoal = SetFocusGoal.T
  object SetFocusGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFocusValue = SetFocusValue.T
  object SetFocusValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerLimit = SchedulerLimit.T
  object SchedulerLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSchedulerStrategy = SetSchedulerStrategy.T
  object SetSchedulerStrategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerWhyItem = SchedulerWhyItem.T
  object SchedulerWhyItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainOn = DrainOn.T
  object DrainOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StallsDays = StallsDays.T
  object StallsDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerUpdateId = ManagerUpdateId.T
  object ManagerUpdateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerActionId = ManagerActionId.T
  object ManagerActionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerActionAction = ManagerActionAction.T
  object ManagerActionAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerActionRuns = ManagerActionRuns.T
  object ManagerActionRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItemsId = ManagerItemsId.T
  object ManagerItemsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItemsStatus = ManagerItemsStatus.T
  object ManagerItemsStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItemsOffset = ManagerItemsOffset.T
  object ManagerItemsOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItemsLimit = ManagerItemsLimit.T
  object ManagerItemsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPatchItemId = ManagerPatchItemId.T
  object ManagerPatchItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPatchItemItem = ManagerPatchItemItem.T
  object ManagerPatchItemItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerAddHintId = ManagerAddHintId.T
  object ManagerAddHintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerRemoveHintId = ManagerRemoveHintId.T
  object ManagerRemoveHintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerRemoveHintHint = ManagerRemoveHintHint.T
  object ManagerRemoveHintHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerEventsId = ManagerEventsId.T
  object ManagerEventsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerEventsBefore = ManagerEventsBefore.T
  object ManagerEventsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerEventsLimit = ManagerEventsLimit.T
  object ManagerEventsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueueTarget = EnqueueTarget.T
  object EnqueueTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueueReviewSlug = EnqueueReviewSlug.T
  object EnqueueReviewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchHtmlResult = FetchHtmlResult.T
  object FetchHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchHtmlUrl = _root_.com.histmersion.web.ApiClient.FetchTextUrl
  val FetchHtmlUrl: _root_.com.histmersion.web.ApiClient.FetchTextUrl.type = _root_.com.histmersion.web.ApiClient.FetchTextUrl
  type TextResult = TextResult.T
  object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextEpPart3 = TextEpPart3.T
  object TextEpPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeErrorBody = DecodeErrorBody.T
  object DecodeErrorBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchTextResultPart1 = FetchTextResultPart1.T
  object FetchTextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchTextResultPart2 = FetchTextResultPart2.T
  object FetchTextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchTextUrl = FetchTextUrl.T
  object FetchTextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val backend = FetchBackend()
  private val interp = SttpClientInterpreter()

  /** Pure: does a request of this method carry the API key? Only writes — the gate lets reads by. */
  private[web] def isWrite(m: Method): com.histmersion.web.ApiClient.IsWriteResult = m != Method.GET && m != Method.HEAD && m != Method.OPTIONS

  /** THE one place a write is given its `Authorization` header ([[Auth.currentHeader]]). */
  private def authorize[T](req: Request[T]): Request[T] =
    if isWrite(req.method) then Auth.currentHeader.fold(req)((k, v) => req.header(k, v)) else req

  /** A refused write drops the key and asks for it again, wherever it came from. */
  private def refuse(f: ApiFailure): ApiFailure =
    if f.unauthorized then Auth.rejected()
    f

  private def call[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Future[O] =
    val req = authorize(interp.toRequest(ep, None).apply(in))
    req.send(backend).flatMap { resp =>
      val code = resp.code.code
      resp.body match
        case _ if code == 401 || code == 403 => Future.failed(refuse(ApiFailure.of(code, None)))
        case DecodeResult.Value(Right(o))    => Future.successful(o)
        case DecodeResult.Value(Left(e))     => Future.failed(refuse(ApiFailure(code, e)))
        case other                           =>
          Future.failed(ApiFailure(code, Api.Error(s"could not read the server's answer (HTTP $code): $other")))
    }

  /** The URL template of a layer's tiles (`{z}`, `{x}`, `{y}` for the map to fill in): `q`'s request as the tile
    * endpoint builds it, the tile given as placeholder numbers no tile has, swapped for the template's markers.
    * Absolute: the map's workers fetch it.
    */
  def tileTemplate(q: TilesApi.TileQuery): com.histmersion.web.ApiClient.TileTemplateResult =
    val req = interp
      .toRequest(TilesApi.tile, None)
      .apply(q.modify(_.z).setTo(TilePlaceholder.Z).modify(_.x).setTo(TilePlaceholder.X).modify(_.y).setTo(TilePlaceholder.Y))
    dom.window.location.origin + TilePlaceholder.fill(req.uri.toString)

  def config(): Future[UiConfig] = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult] = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]] = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram] = call(Api.aggregate)(q)
  def facets(field: com.histmersion.web.ApiClient.FacetsField, prefix: Option[com.histmersion.web.ApiClient.FacetsPrefix], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def resolveId(id: com.histmersion.web.ApiClient.ResolveIdId): Future[ResolvedId] = call(Api.resolveId)(id)
  def getSource(ck: com.histmersion.web.ApiClient.GetSourceCk, body: com.histmersion.web.ApiClient.GetSourceBody = true): Future[Source.WithFindings] =
    call(Api.getSource)((ck, body))

  /** The articles that cite a source (`GET /api/sources/{citekey}/articles`). */
  def sourceArticles(ck: com.histmersion.web.ApiClient.SourceArticlesCk): Future[List[Backlink]] = call(Api.sourceArticles)(ck)

  /** `POST /api/acquire/ingest`: a DOI or arXiv id brought in ([[library.DoiIngest]] builds the request) — by the legal
    * open-access chain only, whatever the request says: the server may be configured to allow fetchers without the
    * rights holder's authorisation, and the web UI never sets them off.
    */
  def ingestId(req: Ingest.Request): Future[Ingest.Report] =
    call(AcquireApi.ingest)(req.modify(_.legalOnly).setTo(true))
  def neighbors(ck: com.histmersion.web.ApiClient.NeighborsCk, kinds: List[com.histmersion.web.ApiClient.NeighborsKinds] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[Article.List] = call(Api.articles)(q)
  def getArticle(slug: com.histmersion.web.ApiClient.GetArticleSlug, brief: com.histmersion.web.ApiClient.GetArticleBrief = false): Future[Article.View] = call(Api.getArticle)((slug, brief))
  def places(q: Option[com.histmersion.web.ApiClient.PlacesQ], asOf: Option[com.histmersion.web.ApiClient.PlacesAsOf], limit: Option[com.histmersion.web.ApiClient.PlacesLimit]): Future[List[Place.Hit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: com.histmersion.web.ApiClient.PlaceId, asOf: Option[com.histmersion.web.ApiClient.PlaceAsOf], counts: com.histmersion.web.ApiClient.PlaceCounts = false): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts)(true)))
  def about(): Future[About] = call(Api.about)(())
  def basemap(): Future[Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Future[JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Future[Basemap.Removed] = call(BasemapApi.remove)(q)
  def job(id: JobInfo.Id): Future[JobInfo] = call(Api.job)(id.value)
  def uiCounters(): Future[UiNotices.Counters] = call(UiApi.counters)(())
  def markSeen(board: com.histmersion.web.ApiClient.MarkSeenBoard, at: com.histmersion.web.ApiClient.MarkSeenAt): Future[UiNotices.Counters] =
    call(UiApi.markSeen)((board, UiNotices.SeenMark(Some(at))))

  def queueList(list: com.histmersion.web.ApiClient.QueueListList, all: com.histmersion.web.ApiClient.QueueListAll = false): Future[QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueForGoal(list: com.histmersion.web.ApiClient.QueueForGoalList, goal: com.histmersion.web.ApiClient.QueueForGoalGoal, all: com.histmersion.web.ApiClient.QueueForGoalAll = false): Future[QueueListing] =
    call(OpsApi.queueList)((list, all, Some(goal), None))
  def queueOfJob(list: com.histmersion.web.ApiClient.QueueOfJobList, job: com.histmersion.web.ApiClient.QueueOfJobJob): Future[QueueListing] =
    call(OpsApi.queueList)((list, true, None, Some(job)))
  def activity(
      show: Option[com.histmersion.web.ApiClient.ActivityShow],
      since: Option[com.histmersion.web.ApiClient.ActivitySince],
      goal: Option[com.histmersion.web.ApiClient.ActivityGoal],
      limit: Option[com.histmersion.web.ApiClient.ActivityLimit],
      before: Option[com.histmersion.web.ApiClient.ActivityBefore]
  ): Future[Activity.Page] = call(Api.activity)((show, since, goal, limit, before))
  def jobControl(id: com.histmersion.web.ApiClient.JobControlId, action: com.histmersion.web.ApiClient.JobControlAction): Future[JobInfo] = call(Api.jobControl)((id, action))

  // ── a person's bookmarks, notes and read marks ───────────────────────────────────────────────────

  def bookmarks(): Future[List[personal.Bookmark]] =
    call(personal.PersonalApi.bookmarks)((None, None, Some(personal.PersonalIds.MaxLimit)))
  def addBookmark(b: com.histmersion.api.personal.Bookmark.New): Future[personal.Bookmark] =
    call(personal.PersonalApi.addBookmark)(b)
  def editBookmark(id: com.histmersion.web.ApiClient.EditBookmarkId, e: com.histmersion.api.personal.Bookmark.Edit): Future[personal.Bookmark] =
    call(personal.PersonalApi.editBookmark)((id, e))
  def removeBookmark(id: com.histmersion.web.ApiClient.RemoveBookmarkId): Future[personal.Bookmark] = call(personal.PersonalApi.removeBookmark)(id)
  def notes(): Future[List[personal.Note]] =
    call(personal.PersonalApi.notes)((None, None, None, Some(personal.PersonalIds.MaxLimit)))
  def addNote(n: com.histmersion.api.personal.Note.New): Future[personal.Note] = call(personal.PersonalApi.addNote)(n)
  def editNote(id: com.histmersion.web.ApiClient.EditNoteId, e: com.histmersion.api.personal.Note.Edit): Future[personal.Note] =
    call(personal.PersonalApi.editNote)((id, e))
  def removeNote(id: com.histmersion.web.ApiClient.RemoveNoteId): Future[personal.Note] = call(personal.PersonalApi.removeNote)(id)
  def readMarks(): Future[List[personal.ReadMark]] =
    call(personal.PersonalApi.reading)((None, Some("all"), Some(personal.PersonalIds.MaxLimit)))
  def setRead(m: com.histmersion.api.personal.ReadMark.Set): Future[personal.ReadMark] =
    call(personal.PersonalApi.setRead)(m)
  def opened(o: personal.ReadOpened): Future[personal.ReadMark] = call(personal.PersonalApi.opened)(o)
  def unread(kind: com.histmersion.web.ApiClient.UnreadKind, limit: com.histmersion.web.ApiClient.UnreadLimit, offset: com.histmersion.web.ApiClient.UnreadOffset): Future[personal.UnreadPage] =
    call(personal.PersonalApi.unread)((Some(kind), Some(limit), Some(offset)))

  // ── Settings and the setup ───────────────────────────────────────────────────────────────────────

  def settings(prefix: Option[com.histmersion.web.ApiClient.SettingsPrefix] = None): Future[ConfigEdit.Page] = call(ConfigEditApi.get)(prefix)
  def writeSettings(w: ConfigEdit.Write): Future[ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer(): Future[ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backupNow(): Future[Backup.Status] = call(Api.backup)(())
  def backups(): Future[List[Backup.Info]] = call(Api.backups)(())
  def initData(fetch: com.histmersion.web.ApiClient.InitDataFetch): Future[JobInfo] = call(Api.init)(fetch)
  def placeSources(): Future[List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: com.histmersion.web.ApiClient.LoadPlacesSource): Future[JobInfo] = call(Api.loadPlaces)((source, true))
  def eventCatalogues(): Future[List[CatalogProviderInfo]] = call(EventsApi.catalogues)(())
  def importEvents(provider: com.histmersion.web.ApiClient.ImportEventsProvider): Future[JobInfo] =
    call(EventsApi.importEvents)(EventImportRequest(provider, dryRun = false))
  def mediaProviders(): Future[List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def importMedia(provider: com.histmersion.web.ApiClient.ImportMediaProvider): Future[JobInfo] =
    call(Api.mediaImportJob)(MediaImportRequest(provider, dryRun = false))
  def setAgentMode(mode: AgentMode): Future[AutopilotStatus] =
    call(AutopilotApi.setMode)(AgentMode.ModeSet(mode.wire, Some("web")))
  def acquireRecheck(in: Acquire.Checks.RecheckRequest): Future[Acquire.Checks.RecheckResult] =
    call(com.histmersion.api.library.AcquisitionWorkApi.acquireRecheck)(in)
  def queuePatch(id: com.histmersion.web.ApiClient.QueuePatchId, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueAdd(list: com.histmersion.web.ApiClient.QueueAddList, in: Queue.QueueAdd): Future[Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queueDelete(id: com.histmersion.web.ApiClient.QueueDeleteId): Future[Queue.QueueItem] =
    call(OpsApi.queueDelete)(id)

  def graph(citekey: com.histmersion.web.ApiClient.GraphCitekey, depth: Option[com.histmersion.web.ApiClient.GraphDepth] = None): Future[Source.Graph] =
    call(Api.graph)((citekey, depth))

  def periods(
      q: Option[com.histmersion.web.ApiClient.PeriodsQ] = None,
      region: Option[com.histmersion.web.ApiClient.PeriodsRegion] = None,
      from: Option[com.histmersion.web.ApiClient.PeriodsFrom] = None,
      to: Option[com.histmersion.web.ApiClient.PeriodsTo] = None
  ): Future[List[HistoricalPeriod]] =
    call(Api.periods)((q, region, from, to))

  /** The places' GeoJSON — megabytes over a whole range, read as text by the browser ([[text]]). */
  def placesGeo(q: Api.PlacesGeoQuery): Future[com.histmersion.web.ApiClient.PlacesGeoResult] = text(Api.placesGeo)(q)

  def mediaGeo(q: Api.MediaGeoQuery): Future[List[MediaGeoHit]] = call(Api.mediaGeo)(q)
  def mediaFacets(): Future[MediaFacets] = call(Api.mediaFacets)(())
  def getEvent(id: com.histmersion.web.ApiClient.GetEventId): Future[EventDetail] = call(Api.getEvent)(id)

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)
  def eventsGeo(q: Api.EventQuery): Future[EventGeo.List] = call(Api.eventsGeo)(q)

  /** The literature search for `q`, works not in the corpus yet (an event card's lead, #1449). */
  def discover(q: com.histmersion.web.ApiClient.DiscoverQ, limit: com.histmersion.web.ApiClient.DiscoverLimit): Future[DiscoverResult] =
    call(AcquireApi.discover)((q, None, None, Some(limit), true))

  /** One page of the jobs that match `f` ([[Paging]]): `before` = older than that job, `upTo` = down to it. */
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[com.histmersion.web.ApiClient.JobsLimit],
      before: Option[com.histmersion.web.ApiClient.JobsBefore] = None,
      upTo: Option[com.histmersion.web.ApiClient.JobsUpTo] = None
  ): Future[(List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, upTo))

  def patchSource(ck: com.histmersion.web.ApiClient.PatchSourceCk, p: Source.Patch): Future[Source.WithFindings] = call(Api.patchSource)((ck, p, false))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  def agentRuns(
      f: ListFilters.RunFilter,
      limit: Option[com.histmersion.web.ApiClient.AgentRunsLimit],
      before: Option[com.histmersion.web.ApiClient.AgentRunsBefore] = None,
      upTo: Option[com.histmersion.web.ApiClient.AgentRunsUpTo] = None
  ): Future[(List[Agents.RunInfo], Paging.Page)] =
    call(AgentsApi.runs)((f, limit, before, upTo))
  def startRun(req: Agents.RunRequest): Future[Agents.RunInfo] = call(AgentsApi.startRun)(req)
  def agentRun(id: com.histmersion.web.ApiClient.AgentRunId): Future[Agents.RunDetail] = call(AgentsApi.run)(id)

  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  def agentControl(id: com.histmersion.web.ApiClient.AgentControlId, action: com.histmersion.web.ApiClient.AgentControlAction, budgetUsd: Option[com.histmersion.web.ApiClient.AgentControlBudgetUsd] = None): Future[Agents.RunInfo] =
    call(AgentsApi.control)((id, action, budgetUsd, None))
  def agentModels: Future[Agents.ModelsInfo] = call(AgentsApi.models)(())
  def setChain(role: com.histmersion.web.ApiClient.SetChainRole, models: List[com.histmersion.web.ApiClient.SetChainModels]): Future[ModelChains.RoleChain] =
    call(AgentsApi.setChain)((role, ModelChains.ChainSet(models)))
  def resetChain(role: com.histmersion.web.ApiClient.ResetChainRole): Future[ModelChains.RoleChain] = call(AgentsApi.resetChain)(role)
  def setAllowance(in: ModelChains.AllowanceSet): Future[ModelChains.AllowanceStatus] = call(AgentsApi.setAllowance)(in)
  def removeAllowance(target: com.histmersion.web.ApiClient.RemoveAllowanceTarget): Future[Goal.Plans.Removed] = call(AgentsApi.removeAllowance)(target)
  def agentDoctor(offline: com.histmersion.web.ApiClient.AgentDoctorOffline): Future[Agents.DoctorReport] = call(AgentsApi.doctor)(Some(offline))
  def agentQuota: Future[PlanQuota.QuotaInfo] = call(AgentsApi.quota)(())
  def setPool(pool: com.histmersion.web.ApiClient.SetPoolPool, in: PlanQuota.PoolSet): Future[PlanQuota.PoolStatus] = call(AgentsApi.setPool)((pool, in))
  def refreshQuota(pool: com.histmersion.web.ApiClient.RefreshQuotaPool): Future[PlanQuota.QuotaRefreshed] =
    call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(Some(pool)))
  def approvals(
      f: ListFilters.ApprovalFilter,
      limit: Option[com.histmersion.web.ApiClient.ApprovalsLimit],
      before: Option[com.histmersion.web.ApiClient.ApprovalsBefore] = None,
      upTo: Option[com.histmersion.web.ApiClient.ApprovalsUpTo] = None
  ): Future[(List[Agents.ApprovalInfo], Paging.Page)] =
    call(AgentsApi.approvals)((f, limit, before, upTo))
  def decide(id: com.histmersion.web.ApiClient.DecideId, d: Agents.Decision, note: Option[com.histmersion.web.ApiClient.DecideNote]): Future[Agents.ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, Agents.DecisionRequest(note)))

  // ── goals: the list, one goal's view, its links and schedules ───────────────────────────────────

  def goals(limit: Option[com.histmersion.web.ApiClient.GoalsLimit]): Future[Goal.List] = call(Api.goals)(Api.GoalQuery(None, limit, None))
  def goalView(slug: com.histmersion.web.ApiClient.GoalViewSlug): Future[Goal.Plans.GoalView] = call(AgentsApi.goalView)(slug)
  def getGoal(slug: com.histmersion.web.ApiClient.GetGoalSlug): Future[Goal] = call(Api.getGoal)(slug)

  /** Create (or REPLACE) a goal — the New research area form checks the slug is free first. */
  def putGoal(slug: com.histmersion.web.ApiClient.PutGoalSlug, in: Goal.Put): Future[Goal] = call(Api.putGoal)((slug, in))
  def addGoalLink(slug: com.histmersion.web.ApiClient.AddGoalLinkSlug, in: Goal.Plans.LinkAdd): Future[Goal.Plans.GoalLink] =
    call(AgentsApi.addGoalLink)((slug, in))
  def removeGoalLink(slug: com.histmersion.web.ApiClient.RemoveGoalLinkSlug, kind: Goal.Plans.LinkKind, ref: com.histmersion.web.ApiClient.RemoveGoalLinkRef): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeGoalLink)((slug, kind.wire, ref))
  def setSchedule(
      slug: com.histmersion.web.ApiClient.SetScheduleSlug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet
  ): Future[Goal.Plans.GoalSchedule] =
    call(AgentsApi.setSchedule)((slug, kind.wire, in))
  def removeSchedule(slug: com.histmersion.web.ApiClient.RemoveScheduleSlug, kind: Goal.Plans.ScheduleKind): Future[Goal.Plans.Removed] =
    call(AgentsApi.removeSchedule)((slug, kind.wire))

  /** `POST /api/ingest`, with upload progress (`fetch` reports none, so this one request goes through
    * `XMLHttpRequest`). The URL and its query parameters still come from the tapir endpoint, and the key from
    * [[authorize]] — the same two places as every other call.
    */
  def ingest(params: Ingest.Params, file: dom.File, progress: com.histmersion.web.ApiClient.IngestProgressArg1 => Unit): Future[Ingest.Accepted] =
    val req = authorize(interp.toRequest(Api.ingest, None).apply((params, file)))
    val result = Promise[Ingest.Accepted]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers
      .filter(h => h.name.equalsIgnoreCase("Authorization"))
      .foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 202 || xhr.status == 200 then
        io.circe.parser
          .decode[Ingest.Accepted](body)
          .fold(
            e =>
              result.failure(ApiFailure(xhr.status, Api.Error(s"could not read the server's answer: ${e.getMessage}"))),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── merging another collection's bundle (the /merge review screen) ──────────────────────────────

  def mergePlan(req: MergePlan.Request): Future[MergePlan] = call(Api.mergePlan)(req)
  def mergeSession(bundle: com.histmersion.web.ApiClient.MergeSessionBundle): Future[MergeSession] = call(Api.mergeSession)(bundle)
  def putMergeSession(bundle: com.histmersion.web.ApiClient.PutMergeSessionBundle, r: MergeResolutions): Future[MergeSession] =
    call(Api.putMergeSession)((bundle, r))
  def parseResolutions(yaml: com.histmersion.web.ApiClient.ParseResolutionsYaml): Future[MergeResolutions] = call(Api.parseResolutions)(yaml)
  def mergeApply(req: MergeApplyRequest): Future[MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: com.histmersion.web.ApiClient.MergeApplyStatusJob): Future[MergeApplyJob] = call(Api.mergeApplyStatus)(job)

  /** `POST /api/bundle/upload`, with upload progress — a bundle can be large; through `XMLHttpRequest` for the same
    * reason as [[ingest]].
    */
  def uploadBundle(file: dom.File, progress: com.histmersion.web.ApiClient.UploadBundleProgressArg1 => Unit): Future[Bundle.Upload] =
    val req = authorize(interp.toRequest(Api.uploadBundle, None).apply(file))
    val result = Promise[Bundle.Upload]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers
      .filter(h => h.name.equalsIgnoreCase("Authorization"))
      .foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 200 then
        io.circe.parser
          .decode[Bundle.Upload](body)
          .fold(
            e =>
              result.failure(ApiFailure(xhr.status, Api.Error(s"could not read the server's answer: ${e.getMessage}"))),
            ok => result.success(ok)
          )
      else
        val decoded = io.circe.parser.decode[Api.Error](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  // ── process managers (docs/research-process-managers.md) ─────────────────────────────────────────

  def managers: Future[List[Managers.ManagerInfo]] = call(ManagersApi.list)(())
  def autopilot: Future[AutopilotStatus] = call(AutopilotApi.autopilot)(())
  def setAutopilot(on: com.histmersion.web.ApiClient.SetAutopilotOn): Future[AutopilotStatus] =
    call(AutopilotApi.setAutopilot)(Autopilot.AutopilotSet(on, Some("web")))

  /** The autopilot's focus: pin/unpin/priority a goal, or the cap (`max`; no value: the config's). */
  def setFocus(action: com.histmersion.web.ApiClient.SetFocusAction, goal: Option[com.histmersion.web.ApiClient.SetFocusGoal] = None, value: Option[com.histmersion.web.ApiClient.SetFocusValue] = None): Future[Autopilot.FocusInfo] =
    call(ManagersApi.setFocus)(Autopilot.FocusSet(action, goal, value, Some("web")))

  /** The central scheduler: its status and queue (`limit` waiting items), a live strategy switch, one item's why. */
  def scheduler(limit: Option[com.histmersion.web.ApiClient.SchedulerLimit] = None): Future[AgentScheduler.SchedulerStatus] = call(ManagersApi.scheduler)(limit)
  def setScheduler(strategy: com.histmersion.web.ApiClient.SetSchedulerStrategy): Future[AgentScheduler.SchedulerStatus] =
    call(ManagersApi.setScheduler)(AgentScheduler.SchedulerSet(strategy, Some("web")))
  def schedulerWhy(item: com.histmersion.web.ApiClient.SchedulerWhyItem): Future[AgentScheduler.WhyInfo] = call(ManagersApi.schedulerWhy)(item)

  /** Drain (nothing new starts while the runs in flight finish) or lift it. */
  def drain(on: com.histmersion.web.ApiClient.DrainOn): Future[AutopilotStatus] =
    call(AutopilotApi.drain)(Autopilot.DrainSet(on, Some("web")))

  /** The goals with no progress in `days` days, why, and what unblocks them. */
  def stalls(days: Option[com.histmersion.web.ApiClient.StallsDays] = None): Future[Stalls.StallReport] = call(ManagersApi.stalls)(days)

  /** Everything waiting for a person, by kind. */
  def approvalDigest: Future[ApprovalDigest.Digest] = call(ManagersApi.digest)(())
  def manager(id: com.histmersion.web.ApiClient.ManagerId): Future[Managers.ManagerDetail] = call(ManagersApi.get)(id)
  def managerUpdate(id: com.histmersion.web.ApiClient.ManagerUpdateId, in: Managers.ManagerUpdate): Future[Managers.ManagerInfo] =
    call(ManagersApi.update)((id, in))

  /** pause | resume | stop; `runs` = a pause takes its runs in flight with it. */
  def managerAction(id: com.histmersion.web.ApiClient.ManagerActionId, action: com.histmersion.web.ApiClient.ManagerActionAction, runs: com.histmersion.web.ApiClient.ManagerActionRuns = false): Future[Managers.ManagerInfo] =
    call(ManagersApi.action)((id, action, Option.when(runs)(true)))
  def managerItems(id: com.histmersion.web.ApiClient.ManagerItemsId, status: Option[com.histmersion.web.ApiClient.ManagerItemsStatus], offset: com.histmersion.web.ApiClient.ManagerItemsOffset, limit: com.histmersion.web.ApiClient.ManagerItemsLimit): Future[Managers.ItemPage] =
    call(ManagersApi.items)((id, status, Some(offset), Some(limit)))
  def managerPatchItem(id: com.histmersion.web.ApiClient.ManagerPatchItemId, item: com.histmersion.web.ApiClient.ManagerPatchItemItem, p: Managers.ItemPatch): Future[Managers.ManagerItem] =
    call(ManagersApi.patchItem)((id, item, p))
  def managerAddHint(id: com.histmersion.web.ApiClient.ManagerAddHintId, in: Managers.HintAdd): Future[Managers.ManagerHint] =
    call(ManagersApi.addHint)((id, in))
  def managerRemoveHint(id: com.histmersion.web.ApiClient.ManagerRemoveHintId, hint: com.histmersion.web.ApiClient.ManagerRemoveHintHint): Future[Goal.Plans.Removed] = call(ManagersApi.removeHint)((id, hint))
  def managerEvents(id: com.histmersion.web.ApiClient.ManagerEventsId, before: Option[com.histmersion.web.ApiClient.ManagerEventsBefore], limit: com.histmersion.web.ApiClient.ManagerEventsLimit): Future[Managers.EventPage] =
    call(ManagersApi.events)((id, before, Some(limit)))

  /** A page button — Review, Extend, Enrich (an article or a goal), Extend a goal (`seed`): the target goes to the
    * manager of its kind, first in line.
    */
  def enqueue(kind: Managers.EnqueueKind, target: com.histmersion.web.ApiClient.EnqueueTarget): Future[Managers.EnqueueResult] =
    call(ManagersApi.enqueue)(Managers.EnqueueRequest(kind.wire, target, Some(10)))

  /** The article window's Review button: the article goes to the review manager, first in line. */
  def enqueueReview(slug: com.histmersion.web.ApiClient.EnqueueReviewSlug): Future[Managers.EnqueueResult] = enqueue(Managers.EnqueueKind.Review, slug)

  /** A new manager (the New research area form's seeding manager); created paused. */
  def managerCreate(in: Managers.ManagerCreate): Future[Managers.ManagerInfo] = call(ManagersApi.create)(in)

  /** A page's HTML fragment, whatever the status (an error page is shown as it is). */
  def fetchHtml(url: com.histmersion.web.ApiClient.FetchHtmlUrl): Future[com.histmersion.web.ApiClient.FetchHtmlResult] = fetchText(url).map(_._2)

  /** A text answer of `ep` read by the BROWSER (#1949): sttp's fetch backend reads a body as bytes and decodes them in
    * Scala.js, a byte at a time — tens of ms per MB, on the page's one thread, for a whole range's 7 MB of boundaries.
    * `Response.text()` decodes natively. The request is still the endpoint's own (its path and query, from the same
    * Tapir definition); a GET, so no key.
    */
  private def text[I](ep: PublicEndpoint[I, Api.Error, com.histmersion.web.ApiClient.TextEpPart3, Any])(in: I): Future[com.histmersion.web.ApiClient.TextResult] =
    fetchText(interp.toRequest(ep, None).apply(in).uri.toString).flatMap { (status, body) =>
      if status >= 200 && status < 300 then Future.successful(body)
      else Future.failed(ApiFailure.of(status, decodeError(body)))
    }

  private def decodeError(body: com.histmersion.web.ApiClient.DecodeErrorBody): Option[Api.Error] =
    io.circe.parser.decode[Api.Error](body).toOption

  /** GET `url`: the status and the body, decoded by the browser. */
  private def fetchText(url: com.histmersion.web.ApiClient.FetchTextUrl): Future[(com.histmersion.web.ApiClient.FetchTextResultPart1, com.histmersion.web.ApiClient.FetchTextResultPart2)] =
    import scala.scalajs.js.Thenable.Implicits.*
    for
      r <- dom.fetch(url).toFuture
      body <- r.text().toFuture
    yield (r.status, body)
