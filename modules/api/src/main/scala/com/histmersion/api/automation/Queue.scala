package com.histmersion.api.automation
import hearth.kindlings.optics.syntax.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

object Queue:
  type Export = Export.T
  object Export extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileText = FileText.T
  object FileText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstantKey = InstantKey.T
  object InstantKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preamble = Preamble.T
  object Preamble extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    export com.histmersion.api.automation.Queue.Kind.{Label as List}
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topic = Topic.T
    object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
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
      job: Option[com.histmersion.api.SpatialHierarchyResult.Job.Id] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
  object QueueAdd:


    export com.histmersion.api.automation.Queue.QueueItem.CreatedAt

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


    export com.histmersion.api.automation.Queue.QueueItem.ClosedBy

    given Codec.AsObject[QueuePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[QueuePatch])

  final case class QueueImport(text: com.histmersion.api.automation.Queue.QueueItem.Text, replace: QueueImport.Replace = _root_.com.histmersion.api.automation.Queue.QueueImport.Replace(true)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object QueueImport:
    type Replace = Replace.T
    object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class QueueImported(list: QueueImported.List, items: QueueImported.Items, notes: QueueImported.Notes, replaced: QueueImported.Replaced)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QueueImported:
    export com.histmersion.api.automation.Queue.Kind.{Label as List}
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

    type All = _root_.com.histmersion.api.automation.Queue.Kind.Label
    val All: _root_.com.histmersion.api.automation.Queue.Kind.Label.type = _root_.com.histmersion.api.automation.Queue.Kind.Label

    val all: List[com.histmersion.api.automation.Queue.Kind.All] = values.toList.map(_.label)
    def parse(s: com.histmersion.api.automation.Queue.Kind.Label): Option[Kind] = values.find(_.label == _root_.com.histmersion.api.automation.Queue.Kind.Label((s).value.trim.toLowerCase))

  val Open = "open"
  val Done = "done"
  val Note = "note"

  val Statuses: List[com.histmersion.api.automation.Queue.QueueItem.Status] = List(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open), _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done), _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Note))

  def statusOf(raw: Option[com.histmersion.api.automation.Queue.QueueItem.Status]): Either[com.histmersion.api.automation.Queue.Problem, Option[com.histmersion.api.automation.Queue.QueueItem.Status]] =
    raw.map((_).value.trim.toLowerCase).filter(_.nonEmpty).map(x => _root_.com.histmersion.api.automation.Queue.QueueItem.Status(x)) match
      case None                            => Right(None)
      case Some(s) if Statuses.contains(s) => Right(Some(s))
      case Some(s)                         => Left(_root_.com.histmersion.api.automation.Queue.Problem(s"unknown status '$s' — one of: ${Statuses.mkString(", ")}"))

  def textOf(raw: Option[com.histmersion.api.automation.Queue.QueueItem.Text]): Either[com.histmersion.api.automation.Queue.Problem, Option[com.histmersion.api.automation.Queue.QueueItem.Text]] =
    raw match
      case Some(t) if (t).value.trim.isEmpty => Left(_root_.com.histmersion.api.automation.Queue.Problem("queue item text is empty"))
      case other                     => Right(other)

  def fulfilledReason(prev: Option[com.histmersion.api.automation.Queue.QueueItem.Reason], citekey: com.histmersion.api.library.Citekey): com.histmersion.api.automation.Queue.QueueItem.Reason =
    val tag = s"ingested as [[$citekey]]"
    prev.map((_).value.trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => _root_.com.histmersion.api.automation.Queue.QueueItem.Reason(p)
      case Some(p)                    => _root_.com.histmersion.api.automation.Queue.QueueItem.Reason(s"$p · $tag")
      case None                       => _root_.com.histmersion.api.automation.Queue.QueueItem.Reason(tag)

  def fulfil(item: QueueItem, citekey: com.histmersion.api.library.Citekey, by: Option[com.histmersion.api.automation.Queue.QueueItem.ClosedBy] = None): QueuePatch =
    QueuePatch(status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done)), reason = Some(fulfilledReason(item.reason, citekey)), closedBy = by)

  def fulfillable(id: com.histmersion.api.automation.Queue.QueueItem.Id, found: Option[QueueItem]): Either[com.histmersion.api.automation.Queue.Problem, QueueItem] =
    found match
      case None                                    => Left(_root_.com.histmersion.api.automation.Queue.Problem(s"no acquisition item $id"))
      case Some(i) if i.list != Kind.Acquire.label =>
        Left(_root_.com.histmersion.api.automation.Queue.Problem(s"item $id is on the ${i.list} list, not the acquisition list"))
      case Some(i) if i.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done) => Left(_root_.com.histmersion.api.automation.Queue.Problem(s"acquisition item $id is already done"))
      case Some(i) if i.status != _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open) => Left(_root_.com.histmersion.api.automation.Queue.Problem(s"item $id is not an open acquisition item"))
      case Some(i)                     => Right(i)

  def itemGoals(own: List[(com.histmersion.api.automation.Queue.QueueItem.Id, GoalRef)], linked: List[(com.histmersion.api.automation.Queue.QueueItem.Id, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  def applyPatch(cur: QueueItem, p: QueuePatch): QueueItem =
    def clearable[A](v: Option[A], old: Option[A])(text: A => String, make: String => A): Option[A] = v.fold(old)(x => Some(text(x).trim).filter(_.nonEmpty).map(make))
    val status = p.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(x => _root_.com.histmersion.api.automation.Queue.QueueItem.Status(x)).getOrElse(cur.status)
    val closedBy =
      if status != _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done) then None
      else p.closedBy.map(_.value.trim).filter(_.nonEmpty).map(x => _root_.com.histmersion.api.automation.Queue.QueueItem.ClosedBy(x)).orElse(Option.when(cur.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done))(cur.closedBy).flatten)
    cur.modify(
      _.status).setTo(status).modify(_.text).setTo(p.text.getOrElse(cur.text)).modify(_.topic).setTo(clearable(p.topic, cur.topic)(t => (t).value, x => _root_.com.histmersion.api.automation.Queue.QueueItem.Topic(x))).modify(_.reason).setTo(clearable(p.reason, cur.reason)(t => (t).value, x => _root_.com.histmersion.api.automation.Queue.QueueItem.Reason(x))).modify(_.goal).setTo(clearable(p.goal, cur.goal)(t => (t).value, x => _root_.com.histmersion.api.automation.Goal.Slug(x))).modify(_.closedBy).setTo(closedBy
    )

  def createdAtOf(raw: Option[com.histmersion.api.automation.Queue.QueueItem.CreatedAt]): Either[com.histmersion.api.automation.Queue.Problem, Option[com.histmersion.api.automation.Queue.QueueItem.CreatedAt]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        ((scala.util
          .Try(java.time.Instant.parse(s))
          .toEither
          .map(i => Some(i.toString))
          .left
          .map(_ => s"created_at '$s' is not an ISO-8601 instant (e.g. 2026-08-25T00:00:00Z)")).left.map(refactorError => _root_.com.histmersion.api.automation.Queue.Problem(refactorError))).map(refactorValue2 => (refactorValue2).map(refactorValue3 => _root_.com.histmersion.api.automation.Queue.QueueItem.CreatedAt(refactorValue3)))

  val Sep = " > "

  def path(topic: Option[com.histmersion.api.automation.Queue.QueueItem.Topic]): Vector[String] =
    topic.map((_).value.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty)).getOrElse(Vector.empty)

  def topicOf(p: Vector[String]): Option[com.histmersion.api.automation.Queue.QueueItem.Topic] = if p.isEmpty then None else Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Topic(p.mkString(Sep)))

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r
  private val BoldRe = """^(\*\*.+\*\*)\s*$""".r
  private val CheckRe = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r
  private val BulletRe = """^\s*[-*]\s+(.*)$""".r

  private val DateRe = """^(\d{4}-\d{2}-\d{2}).*""".r

  def parse(kind: Kind, text: com.histmersion.api.automation.Queue.FileText): List[QueueAdd] =
    val out = List.newBuilder[QueueAdd]
    var stack = Vector.empty[String]
    val note = List.newBuilder[String]
    var last: Option[QueueAdd] = None

    def flushItem(): Unit =
      last.foreach(i => out += i)
      last = None

    def flushNote(): Unit =
      val lines = trimBlank((note.result()).map(refactorValue8 => (refactorValue8)))
      note.clear()
      if lines.nonEmpty then out += QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(lines.mkString("\n")), topicOf(stack), status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Note)))

    def flush(): Unit = { flushItem(); flushNote() }

    def push(segment: String, index: Int): Unit =
      val i = math.min((index), stack.length)
      stack = stack.take(i) :+ (segment)

    (text).value.linesIterator.foreach { line =>
      line match
        case HeadingRe(hashes, title) =>
          flush()
          push((title), (hashes.length - 2))
        case BoldRe(bold) =>
          flush()
          val depth = if stack.lastOption.exists((isBold)) then stack.length - 1 else stack.length
          push((bold), (depth))
        case CheckRe(box, body) if (kind.checkboxes).value =>
          flush()
          last = Some(QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(body), topicOf(stack), status = Some(if box.trim.isEmpty then _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open) else _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done))))
        case BulletRe(body) if !(kind.checkboxes).value =>
          flush()
          last = Some(QueueAdd(_root_.com.histmersion.api.automation.Queue.QueueItem.Text(body), topicOf(stack), status = Some(_root_.com.histmersion.api.automation.Queue.QueueItem.Status(Open)), createdAt = entryDate(stack)))
        case l =>
          if last.isDefined && l.nonEmpty && l.head.isWhitespace then
            last = last.map(i => i.modify(_.text).using(t => _root_.com.histmersion.api.automation.Queue.QueueItem.Text((t).value + "\n" + l)))
          else
            flushItem()
            note += l
    }
    flush()
    out.result()

  private def isBold(s: String): Boolean = ((s).startsWith("**") && (s).endsWith("**"))

  private def entryDate(stack: Vector[String]): Option[com.histmersion.api.automation.Queue.QueueItem.CreatedAt] =
    stack.headOption.flatMap {
      case DateRe(d) => Some(_root_.com.histmersion.api.automation.Queue.QueueItem.CreatedAt(s"${d}T00:00:00Z"))
      case _         => None
    }

  private def trimBlank(ls: List[String]): List[String] =
    (ls.dropWhile((_).trim.isEmpty).reverse.dropWhile((_).trim.isEmpty).reverse).map(refactorValue15 => ((refactorValue15)))

  private def defaultPreamble(kind: Kind): com.histmersion.api.automation.Queue.Preamble = kind match
    case Kind.Acquire =>
      _root_.com.histmersion.api.automation.Queue.Preamble("# 📥 ACQUIRE — the acquisition queue\n\nWhat to buy / borrow / download → Calibre. " +
        "`[ ]` = to get · `[x]` = done.\n\n---")
    case Kind.Tasks =>
      _root_.com.histmersion.api.automation.Queue.Preamble("# 🛠️ TASKS — the agent's backlog\n\nWhat is left to do. `[ ]` = open · `[x]` = done.\n\n---")
    case Kind.Log =>
      _root_.com.histmersion.api.automation.Queue.Preamble("# 📓 LOG — project worklog\n\nWhat was done and when, newest first.\n\n---")

  private enum Block:
    case Heading(line: String)
    case Item(lines: String)
    case Note(lines: String)

  def render(kind: Kind, items: List[QueueItem]): com.histmersion.api.automation.Queue.RenderResult =
    val (preamble, rest) = items.partition(_.topic.isEmpty)
    val groups = rest
      .groupBy(_.topic)
      .toList
      .sortBy { (_, is) =>
        val minId = is.map(_.id).minOption.fold(0)(i => (i).value)
        if (kind.checkboxes).value then (0L, minId.toLong)
        else (-is.flatMap(_.createdAt).maxOption.map(instantKey).fold(0L)(k => (k).value), minId.toLong)
      }
      .map((topic, is) => (path(topic), is.sortBy(_.id)))

    val blocks = List.newBuilder[Block]
    val head = preamble.sortBy(_.id).map(_.text).filter(_.value.trim.nonEmpty)
    if head.isEmpty then blocks += Block.Note((defaultPreamble(kind)).value)
    else head.foreach(t => blocks += Block.Note((t).value))

    var prev = Vector.empty[String]
    groups.foreach { (p, is) =>
      headingLines(prev, p).foreach(h => blocks += h)
      prev = p
      is.foreach(i => blocks += (if i.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Note) then Block.Note((i.text).value) else Block.Item(bullet(kind, i))))
    }
    _root_.com.histmersion.api.automation.Queue.RenderResult((join(blocks.result())))

  private def headingLines(prev: Vector[String], next: Vector[String]): List[Block] =
    if next.isEmpty then Nil
    else
      val firstDiff = next.indices.find(i => prev.lift(i) != Some(next(i)))
      val from = firstDiff.getOrElse(next.length - 1)
      (from until next.length).toList.map { i =>
        val seg = next(i)
        if isBold(seg) then Block.Heading(seg)
        else Block.Heading(s"${"#" * (i + 2)} $seg")
      }

  private def bullet(kind: Kind, i: QueueItem): String =
    val marker = if !(kind.checkboxes).value then "- " else if i.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done) then "- [x] " else "- [ ] "
    val lines = (i.text).value.linesIterator.toList
    (marker + lines.headOption.getOrElse("")) + lines.drop(1).map("\n" + _).mkString

  private def join(blocks: List[Block]): String =
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
    (sb.result())

  private def instantKey(s: com.histmersion.api.automation.Queue.QueueItem.CreatedAt): com.histmersion.api.automation.Queue.InstantKey =
    _root_.com.histmersion.api.automation.Queue.InstantKey(scala.util.Try(java.time.Instant.parse((s).value).toEpochMilli).getOrElse(0L))

  /** A list's open items by topic; `notes` is a line under an item, by its id (an ACQUIRE item's last check). */
  def markdown(kind: Kind, items: List[QueueItem], notes: Map[com.histmersion.api.automation.Queue.QueueItem.Id, com.histmersion.api.automation.Queue.QueueItem.Reason] = Map.empty): com.histmersion.api.automation.Queue.MarkdownResult =
    val shown = items.filterNot(_.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Note))
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
          val box = if !(kind.checkboxes).value then "" else if i.status == _root_.com.histmersion.api.automation.Queue.QueueItem.Status(Done) then "[x] " else "[ ] "
          val why = i.reason.map(r => s" — _${r}_").getOrElse("")
          s"- $box${(i.text).value.linesIterator.mkString(" ")}$why  `#${i.id}`" +
            notes.get(i.id).fold("")(n => s"\n  - _${n}_")
        }
        s"\n\n**$head**\n${lines.mkString("\n")}"
      }
      _root_.com.histmersion.api.automation.Queue.MarkdownResult(s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}")
