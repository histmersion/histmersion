import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.meta.internal.{semanticdb => s}

/** Read-only inventory of guideline violations (G3, G9/G50, G44, G45, G46, G47, G48, G49, G58raw, silent comparisons).
  * Writes one row per finding: rule, module, path, line, detail. Never patches sources. */
object GuidelineInventory {
  case class Policy(
    roots: List[String] = List("modules"),
    boundaries: String = "architecture/boundaries.tsv",
    report: String = "refactor-audit/guidelines/findings.tsv",
    unclassified: String = "refactor-audit/guidelines/unclassified.tsv",
    summary: String = "",
    notes: List[String] = Nil,
    expected: String = "",
    emitLints: Boolean = false,
    requireCoverage: Boolean = true,
    projectPrefix: String = "com/histmersion/",
    semanticdbRoots: List[String] = List("target/out/jvm/scala-3.10.0", "target/out/sjs1/scala-3.10.0"),
    testMarkers: List[String] = List("/src/test/"),
    controllerFile: String = ".*(Controller|Controllers|Routes)\\.scala",
    rawSqlOwners: List[String] = List("com/histmersion/util/database/Sql#", "com/histmersion/util/database/Sql."),
    rawSqlText: List[String] = List("com/histmersion/util/database/Sql.Text."),
    roleSuffixes: List[String] = List("Suite", "Spec", "Test", "Tests", "Service", "Repo", "Repository", "Controllers", "Controller",
      "Routes", "Row", "Rows", "Markdown", "Render", "Renderer", "Codec", "Codecs", "Cmd", "View", "Views", "Page", "Api", "Client",
      "Store", "Config", "Json", "Schema", "Fixtures", "Fixture", "Support", "Impl", "Ops", "Syntax", "Rule"),
    collectionOwners: List[String] = List("scala/collection/", "scala/Option#", "scala/Some#", "scala/Array#", "scala/package.", "scala/Predef.", "scala/IArray"),
    collectionOps: List[String] = List("find", "filter", "filterNot", "exists", "forall", "sortBy", "sortWith", "map", "flatMap", "contains",
      "count", "collect", "collectFirst", "groupBy", "groupMap", "partition", "distinctBy", "maxBy", "minBy", "maxByOption", "minByOption",
      "indexWhere", "takeWhile", "dropWhile", "foreach", "foldLeft", "foldRight", "toMap", "span", "segmentLength", "lastIndexWhere", "findLast",
      "sumBy", "mapFilter", "flatten", "sorted", "zip", "indexOf", "diff", "intersect", "startsWith", "endsWith", "keyBy", "filterKeys", "mapValues")
  )
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Finding(rule: String, module: String, path: String, line: Int, detail: String) {
    def row: String = List(rule, module, path, line.toString, detail.replace("\t", " ").replace("\n", " ")).mkString("\t")
  }
  case class TypeDef(module: String, path: String, line: Int, pkg: String, owner: String, name: String, kind: String, symbol: String, scope: String)
  case class PlainDef(symbol: String, module: String, path: String, line: Int, name: String, params: Map[Int, (String, String)], public: Boolean, boundary: Boolean)
  case class PlainCall(callee: String, index: Int, newtype: String, path: String, line: Int)

  /** Symbol information of every current SemanticDB document under `roots` (a document counts only when its md5 matches the
    * source on disk). Scalafix's own symbol table reads class files, which lose Scala 3 opaque aliases across files. */
  def buildIndex(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = {
    val index = new ConcurrentHashMap[String, s.SymbolInformation]()
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList finally stream.close()
    }
    val md5Cache = new ConcurrentHashMap[String, String]()
    def md5(uri: String): String = md5Cache.computeIfAbsent(uri, u => {
      val p = Paths.get(u)
      if (!Files.isRegularFile(p)) "" else java.security.MessageDigest.getInstance("MD5").digest(Files.readAllBytes(p)).map(b => f"${b & 255}%02X").mkString
    })
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.foreach { d =>
        if (md5(d.uri) == d.md5) d.symbols.foreach { i =>
          index.putIfAbsent(if (i.symbol.startsWith("local")) d.uri + "::" + i.symbol else i.symbol, i)
        }
      }
    }
    index
  }
  val legend: List[(String, String)] = List(
    "G3misplaced" -> "HEURISTIC: a def in a standalone object (no class/trait/enum/type of that name beside it) whose first explicit parameter has a project type",
    "G9prefix" -> "type `<Entity><Property>` while a top-level (or same-owner) type `<Entity>` exists in the same module",
    "G9prefixRole" -> "as G9prefix, but the suffix is a role word (Suite, Repo, Service, Row, Markdown, ...)",
    "G44" -> "Iron `.value`, or a newtype built with apply/applyUnsafe/assume/option/either, outside the newtype's companion and outside boundary files",
    "G44conversion" -> "the same inside a *Controller(s)/*Routes file where the value feeds a domain newtype constructor/parse directly, or the construction itself",
    "G44plaindef" -> "a public def with a String/Int/Long/Double/Boolean parameter that some call site feeds with `.value` of a newtype (reported at the def)",
    "G45boundary" -> "hand `.value` or newtype construction inside a boundaries.tsv file (G45: lift type classes instead)",
    "G46" -> "`type X = a.B.C` immediately followed by `val X: a.B.C.type = a.B.C`",
    "G47root" -> "each `_root_` occurrence",
    "G47fqn" -> "a reference spelled `com.histmersion.` (or `_root_.com.histmersion.`) inside a com.histmersion file, outside imports/package clauses; same-package or other-package",
    "G47self" -> "`T.Member` (name-qualified) or `a.b.T.Member` (full-path) written inside T or its companion",
    "G47package" -> "a file with one `package com.histmersion.x.y` clause instead of chained clauses",
    "G48" -> "`.modify(_.x)(_.description(...))` on a Tapir Schema (one per modify)",
    "G49" -> "`.value` inside a lambda passed to a collection operation, as an argument of contains/indexOf, or in `a.value == b.value` / `.value.equalsIgnoreCase(...)`",
    "silentCompare" -> "==/!=/contains/indexOf whose sides have unrelated types (opaque newtype vs primitive, two newtypes, enum vs primitive/newtype)",
    "silentInterp" -> "an enum value interpolated into s\"...\", sql\"...\" or another interpolator",
    "silentPure" -> "X.option/X.either on a newtype whose constraint is Pure (always succeeds)",
    "G5problem" -> "a newtype over String (project Newtype or Iron RefinedType) named, or ending in, Problem, Error, Reason, Refusal, Failure, Why or Message, other than the layer's one top-level `Problem` (a failure message is the layer's one Problem; an enum where a caller branches on it). The layer Problem by module: values and config com.histmersion.Problem; api, files, db, server, cli, web com.histmersion.api.Problem; core com.histmersion.domain.Problem; plugin-api, upstreams, plugin-vaults, plugin-host, plugin-testkit and each plugin com.histmersion.plugin.Problem; plugin-resolver com.histmersion.pluginresolver.Problem. A module may use the Problem of a layer below it (db and server use the wire contract's because they build Api.Error; plugin-host the SPI's because it implements it); a second top-level Problem in one module and scope is a finding, and so is every nested one",
    "G55positional" -> "a newtype object or type alias named `<X>Result`, `<X>Result<n>`, `<X>Arg<n>` or `<X>Part<n>`: a mechanical name, not a concept",
    "G58raw" -> "raw SQL text: a call of an Sql operator that takes a String or an Sql.Text (Sql.raw, Sql.of, the constructor, `++ \"...\"`, wrap), or an Sql.Text built (also through an alias such as RawText/OfText)")
  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
  val primitives = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Double#", "scala/Boolean#",
    "scala/Short#", "scala/Byte#", "scala/Char#", "scala/Float#")
  val plainDefTypes = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Double#", "scala/Boolean#")
  val pure = "io/github/iltotore/iron/constraint/any.Pure#"
  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
  def module(rel: String): String = if (rel.startsWith("modules/")) rel.stripPrefix("modules/").split("/src/").head else "fixture"
  def sources(roots: List[String]): Set[String] = roots.flatMap { root =>
    val stream = Files.walk(Paths.get(root).toAbsolutePath.normalize)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(p => relative(p.toString)).toList finally stream.close()
  }.toSet
  def readBoundaries(path: String): Map[String, String] =
    if (path.isEmpty || !Files.exists(Paths.get(path))) Map.empty
    else Files.readAllLines(Paths.get(path), UTF_8).asScala.toList.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#") && !l.startsWith("path\t")).map { l =>
      val cols = l.split("\t", -1)
      require(cols.length == 3 && cols(0).nonEmpty && cols(2).nonEmpty, "Malformed boundaries row: " + l)
      require(Set("sql", "http", "json", "cli-args", "dom", "config", "process", "spi")(cols(1)), "Unknown boundary kind: " + l)
      require(Files.isRegularFile(Paths.get(cols(0))), "Boundary file does not exist: " + cols(0))
      cols(0) -> cols(1)
    }.toMap
}

class GuidelineInventory(val policy: GuidelineInventory.Policy, boundaryKinds: Map[String, String],
    index: ConcurrentHashMap[String, scala.meta.internal.semanticdb.SymbolInformation]) extends SemanticRule("GuidelineInventory") {
  def this() = this(GuidelineInventory.Policy(), Map.empty, new ConcurrentHashMap())
  import GuidelineInventory._
  private val findings = new ConcurrentLinkedQueue[Finding]()
  private val visited = new ConcurrentLinkedQueue[String]()
  private val typeDefs = new ConcurrentLinkedQueue[TypeDef]()
  private val plainDefs = new ConcurrentHashMap[String, PlainDef]()
  private val plainCalls = new ConcurrentLinkedQueue[PlainCall]()
  private val unclassified = new ConcurrentHashMap[String, (Int, String)]()
  private val stale = new ConcurrentLinkedQueue[String]()
  private val topProblems = new ConcurrentLinkedQueue[(String, String, String, Int)]()
  private val controller = policy.controllerFile.r

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("GuidelineInventory")(Policy()).map { p =>
      val b = readBoundaries(p.boundaries)
      Option(Paths.get(p.report).getParent).foreach(Files.createDirectories(_))
      new GuidelineInventory(p, b, buildIndex(p.semanticdbRoots))
    }

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def note(shape: String, where: String): Unit = unclassified.merge(shape, (1, where), (a, b) => (a._1 + b._1, a._2))

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = relative(doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case other => other.toString })
    visited.add(path)
    // a SemanticDB written for another version of the file resolves no symbol, and every symbol-based rule would read
    // the file wrongly (G9prefix doubles when no type has an owner): such a file is refused, never counted
    if (staleSemanticdb(doc)) stale.add(path)
    val mod = module(path)
    val scope = if (policy.testMarkers.exists(path.contains)) "test" else "main"
    val boundary = boundaryKinds.contains(path)
    val isController = controller.pattern.matcher(Paths.get(path).getFileName.toString).matches
    var patches = List.empty[Patch]
    def report(rule: String, t: Tree, detail: String): Unit = {
      findings.add(Finding(rule, mod, path, t.pos.startLine + 1, detail))
      if (policy.emitLints) patches ::= Patch.lint(Diagnostic(rule, detail, t.pos, severity = scalafix.lint.LintSeverity.Warning))
    }
    def where(t: Tree): String = path + ":" + (t.pos.startLine + 1)

    // ---- symbols and types (resolved through the project-wide SemanticDB index, see GuidelineInventory.Index) ----
    def infoS(sym: String): Option[s.SymbolInformation] =
      if (sym.isEmpty) None else Option(index.get(if (sym.startsWith("local")) path + "::" + sym else sym))
    def info(sym: Symbol): Option[s.SymbolInformation] = if (sym.isNone) None else infoS(sym.value)
    def hasKind(sym: Symbol, kinds: Set[s.SymbolInformation.Kind]): Boolean = info(sym).exists(i => kinds(i.kind))
    val objectKinds: Set[s.SymbolInformation.Kind] = Set(s.SymbolInformation.Kind.OBJECT, s.SymbolInformation.Kind.PACKAGE_OBJECT, s.SymbolInformation.Kind.PACKAGE)
    def isEnumSym(sym: Symbol): Boolean = info(sym).exists(i => (i.properties & s.SymbolInformation.Property.ENUM.value) != 0)
    def dealias(t: s.Type, depth: Int = 0): s.Type = if (depth > 12) t else t match {
      case a: s.AnnotatedType => dealias(a.tpe, depth + 1)
      case b: s.ByNameType => dealias(b.tpe, depth + 1)
      case r: s.RepeatedType => dealias(r.tpe, depth + 1)
      case r: s.TypeRef if !r.symbol.startsWith(ironValue) => infoS(r.symbol).map(_.signature) match {
        case Some(ts: s.TypeSignature) if ts.lowerBound == ts.upperBound && ts.upperBound != s.Type.Empty &&
            ts.typeParameters.forall(sc => sc.symlinks.isEmpty && sc.hardlinks.isEmpty) => dealias(ts.upperBound, depth + 1)
        case _ => t
      }
      case _ => t
    }
    def typeSymbol(t: s.Type): Symbol = t match {
      case r: s.TypeRef => Symbol(r.symbol)
      case st: s.SingleType => Symbol(st.symbol)
      case _ => Symbol.None
    }
    def newtypeParents(obj: Symbol): Option[(String, Symbol, Symbol)] = info(obj).map(_.signature).collect {
      case cs: s.ClassSignature => cs.parents.collectFirst {
        // com.histmersion.Newtype (step 3a) is a RefinedType
        case r: s.TypeRef if (r.symbol == ironValue + "RefinedType#" || r.symbol == ironValue + "RefinedSubtype#" || r.symbol == newtypeBase) && r.typeArguments.size == 2 =>
          (if (r.symbol.endsWith("RefinedSubtype#")) "RefinedSubtype" else "RefinedType", typeSymbol(dealias(r.typeArguments(0))), typeSymbol(dealias(r.typeArguments(1))))
      }
    }.flatten
    def isNewtypeObject(sym: Symbol): Boolean = !sym.isNone && sym.value.endsWith(".") && newtypeParents(sym).nonEmpty
    /** The newtype object a type denotes (`X.T`, or an alias to it). */
    def newtypeOf(t: s.Type): Option[Symbol] = dealias(t) match {
      case r: s.TypeRef if r.symbol.startsWith(ironValue) && r.symbol.endsWith("#T#") => r.prefix match {
        case st: s.SingleType if isNewtypeObject(Symbol(st.symbol)) => Some(Symbol(st.symbol))
        case th: s.ThisType if isNewtypeObject(Symbol(th.symbol)) => Some(Symbol(th.symbol))
        case _ => None
      }
      case _ => None
    }
    def resultOf(sym: Symbol): Option[s.Type] = info(sym).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature => m.returnType
    }
    /** What calling `sym(…)` gives: a method's result; applying a value (`text(i)` on a String) calls its `apply`, whose
      * result is not the value's type. */
    def callResult(sym: Symbol): Option[s.Type] = info(sym).map(_.signature) match {
      case Some(m: s.MethodSignature) if m.parameterLists.nonEmpty => Some(m.returnType)
      case _ => None
    }
    def lit(name: String): Option[s.Type] = Some(s.TypeRef(s.Type.Empty, name, Nil))
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def termType(t: Term): Option[s.Type] = t match {
      case _: Lit.String => lit("java/lang/String#")
      case _: Lit.Int => lit("scala/Int#")
      case _: Lit.Long => lit("scala/Long#")
      case _: Lit.Double => lit("scala/Double#")
      case _: Lit.Boolean => lit("scala/Boolean#")
      case _: Lit.Char => lit("scala/Char#")
      case i: Term.Interpolate if i.prefix.value == "s" || i.prefix.value == "f" => lit("java/lang/String#")
      case sel: Term.Select if isIronValue(sel.name) => termType(sel.qual).flatMap(newtypeOf).flatMap(newtypeParents).map(p => s.TypeRef(s.Type.Empty, p._2.value, Nil))
      case n: Term.Name => if (hasKind(n.symbol, objectKinds)) None else resultOf(n.symbol)
      case sel: Term.Select => if (hasKind(sel.name.symbol, objectKinds)) None else resultOf(sel.name.symbol)
      case a: Term.Apply => a.fun match {
        case n: Term.Name if isNewtypeObject(n.symbol) => None
        case n: Term.Name => callResult(n.symbol)
        case sel: Term.Select => callResult(sel.name.symbol)
        case _ => None
      }
      case b: Term.Block if b.stats.size == 1 => b.stats.head match { case x: Term => termType(x); case _ => None }
      case p: Term.Placeholder => placeholderType(p)
      case _ => None
    }
    /** `xs.op(_.f)`: the placeholder has the element type of `xs` (first type argument of a one-argument type constructor). */
    def placeholderType(p: Term.Placeholder): Option[s.Type] =
      ancestors(p).collectFirst { case f: Term.AnonymousFunction => f }.flatMap(f => f.parent.flatMap(_.parent)).collect {
        case a: Term.Apply => a.fun
      }.collect { case Term.Select(recv, _) => recv }.flatMap(r => termType(r)).map(dealias(_)).collect {
        case r: s.TypeRef if r.typeArguments.size == 1 => r.typeArguments.head
      }
    sealed trait Kind
    case class Newtype(obj: Symbol, opaque: Boolean, base: Symbol) extends Kind
    case class Prim(sym: Symbol) extends Kind
    case class EnumK(sym: Symbol) extends Kind
    case object Unknown extends Kind
    def classify(t: Option[s.Type]): Kind = t.map(dealias(_)) match {
      case Some(tpe) => newtypeOf(tpe) match {
        case Some(obj) => val p = newtypeParents(obj).get; Newtype(obj, p._1 == "RefinedType", p._2)
        case None =>
          val ts = typeSymbol(tpe)
          if (primitives(ts.value)) Prim(if (ts.value == "scala/Predef.String#") Symbol("java/lang/String#") else ts)
          else if (isEnumSym(ts)) EnumK(ts)
          else Unknown
      }
      case None => Unknown
    }
    def show(k: Kind): String = k match {
      case Newtype(o, _, _) => o.displayName
      case Prim(x) => x.displayName
      case EnumK(x) => x.displayName
      case Unknown => "?"
    }
    def unrelated(a: Kind, b: Kind): Boolean = (a, b) match {
      case (Newtype(x, ox, _), Newtype(y, oy, _)) => x != y && (ox || oy)
      case (Newtype(_, true, _), Prim(_)) | (Prim(_), Newtype(_, true, _)) => true
      case (Newtype(_, false, base), Prim(p)) => base != p && !(primitives(base.value) && primitives(p.value) && base.value != "java/lang/String#" && p.value != "java/lang/String#")
      case (Prim(p), Newtype(_, false, base)) => base != p && !(primitives(base.value) && primitives(p.value) && base.value != "java/lang/String#" && p.value != "java/lang/String#")
      case (EnumK(_), Prim(_)) | (Prim(_), EnumK(_)) => true
      case (EnumK(x), Newtype(_, _, _)) => true
      case (Newtype(_, _, _), EnumK(x)) => true
      case (EnumK(x), EnumK(y)) => x != y
      case (Prim(x), Prim(y)) => (x.value == "java/lang/String#") != (y.value == "java/lang/String#")
      case _ => false
    }

    // ---- enclosing definitions ---------------------------------------------------------------------------------
    def enclosingNewtypes(t: Tree): List[Symbol] = ancestors(t).collect { case o: Defn.Object if isNewtypeObject(o.name.symbol) => o.name.symbol }
    def inImport(t: Tree): Boolean = ancestors(t).exists(a => a.is[Import] || a.is[Export] || a.is[Pkg] && a.asInstanceOf[Pkg].ref.pos.end >= t.pos.end && a.asInstanceOf[Pkg].ref.pos.start <= t.pos.start)
    def companionKey(s: Symbol): String = s.value.stripSuffix("#").stripSuffix(".")
    def enclosingTemplates(t: Tree): List[Symbol] = ancestors(t).collect {
      case o: Defn.Object => o.name.symbol
      case c: Defn.Class => c.name.symbol
      case c: Defn.Trait => c.name.symbol
      case e: Defn.Enum => e.name.symbol
    }
    def pkgOf(t: Tree): String = ancestors(t).collect { case p: Pkg => p.ref.syntax }.reverse.mkString(".")

    // ---- constructions and unwraps ----------------------------------------------------------------------------
    val constructNames = Set("apply", "applyUnsafe", "assume", "option", "either", "assumeAll", "applyAllUnsafe", "optionAll", "eitherAll", "make", "from")
    def constructionOf(fun: Term): Option[(Symbol, String)] = fun match {
      case n: Term.Name if isNewtypeObject(n.symbol) => Some(n.symbol -> "apply")
      case Term.Select(q, n) if constructNames(n.value) && n.symbol.value.startsWith(ironValue) => q match {
        case qn: Term.Name if isNewtypeObject(qn.symbol) => Some(qn.symbol -> n.value)
        case qs: Term.Select if isNewtypeObject(qs.name.symbol) => Some(qs.name.symbol -> n.value)
        case _ => None
      }
      case Term.Select(q, n) if isNewtypeObject(n.symbol) => Some(n.symbol -> "apply")
      case Term.ApplyType(f, _) => constructionOf(f)
      case _ => None
    }
    def isConversionTarget(fun: Term): Boolean = constructionOf(fun).nonEmpty || (fun match {
      case Term.Select(_, n) => Set("parse", "fromWire", "fromString", "either", "option", "transformIntoPartial")(n.value) && n.symbol.value.startsWith(policy.projectPrefix)
      case _ => false
    })
    def isLiteral(t: Term): Boolean = t match {
      case _: Lit.Null | _: Lit.Unit => false
      case _: Lit => true
      case u: Term.ApplyUnary => u.op.value == "-" && (u.arg match { case _: Lit.Int | _: Lit.Long | _: Lit.Double | _: Lit.Float => true; case _ => false })
      case _ => false
    }

    // ---- G58raw: raw SQL text handed to an operator that bypasses the codecs --------------------------------------
    def isRawText(sym: Symbol): Boolean = !sym.isNone && (policy.rawSqlText.contains(sym.value) || (resultOf(sym) match {
      case Some(st: s.SingleType) => policy.rawSqlText.contains(st.symbol)
      case _ => false
    }))
    def takesText(sym: Symbol): Boolean = info(sym).map(_.signature).exists {
      case m: s.MethodSignature => m.parameterLists.flatMap(_.symlinks).exists { p =>
        infoS(p).map(_.signature).exists {
          case v: s.ValueSignature =>
            val t = dealias(v.tpe)
            typeSymbol(t).value == "java/lang/String#" || typeSymbol(t).value == "scala/Predef.String#" || newtypeOf(t).exists(o => policy.rawSqlText.contains(o.value))
          case _ => false
        }
      }
      case _ => false
    }
    def rawSqlOperator(sym: Symbol): Boolean = !sym.isNone && policy.rawSqlOwners.contains(sym.owner.value) && takesText(sym)
    def rawSqlOf(fun: Term): Option[String] = fun match {
      case n: Term.Name if isRawText(n.symbol) => Some("text:" + n.value)
      case n: Term.Name if rawSqlOperator(n.symbol) => Some("operator:" + n.value)
      case Term.Select(q, n) if isRawText(n.symbol) => Some("text:" + n.value)
      case Term.Select(q, n) if constructNames(n.value) && (q match { case qn: Term.Name => isRawText(qn.symbol); case qs: Term.Select => isRawText(qs.name.symbol); case _ => false }) =>
        Some("text:" + (q match { case qn: Term.Name => qn.value; case qs: Term.Select => qs.name.value; case _ => "" }) + "." + n.value)
      case Term.Select(_, n) if rawSqlOperator(n.symbol) => Some("operator:" + n.value)
      case Term.ApplyType(f, _) => rawSqlOf(f)
      case _ => None
    }

    def allowedInCompanion(t: Tree, nt: Option[Symbol]): Boolean = {
      val enclosing = enclosingNewtypes(t)
      enclosing.nonEmpty && nt.forall(enclosing.contains)
    }

    doc.tree.traverse {
      // G44/G45: .value
      case s @ Term.Select(q, n) if isIronValue(n) && !inImport(s) =>
        val nt = termType(q).flatMap(newtypeOf)
        if (nt.isEmpty) note("value-receiver-type-unresolved", where(s))
        val ntName = nt.fold("?")(_.displayName)
        if (!allowedInCompanion(s, nt)) {
          val fedInto = s.parent.flatMap(_.parent) match {
            case Some(a: Term.Apply) if a.argClause.values.exists(_ eq s) && isConversionTarget(a.fun) => Some(a.fun.syntax.take(60))
            case _ => None
          }
          if (boundary) report("G45boundary", s, s"value:$ntName;${boundaryKinds(path)}")
          else if (isController && fedInto.nonEmpty) report("G44conversion", s, s"value:$ntName->${fedInto.get}")
          else report("G44", s, s"value:$ntName" + fedInto.fold("")(f => s";bridge->$f"))
        }
        // G49: inside a lambda passed to a collection op
        // a block that only holds the lambda (`xs.map { x => … }`) is the same argument as `xs.map(x => …)`
        val lambdaOp = ancestors(s).takeWhile(a => !a.is[Defn.Def] && !a.is[Template]).filterNot {
          case b: Term.Block => b.stats.size == 1
          case _ => false
        }.sliding(3).collectFirst {
          case List(f: Term.FunctionTerm, _: Term.ArgClause, a: Term.Apply) => a
          case List(f: Term.AnonymousFunction, _: Term.ArgClause, a: Term.Apply) => a
          case List(f: Term.PartialFunction, _: Term.ArgClause, a: Term.Apply) => a
        }.flatMap(a => a.fun match {
          case Term.Select(_, op) if policy.collectionOps.contains(op.value) && policy.collectionOwners.exists(op.symbol.value.startsWith) => Some(op.value)
          case _ => None
        })
        val direct = s.parent.flatMap(_.parent) match {
          case Some(a: Term.Apply) if a.argClause.values.exists(_ eq s) => a.fun match {
            case Term.Select(_, op) if Set("contains", "indexOf", "startsWith", "endsWith")(op.value) && policy.collectionOwners.exists(op.symbol.value.startsWith) => Some("arg-of-" + op.value)
            case _ => None
          }
          case _ => None
        }
        val compared = s.parent match {
          case Some(i: Term.ApplyInfix) if Set("==", "!=", "<", ">", "<=", ">=")(i.op.value) =>
            val other = if (i.lhs eq s) i.argClause.values.headOption else Some(i.lhs)
            other.collect { case o: Term.Select if isIronValue(o.name) => "compare-" + i.op.value }.filter(_ => i.lhs eq s)
          case Some(Term.Select(_, m)) if Set("equalsIgnoreCase", "compareTo", "compareToIgnoreCase", "equals")(m.value) => Some(m.value)
          case _ => None
        }
        (compared.map("compare:" + _) orElse lambdaOp.map("lambda:" + _) orElse direct).foreach(d => if (!allowedInCompanion(s, nt)) report("G49", s, s"$d;$ntName"))
      // G44/G45: construction from a raw value
      case a: Term.Apply if constructionOf(a.fun).nonEmpty && !inImport(a) =>
        val (obj, how) = constructionOf(a.fun).get
        val args = a.argClause.values
        val bridged = args.exists { case s: Term.Select => isIronValue(s.name); case _ => false }
        // a newtype built from a literal is checked by Iron at compile time and allowed anywhere (decision 2026-11-08)
        val literal = how == "apply" && args.nonEmpty && args.forall(isLiteral)
        if (!bridged && !literal && !allowedInCompanion(a, Some(obj))) {
          val detail = s"construct:${obj.displayName}.$how;expr"
          if (boundary) report("G45boundary", a, detail + ";" + boundaryKinds(path))
          else if (isController) report("G44conversion", a, detail)
          else report("G44", a, detail)
        }
        // silent: option/either on a Pure newtype
        a.fun match {
          case Term.Select(_, n) if Set("option", "either", "optionAll", "eitherAll")(n.value) && newtypeParents(obj).exists(_._3.value == pure) =>
            report("silentPure", a, s"${obj.displayName}.${n.value}")
          case _ =>
        }
      case s @ Term.Select(q, n) if Set("option", "either", "optionAll", "eitherAll", "applyUnsafe", "assume")(n.value) && n.symbol.value.startsWith(ironValue) &&
          !s.parent.exists { case a: Term.Apply => a.fun eq s; case _ => false } && !inImport(s) =>
        val obj = q match { case qn: Term.Name => qn.symbol; case qs: Term.Select => qs.name.symbol; case _ => Symbol.None }
        if (isNewtypeObject(obj) && !allowedInCompanion(s, Some(obj))) {
          val detail = s"construct:${obj.displayName}.${n.value};function-reference"
          if (boundary) report("G45boundary", s, detail + ";" + boundaryKinds(path))
          else if (isController) report("G44conversion", s, detail)
          else report("G44", s, detail)
          if (Set("option", "either", "optionAll", "eitherAll")(n.value) && newtypeParents(obj).exists(_._3.value == pure)) report("silentPure", s, s"${obj.displayName}.${n.value}")
        }
    }

    doc.tree.traverse {
      case a: Term.Apply if !inImport(a) => rawSqlOf(a.fun).foreach(d => report("G58raw", a, d))
      case i: Term.ApplyInfix if rawSqlOperator(i.op.symbol) => report("G58raw", i, "operator:" + i.op.value)
    }

    // ---- G44 plain-typed defs fed by newtypes: record defs and calls --------------------------------------------
    doc.tree.collect {
      case d: Defn.Def if !d.name.symbol.isNone && !d.name.symbol.isLocal && d.name.symbol.value.startsWith(policy.projectPrefix) =>
        val public = !d.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected]) && !ancestors(d).exists {
          case x: Defn.Object => x.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
          case x: Defn.Class => x.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
          case x: Defn.Trait => x.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
          case _: Defn.Def | _: Term.Block => true
          case _ => false
        }
        val params = d.paramClauseGroups.flatMap(_.paramClauses).filterNot(_.mod.exists(m => m.is[Mod.Using] || m.is[Mod.Implicit])).flatMap(_.values).zipWithIndex.flatMap { case (p, i) =>
          resultOf(p.name.symbol).map(t => typeSymbol(dealias(t)).value).filter(plainDefTypes).map(t => i -> (p.name.value, t.split("[/#.]").last))
        }.toMap
        if (params.nonEmpty) plainDefs.put(d.name.symbol.value, PlainDef(d.name.symbol.value, mod, path, d.pos.startLine + 1, d.name.value, params, public, boundary))
    }
    doc.tree.collect {
      case a: Term.Apply =>
        val callee = a.fun match { case n: Term.Name => n.symbol; case Term.Select(_, n) => n.symbol; case _ => Symbol.None }
        if (callee.value.startsWith(policy.projectPrefix)) {
          // position within the flattened explicit argument lists
          def clauses(t: Term): List[Term.ArgClause] = t match { case x: Term.Apply => clauses(x.fun) :+ x.argClause; case _ => Nil }
          val all = clauses(a)
          val offset = all.takeWhile(_ ne a.argClause).map(_.values.size).sum
          a.argClause.values.zipWithIndex.foreach {
            case (s: Term.Select, i) if isIronValue(s.name) && !boundary =>
              plainCalls.add(PlainCall(callee.value, offset + i, termType(s.qual).flatMap(newtypeOf).fold("?")(_.displayName), path, a.pos.startLine + 1))
            case _ =>
          }
        }
    }

    // ---- G5problem and G55positional ---------------------------------------------------------------------------
    val problemWord = "(Problem|Error|Reason|Refusal|Failure|Why|Message)$".r
    val topLevelProblem = "^com/histmersion/(?:[a-z0-9]+/)*Problem\\.$".r
    val positionalName = "^.+(Result[0-9]*|Arg[0-9]+|Part[0-9]+)$".r
    doc.tree.traverse {
      case o: Defn.Object if isNewtypeObject(o.name.symbol) && !o.name.symbol.isLocal =>
        val base = newtypeParents(o.name.symbol).map(_._2.value)
        if (base.exists(b => b == "java/lang/String#" || b == "scala/Predef.String#") && problemWord.findFirstIn(o.name.value).nonEmpty) {
          if (topLevelProblem.pattern.matcher(o.name.symbol.value).matches) topProblems.add((mod, scope, path, o.pos.startLine + 1))
          else report("G5problem", o, o.name.symbol.value.stripPrefix("com/histmersion/"))
        }
        if (positionalName.pattern.matcher(o.name.value).matches) report("G55positional", o, "newtype:" + o.name.value)
      case t: Defn.Type if !t.name.symbol.isLocal && positionalName.pattern.matcher(t.name.value).matches &&
          !(t.body match { case Type.Select(q, Type.Name("T")) => q.syntax.split('.').last == t.name.value; case _ => false }) =>
        report("G55positional", t, "alias:" + t.name.value)
    }

    // ---- G46 alias pairs ----------------------------------------------------------------------------------------
    def statLists: List[List[Stat]] = doc.tree.collect {
      case t: Template => t.body.stats
      case p: Pkg => p.body.stats
      case s: Source => s.stats
    }
    statLists.foreach { stats =>
      stats.zip(stats.drop(1)).foreach {
        case (t: Defn.Type, v: Defn.Val) if v.pats.size == 1 && (v.pats.head match { case Pat.Var(n) => n.value == t.name.value; case _ => false }) &&
            v.decltpe.exists(_.is[Type.Singleton]) =>
          val ts = t.body match { case r: Type.Ref => r.symbol; case _ => Symbol.None }
          val vs = v.rhs match { case r: Term.Ref => r.symbol; case _ => Symbol.None }
          val renamed = if (t.body.syntax.split('.').last == t.name.value) "" else ";renamed"
          if (!ts.isNone && companionKey(ts) == companionKey(vs)) report("G46", t, s"${t.name.value}=${companionKey(ts)}$renamed")
          else if (t.body.syntax.replace("_root_.", "") == v.rhs.syntax.replace("_root_.", "")) report("G46", t, s"${t.name.value}=${t.body.syntax.replace("_root_.", "")};unresolved")
        case _ =>
      }
    }

    // ---- G47 ----------------------------------------------------------------------------------------------------
    val filePkg = doc.tree match { case s: Source => s.stats.collect { case p: Pkg => p.ref.syntax }.mkString("."); case _ => "" }
    val projectFile = filePkg == "com.histmersion" || filePkg.startsWith("com.histmersion.")
    doc.tree.traverse {
      case n: Term.Name if n.value == "_root_" => report("G47root", n, n.parent.map(_.syntax.take(80)).getOrElse(""))
      case h: Term.Select if h.name.value == "histmersion" && projectFile && !inImport(h) && (h.qual match {
            case Term.Name("com") => true
            case Term.Select(Term.Name("_root_"), Term.Name("com")) => true
            case _ => false
          }) =>
        def outermost(t: Tree): Tree = t.parent match {
          case Some(p: Term.Select) if p.qual eq t => outermost(p)
          case Some(p: Type.Select) if p.qual eq t => outermost(p)
          case Some(p: Type.Singleton) => p
          case _ => t
        }
        val top = outermost(h)
        val target = top match {
          case s: Term.Select => s.name.symbol
          case s: Type.Select => s.name.symbol
          case Type.Singleton(r) => r.symbol
          case _ => Symbol.None
        }
        def packageOf(s: Symbol): String = { var x = s; while (!x.isNone && !x.value.endsWith("/")) x = x.owner; x.value.stripSuffix("/").replace('/', '.') }
        val here = pkgOf(top)
        val targetPkg = packageOf(target)
        report("G47fqn", top, (if (targetPkg == here) "same-package:" else "other-package:") + top.syntax.replace("_root_.", "").take(100))
    }
    // G47self: once per outermost reference whose chain has a qualifier naming an enclosing template (or its companion)
    def qualifiers(t: Tree): List[Term.Name] = t match {
      case x: Term.Select => (x.qual match { case n: Term.Name => List(n); case q: Term.Select => qualifiers(q) :+ q.name; case _ => Nil })
      case x: Type.Select => (x.qual match { case n: Term.Name => List(n); case q: Term.Select => qualifiers(q) :+ q.name; case _ => Nil })
      case _ => Nil
    }
    def chainTop(t: Tree): Boolean = t.parent match {
      case Some(p: Term.Select) => !(p.qual eq t)
      case Some(p: Type.Select) => !(p.qual eq t)
      case _ => true
    }
    doc.tree.traverse {
      case sel @ (_: Term.Select | _: Type.Select) if chainTop(sel) && !inImport(sel) =>
        val ref = sel
        val qs = qualifiers(ref)
        val enclosing = enclosingTemplates(sel).map(companionKey).toSet
        val hit = qs.zipWithIndex.find { case (q, _) => !q.symbol.isNone && enclosing(companionKey(q.symbol)) && !isNewtypeObject(q.symbol) }
        hit.foreach { case (q, i) =>
          val node = (if (i == 0) q.parent else q.parent.flatMap(_.parent)).getOrElse(ref)
          val member = node match { case x: Term.Select => x.name.value; case x: Type.Select => x.name.value; case _ => "" }
          if (member != "T") report("G47self", node, (if (i > 0) "full-path:" else "name-qualified:") + node.syntax.replace("_root_.", "").take(100))
        }
    }
    doc.tree match {
      case src: Source =>
        val pkgs = src.stats.collect { case p: Pkg => p }
        if (pkgs.size == 1 && !pkgs.head.body.stats.exists(_.is[Pkg]) && pkgs.head.ref.syntax.startsWith("com.histmersion.") && pkgs.head.ref.syntax.count(_ == '.') >= 2)
          report("G47package", pkgs.head, pkgs.head.ref.syntax)
      case _ =>
    }

    // ---- G48 Tapir Schema modify(_.x)(_.description(...)) --------------------------------------------------------
    doc.tree.traverse {
      case a: Term.Apply => a.fun match {
        case inner: Term.Apply => inner.fun match {
          case Term.Select(_, m) if m.value == "modify" && m.symbol.value.startsWith("sttp/tapir/") &&
              a.argClause.values.exists(_.collect { case Term.Select(_, d) if d.value == "description" => d }.nonEmpty) =>
            report("G48", m, "modify(" + inner.argClause.values.map(_.syntax).mkString(",").take(40) + ")")
          case _ =>
        }
        case _ =>
      }
    }

    // ---- silent comparisons and interpolations ------------------------------------------------------------------
    doc.tree.traverse {
      case i: Term.ApplyInfix if (i.op.value == "==" || i.op.value == "!=") && i.argClause.values.size == 1 =>
        val l = classify(termType(i.lhs)); val r = classify(termType(i.argClause.values.head))
        if (l == Unknown || r == Unknown) note("comparison-side-type-unresolved", where(i))
        if (unrelated(l, r)) report("silentCompare", i, s"${i.op.value}:${show(l)}~${show(r)}")
      case a: Term.Apply if a.argClause.values.size == 1 => a.fun match {
        case Term.Select(recv, op) if Set("contains", "indexOf", "lastIndexOf")(op.value) && op.symbol.value.startsWith("scala/") =>
          val elem = termType(recv).map(dealias(_)) match {
            case Some(r: s.TypeRef) if r.typeArguments.nonEmpty => Some(r.typeArguments.head)
            case _ => None
          }
          val l = classify(elem); val r = classify(termType(a.argClause.values.head))
          if (elem.isEmpty) note("contains-receiver-element-unresolved", where(a))
          if (unrelated(l, r)) report("silentCompare", a, s"${op.value}:${show(l)}~${show(r)}")
        case _ =>
      }
      case i: Term.Interpolate =>
        i.args.foreach { arg =>
          val k = classify(termType(arg))
          val caseSym = arg match { case n: Term.Name => n.symbol; case s: Term.Select => s.name.symbol; case _ => Symbol.None }
          val enumCase = isEnumSym(caseSym) && !hasKind(caseSym, Set(s.SymbolInformation.Kind.CLASS, s.SymbolInformation.Kind.TRAIT))
          k match {
            case EnumK(e) => report("silentInterp", arg, s"${i.prefix.value}:${e.displayName}")
            case _ if enumCase => report("silentInterp", arg, s"${i.prefix.value}:${caseSym.owner.displayName}.${caseSym.displayName}")
            case Unknown => note("interpolation-arg-type-unresolved", where(arg))
            case _ =>
          }
        }
    }

    // ---- type definitions for G9 and standalone-object defs for G3 ---------------------------------------------
    def ownerOf(t: Tree): String = ancestors(t).collectFirst {
      case o: Defn.Object => o.name.symbol.value
      case c: Defn.Class => c.name.symbol.value
      case c: Defn.Trait => c.name.symbol.value
      case e: Defn.Enum => e.name.symbol.value
    }.getOrElse("")
    def addType(t: Tree, n: Name, kind: String): Unit =
      if (!n.symbol.isLocal) typeDefs.add(TypeDef(mod, path, t.pos.startLine + 1, pkgOf(t), ownerOf(t), n.value, kind, n.symbol.value, scope))
    doc.tree.traverse {
      case d: Defn.Class => addType(d, d.name, "class")
      case d: Defn.Trait => addType(d, d.name, "trait")
      case d: Defn.Enum => addType(d, d.name, "enum")
      case d: Defn.Object => addType(d, d.name, if (isNewtypeObject(d.name.symbol)) "newtype" else "object")
    }
    statLists.foreach { stats =>
      val siblings = stats.collect {
        case c: Defn.Class => c.name.value
        case c: Defn.Trait => c.name.value
        case c: Defn.Enum => c.name.value
        case c: Defn.Type => c.name.value
      }.toSet
      stats.foreach {
        case o: Defn.Object if !siblings(o.name.value) && !isNewtypeObject(o.name.symbol) =>
          o.templ.body.stats.foreach {
            case d: Defn.Def if !d.mods.exists(_.is[Mod.Override]) && d.name.value != "main" =>
              val first = d.paramClauseGroups.flatMap(_.paramClauses).filterNot(_.mod.exists(m => m.is[Mod.Using] || m.is[Mod.Implicit])).headOption.flatMap(_.values.headOption)
              first.foreach { p =>
                val tpe = resultOf(p.name.symbol)
                val sym = tpe.map(t => typeSymbol(t)).getOrElse(Symbol.None)
                val project = sym.value.startsWith(policy.projectPrefix)
                if (project) report("G3misplaced", d, s"${o.name.value}.${d.name.value}(${p.name.value}: ${sym.displayName})")
              }
            case _ =>
          }
        case _ =>
      }
    }
    patches.asPatch
  }

  /** The document's SemanticDB was written from other text than the file's (its md5 differs). Scalafix keeps the
    * document it loaded behind a scalafix-private accessor, so it is read reflectively; a document it cannot read is
    * taken as current. */
  private def staleSemanticdb(doc: SemanticDocument): Boolean = scala.util.Try {
    val internal = doc.getClass.getMethod("internal").invoke(doc)
    val textDocument = internal.getClass.getMethod("textDocument").invoke(internal).asInstanceOf[s.TextDocument]
    val md5 = java.security.MessageDigest.getInstance("MD5").digest(doc.input.text.getBytes(UTF_8)).map(b => f"${b & 255}%02X").mkString
    textDocument.md5.nonEmpty && textDocument.md5 != md5
  }.getOrElse(false)

  override def afterComplete(): Unit = {
    val seen = visited.asScala.toSet
    val staleFiles = stale.asScala.toList.distinct.sorted
    require(staleFiles.isEmpty, s"Stale SemanticDB for ${staleFiles.size} source(s) (compile them; or a leftover META-INF/semanticdb is read): " +
      staleFiles.take(5).mkString(", "))
    if (policy.requireCoverage) {
      val expectedFiles = sources(policy.roots)
      val missing = expectedFiles -- seen
      require(missing.isEmpty, s"Incomplete coverage: ${missing.size} source(s) not visited, e.g. ${missing.take(5).mkString(", ")}")
    }
    // G9/G50 prefix names within a module
    val defs = typeDefs.asScala.toList.distinct
    val byModule = defs.groupBy(_.module)
    val prefixRows = defs.flatMap { d =>
      val pool = byModule(d.module)
      val entities = pool.filter(e => e.name.length < d.name.length && d.name.startsWith(e.name) && d.name.charAt(e.name.length).isUpper &&
        Set("class", "trait", "enum", "newtype", "object")(e.kind) && (e.owner.isEmpty || e.owner == d.owner))
      entities.sortBy(e => -e.name.length).headOption.map { e =>
        val rest = d.name.drop(e.name.length)
        val role = policy.roleSuffixes.exists(r => rest == r || rest.endsWith(r))
        Finding(if (role) "G9prefixRole" else "G9prefix", d.module, d.path, d.line, s"${e.name}+$rest;${d.kind};entity=${e.symbol}")
      }
    }.groupBy(f => (f.path, f.detail.split(";").head)).values.map(_.minBy(_.line)).toList
    prefixRows.foreach(findings.add)
    // G44 plain-typed public defs fed by .value
    val calls = plainCalls.asScala.toList.groupBy(_.callee)
    plainDefs.asScala.values.foreach { d =>
      calls.get(d.symbol).foreach { cs =>
        val fed = cs.filter(c => d.params.contains(c.index))
        if (fed.nonEmpty && d.public && !d.boundary) {
          val ps = fed.groupBy(_.index).toList.sortBy(_._1).map { case (i, xs) => s"${d.params(i)._1}:${d.params(i)._2}<-${xs.map(_.newtype).distinct.sorted.mkString("|")}" }
          findings.add(Finding("G44plaindef", d.module, d.path, d.line, s"${d.name}(${ps.mkString(",")});calls=${fed.size}"))
        }
      }
    }
    // G5problem: a second top-level Problem in one module and scope is a copy of the layer's one
    topProblems.asScala.toList.groupBy(p => (p._1, p._2)).values.foreach { ps =>
      ps.sortBy(p => (p._3, p._4)).drop(1).foreach(p => findings.add(Finding("G5problem", p._1, p._3, p._4, "second top-level Problem")))
    }
    val rows = findings.asScala.toList.sortBy(f => (f.rule, f.path, f.line, f.detail))
    val header = "rule\tmodule\tpath\tline\tdetail"
    Files.write(Paths.get(policy.report), (header :: rows.map(_.row)).asJava, UTF_8)
    if (policy.unclassified.nonEmpty) {
      Option(Paths.get(policy.unclassified).getParent).foreach(Files.createDirectories(_))
      Files.write(Paths.get(policy.unclassified), ("shape\tcount\texample" :: unclassified.asScala.toList.sortBy(_._1).map { case (k, (n, w)) => s"$k\t$n\t$w" }).asJava, UTF_8)
    }
    if (policy.summary.nonEmpty) writeSummary(rows)
    if (policy.expected.nonEmpty) {
      val want = Files.readAllLines(Paths.get(policy.expected), UTF_8).asScala.toList.filter(_.nonEmpty)
      val got = header :: rows.map(_.row)
      val missing = want.filterNot(got.contains)
      val extra = got.filterNot(want.contains)
      require(missing.isEmpty && extra.isEmpty, "findings differ from " + policy.expected + "\nmissing:\n" + missing.mkString("\n") + "\nunexpected:\n" + extra.mkString("\n"))
    }
  }

  private def writeSummary(rows: List[GuidelineInventory.Finding]): Unit = {
    val rules = rows.map(_.rule).distinct.sorted
    val modules = rows.map(_.module).distinct.sorted
    val counts = rows.groupBy(f => (f.rule, f.module)).map { case (k, v) => k -> v.size }
    def table(header: List[String], body: List[List[String]]): List[String] =
      ("| " + header.mkString(" | ") + " |") :: ("|" + header.map(_ => "---").mkString("|") + "|") :: body.map(r => "| " + r.mkString(" | ") + " |")
    val byScope = rows.groupBy(f => (f.rule, policy.testMarkers.exists(f.path.contains)))
    val matrix = table("rule" :: "main" :: "test" :: "total" :: modules,
      rules.map(r => List(r, byScope.getOrElse((r, false), Nil).size.toString, byScope.getOrElse((r, true), Nil).size.toString,
        rows.count(_.rule == r).toString) ++ modules.map(m => counts.getOrElse((r, m), 0).toString)))
    val inBoundary = table(List("rule", "findings in boundaries.tsv files", "of which kind"), rules.map { r =>
      val b = rows.filter(f => f.rule == r && boundaryKinds.contains(f.path))
      List(r, b.size.toString, b.groupBy(f => boundaryKinds(f.path)).toList.sortBy(-_._2.size).map { case (k, v) => s"$k ${v.size}" }.mkString(", "))
    })
    val top = rules.flatMap { r =>
      val files = rows.filter(_.rule == r).groupBy(_.path).toList.map { case (p, v) => p -> v.size }.sortBy { case (p, n) => (-n, p) }.take(20)
      List("", s"### $r", "") ++ table(List("findings", "file"), files.map { case (p, n) => List(n.toString, "`" + p + "`") })
    }
    val shapes = unclassified.asScala.toList.sortBy(_._1).map { case (k, (n, w)) => s"- `$k`: $n occurrence(s), e.g. `$w`" }
    val text = List("# Guideline inventory", "",
      s"Generated by `tools/refactor/GuidelineInventory.scala` (`tools/refactor/guideline-inventory.conf`) over ${visited.size} sources, main and test. " +
      s"${rows.size} findings in `${policy.report}`; boundary files from `${policy.boundaries}` (${boundaryKinds.size} files). Heuristic rules are marked as such below.",
      "", "## Findings per rule and module", "") ++ matrix ++ List("", "Rule ids:", "") ++ GuidelineInventory.legend.map { case (k, v) => s"- `$k`: $v" } ++
      List("", "## Findings located in boundary files", "") ++ inBoundary ++
      List("", "## Top 20 files per rule") ++ top ++
      List("", "## Shapes seen but not classified", "", "Counted by the rule (no finding emitted):", "") ++ shapes ++
      List("", "Observed while building the rule:", "") ++ policy.notes.map("- " + _)
    Files.write(Paths.get(policy.summary), text.asJava, UTF_8)
  }
}
