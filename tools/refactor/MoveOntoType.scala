import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object MoveOntoType {

  case class Move(source: String = "", member: String = "", to: String = "", form: String = "auto", name: String = "",
      file: String = "")
  object Move {
    implicit val surface: generic.Surface[Move] = generic.deriveSurface[Move]
    implicit val decoder: ConfDecoder[Move] = generic.deriveDecoder(Move()).noTypos
  }

  case class Config(
      moves: List[Move] = Nil,
      semanticdb: List[String] = List("target/out", ".semanticdb"),
      sourceroot: String = ".",
      report: String = "",
      keepComments: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  final case class Planned(symbols: Set[String], name: String, newName: String, to: String, form: String, file: String) {
    def toplevel: Boolean = form == "toplevel" || form == "promote"
    def promote: Boolean = form == "promote"
    def extension: Boolean = form == "extension"
  }

  def norm(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")

  def strip(sym: String): String = sym.stripSuffix(".").stripSuffix("#").stripSuffix("/")

  private val ParamsSuffix = "\\(\\+?\\d*\\)\\.$"

  def path(sym: String): String = {
    val parts = strip(sym.replaceAll(ParamsSuffix, "")).split("[/.#]").toList.filter(_.nonEmpty).filterNot(_.endsWith("$package"))
    parts.map(p => if (p.matches("[A-Za-z_][A-Za-z0-9_]*") || p.forall(c => "+-*/<>=!&|^~%:".contains(c))) p else s"`$p`").mkString(".")
  }

  def owner(sym: String): String = {
    val trimmed = sym.replaceAll(ParamsSuffix, ".").dropRight(1)
    val cut = math.max(trimmed.lastIndexOf('/'), math.max(trimmed.lastIndexOf('.'), trimmed.lastIndexOf('#')))
    if (cut < 0) ""
    else {
      val o = trimmed.take(cut + 1)
      if (o.endsWith("$package.")) o.take(o.dropRight(1).lastIndexOf('/') + 1) else o
    }
  }

  def lastName(sym: String): String = path(sym).split('.').last.stripPrefix("`").stripSuffix("`")

  def isPackage(sym: String): Boolean = sym.endsWith("/")

  def termOf(sym: String): String = if (sym.endsWith("#")) sym.dropRight(1) + "." else sym

  def topLevelOf(sym: String): String = {
    var cur = termOf(sym)
    while (owner(cur).nonEmpty && !isPackage(owner(cur))) cur = owner(cur)
    cur
  }

  def targetRef(to: String): (String, String) = {
    val obj = termOf(to)
    val pkg = path(owner(topLevelOf(obj)))
    val segs = path(obj).split('.').toList
    val within = segs.drop(if (pkg.isEmpty) 0 else pkg.split('.').length)
    (within.mkString("."), (if (pkg.isEmpty) "" else pkg + ".") + within.head)
  }

  def sameType(paramType: String, target: String): Boolean =
    strip(paramType) == strip(target) || strip(paramType) == strip(target) + ".T" ||
      (lastName(paramType) == lastName(target) && owner(paramType) == owner(termOf(target)))
}

class MoveOntoType(config: MoveOntoType.Config) extends SemanticRule("MoveOntoType") {
  import MoveOntoType._

  def this() = this(MoveOntoType.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("MoveOntoType")(MoveOntoType.Config()).map(new MoveOntoType(_))

  implicit private val dialect: Dialect = dialects.Scala3

  private val enabled = config.moves.nonEmpty

  private val moves = config.moves.map(m => m.copy(source = norm(m.source), to = norm(m.to)))

  private def sym(t: Tree)(implicit doc: SemanticDocument): String = norm(t.symbol.value)

  private final case class SourceDoc(uri: String, text: String, tree: Source, doc: s.TextDocument) {
    private val byRange: Map[(Int, Int, Int, Int), Seq[s.SymbolOccurrence]] =
      doc.occurrences.filter(_.range.isDefined).groupBy { o =>
        val r = o.range.get; (r.startLine, r.startCharacter, r.endLine, r.endCharacter)
      }
    def occurrences(t: Tree): Seq[s.SymbolOccurrence] = {
      val p = t.pos; byRange.getOrElse((p.startLine, p.startColumn, p.endLine, p.endColumn), Nil)
    }
    def symbol(t: Tree): String = occurrences(t).headOption.map(o => norm(o.symbol)).getOrElse("")
    def definitions(n: Name): Set[String] = occurrences(n).filter(_.role.isDefinition).map(o => norm(o.symbol)).toSet
    val infos: Map[String, s.SymbolInformation] = doc.symbols.map(i => i.symbol -> i).toMap
  }

  private val stale = new java.util.concurrent.ConcurrentLinkedQueue[String]()

  private def md5(text: String): String =
    java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(UTF_8)).map(b => f"${b & 255}%02X").mkString

  private def semanticdbFile(p: Path): Boolean = {
    val n = p.toAbsolutePath.toString
    n.endsWith(".semanticdb") && (n.contains("/meta/META-INF/") || n.contains("/test-meta/META-INF/") ||
      n.contains("/.semanticdb/"))
  }

  private lazy val sourceDocs: List[SourceDoc] = {
    val wanted = moves.map(_.source).toSet
    val found = mutable.LinkedHashMap.empty[String, SourceDoc]
    config.semanticdb.map(Paths.get(_)).filter(Files.isDirectory(_)).foreach { root =>
      val walk = Files.walk(root)
      try walk.iterator().asScala.filter(semanticdbFile).foreach { p =>
          s.TextDocuments.parseFrom(Files.readAllBytes(p)).documents.foreach { d =>
            if (!found.contains(d.uri) && d.occurrences.exists(o => o.role.isDefinition && wanted(norm(o.symbol)))) {
              val file = Paths.get(config.sourceroot).resolve(d.uri)
              if (Files.exists(file)) {
                val text = new String(Files.readAllBytes(file), UTF_8)
                if (d.md5.nonEmpty && d.md5 != md5(text)) stale.add(d.uri)
                else found(d.uri) = SourceDoc(d.uri, text, Input.VirtualFile(d.uri, text).parse[Source].get, d)
              }
            }
          }
        }
      finally walk.close()
    }
    found.values.toList
  }

  private def statName(stat: Stat): Option[Name] = stat match {
    case d: Defn.Def => Some(d.name)
    case d: Defn.Val => d.pats.collectFirst { case p: Pat.Var => p.name }
    case d: Defn.Var => d.pats.collectFirst { case p: Pat.Var => p.name }
    case d: Defn.Type => Some(d.name)
    case d: Defn.Object => Some(d.name)
    case d: Defn.Class => Some(d.name)
    case d: Defn.Trait => Some(d.name)
    case d: Defn.Enum => Some(d.name)
    case d: Defn.Given => Some(d.name)
    case d: Defn.GivenAlias => Some(d.name)
    case d: Decl.Def => Some(d.name)
    case _ => None
  }

  private final case class SourceObject(sd: SourceDoc, obj: Defn.Object, symbol: String, stats: List[(Stat, Option[Planned])],
      widened: List[Stat] = Nil) {
    def emptied: Boolean = stats.forall(_._2.isDefined)
  }

  private def firstExplicitParamType(sd: SourceDoc, d: Defn.Def): Option[String] =
    d.paramClauseGroups.flatMap(_.paramClauses).find(c => !c.mod.exists(m => m.is[Mod.Using] || m.is[Mod.Implicit]))
      .flatMap(_.values.headOption).flatMap { p =>
        sd.definitions(p.name).headOption.flatMap(sd.infos.get).flatMap { info =>
          info.signature match {
            case v: s.ValueSignature => v.tpe match {
                case t: s.TypeRef => Some(norm(t.symbol))
                case _ => None
              }
            case _ => None
          }
        }
      }

  private val nameless = new java.util.concurrent.ConcurrentLinkedQueue[String]()

  private lazy val sourceObjects: List[SourceObject] =
    moves.groupBy(_.source).toList.map { case (objSym, moves) =>
      val hit = sourceDocs.iterator.flatMap { sd =>
        sd.tree.collect { case o: Defn.Object if sd.definitions(o.name).contains(objSym) => sd -> o }
      }.toList.headOption
      require(hit.isDefined, s"MoveOntoType: no current SemanticDB defines $objSym; stale: ${stale.asScala.mkString(", ")}")
      val (sd, o) = hit.get
      val named = moves.filter(_.member.nonEmpty).map(m => m.member -> m).toMap
      val whole = moves.find(_.member.isEmpty)
      val unknown = named.keySet -- o.templ.body.stats.flatMap(statName).map(_.value).toSet
      require(unknown.isEmpty, s"MoveOntoType: $objSym has no member ${unknown.mkString(", ")}")
      val promotedTypes: Map[String, String] =
        moves.filter(_.form == "promote").map(p => (p.to + p.member + ".") -> (objSym + p.member + "#")).toMap
      def receiverOf(paramType: String, to: String): Boolean =
        sameType(paramType, to) || promotedTypes.get(to).contains(paramType)
      val stats = o.templ.body.stats.map { stat =>
        val name = statName(stat)
        val move = name.flatMap(n => named.get(n.value)).orElse(whole.filterNot(_ => stat.is[Import]))
        if (move.nonEmpty && name.isEmpty) nameless.add(s"$objSym: ${stat.syntax.linesIterator.next()}")
        val planned = move.filterNot(_.form == "stay").map { m =>
          val form = m.form match {
            case "auto" if isPackage(m.to) => "toplevel"
            case "auto" => stat match {
                case d: Defn.Def if firstExplicitParamType(sd, d).exists(receiverOf(_, m.to)) => "extension"
                case _ => "companion"
              }
            case "extension" if !stat.is[Defn.Def] => "companion"
            case f => f
          }
          val n = name.map(_.value).getOrElse("")
          val syms = name.map(sd.definitions).getOrElse(Set.empty)
          Planned(syms, n, if (m.name.nonEmpty && m.member.nonEmpty) m.name else n, m.to, form, m.file)
        }
        stat -> planned
      }
      val (carried, widened) = carryPrivateHelpers(sd, stats)
      SourceObject(sd, o, objSym, carried, widened)
    }

  private def isPrivate(stat: Stat): Boolean = StatMods.modsOf(stat).exists {
    case p: Mod.Private => p.within.is[Name.Anonymous]
    case _ => false
  }

  private object StatMods {
    def modsOf(stat: Stat): List[Mod] = stat match {
      case d: Defn.Def => d.mods
      case d: Defn.Val => d.mods
      case d: Defn.Var => d.mods
      case d: Defn.Type => d.mods
      case d: Defn.Object => d.mods
      case d: Defn.Class => d.mods
      case d: Defn.Trait => d.mods
      case d: Defn.Enum => d.mods
      case d: Defn.GivenAlias => d.mods
      case _ => Nil
    }
  }

  private def carryPrivateHelpers(sd: SourceDoc, stats: List[(Stat, Option[Planned])]): (List[(Stat, Option[Planned])], List[Stat]) = {
    def place(p: Planned): String = if (p.promote) p.to + p.name + "." else if (p.toplevel) "" else p.to
    val refs: List[(String, Int)] = sd.tree.collect {
      case n: Name if sd.definitions(n).isEmpty => sd.occurrences(n).map(o => o.symbol -> n.pos.start)
    }.flatten
    var current = stats
    var changed = true
    while (changed) {
      changed = false
      val ranges = current.collect { case (st, Some(p)) => (st.pos.start, st.pos.end, place(p)) }
      current = current.map {
        case (st, None) if isPrivate(st) && statName(st).nonEmpty =>
          val syms = sd.definitions(statName(st).get)
          val uses = refs.filter { case (s, at) => syms(s) && !(at >= st.pos.start && at < st.pos.end) }.map(_._2)
          val places = uses.map(at => ranges.find { case (a, b, _) => at >= a && at < b }.map(_._3))
          if (uses.nonEmpty && places.forall(_.isDefined) && places.flatten.distinct.size == 1 && places.head.get.nonEmpty) {
            changed = true
            val n = statName(st).get.value
            st -> Some(Planned(syms, n, n, places.head.get, "companion", ""))
          } else st -> None
        case other => other
      }
    }
    val ranges = current.collect { case (st, Some(_)) => (st.pos.start, st.pos.end) }
    val widened = current.collect {
      case (st, None) if isPrivate(st) && statName(st).nonEmpty &&
          refs.exists { case (s, at) =>
            sd.definitions(statName(st).get)(s) && ranges.exists { case (a, b) => at >= a && at < b }
          } => st
    }
    (current, widened)
  }

  private lazy val planned: Map[String, Planned] =
    sourceObjects.flatMap(_.stats.flatMap(_._2)).flatMap(p => p.symbols.map(_ -> p)).toMap

  private def plannedFor(sym: String): Option[Planned] = {
    val companion = if (sym.endsWith("#")) sym.dropRight(1) + "." else if (sym.endsWith(".")) sym.dropRight(1) + "#" else sym
    planned.get(sym).orElse(planned.get(sym.replaceAll("\\(\\+\\d+\\)\\.$", "()."))).orElse {
      planned.get(companion).filter(p => p.symbols.exists(s => s.endsWith("#")) && !sym.contains("("))
    }
  }

  private lazy val applyOf: Map[String, Planned] = sourceObjects.flatMap { so =>
    so.stats.collect { case (_, Some(p)) if p.name == "apply" && !p.extension => so.symbol -> p }
  }.toMap

  private lazy val deletedObjects: Set[String] = sourceObjects.filter(_.emptied).map(_.symbol).toSet

  private final class Rewriter(text: String, symbolOf: Tree => String, insideTarget: (Tree, String) => Boolean,
      clashes: String => Boolean = _ => false, taken: String => Boolean = _ => false, selfTarget: Option[String] = None) {
    def selfSelect(t: Tree): Boolean = selfTarget.exists { to =>
      val qual = t match {
        case s: Term.Select => Some(s.qual)
        case s: Type.Select => Some(s.qual)
        case _ => None
      }
      qual.exists(q => q.is[Term.Ref] && Set(termOf(to), termOf(to).dropRight(1) + "#")(symbolOf(nameOf(q))))
    }

    val imports = mutable.LinkedHashSet.empty[String]
    val lints = mutable.ListBuffer.empty[(Tree, String)]

    def moved(t: Tree): Option[Planned] = plannedFor(symbolOf(t)).orElse {
      applyOf.get(symbolOf(t)).filter { _ =>
        val ref = t.parent match {
          case Some(s: Term.Select) if s.name eq t => s
          case _ => t
        }
        ref.parent.exists {
          case a: Term.Apply => a.fun eq ref
          case _ => false
        }
      }
    }

    def inImport(t: Tree): Boolean = t.parent match {
      case Some(_: Importer) | Some(_: Import) | Some(_: Export) => true
      case Some(p) => inImport(p)
      case None => false
    }

    def isDefinitionName(n: Name): Boolean = n.parent.exists {
      case d: Defn.Def => d.name eq n
      case d: Defn.Object => d.name eq n
      case d: Defn.Class => d.name eq n
      case d: Defn.Trait => d.name eq n
      case d: Defn.Enum => d.name eq n
      case d: Defn.Type => d.name eq n
      case d: Defn.Given => d.name eq n
      case d: Defn.GivenAlias => d.name eq n
      case d: Decl.Def => d.name eq n
      case p: Pat.Var => p.name eq n
      case _ => false
    }

    def inExportRef(t: Tree): Boolean = {
      def walk(c: Tree, p: Option[Tree]): Boolean = p match {
        case Some(imp: Importer) => imp.parent.exists(_.is[Export]) && (imp.ref eq c)
        case Some(s: Term.Select) => walk(s, s.parent)
        case _ => false
      }
      walk(t, t.parent)
    }

    def site(t: Tree): Option[Tree] = t match {
      case n: Name if !isDefinitionName(n) && (!inImport(n) || inExportRef(n)) && moved(n).isDefined =>
        val p = moved(n).get
        val ref: Tree = n.parent match {
          case Some(s: Term.Select) if s.name eq n => s
          case Some(s: Type.Select) if s.name eq n => s
          case _ => n
        }
        if (p.extension) ref.parent match {
          case Some(a: Term.Apply) if a.fun eq ref => Some(a)
          case _ => Some(ref)
        }
        else Some(ref)
      case s @ (_: Term.Select | _: Type.Select) if selfSelect(s) && moved(nameOf(s)).isEmpty => Some(s)
      case _ => None
    }

    def sitesIn(tree: Tree): List[Tree] = tree.collect { case t => site(t) }.flatten.distinct

    def nameOf(t: Tree): Name = t match {
      case n: Name => n
      case s: Term.Select => s.name
      case s: Type.Select => s.name
      case a: Term.Apply => nameOf(a.fun)
      case other => other.collect { case n: Name => n }.head
    }

    def simpleReceiver(t: Term): Boolean = t match {
      case _: Term.Name | _: Term.Select | _: Term.This | _: Term.ApplyType | _: Lit.String | _: Term.Interpolate |
          _: Term.Tuple | _: Term.Placeholder => true
      case a: Term.Apply => simpleReceiver(a.fun)
      case _ => false
    }

    def rewrite(site: Tree, inner: List[Tree]): String =
      if (moved(nameOf(site)).isEmpty && selfSelect(site)) nameOf(site).value else rewriteMoved(site, inner)

    def rewriteMoved(site: Tree, inner: List[Tree]): String = {
      val p = moved(nameOf(site)).get
      if (p.extension) site match {
        case a: Term.Apply if a.argClause.values.nonEmpty && !a.argClause.values.head.is[Term.Assign] =>
          val recv = a.argClause.values.head
          val rest = a.argClause.values.tail
          val recvText = render(recv, inner)
          val r = if (simpleReceiver(recv)) recvText else s"($recvText)"
          val restText = if (rest.isEmpty) "" else "(" + renderRange(rest.head.pos.start, rest.last.pos.end, inner) + ")"
          s"$r.${p.newName}$restText"
        case other =>
          lints += other -> s"${p.name} became an extension of its first parameter: rewrite this reference by hand"
          text.substring(other.pos.start, other.pos.end)
      }
      else if (p.toplevel && taken(p.newName)) path(p.to).stripPrefix("com.histmersion.") + "." + p.newName
      else if (p.toplevel) {
        imports += path(p.to) + "." + p.newName
        p.newName
      } else if (insideTarget(site, p.to)) p.newName
      else if (clashes(p.to) && taken(p.newName)) s"${path(termOf(p.to))}.${p.newName}"
      else if (clashes(p.to)) {
        imports += path(termOf(p.to)) + "." + p.newName
        p.newName
      } else {
        val (ref, imp) = targetRef(p.to)
        imports += imp
        s"$ref.${p.newName}"
      }
    }

    def render(t: Tree, sites: List[Tree]): String = renderRange(t.pos.start, t.pos.end, sites)

    def renderRange(start: Int, end: Int, sites: List[Tree]): String = {
      val inside = sites.filter(s => s.pos.start >= start && s.pos.end <= end)
      val sb = new StringBuilder
      var at = start
      outermost(inside).sortBy(_.pos.start).foreach { s =>
        sb.append(text.substring(at, s.pos.start))
        sb.append(rewrite(s, inside.filter(i => (i ne s) && contains(s, i))))
        at = s.pos.end
      }
      sb.append(text.substring(at, end))
      sb.toString
    }

    def contains(outer: Tree, inner: Tree): Boolean = outer.pos.start <= inner.pos.start && outer.pos.end >= inner.pos.end

    def outermost(sites: List[Tree]): List[Tree] =
      sites.filterNot(s => sites.exists(o => (o ne s) && contains(o, s) && !(o.pos.start == s.pos.start && o.pos.end == s.pos.end)))
  }

  private def leadingStart(tokens: Tokens, stat: Tree): Int = {
    val idx = tokens.indexWhere(_.start == stat.pos.start)
    var i = idx - 1
    var start = stat.pos.start
    while (i >= 0 && (tokens(i).is[Token.Whitespace] || tokens(i).is[Token.Comment] || tokens(i).text.isEmpty)) {
      if (tokens(i).is[Token.Comment]) start = tokens(i).start
      i -= 1
    }
    start
  }

  private def lineStart(text: String, offset: Int): Int = {
    var i = offset
    while (i > 0 && text.charAt(i - 1) != '\n') i -= 1
    i
  }

  private def column(text: String, offset: Int): Int = offset - lineStart(text, offset)

  private def reindent(member: String, from: Int, to: Int): String = {
    val lines = member.split("\n", -1).toList
    (lines.head :: lines.tail.map { l =>
      if (l.trim.isEmpty) "" else (" " * to) + l.drop(l.takeWhile(_ == ' ').length.min(from))
    }).mkString("\n")
  }

  private def visiblePackages(tree: Tree): List[String] = {
    def walk(t: Tree, prefix: String): List[String] = t match {
      case p: Pkg =>
        val full = if (prefix.isEmpty) p.ref.syntax else prefix + "." + p.ref.syntax
        full :: p.stats.flatMap(walk(_, full))
      case s: Source => s.stats.flatMap(walk(_, prefix))
      case _ => Nil
    }
    walk(tree, "")
  }

  private final case class Extracted(planned: Planned, text: String, imports: List[String], start: Int, column: Int,
      order: Int, outerPackage: Option[String], isObject: Boolean, isType: Boolean)

  private lazy val extracted: List[Extracted] = {
    var order = 0
    sourceObjects.flatMap { so =>
      val sd = so.sd
      val tokens = sd.tree.tokens
      so.stats.collect { case (stat, Some(p)) =>
        val definedHere = stat.collect { case n: Name => sd.definitions(n) }.flatten.toSet
        val rw = new Rewriter(sd.text, sd.symbol, (_, to) => to == p.to && !p.toplevel,
          selfTarget = Option(p.to).filterNot(_ => p.toplevel))
        val sites = rw.sitesIn(stat).filter(s => s.pos.start >= stat.pos.start && s.pos.end <= stat.pos.end)
        val start = leadingStart(tokens, stat)
        val comments = if (config.keepComments) sd.text.substring(start, stat.pos.start) else ""
        val body = stat match {
          case d: Defn.Def if p.extension => extensionText(sd.text, d, p, rw, sites)
          case _ =>
            val t = rw.renderRange(stat.pos.start, stat.pos.end, sites)
            val n = statName(stat).get
            if (p.newName == p.name) t
            else t.substring(0, n.pos.start - stat.pos.start) + p.newName + t.substring(n.pos.end - stat.pos.start)
        }
        order += 1
        val imports = (neededImports(so, stat, p, definedHere) ++ rw.imports).distinct
        val outerPackage = sd.tree.collect { case pk: Pkg => pk.ref.syntax }.headOption
        Extracted(p, comments + body, imports, start, column(sd.text, start), order, outerPackage,
          stat.is[Defn.Object], stat.is[Defn.Type])
      }
    }
  }

  private def extensionText(text: String, d: Defn.Def, p: Planned, rw: Rewriter, sites: List[Tree]): String = {
    val clauses = d.paramClauseGroups.flatMap(_.paramClauses)
    val first = clauses.find(c => !c.mod.exists(m => m.is[Mod.Using] || m.is[Mod.Implicit])).get
    val recv = first.values.head
    val rest = first.values.tail
    val defToken = d.tokens.find(_.is[Token.KwDef]).get
    val mods = rw.renderRange(d.pos.start, defToken.start, sites)
    val tparams = d.paramClauseGroups.headOption.map(_.tparamClause).filter(_.values.nonEmpty)
      .map(tc => rw.renderRange(tc.pos.start, tc.pos.end, sites) + " ").getOrElse("")
    val recvText = rw.renderRange(recv.pos.start, recv.pos.end, sites)
    val restText = if (rest.isEmpty) "" else "(" + rw.renderRange(rest.head.pos.start, rest.last.pos.end, sites) + ")"
    val afterFirst = rw.renderRange(first.pos.end, d.pos.end, sites)
    s"extension $tparams($recvText) ${mods}def ${p.newName}$restText$afterFirst"
  }

  private def neededImports(so: SourceObject, stat: Stat, p: Planned, definedHere: Set[String]): List[String] = {
    val sd = so.sd
    val refs: Map[String, Boolean] = stat.collect {
      case n: Name if sd.definitions(n).isEmpty =>
        val selected = n.parent.exists {
          case s: Term.Select => s.name eq n
          case s: Type.Select => s.name eq n
          case _ => false
        }
        sd.occurrences(n).map(o => o.symbol -> selected)
    }.flatten.filter { case (sym, _) =>
      sym.nonEmpty && !sym.startsWith("local") && !ownedBy(definedHere, sym) && !sym.contains(".(") && !sym.contains("[")
    }
      .groupBy(_._1).map { case (k, v) => k -> v.forall(_._2) }
    val unresolved: Set[String] = stat.collect {
      case n: Name if sd.definitions(n).isEmpty && sd.occurrences(n).isEmpty && n.value.headOption.exists(_.isUpper) &&
          !n.parent.exists { case s: Term.Select => s.name eq n; case s: Type.Select => s.name eq n; case _ => false } =>
        n.value
    }.toSet
    val importers = sd.tree.collect { case i: Import => i }.filter(i => i.pos.end <= stat.pos.start)
      .filter(i => !i.parent.exists(_.is[Template]) || i.parent.exists(_ eq so.obj.templ))
      .flatMap(_.importers)
    val provided = mutable.Set.empty[String]
    val out = mutable.ListBuffer.empty[String]
    def moving(r: String): Boolean = plannedFor(r).isDefined || deletedObjects(r) || deletedObjects(termOf(r))
    importers.foreach { imp =>
      val refSym = imp.ref match {
        case s: Term.Select => sd.symbol(s.name)
        case n: Term.Name => sd.symbol(n)
        case _ => ""
      }
      val absRef =
        if (refSym.isEmpty || refSym.startsWith("local")) imp.ref.syntax
        else relocate(refSym).orElse(plannedFor(refSym).map(newPath)).getOrElse(path(refSym))
      imp.importees.foreach { importee =>
        val hits = importee match {
          case _: Importee.Given | _: Importee.GivenAll => List("given")
          case _: Importee.Wildcard =>
            refs.keys.filter(r => (strip(owner(r)) == strip(refSym) || strip(owner(norm(r))) == strip(norm(refSym))) &&
              !moving(r)).toList ++
              List(s"wildcard:$refSym").filter(_ => refSym.nonEmpty && !refSym.startsWith("com/histmersion/") &&
                !refSym.startsWith("scala/"))
          case i: Importee.Name =>
            val syms = sd.occurrences(i.name).map(s => strip(norm(s.symbol))).toSet
            refs.keys.filter(r =>
              (syms(strip(r)) || (lastName(r) == i.name.value && path(owner(r)) == imp.ref.syntax.replace("_root_.", ""))) &&
                !moving(r)
            ).toList ++ List(s"name:${i.name.value}").filter(_ => unresolved(i.name.value))
          case i: Importee.Rename =>
            val syms = sd.occurrences(i.name).map(s => strip(norm(s.symbol))).toSet
            refs.keys.filter(r => syms(strip(r)) && !moving(r)).toList
          case _ => Nil
        }
        provided ++= hits
        val importeeText = importee match {
          case _: Importee.Wildcard => "*"
          case other => sd.text.substring(other.pos.start, other.pos.end)
        }
        if (hits.nonEmpty) out += s"$absRef.$importeeText"
      }
    }
    refs.foreach { case (sym, onlySelected) =>
      if (!provided(sym) && !onlySelected && !deletedObjects(sym)) {
        val o = owner(sym)
        plannedFor(sym) match {
          case Some(m) if m.to == p.to && !m.toplevel => ()
          case Some(m) if m.toplevel => out += path(m.to) + "." + m.newName
          case Some(m) => out += targetRef(m.to)._2
          case None =>
            relocate(sym) match {
              case Some(np) => out += np
              case None =>
                if (isPackage(o) && !o.startsWith("scala/") && !o.startsWith("java/lang/")) out += path(o) + "." + lastName(sym)
                else if (o.endsWith(".") && !o.startsWith("scala/") && !termOf(p.to).startsWith(o)) out += path(o) + "." + lastName(sym)
            }
        }
      }
    }
    out.toList.distinct
  }

  private def ownedBy(defined: Set[String], sym: String): Boolean = {
    val s = strip(norm(sym))
    defined.exists { d =>
      val base = strip(norm(d))
      base.nonEmpty && (s == base || s.startsWith(base + ".") || s.startsWith(base + "#"))
    }
  }

  private def newPath(m: Planned): String =
    if (m.toplevel) path(m.to) + "." + m.newName else path(termOf(m.to)) + "." + m.newName

  private def relocate(sym: String): Option[String] = {
    var o = owner(sym)
    var rest = List(lastName(sym))
    while (o.nonEmpty && !isPackage(o)) {
      plannedFor(o).orElse(plannedFor(termOf(o))) match {
        case Some(m) => return Some((newPath(m) :: rest).mkString("."))
        case None =>
          rest = lastName(o) :: rest
          o = owner(o)
      }
    }
    None
  }

  private val emptiedFiles = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  private val reported = new java.util.concurrent.ConcurrentLinkedQueue[String]()

  override def beforeStart(): Unit = if (enabled) {
    sourceObjects.size
    require(nameless.isEmpty,
      "MoveOntoType: members without a name the rule cannot move (dissolve them by hand first):\n  " +
        nameless.asScala.mkString("\n  "))
    extracted.filter(_.planned.toplevel).groupBy(_.planned.file).foreach { case (file, members) =>
      require(file.nonEmpty, s"MoveOntoType: a toplevel move needs a file (${members.map(_.planned.name).mkString(", ")})")
      val pkg = path(members.head.planned.to)
      val outer =members.head.outerPackage.filter(o => pkg.startsWith(o + "."))
      val visible = pkg :: outer.toList
      val promoted = members.filter(_.planned.promote).map(_.planned.name).distinct
      val extras: Map[String, List[Extracted]] = promoted.map { x =>
        x -> extracted.filter(e => !e.planned.toplevel && e.planned.to == members.head.planned.to + x + ".").sortBy(_.order)
      }.toMap
      val all = (members ++ extras.values.flatten).flatMap(_.imports).distinct
      val wildcard = all.exists(_.endsWith(".*"))
      val imports = all.filterNot { i =>
        !wildcard && visible.exists(v => i.startsWith(v + ".") && !i.drop(v.length + 1).contains('.'))
      }.filterNot(i => promoted.exists(x => i == s"$pkg.$x"))
      val ordered = members.sortBy(_.order)
      val companionOf: Map[String, Extracted] = ordered.filter(m => m.planned.promote && m.isObject).map(m => m.planned.name -> m).toMap
      val body = ordered.zipWithIndex.map { case (m, i) =>
        val pair = ordered.lift(i - 1).exists(prev =>
          prev.planned.name == m.planned.name && prev.isType && m.isObject)
        val text = reindent(m.text, m.column, 0)
        val more = extras.getOrElse(m.planned.name, Nil)
        val withExtras =
          if (!m.planned.promote || more.isEmpty) text
          else if (companionOf.get(m.planned.name).exists(_ eq m)) withCompanionBody(text, more)
          else if (companionOf.contains(m.planned.name)) text
          else text + "\n\n" + withCompanionBody(s"object ${m.planned.name}", more)
        (if (i == 0) "" else if (pair) "\n" else "\n\n") + withExtras
      }.mkString
      val header = outer.fold(s"package $pkg")(o => s"package $o\npackage ${pkg.drop(o.length + 1)}")
      val content = s"$header\n\n" + imports.map("import " + _).mkString("\n") +
        (if (imports.isEmpty) "" else "\n\n") + body + "\n"
      val target = Paths.get(config.sourceroot).resolve(file)
      Files.createDirectories(target.getParent)
      Files.write(target, content.getBytes(UTF_8))
    }
  }

  private def withCompanionBody(objectText: String, more: List[Extracted]): String = {
    val stats = objectText.parse[Stat].toOption.collect { case o: Defn.Object => o.templ.body.stats }.getOrElse(Nil)
    if (stats.nonEmpty) {
      val col = column(objectText, stats.head.pos.start)
      val end = stats.last.pos.end
      objectText.substring(0, end) + more.map(m => "\n\n" + (" " * col) + reindent(m.text, m.column, col)).mkString +
        objectText.substring(end)
    } else objectText + ":" + more.zipWithIndex.map { case (m, i) =>
      (if (i == 0) "\n" else "\n\n") + "  " + reindent(m.text, m.column, 2)
    }.mkString
  }

  private lazy val promotedTargets: Set[String] =
    extracted.filter(_.planned.promote).map(e => e.planned.to + e.planned.name + ".").toSet

  private val insertedTargets = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  override def afterComplete(): Unit = if (enabled && config.report.nonEmpty) {
    val missing = extracted.filterNot(e => e.planned.toplevel || promotedTargets(e.planned.to)).map(_.planned.to)
      .distinct.filterNot(insertedTargets.contains).sorted
    val lines = missing.map("LOST: no file defines the target\t" + _) ++
      emptiedFiles.asScala.toList.sorted.map("emptied\t" + _) ++ reported.asScala.toList.sorted.map("lint\t" + _)
    Files.write(Paths.get(config.report), lines.mkString("", "\n", "\n").getBytes(UTF_8))
  }

  override def fix(implicit doc: SemanticDocument): Patch = if (!enabled) Patch.empty else {
    val text = doc.input.text
    val uri = doc.input match {
      case f: Input.VirtualFile => f.path
      case f: Input.File => f.path.toString
      case other => other.syntax
    }
    def insideTarget(t: Tree, to: String): Boolean = t.parent.exists {
      case o: Defn.Object if sym(o.name) == termOf(to) => true
      case p => insideTarget(p, to)
    }
    def selected(n: Name): Boolean = n.parent.exists {
      case s: Term.Select => s.name eq n
      case s: Type.Select => s.name eq n
      case _ => false
    }
    val bound: Map[String, Set[String]] = doc.tree.collect {
      case n: Name if n.value.headOption.exists(_.isUpper) && !selected(n) => n.value -> sym(n)
    }.filter { case (_, s) => s.nonEmpty && !s.startsWith("local") }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).toSet }
    def headOf(to: String): String = targetRef(to)._1.split('.').head
    def topOf(to: String): String = topLevelOf(termOf(to))
    val finder = new Rewriter(text, sym(_), insideTarget)
    val newTargets: Map[String, Int] = finder.sitesIn(doc.tree).flatMap(s => finder.moved(finder.nameOf(s)))
      .filterNot(p => p.toplevel || p.extension).groupBy(p => topOf(p.to)).map { case (k, v) => k -> v.size }
    def clashes(to: String): Boolean = {
      val top = topOf(to)
      val boundElsewhere =
        bound.getOrElse(headOf(to), Set.empty).exists(s => s != top && s != top.dropRight(1) + "#" && !planned.contains(s))
      val rival = newTargets.exists { case (t, n) =>
        t != top && lastName(t) == lastName(top) && (n > newTargets.getOrElse(top, 0) || (n == newTargets.getOrElse(top, 0) && t < top))
      }
      val boundHere = bound.getOrElse(headOf(to), Set.empty).exists(s => s == top || s == top.dropRight(1) + "#")
      boundElsewhere || (rival && !boundHere)
    }
    val names: Set[String] = doc.tree.collect {
      case n: Name if sym(n).nonEmpty && plannedFor(sym(n)).isEmpty && !finder.inImport(n) => n.value
    }.toSet
    val rw = new Rewriter(text, sym(_), insideTarget, clashes, names)
    val tokens = doc.tree.tokens

    val removedRanges = mutable.ListBuffer.empty[(Int, Int)]
    val removals = sourceObjects.flatMap { so =>
      doc.tree.collect { case o: Defn.Object if sym(o.name) == so.symbol => o }.headOption.toList.flatMap { obj =>
        if (so.emptied) {
          val start = leadingStart(tokens, obj)
          removedRanges += ((start, obj.pos.end))
          List(removeRange(doc, lineStart(text, start), obj.pos.end))
        } else so.stats.collect { case (st, Some(_)) =>
          obj.templ.body.stats.find(x => x.pos.start == st.pos.start && x.pos.end == st.pos.end).toList.map { live =>
            val start = leadingStart(tokens, live)
            removedRanges += ((start, live.pos.end))
            removeRange(doc, lineStart(text, start), live.pos.end)
          }
        }.flatten
      }
    }
    def removed(t: Tree): Boolean = removedRanges.exists { case (a, b) => t.pos.start >= a && t.pos.end <= b }
    val topLevel = doc.tree.collect { case p: Pkg => p.stats }.flatten.filterNot(s => s.is[Import] || s.is[Pkg])
    if (removedRanges.nonEmpty && topLevel.nonEmpty && topLevel.forall(removed)) emptiedFiles.add(uri)

    def exported(i: Importee): Boolean = i.parent.exists(_.parent.exists(_.is[Export]))
    def importeeName(i: Importee): Option[Name] = i match {
      case n: Importee.Name => Some(n.name)
      case r: Importee.Rename => Some(r.name)
      case _ => None
    }
    def exportedPlan(imp: Importer, n: Name): Option[Planned] = plannedFor(sym(n)).orElse {
      val owner = Option(refSymbol(imp)).filter(_.nonEmpty).orElse {
        val last = imp.ref.syntax.split('.').last
        sourceObjects.map(_.symbol).find(s => lastName(s) == last)
      }
      owner.flatMap(o => List(".", "#", "().").flatMap(d => plannedFor(o + n.value + d)).headOption)
    }
    def exportOfMoved(imp: Importer): Boolean =
      imp.parent.exists(_.is[Export]) && imp.importees.flatMap(importeeName).exists(n => exportedPlan(imp, n).isDefined)
    val handledExports = doc.tree.collect { case imp: Importer if exportOfMoved(imp) => imp }
    def inHandledExport(t: Tree): Boolean = handledExports.exists(imp => rw.contains(imp, t))

    val sites = rw.sitesIn(doc.tree).filterNot(removed).filterNot(inHandledExport)
    val refPatches = rw.outermost(sites).map(s => Patch.replaceTree(s, rw.rewrite(s, sites.filter(i => (i ne s) && rw.contains(s, i)))))
    rw.lints.foreach { case (t, msg) => reported.add(s"$uri:${t.pos.startLine + 1}\t$msg") }

    val exportImports = mutable.ListBuffer.empty[String]
    val importPatches = doc.tree.collect { case t if !removed(t) => t }.collect {
      case imp: Importer if exportOfMoved(imp) =>
        val plans = imp.importees.flatMap(importeeName).flatMap(n => exportedPlan(imp, n).map(n -> _))
        val p = plans.head._2
        val ref =
          if (p.toplevel) path(p.to)
          else if (clashes(p.to)) path(termOf(p.to))
          else { exportImports += targetRef(p.to)._2; targetRef(p.to)._1 }
        Patch.replaceTree(imp.ref, ref) + plans.collect {
          case (n, q) if q.newName != q.name => Patch.replaceTree(n, q.newName)
        }.asPatch
      case i: Importee.Name if !exported(i) && (plannedFor(sym(i.name)).isDefined || deletedObjects(sym(i.name))) =>
        Patch.removeImportee(i)
      case i: Importee.Rename if !exported(i) && plannedFor(sym(i.name)).isDefined => Patch.removeImportee(i)
      case i: Importee.Wildcard if i.parent.exists {
            case imp: Importer => deletedObjects(refSymbol(imp))
            case _ => false
          } => Patch.removeImportee(i)
      case imp: Importer if !imp.parent.exists(_.is[Export]) && importerPathMoved(imp).isDefined =>
        val (sel, p) = importerPathMoved(imp).get
        Patch.replaceTree(sel, if (p.toplevel) path(p.to) + "." + p.newName else path(termOf(p.to)) + "." + p.newName)
    }

    val (inserts, insertImports) = insertPatches(doc, text)

    val visible = visiblePackages(doc.tree)
    val existing = doc.tree.collect { case i: Importer =>
      i.syntax :: i.importees.collect {
        case n: Importee.Name => s"${i.ref.syntax}.${n.name.value}"
        case _: Importee.Wildcard => s"${i.ref.syntax}.*"
      }
    }.flatten.toSet
    def single(imp: String): List[String] = dialects.Scala3(imp).parse[Importer].toOption match {
      case Some(i) if i.importees.forall(_.is[Importee.Name]) =>
        i.importees.collect { case n: Importee.Name => s"${i.ref.syntax}.${n.name.value}" }
      case _ => List(imp)
    }
    val importAdds = (rw.imports.toList ++ insertImports ++ exportImports).flatMap(single).distinct.filterNot { imp =>
      val plain = imp.matches("[\\w.`]+")
      val pkg = imp.split('.').dropRight(1).mkString(".")
      existing(imp) || (plain && (visible.contains(pkg) || existing(pkg + ".*") || existing(pkg + "._")))
    }
    def kept(i: Import): Boolean = i.importers.forall { imp =>
      importerPathMoved(imp).isEmpty && imp.importees.forall {
        case n: Importee.Name => plannedFor(sym(n.name)).isEmpty && !deletedObjects(sym(n.name))
        case r: Importee.Rename => plannedFor(sym(r.name)).isEmpty
        case _: Importee.Wildcard => !deletedObjects(refSymbol(imp))
        case _ => true
      }
    }
    val anchorImport = doc.tree.collect { case i: Import if i.parent.exists(_.is[Pkg]) && kept(i) => i }
      .sortBy(_.pos.end).lastOption
    val innermost = doc.tree.collect { case p: Pkg => p }.sortBy(_.ref.pos.start).lastOption
    val importPatch =
      if (importAdds.isEmpty) Nil
      else (anchorImport, innermost) match {
        case (Some(last), _) => List(Patch.addRight(last, importAdds.map("\nimport " + _).mkString))
        case (None, Some(p)) => List(Patch.addRight(p.ref, "\n" + importAdds.map("\nimport " + _).mkString))
        case _ => importAdds.map(i => Patch.addGlobalImport(dialects.Scala3(i).parse[Importer].get))
      }

    val widenPatches = sourceObjects.filter(so => doc.tree.collect { case o: Defn.Object if sym(o.name) == so.symbol => o }.nonEmpty)
      .flatMap { so =>
        val pkg = lastName(owner(topLevelOf(so.symbol)))
        so.widened.flatMap { st =>
          doc.tree.collect { case s: Stat if s.pos.start == st.pos.start && s.pos.end == st.pos.end => s }.headOption.toList
            .flatMap(StatMods.modsOf).collect { case p: Mod.Private if p.within.is[Name.Anonymous] =>
              Patch.replaceTree(p, s"private[$pkg]")
            }
        }
      }

    (removals ++ refPatches ++ importPatches ++ List(inserts) ++ importPatch ++ widenPatches).asPatch
  }

  private def refSymbol(imp: Importer)(implicit doc: SemanticDocument): String = imp.ref match {
    case s: Term.Select => sym(s.name)
    case n: Term.Name => sym(n)
    case _ => ""
  }

  private def importerPathMoved(imp: Importer)(implicit doc: SemanticDocument): Option[(Term, Planned)] = {
    def walk(t: Term): Option[(Term, Planned)] = t match {
      case s: Term.Select => plannedFor(sym(s.name)).map(s -> _).orElse(walk(s.qual))
      case n: Term.Name => plannedFor(sym(n)).map(n -> _)
      case _ => None
    }
    walk(imp.ref)
  }

  private def removeRange(doc: SemanticDocument, start: Int, end: Int): Patch = {
    val text = doc.input.text
    var b = start
    while (b > 0 && Character.isWhitespace(text.charAt(b - 1))) b -= 1
    Patch.removeTokens(doc.tree.tokens.filter(t => t.start >= b && t.end <= end && !t.is[Token.BOF] && !t.is[Token.EOF]))
  }

  private def insertPatches(doc: SemanticDocument, text: String): (Patch, List[String]) = {
    implicit val d: SemanticDocument = doc
    val imports = mutable.ListBuffer.empty[String]
    val patches = extracted.filterNot(e => e.planned.toplevel || promotedTargets(e.planned.to)).groupBy(_.planned.to).toList.flatMap { case (to, members) =>
      val ordered = members.sortBy(_.order)
      val obj = doc.tree.collect { case o: Defn.Object if sym(o.name) == termOf(to) => o }.headOption
      val typeSym = termOf(to).dropRight(1) + "#"
      lazy val tpe = doc.tree.collect {
        case c: Defn.Class if sym(c.name) == typeSym => c: Stat
        case c: Defn.Trait if sym(c.name) == typeSym => c: Stat
        case c: Defn.Enum if sym(c.name) == typeSym => c: Stat
        case c: Defn.Type if sym(c.name) == typeSym => c: Stat
      }.headOption
      if (obj.isDefined || tpe.isDefined) insertedTargets.add(to)
      def bodyAt(col: Int, first: String) = ordered.zipWithIndex.map { case (m, i) =>
        val pair = ordered.lift(i - 1).exists(prev =>
          prev.planned.name == m.planned.name && prev.isType && m.isObject)
        (if (i == 0) first else if (pair) "\n" else "\n\n") + (" " * col) + reindent(m.text, m.column, col)
      }.mkString
      obj match {
        case Some(o) =>
          imports ++= ordered.flatMap(_.imports)
          val stats = o.templ.body.stats
          if (stats.nonEmpty) List(Patch.addRight(stats.last, bodyAt(column(text, stats.head.pos.start), "\n\n")))
          else {
            val col = column(text, o.pos.start) + 2
            if (o.templ.tokens.lastOption.exists(_.is[Token.RightBrace]))
              List(Patch.addLeft(o.templ.tokens.last, bodyAt(col, "\n") + "\n" + (" " * (col - 2))))
            else List(Patch.addRight(o, ":" + bodyAt(col, "\n")))
          }
        case None =>
          tpe.toList.map { t =>
            imports ++= ordered.flatMap(_.imports)
            val col0 = column(text, t.pos.start)
            Patch.addRight(t, "\n\n" + (" " * col0) + s"object ${lastName(to)}:" + bodyAt(col0 + 2, "\n"))
          }
      }
    }
    (patches.asPatch, imports.toList.distinct)
  }
}
