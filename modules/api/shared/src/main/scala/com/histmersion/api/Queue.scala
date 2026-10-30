package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** The bookkeeping queues (plan §5.1 row `bookkeeping`, §5.2 stream 2.8) — the database replacement
  * for the research workspace's three hand-edited files:
  *
  *   - `meta/ACQUIRE.md` — what the USER must buy/borrow/download (grouped by topic, `[ ]`/`[x]`),
  *   - `meta/TASKS.md`   — the AGENT's backlog and maintenance state,
  *   - `meta/LOG.md`     — the chronological worklog, newest entry first.
  *
  * `queue export` renders the three files out of the rows and `queue import` parses them back, so the
  * Obsidian-readable Markdown stays a PROJECTION of the database. Both directions are pure functions
  * of text, unit-tested against the real files copied into `src/test/resources/meta/`.
  *
  * ==The document model==
  * A file is an ordered sequence of blocks under a heading PATH (`topic`, joined with `" > "`):
  *
  *   - a **heading** — `## …`, `### …`, or a whole-line bold group (`**Highest value — get first**`).
  *     A bold segment keeps its `**` markers in the topic path, which is how the renderer knows to
  *     emit it as a bold line rather than as a deeper `#` heading;
  *   - an **item** — `- [ ] text` / `- [x] text` in ACQUIRE and TASKS, a plain `- text` bullet in
  *     LOG; indented continuation lines belong to the item and are preserved verbatim;
  *   - a **note** (`status = 'note'`) — any other run of lines, stored VERBATIM: the file preamble,
  *     a `---` rule, the currency-checkpoint table, a numbered maintenance routine. Notes are never
  *     listed by `queue <list>`; they exist so an imported file survives a round trip with its
  *     hand-written prose intact.
  *
  * ==Whitespace==
  * The renderer is canonical, not byte-preserving: exactly one blank line before every block, except
  * that a heading HUGS the note or item that follows it and consecutive items are not separated. That
  * reproduces the real files as written, apart from where the originals are internally inconsistent.
  */
object Queue:

  // ── wire DTOs ────────────────────────────────────────────────────────────────────────────────

  /** A stored row. `topic` is the `" > "`-joined heading path (absent = the file preamble);
    * `createdAt`/`doneAt` are ISO-8601 UTC strings, as everywhere else on the wire. `goal` = the research
    * goal the item serves; `createdBy` / `closedBy` = the agent run or background job (a job id) that wrote
    * it and that ticked it off — absent for a person's own item. */
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
  ) derives KindlingsCodecAsObject
  object QueueItem:
    given Schema[QueueItem] = KindlingsSchema.derived[QueueItem].schema

  /** `GET /api/queue/{list}` — open items by default, everything with `?all=true`; on the acquisition list,
    * each checked item's last legal re-check (`checks`, by item id); on every list, the research goals that
    * link an item (`goals`, by item id — the goal links of kind `queue-item`). */
  final case class QueueListing(list: String, items: List[QueueItem], checks: List[AcquireChecks.AcquireCheck] = Nil,
      goals: List[ItemGoals] = Nil) derives KindlingsCodecAsObject
  object QueueListing:
    given Schema[QueueListing] = KindlingsSchema.derived[QueueListing].schema

  /** The research goals that link one item: slug and title. */
  final case class ItemGoals(item: Int, goals: List[GoalRef]) derives KindlingsCodecAsObject
  object ItemGoals:
    given Schema[ItemGoals] = KindlingsSchema.derived[ItemGoals].schema

  final case class GoalRef(slug: String, title: String) derives KindlingsCodecAsObject
  object GoalRef:
    given Schema[GoalRef] = KindlingsSchema.derived[GoalRef].schema

  /** `POST /api/queue/{list}` — and, internally, one parsed row on the way in from a file. */
  final case class QueueAdd(
      text: String,
      topic: Option[String] = None,
      status: Option[String] = None,
      reason: Option[String] = None,
      createdAt: Option[String] = None,
      /** The research goal the item serves. */
      goal: Option[String] = None,
      /** The agent run or background job writing it (its job id). */
      job: Option[String] = None
  )
  object QueueAdd:
    given Schema[QueueAdd] = KindlingsSchema.derived[QueueAdd].schema
    /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
    given Codec.AsObject[QueueAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[QueueAdd])

  /** `PATCH /api/queue/items/{id}` — only the parts present are changed. `status = "done"` stamps
    * `done_at`; back to `"open"` clears it. A blank `topic` or `reason` CLEARS that field (an item
    * moved out of every group); a blank `text` is refused, as it is on add. See [[Queue.applyPatch]]. */
  final case class QueuePatch(
      status: Option[String] = None,
      text: Option[String] = None,
      topic: Option[String] = None,
      reason: Option[String] = None,
      /** The research goal the item serves (blank clears it). */
      goal: Option[String] = None,
      /** The agent run or background job that ticks the item off (with `status = "done"`). */
      closedBy: Option[String] = None
  )
  object QueuePatch:
    given Schema[QueuePatch] = KindlingsSchema.derived[QueuePatch].schema
    /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
    given Codec.AsObject[QueuePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[QueuePatch])

  /** `POST /api/queue/{list}/import` — the whole Markdown file, parsed server-side.
    * `replace` (the default) makes import idempotent: the list is emptied first, so importing the
    * same file twice does not double it. */
  final case class QueueImport(text: String, replace: Boolean = true) derives KindlingsCodecAsObject
  object QueueImport:
    given Schema[QueueImport] = KindlingsSchema.derived[QueueImport].schema

  final case class QueueImported(list: String, items: Int, notes: Int, replaced: Int)
      derives KindlingsCodecAsObject
  object QueueImported:
    given Schema[QueueImported] = KindlingsSchema.derived[QueueImported].schema

  // ── the three lists ──────────────────────────────────────────────────────────────────────────

  /** ACQUIRE and TASKS are checkbox lists; LOG is a chronological bullet list. */
  enum Kind(val label: String, val file: String, val checkboxes: Boolean):
    case Acquire extends Kind("acquire", "ACQUIRE.md", true)
    case Tasks   extends Kind("tasks", "TASKS.md", true)
    case Log     extends Kind("log", "LOG.md", false)

  object Kind:
    val all: List[String] = values.toList.map(_.label)
    def parse(s: String): Option[Kind] = values.find(_.label == s.trim.toLowerCase)

  val Open = "open"
  val Done = "done"
  val Note = "note"

  /** The only three values the `status` column may hold. Anything else used to go straight into the
    * INSERT, so `status: "pending"` produced a row no listing would ever return. */
  val Statuses: List[String] = List(Open, Done, Note)

  /** Validate a caller-supplied status. `None` in ⇒ `None` out: "leave it as it is". */
  def statusOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                  => Right(None)
      case Some(s) if Statuses.contains(s)       => Right(Some(s))
      case Some(s) => Left(s"unknown status '$s' — one of: ${Statuses.mkString(", ")}")

  /** Validate a caller-supplied item text: absent ⇒ "leave it", blank ⇒ refused (an item with no
    * text cannot be listed, rendered or ticked off). */
  def textOf(raw: Option[String]): Either[String, Option[String]] =
    raw match
      case Some(t) if t.trim.isEmpty => Left("queue item text is empty")
      case other                     => Right(other)

  // ── fulfilling an acquisition item ───────────────────────────────────────────────────────────

  /** The reason an item fulfilled by a source is ticked off with. A reason the item already had —
    * why it was wanted — is kept in front of it; recording the same source twice adds nothing. */
  def fulfilledReason(prev: Option[String], citekey: String): String =
    val tag = s"ingested as [[$citekey]]"
    prev.map(_.trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => p
      case Some(p)                    => s"$p · $tag"
      case None                       => tag

  /** The ONE write that ticks `item` off as fulfilled by the source `citekey` — made by the web UI's
    * "Link existing source…" and by an OCR job that was started for the item alike. */
  def fulfil(item: QueueItem, citekey: String, by: Option[String] = None): QueuePatch =
    QueuePatch(status = Some(Done), reason = Some(fulfilledReason(item.reason, citekey)), closedBy = by)

  /** Whether an upload may name `id` as the acquisition item it fulfils, given what the store holds
    * under that id: it must exist, be on the acquisition list, and still be open. Checked when the
    * upload is accepted, so a typo fails at once rather than after a long OCR. */
  def fulfillable(id: Int, found: Option[QueueItem]): Either[String, QueueItem] =
    found match
      case None                                       => Left(s"no acquisition item $id")
      case Some(i) if i.list != Kind.Acquire.label    => Left(s"item $id is on the ${i.list} list, not the acquisition list")
      case Some(i) if i.status == Done                => Left(s"acquisition item $id is already done")
      case Some(i) if i.status != Open                => Left(s"item $id is not an open acquisition item")
      case Some(i)                                    => Right(i)

  /** Pure: the goals of each item, by item id — its own goal first, then the goals that link it, each once. */
  def itemGoals(own: List[(Int, GoalRef)], linked: List[(Int, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  /** Pure: the row a patch makes of `cur`. Absent fields stay; `status` is normalised; a blank
    * `topic`/`reason`/`goal` clears it. `doneAt` is the database's to stamp, so it is left alone here. The run
    * or job that closed an item is kept while it stays done: reopened, it was closed by no one. */
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

  /** Validate a caller-supplied `created_at`. It is handed to Postgres as `?::timestamptz`, so a
    * malformed one was a 500 from the driver rather than a 400 naming the field. Normalised to an
    * ISO instant, which is what the column and the export both use. */
  def createdAtOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None => Right(None)
      case Some(s) =>
        scala.util.Try(java.time.Instant.parse(s)).toEither
          .map(i => Some(i.toString))
          .left.map(_ => s"created_at '$s' is not an ISO-8601 instant (e.g. 2026-08-25T00:00:00Z)")

  /** The path separator inside `topic`. A heading containing it would round-trip as two levels;
    * no heading in the corpus does, and the alternative (escaping) would make the column unreadable. */
  val Sep = " > "

  def path(topic: Option[String]): Vector[String] =
    topic.map(_.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty)).getOrElse(Vector.empty)

  def topicOf(p: Vector[String]): Option[String] = if p.isEmpty then None else Some(p.mkString(Sep))

  // ── parsing (pure) ───────────────────────────────────────────────────────────────────────────

  private val HeadingRe = """^(#{2,6})\s+(.*?)\s*$""".r
  private val BoldRe    = """^(\*\*.+\*\*)\s*$""".r
  private val CheckRe   = """^\s*[-*]\s+\[([ xX])\]\s?(.*)$""".r
  private val BulletRe  = """^\s*[-*]\s+(.*)$""".r
  /** A LOG heading opens with its date; a range (`2026-06-24/25`) keeps its first day. */
  private val DateRe    = """^(\d{4}-\d{2}-\d{2}).*""".r

  /** Parse one bookkeeping file into rows, in document order. Never fails: anything unrecognised is
    * kept verbatim as a `note`, which is what makes the round trip lossless. */
  def parse(kind: Kind, text: String): List[QueueAdd] =
    val out   = List.newBuilder[QueueAdd]
    var stack = Vector.empty[String]
    val note  = List.newBuilder[String]
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
          // a bold group REPLACES a bold sibling rather than nesting under it
          val depth = if stack.lastOption.exists(isBold) then stack.length - 1 else stack.length
          push(bold, depth)
        case CheckRe(box, body) if kind.checkboxes =>
          flush()
          last = Some(QueueAdd(body, topicOf(stack), status = Some(if box.trim.isEmpty then Open else Done)))
        case BulletRe(body) if !kind.checkboxes =>
          flush()
          last = Some(QueueAdd(body, topicOf(stack), status = Some(Open), createdAt = entryDate(stack)))
        case l =>
          // an indented, non-blank line continues the item above it; anything else is note text
          if last.isDefined && l.nonEmpty && l.head.isWhitespace then
            last = last.map(i => i.copy(text = i.text + "\n" + l))
          else
            flushItem()
            note += l
    }
    flush()
    out.result()

  private def isBold(s: String): Boolean = s.startsWith("**") && s.endsWith("**")

  /** The LOG entry's date, taken from its heading (`## 2026-06-25 — …`). Used to order entries
    * newest-first; absent when the heading does not start with a date. */
  private def entryDate(stack: Vector[String]): Option[String] =
    stack.headOption.flatMap {
      case DateRe(d) => Some(s"${d}T00:00:00Z")
      case _         => None
    }

  private def trimBlank(ls: List[String]): List[String] =
    ls.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse

  // ── rendering (pure) ─────────────────────────────────────────────────────────────────────────

  /** Emitted only when the stored list carries no preamble note of its own (a queue built entirely
    * through `queue add`), so an imported file's own header always wins. */
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

  /** Render a whole list back as its Markdown file. Groups keep document order (lowest id first);
    * LOG groups are ordered newest-first on their entry date, so `queue log add` lands on top. */
  def render(kind: Kind, items: List[QueueItem]): String =
    val (preamble, rest) = items.partition(_.topic.isEmpty)
    val groups = rest
      .groupBy(_.topic.getOrElse(""))
      .toList
      .sortBy { (_, is) =>
        val minId = is.map(_.id).minOption.getOrElse(0)
        if kind.checkboxes then (0L, minId.toLong)
        // newest entry first; ties keep file order
        else (-is.flatMap(_.createdAt).maxOption.map(instantKey).getOrElse(0L), minId.toLong)
      }
      .map((topic, is) => (path(Some(topic)), is.sortBy(_.id)))

    val blocks = List.newBuilder[Block]
    val head   = preamble.sortBy(_.id).map(_.text).filter(_.trim.nonEmpty)
    if head.isEmpty then blocks += Block.Note(defaultPreamble(kind))
    else head.foreach(t => blocks += Block.Note(t))

    var prev = Vector.empty[String]
    groups.foreach { (p, is) =>
      headingLines(prev, p).foreach(h => blocks += h)
      prev = p
      is.foreach(i => blocks += (if i.status == Note then Block.Note(i.text) else Block.Item(bullet(kind, i))))
    }
    join(blocks.result())

  /** The heading lines to emit when moving from one topic path to the next. */
  private def headingLines(prev: Vector[String], next: Vector[String]): List[Block] =
    if next.isEmpty then Nil
    else
      val firstDiff = next.indices.find(i => prev.lift(i) != Some(next(i)))
      // next is a prefix of prev (a shallower section reopening) → re-emit its deepest heading
      val from = firstDiff.getOrElse(next.length - 1)
      (from until next.length).toList.map { i =>
        val seg = next(i)
        if isBold(seg) then Block.Heading(seg)
        else Block.Heading(s"${"#" * (i + 2)} $seg")
      }

  private def bullet(kind: Kind, i: QueueItem): String =
    val marker = if !kind.checkboxes then "- " else if i.status == Done then "- [x] " else "- [ ] "
    val lines  = i.text.linesIterator.toList
    (marker + lines.headOption.getOrElse("")) + lines.drop(1).map("\n" + _).mkString

  /** One blank line before every block, except that a heading hugs the note/item under it and
    * consecutive items are not separated. */
  private def join(blocks: List[Block]): String =
    val sb = new StringBuilder
    blocks.zipWithIndex.foreach { (b, idx) =>
      val sep = blocks.lift(idx - 1) match
        case None                                                      => ""
        case Some(Block.Heading(_)) if !b.isInstanceOf[Block.Heading]  => "\n"
        case Some(Block.Item(_)) if b.isInstanceOf[Block.Item]           => "\n"
        case Some(_)                                                     => "\n\n"
      sb.append(sep)
      sb.append(b match
        case Block.Heading(l)    => l
        case Block.Item(l)       => l
        case Block.Note(l)       => l)
    }
    sb.append("\n")
    sb.result()

  /** Sortable key for an ISO instant; unparseable values sort oldest. */
  private def instantKey(s: String): Long =
    scala.util.Try(java.time.Instant.parse(s).toEpochMilli).getOrElse(0L)

  // ── terminal / MCP rendering ─────────────────────────────────────────────────────────────────

  /** `queue <list>` output: open items grouped by topic (LOG stays chronological), with the ids the
    * `done`/`rm` commands take; an acquisition item the re-check looked at says when and what it found. */
  def markdown(kind: Kind, items: List[QueueItem], checks: List[AcquireChecks.AcquireCheck] = Nil): String =
    val checked = checks.map(c => c.item -> c).toMap
    val shown = items.filterNot(_.status == Note)
    if shown.isEmpty then s"_${kind.label}: nothing queued._"
    else
      val groups = shown.groupBy(_.topic).toList.sortBy((_, is) => is.map(_.id).min)
      val blocks = groups.map { (topic, is) =>
        // the stored path keeps a bold group's `**` markers (the renderer needs them); a terminal
        // listing wraps the whole heading in bold itself, so strip them here or they double up.
        val head  = if topic.isEmpty then "(no topic)"
                    else path(topic).map(seg => if isBold(seg) then seg.drop(2).dropRight(2) else seg)
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
