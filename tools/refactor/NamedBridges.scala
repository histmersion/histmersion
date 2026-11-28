import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

/** Kept different-name pairs (owner 2026-11-28): a bridge `To.m(x.value)`, `x` of the newtype `from`, becomes `x.name`,
  * a named function the `from` type's companion defines (an extension, so the site needs no import). Inside that
  * companion the bridge is the function's own body and stays. `method` limits a row to one construction (apply,
  * applyUnsafe or assume); empty takes any. Sites the rule cannot type (a lambda's `_.value`) are reported as
  * `unresolved` only when their `To` matches a row, for a hand edit.
  */
object NamedBridges {
  case class Bridge(from: String = "", to: String = "", name: String = "", method: String = "")
  object Bridge {
    implicit val surface: generic.Surface[Bridge] = generic.deriveSurface[Bridge]
    implicit val decoder: ConfDecoder[Bridge] = generic.deriveDecoder(Bridge()).noTypos
  }
  case class Config(
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}"),
      bridges: List[Bridge] = Nil,
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val constructions = Set("apply", "applyUnsafe", "assume")
  def norm(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")

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

class NamedBridges(config: NamedBridges.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("NamedBridges") {
  import NamedBridges._

  def this() = this(NamedBridges.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("NamedBridges")(NamedBridges.Config()).map { cfg =>
      new NamedBridges(cfg, buildIndex(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private val rows: List[Bridge] = config.bridges.map(b => b.copy(from = norm(b.from), to = norm(b.to)))
  private val targets: Set[String] = rows.map(_.to).toSet
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
    def newtypeOf(t: s.Type): Option[String] = dealias(t) match {
      case r: s.TypeRef if r.symbol.startsWith(ironValue) && r.symbol.endsWith("#T#") => r.prefix match {
        case st: s.SingleType => Some(norm(st.symbol))
        case th: s.ThisType => Some(norm(th.symbol))
        case _ => None
      }
      case _ => None
    }
    def resultOf(sym: String): Option[s.Type] = infoS(sym).map(_.signature).collect {
      case v: s.ValueSignature => v.tpe
      case m: s.MethodSignature if m.parameterLists.isEmpty => m.returnType
    }
    def termType(t: Term): Option[s.Type] = t match {
      case n: Term.Name => resultOf(n.symbol.value)
      case sel: Term.Select => resultOf(sel.name.symbol.value)
      case a: Term.Apply => (a.fun match {
          case n: Term.Name => Some(n.symbol.value)
          case Term.Select(_, n) => Some(n.symbol.value)
          case _ => None
        }).flatMap(infoS).map(_.signature).collect {
          case m: s.MethodSignature if m.parameterLists.nonEmpty => m.returnType
        }
      case Term.Block(List(only: Term)) => termType(only)
      case _ => None
    }
    def objectOf(t: Term): Option[String] = t match {
      case n: Term.Name => infoS(n.symbol.value).filter(_.kind == s.SymbolInformation.Kind.OBJECT).map(_ => norm(n.symbol.value))
      case sel: Term.Select =>
        infoS(sel.name.symbol.value).filter(_.kind == s.SymbolInformation.Kind.OBJECT).map(_ => norm(sel.name.symbol.value))
      case _ => None
    }
    def construction(fun: Term): Option[(String, String)] = fun match {
      case Term.Select(q, n) if constructions(n.value) => objectOf(q).map(_ -> n.value)
      case other => objectOf(other).map(_ -> "apply")
    }
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def inCompanionOf(t: Tree, obj: String): Boolean =
      Iterator.iterate(t.parent)(_.flatMap(_.parent)).takeWhile(_.nonEmpty).flatten.exists {
        case o: Defn.Object => norm(o.symbol.value) == obj
        case _ => false
      }
    def receiver(q: Term): String = q match {
      case _: Term.Name | _: Term.Select | _: Term.Apply | _: Term.ApplyType | _: Term.Tuple | _: Lit => q.syntax
      case Term.Block(List(only: Term)) => receiver(only)
      case other => s"(${other.syntax})"
    }

    doc.tree.collect {
      case a: Term.Apply if a.argClause.values.size == 1 && construction(a.fun).exists(c => targets(c._1)) =>
        val (to, how) = construction(a.fun).get
        a.argClause.values.head match {
          case Term.Select(q, v) if isIronValue(v) =>
            val from = termType(q).flatMap(newtypeOf).orElse(q match {
              case c: Term.Apply => construction(c.fun).map(_._1)
              case _ => None
            })
            rows.find(r => from.contains(r.from) && r.to == to && (r.method.isEmpty || r.method == how)) match {
              case Some(r) if inCompanionOf(a, r.from) =>
                log.add(s"own-body\t${r.from}\t$to\t$path:${a.pos.startLine + 1}")
                Patch.empty
              case Some(r) =>
                log.add(s"named\t${r.from}\t$to\t${r.name}\t$how\t$path:${a.pos.startLine + 1}")
                Patch.replaceTree(a, s"${receiver(q)}.${r.name}")
              case None =>
                if (from.isEmpty) log.add(s"unresolved\t?\t$to\t$path:${a.pos.startLine + 1}\t${a.syntax.take(80)}")
                Patch.empty
            }
          case _ => Patch.empty
        }
    }.asPatch
  }
}
