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
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  final case class QueueImport(text: com.histmersion.api.automation.Queue.QueueItem.Text, replace: QueueImport.Replace = true) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

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


  enum Kind(val label: String, val file: String, val checkboxes: Boolean):
    case Acquire extends Kind("acquire", "ACQUIRE.md", true)
    case Tasks extends Kind("tasks", "TASKS.md", true)
    case Log extends Kind("log", "LOG.md", false)

  object Kind:
    val all: List[String] = values.toList.map(_.label)
    def parse(s: String): Option[Kind] = values.find(_.label == s.trim.toLowerCase)

  val Open = "open"
  val Done = "done"
  val Note = "note"

  val Statuses: List[String] = List(Open, Done, Note)

  def statusOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None                            => Right(None)
      case Some(s) if Statuses.contains(s) => Right(Some(s))
      case Some(s)                         => Left(s"unknown status '$s' — one of: ${Statuses.mkString(", ")}")

  def textOf(raw: Option[String]): Either[String, Option[String]] =
    raw match
      case Some(t) if t.trim.isEmpty => Left("queue item text is empty")
      case other                     => Right(other)

  def fulfilledReason(prev: Option[String], citekey: String): String =
    val tag = s"ingested as [[$citekey]]"
    prev.map(_.trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => p
      case Some(p)                    => s"$p · $tag"
      case None                       => tag

  def fulfil(item: QueueItem, citekey: String, by: Option[String] = None): QueuePatch =
    QueuePatch(status = Some(Done), reason = Some(fulfilledReason(item.reason, citekey)), closedBy = by)

  def fulfillable(id: Int, found: Option[QueueItem]): Either[String, QueueItem] =
    found match
      case None                                    => Left(s"no acquisition item $id")
      case Some(i) if i.list != Kind.Acquire.label =>
        Left(s"item $id is on the ${i.list} list, not the acquisition list")
      case Some(i) if i.status == Done => Left(s"acquisition item $id is already done")
      case Some(i) if i.status != Open => Left(s"item $id is not an open acquisition item")
      case Some(i)                     => Right(i)

  def itemGoals(own: List[(Int, GoalRef)], linked: List[(Int, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  def applyPatch(cur: QueueItem, p: QueuePatch): QueueItem =
    def clearable(v: Option[String], old: Option[String]) = v.fold(old)(x => Some(x.trim).filter(_.nonEmpty))
    val status = p.status.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(cur.status)
    val closedBy =
      if status != Done then None
      else p.closedBy.map(_.trim).filter(_.nonEmpty).orElse(Option.when(cur.status == Done)(cur.closedBy).flatten)
    cur.modify(
      _.status).setTo(status).modify(_.text).setTo(p.text.getOrElse(cur.text)).modify(_.topic).setTo(clearable(p.topic, cur.topic)).modify(_.reason).setTo(clearable(p.reason, cur.reason)).modify(_.goal).setTo(clearable(p.goal, cur.goal)).modify(_.closedBy).setTo(closedBy
    )

  def createdAtOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        scala.util
          .Try(java.time.Instant.parse(s))
          .toEither
          .map(i => Some(i.toString))
          .left
          .map(_ => s"created_at '$s' is not an ISO-8601 instant (e.g. 2026-08-25T00:00:00Z)")

  val Sep = " > "

  def path(topic: Option[String]): Vector[String] =
    topic.map(_.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty)).getOrElse(Vector.empty)

  def topicOf(p: Vector[String]): Option[String] = if p.isEmpty then None else Some(p.mkString(Sep))

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r
  private val BoldRe = """^(\*\*.+\*\*)\s*$""".r
  private val CheckRe = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r
  private val BulletRe = """^\s*[-*]\s+(.*)$""".r

  private val DateRe = """^(\d{4}-\d{2}-\d{2}).*""".r

  def parse(kind: Kind, text: String): List[QueueAdd] =
    val out = List.newBuilder[QueueAdd]
    var stack = Vector.empty[String]
    val note = List.newBuilder[String]
    var last: Option[QueueAdd] = None

    def flushItem(): Unit =
      last.foreach(i => out += i)
      last = None

    def flushNote(): Unit =
      val lines = trimBlank(note.result())
      note.clear()
      if lines.nonEmpty then out += QueueAdd(lines.mkString("\n"), topicOf(stack), status = Some(Note))

    def flush(): Unit = { flushItem(); flushNote() }

    def push(segment: String, index: Int): Unit =
      val i = math.min(index, stack.length)
      stack = stack.take(i) :+ segment

    text.linesIterator.foreach { line =>
      line match
        case HeadingRe(hashes, title) =>
          flush()
          push(title, hashes.length - 2)
        case BoldRe(bold) =>
          flush()
          val depth = if stack.lastOption.exists(isBold) then stack.length - 1 else stack.length
          push(bold, depth)
        case CheckRe(box, body) if kind.checkboxes =>
          flush()
          last = Some(QueueAdd(body, topicOf(stack), status = Some(if box.trim.isEmpty then Open else Done)))
        case BulletRe(body) if !kind.checkboxes =>
          flush()
          last = Some(QueueAdd(body, topicOf(stack), status = Some(Open), createdAt = entryDate(stack)))
        case l =>
          if last.isDefined && l.nonEmpty && l.head.isWhitespace then
            last = last.map(i => i.modify(_.text).using(_ + "\n" + l))
          else
            flushItem()
            note += l
    }
    flush()
    out.result()

  private def isBold(s: String): Boolean = s.startsWith("**") && s.endsWith("**")

  private def entryDate(stack: Vector[String]): Option[String] =
    stack.headOption.flatMap {
      case DateRe(d) => Some(s"${d}T00:00:00Z")
      case _         => None
    }

  private def trimBlank(ls: List[String]): List[String] =
    ls.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse

  private def defaultPreamble(kind: Kind): String = kind match
    case Kind.Acquire =>
      "# 📥 ACQUIRE — the acquisition queue\n\nWhat to buy / borrow / download → Calibre. " +
        "`[ ]` = to get · `[x]` = done.\n\n---"
    case Kind.Tasks =>
      "# 🛠️ TASKS — the agent's backlog\n\nWhat is left to do. `[ ]` = open · `[x]` = done.\n\n---"
    case Kind.Log =>
      "# 📓 LOG — project worklog\n\nWhat was done and when, newest first.\n\n---"

  private enum Block:
    case Heading(line: String)
    case Item(lines: String)
    case Note(lines: String)

  def render(kind: Kind, items: List[QueueItem]): String =
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
    val marker = if !kind.checkboxes then "- " else if i.status == Done then "- [x] " else "- [ ] "
    val lines = i.text.linesIterator.toList
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
    sb.result()

  private def instantKey(s: String): Long =
    scala.util.Try(java.time.Instant.parse(s).toEpochMilli).getOrElse(0L)

  /** A list's open items by topic; `notes` is a line under an item, by its id (an ACQUIRE item's last check). */
  def markdown(kind: Kind, items: List[QueueItem], notes: Map[Int, String] = Map.empty): String =
    val shown = items.filterNot(_.status == Note)
    if shown.isEmpty then s"_${kind.label}: nothing queued._"
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
      s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}"
