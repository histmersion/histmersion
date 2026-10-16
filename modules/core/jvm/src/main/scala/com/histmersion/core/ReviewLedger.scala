package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{Decoder, Encoder}

import com.histmersion.core.ReviewArticle.*

/** The review ledger of one article (docs/research-review-convergence.md §6): every finding the
  * `review-article` panel ever raised on it, under an id that never changes, with what became of it —
  * open, fixed (verified by the reviewer that raised it), rejected (with the record's sentence) or won't
  * fix — and the lessons learned from rejections and failed fixes. It persists across runs, so a later
  * run starts from it; the reviewers and the fixer read it in their prompts.
  *
  * Everything here is pure: the pipeline loads the ledger, threads it through its passes as a value
  * (each pass's new ledger is a checkpointed step output) and writes it back through the run's gate. */
object ReviewLedger:

  enum Status(val wire: String):
    case Open     extends Status("open")
    case Fixed    extends Status("fixed")
    case Rejected extends Status("rejected")
    case WontFix  extends Status("wont-fix")

  object Status:
    def parse(s: String): Option[Status] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): String = e.wire
      def fromWire(s: String): Option[Status] = parse(s)
    given Encoder[Status] = WireEnum.encoder
    given Decoder[Status] = WireEnum.decoder

  /** Where a lesson came from: a rejection the fixer proved, or a reviewer's re-check. */
  enum LessonSource(val wire: String):
    case Rejection extends LessonSource("rejection")
    case Recheck   extends LessonSource("recheck")

  object LessonSource:
    def parse(s: String): Option[LessonSource] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[LessonSource]:
      def all: Array[LessonSource] = LessonSource.values
      def wire(e: LessonSource): String = e.wire
      def fromWire(s: String): Option[LessonSource] = parse(s)
    given Encoder[LessonSource] = WireEnum.encoder
    given Decoder[LessonSource] = WireEnum.decoder

  /** How a finding was closed: by whom (a reviewer's wire name, `fixer`, or `mechanical` for code),
    * in which pass of which run, and the evidence — the quote of the fixed text, or the record's
    * sentence of a rejection (with its citekey), or the reason a minor one is declined. */
  final case class Closure(by: String, round: Int, run: String, note: String = "", quote: String = "",
      citekey: Option[String] = None) derives KindlingsCodecAsObject

  /** One finding in the ledger. `claimed`: the fixer lists it as applied, and its reviewer has not
    * checked it yet. `notFixed`: the last check's word that it is not (or the rejection it was raised
    * again after). `reopened`: how often a fixed finding came back. */
  final case class Entry(
      finding: Finding,
      run: String,
      round: Int,
      status: Status = Status.Open,
      claimed: Boolean = false,
      notFixed: Option[String] = None,
      reopened: Int = 0,
      closed: Option[Closure] = None
  ) derives KindlingsCodecAsObject:
    def id: String       = finding.id
    def open: Boolean    = status == Status.Open
    def major: Boolean   = finding.major
    def reviewer: Reviewer = finding.reviewer

  final case class Lesson(text: String, source: LessonSource, run: String, round: Int) derives KindlingsCodecAsObject

  /** `task`: the article's one backlog item (#934), while it has one. */
  final case class Ledger(slug: String, entries: List[Entry] = Nil, lessons: List[Lesson] = Nil, task: Option[Int] = None)
      derives KindlingsCodecAsObject:
    def open: List[Entry]       = entries.filter(_.open)
    def openMajors: List[Entry] = open.filter(_.major)
    def entry(id: String): Option[Entry] = entries.find(_.id == id)
    /** The open findings the fixer claims, of one reviewer: what its re-check verifies. */
    def claimed(r: Reviewer): List[Entry] = open.filter(e => e.claimed && e.reviewer == r)
    def verdict: Verdict = Verdict.of(open.map(_.finding))

  /** At most this many lessons are kept (the newest). */
  val MaxLessons = 15

  // ── matching ───────────────────────────────────────────────────────────────────────────────────

  /** A mechanical finding is the same one when code says the same thing about the same passage: its
    * kind, its place, its problem up to the first colon (a coverage count changes as reasons are
    * given; the passage does not) and its quote. */
  private def mechanicalKey(f: Finding): (String, String, String, String) =
    (f.kind, AgentVerify.normalise(f.where), AgentVerify.normalise(f.problem.takeWhile(_ != ':')), AgentVerify.normalise(f.quote))

  /** Is `b` the finding `a` again? The same reviewer, and the same passage (quotes equal, or one inside
    * the other when both are at least 20 characters), or the same `where` and a problem that shares at
    * least half its words ([[CompareRuns.similarity]]). Mechanical findings compare by [[mechanicalKey]];
    * the cold reader's term-count findings ([[TermCount]]) are one finding whatever the count or wording. */
  def same(a: Finding, b: Finding): Boolean =
    a.reviewer == b.reviewer && {
      if a.reviewer == Reviewer.Mechanical then mechanicalKey(a) == mechanicalKey(b)
      else if TermCount.is(a) || TermCount.is(b) then TermCount.is(a) && TermCount.is(b)
      else
        val (qa, qb) = (AgentVerify.normalise(a.quote), AgentVerify.normalise(b.quote))
        val quoted = qa.nonEmpty && qb.nonEmpty &&
          (qa == qb || (math.min(qa.length, qb.length) >= 20 && (qa.contains(qb) || qb.contains(qa))))
        quoted || (AgentVerify.normalise(a.where) == AgentVerify.normalise(b.where) &&
          CompareRuns.similarity(a.problem, b.problem) >= CompareRuns.ProblemSimilarity)
    }

  /** The next ledger id of a reviewer: its letter and one more than the highest number it used. */
  def nextId(l: Ledger, r: Reviewer): String =
    val used = l.entries.map(_.id).filter(_.startsWith(r.prefix)).flatMap(_.drop(r.prefix.length).toIntOption)
    s"${r.prefix}${used.maxOption.getOrElse(0) + 1}"

  // ── transitions ────────────────────────────────────────────────────────────────────────────────

  private def update(l: Ledger, id: String)(f: Entry => Entry): Ledger =
    l.copy(entries = l.entries.map(e => if e.id == id then f(e) else e))

  /** Record a pass's findings (ids per pass, as the reviewers' answers were numbered). A finding the
    * ledger has already: an open one keeps its id (and is no longer "claimed": it was raised again); a
    * fixed one is RE-OPENED (a regression); a rejected one is re-opened and flagged escalated; a declined
    * minor stays declined unless it comes back MAJOR. Anything else is a new entry with the next id.
    * Returns the ledger and the findings under their ledger ids, in the order given. */
  def absorb(l: Ledger, fs: List[Finding], run: String, round: Int): (Ledger, List[Finding]) =
    fs.foldLeft((l, Vector.empty[Finding], Set.empty[String])) { case ((led, out, seen), f) =>
      led.entries.find(e => !seen(e.id) && same(e.finding, f)) match
        case Some(e) =>
          val id = e.id
          val next: Entry = e.status match
            case Status.Open =>
              e.copy(finding = f.copy(id = id, escalated = f.escalated || e.finding.escalated), claimed = false)
            case Status.Fixed =>
              e.copy(finding = f.copy(id = id), status = Status.Open, claimed = false,
                notFixed = Some(s"fixed in pass ${e.closed.fold(0)(_.round)}, raised again in pass $round"),
                reopened = e.reopened + 1, closed = None)
            case Status.Rejected =>
              e.copy(finding = f.copy(id = id, escalated = true), status = Status.Open, claimed = false,
                notFixed = e.closed.map(c => s"rejected before${c.citekey.fold("")(ck => s" ([[$ck]])")}: ${c.note}".take(300)),
                closed = None)
            case Status.WontFix if f.major =>
              e.copy(finding = f.copy(id = id), status = Status.Open, claimed = false, closed = None)
            case Status.WontFix => e
          (update(led, id)(_ => next), out :+ next.finding, seen + id)
        case None =>
          val id = nextId(led, f.reviewer)
          val g  = f.copy(id = id)
          (led.copy(entries = led.entries :+ Entry(g, run, round)), out :+ g, seen + id)
    } match
      case (led, out, _) => (led, out.toList)

  /** A reviewer's checks of its own findings: fixed → closed by that reviewer in this pass (with the
    * quote of the fixed text); not fixed → open, unclaimed, with the reviewer's note. A check of
    * another reviewer's finding, or of a closed one, changes nothing. */
  def applyChecks(l: Ledger, r: Reviewer, checks: List[Check], run: String, round: Int): Ledger =
    checks.foldLeft(l) { (led, c) =>
      led.entry(c.id).filter(e => e.open && e.reviewer == r).fold(led) { _ =>
        update(led, c.id) { e =>
          if c.fixed then e.copy(status = Status.Fixed, claimed = false, notFixed = None,
            closed = Some(Closure(r.wire, round, run, c.note, c.quote)))
          else e.copy(claimed = false, notFixed = Some(Option(c.note).filter(_.nonEmpty).getOrElse("not fixed")))
        }
      }
    }

  /** The mechanical re-check, by code: an open mechanical finding that no longer recurs is fixed; one
    * that recurs stays open (unclaimed); a new one is opened. */
  def recheckMechanical(l: Ledger, now: List[Finding], run: String, round: Int): (Ledger, List[Finding]) =
    val gone = l.open.filter(_.reviewer == Reviewer.Mechanical).filterNot(e => now.exists(same(e.finding, _)))
    val closed = gone.foldLeft(l)((led, e) => update(led, e.id)(_.copy(status = Status.Fixed, claimed = false,
      notFixed = None, closed = Some(Closure(Reviewer.Mechanical.wire, round, run, "no longer found by the checks")))))
    absorb(closed, now, run, round)

  /** The term count after a full panel, by code: the cold reader's count is the whole of that finding,
    * so an open count entry the pass did not raise again (8 terms or fewer now) is fixed. One it did
    * raise again is matched by [[absorb]], which replaces it — a MAJOR stored by an earlier run becomes the
    * pass's minor one under the same id. */
  def recheckTermCount(l: Ledger, cold: List[Finding], run: String, round: Int): Ledger =
    if cold.exists(TermCount.is) then l
    else l.open.filter(e => TermCount.is(e.finding)).foldLeft(l)((led, e) => update(led, e.id)(_.copy(
      status = Status.Fixed, claimed = false, notFixed = None,
      closed = Some(Closure(Reviewer.Mechanical.wire, round, run, "the cold reader's count is no longer above the threshold")))))

  /** The fixer's verified revision: what it applied is claimed (its reviewer re-checks it), a rejection
    * code verified closes the finding with the record's sentence (and teaches a lesson), a declined
    * minor is closed as won't fix. */
  def applyFix(l: Ledger, f: Fix, run: String, round: Int): Ledger =
    val claimed = f.applied.foldLeft(l)((led, id) => led.entry(id).filter(_.open).fold(led)(_ =>
      update(led, id)(_.copy(claimed = true))))
    val rejected = f.rejected.foldLeft(claimed)((led, r) => led.entry(r.id).filter(_.open).fold(led)(_ =>
      update(led, r.id)(_.copy(status = Status.Rejected, claimed = false,
        closed = Some(Closure("fixer", round, run, r.reason, r.quote, Some(r.citekey)))))))
    val declined = f.wontFix.foldLeft(rejected)((led, w) => led.entry(w.id).filter(e => e.open && !e.major).fold(led)(_ =>
      update(led, w.id)(_.copy(status = Status.WontFix, claimed = false, closed = Some(Closure("fixer", round, run, w.reason))))))
    learn(declined, f.rejected.map(r => s"[[${r.citekey}]]: ${r.reason}"), LessonSource.Rejection, run, round)

  /** Add lessons: trimmed, at most [[LessonChars]] each, a lesson already held (normalised) not twice,
    * the newest [[MaxLessons]] kept. */
  def learn(l: Ledger, texts: List[String], source: LessonSource, run: String, round: Int): Ledger =
    val fresh = texts.map(_.trim.replaceAll("\\s+", " ").take(LessonChars)).filter(_.nonEmpty)
    val all = fresh.foldLeft(l.lessons.toVector) { (acc, t) =>
      if acc.exists(x => AgentVerify.normalise(x.text) == AgentVerify.normalise(t)) then acc
      else acc :+ Lesson(t, source, run, round)
    }
    l.copy(lessons = all.takeRight(MaxLessons).toList)

  /** A verification pass, for one reviewer: its checks, then its new findings and its lessons. */
  def applyRecheck(l: Ledger, rc: Recheck, run: String, round: Int): Ledger =
    val checked = applyChecks(l, rc.reviewer, rc.checks, run, round)
    learn(absorb(checked, rc.findings, run, round)._1, rc.lessons, LessonSource.Recheck, run, round)

  // ── what the steps read ────────────────────────────────────────────────────────────────────────

  /** The open findings as one pass's merged result — the fixer's input and the pass's verdict: the
    * mechanical ones first, then rules, cold reader, skeptic, each by id. `explainBack`, `emptySlots`
    * and `proposals` are the pass's own. */
  def merged(l: Ledger, round: Int, explainBack: Option[String] = None, emptySlots: List[String] = Nil,
      proposals: List[AgentProposals.Proposal] = Nil): Merged =
    val fs = l.open.map(_.finding).sortBy(f => (f.reviewer.ordinal, f.id.drop(f.reviewer.prefix.length).toIntOption.getOrElse(0)))
    Merged(round, Verdict.of(fs), fs, explainBack, emptySlots, proposals)

  /** The notes the fixer reads under an open finding: a fix claimed before that its reviewer found
    * wanting, a regression, a rejection it was raised again after. */
  def notes(l: Ledger): Map[String, String] =
    l.open.flatMap(e => e.notFixed.map(n => e.id -> s"not fixed yet — $n")).toMap

  /** One line of a closed finding. */
  def closedLine(e: Entry): String =
    val at = Option(e.finding.where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
    val how = (e.status, e.closed) match
      case (Status.Fixed, Some(c))    => s"fixed, verified by ${c.by} in pass ${c.round}"
      case (Status.Rejected, Some(c)) => s"rejected${c.citekey.fold("")(ck => s" — [[$ck]]: \"${c.quote.take(120)}\"")}"
      case (Status.WontFix, Some(c))  => s"won't fix — ${c.note.take(120)}"
      case (s, _)                     => s.wire
    s"${e.id} ${e.finding.severity.wire}$at: ${e.finding.problem.trim.take(140)} → $how"

  /** How much of the open findings a prompt shows: in full, one line each, or not at all. */
  enum OpenStyle:
    case Full, Lines, Omit

  /** The ledger as a prompt shows it (inside `<review-ledger>`): the open findings (`style`; with
    * `only`, just that reviewer's), the newest `closedMax` closed ones as one line each, and the lessons.
    * Empty when there is nothing to show. */
  def promptText(l: Ledger, style: OpenStyle, closedMax: Int, only: Option[Reviewer] = None): String =
    val mine   = (e: Entry) => only.forall(_ == e.reviewer)
    val open   = l.open.filter(mine)
    val closed = l.entries.filterNot(_.open).filter(mine).takeRight(math.max(0, closedMax))
    val openPart = style match
      case OpenStyle.Omit => ""
      case _ if open.isEmpty => ""
      case OpenStyle.Lines =>
        "Open findings:\n" + open.map(e => s"- ${e.id} ${e.finding.severity.wire}: ${e.finding.problem.trim.take(160)}").mkString("\n")
      case OpenStyle.Full =>
        "Open findings:\n" + findingsText(merged(l.copy(entries = open), 0), notes(l)) +
          (if open.exists(_.claimed) then "\n(The fixer claims to have fixed: " + open.filter(_.claimed).map(_.id).mkString(", ") + ".)" else "")
    val closedPart =
      if closed.isEmpty then ""
      else "Closed findings (one line each):\n" + closed.map(e => s"- ${closedLine(e)}").mkString("\n")
    val lessonPart = if l.lessons.isEmpty then "" else "Lessons:\n" + l.lessons.map(x => s"- ${x.text}").mkString("\n")
    List(openPart, closedPart, lessonPart).filter(_.nonEmpty).mkString("\n\n")

  // ── the edited passages ────────────────────────────────────────────────────────────────────────

  /** A run of changed paragraphs of the new body (with its margin), under the heading it sits in, and
    * the paragraphs of the old body that are gone. */
  final case class Passage(heading: Option[String], text: String) derives KindlingsCodecAsObject
  final case class Edited(passages: List[Passage], removed: List[String]) derives KindlingsCodecAsObject:
    def isEmpty: Boolean = passages.isEmpty && removed.isEmpty
    /** The text a finding's quote is looked up in (to scope it). */
    def text: String = passages.map(_.text).mkString("\n\n")
    /** As a prompt shows it. */
    def render: String =
      val ps = passages.map(p => p.heading.fold("")(h => s"[under: $h]\n") + p.text).mkString("\n\n---\n\n")
      val rs = if removed.isEmpty then "" else "\n\n---\n\nRemoved:\n" + removed.map(r => s"- ${r.take(300)}").mkString("\n")
      ps + rs

  private def paragraphs(body: String): Vector[String] =
    body.replace("\r\n", "\n").split("\n[ \t]*\n").map(_.trim).filter(_.nonEmpty).toVector

  private val Heading = "^#{1,6}\\s+(.+?)\\s*#*\\s*$".r

  /** The passages a fix changed: a paragraph-level diff of `before` and `after` (longest common
    * subsequence of whitespace-normalised paragraphs), each changed paragraph of `after` widened by
    * `margin` paragraphs on each side, adjacent ones joined; plus the paragraphs of `before` that are
    * gone. */
  def editedPassages(before: String, after: String, margin: Int = 1): Edited =
    val a = paragraphs(before)
    val b = paragraphs(after)
    def key(p: String) = p.replaceAll("\\s+", " ")
    val (ka, kb) = (a.map(key), b.map(key))
    // LCS table, suffix form: t(i)(j) = LCS of a.drop(i), b.drop(j)
    val t = Array.ofDim[Int](a.size + 1, b.size + 1)
    for i <- a.indices.reverse; j <- b.indices.reverse do
      t(i)(j) = if ka(i) == kb(j) then t(i + 1)(j + 1) + 1 else math.max(t(i + 1)(j), t(i)(j + 1))
    @annotation.tailrec
    def walk(i: Int, j: Int, keptA: Set[Int], keptB: Set[Int]): (Set[Int], Set[Int]) =
      if i >= a.size || j >= b.size then (keptA, keptB)
      else if ka(i) == kb(j) then walk(i + 1, j + 1, keptA + i, keptB + j)
      else if t(i + 1)(j) >= t(i)(j + 1) then walk(i + 1, j, keptA, keptB)
      else walk(i, j + 1, keptA, keptB)
    val (keptA, keptB) = walk(0, 0, Set.empty, Set.empty)
    val changed = b.indices.filterNot(keptB)
    val m = math.max(0, margin)
    val shown = changed.flatMap(i => (i - m) to (i + m)).filter(b.indices.contains).distinct.sorted
    val runs = shown.foldLeft(List.empty[List[Int]]) {
      case (cur :: rest, i) if cur.head == i - 1 => (i :: cur) :: rest
      case (acc, i)                               => List(i) :: acc
    }.reverse.map(_.reverse)
    def headingOf(i: Int): Option[String] =
      (i to 0 by -1).iterator.map(b(_)).collectFirst { case p if p.linesIterator.nextOption().exists(Heading.matches) =>
        p.linesIterator.next() match { case Heading(h) => h.trim; case h => h }
      }
    Edited(runs.map(r => Passage(headingOf(r.head), r.map(b(_)).mkString("\n\n"))), a.indices.filterNot(keptA).map(a(_)).toList)

  // ── the backlog item (#934) ────────────────────────────────────────────────────────────────────

  /** The article's one backlog item while a MAJOR is open: how many, and each one's line. */
  def taskText(l: Ledger, runId: String, maxChars: Int = 1500): Option[String] =
    val majors = l.openMajors
    Option.when(majors.nonEmpty) {
      val lines = majors.map { e =>
        val esc = if e.finding.escalated then " [raised again after a rejection]" else ""
        val at  = Option(e.finding.where).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
        s"${e.id}$at: ${e.finding.problem.trim.take(160)}$esc"
      }
      val head = s"review-article [[${l.slug}]]: ${majors.size} open MAJOR${if majors.size == 1 then "" else "s"} — "
      val tail = s" (agent run ${runId.take(8)})"
      val body = lines.mkString("; ")
      val room = math.max(40, maxChars - head.length - tail.length)
      head + (if body.length <= room then body else body.take(room - 1) + "…") + tail
    }

  /** The text of the article's one collapsed item — as [[taskText]] writes it, or as the interactive
    * skill does (`review-article <slug>: 2 open MAJORs — …`) — so a run whose ledger lost the item's id
    * rewrites the open one instead of adding a second. Not a [[legacyTask]] (those are closed). */
  def articleTask(slug: String, text: String): Boolean =
    val t = text.trim
    !legacyTask(slug, t) &&
      (t.startsWith(s"review-article [[$slug]]: ") || t.startsWith(s"review-article $slug: ")) &&
      "^review-article \\S+: \\d+ open MAJORs?\\b".r.findPrefixOf(t).isDefined

  /** The text of the per-finding items earlier runs left for the article (before #934). */
  def legacyTask(slug: String, text: String): Boolean =
    text.startsWith(s"review-article [[$slug]]: MAJOR ")
