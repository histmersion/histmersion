package com.histmersion.api.research

import com.histmersion.api.Plural
import com.histmersion.api.given
object Critiques:

  val Kinds: List[String] = Article.Coverage.DisagreementKinds

  def olderFirst(kinds: List[String]): Boolean = kinds.nonEmpty && kinds.forall(_ == "superseded_by")

  private val ListItem = "^([-*+]|\\d+[.)])\\s+.*".r

  def paragraphs(body: String): List[String] = units(body).map(_.text)

  final case class Block(from: Int, to: Int, text: String)

  def units(body: String): List[Block] =
    val lines = body.replace("\r\n", "\n").split("\n", -1).toVector
    val skip = notUsedLines(lines)
    val out = List.newBuilder[Block]
    val cur = new StringBuilder()
    var fence = false
    var start = -1
    var last = -1
    def flush(): Unit =
      val t = cur.toString.trim
      if t.nonEmpty then out += Block(start, last, t)
      cur.clear()
      start = -1
    lines.indices.foreach { i =>
      val t = lines(i).trim
      def add(s: String): Unit =
        if start < 0 then start = i
        last = i
        cur.append(if cur.isEmpty then s else s" $s")
      if skip(i) then flush()
      else if t.startsWith("```") then { flush(); fence = !fence }
      else if fence then ()
      else if t.isEmpty then flush()
      else if t.startsWith("#") then flush()
      else if t.startsWith("|") then { flush(); add(t); flush() }
      else if ListItem.matches(t) then { flush(); add(t) }
      else add(t)
    }
    flush()
    out.result()

  private def notUsedLines(lines: Vector[String]): Set[Int] =
    def heading(l: String) = l.trim.startsWith("#")
    def level(l: String) = l.trim.takeWhile(_ == '#').length
    lines.indexWhere(l => heading(l) && l.trim.replaceAll("^#+\\s*", "").trim.toLowerCase.startsWith("not used")) match
      case -1 => Set.empty
      case i  =>
        val lv = level(lines(i))
        val end = lines.indexWhere(l => heading(l) && level(l) <= lv, i + 1)
        (i until (if end < 0 then lines.size else end)).toSet

  def build(
      body: String,
      resolve: String => Option[String],
      edges: List[Coverage.Edge],
      reasons: List[CritiqueReason],
      refs: String => Option[(String, Option[Int])] = _ => None
  ): List[CritiquePair] =
    val units = paragraphs(body).map(p => Article.Links.targets(p).flatMap(resolve).toSet)
    val cited = units.flatten.toSet
    val mentions = units.flatMap(_.toList).groupBy(identity).view.mapValues(_.size).toMap
    def side(ck: String) =
      val r = refs(ck)
      CritiqueSide(ck, r.map(_._1), r.flatMap(_._2), cited(ck), mentions.getOrElse(ck, 0))
    edges
      .filter(e => Kinds.contains(e.kind) && e.from != e.to && (cited(e.from) || cited(e.to)))
      .groupBy(e => (e.from, e.to))
      .toList
      .sortBy(_._1)
      .map { case ((src, dst), es) =>
        val ref = CritiqueRef(src, dst)
        val together = units.exists(u => u(src) && u(dst))
        val stored = reasons.find(r => r.ref.same(ref) && r.reason.trim.nonEmpty)
        val state =
          if together then CritiqueState.Addressed
          else if stored.isDefined then CritiqueState.Reasoned
          else CritiqueState.Open
        CritiquePair(
          side(src),
          es.map(_.kind).distinct.sortBy(Kinds.indexOf),
          side(dst),
          state,
          stored.map(_.reason),
          stored.flatMap(_.by)
        )
      }

  def find(pairs: List[CritiquePair], r: CritiqueRef): Option[CritiquePair] = pairs.find(_.ref.same(r))

  def count(pairs: List[CritiquePair], s: CritiqueState): Int = pairs.count(_.state == s)

  def together(body: String, r: CritiqueRef, resolve: String => Option[String]): Boolean =
    units(body).exists { u =>
      val cks = Article.Links.targets(u.text).flatMap(resolve).toSet
      cks(r.src) && cks(r.dst)
    }

  def ranked(pairs: List[CritiquePair]): List[CritiquePair] =
    def tier(p: CritiquePair) =
      if !p.critic.cited && p.criticised.cited then 0 else if p.critic.cited && p.criticised.cited then 1 else 2
    def kind(p: CritiquePair) = p.kinds
      .map {
        case "refutes" | "superseded_by" => 0
        case "challenges"                => 1
        case _                           => 2
      }
      .minOption
      .getOrElse(3)
    pairs
      .filter(_.open)
      .sortBy(p => (tier(p), kind(p), -(p.src.mentions + p.dst.mentions), p.src.citekey, p.dst.citekey))

  def summary(pairs: List[CritiquePair]): String =
    s"${Plural.count(pairs.size, "pair")} — ${count(pairs, CritiqueState.Addressed)} addressed, " +
      s"${count(pairs, CritiqueState.Reasoned)} reasoned, ${count(pairs, CritiqueState.Open)} open"

  def methodPart(pairs: List[CritiquePair]): Option[String] =
    Option.when(pairs.nonEmpty)(s"disagreements checked: ${summary(pairs)}")

  private def sideText(s: CritiqueSide): String =
    val meta = (s.year.map(_.toString).toList ++ s.title
      .map(t => "\"" + (if t.length > 90 then t.take(89).trim + "…" else t) + "\"")
      .toList)
    val cited = if s.cited then "cited" else "not cited"
    s"[[${s.citekey}]] (${(meta :+ cited).mkString(", ")})"

  def pairText(p: CritiquePair): String =
    s"${p.ref.wire}: ${sideText(p.src)} ${p.kinds.mkString("/")} ${sideText(p.dst)}"

  def markdown(pairs: List[CritiquePair]): String =
    if pairs.isEmpty then ""
    else
      val open = ranked(pairs)
      val reasoned = pairs.filter(_.state == CritiqueState.Reasoned)
      val openPart = if open.isEmpty then ""
      else
        "\n\nOpen (neither discussed in one paragraph nor given a reason):\n\n" +
          open.map(p => s"- ${pairText(p)}").mkString("\n")
      val reasonPart = if reasoned.isEmpty then ""
      else
        "\n\nSet aside with a reason:\n\n" +
          reasoned
            .map(p => s"- ${p.line} — ${p.reason.getOrElse("")}${p.reasonBy.fold("")(b => s" _(by $b)_")}")
            .mkString("\n")
      s"### Disagreements checked\n\n${summary(pairs)} (a pair is addressed when both sources are cited in one paragraph)." +
        openPart + reasonPart

  def toRule(pairs: List[CritiquePair]): List[CritiquePair] =
    ranked(pairs)
      .filter(_.bearing)
      .foldLeft(Vector.empty[CritiquePair])((acc, p) => if acc.exists(_.ref.same(p.ref)) then acc else acc :+ p)
      .toList

  def promptText(pairs: List[CritiquePair], maxOpen: Int, maxReasoned: Int): (String, List[CritiquePair]) =
    val open = toRule(pairs)
    val shown = open.take(math.max(0, maxOpen))
    val rest = open.drop(shown.size)
    val background = ranked(pairs).filterNot(_.bearing)
    val reasoned = pairs.filter(_.state == CritiqueState.Reasoned)
    if shown.isEmpty && background.isEmpty && reasoned.isEmpty then ("", Nil)
    else
      def names(ps: List[CritiquePair]) =
        ps.take(40).map(_.ref.wire).mkString(", ") + (if ps.size > 40 then ", …" else "")
      val openPart =
        (if shown.isEmpty then
           (if background.isEmpty then "No pair is open."
            else "No open pair is on a work the article cites."
         )
         else
           "Open pairs — rule on each one:\n" + shown.map(p => s"- ${pairText(p)}").mkString("\n") +
             (if rest.isEmpty then ""
              else s"\n(${Plural.count(rest.size, "more open pair")} not shown, for a later pass: ${names(rest)})")
        ) +
          (if background.isEmpty then ""
           else
             s"\n(${Plural.count(background.size, "open pair")} on a work the article does not cite — it states none of " +
               s"that work's claims, so no ruling is owed: ${names(background)})")
      val reasonPart =
        if reasoned.isEmpty then ""
        else
          "\n\nSet aside with a stored reason (not open; challenge a wrong reason as a finding, with the record's sentence):\n" +
            reasoned
              .take(math.max(0, maxReasoned))
              .map(p => s"- ${p.ref.wire}: ${p.reason.getOrElse("").take(200)}")
              .mkString("\n") +
            (if reasoned.size > maxReasoned then s"\n(+${reasoned.size - maxReasoned} more)" else "")
      (s"${summary(pairs)}.\n\n$openPart$reasonPart", shown)
