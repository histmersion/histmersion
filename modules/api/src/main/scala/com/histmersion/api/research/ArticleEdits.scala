package com.histmersion.api.research
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues
import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.given

object ArticleEdits:

  enum Op(val wire: String):
    case Replace extends Op("replace")

    case InsertAfter extends Op("insertAfter")

    case InsertBefore extends Op("insertBefore")

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

  enum Edit:
    def anchor: String
    case Replace(anchor: String, text: String)
    case InsertAfter(anchor: String, text: String)
    case InsertBefore(anchor: String, text: String)
    case Delete(anchor: String)

  /** A replace whose text is empty or null is not read as a delete: a strict-schema provider sends `null` (or `""`) for
    * a field it left out.
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

  final case class EditResult(index: Int, op: Op, anchor: String, text: String, problem: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def applied: Boolean = problem.isEmpty
    def label: String = s"edit $index (${op.wire} \"$anchor\")"
  object EditResult:
    given Schema[EditResult] = summon[KindlingsSchema[EditResult]].schema

  final case class Applied(body: String, results: List[EditResult]):
    def problems: List[String] = results.flatMap(r => r.problem.map(p => s"${r.label}: $p"))
    def appliedCount: Int = results.count(_.applied)

  def head(anchor: String, max: Int = 48): String =
    val one = anchor.trim.replaceAll("\\s+", " ")
    if one.length <= max then one else one.take(max).trim + "…"

  private def squeeze(s: String): (String, Array[Int]) =
    val out = StringBuilder()
    val pos = Array.newBuilder[Int]
    var i = 0
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
            val last = i + na.length - 1
            Right((pos(i), pos(last) + 1))
          case Nil =>
            Left("the anchor is not in the article — copy it exactly from the current body (earlier edits included)")
          case more => Left(s"the anchor occurs ${more.size} times — quote more of the passage so it occurs once")

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

  def applyOne(text: String, e: Edit): Either[String, String] =
    locate(text, e.anchor).map { (s, end) =>
      e match
        case Edit.Replace(_, t)      => text.substring(0, s) + t + text.substring(end)
        case Edit.InsertAfter(_, t)  => text.substring(0, end) + t + text.substring(end)
        case Edit.InsertBefore(_, t) => text.substring(0, s) + t + text.substring(s)
        case Edit.Delete(_)          => cut(text, s, end)
    }

  def applyAll(body: String, edits: List[EditAnswer], refuse: EditAnswer => Option[String] = _ => None): Applied =
    @tailrec def loop(rest: List[EditAnswer], number: Int, cur: String, acc: Vector[EditResult]): Applied =
      rest match
        case Nil       => Applied(cur, acc.toList)
        case a :: tail =>
          val base = EditResult(number, a.op, head(Option(a.anchor).getOrElse("")), a.text.getOrElse(""))
          parse(a).flatMap(e => refuse(a).toLeft(e)).flatMap(applyOne(cur, _)) match
            case Right(t) => loop(tail, number + 1, t, acc :+ base)
            case Left(p)  => loop(tail, number + 1, cur, acc :+ base.copy(problem = Some(p)))
    loop(edits, 1, body, Vector.empty)

  enum Draft:
    case Edits(edits: List[EditAnswer])
    case Body(text: String)

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

  final case class ArticleCheck(
      slug: String,
      kind: String,
      edits: List[EditResult],
      problems: List[String],
      fatal: Boolean,
      wordsBefore: Int,
      wordsAfter: Int,
      body: String
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def clean: Boolean = problems.isEmpty
  object ArticleCheck:
    given Schema[ArticleCheck] = summon[KindlingsSchema[ArticleCheck]].schema

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
