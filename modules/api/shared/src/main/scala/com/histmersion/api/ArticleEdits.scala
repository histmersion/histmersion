package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** Edits to an article body, anchored on its own text — how the review fixer answers (a list of small changes code
  * applies, never a rewritten body), and what `check_edits` / `POST /api/articles/{slug}/check` dry-runs.
  *
  * An edit names an `anchor`: text copied from the body as it stands when the edit is applied. Edits apply IN ORDER,
  * each to the result of the ones before it. The anchor must occur exactly once; whitespace is normalised for the match
  * (any run of spaces, tabs and newlines in the anchor matches any run in the body), nothing else is. An anchor found
  * nowhere, or in more than one place, is a problem naming the edit's number and the anchor's head, and that edit is
  * skipped — the later ones still apply to the text without it. Pure; table-tested.
  */
object ArticleEdits:

  enum Op(val wire: String):
    /** The anchor becomes `text`. */
    case Replace extends Op("replace")

    /** `text` goes right after the anchor (start it with a blank line for a new paragraph). */
    case InsertAfter extends Op("insertAfter")

    /** `text` goes right before the anchor. */
    case InsertBefore extends Op("insertBefore")

    /** The anchor is removed (and the blank lines around it closed up). */
    case Delete extends Op("delete")

  object Op:
    def parse(s: String): Option[Op] = values.find(_.wire.equalsIgnoreCase(s.trim))
    given WireEnumValues[Op]:
      def all: Array[Op] = Op.values
      def wire(e: Op): String = e.wire
      def fromWire(s: String): Option[Op] = parse(s)
    given Encoder[Op] = WireEnum.encoder
    given Decoder[Op] = WireEnum.decoder
    given Schema[Op] = WireEnum.schema

  /** One edit as a model (or a person) states it. */
  final case class EditAnswer(op: Op, anchor: String, text: Option[String] = None) derives KindlingsCodecAsObject
  object EditAnswer:
    given Schema[EditAnswer] = KindlingsSchema
      .derived[EditAnswer]
      .schema
      .description("One change to the article body")
      .modify(_.op)(_.description("replace | insertAfter | insertBefore | delete"))
      .modify(_.anchor)(
        _.description(
          "Text copied EXACTLY from the current body (a sentence, or enough words to occur only once); " +
            "edits apply in order, so an anchor is looked up in the text the earlier edits produced"
        )
      )
      .modify(_.text)(
        _.description(
          "replace: the new text; insertAfter/insertBefore: the text to insert, verbatim (start it with a blank " +
            "line to add a paragraph); delete: null"
        )
      )

  /** A validated edit. */
  enum Edit:
    def anchor: String
    case Replace(anchor: String, text: String)
    case InsertAfter(anchor: String, text: String)
    case InsertBefore(anchor: String, text: String)
    case Delete(anchor: String)

  /** An answer → an edit: an empty anchor, a replace with no new text, or an insert with nothing to insert, is a
    * problem. A replace whose text is empty or null is NOT read as a delete: a strict-schema provider sends `null` (or
    * `""`) for a field it left out, and an edit that deletes says so with `delete`.
    */
  def parse(a: EditAnswer): Either[String, Edit] =
    val anchor = Option(a.anchor).getOrElse("")
    val text = Option(a.text).flatten.getOrElse("")
    if anchor.trim.isEmpty then Left("no anchor — copy the passage the edit is about from the body")
    else
      a.op match
        case Op.Delete                       => Right(Edit.Delete(anchor))
        case Op.Replace if text.trim.isEmpty =>
          Left("a replace with no new text — give the text, or use delete to remove the passage")
        case Op.Replace             => Right(Edit.Replace(anchor, text))
        case _ if text.trim.isEmpty => Left("no text to insert")
        case Op.InsertAfter         => Right(Edit.InsertAfter(anchor, text))
        case Op.InsertBefore        => Right(Edit.InsertBefore(anchor, text))

  /** What happened to one edit: `problem` None = applied. `anchor` is the anchor's head; `text` what it wrote (empty
    * for a delete). Numbered from 1, in the order given.
    */
  final case class EditResult(index: Int, op: Op, anchor: String, text: String, problem: Option[String] = None)
      derives KindlingsCodecAsObject:
    def applied: Boolean = problem.isEmpty
    def label: String = s"edit $index (${op.wire} \"$anchor\")"
  object EditResult:
    given Schema[EditResult] = KindlingsSchema.derived[EditResult].schema

  /** The body after the edits that applied, and every edit's result. */
  final case class Applied(body: String, results: List[EditResult]):
    def problems: List[String] = results.flatMap(r => r.problem.map(p => s"${r.label}: $p"))
    def appliedCount: Int = results.count(_.applied)

  /** The first words of an anchor, on one line — how a problem names it. */
  def head(anchor: String, max: Int = 48): String =
    val one = anchor.trim.replaceAll("\\s+", " ")
    if one.length <= max then one else one.take(max).trim + "…"

  /** `s` with every whitespace run as one space, and for each kept character its offset in `s`. */
  private def squeeze(s: String): (String, Array[Int]) =
    val out = StringBuilder()
    val pos = Array.newBuilder[Int]
    var i = 0 // offset in `s`; a scan loop
    while i < s.length do
      if s(i).isWhitespace then
        val from = i
        while i < s.length && s(i).isWhitespace do i += 1
        out.append(' '); pos += from
      else
        out.append(s(i)); pos += i; i += 1
    (out.toString, pos.result())

  private def occurrences(text: String, needle: String): List[Int] =
    Iterator.iterate(text.indexOf(needle))(i => text.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

  /** Where `anchor` is in `text`, as `[start, end)`: exactly once verbatim, else exactly once with whitespace
    * normalised. Not found, or found more than once, is the problem to report.
    */
  def locate(text: String, anchor: String): Either[String, (Int, Int)] =
    val a = anchor.trim
    occurrences(text, a) match
      case List(i)     => Right((i, i + a.length))
      case _ :: _ :: _ =>
        Left(s"the anchor occurs ${occurrences(text, a).size} times — quote more of the passage so it occurs once")
      case Nil =>
        val (sq, pos) = squeeze(text)
        val na = a.replaceAll("\\s+", " ")
        occurrences(sq, na) match
          case List(i) =>
            val last = i + na.length - 1 // the anchor is trimmed: its last character is not whitespace
            Right((pos(i), pos(last) + 1))
          case Nil =>
            Left("the anchor is not in the article — copy it exactly from the current body (earlier edits included)")
          case more => Left(s"the anchor occurs ${more.size} times — quote more of the passage so it occurs once")

  /** Remove `[s, e)` and close up the gap: the whitespace on both sides becomes one blank line if either side had one,
    * one newline if either had one, else one space (none at the start or end).
    */
  private def cut(text: String, s: Int, e: Int): String =
    val before = text.substring(0, s)
    val after = text.substring(e)
    val pre = before.reverseIterator.takeWhile(_.isWhitespace).mkString.reverse
    val post = after.takeWhile(_.isWhitespace)
    val gap = pre + post
    val head = before.dropRight(pre.length)
    val tail = after.drop(post.length)
    val joint =
      if head.isEmpty || tail.isEmpty then ""
      else if gap.count(_ == '\n') >= 2 then "\n\n"
      else if gap.contains('\n') then "\n"
      else if gap.nonEmpty then " "
      else ""
    head + joint + tail + (if tail.isEmpty then post.filter(_ == '\n').take(1) else "")

  /** One edit on `text`. */
  def applyOne(text: String, e: Edit): Either[String, String] =
    locate(text, e.anchor).map { (s, end) =>
      e match
        case Edit.Replace(_, t)      => text.substring(0, s) + t + text.substring(end)
        case Edit.InsertAfter(_, t)  => text.substring(0, end) + t + text.substring(end)
        case Edit.InsertBefore(_, t) => text.substring(0, s) + t + text.substring(s)
        case Edit.Delete(_)          => cut(text, s, end)
    }

  /** Apply `edits` to `body` in order. An edit that does not parse, whose anchor is missing or ambiguous, or that
    * `refuse` turns down (e.g. its text links to nothing) is skipped with its problem; the rest apply. The caller
    * decides what a skipped edit means (a retry, or — on the last attempt — a warning).
    */
  def applyAll(body: String, edits: List[EditAnswer], refuse: EditAnswer => Option[String] = _ => None): Applied =
    val (text, results) = edits.zipWithIndex.foldLeft((body, Vector.empty[EditResult])) { case ((cur, acc), (a, i)) =>
      val base = EditResult(i + 1, a.op, head(Option(a.anchor).getOrElse("")), a.text.getOrElse(""))
      val next = parse(a).flatMap(e => refuse(a).toLeft(e)).flatMap(applyOne(cur, _))
      next match
        case Right(t) => (t, acc :+ base)
        case Left(p)  => (cur, acc :+ base.copy(problem = Some(p)))
    }
    Applied(text, results.toList)

  /** A draft to check: edits to the stored body, or a whole body. */
  enum Draft:
    case Edits(edits: List[EditAnswer])
    case Body(text: String)

  /** `POST /api/articles/{slug}/check` / `check_edits`: `edits` against the stored body, or a whole `body` (not both).
    * `kind` overrides the article's kind for the section-set check (`index` checks none — for a fragment that is to be
    * appended).
    */
  final case class ArticleCheckRequest(
      edits: List[EditAnswer] = Nil,
      body: Option[String] = None,
      kind: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def draft: Either[String, Draft] =
      (edits, body.filter(_.trim.nonEmpty)) match
        case (Nil, None)       => Left("give `edits` (against the stored body) or `body` (a whole draft)")
        case (_ :: _, Some(_)) => Left("give `edits` or `body`, not both")
        case (es, None)        => Right(Draft.Edits(es))
        case (Nil, Some(b))    => Right(Draft.Body(b))
  object ArticleCheckRequest:
    given Schema[ArticleCheckRequest] = KindlingsSchema
      .derived[ArticleCheckRequest]
      .schema
      .modify(_.edits)(_.description("Edits to the CURRENT stored body, applied in order"))
      .modify(_.body)(_.description("Instead of edits: a whole draft body to check"))
      .modify(_.kind)(
        _.description("Check the section set of this kind instead of the article's (index = none, for a fragment)")
      )

  /** What the pipeline's validation would say of a draft: each edit's result, the problems of the resulting body, and
    * whether any of them is FATAL (an empty body, a link to nothing the draft added) — a fatal result is never written.
    * `body` is the resulting text.
    */
  final case class ArticleCheck(
      slug: String,
      kind: String,
      edits: List[EditResult],
      problems: List[String],
      fatal: Boolean,
      wordsBefore: Int,
      wordsAfter: Int,
      body: String
  ) derives KindlingsCodecAsObject:
    def clean: Boolean = problems.isEmpty
  object ArticleCheck:
    given Schema[ArticleCheck] = KindlingsSchema.derived[ArticleCheck].schema

  /** The check as every surface prints it (the resulting body is left out: it can be long). */
  def markdown(c: ArticleCheck): String =
    val edits =
      if c.edits.isEmpty then ""
      else "\n\n### Edits\n\n" + c.edits.map(r => s"- ${r.label}: ${r.problem.getOrElse("applies")}").mkString("\n")
    val verdict =
      if c.clean then "No problems: these are the checks code runs on the answer, and it would pass them."
      else if c.fatal then s"${Plural.count(c.problems.size, "problem")}, at least one FATAL (never written):"
      else s"${Plural.count(c.problems.size, "problem")}:"
    val problems = if c.clean then "" else "\n\n" + c.problems.map(p => s"- $p").mkString("\n")
    s"## Check of [[${c.slug}]] (${c.kind})\n\n${c.wordsBefore} → ${c.wordsAfter} words.$edits\n\n### Result\n\n$verdict$problems"
