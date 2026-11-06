package com.histmersion.api.automation
import hearth.kindlings.optics.syntax.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.library.Acquire
import com.histmersion.api.given

object Queue:

  type ApplyPatchClearableV = ApplyPatchClearableV.T
  object ApplyPatchClearableV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPatchClearableOld = ApplyPatchClearableOld.T
  object ApplyPatchClearableOld extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsePushSegment = ParsePushSegment.T
  object ParsePushSegment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsePushIndex = ParsePushIndex.T
  object ParsePushIndex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Statuses = Statuses.T
  object Statuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusOfResultPart1 = StatusOfResultPart1.T
  object StatusOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusOfResultPart2 = StatusOfResultPart2.T
  object StatusOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusOfRaw = StatusOfRaw.T
  object StatusOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextOfResultPart1 = TextOfResultPart1.T
  object TextOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextOfResultPart2 = TextOfResultPart2.T
  object TextOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextOfRaw = TextOfRaw.T
  object TextOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfilledReasonResult = FulfilledReasonResult.T
  object FulfilledReasonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfilledReasonPrev = FulfilledReasonPrev.T
  object FulfilledReasonPrev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfilledReasonCitekey = FulfilledReasonCitekey.T
  object FulfilledReasonCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfilCitekey = FulfilCitekey.T
  object FulfilCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfilBy = FulfilBy.T
  object FulfilBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfillableResultPart1 = FulfillableResultPart1.T
  object FulfillableResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FulfillableId = FulfillableId.T
  object FulfillableId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemGoalsOwnPart1 = ItemGoalsOwnPart1.T
  object ItemGoalsOwnPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemGoalsLinkedPart1 = ItemGoalsLinkedPart1.T
  object ItemGoalsLinkedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAtOfResultPart1 = CreatedAtOfResultPart1.T
  object CreatedAtOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAtOfResultPart2 = CreatedAtOfResultPart2.T
  object CreatedAtOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAtOfRaw = CreatedAtOfRaw.T
  object CreatedAtOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathResult = PathResult.T
  object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathTopic = PathTopic.T
  object PathTopic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TopicOfResult = TopicOfResult.T
  object TopicOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TopicOfP = TopicOfP.T
  object TopicOfP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseText = ParseText.T
  object ParseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsBoldResult = IsBoldResult.T
  object IsBoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsBoldS = IsBoldS.T
  object IsBoldS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryDateResult = EntryDateResult.T
  object EntryDateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryDateStack = EntryDateStack.T
  object EntryDateStack extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrimBlankResult = TrimBlankResult.T
  object TrimBlankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrimBlankLs = TrimBlankLs.T
  object TrimBlankLs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultPreambleResult = DefaultPreambleResult.T
  object DefaultPreambleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingLinesPrev = HeadingLinesPrev.T
  object HeadingLinesPrev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingLinesNext = HeadingLinesNext.T
  object HeadingLinesNext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BulletResult = BulletResult.T
  object BulletResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JoinResult = JoinResult.T
  object JoinResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstantKeyResult = InstantKeyResult.T
  object InstantKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstantKeyS = InstantKeyS.T
  object InstantKeyS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownNotesPart1 = MarkdownNotesPart1.T
  object MarkdownNotesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownNotesPart2 = MarkdownNotesPart2.T
  object MarkdownNotesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class QueueItem(
      id: QueueItem.Id,
      list: QueueItem.List,
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic],
      status: QueueItem.Status,
      reason: Option[QueueItem.Reason] = None,
      createdAt: Option[QueueItem.CreatedAt] = None,
      doneAt: Option[QueueItem.DoneAt] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      createdBy: Option[QueueItem.CreatedBy] = None,
      closedBy: Option[QueueItem.ClosedBy] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QueueItem:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
    type List = List.T
    object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topic = Topic.T
    object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoneAt = DoneAt.T
    object DoneAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedBy = CreatedBy.T
    object CreatedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClosedBy = ClosedBy.T
    object ClosedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ItemGoals(item: com.histmersion.api.automation.Queue.QueueItem.Id, goals: List[GoalRef]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class GoalRef(slug: com.histmersion.api.automation.Goal.Slug, title: com.histmersion.api.automation.Goal.Title) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class QueueAdd(
      text: com.histmersion.api.automation.Queue.QueueItem.Text,
      topic: Option[com.histmersion.api.automation.Queue.QueueItem.Topic] = None,
      status: Option[com.histmersion.api.automation.Queue.QueueItem.Status] = None,
      reason: Option[com.histmersion.api.automation.Queue.QueueItem.Reason] = None,
      createdAt: Option[QueueAdd.CreatedAt] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      job: Option[com.histmersion.api.jobs.JobInfo.Id] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
  object QueueAdd:


    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[QueueAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[QueueAdd])

  final case class QueuePatch(
      status: Option[com.histmersion.api.automation.Queue.QueueItem.Status] = None,
      text: Option[com.histmersion.api.automation.Queue.QueueItem.Text] = None,
      topic: Option[com.histmersion.api.automation.Queue.QueueItem.Topic] = None,
      reason: Option[com.histmersion.api.automation.Queue.QueueItem.Reason] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      closedBy: Option[QueuePatch.ClosedBy] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
  object QueuePatch:


    type ClosedBy = ClosedBy.T
    object ClosedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[QueuePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[QueuePatch])

  final case class QueueImport(text: com.histmersion.api.automation.Queue.QueueItem.Text, replace: QueueImport.Replace = _root_.com.histmersion.api.automation.Queue.QueueImport.Replace(true)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object QueueImport:
    type Replace = Replace.T
    object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class QueueImported(list: QueueImported.List, items: QueueImported.Items, notes: QueueImported.Notes, replaced: QueueImported.Replaced)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QueueImported:
    type List = List.T
    object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Items = Items.T
    object Items extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Replaced = Replaced.T
    object Replaced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum Kind(val label: Kind.Label, val file: Kind.File, val checkboxes: Kind.Checkboxes):
    case Acquire extends Kind(_root_.com.histmersion.api.automation.Queue.Kind.Label("acquire"), _root_.com.histmersion.api.automation.Queue.Kind.File("ACQUIRE.md"), _root_.com.histmersion.api.automation.Queue.Kind.Checkboxes(true))
    case Tasks extends Kind(_root_.com.histmersion.api.automation.Queue.Kind.Label("tasks"), _root_.com.histmersion.api.automation.Queue.Kind.File("TASKS.md"), _root_.com.histmersion.api.automation.Queue.Kind.Checkboxes(true))
    case Log extends Kind(_root_.com.histmersion.api.automation.Queue.Kind.Label("log"), _root_.com.histmersion.api.automation.Queue.Kind.File("LOG.md"), _root_.com.histmersion.api.automation.Queue.Kind.Checkboxes(false))

  object Kind:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Checkboxes = Checkboxes.T
    object Checkboxes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val all: List[com.histmersion.api.automation.Queue.Kind.All] = values.toList.map(_.label)
    def parse(s: com.histmersion.api.automation.Queue.Kind.ParseS): Option[Kind] = values.find(_.label == (s).value.trim.toLowerCase)

  val Open = "open"
  val Done = "done"
  val Note = "note"

  val Statuses: List[com.histmersion.api.automation.Queue.Statuses] = List(_root_.com.histmersion.api.automation.Queue.Statuses(Open), _root_.com.histmersion.api.automation.Queue.Statuses(Done), _root_.com.histmersion.api.automation.Queue.Statuses(Note))

  def statusOf(raw: Option[com.histmersion.api.automation.Queue.StatusOfRaw]): Either[com.histmersion.api.automation.Queue.StatusOfResultPart1, Option[com.histmersion.api.automation.Queue.StatusOfResultPart2]] =
    raw.map((_).value.trim.toLowerCase).filter(_.nonEmpty) match
      case None                            => Right(None)
      case Some(s) if Statuses.contains(s) => Right(Some(_root_.com.histmersion.api.automation.Queue.StatusOfResultPart2(s)))
      case Some(s)                         => Left(_root_.com.histmersion.api.automation.Queue.StatusOfResultPart1(s"unknown status '$s' — one of: ${Statuses.mkString(", ")}"))

  def textOf(raw: Option[com.histmersion.api.automation.Queue.TextOfRaw]): Either[com.histmersion.api.automation.Queue.TextOfResultPart1, Option[com.histmersion.api.automation.Queue.TextOfResultPart2]] =
    raw match
      case Some(t) if (t).value.trim.isEmpty => Left(_root_.com.histmersion.api.automation.Queue.TextOfResultPart1("queue item text is empty"))
      case other                     => Right(other)

  def fulfilledReason(prev: Option[com.histmersion.api.automation.Queue.FulfilledReasonPrev], citekey: com.histmersion.api.automation.Queue.FulfilledReasonCitekey): com.histmersion.api.automation.Queue.FulfilledReasonResult =
    val tag = s"ingested as [[$citekey]]"
    prev.map((_).value.trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => _root_.com.histmersion.api.automation.Queue.FulfilledReasonResult(p)
      case Some(p)                    => _root_.com.histmersion.api.automation.Queue.FulfilledReasonResult(s"$p · $tag")
      case None                       => _root_.com.histmersion.api.automation.Queue.FulfilledReasonResult(tag)

  def fulfil(item: QueueItem, citekey: com.histmersion.api.automation.Queue.FulfilCitekey, by: Option[com.histmersion.api.automation.Queue.FulfilBy] = None): QueuePatch =
    QueuePatch(status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done)), reason = Some(fulfilledReason(item.reason, citekey)), closedBy = by)

  def fulfillable(id: com.histmersion.api.automation.Queue.FulfillableId, found: Option[QueueItem]): Either[com.histmersion.api.automation.Queue.FulfillableResultPart1, QueueItem] =
    found match
      case None                                    => Left(_root_.com.histmersion.api.automation.Queue.FulfillableResultPart1(s"no acquisition item $id"))
      case Some(i) if i.list != Kind.Acquire.label =>
        Left(_root_.com.histmersion.api.automation.Queue.FulfillableResultPart1(s"item $id is on the ${i.list} list, not the acquisition list"))
      case Some(i) if i.status == Done => Left(_root_.com.histmersion.api.automation.Queue.FulfillableResultPart1(s"acquisition item $id is already done"))
      case Some(i) if i.status != Open => Left(_root_.com.histmersion.api.automation.Queue.FulfillableResultPart1(s"item $id is not an open acquisition item"))
      case Some(i)                     => Right(i)

  def itemGoals(own: List[(com.histmersion.api.automation.Queue.ItemGoalsOwnPart1, GoalRef)], linked: List[(com.histmersion.api.automation.Queue.ItemGoalsLinkedPart1, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  def applyPatch(cur: QueueItem, p: QueuePatch): QueueItem =
    def clearable(v: Option[com.histmersion.api.automation.Queue.ApplyPatchClearableV], old: Option[com.histmersion.api.automation.Queue.ApplyPatchClearableOld]) = v.fold(old)(x => Some((x).value.trim).filter(_.nonEmpty))
    val status = p.status.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(cur.status)
    val closedBy =
      if status != Done then None
      else p.closedBy.map(_.trim).filter(_.nonEmpty).orElse(Option.when(cur.status == Done)(cur.closedBy).flatten)
    cur.modify(
      _.status).setTo(status).modify(_.text).setTo(p.text.getOrElse(cur.text)).modify(_.topic).setTo(clearable(p.topic, cur.topic)).modify(_.reason).setTo(clearable(p.reason, cur.reason)).modify(_.goal).setTo(clearable(p.goal, cur.goal)).modify(_.closedBy).setTo(closedBy
    )

  def createdAtOf(raw: Option[com.histmersion.api.automation.Queue.CreatedAtOfRaw]): Either[com.histmersion.api.automation.Queue.CreatedAtOfResultPart1, Option[com.histmersion.api.automation.Queue.CreatedAtOfResultPart2]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        ((scala.util
          .Try(java.time.Instant.parse(s))
          .toEither
          .map(i => Some(i.toString))
          .left
          .map(_ => s"created_at '$s' is not an ISO-8601 instant (e.g. 2026-08-25T00:00:00Z)")).left.map(refactorError => _root_.com.histmersion.api.automation.Queue.CreatedAtOfResultPart1(refactorError))).map(refactorValue2 => (refactorValue2).map(refactorValue3 => _root_.com.histmersion.api.automation.Queue.CreatedAtOfResultPart2(refactorValue3)))

  val Sep = " > "

  def path(topic: Option[com.histmersion.api.automation.Queue.PathTopic]): Vector[com.histmersion.api.automation.Queue.PathResult] =
    topic.map((_).value.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty)).getOrElse(Vector.empty)

  def topicOf(p: Vector[com.histmersion.api.automation.Queue.TopicOfP]): Option[com.histmersion.api.automation.Queue.TopicOfResult] = if p.isEmpty then None else Some(_root_.com.histmersion.api.automation.Queue.TopicOfResult(p.mkString(Sep)))

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r
  private val BoldRe = """^(\*\*.+\*\*)\s*$""".r
  private val CheckRe = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r
  private val BulletRe = """^\s*[-*]\s+(.*)$""".r

  private val DateRe = """^(\d{4}-\d{2}-\d{2}).*""".r

  def parse(kind: Kind, text: com.histmersion.api.automation.Queue.ParseText): List[QueueAdd] =
    val out = List.newBuilder[QueueAdd]
    var stack = Vector.empty[String]
    val note = List.newBuilder[String]
    var last: Option[QueueAdd] = None

    def flushItem(): Unit =
      last.foreach(i => out += i)
      last = None

    def flushNote(): Unit =
      val lines = trimBlank((note.result()).map(refactorValue8 => _root_.com.histmersion.api.automation.Queue.TrimBlankLs(refactorValue8)))
      note.clear()
      if lines.nonEmpty then out += QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(lines.mkString("\n")), topicOf(stack), status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Note)))

    def flush(): Unit = { flushItem(); flushNote() }

    def push(segment: com.histmersion.api.automation.Queue.ParsePushSegment, index: com.histmersion.api.automation.Queue.ParsePushIndex): Unit =
      val i = math.min((index).value, stack.length)
      stack = stack.take(i) :+ (segment).value

    (text).value.linesIterator.foreach { line =>
      line match
        case HeadingRe(hashes, title) =>
          flush()
          push(_root_.com.histmersion.api.automation.Queue.ParsePushSegment(title), _root_.com.histmersion.api.automation.Queue.ParsePushIndex(hashes.length - 2))
        case BoldRe(bold) =>
          flush()
          val depth = if stack.lastOption.exists(_root_.com.histmersion.api.automation.Queue.IsBoldS(isBold)) then stack.length - 1 else stack.length
          push(_root_.com.histmersion.api.automation.Queue.ParsePushSegment(bold), _root_.com.histmersion.api.automation.Queue.ParsePushIndex(depth))
        case CheckRe(box, body) if kind.checkboxes =>
          flush()
          last = Some(QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(body), topicOf(stack), status = Some(if box.trim.isEmpty then _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open) else _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done))))
        case BulletRe(body) if !kind.checkboxes =>
          flush()
          last = Some(QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(body), topicOf(stack), status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open)), createdAt = entryDate(stack)))
        case l =>
          if last.isDefined && l.nonEmpty && l.head.isWhitespace then
            last = last.map(i => i.modify(_.text).using(_ + "\n" + l))
          else
            flushItem()
            note += l
    }
    flush()
    out.result()

  private def isBold(s: com.histmersion.api.automation.Queue.IsBoldS): com.histmersion.api.automation.Queue.IsBoldResult = _root_.com.histmersion.api.automation.Queue.IsBoldResult((s).value.startsWith("**") && (s).value.endsWith("**"))

  private def entryDate(stack: Vector[com.histmersion.api.automation.Queue.EntryDateStack]): Option[com.histmersion.api.automation.Queue.EntryDateResult] =
    stack.headOption.flatMap (({
      case DateRe(d) => Some(s"${d}T00:00:00Z")
      case _         => None
    }).value).value

  private def trimBlank(ls: List[com.histmersion.api.automation.Queue.TrimBlankLs]): List[com.histmersion.api.automation.Queue.TrimBlankResult] =
    ls.dropWhile((_).value.trim.isEmpty).reverse.dropWhile((_).value.trim.isEmpty).reverse

  private def defaultPreamble(kind: Kind): com.histmersion.api.automation.Queue.DefaultPreambleResult = kind match
    case Kind.Acquire =>
      _root_.com.histmersion.api.automation.Queue.DefaultPreambleResult("# 📥 ACQUIRE — the acquisition queue\n\nWhat to buy / borrow / download → Calibre. " +
        "`[ ]` = to get · `[x]` = done.\n\n---")
    case Kind.Tasks =>
      _root_.com.histmersion.api.automation.Queue.DefaultPreambleResult("# 🛠️ TASKS — the agent's backlog\n\nWhat is left to do. `[ ]` = open · `[x]` = done.\n\n---")
    case Kind.Log =>
      _root_.com.histmersion.api.automation.Queue.DefaultPreambleResult("# 📓 LOG — project worklog\n\nWhat was done and when, newest first.\n\n---")

  private enum Block:
    case Heading(line: Block.Heading.Line)
    case Item(lines: Block.Item.Lines)
    case Note(lines: Block.Note.Lines)

  object Block:
    object Heading:
      type Line = Line.T
      object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Item:
      type Lines = Lines.T
      object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Note:
      type Lines = Lines.T
      object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def render(kind: Kind, items: List[QueueItem]): com.histmersion.api.automation.Queue.RenderResult =
    val (preamble, rest) = items.partition(_.topic.isEmpty)
    val groups = rest
      .groupBy(_.topic.getOrElse(""))
      .toList
      .sortBy { (_, is) =>
        val minId = is.map(_.id).minOption.getOrElse(0)
        if kind.checkboxes then (0L, minId.toLong)
        else (-is.flatMap(_.createdAt).maxOption.map(instantKey).getOrElse(0L), minId.toLong)
      }
      .map((topic, is) => (path(Some(topic)), is.sortBy(_.id)))

    val blocks = List.newBuilder[Block]
    val head = preamble.sortBy(_.id).map(_.text).filter(_.trim.nonEmpty)
    if head.isEmpty then blocks += Block.Note(defaultPreamble(kind))
    else head.foreach(t => blocks += Block.Note(t))

    var prev = Vector.empty[String]
    groups.foreach { (p, is) =>
      headingLines(prev, p).foreach(h => blocks += h)
      prev = p
      is.foreach(i => blocks += (if i.status == Note then Block.Note(i.text) else Block.Item(bullet(kind, i))))
    }
    join(blocks.result())

  private def headingLines(prev: Vector[com.histmersion.api.automation.Queue.HeadingLinesPrev], next: Vector[com.histmersion.api.automation.Queue.HeadingLinesNext]): List[Block] =
    if next.isEmpty then Nil
    else
      val firstDiff = next.indices.find(i => prev.lift(i) != Some(next(i)))
      val from = firstDiff.getOrElse(next.length - 1)
      (from until next.length).toList.map { i =>
        val seg = next(i)
        if isBold(seg) then Block.Heading(seg)
        else Block.Heading(_root_.com.histmersion.api.automation.Queue.Block.Heading.Line(s"${"#" * (i + 2)} $seg"))
      }

  private def bullet(kind: Kind, i: QueueItem): com.histmersion.api.automation.Queue.BulletResult =
    val marker = if !kind.checkboxes then "- " else if i.status == Done then "- [x] " else "- [ ] "
    val lines = i.text.linesIterator.toList
    (marker + lines.headOption.getOrElse("")) + lines.drop(1).map("\n" + _).mkString

  private def join(blocks: List[Block]): com.histmersion.api.automation.Queue.JoinResult =
    val sb = new StringBuilder
    blocks.zipWithIndex.foreach { (b, idx) =>
      val sep = blocks.lift(idx - 1) match
        case None                                                     => ""
        case Some(Block.Heading(_)) if !b.isInstanceOf[Block.Heading] => "\n"
        case Some(Block.Item(_)) if b.isInstanceOf[Block.Item]        => "\n"
        case Some(_)                                                  => "\n\n"
      sb.append(sep)
      sb.append(b match
        case Block.Heading(l) => l
        case Block.Item(l)    => l
        case Block.Note(l)    => l)
    }
    sb.append("\n")
    _root_.com.histmersion.api.automation.Queue.JoinResult(sb.result())

  private def instantKey(s: com.histmersion.api.automation.Queue.InstantKeyS): com.histmersion.api.automation.Queue.InstantKeyResult =
    scala.util.Try(java.time.Instant.parse(s).toEpochMilli).getOrElse(_root_.com.histmersion.api.automation.Queue.InstantKeyResult(0L))

  /** A list's open items by topic; `notes` is a line under an item, by its id (an ACQUIRE item's last check). */
  def markdown(kind: Kind, items: List[QueueItem], notes: Map[com.histmersion.api.automation.Queue.MarkdownNotesPart1, com.histmersion.api.automation.Queue.MarkdownNotesPart2] = Map.empty): com.histmersion.api.automation.Queue.MarkdownResult =
    val shown = items.filterNot(_.status == Note)
    if shown.isEmpty then _root_.com.histmersion.api.automation.Queue.MarkdownResult(s"_${kind.label}: nothing queued._")
    else
      val groups = shown.groupBy(_.topic).toList.sortBy((_, is) => is.map(_.id).min)
      val blocks = groups.map { (topic, is) =>
        val head = if topic.isEmpty then "(no topic)"
        else
          path(topic)
            .map(seg => if isBold(seg) then seg.drop(2).dropRight(2) else seg)
            .mkString(Sep)
        val lines = is.sortBy(_.id).map { i =>
          val box = if !kind.checkboxes then "" else if i.status == Done then "[x] " else "[ ] "
          val why = i.reason.map(r => s" — _${r}_").getOrElse("")
          s"- $box${i.text.linesIterator.mkString(" ")}$why  `#${i.id}`" +
            notes.get(i.id).fold("")(n => s"\n  - _${n}_")
        }
        s"\n\n**$head**\n${lines.mkString("\n")}"
      }
      _root_.com.histmersion.api.automation.Queue.MarkdownResult(s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}")
