package com.histmersion.api

import hearth.kindlings.optics.syntax.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

object Queue:

  final case class QueueItem(
      id: Int,
      list: String,
      text: String,
      topic: Option[String],
      status: String,
      reason: Option[String] = None,
      createdAt: Option[String] = None,
      doneAt: Option[String] = None,
      goal: Option[String] = None,
      createdBy: Option[String] = None,
      closedBy: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object QueueItem:
    given Schema[QueueItem] = summon[KindlingsSchema[QueueItem]].schema

  final case class QueueListing(
      list: String,
      items: List[QueueItem],
      checks: List[AcquireChecks.AcquireCheck] = Nil,
      goals: List[ItemGoals] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object QueueListing:
    given Schema[QueueListing] = summon[KindlingsSchema[QueueListing]].schema

  final case class ItemGoals(item: Int, goals: List[GoalRef]) derives KindlingsCodecAsObject, KindlingsSchema
  object ItemGoals:
    given Schema[ItemGoals] = summon[KindlingsSchema[ItemGoals]].schema

  final case class GoalRef(slug: String, title: String) derives KindlingsCodecAsObject, KindlingsSchema
  object GoalRef:
    given Schema[GoalRef] = summon[KindlingsSchema[GoalRef]].schema

  final case class QueueAdd(
      text: String,
      topic: Option[String] = None,
      status: Option[String] = None,
      reason: Option[String] = None,
      createdAt: Option[String] = None,
      goal: Option[String] = None,
      job: Option[String] = None
  ) derives KindlingsSchema
  object QueueAdd:
    given Schema[QueueAdd] = summon[KindlingsSchema[QueueAdd]].schema

    given Codec.AsObject[QueueAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[QueueAdd])

  final case class QueuePatch(
      status: Option[String] = None,
      text: Option[String] = None,
      topic: Option[String] = None,
      reason: Option[String] = None,
      goal: Option[String] = None,
      closedBy: Option[String] = None
  ) derives KindlingsSchema
  object QueuePatch:
    given Schema[QueuePatch] = summon[KindlingsSchema[QueuePatch]].schema

    given Codec.AsObject[QueuePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[QueuePatch])

  final case class QueueImport(text: String, replace: Boolean = true) derives KindlingsCodecAsObject, KindlingsSchema
  object QueueImport:
    given Schema[QueueImport] = summon[KindlingsSchema[QueueImport]].schema

  final case class QueueImported(list: String, items: Int, notes: Int, replaced: Int)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object QueueImported:
    given Schema[QueueImported] = summon[KindlingsSchema[QueueImported]].schema

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
    cur.copy(
      status = status,
      text = p.text.getOrElse(cur.text),
      topic = clearable(p.topic, cur.topic),
      reason = clearable(p.reason, cur.reason),
      goal = clearable(p.goal, cur.goal),
      closedBy = closedBy
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

  def markdown(kind: Kind, items: List[QueueItem], checks: List[AcquireChecks.AcquireCheck] = Nil): String =
    val checked = checks.map(c => c.item -> c).toMap
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
            checked.get(i.id).fold("")(c => s"\n  - _${c.reason}_")
        }
        s"\n\n**$head**\n${lines.mkString("\n")}"
      }
      s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}"
