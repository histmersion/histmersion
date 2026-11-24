import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

object ValueOntoType {

  case class Config(
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}"),
      paths: List[String] = Nil,
      excludes: List[String] = Nil,
      operations: Boolean = true,
      roundTrips: Boolean = true,
      comparisons: Boolean = true,
      sinks: List[String] = Nil,
      sinkImport: String = "",
      sinkPackage: String = "",
      sinkRequires: String = "",
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
  val compared = Set("==", "!=", "<", "<=", ">", ">=")
  val constructions = Set("apply", "applyUnsafe", "assume")

  def buildIndex(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = {
    val index = new ConcurrentHashMap[String, s.SymbolInformation]()
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList
      finally stream.close()
    }
    val md5Cache = new ConcurrentHashMap[String, String]()
    def md5(uri: String): String = md5Cache.computeIfAbsent(uri, u => {
      val p = Paths.get(u)
      if (!Files.isRegularFile(p)) ""
      else java.security.MessageDigest.getInstance("MD5").digest(Files.readAllBytes(p)).map(b => f"${b & 255}%02X").mkString
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

  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
}

class ValueOntoType(config: ValueOntoType.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("ValueOntoType") {
  import ValueOntoType._

  def this() = this(ValueOntoType.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ValueOntoType")(ValueOntoType.Config()).map { cfg =>
      new ValueOntoType(cfg, buildIndex(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private val rewritten = new ConcurrentLinkedQueue[String]()
  private val missing = new ConcurrentLinkedQueue[String]()
  private val sinkPatterns = config.sinks.map(_.r)

  override def afterComplete(): Unit =
    if (config.report.nonEmpty) {
      Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
      Files.write(Paths.get(config.report), rewritten.asScala.toList.sorted.asJava, UTF_8)
      Files.write(Paths.get(config.report.stripSuffix(".tsv") + "-missing.tsv"), missing.asScala.toList.sorted.asJava, UTF_8)
    }

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  private def selection(sel: Term.Select): Seq[Token] = {
    val tokens = sel.tokens
    val dot = tokens.lastIndexWhere(t => t.is[Token.Dot] && t.end <= sel.name.pos.start)
    tokens.drop(dot).filter(_.end <= sel.name.pos.end)
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = relative(doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case other => other.toString
    })
    if (config.paths.nonEmpty && !config.paths.exists(path.startsWith)) return Patch.empty
    if (config.excludes.exists(e => path.matches(e))) return Patch.empty

    def infoS(sym: String): Option[s.SymbolInformation] =
      if (sym.isEmpty) None else Option(index.get(if (sym.startsWith("local")) path + "::" + sym else sym))
    def info(sym: Symbol): Option[s.SymbolInformation] = if (sym.isNone) None else infoS(sym.value)
    def hasObjectKind(sym: Symbol): Boolean = info(sym).exists(i =>
      i.kind == s.SymbolInformation.Kind.OBJECT || i.kind == s.SymbolInformation.Kind.PACKAGE_OBJECT ||
        i.kind == s.SymbolInformation.Kind.PACKAGE)
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
    def isNewtypeObject(sym: String): Boolean = sym.endsWith(".") && infoS(sym).map(_.signature).exists {
      case cs: s.ClassSignature => cs.parents.exists {
        case r: s.TypeRef => (r.symbol == ironValue + "RefinedType#" || r.symbol == ironValue + "RefinedSubtype#" ||
          r.symbol == newtypeBase) && r.typeArguments.size == 2
        case _ => false
      }
      case _ => false
    }
    def newtypeOf(t: s.Type): Option[String] = dealias(t) match {
      case r: s.TypeRef if r.symbol.startsWith(ironValue) && r.symbol.endsWith("#T#") => r.prefix match {
        case st: s.SingleType if isNewtypeObject(st.symbol) => Some(st.symbol)
        case th: s.ThisType if isNewtypeObject(th.symbol) => Some(th.symbol)
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
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def termType(t: Term): Option[s.Type] = t match {
      case n: Term.Name => if (hasObjectKind(n.symbol)) None else resultOf(n.symbol)
      case sel: Term.Select => if (hasObjectKind(sel.name.symbol)) None else resultOf(sel.name.symbol)
      case a: Term.Apply => a.fun match {
        case n: Term.Name if isNewtypeObject(n.symbol.value) => None
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
    def newtypeOfTerm(t: Term): Option[String] = termType(t).flatMap(newtypeOf)

    def hasExtension(obj: String, op: String): Boolean = {
      val name = if (op.matches("[A-Za-z_][A-Za-z0-9_]*")) op else s"`$op`"
      (List(s"$obj$name().") ++ (1 to 6).map(i => s"$obj$name(+$i).")).exists { m =>
        infoS(m).map(_.signature).exists {
          case ms: s.MethodSignature => ms.parameterLists.headOption.flatMap(_.symlinks.headOption).flatMap(infoS)
              .map(_.signature).exists {
                case v: s.ValueSignature => newtypeOf(v.tpe).contains(obj)
                case _ => false
              }
          case _ => false
        }
      }
    }
    def constructionOf(fun: Term): Option[String] = fun match {
      case n: Term.Name if isNewtypeObject(n.symbol.value) => Some(n.symbol.value)
      case Term.Select(q, n) if isNewtypeObject(n.symbol.value) => Some(n.symbol.value)
      case Term.Select(q, n) if constructions(n.value) && n.symbol.value.startsWith(ironValue) => q match {
        case qn: Term.Name if isNewtypeObject(qn.symbol.value) => Some(qn.symbol.value)
        case qs: Term.Select if isNewtypeObject(qs.name.symbol.value) => Some(qs.name.symbol.value)
        case _ => None
      }
      case _ => None
    }
    def inImport(t: Tree): Boolean = ancestors(t).exists(a => a.is[Import] || a.is[Export])
    def enclosingNewtypes(t: Tree): Set[String] =
      ancestors(t).collect { case o: Defn.Object if isNewtypeObject(o.name.symbol.value) => o.name.symbol.value }.toSet

    val used = scala.collection.mutable.Set.empty[Tree]
    var sinkUsed = false
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    def dropValue(sel: Term.Select, why: String): Patch = {
      used += sel
      rewritten.add(s"$path:${sel.pos.startLine + 1}\t$why")
      Patch.removeTokens(selection(sel)).atomic
    }
    def valueSelect(t: Term): Option[(Term.Select, String)] = t match {
      case sel @ Term.Select(q, n) if isIronValue(n) && !used(sel) => newtypeOfTerm(q).map(sel -> _)
      case _ => None
    }
    def isSink(fun: Term): Boolean = {
      val sym = fun match {
        case n: Term.Name => n.symbol.value
        case Term.Select(_, n) => n.symbol.value
        case Term.ApplyType(f, _) => return isSink(f)
        case _ => ""
      }
      sinkSymbol(sym)
    }
    def sinkSymbol(sym: String): Boolean = sym.nonEmpty && sinkPatterns.exists(_.pattern.matcher(sym).matches)
    def outsideCompanion(t: Tree, nt: String): Boolean = !enclosingNewtypes(t).contains(nt)

    def hasInstance(obj: String, typeClass: String): Boolean = infoS(obj).map(_.signature).exists {
      case cs: s.ClassSignature => cs.declarations.toList.flatMap(_.symlinks).flatMap(infoS).exists { i =>
        (i.properties & s.SymbolInformation.Property.GIVEN.value) != 0 && (i.signature match {
          case v: s.ValueSignature => Some(v.tpe)
          case m: s.MethodSignature if m.parameterLists.forall(_.symlinks.forall(p =>
                infoS(p).exists(pi => (pi.properties & s.SymbolInformation.Property.GIVEN.value) != 0))) => Some(m.returnType)
          case _ => None
        }).map(dealias(_)).exists {
          case r: s.TypeRef => r.symbol == typeClass && r.typeArguments.headOption.flatMap(newtypeOf).contains(obj)
          case _ => false
        }
      }
      case _ => false
    }
    def toSink(sel: Term.Select, nt: String, why: String): Unit =
      if (config.sinkRequires.isEmpty || hasInstance(nt, config.sinkRequires)) {
        sinkUsed = true
        patches += dropValue(sel, s"$why:$nt")
      } else missing.add(s"${config.sinkRequires}\t$nt\t$path:${sel.pos.startLine + 1}")

    doc.tree.traverse {
      case a: Term.Apply if config.roundTrips && !inImport(a) && a.argClause.values.size == 1 &&
          constructionOf(a.fun).nonEmpty =>
        val nt = constructionOf(a.fun).get
        valueSelect(a.argClause.values.head).filter(_._2 == nt).filter(_ => outsideCompanion(a, nt)).foreach { case (sel, _) =>
          used += sel
          rewritten.add(s"$path:${a.pos.startLine + 1}\tround-trip:$nt")
          patches += Patch.removeTokens(a.tokens.filter(t => t.start < sel.qual.pos.start || t.start >= sel.qual.pos.end)).atomic
        }
      case i: Term.ApplyInfix if config.comparisons && compared(i.op.value) && i.argClause.values.size == 1 =>
        (valueSelect(i.lhs), valueSelect(i.argClause.values.head)) match {
          case (Some((l, a)), Some((r, b))) if a == b && outsideCompanion(i, a) =>
            patches += dropValue(l, s"compare:${i.op.value}:$a")
            patches += dropValue(r, s"compare:${i.op.value}:$a")
          case _ =>
        }
      case outer @ Term.Select(inner @ Term.Select(q, v), op) if config.operations && isIronValue(v) && !used(inner) &&
          !inImport(outer) =>
        newtypeOfTerm(q).filter(nt => outsideCompanion(outer, nt)).foreach { nt =>
          if (hasExtension(nt, op.value)) patches += dropValue(inner, s"operation:${op.value}:$nt")
          else missing.add(s"${op.value}\t$nt\t$path:${outer.pos.startLine + 1}")
        }
      case i: Term.ApplyInfix if config.operations && !compared(i.op.value) =>
        i.lhs match {
          case inner @ Term.Select(q, v) if isIronValue(v) && !used(inner) =>
            newtypeOfTerm(q).filter(nt => hasExtension(nt, i.op.value) && outsideCompanion(i, nt)).foreach { nt =>
              patches += dropValue(inner, s"operation:${i.op.value}:$nt")
            }
          case _ =>
        }
        if (config.sinks.nonEmpty && sinkSymbol(i.op.symbol.value))
          i.argClause.values.flatMap(valueSelect).foreach { case (sel, nt) => toSink(sel, nt, s"sink:${i.op.value}") }
      case a: Term.Apply if config.sinks.nonEmpty && isSink(a.fun) =>
        a.argClause.values.flatMap(valueSelect).foreach { case (sel, nt) => toSink(sel, nt, "sink") }
    }
    val pkg = doc.tree.collect { case p: Pkg => p.ref.syntax }.mkString(".")
    val imported = doc.tree.collect { case i: Import => i.importers.map(_.syntax) }.flatten.contains(config.sinkImport)
    if (sinkUsed && config.sinkImport.nonEmpty && pkg != config.sinkPackage && !imported) {
      val innermost = doc.tree.collect { case p: Pkg => p }.lastOption
      innermost.foreach { p =>
        p.stats.collect { case i: Import => i }.lastOption match {
          case Some(last) => patches += Patch.addRight(last, s"\nimport ${config.sinkImport}")
          case None => patches += Patch.addRight(p.ref, s"\n\nimport ${config.sinkImport}")
        }
      }
    }
    patches.result().asPatch
  }
}
