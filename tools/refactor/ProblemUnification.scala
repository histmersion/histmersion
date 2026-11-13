import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.{semanticdb => s}

/** G60 step 1: the five String `Problem` newtypes (values `com.histmersion.Problem`, `api.Problem`, `domain.Problem`,
  * `plugin.Problem`, `pluginresolver.Problem`) become the one closed `com.histmersion.util.problem.Problem`. Only where
  * SemanticDB resolves a name to one of the five:
  *
  *  - `Problem(x)` (also `api.Problem(x)`, a renamed `ApiProblem(x)`, `Problem.assume(x)`) becomes
  *    `Problem.Unclassified(Legacy.Text(x))`; `Problem(_)` becomes a lambda; `Problem.of(t)` (values) reads `t.getMessage`;
  *  - a re-wrap `Problem(p.value)`, where `p` is one of the five, becomes `p`; so do `p.transformInto[Problem]` and the
  *    core's `FailureTransformers` (`valuesAsDomain`, `apiAsDomain`, `domainAsApi`); `xs.map(_.transformInto[Problem])`
  *    and `.left.map(...)` of it go as a whole;
  *  - `p.value`, `p.toString` and `$p` in an interpolation read the problem's sentence: `p.sentence` (the api's renderer
  *    `api.problem.Text`) in a module that sees the api, `p.legacyText` (the problem module's temporary accessor) in one
  *    that does not; `ps.mkString(...)` over problems maps them to it first;
  *  - `Problem.T`, `api.Problem`, a renamed `ApiProblem` as a type become `Problem`;
  *  - the imports of the five go, `com.histmersion.util.problem.{Legacy, Problem}` and `Text.sentence` come in as used.
  *
  * What it cannot decide (an unresolved receiver, a `+` on a problem, a collection of problems interpolated) is listed in
  * `report`, one row per place, for the compile-error pass. */
object ProblemUnification {
  case class Config(
    report: String = "refactor-audit/concept-table/scratch/problem-unification.tsv",
    semanticdbRoots: List[String] = List("target/out/jvm/scala-3.10.0", "target/out/sjs1/scala-3.10.0"),
    debug: Boolean = false,
    apiModules: List[String] = List("api", "core", "db", "server", "cli", "web", "files", "plugin-host", "plugin-testkit")
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  val iron = "io/github/iltotore/iron/"
  val oldAlias = "^com/histmersion/(?:api/|domain/|plugin/|pluginresolver/)?Problem\\$package\\.Problem#$".r
  val oldObject = "^com/histmersion/(?:api/|domain/|plugin/|pluginresolver/)?Problem\\$package\\.Problem\\.$".r
  val oldPackages = Set("com.histmersion", "com.histmersion.api", "com.histmersion.domain", "com.histmersion.plugin",
    "com.histmersion.pluginresolver", "api", "domain", "plugin", "pluginresolver")
  val transformers = Set("valuesAsDomain", "apiAsDomain", "domainAsApi")
  private val indexes = new ConcurrentHashMap[List[String], ConcurrentHashMap[String, s.SymbolInformation]]()
  def index(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
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
    // a document counts only when it describes the file as it is on disk: leftovers of older builds describe older texts
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.filter(d => md5(d.uri) == d.md5).foreach { d =>
        d.symbols.foreach(i => index.putIfAbsent(if (i.symbol.startsWith("local")) d.uri + "::" + i.symbol else i.symbol, i))
      }
    }
    index
  }
  private val reported = new ConcurrentHashMap[String, String]()
}

class ProblemUnification(config: ProblemUnification.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("ProblemUnification") {
  import ProblemUnification._
  def this() = this(ProblemUnification.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ProblemUnification")(Config()).map(cfg => new ProblemUnification(cfg, ProblemUnification.index(cfg.semanticdbRoots)))

  override def afterComplete(): Unit = {
    val p = Paths.get(config.report)
    Option(p.getParent).foreach(Files.createDirectories(_))
    Files.write(p, ("where\twhat" :: reported.asScala.toList.sortBy(_._1).map { case (k, v) => s"$k\t$v" }).asJava, UTF_8)
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    val rel = path.substring(math.max(0, path.indexOf("modules/")))
    val module = rel.stripPrefix("modules/").split("/src/").head
    val seesApi = config.apiModules.contains(module)
    val reader = if (seesApi) "sentence" else "legacyText"
    def report(t: Tree, what: String): Unit = { reported.put(s"$rel:${t.pos.startLine + 1}:${t.pos.startColumn + 1}", what); () }

    def infoS(sym: String): Option[s.SymbolInformation] =
      if (sym.isEmpty) None else Option(index.get(if (sym.startsWith("local")) rel + "::" + sym else sym))
    def info(sym: Symbol): Option[s.SymbolInformation] = if (sym.isNone) None else infoS(sym.value)
    def isObj(sym: Symbol): Boolean = !sym.isNone && oldObject.pattern.matcher(sym.value).matches
    def isAlias(sym: Symbol): Boolean = !sym.isNone && oldAlias.pattern.matcher(sym.value).matches
    def dealias(t: s.Type, depth: Int = 0): s.Type = if (depth > 12) t else t match {
      case a: s.AnnotatedType => dealias(a.tpe, depth + 1)
      case b: s.ByNameType => dealias(b.tpe, depth + 1)
      case r: s.RepeatedType => dealias(r.tpe, depth + 1)
      case r: s.TypeRef if !r.symbol.startsWith(iron) && !oldAlias.pattern.matcher(r.symbol).matches => infoS(r.symbol).map(_.signature) match {
        case Some(ts: s.TypeSignature) if ts.lowerBound == ts.upperBound && ts.upperBound != s.Type.Empty && ts.typeParameters.isEmpty =>
          dealias(ts.upperBound, depth + 1)
        case _ => t
      }
      case _ => t
    }
    def isProblemType(t: s.Type): Boolean = dealias(t) match {
      case r: s.TypeRef if oldAlias.pattern.matcher(r.symbol).matches => true
      case r: s.TypeRef if r.symbol.startsWith(iron) && r.symbol.endsWith("#T#") => r.prefix match {
        case st: s.SingleType => oldObject.pattern.matcher(st.symbol).matches
        case th: s.ThisType => oldObject.pattern.matcher(th.symbol).matches
        case _ => false
      }
      case _ => false
    }
    def containsProblem(t: s.Type): Boolean = dealias(t) match {
      case r: s.TypeRef => isProblemType(r) || r.typeArguments.exists(containsProblem)
      case _ => false
    }
    def resultOf(sym: Symbol): Option[s.Type] = info(sym).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature => m.returnType
    }
    def callResult(sym: Symbol): Option[s.Type] = info(sym).map(_.signature) match {
      case Some(m: s.MethodSignature) if m.parameterLists.nonEmpty => Some(m.returnType)
      case _ => None
    }
    def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
    def termType(t: Term): Option[s.Type] = t match {
      case n: Term.Name => resultOf(n.symbol)
      case sel: Term.Select => resultOf(sel.name.symbol)
      case a: Term.Apply => a.fun match {
        case n: Term.Name => callResult(n.symbol)
        case sel: Term.Select => callResult(sel.name.symbol)
        case _ => None
      }
      case b: Term.Block if b.stats.size == 1 => b.stats.head match { case x: Term => termType(x); case _ => None }
      case p: Term.Placeholder => placeholderType(p)
      case _ => None
    }
    def placeholderType(p: Term.Placeholder): Option[s.Type] =
      ancestors(p).collectFirst { case f: Term.AnonymousFunction => f }.flatMap(f => f.parent.flatMap(_.parent)).collect {
        case a: Term.Apply => a.fun
      }.collect { case Term.Select(recv, _) => recv }.flatMap(r => termType(r)).map(dealias(_)).collect {
        case r: s.TypeRef if r.typeArguments.size == 1 => r.typeArguments.head
      }
    def isProblem(t: Term): Boolean = termType(t).exists(isProblemType)
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(iron)
    /** The term names the old object: `Problem`, `api.Problem`, `ApiProblem`. */
    def objRef(t: Term): Boolean = t match {
      case n: Term.Name => isObj(n.symbol)
      case Term.Select(_, n) => isObj(n.symbol)
      case _ => false
    }
    def inImport(t: Tree): Boolean = ancestors(t).exists(a => a.is[Import] || a.is[Pkg] && a.asInstanceOf[Pkg].ref.pos.end >= t.pos.end)
    val constructNames = Set("apply", "assume", "applyUnsafe")

    var patches = List.empty[Patch]
    var keepsName = false
    var usesLegacy = false
    var usesReader = false
    val handled = scala.collection.mutable.Set.empty[Tree]
    def tokensBetween(from: Int, to: Int): List[scala.meta.tokens.Token] = doc.tree.tokens.filter(tk => tk.start >= from && tk.end <= to && tk.end > tk.start).toList
    def removeRange(from: Int, to: Int): Patch = Patch.fromIterable(tokensBetween(from, to).map(Patch.removeToken))
    def construct(arg: String): String = { usesLegacy = true; keepsName = true; s"Problem.Unclassified(Legacy.Text($arg))" }
    def readOf(t: Term): Patch = { usesReader = true; t match {
      case _: Term.Name | _: Term.Select | _: Term.Apply | _: Term.ApplyType | _: Term.Placeholder => Patch.addRight(t, s".$reader")
      case _ => Patch.addLeft(t, "(") + Patch.addRight(t, s").$reader")
    }}

    doc.tree.traverse {
      // ---- constructions --------------------------------------------------------------------------------------
      case a: Term.Apply if !inImport(a) && !handled(a) && (objRef(a.fun) || (a.fun match {
            case Term.Select(q, n) => objRef(q) && (constructNames(n.value) || n.value == "of")
            case _ => false
          })) =>
        val isOf = a.fun match { case Term.Select(q, n) => objRef(q) && n.value == "of"; case _ => false }
        a.argClause.values match {
          case List(sel @ Term.Select(q, n)) if isIronValue(n) && isProblem(q) && !isOf =>
            // re-wrap: Problem(p.value) is p
            patches ::= (removeRange(a.pos.start, q.pos.start) + removeRange(q.pos.end, a.pos.end))
            handled += sel
          case List(p: Term.Placeholder) =>
            patches ::= Patch.replaceTree(a, s"(text => ${construct("text")})")
          case List(arg) =>
            keepsName = true; usesLegacy = true
            patches ::= Patch.replaceTree(a.fun, "Problem.Unclassified")
            patches ::= Patch.addLeft(arg, "Legacy.Text(")
            patches ::= Patch.addRight(arg, if (isOf) ".getMessage)" else ")")
          case _ => report(a, "construction with several arguments")
        }
      // ---- p.value, p.toString ----------------------------------------------------------------------------------
      case sel @ Term.Select(q, n) if !inImport(sel) && !handled(sel) && (isIronValue(n) || n.value == "toString") && isProblem(q) =>
        usesReader = true
        patches ::= Patch.replaceTree(n, reader)
      // ---- transformInto[Problem] and the core's transformers ---------------------------------------------------
      case at @ Term.ApplyType.After_4_6_0(sel @ Term.Select(q, n), targs) if n.value == "transformInto" && targs.values.size == 1 &&
          (targs.values.head match {
            case t: Type.Name => isAlias(t.symbol)
            case t: Type.Select => isAlias(t.name.symbol)
            case _ => false
          }) =>
        dropConversion(at, q)
      case sel @ Term.Select(q, n) if transformers(n.value) && !inImport(sel) =>
        dropConversion(sel, q)
      // ---- interpolation ---------------------------------------------------------------------------------------
      case i: Term.Interpolate =>
        if (config.debug) i.args.foreach { a =>
          val t = a match { case b: Term.Block if b.stats.size == 1 => b.stats.head; case o => o }
          val sym = t match { case n: Term.Name => n.symbol; case Term.Select(_, n) => n.symbol; case _ => Symbol.None }
          report(a, s"debug ${t.syntax} sym=${sym.value} info=${info(sym).map(_.signature.toString.take(200))}")
        }
        i.args.foreach {
          case n: Term.Name if isProblem(n) => usesReader = true; patches ::= Patch.replaceTree(n, s"{${n.value}.$reader}")
          case b: Term.Block if b.stats.size == 1 => b.stats.head match {
            case t: Term if isProblem(t) =>
              patches ::= readOf(t)
            case t: Term if termType(t).exists(containsProblem) && !isProblem(t) => report(t, "a value holding problems interpolated")
            case _ =>
          }
          case n: Term.Name if termType(n).exists(containsProblem) => report(n, "a value holding problems interpolated")
          case _ =>
        }
      // ---- mkString over problems, + on a problem --------------------------------------------------------------
      case sel @ Term.Select(q, n) if n.value == "mkString" && termType(q).exists(t => dealias(t) match {
            case r: s.TypeRef => r.typeArguments.exists(isProblemType)
            case _ => false
          }) =>
        usesReader = true
        patches ::= Patch.addRight(q, s".map(_.$reader)")
      case inf: Term.ApplyInfix if inf.op.value == "+" && (isProblem(inf.lhs) || inf.argClause.values.exists(isProblem)) =>
        report(inf, "+ on a problem")
      // ---- types -----------------------------------------------------------------------------------------------
      case t @ Type.Select(q, Type.Name("T")) if (q match { case n: Term.Name => isObj(n.symbol); case Term.Select(_, n) => isObj(n.symbol); case _ => false }) =>
        keepsName = true; patches ::= Patch.replaceTree(t, "Problem")
      case t @ Type.Select(_, n) if isAlias(n.symbol) && !inImport(t) =>
        keepsName = true; patches ::= Patch.replaceTree(t, "Problem")
      case t: Type.Name if isAlias(t.symbol) && !inImport(t) =>
        keepsName = true; if (t.value != "Problem") patches ::= Patch.replaceTree(t, "Problem")
      // ---- other references to the object (Problem.of handled above) --------------------------------------------
      case n: Term.Name if isObj(n.symbol) && !inImport(n) && !n.parent.exists(p => p.is[Term.Select] || p.is[Type]) && !n.parent.exists { case a: Term.Apply => a.fun eq n; case _ => false } =>
        report(n, "the old object used as a value")
      case sel @ Term.Select(q, n) if objRef(q) && !inImport(sel) && !constructNames(n.value) && n.value != "of" && !sel.parent.exists(_.is[Type]) =>
        report(sel, s"old companion member .${n.value}")
    }

    def dropConversion(whole: Term, q: Term): Unit = q match {
      case p: Term.Placeholder =>
        // xs.map(_.transformInto[Problem]) / e.left.map(...) go as a whole
        val lambda = ancestors(whole).collectFirst { case f: Term.AnonymousFunction => f }
        lambda.flatMap(f => f.parent.flatMap(_.parent)) match {
          case Some(app @ Term.Apply.After_4_6_0(Term.Select(recv, m), _)) if Set("map")(m.value) =>
            val start = recv match {
              case Term.Select(r, l) if l.value == "left" => r.pos.end
              case r => r.pos.end
            }
            patches ::= removeRange(start, app.pos.end)
          case _ => report(whole, "conversion of a placeholder in an unknown shape")
        }
      case _ =>
        patches ::= removeRange(q.pos.end, whole.pos.end)
    }

    // ---- imports ------------------------------------------------------------------------------------------------
    doc.tree.traverse {
      case i: Importer =>
        val ref = i.ref.syntax
        val gone = i.importees.filter {
          case Importee.Name(n) => n.value == "Problem" && oldPackages(ref)
          case Importee.Rename(n, _) => n.value == "Problem" && oldPackages(ref)
          case _ => false
        }
        if (ref.endsWith("FailureTransformers")) patches ::= Patch.removeImportee(i.importees.head) + Patch.fromIterable(i.importees.tail.map(Patch.removeImportee))
        else gone.foreach(g => patches ::= Patch.removeImportee(g))
    }
    val inProblemPackage = doc.tree.collect { case p: Pkg => p.ref.syntax }.mkString(".").endsWith("util.problem")
    val wanted = List(
      Option.when(keepsName && !inProblemPackage)(if (usesLegacy) "import com.histmersion.util.problem.{Legacy, Problem}" else "import com.histmersion.util.problem.Problem"),
      Option.when(!keepsName && usesLegacy && !inProblemPackage)("import com.histmersion.util.problem.Legacy"),
      Option.when(usesReader && seesApi)("import com.histmersion.util.problem.Text.sentence")
    ).flatten
    val importPatch = if (wanted.isEmpty) Patch.empty else {
      val pkgs = doc.tree.collect { case p: Pkg => p }
      val innermost = pkgs.lastOption
      val firstImport = innermost.flatMap(_.stats.collectFirst { case im: Import => im })
      (firstImport, innermost) match {
        case (Some(im), _) => Patch.addLeft(im, wanted.map(_ + "\n").mkString)
        case (None, Some(p)) => Patch.addRight(p.ref, "\n\n" + wanted.mkString("\n"))
        case _ => Patch.empty
      }
    }
    Patch.fromIterable(patches) + importPatch
  }
}
