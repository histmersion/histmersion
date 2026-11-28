import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object SplitWireTwin {
  case class Twin(from: String = "", to: String = "")
  object Twin {
    implicit val surface: generic.Surface[Twin] = generic.deriveSurface[Twin]
    implicit val decoder: ConfDecoder[Twin] = generic.deriveDecoder(Twin()).noTypos
  }
  case class Config(
      report: String = "",
      sourceroot: String = ".",
      twinRoot: String = "",
      twins: List[Twin] = Nil,
      types: Map[String, String] = Map.empty,
      redeclare: Map[String, String] = Map.empty,
      redeclareImports: List[String] = Nil,
      visible: List[String] = Nil,
      codecDerives: List[String] = List("JsonCodec", "KindlingsSchema", "Schema", "com.histmersion.api.json.Schema"),
      derivesAs: Map[String, String] = Map("com.histmersion.api.json.Schema" -> "Schema"),
      derivesImports: Map[String, String] = Map.empty,
      twinDerives: List[String] = Nil,
      twinImports: List[String] = Nil,
      codecGivens: List[String] = List("Schema", "JsonValueCodec", "JsonCodec", "Codec", "KindlingsSchema"),
      stripImports: List[String] = Nil,
      strip: Boolean = true
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val DescriptionImport = "sttp.tapir.Schema.annotations.description"
  val NameImports = List("sttp.tapir.Schema.SName", "sttp.tapir.Schema.annotations.customise")

  def fqn(s: Symbol): String =
    s.value.stripSuffix("#").stripSuffix(".").stripSuffix("/").replace('/', '.').replace('#', '.')
      .replaceAll("[\\w-]+\\$package\\.", "")

  def simple(f: String): String = f.substring(f.lastIndexOf('.') + 1)

  def packageOf(f: String): String = if (f.contains('.')) f.substring(0, f.lastIndexOf('.')) else ""

  private val Predefined = "^(?:scala\\.(?:package\\.|Predef\\.)?|java\\.lang\\.)[A-Za-z]\\w*$".r

  def predefined(f: String): Boolean = Predefined.pattern.matcher(f).matches()

  final case class Edit(start: Int, end: Int, text: String)

  def render(code: String, from: Int, to: Int, edits: List[Edit]): String = {
    val sb = new StringBuilder
    var at = from
    edits.filter(e => e.start >= from && e.end <= to).sortBy(_.start).foreach { e =>
      if (e.start >= at) {
        sb.append(code.substring(at, e.start)).append(e.text)
        at = e.end
      }
    }
    sb.append(code.substring(at, to)).toString
  }
}

class SplitWireTwin(config: SplitWireTwin.Config) extends SemanticRule("SplitWireTwin") {
  import SplitWireTwin._

  def this() = this(SplitWireTwin.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("SplitWireTwin")(SplitWireTwin.Config()).map(new SplitWireTwin(_))

  private val reported = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  private val written = new java.util.concurrent.ConcurrentHashMap[String, String]()

  private def report(kind: String, from: String, rest: String*): Unit =
    reported.add((kind +: from +: rest).mkString("\t"))

  private def twinOf(f: String): Option[Twin] = config.twins.find(t => f == t.from || f.startsWith(t.from + "."))

  private def target(f: String, own: Twin): Option[String] =
    config.types
      .get(f)
      .orElse(twinOf(f).map(t => t.to + f.drop(t.from.length)))
      .orElse(config.types.collectFirst { case (k, v) if f.startsWith(k + ".") => v + f.drop(k.length) })
      .orElse(if (config.redeclare.contains(f)) Some(own.to + "." + simple(f)) else None)
      .orElse(config.visible.find(v => f == v || (v.endsWith(".") && f.startsWith(v))).map(_ => f))
      .orElse(if (predefined(f)) Some(f) else None)

  private def elems(t: Tree): Option[List[Name]] = t match {
    case n: Term.Name => Some(List(n))
    case n: Type.Name => Some(List(n))
    case Term.Select(q, n) => elems(q).map(_ :+ n)
    case Type.Select(q, n) => elems(q).map(_ :+ n)
    case _ => None
  }

  private def isDefinitionName(n: Tree): Boolean = n.parent.exists {
    case d: Defn.Class => d.name eq n
    case d: Defn.Object => d.name eq n
    case d: Defn.Type => d.name eq n
    case d: Defn.Trait => d.name eq n
    case d: Defn.Enum => d.name eq n
    case d: Defn.EnumCase => d.name eq n
    case d: Defn.Def => d.name eq n
    case d: Term.Param => d.name eq n
    case d: Type.Param => d.name eq n
    case _: Pat.Var => true
    case _ => false
  }

  private def references(tree: Tree, own: Twin, skip: Set[Tree])(implicit
      doc: SemanticDocument
  ): (List[Edit], Set[String], List[(String, String)], Set[String]) = {
    var edits = List.empty[Edit]
    var imports = Set.empty[String]
    var missing = List.empty[(String, String)]
    var redeclared = Set.empty[String]
    def visit(t: Tree): Unit =
      if (skip.exists(_ eq t)) ()
      else
        elems(t) match {
          case Some(ns) if !isDefinitionName(t) =>
            val ss = ns.map(_.symbol)
            val last = ss.last
            if (last.isNone || last.isLocal) ()
            else {
              val f = fqn(last)
              val first = ss.indexWhere(s => !s.value.endsWith("/"))
              val typed = if (first < 0) None else (first until ns.size).find(i => config.types.contains(fqn(ss(i))))
              def ownLocal(s: Symbol) = { val g = fqn(s); g == own.from || g.startsWith(own.from + ".") }
              def ownText(i: Int) = (ns.drop(i).zipWithIndex.map { case (n, k) =>
                if (k == 0 && fqn(ss(i)) == own.from) simple(own.to) else n.value
              }).mkString(".")
              if (typed.isEmpty && first >= 0 && ownLocal(ss(first))) {
                if (ownText(first) != t.syntax) edits ::= Edit(t.pos.start, t.pos.end, ownText(first))
              } else {
                val resolved = if (first < 0) None
                else typed.map(i => i -> config.types(fqn(ss(i)))).orElse(
                  (first until ns.size).iterator.flatMap(i => target(fqn(ss(i)), own).map(i -> _)).nextOption()
                )
                resolved match {
                  case Some((i, to0)) if i > first && !config.redeclare.contains(fqn(ss(i))) && {
                        val full = (to0 :: ns.drop(i + 1).map(_.value)).mkString(".")
                        full.endsWith("." + ns.drop(first).map(_.value).mkString("."))
                      } =>
                    val written = ns.drop(first).map(_.value)
                    val full = (to0 :: ns.drop(i + 1).map(_.value)).mkString(".")
                    val holder = full.stripSuffix("." + written.tail.mkString("."))
                    if (!predefined(holder) && packageOf(holder) != packageOf(own.to)) imports += holder
                    val text = written.mkString(".")
                    if (text != t.syntax) edits ::= Edit(t.pos.start, t.pos.end, text)
                  case Some((i, to)) =>
                    if (config.redeclare.contains(fqn(ss(i)))) redeclared += fqn(ss(i))
                    val local = to.startsWith(own.to + ".")
                    val head = if (local) simple(own.to) + "." + to.drop(own.to.length + 1) else simple(to)
                    val text = (head :: ns.drop(i + 1).map(_.value)).mkString(".")
                    if (!local && !predefined(to) && packageOf(to) != packageOf(own.to) && to != "com.histmersion." + simple(to))
                      imports += to
                    if (text != t.syntax) edits ::= Edit(t.pos.start, t.pos.end, text)
                  case None =>
                    missing ::= (t.syntax -> last.value)
                }
              }
            }
          case Some(_) => ()
          case None => t.children.foreach(visit)
        }
    visit(tree)
    (edits, imports, missing, redeclared)
  }

  private def notAnnotation(m: Mod): Boolean = !m.is[Mod.Annot]

  private def startAfterAnnotations(d: Tree, mods: List[Mod]): Int = {
    val annotEnd = mods.collect { case a: Mod.Annot => a.pos.end }.foldLeft(d.pos.start)(math.max)
    d.tokens.find(t => t.pos.start >= annotEnd && !t.is[Token.Whitespace] && !t.is[Token.Comment]).map(_.pos.start)
      .getOrElse(d.pos.start)
  }

  private def isCodecGiven(s: Stat): Boolean = s match {
    case g: Defn.GivenAlias =>
      g.decltpe match {
        case Type.Apply.After_4_6_0(Type.Name(n), _) => config.codecGivens.contains(n)
        case Type.Apply.After_4_6_0(Type.Select(_, Type.Name(n)), _) => config.codecGivens.contains(n)
        case _ => false
      }
    case _ => false
  }

  private def descriptions(body: Term): Either[String, (Map[String, String], Option[String])] = {
    def loop(t: Term, acc: Map[String, String], top: Option[String]): Either[String, (Map[String, String], Option[String])] =
      t match {
        case Term.Apply.After_4_6_0(
              Term.Apply.After_4_6_0(Term.Select(inner, Term.Name("modify")), Term.ArgClause(List(path), _)),
              Term.ArgClause(List(fn), _)
            ) =>
          (path, fn) match {
            case (
                  Term.AnonymousFunction(Term.Select(Term.Placeholder(), Term.Name(field))),
                  Term.AnonymousFunction(
                    Term.Apply.After_4_6_0(
                      Term.Select(Term.Placeholder(), Term.Name("description")),
                      Term.ArgClause(List(lit: Lit.String), _)
                    )
                  )
                ) =>
              loop(inner, acc + (field -> lit.syntax), top)
            case _ => Left(s"modify(${path.syntax})(${fn.syntax})")
          }
        case Term.Apply.After_4_6_0(Term.Select(inner, Term.Name("description")), Term.ArgClause(List(lit: Lit.String), _)) =>
          loop(inner, acc, Some(lit.syntax))
        case Term.Select(Term.ApplyType.After_4_6_0(Term.Select(Term.Name("KindlingsSchema"), Term.Name("derived")), _), Term.Name("schema")) =>
          Right((acc, top))
        case Term.Select(Term.ApplyType.After_4_6_0(Term.Name("summon"), _), Term.Name("schema")) => Right((acc, top))
        case other => Left(other.syntax.take(80))
      }
    loop(body, Map.empty, None)
  }

  private def companionOf(d: Defn, name: String, stats: List[Stat]): Option[Defn.Object] =
    stats.collectFirst { case o: Defn.Object if o.name.value == name => o }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val code = doc.input.text
    val ranges = scala.collection.mutable.ListBuffer.empty[(Int, Int)]
    val patches = doc.tree.collect {
      case tpl: Template => templateOrSource(tpl.body.stats, code, ranges)
      case src: Source => templateOrSource(src.stats, code, ranges)
      case pkg: Pkg => templateOrSource(pkg.body.stats, code, ranges)
    }.flatten
    def gone(t: Tree) = ranges.exists { case (s, e) => t.pos.start >= s && t.pos.end <= e }
    def inImport(t: Tree): Boolean = t.parent.exists(p => p.is[Import] || inImport(p))
    val names = doc.tree.collect { case n: Name if !inImport(n) && !gone(n) => n }
    val codecLeft = doc.tree.collect {
      case t: Template if t.derives.exists(d => config.codecDerives.contains(d.syntax)) && !t.derives.forall(gone) => t
      case s: Stat if isCodecGiven(s) && !gone(s) => s
    }.nonEmpty
    def unused(im: Importer): Boolean = im.importees.forall {
      case i: Importee.Name => !names.exists(_.value == i.name.value)
      case _: Importee.GivenAll | _: Importee.Given => !codecLeft
      case _ => false
    }
    val imports =
      if (patches.isEmpty || !config.strip) Patch.empty
      else
        doc.tree.collect {
          case i: Import if i.importers.forall(im => config.stripImports.contains(im.syntax) && unused(im)) =>
            Patch.removeTokens(lineTokens(i, code))
        }.asPatch
    patches.asPatch + imports
  }

  private def lineTokens(t: Tree, code: String)(implicit doc: SemanticDocument): Iterable[Token] = {
    val from = code.lastIndexOf('\n', t.pos.start - 1) + 1
    val nl = code.indexOf('\n', t.pos.end)
    val to = if (nl < 0) code.length else nl + 1
    doc.tokens.filter(k => k.pos.start >= from && k.pos.end <= to && (k.pos.start >= t.pos.start || k.is[Token.Whitespace]))
  }

  private def statTokens(t: Tree, code: String)(implicit doc: SemanticDocument): Iterable[Token] = {
    val from = code.lastIndexWhere(c => !c.isWhitespace, t.pos.start - 1) + 1
    doc.tokens.filter(k => k.pos.start >= from && k.pos.end <= t.pos.end)
  }

  private type Ranges = scala.collection.mutable.ListBuffer[(Int, Int)]

  private def templateOrSource(stats: List[Stat], code: String, ranges: Ranges)(implicit
      doc: SemanticDocument
  ): List[Patch] =
    stats.flatMap {
      case d: Defn.Class => record(d, d.name.value, d.mods, d.ctor, d.templ, stats, code, ranges)
      case d: Defn.Enum => record(d, d.name.value, d.mods, d.ctor, d.templ, stats, code, ranges)
      case _ => Nil
    }

  private def record(
      d: Defn,
      name: String,
      mods: List[Mod],
      ctor: Ctor.Primary,
      templ: Template,
      siblings: List[Stat],
      code: String,
      ranges: Ranges
  )(implicit doc: SemanticDocument): List[Patch] = {
    val own = config.twins.find(_.from == fqn(d.symbol))
    own.toList.flatMap { tw =>
      val companion = companionOf(d, name, siblings)
      val codecGivens = companion.toList.flatMap(_.templ.body.stats.filter(isCodecGiven))
      val described = codecGivens.collect { case g: Defn.GivenAlias => g.body }.map(descriptions)
      val manual = described.collect { case Left(why) => why }
      val fields = described.collect { case Right((f, _)) => f }.foldLeft(Map.empty[String, String])(_ ++ _)
      val top = described.collect { case Right((_, Some(t))) => t }.headOption
      val params = ctor.paramClauses.flatMap(_.values)
      val unplaced = fields.keySet -- params.map(_.name.value)
      manual.foreach(m => report("manual", tw.from, "codec given", m))
      unplaced.foreach(f => report("manual", tw.from, "description of a field it lacks", f))

      val derivesNames = templ.derives.map(_.syntax)
      val codec = derivesNames.filter(config.codecDerives.contains)
      val kept = derivesNames.filterNot(config.codecDerives.contains)
      val schemaGiven = codecGivens.exists {
        case g: Defn.GivenAlias => g.decltpe.syntax.startsWith("Schema[")
        case _ => false
      }
      val twinDerives = (kept ++ codec.map(n => config.derivesAs.getOrElse(n, n)) ++ config.twinDerives ++
        (if (schemaGiven) List("KindlingsSchema", "Schema") else Nil)).distinct

      val start = startAfterAnnotations(d, mods)
      val derivesSkip: Set[Tree] = templ.derives.toSet
      val caseStats = templ.body.stats.filter(s => s.is[Defn.EnumCase] || s.is[Defn.RepeatedEnumCase])
      val parts = (ctor :: templ.inits) ++ caseStats
      val partRefs = parts.map(p => references(p, tw, derivesSkip))
      val recEdits = partRefs.flatMap(_._1)
      val recImports = partRefs.flatMap(_._2).toSet
      val recMissing = partRefs.flatMap(_._3)
      val recRedeclared = partRefs.flatMap(_._4).toSet
      val descEdits = params.flatMap(p =>
        fields.get(p.name.value).map(text => Edit(p.pos.start, p.pos.start, s"@description($text) "))
      )
      val ctorEnd = (List(ctor.pos.end, d.asInstanceOf[Member].name.pos.end) ++ templ.inits.map(_.pos.end)).max
      val nameTree = d.asInstanceOf[Member].name
      val nameEdit =
        if (nameTree.value == simple(tw.to)) Nil else List(Edit(nameTree.pos.start, nameTree.pos.end, simple(tw.to)))
      val header = render(code, start, ctorEnd, recEdits ++ descEdits ++ nameEdit).replaceAll("\\s+$", "")
      val derivesText = if (twinDerives.isEmpty) "" else twinDerives.mkString(" derives ", ", ", "")
      val cases = d match {
        case _: Defn.Enum =>
          templ.body.stats.collect {
            case c @ (_: Defn.EnumCase | _: Defn.RepeatedEnumCase) => "  " + render(code, c.pos.start, c.pos.end, recEdits)
          }
        case _ => Nil
      }
      val schemaNamed = nameEdit.nonEmpty && twinDerives.exists(n => n == "KindlingsSchema" || n == "Schema")
      val nameText = if (schemaNamed) s"@customise(_.name(SName(\"${nameTree.value}\")))\n" else ""
      val recordText = (top.map(t => s"@description($t)\n").getOrElse("")) + nameText + header + derivesText +
        (if (cases.isEmpty) "" else cases.mkString(":\n", "\n", ""))

      val companionStats = companion.toList.flatMap(_.templ.body.stats).flatMap {
        case t: Defn.Type if !config.types.contains(tw.from + "." + t.name.value) => Some(t)
        case o: Defn.Object
            if o.templ.inits.exists(_.tpe.syntax.startsWith("Newtype")) && !config.types.contains(tw.from + "." + o.name.value) =>
          Some(o)
        case e: Export => Some(e)
        case _ => None
      }
      val compRefs = companionStats.map(s => references(s, tw, s.collect { case a: Mod.Annot => a: Tree }.toSet))
      val compTexts = companionStats.zip(compRefs).map { case (s, (edits, _, _, _)) =>
        val end = s match {
          case o: Defn.Object => o.templ.inits.map(_.pos.end).max
          case other => other.pos.end
        }
        val mods0 = s match {
          case o: Defn.Object => o.mods
          case t: Defn.Type => t.mods
          case _ => Nil
        }
        "  " + render(code, startAfterAnnotations(s, mods0), end, edits)
      }
      val redeclared = (recRedeclared ++ compRefs.flatMap(_._4)).toList.sorted
      val redeclTexts = redeclared.flatMap { f =>
        val n = simple(f)
        List(s"  type $n = $n.T", s"  object $n extends ${config.redeclare(f)}")
      }
      val redeclImports = if (redeclared.isEmpty) Nil else config.redeclareImports

      val missing = (recMissing ++ compRefs.flatMap(_._3)).distinct
      missing.foreach { case (text, sym) =>
        report("unmapped", tw.from, text, sym)
        if (Symbol(sym).info.exists(i => i.isClass || i.isTrait)) report("nested", tw.from, sym)
      }
      val imports = (recImports ++ compRefs.flatMap(_._2) ++ redeclImports ++ config.twinImports ++
        twinDerives.flatMap(config.derivesImports.get) ++ (if (fields.nonEmpty || top.nonEmpty) List(DescriptionImport) else Nil) ++
        (if (schemaNamed) NameImports else Nil))
        .filterNot(i => packageOf(i) == packageOf(tw.to))
      val clashes = imports.filterNot(_.endsWith(".given")).groupBy(i => simple(i)).filter { case (n, is) => is.size > 1 || n == simple(tw.to) }
      clashes.foreach { case (n, is) => report("clash", tw.from, n, is.toList.sorted.mkString(" ")) }

      val ok = missing.isEmpty && manual.isEmpty && unplaced.isEmpty
      if (ok) {
        val pkg = packageOf(tw.to)
        val clause = if (pkg.startsWith("com.histmersion.")) s"package com.histmersion\npackage ${pkg.stripPrefix("com.histmersion.")}"
        else s"package $pkg"
        val body = compTexts ++ redeclTexts
        val companionText = if (body.isEmpty) "" else s"\n\nobject ${simple(tw.to)}:\n" + body.mkString("\n")
        val text = clause + "\n\n" + imports.toList.sorted.map("import " + _).mkString("\n") +
          (if (imports.isEmpty) "" else "\n\n") + recordText + companionText + "\n"
        val file = Paths.get(config.twinRoot).resolve(tw.to.replace('.', '/') + ".scala").toString
        written.put(file, text)
        report("twin", tw.from, file)
      }

      if (!ok || !config.strip) Nil
      else {
        report("stripped", tw.from, codec.mkString(" "), codecGivens.size.toString)
        templ.derives.foreach(t => ranges += (t.pos.start -> t.pos.end))
        codecGivens.foreach(g => ranges += (g.pos.start -> g.pos.end))
        val derivesPatch =
          if (codec.isEmpty) Patch.empty
          else {
            val kw = d.tokens.find(t => t.text == "derives" && t.pos.start >= ctor.pos.end)
            kw.toList.flatMap { k =>
              val before = code.lastIndexWhere(c => !c.isWhitespace, k.pos.start - 1) + 1
              val end = templ.derives.map(_.pos.end).max
              val replacement = if (kept.isEmpty) "" else kept.mkString(" derives ", ", ", "")
              val toks = doc.tokens.filter(t => t.pos.start >= before && t.pos.end <= end).toList
              List(Patch.replaceToken(toks.head, replacement) + Patch.removeTokens(toks.tail))
            }.asPatch
          }
        val givenPatches = companion.toList.flatMap { c =>
          val remaining = c.templ.body.stats.filterNot(isCodecGiven)
          if (remaining.isEmpty) List(Patch.removeTokens(statTokens(c, code)))
          else codecGivens.map(g => Patch.removeTokens(statTokens(g, code)))
        }
        derivesPatch :: givenPatches
      }
    }
  }

  override def afterComplete(): Unit = {
    written.asScala.foreach { case (file, text) =>
      val p = Paths.get(config.sourceroot).resolve(file)
      Files.createDirectories(p.getParent)
      Files.write(p, text.getBytes(UTF_8))
    }
    if (config.report.nonEmpty) {
      val p = Paths.get(config.report)
      Option(p.getParent).foreach(Files.createDirectories(_))
      Files.write(p, reported.asScala.toList.distinct.sorted.mkString("", "\n", "\n").getBytes(UTF_8))
    }
  }
}
