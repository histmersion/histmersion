package com.histmersion.domain.automation.managers

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.automation.ManagersApi

/** REST wiring of the process managers — every route delegates to [[ManagersHandlers]]. The fixed paths come before
  * `{id}/{action}`.
  */
object ManagersRoutes:

  /** `switches` and `draining`: the autopilot's page's routes (read it, switch it, its mode; the drain), which show the
    * library's part too; `acquisition`: the acquisition list's routes its domain binds (the repair and the legal
    * re-check). Each in its place among these.
    */
  def apply(
      m: ManagersHandlers,
      switches: List[ServerEndpoint[Any, Identity]],
      acquisition: List[ServerEndpoint[Any, Identity]],
      draining: List[ServerEndpoint[Any, Identity]]
  ): List[ServerEndpoint[Any, Identity]] =
    List(
      ManagersApi.list.serverLogicSuccess[Identity](_ => m.list),
      ManagersApi.create.serverLogic[Identity](in => m.create(in)),
      ManagersApi.get.serverLogic[Identity](id => m.get(id)),
      ManagersApi.update.serverLogic[Identity]((id, in) => m.update(id, in)),
      ManagersApi.delete.serverLogic[Identity](id => m.delete(id)),
      ManagersApi.items.serverLogic[Identity]((id, status, offset, limit) => m.items(id, status, offset, limit)),
      ManagersApi.addItem.serverLogic[Identity]((id, in) => m.addItem(id, in)),
      ManagersApi.patchItem.serverLogic[Identity]((id, item, in) => m.patchItem(id, item, in)),
      ManagersApi.hints.serverLogic[Identity]((id, all) => m.hints(id, all.contains(true))),
      ManagersApi.addHint.serverLogic[Identity]((id, in) => m.addHint(id, in)),
      ManagersApi.removeHint.serverLogic[Identity]((id, h) => m.deleteHint(id, h)),
      ManagersApi.events.serverLogic[Identity]((id, before, limit) => m.events(id, before, limit)),
      ManagersApi.action.serverLogic[Identity]((id, action, runs) => m.action(id, action, runs)),
      ManagersApi.enqueue.serverLogic[Identity](in => m.enqueue(in))
    ) ++ switches ++ acquisition ++ List(
      ManagersApi.focus.serverLogicSuccess[Identity](_ => m.focus),
      ManagersApi.setFocus.serverLogic[Identity](in => m.setFocus(in)),
      ManagersApi.scheduler.serverLogicSuccess[Identity](limit => m.scheduler(limit)),
      ManagersApi.setScheduler.serverLogic[Identity](in => m.setScheduler(in)),
      ManagersApi.schedulerWhy.serverLogic[Identity](item => m.schedulerWhy(item)),
      ManagersApi.digest.serverLogicSuccess[Identity](_ => m.digest),
      ManagersApi.decideAll.serverLogic[Identity](in => m.decideAll(in))
    ) ++ draining ++ List(
      ManagersApi.stalls.serverLogicSuccess[Identity](days => m.stalls(days))
    )
