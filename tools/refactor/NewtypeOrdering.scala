import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.{semanticdb => s}

/** Step 3a (G52/G53, after NewtypeBase): the shapes the base trait's lifted `Ordering`/`CanEqual` make unnecessary, only
  * where SemanticDB shows the element or the operands to be one project newtype (a companion extending
  * `com.histmersion.Newtype`):
  *
  *  - `xs.sortBy(_.value)` and `xs.sortBy(x => x.value)` become `xs.sorted`;
  *  - `xs.minBy(_.value)` / `xs.maxBy(_.value)` (either lambda form) become `xs.min` / `xs.max`;
  *  - `xs.sortBy(_.value)(Ordering[A].reverse)` (also `using`, also `Ordering.String.reverse` & co.), where `A` is the
  *    newtype's base, becomes `xs.sortWith(_ > _)`: the same comparator signs, so the same stable order;
  *  - `a.value < b.value` (`<=`, `>`, `>=`) with `a` and `b` of the same newtype becomes `a < b` (the base trait's
  *    comparisons), except over a Double/Float base, where `<` on the primitive and the total `Ordering` disagree
  *    on NaN and -0.0;
  *  - `a.value == b.value` / `!=` with `a` and `b` of the same newtype becomes `a == b` (an opaque type erases to its
  *    base, so the comparison at run time is the same);
  *  - a companion's own `given Ordering[T] = Ordering.by[T, A](_.value)` (or `Ordering.by(_.value)`,
  *    `Ordering.String.on(_.value)`, `scala.math.` spelled out) is the lifted one again, so it goes.
  *
  * Left alone and counted (in `report`): an unresolved receiver or operand type, two different newtypes, a newtype whose
  * companion is not a `Newtype`, comparisons inside the newtype's own companion (they define its operations) or inside
  * an `assert*` call (a test's assertion keeps its text), other lambdas (`_.value.toLowerCase`), an ordering argument
  * that is not the base's, and the other collection operations. */
object NewtypeOrdering {
  case class Config(
    report: String = "",
    roots: List[String] = List("."),
    exclude: List[String] = Nil,
    // `{scalaVersion}` is Scalafix's --scala-version: the build's (target/build-versions.properties)
    semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}", ".semanticdb"),
    base: String = "com/histmersion/Newtype#"
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  val iron = "io/github/iltotore/iron/"
  val ordered = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Short#", "scala/Byte#", "scala/Char#",
    "scala/Boolean#", "scala/math/BigDecimal#", "scala/math/BigInt#", "scala/package.BigDecimal#", "scala/package.BigInt#")
  val floating = Set("scala/Double#", "scala/Float#")
  /** `Ordering.String` & co.: the instance `Ordering[A]` summons for these bases. */
  val namedOrderings = Map("java/lang/String#" -> "String", "scala/Predef.String#" -> "String", "scala/Int#" -> "Int", "scala/Long#" -> "Long",
    "scala/Short#" -> "Short", "scala/Byte#" -> "Byte", "scala/Char#" -> "Char", "scala/Boolean#" -> "Boolean")
  val collectionOwners = List("scala/collection/", "scala/Array#", "scala/IArray", "scala/Predef.", "scala/package.")
  private val indexes = new ConcurrentHashMap[List[String], ConcurrentHashMap[String, s.SymbolInformation]]()
  def index(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
  /** The companion's own `given Ordering[T]` is the lifted one again: `Ordering.by[T, A](_.value)`, `Ordering.by(_.value)`,
    * `Ordering.String.on(_.value)` (syntactically; the base type is checked where the symbols are known). */
  def byValueBody(body: Term): Boolean = body match {
    case Term.Apply.After_4_6_0(f, Term.ArgClause(List(lam), _)) if isValueLambda(lam) => f match {
      case Term.ApplyType.After_4_6_0(Term.Select(o, Term.Name("by")), Type.ArgClause(List(_, _))) => o.syntax.stripPrefix("scala.math.") == "Ordering"
      case Term.Select(o, Term.Name("by")) => o.syntax.stripPrefix("scala.math.") == "Ordering"
      case Term.Select(Term.Select(o, Term.Name(_)), Term.Name("on")) => o.syntax.stripPrefix("scala.math.") == "Ordering"
      case _ => false
    }
    case _ => false
  }
  def isValueLambda(t: Term): Boolean = t match {
    case f: Term.AnonymousFunction => f.body match { case Term.Select(_: Term.Placeholder, Term.Name("value")) => true; case _ => false }
    case f: Term.Function if f.paramClause.values.size == 1 => f.body match {
      case Term.Select(q: Term.Name, Term.Name("value")) => q.value == f.paramClause.values.head.name.value
      case _ => false
    }
    case _ => false
  }
  def orderingOfOwn(g: Defn.GivenAlias, name: String): Boolean = g.decltpe match {
    case Type.Apply.After_4_6_0(o, Type.ArgClause(List(t))) => o.syntax.stripPrefix("scala.math.") == "Ordering" && (t.syntax == "T" || t.syntax == name)
    case _ => false
  }
  def orderingDecl(g: Defn.GivenAlias): Boolean = g.decltpe match {
    case Type.Apply.After_4_6_0(o, Type.ArgClause(List(_))) => o.syntax.stripPrefix("scala.math.") == "Ordering"
    case _ => false
  }
  /** Companions (file, object name) whose own Ordering is NOT the lifted one: their sorting by value is not `.sorted`.
    * Found syntactically over `roots` before the run, so that a use in another file knows of them too. */
  @volatile var customOrderings: Set[(String, String)] = Set.empty
  def scanCustom(roots: List[String], exclude: List[String]): Set[(String, String)] = roots.flatMap { root =>
    val st = Files.walk(Paths.get(root))
    val files = try st.iterator.asScala.filter(p => p.toString.endsWith(".scala") && !p.toString.contains("/target/") && !p.toString.split('/').exists(c => c.length > 1 && c.startsWith(".") && c != "..")).toList finally st.close()
    files.filterNot(f => exclude.exists(r => r.r.findFirstIn(f.toString).nonEmpty)).flatMap { f =>
      val text = new String(Files.readAllBytes(f), UTF_8)
      dialects.Scala3(text).parse[Source].toOption.toList.flatMap(_.collect {
        case o: Defn.Object if o.templ.inits.exists(_.tpe.syntax.startsWith("Newtype[")) &&
            o.templ.body.stats.exists { case g: Defn.GivenAlias => orderingOfOwn(g, o.name.value) && !byValueBody(g.body); case _ => false } =>
          (Paths.get(f.toString).toAbsolutePath.normalize.toString, o.name.value)
      })
    }
  }.toSet
  /** Symbol information of every current SemanticDB document under `roots` (as GuidelineInventory builds it). */
  /** The document (source path) each global symbol is defined in. */
  val uris = new ConcurrentHashMap[String, String]()
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
          if (!i.symbol.startsWith("local")) uris.putIfAbsent(i.symbol, d.uri)
        }
      }
    }
    index
  }
}

class NewtypeOrdering(config: NewtypeOrdering.Config) extends SemanticRule("NewtypeOrdering") {
  import NewtypeOrdering._
  def this() = this(NewtypeOrdering.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("NewtypeOrdering")(Config()).map(cfg =>
      new NewtypeOrdering(cfg.copy(semanticdbRoots = cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion)))))
  override def beforeStart(): Unit = customOrderings = scanCustom(config.roots, config.exclude)

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  private def write(rows: List[String]): Unit = if (config.report.nonEmpty && rows.nonEmpty) this.synchronized {
    Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
    Files.write(Paths.get(config.report), rows.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    if (config.exclude.exists(r => r.r.findFirstIn(path).nonEmpty)) return Patch.empty
    val rel = { val here = Paths.get("").toAbsolutePath.normalize; val abs = Paths.get(path).toAbsolutePath.normalize; if (abs.startsWith(here)) here.relativize(abs).toString else path }
    val idx = index(config.semanticdbRoots)
    val uris = NewtypeOrdering.uris
    if (sys.env.contains("NEWTYPE_ORDERING_DEBUG")) System.err.println(s"index ${idx.size} uris ${uris.size} cwd ${Paths.get("").toAbsolutePath} customs $customOrderings")
    val rows = List.newBuilder[String]
    def row(action: String, shape: String, t: Tree, detail: String): Unit = {
      if (sys.env.contains("NEWTYPE_ORDERING_DEBUG")) System.err.println(s"ROW $action $shape ${t.pos.startLine + 1} $detail")
      rows += List(action, shape, rel, (t.pos.startLine + 1).toString, detail.replace("\t", " ").replace("\n", " ").take(160)).mkString("\t")
    }

    // ---- types, from the project-wide SemanticDB index -------------------------------------------------------------
    def infoS(sym: String): Option[s.SymbolInformation] =
      if (sym.isEmpty) None else Option(idx.get(if (sym.startsWith("local")) rel + "::" + sym else sym))
    def info(sym: Symbol): Option[s.SymbolInformation] = if (sym.isNone) None else infoS(sym.value)
    val objectKinds: Set[s.SymbolInformation.Kind] = Set(s.SymbolInformation.Kind.OBJECT, s.SymbolInformation.Kind.PACKAGE_OBJECT, s.SymbolInformation.Kind.PACKAGE)
    def isObject(sym: Symbol): Boolean = info(sym).exists(i => objectKinds(i.kind))
    def dealias(t: s.Type, depth: Int = 0): s.Type = if (depth > 12) t else t match {
      case a: s.AnnotatedType => dealias(a.tpe, depth + 1)
      case b: s.ByNameType => dealias(b.tpe, depth + 1)
      case r: s.RepeatedType => dealias(r.tpe, depth + 1)
      case r: s.TypeRef if !r.symbol.startsWith(iron) => infoS(r.symbol).map(_.signature) match {
        case Some(ts: s.TypeSignature) if ts.lowerBound == ts.upperBound && ts.upperBound != s.Type.Empty &&
            ts.typeParameters.forall(sc => sc.symlinks.isEmpty && sc.hardlinks.isEmpty) => dealias(ts.upperBound, depth + 1)
        case _ => t
      }
      case _ => t
    }
    def typeSymbol(t: s.Type): String = t match {
      case r: s.TypeRef => r.symbol
      case st: s.SingleType => st.symbol
      case _ => ""
    }
    /** (extends the base trait, base type symbol) of a newtype companion. */
    def newtypeParent(obj: String): Option[(Boolean, String)] = infoS(obj).map(_.signature).collect {
      case cs: s.ClassSignature =>
        val mine = cs.parents.exists { case r: s.TypeRef => r.symbol == config.base; case _ => false }
        cs.parents.collectFirst {
          case r: s.TypeRef if (r.symbol == config.base || r.symbol == iron + "RefinedType#" || r.symbol == iron + "RefinedSubtype#") && r.typeArguments.size == 2 =>
            (mine, typeSymbol(dealias(r.typeArguments(0))))
        }
    }.flatten
    /** The companion has an Ordering of its own that is not the lifted one. */
    def custom(obj: String): Boolean = Option(uris.get(obj)).exists(u => customOrderings.contains((Paths.get(u).toAbsolutePath.normalize.toString, Symbol(obj).displayName)))
    def newtypeOf(t: s.Type): Option[String] = dealias(t) match {
      case r: s.TypeRef if r.symbol.startsWith(iron) && r.symbol.endsWith("#T#") => r.prefix match {
        case st: s.SingleType if newtypeParent(st.symbol).nonEmpty => Some(st.symbol)
        case th: s.ThisType if newtypeParent(th.symbol).nonEmpty => Some(th.symbol)
        case _ => None
      }
      case _ => None
    }
    def resultOf(sym: Symbol): Option[s.Type] = info(sym).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature => m.returnType
    }
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(iron)
    def termType(t: Term): Option[s.Type] = t match {
      case n: Term.Name => if (isObject(n.symbol)) None else resultOf(n.symbol)
      case sel: Term.Select => if (isObject(sel.name.symbol)) None else resultOf(sel.name.symbol)
      case a: Term.Apply => a.fun match {
        case n: Term.Name if newtypeParent(n.symbol.value).nonEmpty => None
        case n: Term.Name => resultOf(n.symbol)
        case sel: Term.Select => resultOf(sel.name.symbol)
        case _ => None
      }
      case b: Term.Block if b.stats.size == 1 => b.stats.head match { case x: Term => termType(x); case _ => None }
      case p: Term.Placeholder => placeholderType(p)
      case _ => None
    }
    /** `xs.op(_.f)`: the placeholder has the element type of `xs`. */
    def placeholderType(p: Term.Placeholder): Option[s.Type] =
      ancestors(p).collectFirst { case f: Term.AnonymousFunction => f }.flatMap(f => f.parent.flatMap(_.parent)).collect {
        case a: Term.Apply => a.fun
      }.collect {
        // one placeholder (the element), or the two of sortWith (both elements)
        case Term.Select(recv, op) if op.value == "sortWith" || ancestors(p).collectFirst { case f: Term.AnonymousFunction => f }.exists(_.collect { case x: Term.Placeholder => x }.size == 1) => recv
      }.flatMap(r => elementOf(r))
    /** The element type of a collection-typed term (first type argument of a one-argument constructor). */
    def elementOf(recv: Term): Option[s.Type] = termType(recv).map(dealias(_)).collect {
      case r: s.TypeRef if r.typeArguments.size == 1 => r.typeArguments.head
    }

    // ---- enclosing definitions ------------------------------------------------------------------------------------
    def enclosingObjects(t: Tree): List[String] = ancestors(t).collect { case o: Defn.Object => o.name.symbol.value }
    def inAssert(t: Tree): Boolean = ancestors(t).exists {
      case a: Term.Apply => a.fun match {
        case n: Term.Name => n.value.startsWith("assert")
        case Term.Select(_, n) => n.value.startsWith("assert")
        case Term.ApplyType(n: Term.Name, _) => n.value.startsWith("assert")
        case _ => false
      }
      case _ => false
    }

    /** `_.value` or `x => x.value`: the projection onto the raw value. Some(param symbol) for the named form. */
    def valueLambda(t: Term): Option[Option[Symbol]] = t match {
      case f: Term.AnonymousFunction => f.body match {
        case Term.Select(_: Term.Placeholder, n) if isIronValue(n) => Some(None)
        case _ => None
      }
      case f: Term.Function if f.paramClause.values.size == 1 => (f.paramClause.values.head, f.body) match {
        case (p, Term.Select(q: Term.Name, n)) if isIronValue(n) && q.value == p.name.value && p.decltpe.isEmpty => Some(Some(p.name.symbol))
        case _ => None
      }
      case Term.Block(List(inner: Term)) => valueLambda(inner)
      case _ => None
    }
    def collectionOp(sel: Term.Select): Boolean = collectionOwners.exists(sel.name.symbol.value.startsWith)

    /** The newtype a `_.value` / `x => x.value` lambda passed to `recv` projects; Left(reason) when it cannot be shown. */
    def lambdaNewtype(recv: Term, param: Option[Symbol]): Either[String, String] = {
      val t = param.flatMap(p => resultOf(p)).orElse(elementOf(recv))
      t.flatMap(newtypeOf) match {
        case None if t.nonEmpty => Left("element is not a newtype")
        // `.value` is Iron's, so the element is a newtype; with no companion ordering other than the lifted one anywhere,
        // `.sorted` sorts as `sortBy(_.value)` did, and the compiler checks that the companion is a Newtype
        case None => if (customOrderings.isEmpty) Right("?") else Left("element type unresolved, and custom orderings exist")
        case Some(nt) =>
          if (!newtypeParent(nt).exists(_._1)) Left("companion is not a Newtype: " + nt)
          else if (custom(nt)) Left("companion has an ordering of its own: " + nt)
          else Right(nt)
      }
    }

    val patches = List.newBuilder[Patch]
    /** The object a definition is a direct member of. */
    def ownerObject(d: Stat): Option[Defn.Object] =
      ancestors(d).collectFirst { case t: Template => t }.flatMap(_.parent).collect { case o: Defn.Object if o.templ.body.stats.exists(_ eq d) => o }
    // sortBy/minBy/maxBy: `recv.op(lambda)` and `recv.sortBy(lambda)(ordering)`
    def projection(app: Term.Apply): Option[(Term.Select, Term, Option[Symbol])] = app.fun match {
      case sel @ Term.Select(_, op) if Set("sortBy", "minBy", "maxBy")(op.value) && collectionOp(sel) && app.argClause.values.size == 1 =>
        valueLambda(app.argClause.values.head).map(p => (sel, app.argClause.values.head, p))
      case _ => None
    }
    doc.tree.traverse {
      case outer: Term.Apply if (outer.fun match { case inner: Term.Apply => projection(inner).exists(_._1.name.value == "sortBy"); case _ => false }) =>
        val inner = outer.fun.asInstanceOf[Term.Apply]
        val (sel, lam, param) = projection(inner).get
        lambdaNewtype(sel.qual, param) match {
          case Left(why) => row("left", "sortBy-value-ordering", outer, why + ": " + outer.syntax)
          case Right("?") => row("left", "sortBy-value-ordering", outer, "element type unresolved: " + outer.syntax)
          case Right(nt) =>
            val base = newtypeParent(nt).get._2
            val arg = outer.argClause.values match { case List(a) => Some(a); case _ => None }
            val reversedBase = arg.exists {
              case Term.Select(Term.ApplyType.After_4_6_0(Term.Name("Ordering"), Type.ArgClause(List(tp))), Term.Name("reverse")) =>
                tp match { case n: Type.Name => n.symbol.value == base || (n.symbol.value == "scala/Predef.String#" && base == "java/lang/String#") || (n.symbol.value == "java/lang/String#" && base == "scala/Predef.String#"); case _ => false }
              case Term.Select(Term.Select(Term.Name("Ordering"), Term.Name(n)), Term.Name("reverse")) => namedOrderings.get(base).contains(n)
              case _ => false
            }
            if (reversedBase && !floating(base)) {
              patches += Patch.replaceTree(outer, sel.qual.syntax + ".sortWith(_ > _)").atomic
              row("rewrite", "sortBy-value-reverse", outer, nt)
            } else row("left", "sortBy-value-ordering", outer, s"ordering argument is not the base's reverse ($base): " + outer.syntax)
        }
      case app: Term.Apply if projection(app).nonEmpty && !(app.parent match { case Some(p: Term.Apply) => p.fun eq app; case _ => false }) =>
        val (sel, lam, param) = projection(app).get
        lambdaNewtype(sel.qual, param) match {
          case Left(why) => row("left", sel.name.value + "-value", app, why + ": " + app.syntax.take(120))
          case Right(nt) =>
            val to = sel.name.value match { case "sortBy" => "sorted"; case "minBy" => "min"; case _ => "max" }
            // `.op` + `(lambda)` → `.to`: replace the name, drop the argument clause
            patches += (Patch.replaceTree(sel.name, to) + Patch.removeTokens(app.argClause.tokens)).atomic
            row("rewrite", sel.name.value + "-value" + (if (nt == "?") "-unresolved" else ""), app, if (nt == "?") app.syntax.take(120) else nt)
        }
      case i: Term.ApplyInfix if Set("<", "<=", ">", ">=", "==", "!=")(i.op.value) && i.argClause.values.size == 1 =>
        (i.lhs, i.argClause.values.head) match {
          case (l @ Term.Select(a, ln), r @ Term.Select(b, rn)) if isIronValue(ln) && isIronValue(rn) =>
            val eq = i.op.value == "==" || i.op.value == "!="
            val shape = if (eq) "value-equality" else "value-comparison"
            val (ta, tb) = (termType(a).flatMap(newtypeOf), termType(b).flatMap(newtypeOf))
            (ta, tb) match {
              case (Some(x), Some(y)) if x != y => row("left", shape, i, s"two newtypes $x / $y: " + i.syntax)
              case (Some(x), Some(_)) =>
                val (mine, base) = newtypeParent(x).get
                if (!mine) row("left", shape, i, "companion is not a Newtype: " + x)
                else if (enclosingObjects(i).contains(x)) row("left", shape, i, "inside the newtype's own companion: " + i.syntax)
                else if (inAssert(i)) row("left", shape, i, "inside an assertion: " + i.syntax)
                else if (floating(base)) row("left", shape, i, "Double/Float base: " + i.syntax)
                else if (!eq && !ordered(base)) row("left", shape, i, s"base without a known ordering ($base): " + i.syntax)
                else {
                  // drop `.value` on both sides: the tokens from the end of the receiver to the end of the select
                  // the `.` before `value` and `value` itself; a parenthesised receiver keeps its parentheses
                  def drop(sel: Term.Select, q: Term): Patch = {
                    val dot = sel.tokens.filter(t => t.is[Token.Dot] && t.end <= sel.name.pos.start).lastOption
                    Patch.removeTokens(sel.tokens.filter(t => dot.exists(d => t.start >= d.start) && t.end <= sel.name.pos.end))
                  }
                  patches += (drop(l, a) + drop(r, b)).atomic
                  row("rewrite", shape, i, x)
                }
              case _ => row("left", shape, i, "operand type unresolved: " + i.syntax)
            }
          case _ =>
        }
      // a companion's own Ordering that is the lifted one again
      case g: Defn.GivenAlias if ownerObject(g).exists(o => newtypeParent(o.name.symbol.value).exists(_._1)) =>
        val obj = ownerObject(g).get
        val base = newtypeParent(obj.name.symbol.value).get._2
        val isOrderingOfT = g.decltpe match {
          case Type.Apply.After_4_6_0(o, Type.ArgClause(List(t))) =>
            o.syntax.stripPrefix("scala.math.") == "Ordering" && (t match {
              case n: Type.Name => n.value == "T" || newtypeOf(s.TypeRef(s.Type.Empty, n.symbol.value, Nil)).contains(obj.name.symbol.value)
              case _ => false
            })
          case _ => false
        }
        val byValue = byValueBody(g.body) && valueLambda(g.body.asInstanceOf[Term.Apply].argClause.values.head).nonEmpty && (g.body match {
          case Term.Apply.After_4_6_0(Term.ApplyType.After_4_6_0(_, Type.ArgClause(List(_, b))), _) =>
            b match { case n: Type.Name => n.symbol.value == base || namedOrderings.get(base).contains(n.value); case _ => false }
          case Term.Apply.After_4_6_0(Term.Select(Term.Select(_, Term.Name(n)), Term.Name("on")), _) => namedOrderings.get(base).contains(n)
          case _ => true
        })
        if (isOrderingOfT && byValue) {
          val stats = obj.templ.body.stats
          val toks = doc.tree.tokens
          val lineStart = g.pos.start - g.pos.startColumn
          val end = toks.find(t => t.start >= g.pos.end && !t.is[Token.Space]).filter(_.is[Token.LF]).map(_.end).getOrElse(g.pos.end)
          val removeLine = Patch.removeTokens(toks.filter(t => t.start >= lineStart && t.end <= end))
          if (stats.size == 1) {
            // the given was the companion's only member: the `:` that opened the body goes too
            val colon = toks.filter(t => t.start >= obj.templ.inits.last.pos.end && t.end <= g.pos.start).find(_.is[Token.Colon])
            if (colon.nonEmpty) {
              // from the `:` to the end of the given, keeping the line break after it
              patches += Patch.removeTokens(toks.filter(t => t.start >= colon.get.start && t.end <= g.pos.end)).atomic
              row("rewrite", "own-ordering", g, obj.name.symbol.value)
            } else row("left", "own-ordering", g, "only member, no `:` body: " + g.syntax)
          } else {
            patches += removeLine.atomic
            row("rewrite", "own-ordering", g, obj.name.symbol.value)
          }
        } else if (isOrderingOfT) row("left", "own-ordering", g, "not the lifted ordering: " + g.syntax)
    }
    // other uses of `.value` projections in sorting/min/max that the shapes above do not cover, for the count
    doc.tree.traverse {
      case app: Term.Apply => app.fun match {
        case sel @ Term.Select(_, op) if Set("sortBy", "minBy", "maxBy", "minByOption", "maxByOption", "sortWith", "distinctBy")(op.value) && collectionOp(sel) &&
            projection(app).isEmpty && app.argClause.values.exists(_.collect { case n: Name if isIronValue(n) => n }.nonEmpty) =>
          row("left", "other-" + op.value, app, app.syntax.take(120))
        case _ =>
      }
    }
    write(rows.result())
    patches.result().asPatch
  }
}
