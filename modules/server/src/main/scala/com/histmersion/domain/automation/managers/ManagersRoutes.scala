package com.histmersion
package domain.automation.managers

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import io.scalaland.chimney.dsl.*
import com.histmersion.api.automation.ManagersApi
import com.histmersion.api.automation.Managers.{ManagerInfo, ManagerItem}
import com.histmersion.config.AgentAutopilotConfig
import com.histmersion.{Offset, PageSize}
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers

/** REST wiring of the process managers — every route delegates to [[ManagersControllers]]. The fixed paths come before
  * `{id}/{action}`.
  */
object ManagersRoutes:

  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def light(id: ManagerInfo.Id): ManagerLight.Id = id.transformInto[ManagerLight.Id]

  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  private def pageSize(limit: Option[Int]): Option[PageSize] = limit.flatMap(PageSize.option(_))

  /** `switches` and `draining`: the autopilot's page's routes (read it, switch it, its mode; the drain), which show the
    * library's part too; `acquisition`: the acquisition list's routes its domain binds (the repair and the legal
    * re-check). Each in its place among these.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def apply(
      m: ManagersControllers,
      switches: List[ServerEndpoint[Any, Identity]],
      acquisition: List[ServerEndpoint[Any, Identity]],
      draining: List[ServerEndpoint[Any, Identity]]
  ): List[ServerEndpoint[Any, Identity]] =
    List(
      ManagersApi.list.serverLogicSuccess[Identity](_ => m.list),
      ManagersApi.create.serverLogic[Identity](in => CreatorControllers.rest(in)(m.create(in))),
      ManagersApi.get.serverLogic[Identity](id => m.get(light(id))),
      ManagersApi.update.serverLogic[Identity]((id, in) => m.update(light(id), in)),
      ManagersApi.delete.serverLogic[Identity](id => m.delete(light(id))),
      ManagersApi.items.serverLogic[Identity]((id, status, offset, limit) =>
        m.items(
          light(id),
          status.map(_.transformInto[ItemStatus.Wire]),
          offset.flatMap(o => Offset.option(o.value)),
          pageSize(limit.map(_.value))
        )
      ),
      ManagersApi.addItem.serverLogic[Identity]((id, in) => m.addItem(light(id), in)),
      ManagersApi.patchItem.serverLogic[Identity]((id, item, in) => m.patchItem(light(id), item, in)),
      ManagersApi.hints.serverLogic[Identity]((id, all) => m.hints(light(id), all.contains(true))),
      ManagersApi.addHint.serverLogic[Identity]((id, in) => CreatorControllers.rest(in)(m.addHint(light(id), in))),
      ManagersApi.removeHint.serverLogic[Identity]((id, h) => m.deleteHint(light(id), h)),
      ManagersApi.events.serverLogic[Identity]((id, before, limit) =>
        m.events(light(id), before.map(b => ManagerRepo.EventCursor(b.value)), pageSize(limit.map(_.value)))
      ),
      ManagersApi.action.serverLogic[Identity]((id, action, runs) =>
        m.action(light(id), ManagersControllers.Action(action.value), runs.map(ManagerInfo.PauseRuns(_)))
      ),
      ManagersApi.enqueue.serverLogic[Identity](in => CreatorControllers.rest(in)(m.enqueue(in)))
    ) ++ switches ++ acquisition ++ List(
      ManagersApi.focus.serverLogicSuccess[Identity](_ => m.focus),
      ManagersApi.setFocus.serverLogic[Identity](in => m.setFocus(in)),
      ManagersApi.scheduler.serverLogicSuccess[Identity](limit => m.scheduler(pageSize(limit.map(_.value)))),
      ManagersApi.setScheduler.serverLogic[Identity](in => m.setScheduler(in)),
      ManagersApi.schedulerWhy.serverLogic[Identity](item => m.schedulerWhy(ManagerItem.Ref(item.value))),
      ManagersApi.digest.serverLogicSuccess[Identity](_ => m.digest),
      ManagersApi.decideAll.serverLogic[Identity](in => CreatorControllers.rest(in)(m.decideAll(in)))
    ) ++ draining ++ List(
      ManagersApi.stalls.serverLogicSuccess[Identity](days =>
        m.stalls(days.map(d => AgentAutopilotConfig.StallDays(d.value)))
      )
    )
