import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

object RetypeToNewtype {

  case class Retype(param: String = "", newtype: String = "", tpe: String = "", wrap: String = "")
  object Retype {
    implicit val surface: generic.Surface[Retype] = generic.deriveSurface[Retype]
    implicit val decoder: ConfDecoder[Retype] = generic.deriveDecoder(Retype()).noTypos
  }

  case class Config(
      retypes: List[Retype] = Nil,
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}"),
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
  val primitives = Set("String", "Int", "Long", "Double", "Boolean")

  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
}

class RetypeToNewtype(config: RetypeToNewtype.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("RetypeToNewtype") {
  import RetypeToNewtype._

  def this() = this(RetypeToNewtype.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("RetypeToNewtype")(RetypeToNewtype.Config()).map { cfg =>
      new RetypeToNewtype(cfg, ValueIndex.build(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private def plain(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")
  private val params = config.retypes.map(r => plain(r.param) -> r).toMap
  private val methods = config.retypes.map(r => plain(r.param.take(r.param.lastIndexOf('(')))).toSet
  private object byParam {
    def contains(sym: String): Boolean = params.contains(plain(sym))
    def apply(sym: String): Retype = params(plain(sym))
    def get(sym: String): Option[Retype] = params.get(plain(sym))
  }
  private object byMethod {
    def contains(sym: String): Boolean = methods(plain(sym))
  }
  private val log = new ConcurrentLinkedQueue[String]()

  private lazy val companionsAsWritten: Map[String, String] = {
    val wanted = config.retypes.map(_.newtype).toSet
    index.keySet.asScala.iterator.filter(k => wanted(k.replaceAll("[^/.#]*\\$package\\.", ""))).map(k =>
      k.replaceAll("[^/.#]*\\$package\\.", "") -> k).toMap
  }

  override def afterComplete(): Unit =
    if (config.report.nonEmpty) {
      Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
      Files.write(Paths.get(config.report), log.asScala.toList.sorted.asJava, UTF_8)
    }

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  override def fix(implicit doc: SemanticDocument): Patch = {
    if (config.retypes.isEmpty) return Patch.empty
    val path = relative(doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case other => other.toString
    })
    def infoS(sym: String): Option[s.SymbolInformation] =
      if (sym.isEmpty) None else Option(index.get(if (sym.startsWith("local")) path + "::" + sym else sym))
    def dealias(t: s.Type, depth: Int = 0): s.Type = if (depth > 12) t else t match {
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
    def norm(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")
    def sameNewtype(a: String, b: String): Boolean = norm(a) == norm(b)
    def termNewtype(t: Term): Option[String] = (t match {
      case n: Term.Name => infoS(n.symbol.value)
      case sel: Term.Select => infoS(sel.name.symbol.value)
      case _ => None
    }).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature if m.parameterLists.isEmpty => m.returnType
    }.flatMap(newtypeOf)
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def paramsOf(method: String): List[String] = infoS(method).map(_.signature).collect {
      case m: s.MethodSignature => m.parameterLists.flatMap(_.symlinks).toList
    }.getOrElse(Nil)
    def paramNewtype(param: String): Option[String] =
      byParam.get(param).map(_.newtype).orElse(infoS(param).map(_.signature).collect { case v: s.ValueSignature => v.tpe }
        .flatMap(newtypeOf))
    def hasExtension(configured: String, op: String): Boolean = {
      val obj = companionsAsWritten.getOrElse(configured, configured)
      (List(s"$obj$op().")++ (1 to 6).map(i => s"$obj$op(+$i).")).exists(m => infoS(m).isDefined)
    }
    def symbolOf(fun: Term): String = fun match {
      case n: Term.Name => n.symbol.value
      case Term.Select(_, n) => n.symbol.value
      case Term.ApplyType(f, _) => symbolOf(f)
      case _ => ""
    }

    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    doc.tree.traverse {
      case p: Term.Param if byParam.contains(p.name.symbol.value) =>
        val r = byParam(p.name.symbol.value)
        p.decltpe.foreach { t =>
          t.collect { case n: Type.Name if primitives(n.value) => n }.headOption match {
            case Some(n) =>
              patches += Patch.replaceTree(n, r.tpe).atomic
              log.add(s"$path:${p.pos.startLine + 1}\tdeclared\t${r.param}")
            case None => log.add(s"$path:${p.pos.startLine + 1}\tnot-primitive\t${r.param}")
          }
        }
      case n: Term.Name if byParam.contains(n.symbol.value) && !n.parent.exists(_.is[Term.Param]) &&
          !n.parent.exists(_.is[Term.Assign]) =>
        val r = byParam(n.symbol.value)
        val keeps = n.parent match {
          case Some(Term.Select(q, op)) if q eq n => hasExtension(r.newtype, op.value)
          case Some(ac: Term.ArgClause) => ac.parent match {
            case Some(a: Term.Apply) =>
              val at = ac.values.indexWhere(_ eq n)
              paramsOf(symbolOf(a.fun)).lift(at).flatMap(paramNewtype).exists(sameNewtype(_, r.newtype))
            case _ => false
          }
          case _ => false
        }
        if (!keeps) {
          val spliced = n.parent.exists(_.is[Term.Interpolate])
          patches += (if (spliced) Patch.addLeft(n, "{") + Patch.addRight(n, ".value}") else Patch.addRight(n, ".value")).atomic
          log.add(s"$path:${n.pos.startLine + 1}\tunwrapped-in-body\t${r.param}")
        }
      case a: Term.Apply if byMethod.contains(symbolOf(a.fun)) =>
        val params = paramsOf(symbolOf(a.fun))
        a.argClause.values.zipWithIndex.foreach { case (arg, i) =>
          val (value, param) = arg match {
            case Term.Assign(Term.Name(name), v) => v -> params.find(_.endsWith(s"($name)"))
            case v => v -> params.lift(i)
          }
          param.flatMap(byParam.get).foreach { r =>
            value match {
              case sel @ Term.Select(q, v) if isIronValue(v) && termNewtype(q).exists(sameNewtype(_, r.newtype)) =>
                patches += Patch.removeTokens(sel.tokens.filter(_.start >= q.pos.end)).atomic
                log.add(s"$path:${a.pos.startLine + 1}\tcall-unwrap-removed\t${r.param}")
              case other =>
                patches += Patch.addLeft(other, s"${r.wrap}(").atomic + Patch.addRight(other, ")").atomic
                log.add(s"$path:${a.pos.startLine + 1}\tcall-wrapped\t${r.param}")
            }
          }
        }
    }
    patches.result().asPatch
  }
}

object ValueIndex {
  def build(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = {
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
}
