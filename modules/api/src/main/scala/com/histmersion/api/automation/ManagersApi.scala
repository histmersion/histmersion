package com.histmersion.api.automation
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api

object ManagersApi:
  type ActionPart1Part2 = ActionPart1Part2.T
  object ActionPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Managers.*

  /** The managers' errors: 404 for a missing one, 409 for a run in the way, 400 otherwise. */
  lazy val errors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult => ((refactorResult) == (true)))),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(AgentsApi.CodeConflict))).andThen(refactorResult2 => ((refactorResult2) == (true)))),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  private lazy val base = endpoint.in("api" / "agents" / "managers").errorOut(errors)
  private lazy val one = base.in(path[String]("id"))

  lazy val list: PublicEndpoint[Unit, Api.Error, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  lazy val create: PublicEndpoint[ManagerCreate, Api.Error, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  lazy val get: PublicEndpoint[com.histmersion.api.automation.Managers.ManagerInfo.Id, Api.Error, ManagerDetail, Any] =
    (one.get.out(jsonBody[ManagerDetail])).mapIn[_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id]((refactorDecoded15: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id(refactorDecoded15))((refactorEncoded15: _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id) => (refactorEncoded15).value)

  lazy val update: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, ManagerUpdate), Api.Error, ManagerInfo, Any] =
    (one.put.in(jsonBody[ManagerUpdate]).out(jsonBody[ManagerInfo])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerUpdate)]((refactorDecoded16: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Managers.ManagerUpdate)) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded16)._1), (refactorDecoded16)._2))((refactorEncoded16: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerUpdate)) => (((refactorEncoded16)._1).value, (refactorEncoded16)._2))

  lazy val delete: PublicEndpoint[com.histmersion.api.automation.Managers.ManagerInfo.Id, Api.Error, Goal.Plans.Removed, Any] =
    (one.delete.out(jsonBody[Goal.Plans.Removed])).mapIn[_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id]((refactorDecoded3: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id(refactorDecoded3))((refactorEncoded3: _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id) => (refactorEncoded3).value)

  lazy val items: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, Option[com.histmersion.api.automation.ItemStatus.Wire], Option[com.histmersion.api.automation.Managers.ItemPage.Limit], Option[com.histmersion.api.personal.UnreadPage.Offset]), Api.Error, ItemPage, Any] =
    (one.get
      .in("items")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ItemPage])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[_root_.com.histmersion.api.automation.ItemStatus.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.ItemPage.Limit], _root_.scala.Option[_root_.com.histmersion.api.personal.UnreadPage.Offset])]((refactorDecoded18: (_root_.java.lang.String, _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded18)._1), ((refactorDecoded18)._2).map(refactorElement19 => _root_.com.histmersion.api.automation.ItemStatus.Wire(refactorElement19)), ((refactorDecoded18)._3).map(refactorElement20 => _root_.com.histmersion.api.automation.Managers.ItemPage.Limit(refactorElement20)), ((refactorDecoded18)._4).map(refactorElement21 => _root_.com.histmersion.api.personal.UnreadPage.Offset(refactorElement21))))((refactorEncoded18: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[_root_.com.histmersion.api.automation.ItemStatus.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.ItemPage.Limit], _root_.scala.Option[_root_.com.histmersion.api.personal.UnreadPage.Offset])) => (((refactorEncoded18)._1).value, ((refactorEncoded18)._2).map(refactorElement22 => (refactorElement22).value), ((refactorEncoded18)._3).map(refactorElement23 => (refactorElement23).value), ((refactorEncoded18)._4).map(refactorElement24 => (refactorElement24).value)))

  lazy val addItem: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, EnqueueRequest), Api.Error, ManagerItem, Any] =
    (one.post.in("items").in(jsonBody[EnqueueRequest]).out(jsonBody[ManagerItem])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.EnqueueRequest)]((refactorDecoded5: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Managers.EnqueueRequest)) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded5)._1), (refactorDecoded5)._2))((refactorEncoded5: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.EnqueueRequest)) => (((refactorEncoded5)._1).value, (refactorEncoded5)._2))

  lazy val patchItem: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, com.histmersion.api.automation.Managers.ManagerItem.Ref, ItemPatch), Api.Error, ManagerItem, Any] =
    (one.patch.in("items" / path[String]("item")).in(jsonBody[ItemPatch]).out(jsonBody[ManagerItem])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerItem.Ref, _root_.com.histmersion.api.automation.Managers.ItemPatch)]((refactorDecoded6: (_root_.java.lang.String, _root_.java.lang.String, _root_.com.histmersion.api.automation.Managers.ItemPatch)) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded6)._1), _root_.com.histmersion.api.automation.Managers.ManagerItem.Ref((refactorDecoded6)._2), (refactorDecoded6)._3))((refactorEncoded6: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerItem.Ref, _root_.com.histmersion.api.automation.Managers.ItemPatch)) => (((refactorEncoded6)._1).value, ((refactorEncoded6)._2).value, (refactorEncoded6)._3))

  lazy val hints: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, Option[Boolean]), Api.Error, List[ManagerHint], Any] =
    (one.get.in("hints").in(query[Option[Boolean]]("all")).out(jsonBody[List[ManagerHint]])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[Boolean])]((refactorDecoded21: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Boolean])) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded21)._1), ((refactorDecoded21)._2).map(refactorElement25 => (refactorElement25))))((refactorEncoded21: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[Boolean])) => (((refactorEncoded21)._1).value, ((refactorEncoded21)._2).map(refactorElement26 => (refactorElement26))))

  lazy val addHint: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, HintAdd), Api.Error, ManagerHint, Any] =
    (one.post.in("hints").in(jsonBody[HintAdd]).out(jsonBody[ManagerHint])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.HintAdd)]((refactorDecoded8: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Managers.HintAdd)) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded8)._1), (refactorDecoded8)._2))((refactorEncoded8: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.HintAdd)) => (((refactorEncoded8)._1).value, (refactorEncoded8)._2))

  lazy val removeHint: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, com.histmersion.api.automation.Managers.ManagerHint.Id), Api.Error, Goal.Plans.Removed, Any] =
    (one.delete.in("hints" / path[Long]("hint")).out(jsonBody[Goal.Plans.Removed])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerHint.Id)]((refactorDecoded23: (_root_.java.lang.String, _root_.scala.Long)) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded23)._1), _root_.com.histmersion.api.automation.Managers.ManagerHint.Id((refactorDecoded23)._2)))((refactorEncoded23: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.Managers.ManagerHint.Id)) => (((refactorEncoded23)._1).value, ((refactorEncoded23)._2).value))

  lazy val events: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, Option[com.histmersion.api.automation.Managers.EventPage.Before], Option[com.histmersion.api.automation.Managers.EventPage.Limit]), Api.Error, EventPage, Any] =
    (one.get.in("events").in(query[Option[Long]]("before")).in(query[Option[Int]]("limit")).out(jsonBody[EventPage])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.EventPage.Before], _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.EventPage.Limit])]((refactorDecoded24: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Long], _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded24)._1), ((refactorDecoded24)._2).map(refactorElement27 => _root_.com.histmersion.api.automation.Managers.EventPage.Before(refactorElement27)), ((refactorDecoded24)._3).map(refactorElement28 => _root_.com.histmersion.api.automation.Managers.EventPage.Limit(refactorElement28))))((refactorEncoded24: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.EventPage.Before], _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.EventPage.Limit])) => (((refactorEncoded24)._1).value, ((refactorEncoded24)._2).map(refactorElement29 => (refactorElement29).value), ((refactorEncoded24)._3).map(refactorElement30 => (refactorElement30).value)))

  lazy val action: PublicEndpoint[(com.histmersion.api.automation.Managers.ManagerInfo.Id, com.histmersion.api.automation.ManagersApi.ActionPart1Part2, Option[Boolean]), Api.Error, ManagerInfo, Any] =
    (one.post.in(path[String]("action")).in(query[Option[Boolean]]("runs")).out(jsonBody[ManagerInfo])).mapIn[(_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.ManagersApi.ActionPart1Part2, _root_.scala.Option[Boolean])]((refactorDecoded11: (_root_.java.lang.String, _root_.java.lang.String, _root_.scala.Option[_root_.scala.Boolean])) => (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id((refactorDecoded11)._1), _root_.com.histmersion.api.automation.ManagersApi.ActionPart1Part2((refactorDecoded11)._2), ((refactorDecoded11)._3).map(refactorElement13 => (refactorElement13))))((refactorEncoded11: (_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id, _root_.com.histmersion.api.automation.ManagersApi.ActionPart1Part2, _root_.scala.Option[Boolean])) => (((refactorEncoded11)._1).value, ((refactorEncoded11)._2).value, ((refactorEncoded11)._3).map(refactorElement14 => (refactorElement14))))

  lazy val enqueue: PublicEndpoint[EnqueueRequest, Api.Error, EnqueueResult, Any] =
    endpoint.post
      .in("api" / "agents" / "enqueue")
      .errorOut(errors)
      .in(jsonBody[EnqueueRequest])
      .out(jsonBody[EnqueueResult])

  lazy val focus: PublicEndpoint[Unit, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.get.in("api" / "agents" / "autopilot" / "focus").errorOut(errors).out(jsonBody[Autopilot.FocusInfo])

  lazy val setFocus: PublicEndpoint[Autopilot.FocusSet, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "focus")
      .errorOut(errors)
      .in(jsonBody[Autopilot.FocusSet])
      .out(jsonBody[Autopilot.FocusInfo])

  private lazy val sched = endpoint.in("api" / "agents" / "scheduler").errorOut(errors)

  lazy val scheduler: PublicEndpoint[Option[com.histmersion.api.automation.Managers.Scheduler.Limit], Api.Error, AgentScheduler.SchedulerStatus, Any] =
    (sched.get.in(query[Option[Int]]("limit")).out(jsonBody[AgentScheduler.SchedulerStatus])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.automation.Managers.Scheduler.Limit]]((refactorDecoded12: _root_.scala.Option[_root_.scala.Int]) => (refactorDecoded12).map(refactorElement15 => _root_.com.histmersion.api.automation.Managers.Scheduler.Limit(refactorElement15)))((refactorEncoded12: _root_.scala.Option[_root_.com.histmersion.api.automation.Managers.Scheduler.Limit]) => (refactorEncoded12).map(refactorElement16 => (refactorElement16).value))

  lazy val setScheduler: PublicEndpoint[AgentScheduler.SchedulerSet, Api.Error, AgentScheduler.SchedulerStatus, Any] =
    sched.post.in(jsonBody[AgentScheduler.SchedulerSet]).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val schedulerWhy: PublicEndpoint[com.histmersion.api.automation.Managers.ManagerInfo.Id, Api.Error, AgentScheduler.WhyInfo, Any] =
    (sched.get.in("why" / path[String]("item")).out(jsonBody[AgentScheduler.WhyInfo])).mapIn[_root_.com.histmersion.api.automation.Managers.ManagerInfo.Id]((refactorDecoded13: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id(refactorDecoded13))((refactorEncoded13: _root_.com.histmersion.api.automation.Managers.ManagerInfo.Id) => (refactorEncoded13).value)

  lazy val digest: PublicEndpoint[Unit, Api.Error, ApprovalDigest.Digest, Any] =
    endpoint.get.in("api" / "agents" / "approvals" / "digest").errorOut(errors).out(jsonBody[ApprovalDigest.Digest])

  lazy val decideAll: PublicEndpoint[ApprovalDigest.BulkDecide, Api.Error, ApprovalDigest.BulkResult, Any] =
    endpoint.post
      .in("api" / "agents" / "approvals" / "decide")
      .errorOut(errors)
      .in(jsonBody[ApprovalDigest.BulkDecide])
      .out(jsonBody[ApprovalDigest.BulkResult])

  lazy val stalls: PublicEndpoint[Option[com.histmersion.api.automation.Stalls.Limit], Api.Error, Stalls.StallReport, Any] =
    (endpoint.get
      .in("api" / "agents" / "stalls")
      .errorOut(errors)
      .in(query[Option[Int]]("days"))
      .out(jsonBody[Stalls.StallReport])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.automation.Stalls.Limit]]((refactorDecoded28: _root_.scala.Option[_root_.scala.Int]) => (refactorDecoded28).map(refactorElement35 => _root_.com.histmersion.api.automation.Stalls.Limit(refactorElement35)))((refactorEncoded28: _root_.scala.Option[_root_.com.histmersion.api.automation.Stalls.Limit]) => (refactorEncoded28).map(refactorElement36 => (refactorElement36).value))

  /** The fixed-path endpoints come before `action`, whose last segment is a parameter. */
  /** The managers themselves, their items, hints and events (first of the agents' endpoints in `Api.all`). */
  lazy val all: List[AnyEndpoint] = List(
    list,
    create,
    get,
    update,
    delete,
    items,
    addItem,
    patchItem,
    hints,
    addHint,
    removeHint,
    events,
    action,
    enqueue
  )

  /** The steering of the managers: the focus, the scheduler, the approvals' digest (between the autopilot's switches
    * and the drain, in `Api.all`).
    */
  lazy val steering: List[AnyEndpoint] = List(
    focus,
    setFocus,
    scheduler,
    setScheduler,
    schedulerWhy,
    digest,
    decideAll
  )

  /** What has stalled (after the drain, in `Api.all`). */
  lazy val stalled: List[AnyEndpoint] = List(stalls)
