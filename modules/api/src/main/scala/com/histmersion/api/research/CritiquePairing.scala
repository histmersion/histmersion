package com.histmersion
package api.research

import com.histmersion.api.research.critiquepairing.Work
import com.histmersion.api.research.critiquepairing.Mention
import com.histmersion.api.research.critiquepairing.MissedPair
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.LatinFold
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.text.WikiLinks
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

object CritiquePairing:
  object Instruction:
    type Near = Near.T
    object Near extends Newtype[Int, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  private def letters(s: String): String =
    LatinFold.fold(s).toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z')
  private def ascii(s: String): String = ((s).toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z'))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def names(citekey: Citekey, authors: List[AuthorName]): List[Work.Names] =
    val lead = (citekey).value.takeWhile(_.isLetter).toLowerCase(java.util.Locale.ROOT)
    val words = authors
      .flatMap((_).value.split("[\\s,]+").toList)
      .map(_.trim.stripSuffix("."))
      .filter(w => w.length >= 3 && w.head.isUpper)
    val byKey = words.filter(w => lead.nonEmpty && (letters((w)) == (lead) || ascii((w)) == (lead)))
    val first = authors.headOption
      .map((_).value.trim)
      .filter(_.nonEmpty)
      .map(a => if a.contains(",") then a.takeWhile(_ != ',').trim else a.split("\\s+").last.trim)
    ((if byKey.nonEmpty then byKey else first.toList)
      .filter(n => n.length >= 3 && n.head.isUpper)
      .distinct)
      .map(Work.Names(_))

  private val LinkRe = """!?\\?\[\\?\[[^\[\]\n]+?\\?\]\\?\]""".r
  private val YearRe = """(?<!\d)(1[0-9]\d\d|20\d\d)(?!\d)""".r

  private def words(text: String): List[String] = ((text).split("\\s+").toList.filter(_.exists(_.isLetterOrDigit)))

  private def prose(text: String): String = (LinkRe.replaceAllIn((text), " "))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mention(text: String, name: Work.Names, year: Option[Year]): Option[Mention] =
    val re = ("(?<!\\p{L})" + java.util.regex.Pattern.quote((name).value) + "(?!\\p{L})").r
    val ms = re.findAllMatchIn((text)).toList
    if ms.isEmpty then None
    else
      val near = ms.map(m =>
        YearRe.findAllMatchIn((text).substring(m.end, math.min((text).length, m.end + 25))).map(_.group(1).toInt).toSet
      )
      if year.exists(y => near.exists(_(y.value.toInt))) then Some(Mention.Strong)
      else if near.forall(_.nonEmpty) then Some(Mention.Conflict)
      else Some(Mention.Weak)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def targetsOf(u: CritiquePair.Block): List[WikiLinks.Occurrence.Target] =
    Article.Links.targets(u.text)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def edgeLine(e: Coverage.Edge): MissedPair.Edges = MissedPair.Edges(s"[[${e.from}]] ${e.kind} [[${e.to}]]")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hits(
      body: Article.Body,
      resolve: Mention.Key => Option[Citekey],
      edges: List[Coverage.Edge],
      work: Citekey => Option[Work]
  ): List[MissedPair] =
    val es = edges.filter(e => CritiquePair.DisagreementKinds.contains(e.kind) && e.from != e.to)
    val blocks = CritiquePair.units(body)
    val citedAnywhere = blocks.flatMap(u => targetsOf(u).flatMap(resolve)).toSet
    blocks.zipWithIndex.flatMap { (u, i) =>
      val cited = targetsOf(u).flatMap(resolve).distinct
      val text = prose(u.text.value)
      val where = MissedPair.Where(words(text).take(8).mkString(" "))
      val partners = cited
        .flatMap { x =>
          es.filter(e => e.from == x || e.to == x).map(e => (x, if e.from == x then e.to else e.from, e))
        }
        .filterNot((_, y, _) => cited.contains(y))
        .groupBy((x, y, _) => (x, y))
        .toList
        .sortBy { case ((x, y), _) => (x.value, y.value) }
        .map { case ((x, y), xs) => (x, y, xs.map(_._3)) }
      val citedNames = cited.flatMap(work).flatMap(_.names).toSet
      val candidates = partners.flatMap { (x, y, xes) =>
        work(y).toList.flatMap { w =>
          w.names
            .flatMap(n => mention(text, n, w.year).filterNot(_ == Mention.Conflict).map(m => (x, y, xes, n, m)))
            .headOption
        }
      }
      val byName = candidates.groupBy(_._4)
      candidates.groupBy(_._2).toList.sortBy(_._1.value).map { case (y, cs) =>
        val (x, _, xes, n, m) = cs.head
        val shared = byName(n).map(_._2).distinct.size > 1 || citedNames(n)
        MissedPair(
          MissedPair.Unit(i),
          u.from,
          u.to,
          where,
          x,
          y,
          cs.flatMap(_._3).distinct.map(edgeLine),
          n,
          mechanical = (if citedAnywhere(y) && (m == Mention.Strong || !shared) then MissedPair.Mechanical.Mechanical
                        else MissedPair.Mechanical.Judged)
        )
      }
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def repair(body: Article.Body, hs: List[MissedPair]): (Article.Body, List[MissedPair]) =
    val todo = hs
      .filter((refactorCallback => ((refactorCallback.mechanical) == MissedPair.Mechanical.Mechanical)))
      .groupBy(h => (h.from, h.to))
    if todo.isEmpty then (body, Nil)
    else
      val crlf = (body).value.contains("\r\n")
      val lines = (body).value.replace("\r\n", "\n").split("\n", -1).toVector
      @tailrec def insert(
          rest: List[((MissedPair.From, MissedPair.To), List[MissedPair])],
          ls: Vector[String],
          acc: List[MissedPair]
      ): (Vector[String], List[MissedPair]) =
        rest match
          case Nil                      => (ls, acc)
          case ((from, to), xs) :: tail =>
            val add = xs.map(_.missing).distinct
            ((to.value to from.value).by(-1)).find(j => ls(j).contains("]]")) match
              case None    => insert(tail, ls, acc)
              case Some(j) =>
                val l = ls(j)
                val at = l.lastIndexOf("]]") + 2
                val ins = add.map(ck => s" [[$ck]]").mkString
                insert(tail, ls.updated(j, l.substring(0, at) + ins + l.substring(at)), acc ++ xs.distinctBy(_.missing))
      val (out, done) = insert(todo.toList.sortBy { case ((f, t), _) => ((f.value: Int), (t.value: Int)) }, lines, Nil)
      val text = out.mkString("\n")
      (
        Article.Body(if crlf then text.replace("\n", "\r\n") else text),
        done.sortBy(h => ((h.unit.value: Int), (h.missing.value: String)))
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def instruction(
      body: Article.Body,
      p: CritiquePair,
      resolve: Mention.Key => Option[Citekey],
      near: Option[Instruction.Near] = None
  ): Option[Instruction.Text] =
    val crit = p.criticised.citekey
    val critic = p.critic.citekey
    val us = CritiquePair.units(body).zipWithIndex
    def cites(u: CritiquePair.Block) = targetsOf(u).flatMap(resolve).contains(crit)
    near.flatMap(n => us.find((u, i) => i == n.value && cites(u))).orElse(us.find((u, _) => cites(u))).map { (u, _) =>
      val head = words(prose(u.text.value)).take(10).mkString(" ")
      Instruction.Text(
        s"${p.ref.wire}: in the paragraph beginning \"$head\" (it cites [[$crit]]), add one sentence stating the " +
          s"${p.kinds.mkString("/")} from [[$critic]]'s record and cite [[$critic]] in that paragraph — or, when it does " +
          "not touch what that paragraph says, set the pair aside with a reason"
      )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolver(citekeys: Set[Citekey], aliases: Map[Citekey, Citekey]): Mention.Key => Option[Citekey] =
    val sorted = citekeys.toVector.sorted
    t =>
      val k = (t).value.trim.toLowerCase(java.util.Locale.ROOT)
      if citekeys(Citekey(k)) then Some(Citekey(k))
      else
        aliases.get(Citekey(k)).filter(citekeys).orElse {
          val pre = sorted.filter(_.value.startsWith(k + "_"))
          Option.when(pre.size == 1)(pre.head)
        }
