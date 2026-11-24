package com.histmersion.linting

import metaconfig.Configured
import scalafix.v1._
import scala.meta._
import java.nio.file.Paths
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.{semanticdb => s}

object Guidelines {
  val Owned: Set[String] = Set("G3Misplaced", "G9Prefix", "G9PrefixRole", "G44", "G44Conversion", "G44PlainDef",
    "G45Boundary", "G46", "G47Root", "G47Fqn", "G47Self", "G47Package", "G48", "G5Problem", "G5ProblemText",
    "G5TextBranch", "G55Positional", "G58Raw", "G60FromString", "G60External", "G60Unrecognised", "G89Unguarded",
    "DiscardedOutcome", "BooleanParam", "UnnamedBoolean", "SilentCompare", "SilentInterp", "SilentPure")

  val projectPrefix = "com/histmersion/"
  val testMarkers: List[String] = List("/src/test/", "/src/test-fixture/")
  val textBranchTypes: String = "(?i).*(problem|error|reason|refus|fail|why|because|message|detail|note|outcome|warning|stopped|state)(s|text)?"
  val textBranchFields: Set[String] = Set("message", "error", "detail", "note", "why", "reason")
  val stringProblems: List[String] = List("com/histmersion/Problem.", "com/histmersion/api/Problem.", "com/histmersion/domain/Problem.",
    "com/histmersion/plugin/Problem.", "com/histmersion/pluginresolver/Problem.")
  val unclassifiedProblem: String = "com/histmersion/util/problem/Problem.Unclassified."
  val legacyTextAccessor: String = "com/histmersion/util/problem/Problem.legacyText("
  val unrecognisedProblem: String = "com/histmersion/util/problem/Problem.Stored.Unrecognised."
  val externalText: String = "com/histmersion/util/problem/External."
  val closedProblem: String = "com/histmersion/util/problem/Problem"
  val controllerFile: String = ".*(Controller|Controllers|Routes)\\.scala"
  val rawSqlOwners: List[String] = List("com/histmersion/util/database/Sql#", "com/histmersion/util/database/Sql.")
  val rawSqlText: List[String] = List("com/histmersion/util/database/Sql.Text.")
  val rawSqlLiterals: List[String] = List("com/augustnagro/magnum/SqlLiteral.", "com/augustnagro/magnum/SqlLiteral.apply().")
  val unguardedTransactions: List[String] = List("com/histmersion/util/database/Transactor#transact().",
    "com/histmersion/util/database/Transactor#autocommit().", "com/histmersion/util/database/Transactor.Session#transact().",
    "com/histmersion/util/database/Transactor.Session#autocommit().", "com/histmersion/util/database/WritePermit.standing().")
  val unusedAnswer: String = "com/histmersion/util/database/Unused."
  val roleSuffixes: List[String] = List("Suite", "Spec", "Test", "Tests", "Service", "Repo", "Repository", "Controllers", "Controller",
    "Routes", "Row", "Rows", "Markdown", "Render", "Renderer", "Codec", "Codecs", "Cmd", "View", "Views", "Page", "Api", "Client",
    "Store", "Config", "Json", "Schema", "Fixtures", "Fixture", "Support", "Impl", "Ops", "Syntax", "Rule")
  val testSuiteSuffixes: List[String] = List("Suite", "Spec", "Test", "Tests")
  val collectionOwners: List[String] = List("scala/collection/", "scala/Option#", "scala/Some#", "scala/Array#", "scala/package.", "scala/Predef.", "scala/IArray")
  val collectionOps: List[String] = List("find", "filter", "filterNot", "exists", "forall", "sortBy", "sortWith", "map", "flatMap", "contains",
    "count", "collect", "collectFirst", "groupBy", "groupMap", "partition", "distinctBy", "maxBy", "minBy", "maxByOption", "minByOption",
    "indexWhere", "takeWhile", "dropWhile", "foreach", "foldLeft", "foldRight", "toMap", "span", "segmentLength", "lastIndexWhere", "findLast",
    "sumBy", "mapFilter", "flatten", "sorted", "zip", "indexOf", "diff", "intersect", "startsWith", "endsWith", "keyBy", "filterKeys", "mapValues")
  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
  val primitives = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Double#", "scala/Boolean#",
    "scala/Short#", "scala/Byte#", "scala/Char#", "scala/Float#")
  val plainDefTypes = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Double#", "scala/Boolean#")
  val pure = "io/github/iltotore/iron/constraint/any.Pure#"

  def boundaryOfTree(tree: Tree): Option[String] =
    Tolerance.topLevelDefinitions(tree).iterator.flatMap(d => Tolerance.modsOf(d)).collectFirst {
      case a: Mod.Annot if Tolerance.annotationName(a) == "atBoundary" =>
        Tolerance.arguments(a).headOption.collect { case Term.Select(_, Term.Name(k)) => Project.boundaryWord(k) }
    }.flatten

  def findings(doc: SemanticDocument, scalaVersion: String): List[Finding] =
    new Guidelines(Project.semantic(scalaVersion)).check(using doc)

  private val overriderCache = new ConcurrentHashMap[AnyRef, java.util.Map[String, List[String]]]()

  def overriders(index: ConcurrentHashMap[String, s.SymbolInformation]): java.util.Map[String, List[String]] =
    overriderCache.computeIfAbsent(index, _ => {
      val out = new java.util.HashMap[String, List[String]]()
      index.values.asScala.foreach(i => i.overriddenSymbols.foreach(o => out.merge(o, List(i.symbol), (a, b) => a ++ b)))
      out
    })
}

final class Guidelines(project: Project.Semantic) {
  import Guidelines._
  private val index: ConcurrentHashMap[String, s.SymbolInformation] = project.symbols

  private lazy val overriders: java.util.Map[String, List[String]] = Guidelines.overriders(index)

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  private def unblock(t: Term): Term = t match {
    case b: Term.Block if b.stats.size == 1 => b.stats.head match { case only: Term => only; case _ => t }
    case other => other
  }

  def check(using doc: SemanticDocument): List[Finding] = {
    val path = Project.relative(doc)
    val found = List.newBuilder[Finding]
    if (Project.staleSemanticdb(doc))
      return List(Finding("SemanticDB", doc.tree.pos, "stale SemanticDB: compile this module (sbt compile Test/compile)"))
    val ref = Project.ref(path)
    val mod = ref.fold("fixture")(_.module)
    val scope = if (testMarkers.exists(path.contains)) "test" else "main"
    val boundaryKind = boundaryOfTree(doc.tree)
    val boundary = boundaryKind.nonEmpty
    val isController = controllerFile.r.pattern.matcher(Paths.get(path).getFileName.toString).matches
    def report(rule: String, t: Tree, detail: String): Unit = found += Finding(rule, t.pos, detail)

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
        case r: s.TypeRef if (r.symbol == ironValue + "RefinedType#" || r.symbol == ironValue + "RefinedSubtype#" || r.symbol == newtypeBase) && r.typeArguments.size == 2 =>
          (if (r.symbol.endsWith("RefinedSubtype#")) "RefinedSubtype" else "RefinedType", typeSymbol(dealias(r.typeArguments(0))), typeSymbol(dealias(r.typeArguments(1))))
      }
    }.flatten
    def isNewtypeObject(sym: Symbol): Boolean = !sym.isNone && sym.value.endsWith(".") && newtypeParents(sym).nonEmpty
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
      case Term.Select(_, n) => Set("parse", "fromWire", "fromString", "either", "option", "transformIntoPartial")(n.value) && n.symbol.value.startsWith(projectPrefix)
      case _ => false
    })
    def isLiteral(t: Term): Boolean = t match {
      case _: Lit.Null | _: Lit.Unit => false
      case _: Lit => true
      case u: Term.ApplyUnary => u.op.value == "-" && (u.arg match { case _: Lit.Int | _: Lit.Long | _: Lit.Double | _: Lit.Float => true; case _ => false })
      case _ => false
    }
    def isInterpolatedString(t: Term): Boolean = t match {
      case i: Term.Interpolate => i.prefix.value == "s" || i.prefix.value == "f"
      case _ => false
    }
    def isLiteralInterpolation(t: Term): Boolean = isInterpolatedString(t) && (t match {
      case i: Term.Interpolate => i.args.forall {
        case Term.Block(List(only: Term)) => isLiteral(only)
        case arg => isLiteral(arg)
      }
      case _ => false
    })
    def constantValue(sym: Symbol): Boolean = info(sym).map(_.signature).exists {
      case v: s.ValueSignature => v.tpe.isInstanceOf[s.ConstantType]
      case m: s.MethodSignature => m.parameterLists.isEmpty && m.returnType.isInstanceOf[s.ConstantType]
      case _ => false
    }
    def ironConstant(t: Term): Boolean = isLiteral(t) || isLiteralInterpolation(t) || (t match {
      case i: Term.ApplyInfix => i.op.value == "+" && i.argClause.values.size == 1 && ironConstant(i.lhs) && ironConstant(i.argClause.values.head)
      case Term.Block(List(only: Term)) => ironConstant(only)
      case n: Term.Name => constantValue(n.symbol)
      case sel: Term.Select => constantValue(sel.name.symbol)
      case _ => false
    })
    def isBridge(t: Term): Boolean = t match {
      case s: Term.Select => isIronValue(s.name)
      case i: Term.Interpolate if isInterpolatedString(i) => i.args.exists(isBridge)
      case Term.Block(List(only: Term)) => isBridge(only)
      case _ => false
    }

    def isRawText(sym: Symbol): Boolean = !sym.isNone && (rawSqlText.contains(sym.value) || (resultOf(sym) match {
      case Some(st: s.SingleType) => rawSqlText.contains(st.symbol)
      case _ => false
    }))
    def takesText(sym: Symbol): Boolean = info(sym).map(_.signature).exists {
      case m: s.MethodSignature => m.parameterLists.flatMap(_.symlinks).exists { p =>
        infoS(p).map(_.signature).exists {
          case v: s.ValueSignature =>
            val t = dealias(v.tpe)
            typeSymbol(t).value == "java/lang/String#" || typeSymbol(t).value == "scala/Predef.String#" || newtypeOf(t).exists(o => rawSqlText.contains(o.value))
          case _ => false
        }
      }
      case _ => false
    }
    def rawSqlOperator(sym: Symbol): Boolean = !sym.isNone && rawSqlOwners.contains(sym.owner.value) && takesText(sym)
    def rawSqlOf(fun: Term): Option[String] = fun match {
      case n: Term.Name if isRawText(n.symbol) => Some("text:" + n.value)
      case n: Term.Name if rawSqlLiterals.contains(n.symbol.value) => Some("literal:" + n.value)
      case Term.Select(_, n) if rawSqlLiterals.contains(n.symbol.value) => Some("literal:" + n.value)
      case n: Term.Name if rawSqlOperator(n.symbol) => Some("operator:" + n.value)
      case Term.Select(q, n) if isRawText(n.symbol) => Some("text:" + n.value)
      case Term.Select(q, n) if constructNames(n.value) && (q match { case qn: Term.Name => isRawText(qn.symbol); case qs: Term.Select => isRawText(qs.name.symbol); case _ => false }) =>
        Some("text:" + (q match { case qn: Term.Name => qn.value; case qs: Term.Select => qs.name.value; case _ => "" }) + "." + n.value)
      case Term.Select(_, n) if rawSqlOperator(n.symbol) => Some("operator:" + n.value)
      case Term.ApplyType(f, _) => rawSqlOf(f)
      case _ => None
    }

    val textBranchOps = Set("contains", "startsWith", "endsWith", "matches")
    val textParseOps = Set("takeWhile", "dropWhile", "split", "stripPrefix", "stripSuffix", "indexOf", "lastIndexOf", "substring", "splitAt", "span")
    def allowedInCompanion(t: Tree, nt: Option[Symbol]): Boolean = {
      val enclosing = enclosingNewtypes(t)
      enclosing.nonEmpty && nt.forall(enclosing.contains)
    }

    doc.tree.traverse {
      case s @ Term.Select(q, n) if isIronValue(n) && !inImport(s) =>
        val nt = termType(q).flatMap(newtypeOf)
        val ntName = nt.fold("?")(_.displayName)
        val lambdaOp = ancestors(s).takeWhile(a => !a.is[Defn.Def] && !a.is[Template]).filterNot {
          case b: Term.Block => b.stats.size == 1
          case _ => false
        }.sliding(3).collectFirst {
          case List(f: Term.FunctionTerm, _: Term.ArgClause, a: Term.Apply) => a
          case List(f: Term.AnonymousFunction, _: Term.ArgClause, a: Term.Apply) => a
          case List(f: Term.PartialFunction, _: Term.ArgClause, a: Term.Apply) => a
        }.flatMap(a => a.fun match {
          case Term.Select(_, op) if collectionOps.contains(op.value) && collectionOwners.exists(op.symbol.value.startsWith) => Some(op.value)
          case _ => None
        })
        val direct = s.parent.flatMap(_.parent) match {
          case Some(a: Term.Apply) if a.argClause.values.exists(_ eq s) => a.fun match {
            case Term.Select(_, op) if Set("contains", "indexOf", "startsWith", "endsWith")(op.value) && collectionOwners.exists(op.symbol.value.startsWith) => Some("arg-of-" + op.value)
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
        val g49 = (compared.map("compare:" + _) orElse lambdaOp.map("lambda:" + _) orElse direct).fold("")(d => s";G49:$d")
        if (!allowedInCompanion(s, nt)) {
          val fedInto = s.parent.flatMap(_.parent) match {
            case Some(a: Term.Apply) if a.argClause.values.exists(_ eq s) && isConversionTarget(a.fun) => Some(a.fun.syntax.take(60))
            case _ => None
          }
          if (boundary) report("G45Boundary", s, s"value:$ntName;${boundaryKind.get}$g49")
          else if (isController && fedInto.nonEmpty) report("G44Conversion", s, s"value:$ntName->${fedInto.get}$g49")
          else report("G44", s, s"value:$ntName" + fedInto.fold("")(f => s";bridge->$f") + g49)
        }
      case a: Term.Apply if constructionOf(a.fun).nonEmpty && !inImport(a) =>
        val (obj, how) = constructionOf(a.fun).get
        val args = a.argClause.values
        val bridged = args.exists(isBridge)
        val unconstrained = newtypeParents(obj).exists(_._3.value == pure)
        val literal = how == "apply" && args.nonEmpty && args.forall(arg => isLiteral(arg) || (unconstrained && isLiteralInterpolation(arg)))
        val testConstant = scope == "test" && args.nonEmpty && args.forall(ironConstant)
        if (!bridged && !literal && !testConstant && !allowedInCompanion(a, Some(obj))) {
          val spliced: List[Term] = args.toList.flatMap { case i: Term.Interpolate => i.args.map(unblock); case _ => Nil }
          val shape =
            if (how == "apply" && unconstrained && args.exists(isInterpolatedString))
              "interpolation" + (if (spliced.exists(t => termType(t).flatMap(newtypeOf).nonEmpty)) ":newtype" else "")
            else "expr"
          val detail = s"construct:${obj.displayName}.$how;$shape"
          if (boundary) ()
          else if (isController) report("G44Conversion", a, detail)
          else report("G44", a, detail)
        }
        a.fun match {
          case Term.Select(_, n) if Set("option", "either", "optionAll", "eitherAll")(n.value) && newtypeParents(obj).exists(_._3.value == pure) =>
            report("SilentPure", a, s"${obj.displayName}.${n.value}")
          case _ =>
        }
      case s @ Term.Select(q, n) if Set("option", "either", "optionAll", "eitherAll", "applyUnsafe", "assume")(n.value) && n.symbol.value.startsWith(ironValue) &&
          !s.parent.exists { case a: Term.Apply => a.fun eq s; case _ => false } && !inImport(s) =>
        val obj = q match { case qn: Term.Name => qn.symbol; case qs: Term.Select => qs.name.symbol; case _ => Symbol.None }
        if (isNewtypeObject(obj) && !allowedInCompanion(s, Some(obj))) {
          val detail = s"construct:${obj.displayName}.${n.value};function-reference"
          if (boundary) ()
          else if (isController) report("G44Conversion", s, detail)
          else report("G44", s, detail)
          if (Set("option", "either", "optionAll", "eitherAll")(n.value) && newtypeParents(obj).exists(_._3.value == pure)) report("SilentPure", s, s"${obj.displayName}.${n.value}")
        }
      case i: Term.Interpolate if Set("s", "f", "raw")(i.prefix.value) && !inImport(i) && !(i.parent.flatMap(_.parent) match {
            case Some(a: Term.Apply) => a.argClause.values.exists(_ eq i) && constructionOf(a.fun).nonEmpty
            case _ => false
          }) =>
        i.args.map(unblock).foreach { arg =>
          termType(arg).flatMap(newtypeOf).filterNot(nt => allowedInCompanion(arg, Some(nt))).foreach { nt =>
            if (boundary) report("G45Boundary", arg, s"splice:${nt.displayName};${boundaryKind.get}")
            else report("G44", arg, s"splice:${nt.displayName}")
          }
        }
    }

    def textShape(a: Term.Apply): String = a.argClause.values match {
      case List(arg) if isLiteral(arg) => "literal"
      case List(arg) if isInterpolatedString(arg) => "interpolation"
      case _ => "expression"
    }
    val filePackage = "[A-Za-z0-9_]+\\$package\\.".r
    def unclassified(fun: Term): Boolean = fun match {
      case n: Term.Name => n.symbol.value.startsWith(unclassifiedProblem)
      case Term.Select(q, n) if n.value == "apply" => unclassified(q)
      case Term.Select(_, n) => n.symbol.value.startsWith(unclassifiedProblem)
      case _ => false
    }
    doc.tree.traverse {
      case a: Term.Apply if !inImport(a) =>
        constructionOf(a.fun).map { case (obj, how) => (filePackage.replaceAllIn(obj.value, ""), how) }
          .filter { case (obj, _) => stringProblems.contains(obj) }.foreach { case (obj, how) =>
            report("G60FromString", a, s"${obj.stripPrefix("com/histmersion/").stripSuffix(".")}.$how;${textShape(a)}")
          }
        if (unclassified(a.fun)) report("G60FromString", a, s"Unclassified;${textShape(a)}")
        if (scope == "main" && (a.fun match {
              case n: Term.Name => n.symbol.value.startsWith(unrecognisedProblem)
              case Term.Select(q, n) if n.value == "apply" => q match { case qn: Term.Name => qn.symbol.value.startsWith(unrecognisedProblem); case qs: Term.Select => qs.name.symbol.value.startsWith(unrecognisedProblem); case _ => false }
              case Term.Select(_, n) => n.symbol.value.startsWith(unrecognisedProblem)
              case _ => false
            })) report("G60Unrecognised", a, "Stored.Unrecognised")
      case sel @ Term.Select(_, n) if !inImport(sel) && n.symbol.value.startsWith(legacyTextAccessor) =>
        report("G60FromString", sel, "legacyText")
      case sel @ Term.Select(q, n) if isIronValue(n) && !inImport(sel) && boundaryKind.forall(_ != "render") =>
        termType(q).flatMap(newtypeOf).filter(_.value.startsWith(externalText)).foreach { nt =>
          if (!allowedInCompanion(sel, Some(nt))) report("G60External", sel, s"value:${nt.displayName}")
        }
    }

    doc.tree.traverse {
      case a: Term.Apply if !inImport(a) => rawSqlOf(a.fun).foreach(d => report("G58Raw", a, d))
      case i: Term.ApplyInfix if rawSqlOperator(i.op.symbol) => report("G58Raw", i, "operator:" + i.op.value)
    }

    def wildcardVal(v: Defn.Val): Boolean = v.pats match { case List(_: Pat.Wildcard) => true; case _ => false }
    def skipCasesBlock(p: Option[Tree]): Option[Tree] = p.flatMap { case b: Term.CasesBlock => b.parent; case other => Some(other) }
    def calleeOf(fun: Term): Symbol = fun match {
      case n: Term.Name => n.symbol
      case Term.Select(_, n) => n.symbol
      case Term.ApplyType(f, _) => calleeOf(f)
      case a: Term.Apply => calleeOf(a.fun)
      case _ => Symbol.None
    }
    def isUnusedAnswer(fun: Term): Boolean = calleeOf(fun).value.startsWith(unusedAnswer)
    def clausesOf(t: Term): List[Term.ArgClause] = t match { case x: Term.Apply => clausesOf(x.fun) :+ x.argClause; case _ => Nil }
    def unwrapType(t: s.Type): s.Type = t match {
      case b: s.ByNameType => unwrapType(b.tpe)
      case r: s.RepeatedType => unwrapType(r.tpe)
      case a: s.AnnotatedType => unwrapType(a.tpe)
      case other => other
    }
    def runResult(t: s.Type): Option[s.Type] = unwrapType(t) match {
      case r: s.TypeRef if (r.symbol.startsWith("scala/Function") || r.symbol.startsWith("scala/ContextFunction")) && r.typeArguments.nonEmpty =>
        Some(r.typeArguments.last)
      case r: s.TypeRef if r.symbol == "java/lang/Runnable#" => lit("scala/Unit#")
      case other => Some(other)
    }
    def isUnitType(t: s.Type): Boolean = typeSymbol(unwrapType(t)).value == "scala/Unit#"
    def isTypeParameter(t: s.Type, method: Symbol): Boolean = unwrapType(t) match {
      case r: s.TypeRef => r.symbol.startsWith(method.value) && r.symbol.endsWith("]") && r.prefix == s.Type.Empty
      case _ => false
    }
    def argumentDiscarded(clause: Term.ArgClause, arg: Tree): Boolean = clause.parent match {
      case Some(a: Term.Apply) if (a.argClause eq clause) && isUnusedAnswer(a.fun) => false
      case Some(a: Term.Apply) if a.argClause eq clause =>
        val callee = calleeOf(a.fun)
        val at = clausesOf(a).indexWhere(_ eq clause)
        val index = clause.values.indexWhere(_ eq arg)
        info(callee).map(_.signature) match {
          case Some(m: s.MethodSignature) if at >= 0 && at < m.parameterLists.size && index >= 0 =>
            val params = m.parameterLists(at).symlinks
            val param = if (index < params.size) params.lift(index) else params.lastOption
            param.flatMap(infoS).map(_.signature).collect { case v: s.ValueSignature => v.tpe }.flatMap(runResult).exists { result =>
              isUnitType(result) ||
                (isTypeParameter(result, callee) && isUnitType(m.returnType)) ||
                (isTypeParameter(result, callee) && unwrapType(m.returnType) == unwrapType(result) && discarded(a))
            }
          case None => Set("foreach", "forEach")(callee.displayName)
          case _ => false
        }
      case _ => false
    }
    def asArgument(t: Tree): Tree = t.parent match {
      case Some(b: Term.Block) if b.stats.size == 1 => b
      case _ => t
    }
    def functionDiscarded(f: Tree): Boolean = {
      val arg = asArgument(f)
      arg.parent match {
        case Some(c: Term.ArgClause) => argumentDiscarded(c, arg)
        case _ => false
      }
    }
    def discarded(t: Tree): Boolean = t.parent match {
      case Some(v: Defn.Val) => (v.rhs eq t) && wildcardVal(v)
      case Some(b: Term.Block) => !b.stats.lastOption.exists(_ eq t) || discarded(b)
      case Some(_: Template.Body) | Some(_: Template) => t.is[Term]
      case Some(i: Term.If) => ((i.thenp eq t) || (i.elsep eq t)) && discarded(i)
      case Some(c: Case) => (c.body eq t) && (skipCasesBlock(c.parent) match {
        case Some(m: Term.Match) => discarded(m)
        case Some(tr: Term.Try) => discarded(tr)
        case Some(pf: Term.PartialFunction) => functionDiscarded(pf)
        case _ => false
      })
      case Some(tr: Term.Try) => tr.finallyp.exists(_ eq t) || ((tr.expr eq t) && discarded(tr))
      case Some(f: Term.FunctionTerm) => (f.body eq t) && functionDiscarded(f)
      case Some(f: Term.AnonymousFunction) => (f.body eq t) && functionDiscarded(f)
      case Some(w: Term.While) => w.body eq t
      case Some(d: Term.Do) => d.body eq t
      case Some(f: Term.For) => f.body eq t
      case Some(d: Defn.Def) => (d.body eq t) && d.decltpe.exists(_.syntax == "Unit")
      case Some(c: Term.ArgClause) => !t.is[Term.FunctionTerm] && !t.is[Term.AnonymousFunction] && argumentDiscarded(c, t)
      case _ => false
    }
    val dbConnection = "com/augustnagro/magnum/DbCon#"
    val writePermit = "com/histmersion/util/database/WritePermit#"
    val databaseHolders = Set("com/histmersion/util/database/Transactor#", "com/histmersion/util/data/maintenance/WriteGate#",
      "com/histmersion/util/database/WritePermit.Gate#", "javax/sql/DataSource#")
    val jdbcWrites = Set("executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch")
    def outcomeKind(t: s.Type): Option[String] = {
      val d = dealias(unwrapType(t))
      newtypeOf(d).filter(_.displayName == "RowCount").map(_ => "RowCount").orElse(typeSymbol(d).value match {
        case "scala/Option#" | "scala/Some#" => Some("Option")
        case "scala/util/Either#" | "scala/util/Right#" | "scala/util/Left#" => Some("Either")
        case "scala/util/Try#" => Some("Try")
        case "scala/Boolean#" => Some("Boolean")
        case "scala/Int#" => Some("Int")
        case "scala/Long#" => Some("Long")
        case "scala/Array#" if d.isInstanceOf[s.TypeRef] && d.asInstanceOf[s.TypeRef].typeArguments.headOption.exists(a => typeSymbol(a).value == "scala/Int#") => Some("Int[]")
        case _ => None
      })
    }
    def takesDatabase(m: s.MethodSignature): Boolean = m.parameterLists.flatMap(_.symlinks).exists { p =>
      infoS(p).map(_.signature).exists {
        case v: s.ValueSignature =>
          val t = typeSymbol(dealias(unwrapType(v.tpe))).value
          t == dbConnection || t == writePermit || databaseHolders(t)
        case _ => false
      }
    }
    def statementOf(t: Term): Option[String] =
      t.collect { case i: Term.Interpolate if i.prefix.value == "sql" => i }.headOption.map(_.parts.map(_.value).mkString("?"))
    val noCount = "^(SET|RESET|CREATE|DROP|ALTER|ANALYZE|VACUUM|TRUNCATE|LOCK|REFRESH|COMMENT|GRANT|REVOKE|CLUSTER|REINDEX|DISCARD|NOTIFY|LISTEN)\\b.*".r
    def silentStatement(sql: String): Boolean = {
      val text = sql.linesIterator.map(_.replaceAll("--.*$", "")).mkString(" ").trim.replaceAll("\\s+", " ").toUpperCase
      val values = text.startsWith("INSERT ") && text.contains(" VALUES") && !text.contains("SELECT") && !text.contains("RETURNING")
      val conflict = text.indexOf("ON CONFLICT")
      val doUpdate = text.indexOf(" DO UPDATE ")
      noCount.pattern.matcher(text).matches ||
        (values && conflict < 0) ||
        (values && conflict >= 0 && doUpdate > conflict && !text.substring(doUpdate).contains(" WHERE "))
    }
    def writeOutcome(t: Term): Option[String] = {
      val callee = t match {
        case a: Term.Apply => calleeOf(a.fun)
        case sel: Term.Select => sel.name.symbol
        case n: Term.Name => n.symbol
        case _ => Symbol.None
      }
      val v = callee.value
      val name = callee.displayName
      val owner = callee.owner.displayName
      if (v.startsWith("com/augustnagro/magnum/") || v.startsWith("java/sql/")) {
        val result = scala.util.Try(callee.info).toOption.flatten.map(_.signature).collect {
          case m: MethodSignature => m.returnType
        }.collect { case TypeRef(_, sym, args) => (sym.value, args) }
        val kind = result.collect {
          case ("scala/Int#", _) => "Int"
          case ("scala/Long#", _) => "Long"
          case ("scala/Array#", List(TypeRef(_, a, _))) if a.value == "scala/Int#" || a.value == "scala/Long#" => "Int[]"
        }.orElse {
          if (v == "com/augustnagro/magnum/Update#run().") Some("Int")
          else if (v.startsWith("com/augustnagro/magnum/") && name == "batchUpdate") Some("BatchUpdateResult")
          else None
        }
        kind.filterNot(_ => statementOf(t).exists(silentStatement)).flatMap { k =>
          if (v.startsWith("com/augustnagro/magnum/") && Set("run", "batchUpdate", "insert", "insertAll", "update", "updateAll", "delete", "deleteById", "deleteAll", "deleteAllById", "truncate")(name))
            Some(s"magnum:$owner.$name:$k;" + statementOf(t).map(_.trim.takeWhile(c => !c.isWhitespace).toUpperCase).filter(_.nonEmpty).getOrElse("?"))
          else if (v.startsWith("java/sql/") && jdbcWrites(name)) Some(s"jdbc:$owner.$name:$k")
          else None
        }
      } else info(callee).map(_.signature) match {
        case Some(m: s.MethodSignature) if v.startsWith(projectPrefix) && reachesDatabase(callee) =>
          outcomeKind(m.returnType).map(kind => s"$owner.$name:$kind")
        case _ => None
      }
    }
    def reachesDatabase(method: Symbol): Boolean = {
      def direct(m: Symbol): Boolean = info(m).map(_.signature).exists {
        case sig: s.MethodSignature => takesDatabase(sig) || holdsDatabase(m.owner)
        case _ => false
      }
      direct(method) || overriders.getOrDefault(method.value, Nil).exists(o => direct(Symbol(o)))
    }
    def holdsDatabase(owner: Symbol): Boolean = info(owner).map(_.signature).exists {
      case c: s.ClassSignature => c.declarations.exists(_.symlinks.exists { d =>
        infoS(d).map(_.signature).exists {
          case v: s.ValueSignature => databaseHolders(typeSymbol(dealias(unwrapType(v.tpe))).value)
          case m: s.MethodSignature if m.parameterLists.isEmpty => databaseHolders(typeSymbol(dealias(unwrapType(m.returnType))).value)
          case _ => false
        }
      })
      case _ => false
    }

    def unguardedSymbol(n: Term.Name): Option[String] = {
      val v = n.symbol.value
      if (unguardedTransactions.contains(v)) Some("Transactor." + n.value)
      else if (v.startsWith("com/augustnagro/magnum/") && n.value == "transact") Some("magnum.transact")
      else None
    }
    def unguardedTransaction(fun: Term): Option[String] = fun match {
      case Term.ApplyType(f, _) => unguardedTransaction(f)
      case n: Term.Name => unguardedSymbol(n)
      case Term.Select(_, n) => unguardedSymbol(n)
      case _ => None
    }
    if (scope == "main" && !path.endsWith("/util/database/Transactor.scala")) doc.tree.traverse {
      case a: Term.Apply if !inImport(a) => unguardedTransaction(a.fun).foreach(d => report("G89Unguarded", a, d))
    }

    if (scope == "main") doc.tree.traverse {
      case a: Term.Apply if isUnusedAnswer(a.fun) && !inImport(a) => ()
      case t: Term if discarded(t) => writeOutcome(t).foreach(d => report("DiscardedOutcome", t, d))
    }

    def isPublic(sym: Symbol): Boolean =
      Iterator.iterate(sym)(_.owner).takeWhile(x => !x.isNone && !x.value.endsWith("/")).forall { x =>
        info(x).forall(i => i.access.isInstanceOf[s.PublicAccess] || i.access == s.Access.Empty)
      }
    def definedAtBoundary(sym: Symbol): Boolean =
      Option(project.definedIn.get(sym.value)).map(Project.relative).flatMap(Project.byRel.get).exists(t => Project.boundaryOf(t.raw).nonEmpty)
    def plainParams(sym: Symbol): Map[Int, (String, String)] = info(sym).map(_.signature) match {
      case Some(m: s.MethodSignature) =>
        m.parameterLists.toList.filterNot(l => l.symlinks.nonEmpty && l.symlinks.forall(p => infoS(p).exists(i =>
          (i.properties & (s.SymbolInformation.Property.IMPLICIT.value | s.SymbolInformation.Property.GIVEN.value)) != 0)))
          .flatMap(_.symlinks).zipWithIndex.flatMap { case (p, i) =>
            infoS(p).map(_.signature).collect { case v: s.ValueSignature => typeSymbol(dealias(v.tpe)).value }.filter(plainDefTypes)
              .map(t => i -> (Symbol(p).displayName, t.split("[/#.]").last))
          }.toMap
      case _ => Map.empty
    }
    if (!boundary) doc.tree.traverse {
      case a: Term.Apply =>
        val callee = a.fun match { case n: Term.Name => n.symbol; case Term.Select(_, n) => n.symbol; case _ => Symbol.None }
        if (callee.value.startsWith(projectPrefix) && !callee.isLocal) {
          def clauses(t: Term): List[Term.ArgClause] = t match { case x: Term.Apply => clauses(x.fun) :+ x.argClause; case _ => Nil }
          val all = clauses(a)
          val offset = all.takeWhile(_ ne a.argClause).map(_.values.size).sum
          lazy val params = plainParams(callee)
          a.argClause.values.zipWithIndex.foreach {
            case (sv: Term.Select, i) if isIronValue(sv.name) && params.contains(offset + i) && isPublic(callee) && !definedAtBoundary(callee) =>
              val (pn, pt) = params(offset + i)
              report("G44PlainDef", sv, s"${callee.displayName}($pn:$pt<-${termType(sv.qual).flatMap(newtypeOf).fold("?")(_.displayName)})")
            case _ =>
          }
        }
    }

    val problemWord = "(Problem|Error|Reason|Refusal|Failure|Why|Message)$".r
    val topLevelProblem = "^com/histmersion/(?:[a-z0-9]+/)*(?:Problem\\$package\\.)?Problem\\.$".r
    val positionalName = "^.+(Result[0-9]*|Arg[0-9]+|Part[0-9]+)$".r
    doc.tree.traverse {
      case o: Defn.Object if isNewtypeObject(o.name.symbol) && !o.name.symbol.isLocal =>
        val base = newtypeParents(o.name.symbol).map(_._2.value)
        if (base.exists(b => b == "java/lang/String#" || b == "scala/Predef.String#") && problemWord.findFirstIn(o.name.value).nonEmpty) {
          if (!topLevelProblem.pattern.matcher(o.name.symbol.value).matches) report("G5Problem", o, o.name.symbol.value.stripPrefix("com/histmersion/"))
          else report("G5Problem", o, "a second top-level Problem: " + o.name.symbol.value.stripPrefix("com/histmersion/").replaceAll("Problem\\$package\\.", ""))
        }
        if (positionalName.pattern.matcher(o.name.value).matches) report("G55Positional", o, "newtype:" + o.name.value)
      case d: Defn if d.parent.exists(p => p.is[Pkg.Body] || p.is[Pkg] || p.is[Source]) && (d match {
            case t: Defn.Trait => t.name.value == "Problem"; case c: Defn.Class => c.name.value == "Problem"
            case e: Defn.Enum => e.name.value == "Problem"; case o: Defn.Object => o.name.value == "Problem"
            case t: Defn.Type => t.name.value == "Problem" && !(t.body match { case Type.Select(q, Type.Name("T")) => q.syntax == "Problem"; case _ => false })
            case _ => false
          }) =>
        val sym = d match { case t: Defn.Trait => t.name.symbol; case c: Defn.Class => c.name.symbol; case e: Defn.Enum => e.name.symbol
          case t: Defn.Type => t.name.symbol; case o: Defn.Object => o.name.symbol; case _ => Symbol.None }
        val companionOfClosed = sym.value.startsWith(closedProblem)
        if (!companionOfClosed) report("G5Problem", d, "a second top-level Problem: " + sym.value.stripPrefix("com/histmersion/"))
      case t: Defn.Type if !t.name.symbol.isLocal && positionalName.pattern.matcher(t.name.value).matches &&
          !(t.body match { case Type.Select(q, Type.Name("T")) => q.syntax.split('.').last == t.name.value; case _ => false }) =>
        report("G55Positional", t, "alias:" + t.name.value)
    }

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

    if (scope == "main" && !boundary) {
      def isStringBase(sym: Symbol): Boolean = sym.value == "java/lang/String#" || sym.value == "scala/Predef.String#"
      def stringish(t: Option[s.Type], depth: Int = 0): Boolean = t.map(dealias(_)).exists { tpe =>
        newtypeOf(tpe).flatMap(newtypeParents).exists(p => isStringBase(p._2)) || isStringBase(typeSymbol(tpe)) ||
          (depth == 0 && (tpe match {
            case r: s.TypeRef if (r.symbol == "scala/Option#" || r.symbol == "scala/collection/immutable/List#") && r.typeArguments.size == 1 =>
              stringish(Some(r.typeArguments.head), 1)
            case _ => false
          }))
      }
      def textNamed(name: String): Boolean = textBranchTypes.r.pattern.matcher(name).matches
      def throwableText(n: Name): Boolean = Set("getMessage", "getLocalizedMessage", "toString")(n.value) && {
        val owner = n.symbol.value.takeWhile(_ != '#')
        owner == "java/lang/Throwable" || owner.endsWith("Exception") || owner.endsWith("Error")
      }
      def fieldNamed(t: Term): Option[String] = t match {
        case n: Term.Name if textBranchFields.contains(n.value) && stringish(termType(n)) => Some("field:" + n.value)
        case Term.Select(_, n) if textBranchFields.contains(n.value) && stringish(termType(t)) => Some("field:" + n.value)
        case _ => None
      }
      def problemWords(n: Name): Option[String] = {
        val sym = n.symbol.value
        if (sym.startsWith("com/histmersion/util/problem/Problem.legacyText(")) Some("Problem.legacyText")
        else if (sym.startsWith("com/histmersion/util/problem/Text.") && !sym.endsWith("Text.")) Some("Text." + n.value)
        else None
      }
      def textSubject(t: Term): Option[String] = t match {
        case Term.Select(_, n) if problemWords(n).nonEmpty => problemWords(n)
        case a: Term.Apply if (a.fun match {
              case Term.Select(_, n) => problemWords(n).nonEmpty
              case n: Term.Name      => problemWords(n).nonEmpty
              case _                 => false
            }) =>
          a.fun match {
            case Term.Select(_, n) => problemWords(n)
            case n: Term.Name      => problemWords(n)
            case _                 => None
          }
        case Term.Select(q, n) if isIronValue(n) =>
          termType(q).flatMap(newtypeOf).filter(nt => newtypeParents(nt).exists(p => isStringBase(p._2)) && textNamed(nt.displayName)).map(_.displayName)
            .orElse(fieldNamed(q))
        case Term.Select(_, n) if throwableText(n) => Some("Throwable." + n.value)
        case a: Term.Apply if a.argClause.values.isEmpty && (a.fun match { case Term.Select(_, n) => throwableText(n); case _ => false }) =>
          Some("Throwable." + a.fun.asInstanceOf[Term.Select].name.value)
        case p: Term.Placeholder =>
          ancestors(p).collectFirst { case f: Term.AnonymousFunction => f }.flatMap(f => f.parent.flatMap(_.parent)).collect {
            case a: Term.Apply => a.fun
          }.collect { case Term.Select(recv, _) => recv }.flatMap(r => fieldNamed(r) orElse (r match {
            case Term.Select(q, n) if isIronValue(n) => fieldNamed(q)
            case _ => None
          }))
        case other => fieldNamed(other)
      }
      def objectConstant(sym: Symbol): Boolean = info(sym).exists(i =>
        (i.kind == s.SymbolInformation.Kind.OBJECT || i.kind == s.SymbolInformation.Kind.FIELD || i.kind == s.SymbolInformation.Kind.METHOD) &&
          sym.owner.value.endsWith(".") && (i.signature match { case m: s.MethodSignature => m.parameterLists.forall(_.symlinks.isEmpty); case _ => true }))
      def isConstant(t: Term): Boolean = isLiteral(t) || t.is[Term.Interpolate] || (t match {
        case sel: Term.Select if isIronValue(sel.name) || sel.name.value == "r" => isConstant(sel.qual)
        case sel: Term.Select => objectConstant(sel.name.symbol)
        case n: Term.Name => objectConstant(n.symbol)
        case a: Term.Apply if a.argClause.values.size == 1 =>
          (a.fun match { case n: Term.Name => isNewtypeObject(n.symbol); case sel: Term.Select => isNewtypeObject(sel.name.symbol); case _ => false }) && isConstant(a.argClause.values.head)
        case _ => false
      })
      def constantOperand(t: Term): Boolean = t match {
        case Term.Select(q, n) if isIronValue(n) => constantOperand(q)
        case other => isConstant(other)
      }
      def ownText(t: Tree, subject: Term): Boolean = {
        val nt = (subject match { case Term.Select(q, n) if isIronValue(n) => termType(q).flatMap(newtypeOf); case _ => None })
        allowedInCompanion(t, nt) && nt.nonEmpty
      }
      def flag(t: Tree, op: String, subject: String): Unit =
        report(if (subject.startsWith("Text.") || subject == "Problem.legacyText") "G5ProblemText" else "G5TextBranch", t, s"$op:$subject")
      val regexOps = Set("findFirstIn", "findFirstMatchIn", "findPrefixOf", "findPrefixMatchOf", "findAllIn", "findAllMatchIn", "unapplySeq", "matcher", "replaceAllIn", "replaceFirstIn")
      doc.tree.traverse {
        case a: Term.Apply if a.fun.is[Term.Select] && (textBranchOps ++ textParseOps)(a.fun.asInstanceOf[Term.Select].name.value) =>
          val sel = a.fun.asInstanceOf[Term.Select]
          val direct = textSubject(sel.qual).filter(_ => (textParseOps(sel.name.value) || a.argClause.values.exists(constantOperand)) && !ownText(a, sel.qual)).map(sel.name.value -> _)
          val listed = if (sel.name.value != "contains") None else {
            val constants = sel.qual match {
              case ap: Term.Apply => ap.argClause.values.nonEmpty && ap.argClause.values.forall(constantOperand)
              case l => constantOperand(l)
            }
            if (constants) a.argClause.values.headOption.flatMap(x => textSubject(x).filterNot(_ => ownText(a, x))).map("contains-in" -> _) else None
          }
          (direct orElse listed).foreach { case (op, subject) => flag(a, op, subject) }
        case i: Term.ApplyInfix if textBranchOps(i.op.value) || Set("==", "!=")(i.op.value) =>
          val op = i.op.value
          val direct = textSubject(i.lhs).filter(_ => i.argClause.values.exists(constantOperand) && !ownText(i, i.lhs))
          val flipped = if (Set("==", "!=")(op)) i.argClause.values.headOption.flatMap(textSubject).filter(_ => constantOperand(i.lhs) && !ownText(i, i.argClause.values.head)) else None
          (direct orElse flipped).foreach(flag(i, op, _))
        case a: Term.Apply if a.fun.is[Term.Select] && regexOps(a.fun.asInstanceOf[Term.Select].name.value) =>
          a.argClause.values.flatMap(textSubject).headOption.foreach(flag(a, "regex", _))
        case m: Term.Match =>
          textSubject(m.expr).filter { _ =>
            m.cases.exists(c => c.pat.collect {
              case _: Lit.String => ()
              case _: Pat.Interpolate => ()
              case e: Pat.Extract if termType(e.fun).map(dealias(_)).exists(tt => typeSymbol(tt).value == "scala/util/matching/Regex#") => ()
            }.nonEmpty)
          }.filterNot(_ => ownText(m, m.expr)).foreach(flag(m, "match", _))
        case _ =>
      }
    }

    val filePkg = doc.tree match { case s: Source => s.stats.collect { case p: Pkg => p.ref.syntax }.mkString("."); case _ => "" }
    val projectFile = filePkg == "com.histmersion" || filePkg.startsWith("com.histmersion.")
    doc.tree.traverse {
      case n: Term.Name if n.value == "_root_" => report("G47Root", n, n.parent.map(_.syntax.take(80)).getOrElse(""))
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
        report("G47Fqn", top, (if (targetPkg == here) "same-package:" else "other-package:") + top.syntax.replace("_root_.", "").take(100))
    }
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
        val enclosingObjects = ancestors(sel).collect { case o: Defn.Object => o.name.symbol.value }.toSet
        val hit = qs.zipWithIndex.find { case (q, i) =>
          !q.symbol.isNone && !isNewtypeObject(q.symbol) &&
            (if (i == 0) enclosingObjects(q.symbol.value) else enclosing(companionKey(q.symbol)))
        }
        hit.foreach { case (q, i) =>
          val node = (if (i == 0) q.parent else q.parent.flatMap(_.parent)).getOrElse(ref)
          val member = node match { case x: Term.Select => x.name.value; case x: Type.Select => x.name.value; case _ => "" }
          if (member != "T") report("G47Self", node, (if (i > 0) "full-path:" else "name-qualified:") + node.syntax.replace("_root_.", "").take(100))
        }
    }
    doc.tree match {
      case src: Source =>
        val pkgs = src.stats.collect { case p: Pkg => p }
        if (pkgs.size == 1 && !pkgs.head.body.stats.exists(_.is[Pkg]) && pkgs.head.ref.syntax.startsWith("com.histmersion.") && pkgs.head.ref.syntax.count(_ == '.') >= 2)
          report("G47Package", pkgs.head.ref, pkgs.head.ref.syntax)
      case _ =>
    }

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

    doc.tree.traverse {
      case i: Term.ApplyInfix if (i.op.value == "==" || i.op.value == "!=") && i.argClause.values.size == 1 =>
        val l = classify(termType(i.lhs)); val r = classify(termType(i.argClause.values.head))
        if (unrelated(l, r)) report("SilentCompare", i, s"${i.op.value}:${show(l)}~${show(r)}")
      case a: Term.Apply if a.argClause.values.size == 1 => a.fun match {
        case Term.Select(recv, op) if Set("contains", "indexOf", "lastIndexOf")(op.value) && op.symbol.value.startsWith("scala/") =>
          val elem = termType(recv).map(dealias(_)) match {
            case Some(r: s.TypeRef) if r.typeArguments.nonEmpty => Some(r.typeArguments.head)
            case _ => None
          }
          val l = classify(elem); val r = classify(termType(a.argClause.values.head))
          if (unrelated(l, r)) report("SilentCompare", a, s"${op.value}:${show(l)}~${show(r)}")
        case _ =>
      }
      case i: Term.Interpolate =>
        i.args.foreach { arg =>
          val k = classify(termType(arg))
          val caseSym = arg match { case n: Term.Name => n.symbol; case s: Term.Select => s.name.symbol; case _ => Symbol.None }
          val enumCase = isEnumSym(caseSym) && !hasKind(caseSym, Set(s.SymbolInformation.Kind.CLASS, s.SymbolInformation.Kind.TRAIT))
          k match {
            case EnumK(e) => report("SilentInterp", arg, s"${i.prefix.value}:${e.displayName}")
            case _ if enumCase => report("SilentInterp", arg, s"${i.prefix.value}:${caseSym.owner.displayName}.${caseSym.displayName}")
            case _ =>
          }
        }
    }

    def isBooleanType(t: s.Type): Boolean = typeSymbol(dealias(t)).value == "scala/Boolean#"
    def ours(sym: Symbol): Boolean = !sym.isNone && (sym.value.startsWith(projectPrefix) || sym.isLocal)
    def implicitParam(p: String): Boolean = infoS(p).exists(i =>
      (i.properties & (s.SymbolInformation.Property.IMPLICIT.value | s.SymbolInformation.Property.GIVEN.value)) != 0)
    def paramLists(sym: Symbol): Option[List[List[String]]] = info(sym).map(_.signature).collect {
      case m: s.MethodSignature => m.parameterLists.toList.map(_.symlinks.toList)
    }
    def constructorOf(obj: Symbol): Symbol = {
      val apply = Symbol(obj.value + "apply().")
      if (paramLists(apply).nonEmpty) apply else Symbol(obj.value.stripSuffix(".") + "#`<init>`().")
    }
    def booleanCallee(fun: Term): Option[(Symbol, Boolean)] = fun match {
      case n: Term.Name if hasKind(n.symbol, Set(s.SymbolInformation.Kind.OBJECT)) => Some(constructorOf(n.symbol) -> false)
      case n: Term.Name => Some(n.symbol -> false)
      case Term.Select(_, n) if hasKind(n.symbol, Set(s.SymbolInformation.Kind.OBJECT)) => Some(constructorOf(n.symbol) -> false)
      case Term.Select(q, n) =>
        val viaObject = q match {
          case qn: Term.Name => hasKind(qn.symbol, objectKinds)
          case qs: Term.Select => hasKind(qs.name.symbol, objectKinds)
          case _ => false
        }
        Some(n.symbol -> (!viaObject && n.symbol.owner.value.endsWith(".")))
      case Term.ApplyType(f, _) => booleanCallee(f)
      case _ => None
    }
    def isOperator(sym: Symbol): Boolean = !sym.displayName.exists(_.isLetter) && sym.displayName != "<init>"
    def unnamedBooleans(sym: Symbol, extension: Boolean, clauses: List[Term.ArgClause]): Unit =
      if (ours(sym) && !isOperator(sym)) paramLists(sym).foreach { lists =>
        val (contextual, explicit) = lists.partition(l => l.nonEmpty && l.forall(implicitParam))
        val explicitLists = if (extension) explicit.drop(1) else explicit
        val usingClause = clauses.map(_.mod.exists(_.is[Mod.Using]))
        clauses.zipWithIndex.foreach { case (clause, k) =>
          val nth = usingClause.take(k).count(_ == usingClause(k))
          (if (usingClause(k)) contextual.lift(nth) else explicitLists.lift(nth)).foreach { params =>
            clause.values.zipWithIndex.foreach {
              case (_: Term.Assign, _) =>
              case (arg, i) =>
                val param = if (i < params.size) params.lift(i) else params.lastOption.filter(p =>
                  infoS(p).map(_.signature).exists { case v: s.ValueSignature => v.tpe.isInstanceOf[s.RepeatedType]; case _ => false })
                param.filter(p => infoS(p).map(_.signature).exists { case v: s.ValueSignature => isBooleanType(v.tpe); case _ => false }).foreach { p =>
                  val shape = if (arg.is[Lit.Boolean]) "literal" else "value"
                  val callee = if (sym.displayName == "<init>") "new " + sym.owner.displayName else sym.owner.displayName + "." + sym.displayName
                  report("UnnamedBoolean", arg, s"$callee(${Symbol(p).displayName});$shape")
                }
            }
          }
        }
      }
    def argChain(a: Term.Apply): (Term, List[Term.ArgClause]) = a.fun match {
      case inner: Term.Apply => val (f, cs) = argChain(inner); (f, cs :+ a.argClause)
      case f => (f, List(a.argClause))
    }
    doc.tree.traverse {
      case a: Term.Apply if !inImport(a) && !a.parent.exists { case p: Term.Apply => p.fun eq a; case _ => false } =>
        val (fun, clauses) = argChain(a)
        booleanCallee(fun).foreach { case (sym, extension) => unnamedBooleans(sym, extension, clauses) }
      case i: Init if i.argClauses.nonEmpty =>
        val named = List(i.symbol, i.tpe.symbol).filterNot(_.isNone).map(c => if (c.value.endsWith("#")) Symbol(c.value + "`<init>`().") else c)
        named.find(c => paramLists(c).nonEmpty).foreach(unnamedBooleans(_, extension = false, i.argClauses.toList))
    }
    def local(t: Tree): Boolean = ancestors(t).exists(a => a.is[Defn.Def] || a.is[Defn.Val] || a.is[Defn.Var] || a.is[Term])
    def plainPrivate(mods: List[Mod]): Boolean = mods.exists {
      case Mod.Private(w) => w.is[Name.Anonymous] || w.is[Term.This]
      case _ => false
    }
    def booleanDeclared(p: Term.Param): Boolean =
      resultOf(p.name.symbol).map(isBooleanType).getOrElse(p.decltpe.exists(t => Set("Boolean", "scala.Boolean")(t.syntax.stripSuffix("*").trim)))
    doc.tree.traverse {
      case d: Defn.Def if !local(d) && !plainPrivate(d.mods) && !d.mods.exists(_.is[Mod.Override]) =>
        val owner = enclosingTemplates(d).headOption.fold("")(_.displayName + ".")
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).filter(booleanDeclared).foreach { p =>
          report("BooleanParam", p, s"param:$owner${d.name.value}(${p.name.value})")
        }
      case c: Defn.Class if !local(c) => c.ctor.paramClauses.flatMap(_.values).filter(booleanDeclared).foreach { p =>
          report("BooleanParam", p, s"field:${c.name.value}.${p.name.value}")
        }
      case e: Defn.EnumCase if !local(e) => e.ctor.paramClauses.flatMap(_.values).filter(booleanDeclared).foreach { p =>
          report("BooleanParam", p, s"field:${enclosingTemplates(e).headOption.fold("")(_.displayName + ".")}${e.name.value}.${p.name.value}")
        }
    }

    val pool = ref.toList.flatMap(r => Project.typeDefsByModule.getOrElse(r.module, Vector.empty))
    def addPrefix(t: Tree, n: Name): Unit =
      if (!Project.local(t) && !(scope == "test" && testSuiteSuffixes.exists(n.value.endsWith))) {
        val owner = Project.ownerKey(t, path)
        val name = n.value
        val entities = pool.filter(e => e.name.length < name.length && name.startsWith(e.name) && name.charAt(e.name.length).isUpper &&
          (e.owner.isEmpty || e.owner == owner))
        entities.sortBy(e => -e.name.length).headOption.foreach { e =>
          val rest = name.drop(e.name.length)
          val role = roleSuffixes.exists(r => rest == r || rest.endsWith(r))
          report(if (role) "G9PrefixRole" else "G9Prefix", t, s"${e.name}+$rest")
        }
      }
    doc.tree.traverse {
      case d: Defn.Class => addPrefix(d, d.name)
      case d: Defn.Trait => addPrefix(d, d.name)
      case d: Defn.Enum => addPrefix(d, d.name)
      case d: Defn.Object => addPrefix(d, d.name)
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
                val project = sym.value.startsWith(projectPrefix)
                if (project) report("G3Misplaced", d, s"${o.name.value}.${d.name.value}(${p.name.value}: ${sym.displayName})")
              }
            case _ =>
          }
        case _ =>
      }
    }
    val all = found.result()
    val (prefixes, rest) = all.partition(f => f.rule == "G9Prefix" || f.rule == "G9PrefixRole")
    rest ++ prefixes.groupBy(_.message).values.map(_.minBy(_.pos.start)).toList
  }
}

final class GuidelineLint(scalaVersion: String) extends SemanticRule("GuidelineLint") {
  def this() = this("")

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new GuidelineLint(config.scalaVersion))

  override def fix(using doc: SemanticDocument): Patch =
    Tolerance.patch(Tolerance.diagnostics(Guidelines.findings(doc, scalaVersion), doc.tree, Guidelines.Owned))
}

final class GuidelinePrune(scalaVersion: String) extends SemanticRule("GuidelinePrune") {
  def this() = this("")

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new GuidelinePrune(config.scalaVersion))

  override def fix(using doc: SemanticDocument): Patch =
    Baseline.prune(Guidelines.findings(doc, scalaVersion), doc.tree, Guidelines.Owned)
}

final class GuidelineBaseline(scalaVersion: String) extends SemanticRule("GuidelineBaseline") {
  def this() = this("")

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new GuidelineBaseline(config.scalaVersion))

  override def fix(using doc: SemanticDocument): Patch =
    Baseline.tolerations(Guidelines.findings(doc, scalaVersion), doc.tree, Project.relative(doc))
}
