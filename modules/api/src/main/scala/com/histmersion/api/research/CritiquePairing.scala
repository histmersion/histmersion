package com.histmersion.api.research
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.LatinFold
import com.histmersion.api.library.Hit
import com.histmersion.api.given

object CritiquePairing:
  object Mention:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Instruction:
    type Near = Near.T
    object Near extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type ResolverCitekeys = ResolverCitekeys.T
  object ResolverCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  final case class Work(citekey: com.histmersion.api.library.Citekey, names: List[Work.Names], year: Option[com.histmersion.api.catalogue.Year])

  object Work:
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Hit(
      unit: Hit.Unit,
      from: Hit.From,
      to: Hit.To,
      where: Hit.Where,
      cited: com.histmersion.api.library.Citekey,
      missing: com.histmersion.api.library.Citekey,
      edges: List[Hit.Edges],
      named: Hit.Named,
      mechanical: Hit.Mechanical
  ) derives KindlingsCodecAsObject:
    def ref: CritiqueRef = CritiqueRef(cited, missing)

    def line: String =
      (s"paragraph \"$where\" names $named and cites [[$cited]] (${edges.mkString("; ")}) but not [[$missing]] — " +
        (if (mechanical).value then s"cite [[$missing]] there (fixable)"
         else
           s"cite [[$missing]] there if the name means that work (code does not: the name is shared, or the article cites it nowhere else)"))

  object Hit:

    type Unit = Unit.T
    object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
    type Where = Where.T
    object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Edges = Edges.T
    object Edges extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Named = Named.T
    object Named extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mechanical = Mechanical.T
    object Mechanical extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def letters(s: String): String =
    LatinFold.fold(s).toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z')
  private def ascii(s: String): String = ((s).value.toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z'))

  def names(citekey: com.histmersion.api.library.Citekey, authors: List[com.histmersion.api.library.AuthorName]): List[String] =
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
    ((if byKey.nonEmpty then byKey else first.toList).filter(n => n.length >= 3 && n.head.isUpper).distinct).map(refactorValue3 => (refactorValue3))

  private val LinkRe = """!?\\?\[\\?\[[^\[\]\n]+?\\?\]\\?\]""".r
  private val YearRe = """(?<!\d)(1[0-9]\d\d|20\d\d)(?!\d)""".r

  private def words(text: String): List[String] = ((text).value.split("\\s+").toList.filter(_.exists(_.isLetterOrDigit))).map(refactorValue2 => (refactorValue2))

  private def prose(text: String): String = (LinkRe.replaceAllIn((text).value, " "))

  private enum Mention:
    case Strong, Weak, Conflict

  private def mention(text: String, name: com.histmersion.api.library.AuthorName, year: Option[com.histmersion.api.catalogue.Year]): Option[Mention] =
    val re = ("(?<!\\p{L})" + java.util.regex.Pattern.quote((name).value) + "(?!\\p{L})").r
    val ms = re.findAllMatchIn((text).value).toList
    if ms.isEmpty then None
    else
      val near = ms.map(m =>
        YearRe.findAllMatchIn((text).value.substring(m.end, math.min((text).value.length, m.end + 25))).map(_.group(1).toInt).toSet
      )
      if year.exists(y => near.exists(_((y).value))) then Some(Mention.Strong)
      else if near.forall(_.nonEmpty) then Some(Mention.Conflict)
      else Some(Mention.Weak)

  private def edgeLine(e: Coverage.Edge): String = (s"[[${e.from}]] ${e.kind} [[${e.to}]]")

  def hits(
      body: com.histmersion.api.research.Article.Body,
      resolve: com.histmersion.api.research.CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey],
      edges: List[Coverage.Edge],
      work: com.histmersion.api.research.CritiquePairing.Work.Names => Option[Work]
  ): List[Hit] =
    val es = edges.filter(e => Critiques.Kinds.contains(e.kind) && e.from != e.to)
    val blocks = Critiques.units(body)
    val citedAnywhere = blocks.flatMap(u => Article.Links.targets(u.text).flatMap(resolve)).toSet
    blocks.zipWithIndex.flatMap { (u, i) =>
      val cited = Article.Links.targets(u.text).flatMap(resolve).distinct
      val text = prose(u.text)
      val where = words(text).take(8).mkString(" ")
      val partners = cited
        .flatMap { x =>
          es.filter(e => e.from == x || e.to == x).map(e => (x, if e.from == x then e.to else e.from, e))
        }
        .filterNot((_, y, _) => cited.contains(y))
        .groupBy((x, y, _) => (x, y))
        .toList
        .sortBy(_._1)
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
      candidates.groupBy(_._2).toList.sortBy(_._1).map { case (y, cs) =>
        val (x, _, xes, n, m) = cs.head
        val shared = byName(n).map(_._2).distinct.size > 1 || citedNames(n)
        Hit(
          i,
          u.from,
          u.to,
          where,
          x,
          y,
          cs.flatMap(_._3).distinct.map(edgeLine),
          n,
          mechanical = citedAnywhere(y) && (m == Mention.Strong || !shared)
        )
      }
    }

  def repair(body: com.histmersion.api.research.Article.Body, hs: List[Hit]): (com.histmersion.api.research.Article.Body, List[Hit]) =
    val todo = hs.filter((refactorCallback => ((refactorCallback.mechanical) == _root_.com.histmersion.api.research.CritiquePairing.Hit.Mechanical(true)))).groupBy(h => (h.from, h.to))
    if todo.isEmpty then (_root_.com.histmersion.api.research.Article.Body((body).value), Nil)
    else
      val crlf = (body).value.contains("\r\n")
      val lines = (body).value.replace("\r\n", "\n").split("\n", -1).toVector
      @tailrec def insert(
          rest: List[((com.histmersion.api.personal.UnreadPage.Offset, Int), List[Hit])],
          ls: Vector[String],
          acc: List[Hit]
      ): (Vector[com.histmersion.api.research.Article.Body], List[Hit]) =
        rest match
          case Nil                      => (ls, acc)
          case ((from, to), xs) :: tail =>
            val add = xs.map(_.missing).distinct
            (to to from by -1).find(j => ls(j).contains("]]")) match
              case None    => insert(tail, ls, acc)
              case Some(j) =>
                val l = ls(j)
                val at = l.lastIndexOf("]]") + 2
                val ins = add.map(ck => s" [[$ck]]").mkString
                insert(tail, ls.updated(j, l.substring(0, at) + ins + l.substring(at)), acc ++ xs.distinctBy(_.missing))
      val (out, done) = insert(todo.toList.sortBy(_._1), lines, Nil)
      val text = out.mkString("\n")
      (if crlf then text.replace("\n", "\r\n") else text, done.sortBy(h => (h.unit, h.missing)))

  def instruction(
      body: com.histmersion.api.research.Article.Body,
      p: CritiquePair,
      resolve: com.histmersion.api.research.CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey],
      near: Option[com.histmersion.api.research.CritiquePairing.Instruction.Near] = None
  ): Option[com.histmersion.api.research.CritiquePairing.Instruction.Text] =
    val crit = p.criticised.citekey
    val critic = p.critic.citekey
    val us = Critiques.units(body).zipWithIndex
    def cites(u: Critiques.Block) = Article.Links.targets(u.text).flatMap(resolve).contains(crit)
    near.flatMap(n => us.find((u, i) => i == n && cites(u))).orElse(us.find((u, _) => cites(u))).map { (u, _) =>
      val head = words(prose(u.text)).take(10).mkString(" ")
      s"${p.ref.wire}: in the paragraph beginning \"$head\" (it cites [[$crit]]), add one sentence stating the " +
        s"${p.kinds.mkString("/")} from [[$critic]]'s record and cite [[$critic]] in that paragraph — or, when it does " +
        "not touch what that paragraph says, set the pair aside with a reason"
    }

  def resolver(citekeys: Set[com.histmersion.api.library.Citekey], aliases: Map[com.histmersion.api.library.Citekey, com.histmersion.api.library.Citekey]): com.histmersion.api.research.CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey] =
    val sorted = citekeys.toVector.sorted
    t =>
      val k = (t).value.trim.toLowerCase(java.util.Locale.ROOT)
      if citekeys(_root_.com.histmersion.api.library.Citekey(k)) then Some(_root_.com.histmersion.api.library.Citekey(k))
      else
        aliases.get(_root_.com.histmersion.api.library.Citekey(k)).filter(citekeys).orElse {
          val pre = sorted.filter(_.startsWith(k + "_"))
          Option.when(pre.size == 1)(pre.head)
        }
