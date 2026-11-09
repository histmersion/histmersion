package com.histmersion
package api.automation
import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.library.Citekey

object Queue:
  type Export = Export.T
  object Export extends Newtype[String, Pure]
  type FileText = FileText.T
  object FileText extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[Int, Pure]
  type InstantKey = InstantKey.T
  object InstantKey extends Newtype[Long, Pure]
  type Preamble = Preamble.T
  object Preamble extends Newtype[String, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  final case class QueueItem(
      id: QueueItem.Id,
      list: QueueItem.List,
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic],
      status: QueueItem.Status,
      reason: Option[Problem] = None,
      createdAt: Option[QueueItem.CreatedAt] = None,
      doneAt: Option[QueueItem.DoneAt] = None,
      goal: Option[Goal.Slug] = None,
      createdBy: Option[QueueItem.CreatedBy] = None,
      closedBy: Option[QueueItem.ClosedBy] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QueueItem:
    type Id = Id.T
    object Id extends Newtype[Int, Pure]
    export Kind.Label as List
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Topic = Topic.T
    object Topic extends Newtype[String, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type DoneAt = DoneAt.T
    object DoneAt extends Newtype[String, Pure]
    type CreatedBy = CreatedBy.T
    object CreatedBy extends Newtype[String, Pure]
    type ClosedBy = ClosedBy.T
    object ClosedBy extends Newtype[String, Pure]

  final case class ItemGoals(item: QueueItem.Id, goals: List[GoalRef])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class GoalRef(slug: Goal.Slug, title: Goal.Title)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class QueueAdd(
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic] = None,
      status: Option[QueueItem.Status] = None,
      reason: Option[Problem] = None,
      createdAt: Option[QueueAdd.CreatedAt] = None,
      goal: Option[Goal.Slug] = None,
      job: Option[SpatialHierarchyResult.Job.Id] = None
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object QueueAdd:

    export QueueItem.CreatedAt

    given Codec.AsObject[QueueAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[QueueAdd])

  final case class QueuePatch(
      status: Option[QueueItem.Status] = None,
      text: Option[QueueItem.Text] = None,
      topic: Option[QueueItem.Topic] = None,
      reason: Option[Problem] = None,
      goal: Option[Goal.Slug] = None,
      closedBy: Option[QueuePatch.ClosedBy] = None
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object QueuePatch:

    export QueueItem.ClosedBy

    given Codec.AsObject[QueuePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[QueuePatch])

  final case class QueueImport(text: QueueItem.Text, replace: QueueImport.Replace = QueueImport.Replace(true))
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QueueImport:
    type Replace = Replace.T
    object Replace extends Newtype[Boolean, Pure]

  final case class QueueImported(
      list: QueueImported.List,
      items: QueueImported.Items,
      notes: QueueImported.Notes,
      replaced: QueueImported.Replaced
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QueueImported:
    export Kind.Label as List
    type Items = Items.T
    object Items extends Newtype[Int, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[Int, Pure]
    type Replaced = Replaced.T
    object Replaced extends Newtype[Int, Pure]

  enum Kind(val label: Kind.Label, val file: Kind.File, val checkboxes: Kind.Checkboxes) derives CanEqual:
    case Acquire extends Kind(Kind.Label("acquire"), Kind.File("ACQUIRE.md"), Kind.Checkboxes(true))
    case Tasks extends Kind(Kind.Label("tasks"), Kind.File("TASKS.md"), Kind.Checkboxes(true))
    case Log extends Kind(Kind.Label("log"), Kind.File("LOG.md"), Kind.Checkboxes(false))

  object Kind:
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type File = File.T
    object File extends Newtype[String, Pure]
    type Checkboxes = Checkboxes.T
    object Checkboxes extends Newtype[Boolean, Pure]

    type All = Label
    val All: Label.type = Label

    val all: List[All] = values.toList.map(_.label)
    def parse(s: Label): Option[Kind] = values.find(_.label == Label((s).value.trim.toLowerCase))

  val Open = "open"
  val Done = "done"
  val Note = "note"

  val Statuses: List[QueueItem.Status] = List(QueueItem.Status(Open), QueueItem.Status(Done), QueueItem.Status(Note))

  def statusOf(raw: Option[QueueItem.Status]): Either[Problem, Option[QueueItem.Status]] =
    raw.map((_).value.trim.toLowerCase).filter(_.nonEmpty).map(x => QueueItem.Status(x)) match
      case None                            => Right(None)
      case Some(s) if Statuses.contains(s) => Right(Some(s))
      case Some(s)                         => Left(Problem(s"unknown status '$s' — one of: ${Statuses.mkString(", ")}"))

  def textOf(raw: Option[QueueItem.Text]): Either[Problem, Option[QueueItem.Text]] =
    raw match
      case Some(t) if (t).value.trim.isEmpty => Left(Problem("queue item text is empty"))
      case other                             => Right(other)

  def fulfilledReason(prev: Option[Problem], citekey: Citekey): Problem =
    val tag = s"ingested as [[$citekey]]"
    prev.map((_).value.trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => Problem(p)
      case Some(p)                    => Problem(s"$p · $tag")
      case None                       => Problem(tag)

  def fulfil(item: QueueItem, citekey: Citekey, by: Option[QueueItem.ClosedBy] = None): QueuePatch =
    QueuePatch(
      status = Some(QueueItem.Status(Done)),
      reason = Some(fulfilledReason(item.reason, citekey)),
      closedBy = by
    )

  def fulfillable(id: QueueItem.Id, found: Option[QueueItem]): Either[Problem, QueueItem] =
    found match
      case None                                    => Left(Problem(s"no acquisition item $id"))
      case Some(i) if i.list != Kind.Acquire.label =>
        Left(Problem(s"item $id is on the ${i.list} list, not the acquisition list"))
      case Some(i) if i.status == QueueItem.Status(Done) => Left(Problem(s"acquisition item $id is already done"))
      case Some(i) if i.status != QueueItem.Status(Open) => Left(Problem(s"item $id is not an open acquisition item"))
      case Some(i)                                       => Right(i)

  def itemGoals(own: List[(QueueItem.Id, GoalRef)], linked: List[(QueueItem.Id, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  def applyPatch(cur: QueueItem, p: QueuePatch): QueueItem =
    def clearable[A](v: Option[A], old: Option[A])(text: A => String, make: String => A): Option[A] =
      v.fold(old)(x => Some(text(x).trim).filter(_.nonEmpty).map(make))
    val status =
      p.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(x => QueueItem.Status(x)).getOrElse(cur.status)
    val closedBy =
      if status != QueueItem.Status(Done) then None
      else
        p.closedBy
          .map(_.value.trim)
          .filter(_.nonEmpty)
          .map(x => QueueItem.ClosedBy(x))
          .orElse(Option.when(cur.status == QueueItem.Status(Done))(cur.closedBy).flatten)
    cur
      .modify(_.status)
      .setTo(status)
      .modify(_.text)
      .setTo(p.text.getOrElse(cur.text))
      .modify(_.topic)
      .setTo(clearable(p.topic, cur.topic)(t => (t).value, x => QueueItem.Topic(x)))
      .modify(_.reason)
      .setTo(clearable(p.reason, cur.reason)(t => (t).value, x => Problem(x)))
      .modify(_.goal)
      .setTo(clearable(p.goal, cur.goal)(t => (t).value, x => Goal.Slug(x)))
      .modify(_.closedBy)
      .setTo(closedBy)

  def createdAtOf(raw: Option[QueueItem.CreatedAt]): Either[Problem, Option[QueueItem.CreatedAt]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        ((scala.util
          .Try(java.time.Instant.parse(s))
          .toEither
          .map(i => Some(i.toString))
          .left
          .map(_ => s"created_at '$s' is not an ISO-8601 instant (e.g. 2026-08-25T00:00:00Z)"))
          .left
          .map(refactorError => Problem(refactorError)))
          .map(refactorValue2 => (refactorValue2).map(refactorValue3 => QueueItem.CreatedAt(refactorValue3)))

  val Sep = " > "

  def path(topic: Option[QueueItem.Topic]): Vector[String] =
    topic
      .map((_).value.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty))
      .getOrElse(Vector.empty)

  def topicOf(p: Vector[String]): Option[QueueItem.Topic] =
    if p.isEmpty then None else Some(QueueItem.Topic(p.mkString(Sep)))

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r
  private val BoldRe = """^(\*\*.+\*\*)\s*$""".r
  private val CheckRe = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r
  private val BulletRe = """^\s*[-*]\s+(.*)$""".r

  private val DateRe = """^(\d{4}-\d{2}-\d{2}).*""".r

  def parse(kind: Kind, text: FileText): List[QueueAdd] =
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
      if lines.nonEmpty then
        out += QueueAdd(QueueItem.Text(lines.mkString("\n")), topicOf(stack), status = Some(QueueItem.Status(Note)))

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
          last = Some(
            QueueAdd(
              QueueItem.Text(body),
              topicOf(stack),
              status = Some(if box.trim.isEmpty then QueueItem.Status(Open) else QueueItem.Status(Done))
            )
          )
        case BulletRe(body) if !(kind.checkboxes).value =>
          flush()
          last = Some(
            QueueAdd(
              QueueItem.Text(body),
              topicOf(stack),
              status = Some(QueueItem.Status(Open)),
              createdAt = entryDate(stack)
            )
          )
        case l =>
          if last.isDefined && l.nonEmpty && l.head.isWhitespace then
            last = last.map(i => i.modify(_.text).using(t => QueueItem.Text((t).value + "\n" + l)))
          else
            flushItem()
            note += l
    }
    flush()
    out.result()

  private def isBold(s: String): Boolean = ((s).startsWith("**") && (s).endsWith("**"))

  private def entryDate(stack: Vector[String]): Option[QueueItem.CreatedAt] =
    stack.headOption.flatMap {
      case DateRe(d) => Some(QueueItem.CreatedAt(s"${d}T00:00:00Z"))
      case _         => None
    }

  private def trimBlank(ls: List[String]): List[String] =
    (ls
      .dropWhile((_).trim.isEmpty)
      .reverse
      .dropWhile((_).trim.isEmpty)
      .reverse)
      .map(refactorValue15 => ((refactorValue15)))

  private def defaultPreamble(kind: Kind): Preamble = kind match
    case Kind.Acquire =>
      Preamble(
        "# 📥 ACQUIRE — the acquisition queue\n\nWhat to buy / borrow / download → Calibre. " +
          "`[ ]` = to get · `[x]` = done.\n\n---"
      )
    case Kind.Tasks =>
      Preamble("# 🛠️ TASKS — the agent's backlog\n\nWhat is left to do. `[ ]` = open · `[x]` = done.\n\n---")
    case Kind.Log =>
      Preamble("# 📓 LOG — project worklog\n\nWhat was done and when, newest first.\n\n---")

  private enum Block derives CanEqual:
    case Heading(line: String)
    case Item(lines: String)
    case Note(lines: String)

  def render(kind: Kind, items: List[QueueItem]): Markdown =
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
      is.foreach(i =>
        blocks += (if i.status == QueueItem.Status(Note) then Block.Note((i.text).value)
                   else Block.Item(bullet(kind, i)))
      )
    }
    Markdown((join(blocks.result())))

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
    val marker =
      if !(kind.checkboxes).value then "- " else if i.status == QueueItem.Status(Done) then "- [x] " else "- [ ] "
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

  private def instantKey(s: QueueItem.CreatedAt): InstantKey =
    InstantKey(scala.util.Try(java.time.Instant.parse((s).value).toEpochMilli).getOrElse(0L))

  /** A list's open items by topic; `notes` is a line under an item, by its id (an ACQUIRE item's last check). */
  def markdown(
      kind: Kind,
      items: List[QueueItem],
      notes: Map[QueueItem.Id, Problem] = Map.empty
  ): Markdown =
    val shown = items.filterNot(_.status == QueueItem.Status(Note))
    if shown.isEmpty then Markdown(s"_${kind.label}: nothing queued._")
    else
      val groups = shown.groupBy(_.topic).toList.sortBy((_, is) => is.map(_.id).min)
      val blocks = groups.map { (topic, is) =>
        val head = if topic.isEmpty then "(no topic)"
        else
          path(topic)
            .map(seg => if isBold(seg) then seg.drop(2).dropRight(2) else seg)
            .mkString(Sep)
        val lines = is.sortBy(_.id).map { i =>
          val box =
            if !(kind.checkboxes).value then "" else if i.status == QueueItem.Status(Done) then "[x] " else "[ ] "
          val why = i.reason.map(r => s" — _${r}_").getOrElse("")
          s"- $box${(i.text).value.linesIterator.mkString(" ")}$why  `#${i.id}`" +
            notes.get(i.id).fold("")(n => s"\n  - _${n}_")
        }
        s"\n\n**$head**\n${lines.mkString("\n")}"
      }
      Markdown(s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}")
