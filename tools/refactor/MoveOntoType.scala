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
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  final case class Planned(symbols: Set[String], name: String, newName: String, to: String, form: String, file: String) {
    def toplevel: Boolean = form == "toplevel"
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

  private final case class SourceObject(sd: SourceDoc, obj: Defn.Object, symbol: String, stats: List[(Stat, Option[Planned])]) {
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
      val stats = o.templ.body.stats.map { stat =>
        val name = statName(stat)
        val move = name.flatMap(n => named.get(n.value)).orElse(whole)
        val planned = move.filterNot(_.form == "stay").map { m =>
          val form = m.form match {
            case "auto" if isPackage(m.to) => "toplevel"
            case "auto" => stat match {
                case d: Defn.Def if firstExplicitParamType(sd, d).exists(sameType(_, m.to)) => "extension"
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
      SourceObject(sd, o, objSym, stats)
    }

  private lazy val planned: Map[String, Planned] =
    sourceObjects.flatMap(_.stats.flatMap(_._2)).flatMap(p => p.symbols.map(_ -> p)).toMap

  private def plannedFor(sym: String): Option[Planned] = {
    val companion = if (sym.endsWith("#")) sym.dropRight(1) + "." else if (sym.endsWith(".")) sym.dropRight(1) + "#" else sym
    planned.get(sym).orElse(planned.get(sym.replaceAll("\\(\\+\\d+\\)\\.$", "()."))).orElse {
      planned.get(companion).filter(p => p.symbols.exists(s => s.endsWith("#")) && !sym.contains("("))
    }
  }

  private lazy val deletedObjects: Set[String] = sourceObjects.filter(_.emptied).map(_.symbol).toSet

  private final class Rewriter(text: String, symbolOf: Tree => String, insideTarget: (Tree, String) => Boolean) {
    val imports = mutable.LinkedHashSet.empty[String]
    val lints = mutable.ListBuffer.empty[(Tree, String)]

    def moved(t: Tree): Option[Planned] = plannedFor(symbolOf(t))

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

    def site(t: Tree): Option[Tree] = t match {
      case n: Name if !isDefinitionName(n) && !inImport(n) && moved(n).isDefined =>
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

    def rewrite(site: Tree, inner: List[Tree]): String = {
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
      else if (p.toplevel) {
        imports += path(p.to) + "." + p.newName
        p.newName
      } else if (insideTarget(site, p.to)) p.newName
      else {
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
      order: Int)

  private lazy val extracted: List[Extracted] = {
    var order = 0
    sourceObjects.flatMap { so =>
      val sd = so.sd
      val tokens = sd.tree.tokens
      so.stats.collect { case (stat, Some(p)) =>
        val definedHere = stat.collect { case n: Name => sd.definitions(n) }.flatten.toSet
        val rw = new Rewriter(sd.text, sd.symbol, (_, to) => to == p.to && !p.toplevel)
        val sites = rw.sitesIn(stat).filter(s => s.pos.start >= stat.pos.start && s.pos.end <= stat.pos.end)
        val start = leadingStart(tokens, stat)
        val comments = sd.text.substring(start, stat.pos.start)
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
        Extracted(p, comments + body, imports, start, column(sd.text, start), order)
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
    }.flatten.filter { case (sym, _) => sym.nonEmpty && !sym.startsWith("local") && !definedHere(sym) }
      .groupBy(_._1).map { case (k, v) => k -> v.forall(_._2) }
    val importers = sd.tree.collect { case i: Import => i }.filter(i => i.pos.end <= stat.pos.start)
      .filter(i => !i.parent.exists(_.is[Template]) || i.parent.exists(_ eq so.obj.templ))
      .flatMap(_.importers)
    val provided = mutable.Set.empty[String]
    val out = mutable.ListBuffer.empty[String]
    importers.foreach { imp =>
      val refSym = imp.ref match {
        case s: Term.Select => sd.symbol(s.name)
        case n: Term.Name => sd.symbol(n)
        case _ => ""
      }
      val hits = imp.importees.flatMap {
        case _: Importee.Given | _: Importee.GivenAll => List("given")
        case _: Importee.Wildcard =>
          refs.keys.filter(r => strip(owner(r)) == strip(refSym)).toList
        case i: Importee.Name =>
          val syms = sd.occurrences(i.name).map(s => strip(s.symbol)).toSet
          refs.keys.filter(r => syms(strip(r))).toList
        case i: Importee.Rename =>
          val syms = sd.occurrences(i.name).map(s => strip(s.symbol)).toSet
          refs.keys.filter(r => syms(strip(r))).toList
        case _ => Nil
      }
      provided ++= hits
      if (hits.nonEmpty) out += imp.syntax
    }
    refs.foreach { case (sym, onlySelected) =>
      if (!provided(sym) && !onlySelected) {
        val o = owner(sym)
        plannedFor(sym) match {
          case Some(m) if m.to == p.to && !m.toplevel => ()
          case Some(m) if m.toplevel => out += path(m.to) + "." + m.newName
          case Some(m) => out += targetRef(m.to)._2
          case None =>
            if (isPackage(o) && !o.startsWith("scala/") && !o.startsWith("java/lang/")) out += path(o) + "." + lastName(sym)
            else if (o.endsWith(".") && so.symbol.startsWith(o)) out += path(o) + "." + lastName(sym)
        }
      }
    }
    out.toList.distinct
  }

  private val emptiedFiles = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  private val reported = new java.util.concurrent.ConcurrentLinkedQueue[String]()

  override def beforeStart(): Unit = if (enabled) {
    extracted.filter(_.planned.toplevel).groupBy(_.planned.file).foreach { case (file, members) =>
      require(file.nonEmpty, s"MoveOntoType: a toplevel move needs a file (${members.map(_.planned.name).mkString(", ")})")
      val pkg = path(members.head.planned.to)
      val imports = members.flatMap(_.imports).distinct.filterNot { i =>
        i.startsWith(pkg + ".") && !i.drop(pkg.length + 1).contains('.')
      }
      val body = members.sortBy(_.order).map(m => reindent(m.text, m.column, 0)).mkString("\n\n")
      val content = s"package $pkg\n\n" + imports.map("import " + _).mkString("\n") +
        (if (imports.isEmpty) "" else "\n\n") + body + "\n"
      val target = Paths.get(config.sourceroot).resolve(file)
      Files.createDirectories(target.getParent)
      Files.write(target, content.getBytes(UTF_8))
    }
  }

  override def afterComplete(): Unit = if (enabled && config.report.nonEmpty) {
    val lines = emptiedFiles.asScala.toList.sorted.map("emptied\t" + _) ++ reported.asScala.toList.sorted.map("lint\t" + _)
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
    val rw = new Rewriter(text, sym(_), insideTarget)
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

    val sites = rw.sitesIn(doc.tree).filterNot(removed)
    val refPatches = rw.outermost(sites).map(s => Patch.replaceTree(s, rw.rewrite(s, sites.filter(i => (i ne s) && rw.contains(s, i)))))
    rw.lints.foreach { case (t, msg) => reported.add(s"$uri:${t.pos.startLine + 1}\t$msg") }

    val importPatches = doc.tree.collect {
      case i: Importee.Name if plannedFor(sym(i.name)).isDefined || deletedObjects(sym(i.name)) =>
        Patch.removeImportee(i)
      case i: Importee.Rename if plannedFor(sym(i.name)).isDefined => Patch.removeImportee(i)
      case i: Importee.Wildcard if i.parent.exists {
            case imp: Importer => deletedObjects(refSymbol(imp))
            case _ => false
          } => Patch.removeImportee(i)
      case imp: Importer if importerPathMoved(imp).isDefined =>
        val (sel, p) = importerPathMoved(imp).get
        Patch.replaceTree(sel, if (p.toplevel) path(p.to) + "." + p.newName else path(termOf(p.to)) + "." + p.newName)
    }

    val (inserts, insertImports) = insertPatches(doc, text)

    val visible = visiblePackages(doc.tree)
    val existing = doc.tree.collect { case i: Importer => i.syntax }.toSet
    val importAdds = (rw.imports.toList ++ insertImports).distinct.filterNot { imp =>
      val plain = imp.matches("[\\w.`]+")
      val pkg = imp.split('.').dropRight(1).mkString(".")
      existing(imp) || (plain && (visible.contains(pkg) || existing(pkg + ".*") || existing(pkg + "._")))
    }.map(i => Patch.addGlobalImport(i.parse[Importer].get))

    (removals ++ refPatches ++ importPatches ++ List(inserts) ++ importAdds).asPatch
  }

  private def refSymbol(imp: Importer)(implicit doc: SemanticDocument): String = imp.ref match {
    case s: Term.Select => sym(s.name)
    case n: Term.Name => sym(n)
    case _ => ""
  }

  private def importerPathMoved(imp: Importer)(implicit doc: SemanticDocument): Option[(Term, Planned)] = {
    def walk(t: Term): Option[(Term, Planned)] = t match {
      case s: Term.Select => plannedFor(sym(s.name)).map(s -> _).orElse(walk(s.qual))
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
    val patches = extracted.filterNot(_.planned.toplevel).groupBy(_.planned.to).toList.flatMap { case (to, members) =>
      val ordered = members.sortBy(_.order)
      val obj = doc.tree.collect { case o: Defn.Object if sym(o.name) == termOf(to) => o }.headOption
      lazy val tpe = doc.tree.collect {
        case c: Defn.Class if sym(c.name) == to => c: Stat
        case c: Defn.Trait if sym(c.name) == to => c: Stat
        case c: Defn.Enum if sym(c.name) == to => c: Stat
        case c: Defn.Type if sym(c.name) == to => c: Stat
      }.headOption
      def bodyAt(col: Int, sep: String) = ordered.map(m => sep + (" " * col) + reindent(m.text, m.column, col)).mkString
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
