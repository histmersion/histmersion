package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Plural, Wording}
import com.histmersion.api.automation.Managers.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.{
  Agents,
  AgentScheduler,
  AgentSchedulerMarkdown,
  Autopilot,
  ManagersMarkdown,
  Stalls
}
import com.histmersion.api.views.AutopilotStatus
import com.histmersion.api.automation.Goal

/** The process managers (docs/research-process-managers.md §5): the managers window (each one's state — with what the
  * supervisor derived: starved, at capacity, idle — its items, runs in flight and last event) and one window per
  * manager: pause (with or without its runs) / resume / stop, the items in flight with their runs' progress, the items
  * with hold / release / priority / skip / re-open, the hint box, the live hints, the events and the settings. A
  * manager window polls while it has runs in flight (the runs window's schedule); every change is behind the write
  * gate. Also the article window's Review button.
  */
object ManagersView:

  /** Items per page, events per page. */
  type ConfigGK = ConfigGK.T
  object ConfigGK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigOnOffResultPart1 = ConfigOnOffResultPart1.T
  object ConfigOnOffResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigOnOffResultPart2 = ConfigOnOffResultPart2.T
  object ConfigOnOffResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigOnOffK = ConfigOnOffK.T
  object ConfigOnOffK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigOnOffWhat = ConfigOnOffWhat.T
  object ConfigOnOffWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusPanelGoWhat = FocusPanelGoWhat.T
  object FocusPanelGoWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusPanelGoalLinkSlug = FocusPanelGoalLinkSlug.T
  object FocusPanelGoalLinkSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AutopilotSectionDrainOn = AutopilotSectionDrainOn.T
  object AutopilotSectionDrainOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AutopilotSectionFlipOn = AutopilotSectionFlipOn.T
  object AutopilotSectionFlipOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerSectionSwitchName = SchedulerSectionSwitchName.T
  object SchedulerSectionSwitchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerSectionOpenRunsManager = SchedulerSectionOpenRunsManager.T
  object SchedulerSectionOpenRunsManager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneTimer = ManagerPaneTimer.T
  object ManagerPaneTimer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneDelay = ManagerPaneDelay.T
  object ManagerPaneDelay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneMounted = ManagerPaneMounted.T
  object ManagerPaneMounted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneExtended = ManagerPaneExtended.T
  object ManagerPaneExtended extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneRefreshChanged = ManagerPaneRefreshChanged.T
  object ManagerPaneRefreshChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneWriteWhat = ManagerPaneWriteWhat.T
  object ManagerPaneWriteWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type BadgeResult = BadgeResult.T
  object BadgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerMeaningResult = ManagerMeaningResult.T
  object ManagerMeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerMeaningId = ManagerMeaningId.T
  object ManagerMeaningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemStatusMeaningResult = ItemStatusMeaningResult.T
  object ItemStatusMeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemActionsForResult = ItemActionsForResult.T
  object ItemActionsForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemActionsResult = ItemActionsResult.T
  object ItemActionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemActionsStatus = ItemActionsStatus.T
  object ItemActionsStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntResult = IntResult.T
  object IntResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntS = IntS.T
  object IntS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListS = ListS.T
  object ListS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListSep = ListSep.T
  object ListSep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigResultPart1 = ConfigResultPart1.T
  object ConfigResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigFPart1 = ConfigFPart1.T
  object ConfigFPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigFPart2 = ConfigFPart2.T
  object ConfigFPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldsResultPart1 = FieldsResultPart1.T
  object FieldsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldsResultPart2 = FieldsResultPart2.T
  object FieldsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsPart1 = SettingsPart1.T
  object SettingsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsPart2 = SettingsPart2.T
  object SettingsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsForResultPart1 = SettingsForResultPart1.T
  object SettingsForResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsForResultPart2 = SettingsForResultPart2.T
  object SettingsForResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsForKind = SettingsForKind.T
  object SettingsForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintResultPart1 = HintResultPart1.T
  object HintResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintText = HintText.T
  object HintText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintItem = HintItem.T
  object HintItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintOnce = HintOnce.T
  object HintOnce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintAction = HintAction.T
  object HintAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintTargets = HintTargets.T
  object HintTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintTag = HintTag.T
  object HintTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintUntil = HintUntil.T
  object HintUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintUntilSource = HintUntilSource.T
  object HintUntilSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintSuggest = HintSuggest.T
  object HintSuggest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueuedNoteResult = EnqueuedNoteResult.T
  object EnqueuedNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueueButtonsTarget = EnqueueButtonsTarget.T
  object EnqueueButtonsTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueueButtonsButtonsPart3 = EnqueueButtonsButtonsPart3.T
  object EnqueueButtonsButtonsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnqueueButtonsButtonsPart4 = EnqueueButtonsButtonsPart4.T
  object EnqueueButtonsButtonsPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleActionsSlug = ArticleActionsSlug.T
  object ArticleActionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalActionsSlug = GoalActionsSlug.T
  object GoalActionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLineResult = GoalLineResult.T
  object GoalLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusPaceResult = FocusPaceResult.T
  object FocusPaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitingSummaryResult = WaitingSummaryResult.T
  object WaitingSummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusHeadResult = FocusHeadResult.T
  object FocusHeadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusPanelFailure = FocusPanelFailure.T
  object FocusPanelFailure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainLineResult = DrainLineResult.T
  object DrainLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagesResult = StagesResult.T
  object StagesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VisibleWaitingTypes = VisibleWaitingTypes.T
  object VisibleWaitingTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VisibleWaitingCodes = VisibleWaitingCodes.T
  object VisibleWaitingCodes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VisibleWaitingSubject = VisibleWaitingSubject.T
  object VisibleWaitingSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobTypeMeaningResult = JobTypeMeaningResult.T
  object JobTypeMeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobTypeMeaningJobType = JobTypeMeaningJobType.T
  object JobTypeMeaningJobType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitMeaningResult = WaitMeaningResult.T
  object WaitMeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitMeaningCode = WaitMeaningCode.T
  object WaitMeaningCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetResultPart1 = FacetResultPart1.T
  object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetResultPart2 = FacetResultPart2.T
  object FacetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOfResult = FacetOfResult.T
  object FacetOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyLabelResult = StrategyLabelResult.T
  object StrategyLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyLabelName = StrategyLabelName.T
  object StrategyLabelName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobTypeLabelResult = JobTypeLabelResult.T
  object JobTypeLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobTypeLabelT = JobTypeLabelT.T
  object JobTypeLabelT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitLabelResult = WaitLabelResult.T
  object WaitLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitLabelCode = WaitLabelCode.T
  object WaitLabelCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerHeadResult = SchedulerHeadResult.T
  object SchedulerHeadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsResult = CountsResult.T
  object CountsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerPaneId = ManagerPaneId.T
  object ManagerPaneId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadBusy = HeadBusy.T
  object HeadBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadWriteArg1 = HeadWriteArg1.T
  object HeadWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemRowMid = ItemRowMid.T
  object ItemRowMid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemRowBusy = ItemRowBusy.T
  object ItemRowBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemRowWriteArg1 = ItemRowWriteArg1.T
  object ItemRowWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintBoxId = HintBoxId.T
  object HintBoxId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintBoxBusy = HintBoxBusy.T
  object HintBoxBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintBoxWriteArg1 = HintBoxWriteArg1.T
  object HintBoxWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsBusy = SettingsBusy.T
  object SettingsBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsWriteArg1 = SettingsWriteArg1.T
  object SettingsWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ItemLimit = 25
  val EventLimit = 30

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The badge of a manager: its person-set state, or — active — what the supervisor derived. */
  def badge(m: ManagerInfo): com.histmersion.web.ManagersView.BadgeResult =
    val note = m.statusNote.getOrElse("")
    if m.state != State.Active.wire then m.state
    else if note.startsWith("starved") then "starved"
    else if note.startsWith("budget") then "budget"
    else if note.startsWith("at capacity") then "at capacity"
    else if note == "idle" then "idle"
    else if note.startsWith("focus:") then "queued"
    else "active"

  /** What a manager does, from its id (`review`, `enrich`, `seed:<goal>`, `watch:<goal>`); a tooltip on the runs
    * window's manager menu.
    */
  def managerMeaning(id: com.histmersion.web.ManagersView.ManagerMeaningId): Option[com.histmersion.web.ManagersView.ManagerMeaningResult] =
    val goal = id.dropWhile(_ != ':').drop(1)
    Kind.parse(id.takeWhile(_ != ':')).map {
      case Kind.Review => "The review manager: reviews the library's articles until their reviews converge."
      case Kind.Enrich => "The enrich manager: enriches the library's sources until each one is enriched."
      case Kind.Seed   =>
        s"Seeds the research area $goal: plans it, proposes its new articles and, once approved, compiles them."
      case Kind.Watch => s"Watches the research area $goal: an acquisition sweep and a currency check on its schedule."
    }

  /** What each status of a manager's item means; a tooltip on the items' filter buttons. */
  def itemStatusMeaning(s: ItemStatus): com.histmersion.web.ManagersView.ItemStatusMeaningResult = s match
    case ItemStatus.Pending => "Waiting to start; the scheduler picks the order."
    case ItemStatus.Held    => "Held: it does not start until released, its approval is decided, or its hold ends."
    case ItemStatus.Active  => "A run is working on it now."
    case ItemStatus.Done    => "Settled: its runs got it where the manager wanted it."
    case ItemStatus.Stopped =>
      "Settled without being done: skipped, or the manager gave up on it. Re-open brings it back."
    case ItemStatus.Failed    => "Settled because its run failed. Re-open brings it back."
    case ItemStatus.Cancelled => "Settled because the work is moot: its target is gone, or its sweep was removed."

  /** What a person can do to this item: by its status — but an item the autopilot's focus holds is not released by hand
    * (pin its goal instead).
    */
  def itemActionsFor(i: ManagerItem): List[com.histmersion.web.ManagersView.ItemActionsForResult] =
    if i.hold.exists(_.focus.isDefined) then itemActions(i.status).filterNot(_ == "release") else itemActions(i.status)

  /** What a person can do to an item in this status. */
  def itemActions(status: com.histmersion.web.ManagersView.ItemActionsStatus): List[com.histmersion.web.ManagersView.ItemActionsResult] = ItemStatus.parse(status) match
    case Some(ItemStatus.Pending) => List("hold", "priority", "skip")
    case Some(ItemStatus.Held)    => List("release", "priority", "skip")
    case Some(ItemStatus.Active)  => List("priority", "skip")
    case Some(s) if s.isFinal     => List("reopen")
    case _                        => Nil

  /** A whole number the person typed, or nothing. */
  def int(s: com.histmersion.web.ManagersView.IntS): Option[com.histmersion.web.ManagersView.IntResult] = s.trim.toIntOption.filter(_ >= 0)

  private def list(s: com.histmersion.web.ManagersView.ListS, sep: com.histmersion.web.ManagersView.ListSep): Option[List[com.histmersion.web.ManagersView.ListResult]] =
    Option(s.split(sep).toList.map(_.trim).filter(_.nonEmpty)).filter(_.nonEmpty)

  /** The settings form → a config (blank fields are the server's defaults). Left = what does not parse. */
  def config(f: Map[com.histmersion.web.ManagersView.ConfigFPart1, com.histmersion.web.ManagersView.ConfigFPart2]): Either[com.histmersion.web.ManagersView.ConfigResultPart1, ManagerConfig] =
    def g(k: com.histmersion.web.ManagersView.ConfigGK) = f.getOrElse(k, "").trim
    val bad = List(
      "maxRuns",
      "maxOpen",
      "rounds",
      "maxRunsPerArticle",
      "noProgressRuns",
      "reopenLimit",
      "cooldownMinutes",
      "lowWater",
      "refillBatch",
      "maxFailures",
      "staleAfterDays",
      "minSources",
      "maxArticles",
      "autoIngestPerDay",
      "extendMinNew"
    )
      .filter(k => g(k).nonEmpty && int(g(k)).isEmpty)
    def onOff(k: com.histmersion.web.ManagersView.ConfigOnOffK, what: com.histmersion.web.ManagersView.ConfigOnOffWhat): Either[com.histmersion.web.ManagersView.ConfigOnOffResultPart1, Option[com.histmersion.web.ManagersView.ConfigOnOffResultPart2]] = g(k).toLowerCase match
      case ""                     => Right(None)
      case "true" | "on" | "yes"  => Right(Some(true))
      case "false" | "off" | "no" => Right(Some(false))
      case other                  => Left(s"$what: '$other' is not on or off")
    val sweeps = list(g("sweeps"), ",").map(ws => ws.map(w => Sweep.parse(w).toRight(w)))
    val infer = for
      i <- onOff("inferFilter", "infer filter")
      ai <- onOff("autoIngest", "ingest legal open access")
      aa <- onOff("autoApprove", "decide its approvals by code checks")
      _ <- sweeps.toList.flatten
        .collectFirst { case Left(w) => w }
        .fold[Either[String, Unit]](Right(()))(w => Left(s"sweeps: '$w' is not acquisition or currency"))
    yield (i, ai, aa)
    val usd = g("budgetUsd")
    val daily = g("dailyUsd")
    val models = list(g("models"), ";").getOrElse(Nil).map(Agents.ModelOverride.parse)
    if bad.nonEmpty then Left(s"not a whole number: ${bad.mkString(", ")}")
    else if usd.nonEmpty && usd.toDoubleOption.forall(_ <= 0) then Left("budget USD is not a positive number")
    else if daily.nonEmpty && daily.toDoubleOption.forall(_ < 0) then Left("daily cap USD is not a number (0 = no cap)")
    else
      infer.flatMap((inf, ai, aa) =>
        models.collectFirst { case Left(e) => e } match
          case Some(e) => Left(s"models: $e")
          case None    =>
            Right(
              ManagerConfig(
                int(g("maxRuns")),
                int(g("maxOpen")),
                int(g("rounds")),
                int(g("maxRunsPerArticle")),
                int(g("noProgressRuns")),
                int(g("reopenLimit")),
                int(g("cooldownMinutes")),
                int(g("lowWater")),
                int(g("refillBatch")),
                int(g("maxFailures")),
                usd.toDoubleOption,
                list(g("includeTags"), ","),
                Option(g("includeGoal")).filter(_.nonEmpty),
                Option(models.collect { case Right(m) => m }).filter(_.nonEmpty),
                int(g("staleAfterDays")),
                inf,
                int(g("minSources")),
                int(g("maxArticles")),
                sweeps.map(_.collect { case Right(s) => s.wire }.distinct),
                ai,
                int(g("autoIngestPerDay")),
                daily.toDoubleOption,
                aa,
                int(g("extendMinNew"))
              )
            )
      )

  /** A config as the form shows it (blank = the default). */
  def fields(c: ManagerConfig): Map[com.histmersion.web.ManagersView.FieldsResultPart1, com.histmersion.web.ManagersView.FieldsResultPart2] = Map(
    "maxRuns" -> c.maxRuns,
    "maxOpen" -> c.maxOpen,
    "rounds" -> c.rounds,
    "maxRunsPerArticle" -> c.maxRunsPerArticle,
    "noProgressRuns" -> c.noProgressRuns,
    "reopenLimit" -> c.reopenLimit,
    "cooldownMinutes" -> c.cooldownMinutes,
    "lowWater" -> c.lowWater,
    "refillBatch" -> c.refillBatch,
    "maxFailures" -> c.maxFailures,
    "staleAfterDays" -> c.staleAfterDays,
    "minSources" -> c.minSources,
    "maxArticles" -> c.maxArticles,
    "autoIngestPerDay" -> c.autoIngestPerDay,
    "extendMinNew" -> c.extendMinNew
  ).view.mapValues(_.fold("")(_.toString)).toMap ++ Map(
    "inferFilter" -> c.inferFilter.fold("")(b => if b then "on" else "off"),
    "autoApprove" -> c.autoApprove.fold("")(b => if b then "on" else "off"),
    "autoIngest" -> c.autoIngest.fold("")(b => if b then "on" else "off"),
    "sweeps" -> c.sweeps.fold("")(_.mkString(", ")),
    "budgetUsd" -> c.budgetUsd.fold("")(_.toString),
    "dailyUsd" -> c.dailyUsd.fold("")(_.toString),
    "includeTags" -> c.includeTags.getOrElse(Nil).mkString(", "),
    "includeGoal" -> c.includeGoal.getOrElse(""),
    "models" -> c.models.getOrElse(Nil).map(_.text).mkString("; ")
  )

  /** The settings the form offers, with their labels. */
  val Settings: List[(com.histmersion.web.ManagersView.SettingsPart1, com.histmersion.web.ManagersView.SettingsPart2)] = List(
    "maxRuns" -> "runs at once",
    "maxOpen" -> "open runs (incl. paused)",
    "rounds" -> "rounds per run",
    "maxRunsPerArticle" -> "runs per article",
    "noProgressRuns" -> "no-progress window (runs)",
    "reopenLimit" -> "reopen limit",
    "cooldownMinutes" -> "cooldown (minutes)",
    "lowWater" -> "refill below",
    "refillBatch" -> "refill batch",
    "maxFailures" -> "failures before stop",
    "budgetUsd" -> "budget per run (USD)",
    "dailyUsd" -> "daily cap (USD of metered spend; plan-billed runs count 0; 0 = none)",
    "includeTags" -> "only articles tagged (comma-separated)",
    "includeGoal" -> "only a goal's articles",
    "models" -> "models (role=provider:model; …)",
    "staleAfterDays" -> "review again after (days; 0 never)",
    "inferFilter" -> "infer a missing filter first (on/off)",
    "minSources" -> "seed: sources per article at least",
    "maxArticles" -> "seed: articles per plan at most",
    "sweeps" -> "watch: sweeps (acquisition, currency)",
    "autoIngest" -> "watch: ingest legal open-access finds (on/off; legal open access only)",
    "autoIngestPerDay" -> "watch: ingests a day at most",
    "autoApprove" -> "decide its runs' plans and drafts by code checks (on/off; blank = agents.approvals)",
    "extendMinNew" -> "extend an article at this many new sources (0 never)"
  )

  /** What a manager's settings form offers: every setting, but a watcher's only to a watcher (and the review settings
    * not to one).
    */
  def settingsFor(kind: com.histmersion.web.ManagersView.SettingsForKind): List[(com.histmersion.web.ManagersView.SettingsForResultPart1, com.histmersion.web.ManagersView.SettingsForResultPart2)] =
    val watchOnly = Set("sweeps", "autoIngest", "autoIngestPerDay")
    val reviewOnly = Set(
      "rounds",
      "maxRunsPerArticle",
      "noProgressRuns",
      "reopenLimit",
      "lowWater",
      "refillBatch",
      "includeTags",
      "includeGoal",
      "staleAfterDays",
      "inferFilter",
      "extendMinNew"
    )
    if kind == Kind.Watch.wire then Settings.filterNot((k, _) => reviewOnly(k))
    else Settings.filterNot((k, _) => watchOnly(k))

  /** A watcher's sweeps as its events tell them: each settled sweep and each work it ingested, newest first. */
  def sweepEvents(es: List[ManagerEvent]): List[ManagerEvent] =
    es.filter(e => e.kind == Feed.Swept || e.kind == Feed.Ingested)

  /** The hint box → a hint. `action` none/prioritise/hold/skip; `targets` comma-separated slugs; `suggest`
    * = the server's model proposes the action (none chosen here; a person approves what it proposes).
    */
  def hint(
      text: com.histmersion.web.ManagersView.HintText,
      item: com.histmersion.web.ManagersView.HintItem,
      once: com.histmersion.web.ManagersView.HintOnce,
      action: com.histmersion.web.ManagersView.HintAction,
      targets: com.histmersion.web.ManagersView.HintTargets,
      tag: com.histmersion.web.ManagersView.HintTag,
      until: com.histmersion.web.ManagersView.HintUntil,
      untilSource: com.histmersion.web.ManagersView.HintUntilSource,
      suggest: com.histmersion.web.ManagersView.HintSuggest = false
  ): Either[com.histmersion.web.ManagersView.HintResultPart1, HintAdd] =
    val t = text.trim
    val a = action.trim.toLowerCase
    if t.isEmpty then Left("a hint needs text")
    else if suggest && a.nonEmpty && a != "none" then Left("choose an action, or let the model suggest one — not both")
    else if suggest then Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once), suggest = Some(true)))
    else if a.isEmpty || a == "none" then Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once)))
    else if !HintEffect.Actions.contains(a) then Left(s"unknown action $a")
    else
      val e = HintEffect(
        a,
        list(targets, ",").getOrElse(Nil),
        Option(tag.trim).filter(_.nonEmpty),
        None,
        None,
        Option(until.trim).filter(_.nonEmpty),
        Option(untilSource.trim).filter(_.nonEmpty)
      )
      if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then
        Left("hold: give a date or a source")
      else if e.targets.isEmpty && e.tag.isEmpty && item.trim.isEmpty then Left(s"$a: name the articles or a tag")
      else Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once), Some(e)))

  // ── the page buttons: Review, Extend, Enrich ────────────────────────────────────────────────────

  /** What an enqueue did, in one line: the items queued (and how many were skipped as enriched). */
  def enqueuedNote(kind: EnqueueKind, r: EnqueueResult): com.histmersion.web.ManagersView.EnqueuedNoteResult =
    val what = kind match
      case EnqueueKind.Review => "queued for review"
      case EnqueueKind.Extend => "queued to be extended, then reviewed"
      case EnqueueKind.Enrich => "queued for enrichment"
      case EnqueueKind.Seed   => "queued: its seeding manager proposes the next articles"
    val skipped = if r.skipped.isEmpty then "" else s"; ${Plural.count(r.skipped.size, "source")} enriched already"
    r.items match
      case Nil if kind == EnqueueKind.Enrich => s"nothing to enrich$skipped"
      case Nil                               => "nothing queued"
      case List(i)                           => s"$what (item #${i.id}, ${i.status})$skipped"
      case is                                => s"${Plural.count(is.size, "item")} $what$skipped"

  /** Buttons that hand a target to a manager (only where the server runs agents), with the line saying what was queued
    * and a link to the manager.
    */
  def enqueueButtons(target: com.histmersion.web.ManagersView.EnqueueButtonsTarget, buttons: List[(EnqueueKind, Icon, com.histmersion.web.ManagersView.EnqueueButtonsButtonsPart3, com.histmersion.web.ManagersView.EnqueueButtonsButtonsPart4)]): HtmlElement =
    val note = Var(Option.empty[(String, String)])
    def go(kind: EnqueueKind): Unit =
      ApiClient
        .enqueue(kind, target)
        .onComplete(r =>
          note.set(Some(r.fold(e => (s"not queued: ${e.getMessage}", ""), x => (enqueuedNote(kind, x), x.managerId))))
        )
    div(
      cls := "wm-actions",
      child.maybe <-- RunsView.enabled.signal.map(on =>
        Option.when(on)(
          div(
            cls := "btn-group",
            buttons.map((kind, icon, label, tip) => WriteButton(icon, label, tip, () => go(kind)))
          )
        )
      ),
      child.maybe <-- note.signal.map(
        _.map((n, id) =>
          span(cls := "muted wm-note", n, Option.when(id.nonEmpty)(WinLink(WindowKey.Manager(id), " open the manager")))
        )
      )
    )

  /** The article window: Review (the review manager, first in line), Extend (compile the new sources of its filter into
    * it, then review it) and Enrich (the sources it cites that are not enriched yet).
    */
  def articleActions(slug: com.histmersion.web.ManagersView.ArticleActionsSlug): HtmlElement =
    enqueueButtons(
      slug,
      List(
        (EnqueueKind.Review, Icon.Managers, "review", "queue this article for the review manager (first in line)"),
        (
          EnqueueKind.Extend,
          Icon.Edit,
          "extend",
          "compile the new sources of its filter into this article, then review it"
        ),
        (EnqueueKind.Enrich, Icon.Runs, "enrich", "enrich the sources this article cites that are not enriched yet")
      )
    )

  /** The goal window: Extend (its seeding manager proposes its next articles — a person approves the plan) and Enrich
    * (the sources linked to it that are not enriched yet).
    */
  def goalActions(slug: com.histmersion.web.ManagersView.GoalActionsSlug): HtmlElement =
    enqueueButtons(
      slug,
      List(
        (
          EnqueueKind.Seed,
          Icon.Edit,
          "extend",
          "propose the next articles of this research area (you approve the plan)"
        ),
        (EnqueueKind.Enrich, Icon.Runs, "enrich", "enrich the sources linked to this goal that are not enriched yet")
      )
    )

  // ── the autopilot ───────────────────────────────────────────────────────────────────────────────

  /** One goal's line in the autopilot's table: its counts and its pace. */
  def goalLine(g: Autopilot.GoalProgress): com.histmersion.web.ManagersView.GoalLineResult =
    s"${Plural.count(g.sources, "source")} · ${Plural.count(g.articles, "article")}" +
      (if g.openApprovals > 0 then s" · ${Plural.count(g.openApprovals, "open approval")}" else "") +
      s" · ${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)")

  /** An active or queued goal's pace, in a line. */
  def focusPace(g: Autopilot.FocusGoal): com.histmersion.web.ManagersView.FocusPaceResult =
    s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)")

  /** The goals that cannot use a focus slot now, by why, the commonest first. */
  def waitingSummary(ws: List[Autopilot.FocusWaiting]): com.histmersion.web.ManagersView.WaitingSummaryResult =
    ws.groupBy(_.code).toList.sortBy((c, xs) => (-xs.size, c)).map((c, xs) => s"${xs.size} $c").mkString(" · ")

  /** The focus's heading: the cap and where it comes from, or why it is not in force. */
  def focusHead(f: Autopilot.FocusInfo): com.histmersion.web.ManagersView.FocusHeadResult =
    (if f.on then s"Focus: at most ${f.max} active goals" else "Focus: not in force") +
      (if f.maxFrom == "person" then " (cap set by a person)" else "") +
      (if f.held > 0 then s" · ${Plural.count(f.held, "item")} held" else "")

  /** The focus: the active goals (why each was chosen, what it does or waits for, its pace), the queue in the order the
    * next become active, and why the others cannot use a slot; pin / unpin a goal and set the cap (each behind the
    * write gate).
    */
  private def focusPanel(
      f: Autopilot.FocusInfo,
      changed: Autopilot.FocusInfo => Unit,
      failure: Var[Option[com.histmersion.web.ManagersView.FocusPanelFailure]]
  ): HtmlElement =
    val cap = Var(f.max.toString)
    def go(what: com.histmersion.web.ManagersView.FocusPanelGoWhat)(call: => scala.concurrent.Future[Autopilot.FocusInfo]): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        call.onComplete(
          _.fold(e => failure.set(Some(s"focus $what: ${e.getMessage}")), x => { failure.set(None); changed(x) })
        )
    def pinButton(g: Autopilot.FocusGoal) =
      val (verb, tip) = if g.pinned then ("unpin", "Let the focus choose again")
      else ("pin", "Keep this goal active (it takes a slot now)")
      button(
        typ := "button",
        cls := "btn btn--sm",
        Wording.capitalize(verb),
        title := tip,
        onClick --> (_ => go(s"$verb ${g.slug}")(ApiClient.setFocus(verb, Some(g.slug))))
      )
    def goalLink(slug: com.histmersion.web.ManagersView.FocusPanelGoalLinkSlug) = WinLink(WindowKey.Goal(slug), slug)
    div(
      cls := "autopilot-focus",
      div(
        cls := "toolbar",
        div(cls := "btn-group", span(cls := "autopilot-state", focusHead(f))),
        div(
          cls := "btn-group",
          input(
            typ := "number",
            cls := "field manager-prio",
            aria.label := "the cap of active goals",
            minAttr := "0",
            controlled(value <-- cap.signal, onInput.mapToValue --> cap.writer)
          ),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Set cap",
            title := "At most this many goals active for model work (0 = no cap)",
            onClick --> (_ => int(cap.now()).foreach(n => go("cap")(ApiClient.setFocus("max", value = Some(n)))))
          ),
          Option.when(f.maxFrom == "person")(
            button(
              typ := "button",
              cls := "btn btn--sm",
              "default",
              title := "back to agents.autopilot.max-active-goals",
              onClick --> (_ => go("cap")(ApiClient.setFocus("max")))
            )
          )
        )
      ),
      f.note.map(n => div(cls := "muted", n)),
      Option.when(f.changes.nonEmpty)(div(cls := "form-ok", f.changes.mkString("; ").take(400))),
      ul(
        cls := "job-list focus-active",
        f.active.map(g =>
          li(
            cls := "job-row",
            GoalsView.badge(g.status, g.stage),
            div(
              cls := "job-main",
              div(
                cls := "job-subject",
                goalLink(g.slug),
                Option.when(g.pinned)(span(cls := "muted", " · pinned")),
                span(cls := "muted", s" · ${g.title}")
              ),
              div(cls := "job-progress", g.why),
              g.doing.map(d => div(cls := "muted", s"now: $d")),
              div(cls := "job-meta muted", focusPace(g)),
              child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g))))
            )
          )
        )
      ),
      Option.when(f.queue.nonEmpty)(
        div(
          div(cls := "muted focus-heading", s"next (${f.queued} waiting for a slot):"),
          ol(
            cls := "job-list focus-queue",
            f.queue.map(g =>
              li(
                cls := "job-row",
                GoalsView.badge(g.status, g.stage),
                div(
                  cls := "job-main",
                  div(cls := "job-subject", goalLink(g.slug)),
                  div(cls := "muted", g.why),
                  child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g))))
                )
              )
            )
          )
        )
      ),
      Option.when(f.waiting.nonEmpty)(
        div(cls := "muted focus-heading", s"cannot use a slot now: ${waitingSummary(f.waiting)}")
      )
    )

  /** The drain in a line: draining (runs still finishing) or drained (safe to stop the server). */
  def drainLine(d: Autopilot.DrainInfo): com.histmersion.web.ManagersView.DrainLineResult =
    if d.drained then
      s"Paused since ${d.drainedAt.getOrElse(d.since)}: nothing runs, nothing new starts — safe to stop the server"
    else
      s"Finishing current work, then pausing (since ${d.since}${
          if d.by.nonEmpty then s", by ${d.by}" else ""
        }): nothing new starts; " +
        s"${Plural.count(d.inFlight, "run")} in flight still finishing"

  /** The goals by stage, in the order a goal moves through them: how many in each. */
  def stages(gs: List[Autopilot.GoalProgress]): com.histmersion.web.ManagersView.StagesResult =
    Goal.Status.all.map(st => st -> gs.count(_.status == st)).filter(_._2 > 0).map((st, n) => s"$n $st").mkString(" · ")

  /** The switch and the status: on/off (the click shows the new state at once; the server's answer follows), the
    * approval rules, today's ingests, and every goal's stage, counts and pace.
    */
  def autopilotSection: HtmlElement =
    val st = Var(Option.empty[AutopilotStatus])
    val pending = Var(Option.empty[Boolean])
    val failure = Var(Option.empty[String])
    def load(): Unit = ApiClient.autopilot.onComplete(
      _.fold(e => failure.set(Some(e.getMessage)), s => { failure.set(None); st.set(Some(s)) })
    )
    def drain(on: com.histmersion.web.ManagersView.AutopilotSectionDrainOn): Unit =
      ApiClient
        .drain(on)
        .onComplete(
          _.fold(e => failure.set(Some(s"drain: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
        )
    def flip(on: com.histmersion.web.ManagersView.AutopilotSectionFlipOn): Unit =
      pending.set(Some(on))
      ApiClient.setAutopilot(on).onComplete { r =>
        pending.set(None)
        r.fold(e => failure.set(Some(s"autopilot: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
      }
    val shownOn = st.signal.combineWith(pending.signal).map((s, p) => p.orElse(s.map(_.on)).getOrElse(false))
    div(
      cls := "autopilot",
      onMountCallback(_ => load()),
      div(
        cls := "toolbar",
        div(
          cls := "btn-group",
          span(
            cls := "autopilot-state",
            cls("is-on") <-- shownOn,
            child.text <-- shownOn.combineWith(st.signal.map(_.flatMap(_.drain).isDefined)).map(Wording.agentsState)
          ),
          child <-- shownOn.map(on =>
            if on then
              WriteButton(
                Icon.Managers,
                "Pause",
                "Pause the seeding managers and watchers the autopilot runs",
                () => flip(false)
              )
            else
              WriteButton(
                Icon.Managers,
                "Start working",
                "every research goal gets an active seeding manager and watcher and moves from planned to reviewed by itself",
                () => flip(true)
              )
          )
        ),
        div(
          cls := "btn-group",
          child <-- st.signal.map(_.flatMap(_.drain)).map {
            case Some(_) =>
              WriteButton(
                Icon.Managers,
                Wording.Undrain,
                "The scheduler admits work again (undrain)",
                () => drain(false)
              )
            case None =>
              WriteButton(
                Icon.Managers,
                Wording.Drain,
                "Before a deploy: nothing new starts while the runs in flight finish; no manager is paused (drain)",
                () => drain(true)
              )
          }
        )
      ),
      child.maybe <-- st.signal.map(_.flatMap(_.drain).map(d => div(cls := "form-ok", drainLine(d)))),
      ErrorLine(failure.signal),
      child.maybe <-- st.signal.map(_.map { s =>
        div(
          cls := "autopilot-status",
          div(
            cls := "muted",
            s.since.fold("")(t => s"since $t") + s.by.fold("")(b => s" by $b") +
              s" · ${stages(s.goals)} · ingests today ${s.ingestedToday}${
                  if s.ingestCap > 0 then s" of ${s.ingestCap}" else ""
                }" +
              s" · at most ${s.maxConcurrentRuns} runs at once"
          ),
          Option.when(s.backlog > 0)(div(cls := "muted", ManagersMarkdown.backlog(s.backlog, s.backlogDays))),
          div(
            cls := "muted",
            "Approvals: " + s.approvals
              .map(r => s"${ApprovalsView.kindLabel(r.kind).toLowerCase}: ${r.mode}")
              .mkString(" · ")
          ),
          Option.when(!s.cronsEnabled)(p(cls := "form-error", role := "alert", Autopilot.EngineStatus.CronsOff)),
          Option.when(s.changes.nonEmpty)(div(cls := "form-ok", s.changes.mkString("; ").take(400))),
          s.focus.map(f => focusPanel(f, x => st.update(_.map(_.modify(_.focus).setTo(Some(x)))), failure)),
          ul(
            cls := "job-list autopilot-goals",
            s.goals.map(g =>
              li(
                cls := "job-row",
                GoalsView.badge(g.status),
                div(
                  cls := "job-main",
                  div(
                    cls := "job-subject",
                    WinLink(
                      WindowKey.Goal(g.slug),
                      title := g.slug,
                      Option(g.title).filter(_.trim.nonEmpty).getOrElse(Wording.humanize(g.slug))
                    )
                  ),
                  div(cls := "job-meta muted", goalLine(g)),
                  Option.when(g.managers.nonEmpty)(
                    div(
                      cls := "muted",
                      g.managers
                        .map(m => s"${Wording.manager(m.id).takeWhile(_ != '·').trim} ${m.state}")
                        .mkString(" · ")
                    )
                  )
                )
              )
            )
          )
        )
      })
    )

  // ── the central scheduler ───────────────────────────────────────────────────────────────────────

  /** The waiting items a person's filters leave: any of the job types, any of the reasons, the target text. */
  def visibleWaiting(
      ws: List[AgentScheduler.Waiting],
      types: List[com.histmersion.web.ManagersView.VisibleWaitingTypes],
      codes: List[com.histmersion.web.ManagersView.VisibleWaitingCodes],
      subject: Option[com.histmersion.web.ManagersView.VisibleWaitingSubject]
  ): List[AgentScheduler.Waiting] =
    ws.filter(w =>
      (types.isEmpty || types.contains(w.jobType)) && (codes.isEmpty || codes.contains(w.code)) &&
        subject.forall(t =>
          (w.target + " " + w.goal.getOrElse("") + " " + w.manager).toLowerCase.contains(t.trim.toLowerCase)
        )
    )

  /** What each job type of the scheduler's queue is; a tooltip on its filter chip. */
  def jobTypeMeaning(jobType: com.histmersion.web.ManagersView.JobTypeMeaningJobType): Option[com.histmersion.web.ManagersView.JobTypeMeaningResult] = jobType match
    case "goal-plan" => Some("Planning a research goal: its sub-goals, search queries and works (plan-goal runs).")
    case "propose"   => Some("Proposing the articles a research area should have (propose-articles runs).")
    case "sweep"     =>
      Some("Acquisition sweeps: discovering new works for a goal and ranking them (acquisition-sweep runs).")
    case "currency" =>
      Some("Currency checks: retractions and newer critiques of a goal's sources (currency-check runs).")
    case "enrich"  => Some("Enriching a source: summary, findings, dates, places, status (enrich-source runs).")
    case "compile" => Some("Compiling a new article from the corpus (compile-article runs).")
    case "extend"  => Some("Appending a section to an article from its new sources (compile-article runs).")
    case "filter"  => Some("Inferring an article's coverage filter (infer-filter runs).")
    case "review"  => Some("Reviewing articles and revising them (review-article runs).")
    case _         => None

  /** Why an item waits, by its code (the queue's, and an item's own); a tooltip on the "why" chips. */
  def waitMeaning(code: com.histmersion.web.ManagersView.WaitMeaningCode): Option[com.histmersion.web.ManagersView.WaitMeaningResult] = code match
    case "capacity" =>
      Some("No place free: the most runs at once are running. It starts when one finishes, in the order shown.")
    case "pool"        => Some("The subscription plan its models draw on already carries as many runs as it takes.")
    case "manager-cap" => Some("Its manager already has as many runs in flight as its own runs-at-once setting allows.")
    case "blocked"     =>
      Some("Cannot start yet; its row says why (a spending cap, a model's allowance, or work that must come first).")
    case "busy" =>
      Some("Could not start in the last pass (another run is writing its target, say); the next pass tries again.")
    case "budget" => Some("Nothing starts: today's or this month's agent spending cap is reached.")
    case "drain"  => Some("Nothing new starts: draining, so the runs in flight finish first, until a person lifts it.")
    case AgentScheduler.Code.Person  => Some("Waits for a person to decide an approval; it holds no place.")
    case AgentScheduler.Code.Held    => Some("Held by a person; it holds no place.")
    case AgentScheduler.Code.NotDue  => Some("Not due yet: a cooldown, or a failed run's backoff.")
    case AgentScheduler.Code.Running => Some("Started: its run is in flight.")
    case AgentScheduler.Code.Settled => Some("Settled: done, stopped, failed or cancelled.")
    case AgentScheduler.Code.Paused  => Some("Its manager or its run is paused.")
    case _                           => None

  /** A facet of the queue as the filter chips count it: each value with its items, the most first. */
  def facet(ws: List[AgentScheduler.Waiting], of: AgentScheduler.Waiting => com.histmersion.web.ManagersView.FacetOfResult): List[(com.histmersion.web.ManagersView.FacetResultPart1, com.histmersion.web.ManagersView.FacetResultPart2)] =
    ws.groupMapReduce(of)(_ => 1)(_ + _).toList.sortBy((v, n) => (-n, v))

  def strategyLabel(name: com.histmersion.web.ManagersView.StrategyLabelName): com.histmersion.web.ManagersView.StrategyLabelResult = Wording.strategies.getOrElse(name, Wording.humanize(name))
  def jobTypeLabel(t: com.histmersion.web.ManagersView.JobTypeLabelT): com.histmersion.web.ManagersView.JobTypeLabelResult = Wording.schedulerJobTypes.getOrElse(t, Wording.humanize(t))
  def waitLabel(code: com.histmersion.web.ManagersView.WaitLabelCode): com.histmersion.web.ManagersView.WaitLabelResult = AgentScheduler.waitCodes.getOrElse(code, Wording.humanize(code))

  /** The scheduler's heading: the strategy (and who chose it), the runs at once, the pools, what waits for a person. */
  def schedulerHead(s: AgentScheduler.SchedulerStatus): com.histmersion.web.ManagersView.SchedulerHeadResult =
    s"Scheduler: ${strategyLabel(s.strategy)}" + (if s.strategyFrom == "person" then " (set by a person)" else "") +
      s" · ${s.occupied} of ${s.maxRuns} runs at once" +
      (if s.pools.isEmpty then ""
       else " · " + s.pools.map(p => s"${Wording.pool(p.pool)} ${p.used}/${p.cap}").mkString(", ")) +
      (if s.forPerson > 0 then s" · ${Plural.count(s.forPerson, "item")} wait for a person (no place held)" else "")

  /** The central scheduler: the strategy switch (behind the write gate), the resources, what the last pass started
    * (each opening its manager's runs in the runs window), and the queue — filtered by job type, reason and text like
    * the runs, jobs and approvals windows — with why each item waits and behind what.
    */
  def schedulerSection: HtmlElement =
    val st = Var(Option.empty[AgentScheduler.SchedulerStatus])
    val failure = Var(Option.empty[String])
    def load(): Unit = ApiClient
      .scheduler(Some(300))
      .onComplete(_.fold(e => failure.set(Some(e.getMessage)), s => { failure.set(None); st.set(Some(s)) }))
    def switch(name: com.histmersion.web.ManagersView.SchedulerSectionSwitchName): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        ApiClient
          .setScheduler(name)
          .onComplete(
            _.fold(e => failure.set(Some(s"scheduler: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
          )
    val filters = List(UiState.schedTypes, UiState.schedCodes, UiState.schedSubject)
    val stalls = Var(Option.empty[Stalls.StallReport])
    val waiting = st.signal.map(_.fold(List.empty[AgentScheduler.Waiting])(_.waiting))
    def openRuns(manager: com.histmersion.web.ManagersView.SchedulerSectionOpenRunsManager): Unit =
      UiState.set(UiState.runManager := Some(manager), UiState.runStatus := None)()
      WindowManager.open(WindowKey.Runs)
    div(
      cls := "scheduler",
      onMountCallback(_ => load()),
      div(
        cls := "toolbar",
        div(
          cls := "btn-group",
          span(cls := "autopilot-state", child.text <-- st.signal.map(_.fold("Scheduler")(schedulerHead)))
        ),
        div(
          cls := "btn-group",
          role := "group",
          aria.label := "strategy",
          children <-- st.signal.map(
            _.toList.flatMap(s =>
              s.strategies.map(x =>
                button(
                  typ := "button",
                  cls := "btn btn--toggle btn--sm",
                  strategyLabel(x.name),
                  title := s"${x.describe} (${x.name})",
                  aria.pressed := (x.name == s.strategy).toString,
                  onClick --> (_ => switch(x.name))
                )
              )
            )
          )
        ),
        div(
          cls := "btn-group",
          button(typ := "button", cls := "btn btn--sm", "Refresh", onClick --> (_ => load())),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Stalls",
            title := "The goals with no progress, why, and what unblocks them",
            onClick --> (_ =>
              ApiClient
                .stalls()
                .onComplete(_.fold(e => failure.set(Some(s"stalls: ${e.getMessage}")), r => stalls.set(Some(r))))
            )
          ),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Approvals",
            title := "What waits for a person (the approvals window)",
            onClick --> (_ => { UiState.set(UiState.approvalState := None)(); WindowManager.open(WindowKey.Approvals) })
          )
        )
      ),
      ErrorLine(failure.signal),
      child.maybe <-- stalls.signal.map(_.map { r =>
        div(
          cls := "scheduler-stalls",
          div(
            cls := "muted",
            s"${r.stalled.size} of ${r.goals} unfinished goals with no progress in ${r.days} day(s)" +
              (if r.byReason.isEmpty then "" else ": " + r.byReason.map(x => s"${x.code} ${x.count}").mkString(" · "))
          ),
          ul(
            cls := "job-list",
            r.stalled
              .take(100)
              .map(x =>
                li(
                  cls := "job-row",
                  span(cls := s"job-state state-${x.code}", title := x.code, Wording.humanize(x.code)),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      WinLink(WindowKey.Goal(x.slug), title := x.slug, Wording.humanize(x.slug)),
                      span(cls := "muted", x.idleDays.fold(" · never progressed")(d => s" · idle $d d"))
                    ),
                    div(cls := "job-progress", x.why),
                    div(cls := "muted", s"unblock: ${x.action}")
                  )
                )
              )
          )
        )
      }),
      child.maybe <-- st.signal.map(_.map { s =>
        div(
          s.stop.map(x => p(cls := "form-error", role := "alert", s"nothing starts: $x")),
          div(
            cls := "muted",
            (if s.dry then "what a pass would do now (nothing started) · "
             else s.at.fold("")(t => s"last pass $t · ")) +
              s.types
                .filter(t => t.waiting > 0 || t.recent > 0)
                .map(t => s"${t.jobType} ${t.recent} recent, ${t.waiting} waiting")
                .mkString(" · ")
          ),
          Option.when(s.started.nonEmpty)(
            ul(
              cls := "job-list scheduler-started",
              s.started.map(x =>
                li(
                  cls := "job-row",
                  span(cls := "job-state state-running", title := x.jobType, jobTypeLabel(x.jobType)),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      button(
                        typ := "button",
                        cls := "link-btn",
                        x.target,
                        title := "its manager's runs",
                        onClick --> (_ => openRuns(x.manager))
                      ),
                      span(cls := "muted", title := x.manager, s" · ${Wording.manager(x.manager)}")
                    ),
                    div(cls := "muted", x.why)
                  )
                )
              )
            )
          )
        )
      }),
      div(
        cls := "list-filters",
        ListFilterBar.chips(
          "type",
          waiting.map(facet(_, _.jobType)),
          UiState.schedTypes,
          show = jobTypeLabel,
          hint = jobTypeMeaning,
          about = "Show only waiting items of the job types picked (any of them): what kind of work they are."
        ),
        ListFilterBar.chips(
          "why",
          waiting.map(facet(_, _.code)),
          UiState.schedCodes,
          show = waitLabel,
          hint = waitMeaning,
          about = "Show only waiting items held back for the reasons picked (any of them)."
        ),
        div(
          cls := "toolbar",
          ListFilterBar.text("target, goal or manager", UiState.schedSubject),
          ListFilterBar.clearButton(filters)
        )
      ),
      child <-- waiting
        .combineWith(UiState.schedTypes.signal, UiState.schedCodes.signal, UiState.schedSubject.signal)
        .map { (ws, ty, co, su) =>
          val shown = visibleWaiting(ws, ty, co, su)
          if ws.isEmpty then p(cls := "muted", "Nothing waits.")
          else
            ol(
              cls := "job-list scheduler-queue",
              shown.map(w =>
                li(
                  cls := "job-row",
                  span(cls := s"job-state state-${w.code}", title := w.code, waitLabel(w.code)),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      WinLink(WindowKey.Manager(w.manager), w.target),
                      span(
                        cls := "muted",
                        s" · ${jobTypeLabel(w.jobType)} · ${Wording.manager(w.manager)}" + w.goal
                          .fold("")(g => s" · goal ${Wording.humanize(g)}") +
                          (if w.position > 0 then s" · #${w.position} in line" else "")
                      )
                    ),
                    div(cls := "job-progress", w.why),
                    Option.when(w.behind.nonEmpty)(div(cls := "muted", s"behind ${w.behind.mkString(", ")}")),
                    div(
                      cls := "job-meta muted",
                      s"priority ${w.priority} · waited ${AgentSchedulerMarkdown.minutes(w.waitedMinutes)}"
                    )
                  )
                )
              )
            )
        }
    )

  // ── the managers window ─────────────────────────────────────────────────────────────────────────

  private def stateBadge(m: ManagerInfo): HtmlElement =
    val b = badge(m)
    span(
      cls := s"job-state state-${b.replace(' ', '-')}",
      title := m.statusNote.getOrElse(m.state),
      Wording.capitalize(b)
    )

  def pane: HtmlElement =
    val ms = Var(List.empty[ManagerInfo])
    val loaded = Var(false)
    val failure = Var(Option.empty[String])
    def refresh(): Unit =
      ApiClient.managers.onComplete(
        _.fold(e => failure.set(Some(e.getMessage)), xs => { failure.set(None); ms.set(xs); loaded.set(true) })
      )
    div(
      cls := "tool-pane managers-pane",
      onMountCallback(_ => refresh()),
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "Refresh", onClick --> (_ => refresh()))),
      child.maybe <-- RunsView.enabled.signal.map(on =>
        Option.when(!on)(
          p(cls := "muted", "Embedded agents are off on this server (agents.enabled): no manager starts a run.")
        )
      ),
      autopilotSection,
      schedulerSection,
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- ms.signal.combineWith(loaded.signal).map { (xs, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if xs.isEmpty then
          p(cls := "muted", "No managers yet. The review manager is created (paused) when the server's agents start.")
        else
          ul(
            cls := "job-list manager-list",
            xs.map(m =>
              li(
                cls := "job-row manager-row",
                stateBadge(m),
                div(
                  cls := "job-main",
                  div(cls := "job-subject", WinLink(WindowKey.Manager(m.id), title := m.id, Wording.manager(m.id))),
                  m.statusNote.filter(_.nonEmpty).map(n => div(cls := "job-progress", n)),
                  div(
                    cls := "job-meta muted",
                    counts(m.counts),
                    s" · ${Plural.count(m.inFlight, "run")} in flight",
                    s" · ${ManagersMarkdown.today(m)}"
                  ),
                  Option.when(m.backlog > 0)(div(cls := "muted", ManagersMarkdown.backlog(m.backlog, m.backlogDays))),
                  m.lastEvent.map(e => div(cls := "muted", s"${e.at} · ${e.kind}: ${e.text.take(160)}"))
                )
              )
            )
          )
      }
    )

  private def counts(c: ItemCounts): com.histmersion.web.ManagersView.CountsResult =
    List(
      "pending" -> c.pending,
      "held" -> c.held,
      "active" -> c.active,
      "done" -> c.done,
      "stopped" -> c.stopped,
      "failed" -> c.failed
    ).filter(_._2 > 0).map((k, n) => s"$n $k").mkString(", ") match
      case "" => "no items"
      case s  => s

  // ── one manager ─────────────────────────────────────────────────────────────────────────────────

  def managerPane(id: com.histmersion.web.ManagersView.ManagerPaneId): HtmlElement =
    val detail = Var(Option.empty[ManagerDetail])
    val page = Var(Option.empty[ItemPage])
    val offset = Var(0)
    val status = Var(Option.empty[String])
    val events = Var(List.empty[ManagerEvent])
    val older = Var(Option.empty[Long])
    val loadingOlder = Var(false)
    val failure = Var(Option.empty[String])
    val message = Var(Option.empty[String])
    val busy = Var(false)
    // `var`s local to this window: its pending poll, the delay, whether it is mounted, whether older
    // events were loaded (a poll then keeps them)
    var timer: com.histmersion.web.ManagersView.ManagerPaneTimer = 0
    var delay: Option[com.histmersion.web.ManagersView.ManagerPaneDelay] = None
    var mounted: com.histmersion.web.ManagersView.ManagerPaneMounted = false
    var extended: com.histmersion.web.ManagersView.ManagerPaneExtended = false

    def items(): Unit =
      ApiClient.managerItems(id, status.now(), offset.now(), ItemLimit).foreach(p => page.set(Some(p)))

    def refresh(changed: com.histmersion.web.ManagersView.ManagerPaneRefreshChanged): Unit =
      dom.window.clearTimeout(timer)
      ApiClient.manager(id).onComplete { r =>
        if mounted then
          r.fold(
            e => failure.set(Some(e.getMessage)),
            d => {
              val moved = changed || !detail.now().contains(d)
              failure.set(None)
              detail.set(Some(d))
              if extended then events.update(ListPaging.keepOlder(_, d.events, _.id < _.id))
              else
                events.set(d.events)
                older.set(Option.when(d.events.nonEmpty)(d.events.last.id))
              delay = Poll.next(delay, d.manager.inFlight > 0 || d.current.nonEmpty, moved)
              delay.foreach(ms => timer = dom.window.setTimeout(() => refresh(false), ms))
            }
          )
      }
      items()

    def write[A](what: com.histmersion.web.ManagersView.ManagerPaneWriteWhat)(f: => scala.concurrent.Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.fold(
          e => failure.set(Some(s"$what: ${e.getMessage}")),
          _ => { failure.set(None); message.set(Some(s"$what: done")) }
        )
        refresh(changed = true)
      }

    def showOlder(): Unit =
      older.now().foreach { b =>
        loadingOlder.set(true)
        ApiClient.managerEvents(id, Some(b), EventLimit).onComplete { r =>
          loadingOlder.set(false)
          r.foreach { p =>
            val shown = events.now().map(_.id).toSet
            events.update(_ ++ p.events.filterNot(e => shown(e.id)))
            older.set(p.before)
            extended = true
          }
        }
      }

    div(
      cls := "tool-pane manager-pane",
      onMountCallback { _ => mounted = true; extended = false; refresh(changed = true) },
      onUnmountCallback { _ => mounted = false; dom.window.clearTimeout(timer) },
      offset.signal.changes --> (_ => items()),
      status.signal.changes --> { _ => offset.set(0); items() },
      ErrorLine(failure.signal),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- detail.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(d) => head(d, busy.signal, write)
      },
      div(
        cls := "goal-section",
        h4("In flight"),
        child <-- detail.signal.map(_.toList.flatMap(_.current)).map { cur =>
          if cur.isEmpty then p(cls := "muted", "Nothing in flight.")
          else ul(cls := "job-list", cur.map(i => itemRow(id, i, busy.signal, write)))
        }
      ),
      child.maybe <-- detail.signal.map(
        _.filter(_.manager.kind == Kind.Watch.wire).map(d =>
          div(
            cls := "goal-section manager-sweeps",
            h4("Sweeps"),
            p(
              cls := "muted",
              d.manager.cron.fold("No cron: its sweeps run only when re-opened by hand.")(c =>
                s"Cron `$c` (UTC)" + d.manager.nextTickAt.fold("")(t => s", next $t") +
                  (if d.manager.state == State.Active.wire then "" else " — fires once it is resumed") + ". " +
                  (if d.manager.effective.autoIngest.contains(true)
                   then
                     s"Legal open-access picks are ingested, at most ${d.manager.effective.autoIngestPerDay.getOrElse(0)} a day (legal open access only)."
                   else "Its picks go to the acquisition list (auto-ingest off).")
              )
            ),
            child <-- events.signal
              .map(sweepEvents)
              .map(es =>
                if es.isEmpty then p(cls := "muted", "No sweep has settled yet.")
                else
                  ul(
                    cls := "manager-events",
                    es.map(e => li(span(cls := "muted", s"${e.at} · "), strong(e.kind), s" ${e.text}"))
                  )
              )
          )
        )
      ),
      div(
        cls := "goal-section",
        h4("Items"),
        div(
          cls := "toolbar",
          div(
            cls := "btn-group",
            (None :: ItemStatus.values.toList.map(s => Some(s.wire))).map(s =>
              button(
                typ := "button",
                cls := "btn btn--toggle btn--sm",
                aria.pressed <-- status.signal.map(v => (v == s).toString),
                title := s.flatMap(ItemStatus.parse).fold("Every item, whatever its status.")(itemStatusMeaning),
                s.getOrElse("all"),
                onClick --> (_ => status.set(s))
              )
            )
          )
        ),
        child <-- page.signal.map {
          case None                         => p(cls := "muted", "Loading…")
          case Some(pg) if pg.items.isEmpty => p(cls := "muted", "No items.")
          case Some(pg) => ul(cls := "job-list manager-items", pg.items.map(i => itemRow(id, i, busy.signal, write)))
        },
        Pager("items", offset, ItemLimit, page.signal.map(_.fold(0)(_.items.size)), page.signal.map(_.fold(0)(_.total)))
      ),
      hintBox(id, busy.signal, write),
      div(
        cls := "goal-section",
        h4("Hints"),
        child <-- detail.signal.map(_.toList.flatMap(_.hints)).map { hs =>
          if hs.isEmpty then p(cls := "muted", "No live hints.")
          else
            ul(
              cls := "manager-hints",
              hs.map(h =>
                li(
                  span(ManagersMarkdown.hint(h)),
                  child.maybe <-- Auth.writable.map(w =>
                    Option.when(w)(
                      button(
                        typ := "button",
                        cls := "link-btn",
                        " retire",
                        disabled <-- busy,
                        onClick --> (_ => write("retire hint")(ApiClient.managerRemoveHint(id, h.id)))
                      )
                    )
                  )
                )
              )
            )
        }
      ),
      div(
        cls := "goal-section",
        h4("Events"),
        child <-- events.signal.map(es =>
          if es.isEmpty then p(cls := "muted", "No events yet.")
          else
            ul(
              cls := "manager-events",
              es.map(e =>
                li(
                  span(cls := "muted", s"${e.at} · "),
                  strong(e.kind),
                  s" ${e.text}",
                  e.runId.map(r => span(" ", WinLink(WindowKey.Run(r), s"run ${r.take(8)}")))
                )
              )
            )
        ),
        // newest first with a `before` cursor: the list-paging branch's "Show older"
        ListPaging.olderButton(older.signal.map(_.isDefined), loadingOlder.signal, () => showOlder())
      ),
      child <-- detail.signal.map(_.map(_.manager)).map {
        case None    => emptyNode
        case Some(m) => settings(m, busy.signal, write)
      }
    )

  private def head(
      d: ManagerDetail,
      busy: Signal[com.histmersion.web.ManagersView.HeadBusy],
      write: com.histmersion.web.ManagersView.HeadWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val m = d.manager
    div(
      cls := "goal-detail",
      div(cls := "run-head", stateBadge(m), span(cls := "run-title", title := m.id, s" ${Wording.manager(m.id)}")),
      m.statusNote.filter(_.nonEmpty).map(n => p(cls := "job-progress", n)),
      Option.when(m.state == State.Paused.wire && m.inFlight == 0)(
        p(cls := "muted", "Paused: it starts nothing (nothing is spent) until you resume it.")
      ),
      dl(
        cls := "run-facts",
        dt("items"),
        dd(counts(m.counts)),
        m.cron.toList.flatMap(c => List(dt("cron"), dd(s"$c (UTC)" + m.nextTickAt.fold("")(t => s", next $t")))),
        dt("in flight"),
        dd(Plural.count(m.inFlight, "run")),
        Option
          .when(m.backlog > 0)(List(dt("ingest backlog"), dd(ManagersMarkdown.backlog(m.backlog, m.backlogDays))))
          .toList
          .flatten,
        dt("today"),
        dd(
          span(ManagersMarkdown.today(m)),
          span(
            cls := "muted",
            " — the daily cap counts metered spend; a plan-billed run (Claude or ChatGPT plan) costs 0 USD, its tokens are shown"
          )
        ),
        dt("settings"),
        dd(ManagersHandlersText.describe(m.effective))
      ),
      div(
        cls := "form-row",
        child <-- Auth.writable.map { w =>
          if !w then Auth.keyNeeded("steer this manager")
          else
            val st = State.parse(m.state)
            div(
              cls := "toolbar",
              div(
                cls := "btn-group",
                Option
                  .when(st.contains(State.Active))(
                    List(
                      button(
                        typ := "button",
                        cls := "btn",
                        "pause",
                        disabled <-- busy,
                        title := "start no new runs; its runs in flight go on",
                        onClick --> (_ => write("pause")(ApiClient.managerAction(m.id, "pause")))
                      ),
                      button(
                        typ := "button",
                        cls := "btn",
                        "pause + runs",
                        disabled <-- busy,
                        title := "also pause its runs in flight",
                        onClick --> (_ =>
                          write("pause with its runs")(ApiClient.managerAction(m.id, "pause", runs = true))
                        )
                      )
                    )
                  )
                  .toList
                  .flatten,
                Option.when(!st.contains(State.Active))(
                  button(
                    typ := "button",
                    cls := "btn btn--primary",
                    "resume",
                    disabled <-- busy,
                    onClick --> (_ => write("resume")(ApiClient.managerAction(m.id, "resume")))
                  )
                ),
                Option.when(!st.contains(State.Stopped))(
                  button(
                    typ := "button",
                    cls := "btn btn--danger",
                    "stop",
                    disabled <-- busy,
                    title := "cancel its runs in flight and its open items",
                    onClick --> (_ =>
                      if dom.window.confirm(s"Stop ${m.id}: cancel its runs in flight and its open items?") then
                        write("stop")(ApiClient.managerAction(m.id, "stop"))
                    )
                  )
                )
              )
            )
        }
      )
    )

  private def itemRow(
      mid: com.histmersion.web.ManagersView.ItemRowMid,
      i: ManagerItem,
      busy: Signal[com.histmersion.web.ManagersView.ItemRowBusy],
      write: com.histmersion.web.ManagersView.ItemRowWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val prio = Var(i.priority.toString)
    val until = Var("")
    li(
      cls := s"job-row manager-item item-${i.status}",
      span(cls := s"job-state state-${i.status}", i.status),
      div(
        cls := "job-main",
        div(
          cls := "job-subject",
          com.histmersion.web.ArticleLink(i.target, i.target),
          span(
            cls := "muted",
            s" · priority ${i.priority} · ${Plural.count(i.attempts, "run")}" +
              (if i.failures > 0 then s", ${i.failures} failed" else "")
          )
        ),
        i.run.map(r =>
          div(
            cls := "job-progress",
            WinLink(WindowKey.Run(r.id), s"run ${r.id.take(8)} ${r.status}"),
            r.progress.filter(_.nonEmpty).map(p => span(cls := "muted", s" · $p"))
          )
        ),
        i.hold.map(h =>
          div(
            cls := "muted",
            "held" + h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") +
              h.focus.fold("")(g => s" for the focus (goal $g; pin it in the managers window to run it now)") +
              Option(h.reason).filter(_.nonEmpty).fold("")(r => s" — $r")
          )
        ),
        i.notBefore.map(t => div(cls := "muted", s"not before $t")),
        i.outcome.filter(_.nonEmpty).map(o => div(cls := "muted", o)),
        child.maybe <-- Auth.writable.map(w =>
          Option.when(w && itemActionsFor(i).nonEmpty)(
            div(
              cls := "form-row manager-item-actions",
              itemActionsFor(i).map {
                case "hold" =>
                  span(
                    input(
                      typ := "text",
                      cls := "field",
                      aria.label := "hold until",
                      placeholder := "until 2026-11-01 or a DOI",
                      controlled(value <-- until.signal, onInput.mapToValue --> until.writer)
                    ),
                    button(
                      typ := "button",
                      cls := "btn btn--sm",
                      "hold",
                      disabled <-- busy,
                      onClick --> { _ =>
                        val u = until.now().trim
                        val patch = if u.isEmpty then ItemPatch("hold")
                        else if ManagerPolicyText.isDate(u) then ItemPatch("hold", until = Some(u))
                        else ItemPatch("hold", untilSource = Some(u))
                        write(s"hold ${i.target}")(ApiClient.managerPatchItem(mid, i.id.toString, patch))
                      }
                    )
                  )
                case "priority" =>
                  span(
                    input(
                      typ := "number",
                      cls := "field manager-prio",
                      aria.label := "priority",
                      controlled(value <-- prio.signal, onInput.mapToValue --> prio.writer)
                    ),
                    button(
                      typ := "button",
                      cls := "btn btn--sm",
                      "set priority",
                      disabled <-- busy,
                      onClick --> (_ =>
                        int(prio.now())
                          .orElse(prio.now().trim.toIntOption)
                          .foreach(p =>
                            write(s"priority of ${i.target}")(
                              ApiClient.managerPatchItem(mid, i.id.toString, ItemPatch("priority", priority = Some(p)))
                            )
                          )
                      )
                    )
                  )
                case a =>
                  button(
                    typ := "button",
                    cls := (if a == "skip" then "btn btn--sm btn--danger" else "btn btn--sm"),
                    a,
                    disabled <-- busy,
                    onClick --> (_ =>
                      write(s"$a ${i.target}")(ApiClient.managerPatchItem(mid, i.id.toString, ItemPatch(a)))
                    )
                  )
              }
            )
          )
        )
      )
    )

  private def hintBox(
      id: com.histmersion.web.ManagersView.HintBoxId,
      busy: Signal[com.histmersion.web.ManagersView.HintBoxBusy],
      write: com.histmersion.web.ManagersView.HintBoxWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val text = Var("")
    val item = Var("")
    val once = Var(false)
    val action = Var("none")
    val targets = Var("")
    val tag = Var("")
    val until = Var("")
    val source = Var("")
    val suggest = Var(false)
    val error = Var(Option.empty[String])
    div(
      cls := "goal-section manager-hint-box",
      h4("Hint"),
      p(
        cls := "muted",
        "A note the fixer and the skeptic of its next runs read (subordinate to the record). " +
          "Optionally, an action applied now: put articles first, hold them, or skip them — or let the server's model " +
          "read the note and suggest the action (it waits under Approvals until you approve it)."
      ),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("give this manager a hint")
        else
          div(
            textArea(
              cls := "field manager-hint-text",
              aria.label := "hint",
              placeholder := "e.g. prefer the 2019 dating; the skeptic's Kristiansen critique is covered in [[x]]",
              controlled(value <-- text.signal, onInput.mapToValue --> text.writer)
            ),
            div(
              cls := "form-row",
              input(
                typ := "text",
                cls := "field",
                aria.label := "only about this article",
                placeholder := "only about (article slug, optional)",
                controlled(value <-- item.signal, onInput.mapToValue --> item.writer)
              ),
              label(
                input(typ := "checkbox", controlled(checked <-- once.signal, onClick.mapToChecked --> once.writer)),
                " next run only"
              ),
              child.maybe <-- RunsView.enabled.signal.map(on =>
                Option.when(on)(
                  label(
                    title := "the server's planner model proposes the action the note asks for; nothing changes until you approve it",
                    input(
                      typ := "checkbox",
                      controlled(checked <-- suggest.signal, onClick.mapToChecked --> suggest.writer)
                    ),
                    " suggest an action"
                  )
                )
              ),
              select(
                cls := "field",
                aria.label := "action",
                List("none", HintEffect.Prioritise, HintEffect.HoldAction, HintEffect.Skip).map(a =>
                  option(value := a, a)
                ),
                controlled(value <-- action.signal, onChange.mapToValue --> action.writer)
              )
            ),
            child.maybe <-- action.signal.map(a =>
              Option.when(a != "none")(
                div(
                  cls := "form-row",
                  input(
                    typ := "text",
                    cls := "field",
                    aria.label := "articles",
                    placeholder := "articles (slugs, comma-separated)",
                    controlled(value <-- targets.signal, onInput.mapToValue --> targets.writer)
                  ),
                  input(
                    typ := "text",
                    cls := "field",
                    aria.label := "or a tag",
                    placeholder := "or a tag",
                    controlled(value <-- tag.signal, onInput.mapToValue --> tag.writer)
                  ),
                  Option
                    .when(a == HintEffect.HoldAction)(
                      List(
                        input(
                          typ := "text",
                          cls := "field",
                          aria.label := "until a date",
                          placeholder := "until 2026-11-01",
                          controlled(value <-- until.signal, onInput.mapToValue --> until.writer)
                        ),
                        input(
                          typ := "text",
                          cls := "field",
                          aria.label := "until a source exists",
                          placeholder := "or until a DOI/citekey exists",
                          controlled(value <-- source.signal, onInput.mapToValue --> source.writer)
                        )
                      )
                    )
                    .toList
                    .flatten
                )
              )
            ),
            ErrorLine(error.signal),
            button(
              typ := "button",
              cls := "btn btn--primary",
              "add hint",
              disabled <-- busy,
              onClick --> { _ =>
                hint(
                  text.now(),
                  item.now(),
                  once.now(),
                  action.now(),
                  targets.now(),
                  tag.now(),
                  until.now(),
                  source.now(),
                  suggest.now()
                ) match
                  case Left(e)  => error.set(Some(e))
                  case Right(h) =>
                    error.set(None)
                    write(if h.suggest.contains(true) then "hint (a model suggests its action: see Approvals)"
                    else "hint")(ApiClient.managerAddHint(id, h))
                    text.set(""); targets.set(""); tag.set(""); until.set(""); source.set(""); suggest.set(false)
              }
            )
          )
      }
    )

  private def settings(
      m: ManagerInfo,
      busy: Signal[com.histmersion.web.ManagersView.SettingsBusy],
      write: com.histmersion.web.ManagersView.SettingsWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val form = Var(fields(m.config))
    val cron = Var(m.cron.getOrElse(""))
    val error = Var(Option.empty[String])
    val eff = fields(m.effective)
    htmlTag("details")(
      cls := "goal-section manager-settings",
      htmlTag("summary")("Settings"),
      p(cls := "muted", "Blank = the server's default (shown as the placeholder)."),
      Option.when(m.kind != Kind.Enrich.wire)(
        label(
          cls := "manager-setting",
          span("cron (UTC: five fields, or @daily/@weekly/@monthly; blank = none)"),
          input(
            typ := "text",
            cls := "field",
            aria.label := "cron",
            placeholder := (if m.kind == Kind.Watch.wire then "@weekly" else "none"),
            controlled(value <-- cron.signal, onInput.mapToValue --> cron.writer)
          )
        )
      ),
      div(
        cls := "manager-settings-grid",
        settingsFor(m.kind).map { (k, lbl) =>
          label(
            cls := "manager-setting",
            span(lbl),
            input(
              typ := "text",
              cls := "field",
              aria.label := lbl,
              placeholder := eff.getOrElse(k, ""),
              controlled(
                value <-- form.signal.map(_.getOrElse(k, "")),
                onInput.mapToValue --> (v => form.update(_ + (k -> v)))
              )
            )
          )
        }
      ),
      ErrorLine(error.signal),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("change these settings")
        else
          button(
            typ := "button",
            cls := "btn btn--primary",
            "save settings",
            disabled <-- busy,
            onClick --> { _ =>
              config(form.now()) match
                case Left(e)  => error.set(Some(e))
                case Right(c) =>
                  error.set(None)
                  // the cron goes only when it changed (sending it again would move its next firing)
                  val cr = Option(cron.now().trim).filter(_ != m.cron.getOrElse(""))
                  write("settings")(ApiClient.managerUpdate(m.id, ManagerUpdate(Some(c), cr)))
            }
          )
      }
    )
