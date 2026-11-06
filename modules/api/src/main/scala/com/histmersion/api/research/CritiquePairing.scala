package com.histmersion.api.research
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.LatinFold
import com.histmersion.api.library.Hit
import com.histmersion.api.given

object CritiquePairing:

  type RepairInsertResultPart1 = RepairInsertResultPart1.T
  object RepairInsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairInsertRestPart1Part1 = RepairInsertRestPart1Part1.T
  object RepairInsertRestPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairInsertRestPart1Part2 = RepairInsertRestPart1Part2.T
  object RepairInsertRestPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairInsertLs = RepairInsertLs.T
  object RepairInsertLs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LettersResult = LettersResult.T
  object LettersResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LettersS = LettersS.T
  object LettersS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsciiResult = AsciiResult.T
  object AsciiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsciiS = AsciiS.T
  object AsciiS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesResult = NamesResult.T
  object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesCitekey = NamesCitekey.T
  object NamesCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesAuthors = NamesAuthors.T
  object NamesAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordsResult = WordsResult.T
  object WordsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordsText = WordsText.T
  object WordsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProseResult = ProseResult.T
  object ProseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProseText = ProseText.T
  object ProseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MentionText = MentionText.T
  object MentionText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MentionName = MentionName.T
  object MentionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MentionYear = MentionYear.T
  object MentionYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeLineResult = EdgeLineResult.T
  object EdgeLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HitsBody = HitsBody.T
  object HitsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HitsResolveArg1 = HitsResolveArg1.T
  object HitsResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HitsResolveResult = HitsResolveResult.T
  object HitsResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HitsWorkArg1 = HitsWorkArg1.T
  object HitsWorkArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairResultPart1 = RepairResultPart1.T
  object RepairResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairBody = RepairBody.T
  object RepairBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstructionResult = InstructionResult.T
  object InstructionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstructionBody = InstructionBody.T
  object InstructionBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstructionResolveArg1 = InstructionResolveArg1.T
  object InstructionResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstructionResolveResult = InstructionResolveResult.T
  object InstructionResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstructionNear = InstructionNear.T
  object InstructionNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolverResultArg1 = ResolverResultArg1.T
  object ResolverResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolverResultResult = ResolverResultResult.T
  object ResolverResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolverCitekeys = ResolverCitekeys.T
  object ResolverCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type ResolverAliasesPart1 = ResolverAliasesPart1.T
  object ResolverAliasesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolverAliasesPart2 = ResolverAliasesPart2.T
  object ResolverAliasesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    def line: com.histmersion.api.research.CritiquePairing.Hit.LineResult =
      _root_.com.histmersion.api.research.CritiquePairing.Hit.LineResult(s"paragraph \"$where\" names $named and cites [[$cited]] (${edges.mkString("; ")}) but not [[$missing]] — " +
        (if (mechanical).value then s"cite [[$missing]] there (fixable)"
         else
           s"cite [[$missing]] there if the name means that work (code does not: the name is shared, or the article cites it nowhere else)"))

  object Hit:
    type LineResult = LineResult.T
    object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  private def letters(s: com.histmersion.api.research.CritiquePairing.LettersS): com.histmersion.api.research.CritiquePairing.LettersResult =
    LatinFold.fold(s).toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z')
  private def ascii(s: com.histmersion.api.research.CritiquePairing.AsciiS): com.histmersion.api.research.CritiquePairing.AsciiResult = _root_.com.histmersion.api.research.CritiquePairing.AsciiResult((s).value.toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z'))

  def names(citekey: com.histmersion.api.research.CritiquePairing.NamesCitekey, authors: List[com.histmersion.api.research.CritiquePairing.NamesAuthors]): List[com.histmersion.api.research.CritiquePairing.NamesResult] =
    val lead = (citekey).value.takeWhile(_.isLetter).toLowerCase(java.util.Locale.ROOT)
    val words = authors
      .flatMap((_).value.split("[\\s,]+").toList)
      .map(_.trim.stripSuffix("."))
      .filter(w => w.length >= 3 && w.head.isUpper)
    val byKey = words.filter(w => lead.nonEmpty && (letters(_root_.com.histmersion.api.research.CritiquePairing.LettersS(w)) == _root_.com.histmersion.api.research.CritiquePairing.LettersResult(lead) || ascii(_root_.com.histmersion.api.research.CritiquePairing.AsciiS(w)) == _root_.com.histmersion.api.research.CritiquePairing.AsciiResult(lead)))
    val first = authors.headOption
      .map((_).value.trim)
      .filter(_.nonEmpty)
      .map(a => if a.contains(",") then a.takeWhile(_ != ',').trim else a.split("\\s+").last.trim)
    ((if byKey.nonEmpty then byKey else first.toList).filter(n => n.length >= 3 && n.head.isUpper).distinct).map(refactorValue3 => _root_.com.histmersion.api.research.CritiquePairing.NamesResult(refactorValue3))

  private val LinkRe = """!?\\?\[\\?\[[^\[\]\n]+?\\?\]\\?\]""".r
  private val YearRe = """(?<!\d)(1[0-9]\d\d|20\d\d)(?!\d)""".r

  private def words(text: com.histmersion.api.research.CritiquePairing.WordsText): List[com.histmersion.api.research.CritiquePairing.WordsResult] = ((text).value.split("\\s+").toList.filter(_.exists(_.isLetterOrDigit))).map(refactorValue2 => _root_.com.histmersion.api.research.CritiquePairing.WordsResult(refactorValue2))

  private def prose(text: com.histmersion.api.research.CritiquePairing.ProseText): com.histmersion.api.research.CritiquePairing.ProseResult = _root_.com.histmersion.api.research.CritiquePairing.ProseResult(LinkRe.replaceAllIn((text).value, " "))

  private enum Mention:
    case Strong, Weak, Conflict

  private def mention(text: com.histmersion.api.research.CritiquePairing.MentionText, name: com.histmersion.api.research.CritiquePairing.MentionName, year: Option[com.histmersion.api.research.CritiquePairing.MentionYear]): Option[Mention] =
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

  private def edgeLine(e: Coverage.Edge): com.histmersion.api.research.CritiquePairing.EdgeLineResult = _root_.com.histmersion.api.research.CritiquePairing.EdgeLineResult(s"[[${e.from}]] ${e.kind} [[${e.to}]]")

  def hits(
      body: com.histmersion.api.research.CritiquePairing.HitsBody,
      resolve: com.histmersion.api.research.CritiquePairing.HitsResolveArg1 => Option[com.histmersion.api.research.CritiquePairing.HitsResolveResult],
      edges: List[Coverage.Edge],
      work: com.histmersion.api.research.CritiquePairing.HitsWorkArg1 => Option[Work]
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

  def repair(body: com.histmersion.api.research.CritiquePairing.RepairBody, hs: List[Hit]): (com.histmersion.api.research.CritiquePairing.RepairResultPart1, List[Hit]) =
    val todo = hs.filter((refactorCallback => ((refactorCallback.mechanical) == _root_.com.histmersion.api.research.CritiquePairing.Hit.Mechanical(true)))).groupBy(h => (h.from, h.to))
    if todo.isEmpty then (_root_.com.histmersion.api.research.CritiquePairing.RepairResultPart1((body).value), Nil)
    else
      val crlf = (body).value.contains("\r\n")
      val lines = (body).value.replace("\r\n", "\n").split("\n", -1).toVector
      @tailrec def insert(
          rest: List[((com.histmersion.api.research.CritiquePairing.RepairInsertRestPart1Part1, com.histmersion.api.research.CritiquePairing.RepairInsertRestPart1Part2), List[Hit])],
          ls: Vector[com.histmersion.api.research.CritiquePairing.RepairInsertLs],
          acc: List[Hit]
      ): (Vector[com.histmersion.api.research.CritiquePairing.RepairInsertResultPart1], List[Hit]) =
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
      body: com.histmersion.api.research.CritiquePairing.InstructionBody,
      p: CritiquePair,
      resolve: com.histmersion.api.research.CritiquePairing.InstructionResolveArg1 => Option[com.histmersion.api.research.CritiquePairing.InstructionResolveResult],
      near: Option[com.histmersion.api.research.CritiquePairing.InstructionNear] = None
  ): Option[com.histmersion.api.research.CritiquePairing.InstructionResult] =
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

  def resolver(citekeys: Set[com.histmersion.api.research.CritiquePairing.ResolverCitekeys], aliases: Map[com.histmersion.api.research.CritiquePairing.ResolverAliasesPart1, com.histmersion.api.research.CritiquePairing.ResolverAliasesPart2]): com.histmersion.api.research.CritiquePairing.ResolverResultArg1 => Option[com.histmersion.api.research.CritiquePairing.ResolverResultResult] =
    val sorted = citekeys.toVector.sorted
    t =>
      val k = (t).value.trim.toLowerCase(java.util.Locale.ROOT)
      if citekeys(_root_.com.histmersion.api.research.CritiquePairing.ResolverCitekeys(k)) then Some(_root_.com.histmersion.api.research.CritiquePairing.ResolverResultResult(k))
      else
        aliases.get(_root_.com.histmersion.api.research.CritiquePairing.ResolverAliasesPart1(k)).filter(citekeys).orElse {
          val pre = sorted.filter(_.startsWith(k + "_"))
          Option.when(pre.size == 1)(pre.head)
        }
