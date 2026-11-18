import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.meta.internal.{semanticdb => s}
import scalafix.lint.LintSeverity

/** G60 (speed plan C): a method that fails with a wrapper (`Api.Error`, `Cli.Error`) fails with `Problems`, and the
  * wrapper is built where a caller still wants one. Nothing a user sees changes: the wrapper a caller gets is the one the
  * method built before, field for field.
  *
  * A wrapper carries more than its problems: `Api.Error` a code (it is in the body, and it decides the status for some
  * codes), `Cli.Error` an exit code and two flags. So a method is converted only when every wrapper it builds carries
  * the same extra fields, written with names any caller can see (`exitCode = ExitCode.Usage`, `Some(Api.Error.Code(
  * Write.CodeNotFound))`, or none); the callers then rebuild exactly that wrapper:
  *
  *  - the method `def f(...): Either[W, A]` becomes `Either[Problems, A]`; each `W(p, extra)` in it becomes `p`; an
  *    adapter `x.left.map(W(_, extra))` (or `x.left.map(e => W(e, extra))`) becomes `x`;
  *  - each call `f(...)` outside it becomes `f(...).left.map(W(_, extra))`.
  *
  * A method is left as it is (the reason is reported) when its body uses any other value of the wrapper's type (a
  * call of a method that returns one, a parameter, a local, a field read such as `e.code`), writes the type, builds
  * wrappers with different extra fields or from local values, is abstract, overrides or is overridden, is used as a
  * function value, or has a caller that does not see the names its extra fields need. Each run converts the methods
  * that meet this now, the leaves of the call graph; a caller qualifies in the next run, after a compile, when the
  * adapters the run gave it are all it has left (they are adapters of its own extra fields, so they go).
  *
  * Two passes over the same SemanticDB: `mode = collect` writes the facts (`plan`) and the report of every wrapper
  * field a conversion cannot drop (`carriers`); `mode = apply` reads them and patches. `only` limits the methods
  * converted to files under those prefixes (a module at a time); callers are patched wherever they are.
  */
object ErrorTypesToProblems {
  case class Config(
    mode: String = "collect",
    plan: String = "target/error-types/plan.tsv",
    report: String = "refactor-audit/g60/error-types/report.tsv",
    carriers: String = "refactor-audit/g60/error-types/carriers.tsv",
    only: List[String] = Nil,
    roots: List[String] = List("modules"),
    mainMarker: String = "/src/main/",
    wrappers: List[String] = List("com/histmersion/api/Api.Error#", "com/histmersion/cli/Cli.Error#"),
    problemsType: String = "com/histmersion/util/problem/Problems$package.Problems#",
    problemsImport: String = "com.histmersion.util.problem.Problems",
    // what the code reads off a wrapper beyond its problems: the fields a conversion cannot drop
    carrierFields: List[String] = List("code", "isNotFound", "isInvalidCitekey", "field", "status", "exitCode", "notFound", "workingDirDenied"),
    semanticdbRoots: List[String] = List("target/out/jvm/scala-3.10.1", "target/out/sjs1/scala-3.10.1"),
    // extra fields this run does not move to the callers (a regex found in them): `Code\\(` keeps the methods whose
    // wrapper builds an `Api.Error.Code`, a newtype each caller would build again (one G44 finding per caller)
    refuseExtra: String = "",
    // files the rule adds no import to (ContractsSuite: a domain file names the api only in a boundary); a method with
    // a caller there that does not see the wrapper already stays
    noImportIn: List[String] = List("modules/server/src/main/scala/com/histmersion/domain/"),
    emitLints: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  def clean(text: String): String = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
  def unclean(cell: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < cell.length) {
      if (cell.charAt(i) == '\\' && i + 1 < cell.length) {
        out.append(cell.charAt(i + 1) match { case 't' => '\t'; case 'n' => '\n'; case 'r' => '\r'; case o => o }); i += 2
      } else { out.append(cell.charAt(i)); i += 1 }
    }
    out.toString
  }

  /** A method that fails with a wrapper, as collect saw it. `extra` is its wrappers' extra fields (`-` when they differ
    * or it builds none: `?` none); `needs` the names they and the wrapper are written with (`ident=symbol`). */
  case class Method(sym: String, path: String, wrapper: String, arity: Int, extra: String, needs: Set[String], reasons: List[String])
  /** A call of such a method: the file, the method around the call, and whether the call ends in an indented block. */
  case class Call(callee: String, path: String, inside: String, indented: Boolean)

  /** The facts of collect, and the methods apply converts. */
  case class Plan(methods: Map[String, Method], calls: Map[String, List[Call]], valueRefs: Set[String], overridden: Set[String],
      names: Map[String, Set[String]]) {
    /** Why a method stays: its own reasons, then what its callers and the rest of the code need. */
    def verdict(m: Method, only: List[String], refuseExtra: String = "", noImportIn: List[String] = Nil): List[String] = {
      val callers = calls.getOrElse(m.sym, Nil)
      // a caller in a layer that may not import the wrapper's owner (a domain file naming the api) must see it already
      val walled = callers.filter { c =>
        val there = names.getOrElse(c.path, Set.empty)
        noImportIn.exists(c.path.startsWith) && m.needs.exists(n => !there(n) && !n.split("=", 2)(1).matches("(scala|java/lang)/.*"))
      }
      // a caller that does not see a name gets its import; one that writes the name for something else cannot
      val blind = callers.filter { c =>
        val there = names.getOrElse(c.path, Set.empty)
        m.needs.exists { n =>
          val ident = n.split("=", 2)(0)
          !there(n) && there.exists(_.startsWith(ident + "="))
        }
      }
      m.reasons ++
        Option.when(only.nonEmpty && !only.exists(m.path.startsWith))("outside this run's modules") ++
        Option.when(refuseExtra.nonEmpty && refuseExtra.r.findFirstIn(m.extra).nonEmpty)(s"its extra fields build what each caller would build again ($refuseExtra)") ++
        Option.when(overridden(m.sym))("overridden") ++
        Option.when(m.arity > 0 && valueRefs(m.sym))("used as a function value, or called without its name") ++
        Option.when(callers.exists(_.indented))("a call ends in an indented block") ++
        blind.headOption.map(c => s"a caller in ${c.path} names ${m.needs.map(_.split("=", 2)(0)).mkString(" or ")} otherwise") ++
        walled.headOption.map(c => s"a caller in ${c.path} may not import ${m.needs.map(_.split("=", 2)(0)).mkString(" or ")}")
    }
    def converted(only: List[String], refuseExtra: String = "", noImportIn: List[String] = Nil): Map[String, Method] =
      methods.filter { case (_, m) => verdict(m, only, refuseExtra, noImportIn).isEmpty }
  }
  object Plan {
    def read(path: String): Plan = {
      val lines = Files.readAllLines(Paths.get(path), UTF_8).asScala.toList.map(_.split("\t", -1).toList.map(unclean))
      val methods = lines.collect { case "def" :: sym :: p :: w :: arity :: extra :: needs :: reasons :: Nil =>
        sym -> Method(sym, p, w, arity.toInt, extra, needs.split(";").filter(_.nonEmpty).toSet, reasons.split(";").filter(_.nonEmpty).toList)
      }.toMap
      val calls = lines.collect { case "call" :: callee :: p :: inside :: indented :: Nil => Call(callee, p, inside, indented == "1") }.groupBy(_.callee)
      val refs = lines.collect { case "ref" :: sym :: Nil => sym }.toSet
      val over = lines.collect { case "over" :: sym :: Nil => sym }.toSet
      val names = lines.collect { case "names" :: p :: ns :: Nil => p -> ns.split(";").filter(_.nonEmpty).toSet }.toMap
      Plan(methods, calls, refs, over, names)
    }
  }

  private val plans = new ConcurrentHashMap[String, Plan]()
  private[ErrorTypesToProblems] val facts = new ConcurrentLinkedQueue[String]()
  private[ErrorTypesToProblems] val carrierRows = new ConcurrentLinkedQueue[String]()

  private val indexes = new ConcurrentHashMap[List[String], ConcurrentHashMap[String, s.SymbolInformation]]()
  def index(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
  /** The symbols of every current SemanticDB document under `roots`, locals under `<uri>::<local>`. */
  private def buildIndex(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = {
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
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.filter(d => md5(d.uri) == d.md5).foreach { d =>
        d.symbols.foreach(i => index.putIfAbsent(if (i.symbol.startsWith("local")) d.uri + "::" + i.symbol else i.symbol, i))
      }
    }
    index
  }
}

class ErrorTypesToProblems(config: ErrorTypesToProblems.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("ErrorTypesToProblems") {
  import ErrorTypesToProblems._
  def this() = this(ErrorTypesToProblems.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ErrorTypesToProblems")(Config()).map(cfg => new ErrorTypesToProblems(cfg, ErrorTypesToProblems.index(cfg.semanticdbRoots)))

  override def beforeStart(): Unit = { facts.clear(); carrierRows.clear() }

  private def write(file: String, lines: List[String]): Unit = if (file.nonEmpty) {
    val p = Paths.get(file)
    Option(p.getParent).foreach(Files.createDirectories(_))
    Files.write(p, lines.asJava, UTF_8)
  }

  override def afterComplete(): Unit = if (config.mode == "collect") {
    write(config.plan, facts.asScala.toList.sorted)
    write(config.carriers, "path\tline\tenclosing\twhat\ttext" :: carrierRows.asScala.toList.sorted)
    val plan = Plan.read(config.plan)
    write(config.report, "method\tpath\twrapper\textra\tverdict" :: plan.methods.values.toList.sortBy(m => (m.path, m.sym)).map { m =>
      val v = plan.verdict(m, config.only, config.refuseExtra, config.noImportIn)
      List(m.sym, m.path, m.wrapper, m.extra, if (v.isEmpty) "converted" else v.mkString("; ")).map(clean).mkString("\t")
    })
  }

  private lazy val plan: Plan = plans.computeIfAbsent(config.plan, p => Plan.read(p))
  private lazy val converted: Map[String, Method] = plan.converted(config.only, config.refuseExtra, config.noImportIn)

  // ---- symbols and types ---------------------------------------------------------------------------------------------
  private val Wrappers = config.wrappers.toSet
  private def companion(w: String): String = w.stripSuffix("#") + "."
  private def relative(doc: SemanticDocument): String = {
    val abs = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    config.roots.map(r => abs.indexOf(r + "/")).filter(_ >= 0).sorted.headOption.map(abs.substring).getOrElse(abs)
  }
  private def info(sym: Symbol, file: String): Option[s.SymbolInformation] =
    if (sym.isNone) None else Option(index.get(if (sym.isLocal) file + "::" + sym.value else sym.value))
  private def mentions(t: s.Type): Boolean = t match {
    case r: s.TypeRef => Wrappers(r.symbol) || r.typeArguments.exists(mentions) || mentions(r.prefix)
    case a: s.AnnotatedType => mentions(a.tpe)
    case b: s.ByNameType => mentions(b.tpe)
    case r: s.RepeatedType => mentions(r.tpe)
    case w: s.WithType => w.types.exists(mentions)
    case u: s.UnionType => u.types.exists(mentions)
    case i: s.IntersectionType => i.types.exists(mentions)
    case st: s.StructuralType => mentions(st.tpe)
    case e: s.ExistentialType => mentions(e.tpe)
    case u: s.UniversalType => mentions(u.tpe)
    case _ => false
  }
  /** Is a value of this symbol a wrapper, or does calling it give one (in any type: `Either[W, A]`, `Option[W]`)? */
  private def wrapperTyped(sym: Symbol, file: String): Boolean = info(sym, file).map(_.signature).exists {
    case v: s.ValueSignature => mentions(v.tpe)
    case m: s.MethodSignature => mentions(m.returnType) || m.parameterLists.exists(_.symlinks.exists(p => index.containsKey(p) && wrapperTyped(Symbol(p), file)))
    case _ => false
  }
  private def symbolOf(t: Tree)(implicit doc: SemanticDocument): Symbol = t match {
    case n: Term.Name => n.symbol
    case n: Type.Name => n.symbol
    case Term.Select(_, n) => n.symbol
    case Type.Select(_, n) => n.symbol
    case Term.ApplyType.After_4_6_0(f, _) => symbolOf(f)
    case _ => Symbol.None
  }
  /** The wrapper a construction `W(...)` builds. */
  private def constructs(a: Term.Apply)(implicit doc: SemanticDocument): Option[String] = {
    val sym = symbolOf(a.fun).value
    config.wrappers.find(w => sym == companion(w) || sym.startsWith(companion(w) + "apply(") || sym == w.stripSuffix("#") + "#`<init>`().")
  }
  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def single(t: Tree): String = t.syntax.replaceAll("\\s+", " ")

  /** The extra fields of a construction as written (`exitCode = ExitCode.Usage`), `` when it has none. */
  private def extraOf(a: Term.Apply): String = a.argClause.values.drop(1).map(single).mkString(", ")
  /** The leftmost names of the extra fields: what a caller must see besides the wrapper's own qualifier. */
  private def needsOf(a: Term.Apply)(implicit doc: SemanticDocument): List[(String, Symbol)] =
    a.argClause.values.drop(1).flatMap(_.collect {
      case n: Term.Name if !n.parent.exists { case Term.Select(_, nm) => nm eq n; case Term.Assign(lhs, _) => lhs eq n; case _ => false } => n
    }).map(n => n.value -> n.symbol)
  /** The wrapper as a caller writes it (`Cli.Error` for `com/histmersion/cli/Cli.Error#`), and the name it needs. */
  private def written(wrapper: String): String = wrapper.split('/').last.stripSuffix("#")
  private def qualifierNeed(wrapper: String): String = {
    val owner = wrapper.stripSuffix("#").split('.').dropRight(1).mkString(".")
    s"${owner.split('/').last}=$owner."
  }
  /** `x.left.map(W(_, ...))` or `x.left.map(e => W(e, ...))`: the adapter around a construction, and its receiver. */
  private def adapter(a: Term.Apply): Option[(Term.Apply, Term)] = {
    def leftMap(app: Tree): Option[(Term.Apply, Term)] = app match {
      case outer: Term.Apply if outer.argClause.values.size == 1 => outer.fun match {
        case Term.Select(Term.Select(recv, Term.Name("left")), Term.Name("map")) => Some(outer -> recv)
        case _ => None
      }
      case _ => None
    }
    a.argClause.values.headOption match {
      case Some(_: Term.Placeholder) =>
        // `W(_)` is a function: the application inside an anonymous function
        val function = a.parent.collect { case f: Term.AnonymousFunction => f }.getOrElse(a)
        function.parent.collect { case ac: Term.ArgClause => ac }.flatMap(_.parent).flatMap(leftMap)
      case Some(n: Term.Name) => a.parent match {
        case Some(f: Term.Function) if f.paramClause.values.map(_.name.value) == List(n.value) =>
          f.parent.collect { case ac: Term.ArgClause => ac }.flatMap(_.parent).flatMap(leftMap)
        case _ => None
      }
      case _ => None
    }
  }
  /** The call a reference to a method is part of: the outermost application of it, or the reference itself. */
  private def callOf(ref: Term): Term = {
    def up(t: Term): Term = t.parent match {
      case Some(a: Term.Apply) if a.fun eq t => up(a)
      case Some(at: Term.ApplyType) if at.fun eq t => up(at)
      case Some(sel: Term.Select) if (sel.name eq t) => up(sel)
      case _ => t
    }
    up(ref)
  }
  private def applied(ref: Term): Boolean = ref.parent.exists {
    case a: Term.Apply => a.fun eq ref
    case at: Term.ApplyType => at.fun eq ref
    case sel: Term.Select if sel.name eq ref => applied(sel)
    case _ => false
  }
  /** A call whose last argument is a block without braces (`f(x):` and an indented lambda): text after it would join it. */
  private def indented(call: Term): Boolean = call match {
    case a: Term.Apply => a.argClause.values.lastOption.exists(b => (b.is[Term.Block] || b.is[Term.Function] || b.is[Term.PartialFunction]) &&
      !b.tokens.headOption.exists(_.text == "{") && !a.tokens.lastOption.exists(t => t.text == ")" || t.text == "}"))
    case _ => false
  }
  /** The `apply` methods an application calls without naming them: `Perform(c)(cmd)` calls `Perform#apply`, `obj(x)`
    * calls `obj.apply`. They have no name in the tree, so the scan of names cannot see them. */
  private def implicitApplies(fun: Term, file: String)(implicit doc: SemanticDocument): List[String] = {
    def appliesOf(owner: String): List[String] = (0 to 3).toList.map(i => owner + (if (i == 0) "apply()." else s"apply(+$i)."))
    def classOf(t: s.Type): Option[String] = t match {
      case r: s.TypeRef if r.symbol.endsWith("#") => Some(r.symbol)
      case _ => None
    }
    def resultOwner(sym: Symbol): Option[String] = info(sym, file).map(_.signature).flatMap {
      case v: s.ValueSignature => classOf(v.tpe)
      case m: s.MethodSignature => classOf(m.returnType)
      case _: s.ClassSignature if sym.value.endsWith(".") => Some(sym.value)
      case _ => None
    }
    val owners = fun match {
      case _: Term.Name | _: Term.Select =>
        val sym = symbolOf(fun)
        info(sym, file).map(_.signature) match {
          case Some(m: s.MethodSignature) if m.parameterLists.nonEmpty => Nil // an ordinary call
          case _ => resultOwner(sym).toList
        }
      case a: Term.Apply =>
        val sym = symbolOf(a.fun)
        if (sym.value.endsWith("#`<init>`().")) List(sym.value.stripSuffix("`<init>`()."))
        else if (sym.value.endsWith("#")) List(sym.value) // a creator application names the class
        else resultOwner(sym).toList
      case _ => Nil
    }
    owners.flatMap(appliesOf).filter(a => index.containsKey(a) && wrapperTyped(Symbol(a), file))
  }
  private def enclosing(t: Tree)(implicit doc: SemanticDocument): String =
    ancestors(t).collectFirst { case d: Defn.Def if !d.name.symbol.isLocal => d.name.symbol.value }.getOrElse("-")
  private def wrapperIn(t: Type)(implicit doc: SemanticDocument): Option[(String, Type)] = t match {
    case Type.Apply.After_4_6_0(Type.Name("Either"), args) if args.values.size == 2 =>
      args.values.head match {
        case w @ (_: Type.Select | _: Type.Name) => Some(symbolOf(w).value).filter(Wrappers).map(_ -> w)
        case _ => None
      }
    case _ => None
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val file = relative(doc)
    if (config.mode == "collect") { collect(file); Patch.empty } else rewrite(file)
  }

  // ---- collect -------------------------------------------------------------------------------------------------------
  private def collect(file: String)(implicit doc: SemanticDocument): Unit = {
    def fact(cells: String*): Unit = { facts.add(cells.map(clean).mkString("\t")); () }
    val main = file.contains(config.mainMarker)
    val names = scala.collection.mutable.SortedSet.empty[String]
    doc.tree.traverse {
      case n: Term.Name if !n.symbol.isNone && !n.symbol.isLocal => names += s"${n.value}=${n.symbol.value}"
    }
    fact("names", file, names.mkString(";"))
    // what overrides what, and every reference of a method: calls and values
    doc.tree.traverse {
      case d: Defn.Def => info(d.name.symbol, file).toList.flatMap(_.overriddenSymbols).foreach(o => fact("over", o))
      case a: Term.Apply if constructs(a).isEmpty => implicitApplies(a.fun, file).foreach(sym => fact("ref", sym))
      case n: Term.Name if !n.symbol.isNone && !n.symbol.isLocal && !n.parent.exists { case d: Defn.Def => d.name eq n; case _ => false } &&
          info(n.symbol, file).exists(i => i.signature.isInstanceOf[s.MethodSignature] && wrapperTyped(n.symbol, file)) =>
        val call = callOf(n)
        val arity = info(n.symbol, file).map(_.signature).collect { case m: s.MethodSignature => m.parameterLists.size }.getOrElse(0)
        if (arity > 0 && !applied(n)) fact("ref", n.symbol.value)
        else fact("call", n.symbol.value, file, enclosing(n), if (indented(call)) "1" else "0")
    }
    // the methods that fail with a wrapper
    if (main) doc.tree.traverse {
      case d: Defn.Def if !d.name.symbol.isLocal && d.decltpe.flatMap(wrapperIn).nonEmpty =>
        val (wrapper, _) = d.decltpe.flatMap(wrapperIn).get
        val self = d.name.symbol
        val reasons = scala.collection.mutable.LinkedHashSet.empty[String]
        val extras = scala.collection.mutable.LinkedHashSet.empty[String]
        val needs = scala.collection.mutable.LinkedHashSet.empty[String]
        val ownConstructions = scala.collection.mutable.Set.empty[Tree]
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).foreach { p =>
          if (p.decltpe.exists(t => t.collect { case tt @ (_: Type.Name | _: Type.Select) if Wrappers(symbolOf(tt).value) => tt }.nonEmpty)) reasons += s"parameter ${p.name.value} is a wrapper"
        }
        d.body.traverse {
          case a: Term.Apply if constructs(a).nonEmpty =>
            ownConstructions += a
            if (!constructs(a).contains(wrapper)) reasons += "builds another wrapper"
            a.argClause.values.headOption match {
              case Some(_: Term.Placeholder) if adapter(a).isEmpty => reasons += "a wrapper built as a function"
              case None => reasons += "a wrapper built with no problems"
              case _ =>
            }
            extras += extraOf(a)
            needsOf(a).foreach { case (n, sym) =>
              // a caller can name a member of an object only: not a local, a parameter or a member of an instance
              val stable = !sym.isNone && !sym.isLocal && !sym.value.contains("#") && !sym.value.contains(").(")
              if (!stable) reasons += s"extra fields from a local value ($n)" else needs += s"$n=${sym.value}"
            }
            // a construction's extra fields are not references of the wrapper
          case t @ (_: Type.Name | _: Type.Select) if Wrappers(symbolOf(t).value) => reasons += "writes the wrapper's type"
        }
        info(self, file).toList.flatMap(_.overriddenSymbols).headOption.foreach(o => reasons += s"overrides $o")
        d.body.traverse {
          case n: Term.Name if n.symbol != self && !ancestors(n).exists(ownConstructions) && wrapperTyped(n.symbol, file) =>
            reasons += s"uses ${n.value} (${n.symbol.value})"
          case a: Term.Apply if !ownConstructions(a) => implicitApplies(a.fun, file).headOption.foreach(sym => reasons += s"uses $sym")
        }
        needs += qualifierNeed(wrapper)
        val extra = if (extras.size > 1) { reasons += s"wrappers with different extra fields (${extras.mkString(" | ")})"; "-" } else extras.headOption.getOrElse("")
        val arity = d.paramClauseGroups.flatMap(_.paramClauses).size
        fact("def", self.value, file, wrapper, arity.toString, extra, needs.mkString(";"), reasons.take(3).mkString(";"))
    }
    // the fields a conversion cannot drop: a construction with extra fields, a read of one
    doc.tree.traverse {
      case a: Term.Apply if constructs(a).nonEmpty && extraOf(a).nonEmpty =>
        carrierRows.add(List(file, (a.pos.startLine + 1).toString, enclosing(a), "builds", single(a).take(200)).map(clean).mkString("\t"))
      case sel @ Term.Select(q, n) if config.carrierFields.contains(n.value) && (q match {
            case qn: Term.Name => wrapperTyped(qn.symbol, file) && info(qn.symbol, file).exists(_.signature.isInstanceOf[s.ValueSignature])
            case _ => false
          }) =>
        carrierRows.add(List(file, (sel.pos.startLine + 1).toString, enclosing(sel), s"reads .${n.value}", single(sel)).map(clean).mkString("\t"))
    }
  }

  // ---- apply ---------------------------------------------------------------------------------------------------------
  private def rewrite(file: String)(implicit doc: SemanticDocument): Patch = {
    var patches = List.empty[Patch]
    def tokensBetween(from: Int, to: Int) = doc.tree.tokens.filter(tk => tk.start >= from && tk.end <= to && tk.end > tk.start).toList
    def removeRange(from: Int, to: Int): Patch = Patch.fromIterable(tokensBetween(from, to).map(Patch.removeToken))
    var needsImport = false
    val wanted = scala.collection.mutable.Set.empty[String]
    // the converted methods of this file
    doc.tree.traverse {
      case d: Defn.Def if converted.contains(d.name.symbol.value) =>
        val m = converted(d.name.symbol.value)
        d.decltpe.flatMap(wrapperIn).foreach { case (_, w) => patches ::= Patch.replaceTree(w, "Problems"); needsImport = true }
        val handled = scala.collection.mutable.Set.empty[Tree]
        d.body.traverse {
          case a: Term.Apply if constructs(a).nonEmpty && !handled(a) =>
            adapter(a) match {
              case Some((outer, recv)) =>
                patches ::= removeRange(recv.pos.end, outer.pos.end); handled += outer
              case None =>
                val first = a.argClause.values.head
                patches ::= (removeRange(a.pos.start, first.pos.start) + removeRange(first.pos.end, a.pos.end))
            }
        }
        val _ = m
    }
    // the calls of converted methods, outside them: the wrapper rebuilt
    doc.tree.traverse {
      case n: Term.Name if converted.contains(n.symbol.value) && !n.parent.exists { case d: Defn.Def => d.name eq n; case _ => false } =>
        val m = converted(n.symbol.value)
        val call = callOf(n)
        val insideItself = ancestors(n).exists { case d: Defn.Def => d.name.symbol.value == m.sym; case _ => false }
        if (!insideItself) {
          val w = written(m.wrapper)
          val extra = if (m.extra.isEmpty) "" else s", ${m.extra}"
          patches ::= Patch.addRight(call, s".left.map($w(_$extra))")
          wanted ++= m.needs
        }
    }
    // the names the adapters are written with, imported where the file does not see them yet
    val seen = doc.tree.collect { case n: Term.Name if !n.symbol.isNone => s"${n.value}=${n.symbol.value}" }.toSet
    val imports = wanted.toList.sorted.filterNot(n => seen(n) || n.split("=", 2)(1).matches("(scala|java/lang)/.*")).map { n =>
      val path = n.split("=", 2)(1).replaceAll("[A-Za-z0-9_]+\\$package\\.", "").stripSuffix(".").replace('/', '.').split('.').toList
      Patch.addGlobalImport(Importer(path.dropRight(1).map(Term.Name(_)).reduceLeft[Term.Ref]((q, nm) => Term.Select(q, nm.asInstanceOf[Term.Name])),
        List(Importee.Name(Name(path.last)))))
    }
    val hasProblems = doc.tree.collect { case n: Type.Name if n.value == "Problems" && symbolOf(n).value == config.problemsType => n }.nonEmpty ||
      doc.tree.collect { case i: Importer if i.importees.exists { case Importee.Name(nm) => nm.value == "Problems"; case _ => false } => i }.nonEmpty
    val importPatch = if (needsImport && !hasProblems) Patch.addGlobalImport(Importer(
      config.problemsImport.split('.').dropRight(1).toList.map(Term.Name(_)).reduceLeft[Term.Ref]((q, n) => Term.Select(q, n.asInstanceOf[Term.Name])),
      List(Importee.Name(Name(config.problemsImport.split('.').last))))) else Patch.empty
    Patch.fromIterable(patches) + importPatch + Patch.fromIterable(imports)
  }
}
