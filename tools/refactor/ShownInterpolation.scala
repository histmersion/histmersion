import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

object ShownInterpolation {
  case class Config(
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}"),
      paths: List[String] = Nil,
      excludes: List[String] = Nil,
      asIs: List[String] = Nil,
      asIsFrom: List[String] = Nil,
      interpolator: String = "shown",
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
  val plain = Set("java/lang/String#", "scala/Predef.String#", "scala/Int#", "scala/Long#", "scala/Double#",
    "scala/Boolean#", "scala/Char#")
  def norm(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")

  def symbolsIn(file: String): List[String] =
    if (!Files.isRegularFile(Paths.get(file))) Nil
    else {
      val text = new String(Files.readAllBytes(Paths.get(file)), UTF_8)
      val types = "(?s)types\\s*=\\s*\\[(.*?)\\]".r.findFirstMatchIn(text).map(_.group(1)).getOrElse("")
      "\"([^\"]+)\"".r.findAllMatchIn(types).map(_.group(1)).toList
    }

  def buildIndex(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = {
    val index = new ConcurrentHashMap[String, s.SymbolInformation]()
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList
      finally stream.close()
    }
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.foreach { d =>
        d.symbols.foreach { i =>
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

class ShownInterpolation(config: ShownInterpolation.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("ShownInterpolation") {
  import ShownInterpolation._

  def this() = this(ShownInterpolation.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ShownInterpolation")(ShownInterpolation.Config()).map { cfg =>
      new ShownInterpolation(cfg, buildIndex(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private val asIs: Set[String] = (config.asIs ++ config.asIsFrom.flatMap(symbolsIn)).map(norm).toSet
  private val log = new ConcurrentLinkedQueue[String]()

  override def afterComplete(): Unit =
    if (config.report.nonEmpty) {
      Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
      Files.write(Paths.get(config.report), log.asScala.toList.sorted.asJava, UTF_8)
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
    def dealias(t: s.Type, depth: Int = 0): s.Type = if (depth > 12) t else t match {
      case a: s.AnnotatedType => dealias(a.tpe, depth + 1)
      case r: s.TypeRef if !r.symbol.startsWith(ironValue) => infoS(r.symbol).map(_.signature) match {
        case Some(ts: s.TypeSignature) if ts.lowerBound == ts.upperBound && ts.upperBound != s.Type.Empty &&
            ts.typeParameters.forall(sc => sc.symlinks.isEmpty && sc.hardlinks.isEmpty) => dealias(ts.upperBound, depth + 1)
        case _ => t
      }
      case _ => t
    }
    def isNewtypeObject(sym: String): Boolean = sym.endsWith(".") && infoS(sym).map(_.signature).exists {
      case cs: s.ClassSignature => cs.parents.exists {
        case r: s.TypeRef => (r.symbol == ironValue + "RefinedType#" || r.symbol == newtypeBase) && r.typeArguments.size == 2
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
    def resultOf(sym: String): Option[s.Type] = infoS(sym).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature if m.parameterLists.isEmpty => m.returnType
    }
    def isObject(sym: String): Boolean = infoS(sym).exists(i =>
      i.kind == s.SymbolInformation.Kind.OBJECT || i.kind == s.SymbolInformation.Kind.PACKAGE)
    def termType(t: Term): Option[s.Type] = t match {
      case n: Term.Name if !isObject(n.symbol.value) => resultOf(n.symbol.value)
      case sel: Term.Select if !isObject(sel.name.symbol.value) => resultOf(sel.name.symbol.value)
      case b: Term.Block if b.stats.size == 1 => b.stats.head match { case x: Term => termType(x); case _ => None }
      case _: Lit.String | _: Lit.Int | _: Lit.Long | _: Lit.Double | _: Lit.Boolean | _: Lit.Char =>
        Some(s.TypeRef(s.Type.Empty, "scala/Int#", Nil))
      case _ => None
    }
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def isPlain(t: Term): Boolean = termType(t).map(dealias(_)).exists {
      case r: s.TypeRef => plain(r.symbol)
      case _ => false
    }
    def unwrapped(t: Term): Term = t match {
      case Term.Block(List(only: Term)) => unwrapped(only)
      case other => other
    }
    sealed trait Arg
    case object Plain extends Arg
    case class Displayed(nt: String) extends Arg
    case class Unwrapped(sel: Term.Select, nt: String) extends Arg
    def classify(arg: Term): Option[Arg] = unwrapped(arg) match {
      case sel @ Term.Select(q, v) if isIronValue(v) =>
        termType(q).flatMap(newtypeOf).filter(nt => asIs(norm(nt))).map(nt => Unwrapped(sel, nt))
      case other => termType(other).flatMap(newtypeOf) match {
        case Some(nt) => if (asIs(norm(nt))) Some(Displayed(nt)) else None
        case None => if (isPlain(other)) Some(Plain) else None
      }
    }

    doc.tree.collect {
      case i: Term.Interpolate if i.prefix.value == "s" && i.args.nonEmpty =>
        val args = i.args.map(a => a -> classify(a))
        val newtypes = args.collect { case (_, Some(Displayed(nt))) => nt; case (_, Some(Unwrapped(_, nt))) => nt }
        if (args.forall(_._2.isDefined) && newtypes.nonEmpty) {
          log.add(s"$path:${i.pos.startLine + 1}\t${newtypes.map(norm).distinct.mkString(",")}")
          val unwraps = args.collect { case (_, Some(Unwrapped(sel, _))) =>
            val tokens = sel.tokens
            val dot = tokens.lastIndexWhere(t => t.is[Token.Dot] && t.end <= sel.name.pos.start)
            Patch.removeTokens(tokens.drop(dot).filter(_.end <= sel.name.pos.end))
          }
          (Patch.replaceTree(i.prefix, config.interpolator) +: unwraps).asPatch.atomic
        } else Patch.empty
    }.asPatch
  }
}
