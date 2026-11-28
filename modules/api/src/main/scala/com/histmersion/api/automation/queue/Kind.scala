package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.json.TwoStates
import com.histmersion.Display
import com.histmersion.Newtype
import com.histmersion.api.automation.Queue.Note
import com.histmersion.api.automation.Queue.topicOf
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.Queue.Done
import com.histmersion.api.automation.Queue.Open
import com.histmersion.api.automation.Queue.FileText
import com.histmersion.api.automation.Queue.Preamble
import com.histmersion.api.automation.Queue.Markdown
import com.histmersion.api.automation.Queue.path
import com.histmersion.api.automation.Queue.InstantKey
import com.histmersion.api.library.Acquire
import com.histmersion.api.automation.Queue.Sep

enum Kind(val label: Kind.Label, val file: Kind.File, val checkboxes: Kind.Checkboxes) derives CanEqual:
  case Acquire extends Kind(Kind.Label("acquire"), Kind.File("ACQUIRE.md"), Kind.Checkboxes.Checkboxes)
  case Tasks extends Kind(Kind.Label("tasks"), Kind.File("TASKS.md"), Kind.Checkboxes.Checkboxes)
  case Log extends Kind(Kind.Label("log"), Kind.File("LOG.md"), Kind.Checkboxes.Plain)

object Kind:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  enum Checkboxes derives CanEqual:
    case Checkboxes, Plain
  object Checkboxes extends TwoStates[Checkboxes]:
    extension (checkboxes: Checkboxes) def checkboxes: Boolean = checkboxes == Checkboxes

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type All = Label
  val All: Label.type = Label

  val all: List[All] = values.toList.map(_.label)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Label): Option[Kind] = values.find(_.label == Label((s).value.trim.toLowerCase))

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r

  private val BoldRe = """^(\*\*.+\*\*)\s*$""".r

  private val CheckRe = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r

  private val BulletRe = """^\s*[-*]\s+(.*)$""".r

  private val DateRe = """^(\d{4}-\d{2}-\d{2}).*""".r

  extension (kind: Kind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(text: FileText): List[QueueAdd] =
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
          case CheckRe(box, body) if (kind.checkboxes).checkboxes =>
            flush()
            last = Some(
              QueueAdd(
                QueueItem.Text(body),
                topicOf(stack),
                status = Some(if box.trim.isEmpty then QueueItem.Status(Open) else QueueItem.Status(Done))
              )
            )
          case BulletRe(body) if !(kind.checkboxes).checkboxes =>
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  private enum Block:
    case Heading(line: String)
    case Item(lines: String)
    case Note(lines: String)

  extension (kind: Kind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def render(items: List[QueueItem]): Markdown =
      val (preamble, rest) = items.partition(_.topic.isEmpty)
      val groups = rest
        .groupBy(_.topic)
        .toList
        .sortBy { (_, is) =>
          val minId = is.map(_.id).minOption.fold(0)(i => (i).value)
          if (kind.checkboxes).checkboxes then (0L, minId.toLong)
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bullet(kind: Kind, i: QueueItem): String =
    val marker =
      if !(kind.checkboxes).checkboxes then "- " else if i.status == QueueItem.Status(Done) then "- [x] " else "- [ ] "
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def instantKey(s: QueueItem.CreatedAt): InstantKey =
    InstantKey(scala.util.Try(java.time.Instant.parse((s).value).toEpochMilli).getOrElse(0L))

  extension (kind: Kind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(
        items: List[QueueItem],
        notes: Map[QueueItem.Id, api.library.Acquire.Checks.AcquireCheck.Said] = Map.empty
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
              if !(kind.checkboxes).checkboxes then "" else if i.status == QueueItem.Status(Done) then "[x] " else "[ ] "
            val why = i.reason.map(r => s" — _${r}_").getOrElse("")
            s"- $box${(i.text).value.linesIterator.mkString(" ")}$why  `#${i.id}`" +
              notes.get(i.id).fold("")(n => s"\n  - _${n}_")
          }
          s"\n\n**$head**\n${lines.mkString("\n")}"
        }
        Markdown(s"### ${kind.label} (${shown.size} item(s))${blocks.mkString}")
