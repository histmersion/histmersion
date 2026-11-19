package com.histmersion
package api.automation
import com.histmersion.api.json.NewtypeCodecs.given
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.personal.UnreadPage

object ManagersApi:
  type Action = Action.T
  object Action extends Newtype[String, Pure]

  import Managers.*

  private lazy val base = endpoint.in("api" / "agents" / "managers").errorOut(Api.problemOut)
  private lazy val one = base.in(path[String]("id"))

  lazy val list: PublicEndpoint[Unit, Api.Error, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  lazy val create: PublicEndpoint[ManagerCreate, Api.Error, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  lazy val get: PublicEndpoint[ManagerInfo.Id, Api.Error, ManagerDetail, Any] =
    (one.get
      .out(jsonBody[ManagerDetail]))
      .mapIn[ManagerInfo.Id]((refactorDecoded15: String) => ManagerInfo.Id(refactorDecoded15))(
        (refactorEncoded15: ManagerInfo.Id) => (refactorEncoded15).value
      )

  lazy val update: PublicEndpoint[(ManagerInfo.Id, ManagerUpdate), Api.Error, ManagerInfo, Any] =
    (one.put
      .in(jsonBody[ManagerUpdate])
      .out(jsonBody[ManagerInfo]))
      .mapIn[(ManagerInfo.Id, ManagerUpdate)]((refactorDecoded16: (String, ManagerUpdate)) =>
        (ManagerInfo.Id((refactorDecoded16)._1), (refactorDecoded16)._2)
      )((refactorEncoded16: (ManagerInfo.Id, ManagerUpdate)) =>
        (((refactorEncoded16)._1).value, (refactorEncoded16)._2)
      )

  lazy val delete: PublicEndpoint[ManagerInfo.Id, Api.Error, Goal.Plans.Removed, Any] =
    (one.delete
      .out(jsonBody[Goal.Plans.Removed]))
      .mapIn[ManagerInfo.Id]((refactorDecoded3: String) => ManagerInfo.Id(refactorDecoded3))(
        (refactorEncoded3: ManagerInfo.Id) => (refactorEncoded3).value
      )

  lazy val items: PublicEndpoint[
    (
        ManagerInfo.Id,
        Option[api.automation.ItemStatus.Wire],
        Option[ItemPage.Limit],
        Option[UnreadPage.Offset]
    ),
    Api.Error,
    ItemPage,
    Any
  ] =
    (one.get
      .in("items")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ItemPage]))
      .mapIn[
        (
            ManagerInfo.Id,
            Option[api.automation.ItemStatus.Wire],
            Option[ItemPage.Limit],
            Option[UnreadPage.Offset]
        )
      ]((refactorDecoded18: (String, Option[String], Option[Int], Option[Int])) =>
        (
          ManagerInfo.Id((refactorDecoded18)._1),
          ((refactorDecoded18)._2)
            .map(refactorElement19 => api.automation.ItemStatus.Wire(refactorElement19)),
          ((refactorDecoded18)._3).map(refactorElement20 => ItemPage.Limit(refactorElement20)),
          ((refactorDecoded18)._4).map(refactorElement21 => UnreadPage.Offset(refactorElement21))
        )
      )(
        (refactorEncoded18: (
            ManagerInfo.Id,
            Option[api.automation.ItemStatus.Wire],
            Option[ItemPage.Limit],
            Option[UnreadPage.Offset]
        )) =>
          (
            ((refactorEncoded18)._1).value,
            ((refactorEncoded18)._2).map(refactorElement22 => (refactorElement22).value),
            ((refactorEncoded18)._3).map(refactorElement23 => (refactorElement23).value),
            ((refactorEncoded18)._4).map(refactorElement24 => (refactorElement24).value)
          )
      )

  lazy val addItem: PublicEndpoint[(ManagerInfo.Id, EnqueueRequest), Api.Error, ManagerItem, Any] =
    (one.post
      .in("items")
      .in(jsonBody[EnqueueRequest])
      .out(jsonBody[ManagerItem]))
      .mapIn[(ManagerInfo.Id, EnqueueRequest)]((refactorDecoded5: (String, EnqueueRequest)) =>
        (ManagerInfo.Id((refactorDecoded5)._1), (refactorDecoded5)._2)
      )((refactorEncoded5: (ManagerInfo.Id, EnqueueRequest)) => (((refactorEncoded5)._1).value, (refactorEncoded5)._2))

  lazy val patchItem: PublicEndpoint[(ManagerInfo.Id, ManagerItem.Ref, ItemPatch), Api.Error, ManagerItem, Any] =
    (one.patch
      .in("items" / path[String]("item"))
      .in(jsonBody[ItemPatch])
      .out(jsonBody[ManagerItem]))
      .mapIn[(ManagerInfo.Id, ManagerItem.Ref, ItemPatch)]((refactorDecoded6: (String, String, ItemPatch)) =>
        (ManagerInfo.Id((refactorDecoded6)._1), ManagerItem.Ref((refactorDecoded6)._2), (refactorDecoded6)._3)
      )((refactorEncoded6: (ManagerInfo.Id, ManagerItem.Ref, ItemPatch)) =>
        (((refactorEncoded6)._1).value, ((refactorEncoded6)._2).value, (refactorEncoded6)._3)
      )

  lazy val hints: PublicEndpoint[(ManagerInfo.Id, Option[Boolean]), Api.Error, List[ManagerHint], Any] =
    (one.get
      .in("hints")
      .in(query[Option[Boolean]]("all"))
      .out(jsonBody[List[ManagerHint]]))
      .mapIn[(ManagerInfo.Id, Option[Boolean])]((refactorDecoded21: (String, Option[Boolean])) =>
        (ManagerInfo.Id((refactorDecoded21)._1), ((refactorDecoded21)._2).map(refactorElement25 => (refactorElement25)))
      )((refactorEncoded21: (ManagerInfo.Id, Option[Boolean])) =>
        (((refactorEncoded21)._1).value, ((refactorEncoded21)._2).map(refactorElement26 => (refactorElement26)))
      )

  lazy val addHint: PublicEndpoint[(ManagerInfo.Id, HintAdd), Api.Error, ManagerHint, Any] =
    (one.post
      .in("hints")
      .in(jsonBody[HintAdd])
      .out(jsonBody[ManagerHint]))
      .mapIn[(ManagerInfo.Id, HintAdd)]((refactorDecoded8: (String, HintAdd)) =>
        (ManagerInfo.Id((refactorDecoded8)._1), (refactorDecoded8)._2)
      )((refactorEncoded8: (ManagerInfo.Id, HintAdd)) => (((refactorEncoded8)._1).value, (refactorEncoded8)._2))

  lazy val removeHint: PublicEndpoint[(ManagerInfo.Id, ManagerHint.Id), Api.Error, Goal.Plans.Removed, Any] =
    (one.delete
      .in("hints" / path[Long]("hint"))
      .out(jsonBody[Goal.Plans.Removed]))
      .mapIn[(ManagerInfo.Id, ManagerHint.Id)]((refactorDecoded23: (String, Long)) =>
        (ManagerInfo.Id((refactorDecoded23)._1), ManagerHint.Id((refactorDecoded23)._2))
      )((refactorEncoded23: (ManagerInfo.Id, ManagerHint.Id)) =>
        (((refactorEncoded23)._1).value, ((refactorEncoded23)._2).value)
      )

  lazy val events
      : PublicEndpoint[(ManagerInfo.Id, Option[EventPage.Before], Option[EventPage.Limit]), Api.Error, EventPage, Any] =
    (one.get
      .in("events")
      .in(query[Option[Long]]("before"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[EventPage]))
      .mapIn[(ManagerInfo.Id, Option[EventPage.Before], Option[EventPage.Limit])](
        (refactorDecoded24: (String, Option[Long], Option[Int])) =>
          (
            ManagerInfo.Id((refactorDecoded24)._1),
            ((refactorDecoded24)._2).map(refactorElement27 => EventPage.Before(refactorElement27)),
            ((refactorDecoded24)._3).map(refactorElement28 => EventPage.Limit(refactorElement28))
          )
      )((refactorEncoded24: (ManagerInfo.Id, Option[EventPage.Before], Option[EventPage.Limit])) =>
        (
          ((refactorEncoded24)._1).value,
          ((refactorEncoded24)._2).map(refactorElement29 => (refactorElement29).value),
          ((refactorEncoded24)._3).map(refactorElement30 => (refactorElement30).value)
        )
      )

  lazy val action: PublicEndpoint[(ManagerInfo.Id, Action, Option[Boolean]), Api.Error, ManagerInfo, Any] =
    (one.post
      .in(path[String]("action"))
      .in(query[Option[Boolean]]("runs"))
      .out(jsonBody[ManagerInfo]))
      .mapIn[(ManagerInfo.Id, Action, Option[Boolean])]((refactorDecoded11: (String, String, Option[Boolean])) =>
        (
          ManagerInfo.Id((refactorDecoded11)._1),
          Action((refactorDecoded11)._2),
          ((refactorDecoded11)._3).map(refactorElement13 => (refactorElement13))
        )
      )((refactorEncoded11: (ManagerInfo.Id, Action, Option[Boolean])) =>
        (
          ((refactorEncoded11)._1).value,
          ((refactorEncoded11)._2).value,
          ((refactorEncoded11)._3).map(refactorElement14 => (refactorElement14))
        )
      )

  lazy val enqueue: PublicEndpoint[EnqueueRequest, Api.Error, EnqueueResult, Any] =
    endpoint.post
      .in("api" / "agents" / "enqueue")
      .errorOut(Api.problemOut)
      .in(jsonBody[EnqueueRequest])
      .out(jsonBody[EnqueueResult])

  lazy val focus: PublicEndpoint[Unit, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.get
      .in("api" / "agents" / "autopilot" / "focus")
      .errorOut(Api.problemOut)
      .out(jsonBody[Autopilot.FocusInfo])

  lazy val setFocus: PublicEndpoint[Autopilot.FocusSet, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "focus")
      .errorOut(Api.problemOut)
      .in(jsonBody[Autopilot.FocusSet])
      .out(jsonBody[Autopilot.FocusInfo])

  private lazy val sched = endpoint.in("api" / "agents" / "scheduler").errorOut(Api.problemOut)

  lazy val scheduler: PublicEndpoint[Option[Scheduler.Limit], Api.Error, AgentScheduler.SchedulerStatus, Any] =
    (sched.get
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[AgentScheduler.SchedulerStatus]))
      .mapIn[Option[Scheduler.Limit]]((refactorDecoded12: Option[Int]) =>
        (refactorDecoded12).map(refactorElement15 => Scheduler.Limit(refactorElement15))
      )((refactorEncoded12: Option[Scheduler.Limit]) =>
        (refactorEncoded12).map(refactorElement16 => (refactorElement16).value)
      )

  lazy val setScheduler: PublicEndpoint[AgentScheduler.SchedulerSet, Api.Error, AgentScheduler.SchedulerStatus, Any] =
    sched.post.in(jsonBody[AgentScheduler.SchedulerSet]).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val schedulerWhy: PublicEndpoint[ManagerInfo.Id, Api.Error, AgentScheduler.WhyInfo, Any] =
    (sched.get
      .in("why" / path[String]("item"))
      .out(jsonBody[AgentScheduler.WhyInfo]))
      .mapIn[ManagerInfo.Id]((refactorDecoded13: String) => ManagerInfo.Id(refactorDecoded13))(
        (refactorEncoded13: ManagerInfo.Id) => (refactorEncoded13).value
      )

  lazy val digest: PublicEndpoint[Unit, Api.Error, ApprovalDigest.Digest, Any] =
    endpoint.get
      .in("api" / "agents" / "approvals" / "digest")
      .errorOut(Api.problemOut)
      .out(jsonBody[ApprovalDigest.Digest])

  lazy val decideAll: PublicEndpoint[ApprovalDigest.BulkDecide, Api.Error, ApprovalDigest.BulkResult, Any] =
    endpoint.post
      .in("api" / "agents" / "approvals" / "decide")
      .errorOut(Api.problemOut)
      .in(jsonBody[ApprovalDigest.BulkDecide])
      .out(jsonBody[ApprovalDigest.BulkResult])

  lazy val stalls: PublicEndpoint[Option[Stalls.Limit], Api.Error, Stalls.StallReport, Any] =
    (endpoint.get
      .in("api" / "agents" / "stalls")
      .errorOut(Api.problemOut)
      .in(query[Option[Int]]("days"))
      .out(jsonBody[Stalls.StallReport]))
      .mapIn[Option[Stalls.Limit]]((refactorDecoded28: Option[Int]) =>
        (refactorDecoded28).map(refactorElement35 => Stalls.Limit(refactorElement35))
      )((refactorEncoded28: Option[Stalls.Limit]) =>
        (refactorEncoded28).map(refactorElement36 => (refactorElement36).value)
      )

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
