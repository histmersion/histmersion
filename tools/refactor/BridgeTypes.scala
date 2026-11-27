import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

object BridgeTypes {
  case class Alias(from: String = "", to: String = "")
  object Alias {
    implicit val surface: generic.Surface[Alias] = generic.deriveSurface[Alias]
    implicit val decoder: ConfDecoder[Alias] = generic.deriveDecoder(Alias()).noTypos
  }
  case class Config(
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}"),
      paths: List[String] = Nil,
      aliases: List[Alias] = Nil,
      aliasesFrom: String = "",
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val constructions = Set("apply", "applyUnsafe", "assume")
  def norm(sym: String): String = sym.replaceAll("[^/.#]*\\$package\\.", "")
  def split(sym: String): (String, List[String]) = {
    val n = norm(sym).stripSuffix(".")
    val i = n.lastIndexOf('/')
    (n.take(i), n.drop(i + 1).split('.').toList)
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
          if (!i.symbol.startsWith("local")) index.putIfAbsent("uri::" + i.symbol, s.SymbolInformation(symbol = i.symbol, displayName = d.uri))
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

class BridgeTypes(config: BridgeTypes.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("BridgeTypes") {
  import BridgeTypes._

  def this() = this(BridgeTypes.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("BridgeTypes")(BridgeTypes.Config()).map { cfg =>
      new BridgeTypes(cfg, buildIndex(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private val listed: List[Alias] =
    if (config.aliasesFrom.isEmpty || !Files.isRegularFile(Paths.get(config.aliasesFrom))) Nil
    else Files.readAllLines(Paths.get(config.aliasesFrom), UTF_8).asScala.toList.map(_.split("\t")).collect {
      case Array(from, to) => Alias(from, to)
    }
  private val aliasOf: Map[String, String] = (config.aliases ++ listed).map(a => norm(a.from) -> a.to).toMap
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
        case st: s.SingleType => Some(st.symbol)
        case th: s.ThisType => Some(th.symbol)
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
    def shape(obj: String): Option[(String, String, Int)] = infoS(obj).map(_.signature).collect {
      case cs: s.ClassSignature =>
        val parent = cs.parents.collectFirst { case r: s.TypeRef if r.typeArguments.size == 2 => r }
        val args = parent.toList.flatMap(_.typeArguments).map {
          case r: s.TypeRef => r.symbol
          case other => other.toString.replaceAll("\\s+", " ")
        }
        val decls = cs.declarations.map(_.symlinks.size).getOrElse(0)
        (args.headOption.getOrElse("?"), args.drop(1).headOption.getOrElse("?"), decls)
    }
    def constructedObject(fun: Term): Option[String] = fun match {
      case Term.Select(q, n) if constructions(n.value) => constructedObject(q)
      case n: Term.Name => infoS(n.symbol.value).filter(_.kind == s.SymbolInformation.Kind.OBJECT).map(_ => n.symbol.value)
      case sel: Term.Select =>
        infoS(sel.name.symbol.value).filter(_.kind == s.SymbolInformation.Kind.OBJECT).map(_ => sel.name.symbol.value)
      case _ => None
    }
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def termsIn(stats: List[Tree]): List[(String, String)] = stats.flatMap {
      case d: Defn.Object => List(d.name.value -> d.symbol.value)
      case d: Defn.Def => List(d.name.value -> d.symbol.value)
      case d: Defn.Enum => List(d.name.value -> d.symbol.value)
      case d: Defn.Class if d.mods.exists(_.is[Mod.Case]) => List(d.name.value -> d.symbol.value)
      case d: Defn.Val => d.pats.collect { case Pat.Var(n) => n.value -> n.symbol.value }
      case i: Import => i.importers.flatMap(_.importees).collect {
          case Importee.Name(n) => n.value -> n.symbol.value
          case Importee.Rename(n, r) => r.value -> n.symbol.value
        }
      case e: Export => e.importers.flatMap(_.importees).collect {
          case Importee.Name(n) => n.value -> n.symbol.value
          case Importee.Rename(n, r) => r.value -> n.symbol.value
        }
      case _ => Nil
    }
    def shadowed(t: Tree, head: String, top: String): Boolean =
      Iterator.iterate(t.parent)(_.flatMap(_.parent)).takeWhile(_.nonEmpty).flatten.exists {
        case tp: Template => termsIn(tp.stats).exists { case (n, s) => n == head && norm(s) != norm(top) }
        case pk: Pkg => termsIn(pk.stats).exists { case (n, s) => n == head && norm(s) != norm(top) }
        case src: Source => termsIn(src.stats).exists { case (n, s) => n == head && norm(s) != norm(top) }
        case o: Defn.Object => o.name.value == head && norm(o.symbol.value) != norm(top)
        case _ => false
      }
    def moduleOf(sym: String): String = infoS("uri::" + sym).map(_.displayName.split("/src/").head).getOrElse("?")
    def lineRemoval(t: Tree): Patch = {
      val tokens = doc.tree.tokens
      val first = tokens.indexWhere(_.start == t.pos.start)
      val last = tokens.lastIndexWhere(_.end == t.pos.end)
      val leading = tokens.take(first).reverse.takeWhile(tk => tk.is[Token.Whitespace]).toList
      val newline = tokens.take(first - leading.size).lastOption.filter(_.is[Token.LF]).toList
      Patch.removeTokens(newline ++ leading.reverse ++ tokens.slice(first, last + 1))
    }

    doc.tree.collect {
      case a: Term.Apply if a.argClause.values.size == 1 && constructedObject(a.fun).nonEmpty =>
        val to = constructedObject(a.fun).get
        a.argClause.values.head match {
          case Term.Select(q, v) if isIronValue(v) =>
            termType(q).flatMap(newtypeOf).filter(from => norm(from) != norm(to)).foreach { from =>
              val (fb, fc, fd) = shape(from).getOrElse(("?", "?", -1))
              val (tb, tc, _) = shape(to).getOrElse(("?", "?", -1))
              log.add(s"bridge\t${norm(from)}\t${norm(to)}\t$fb\t$tb\t$fc\t$tc\t$fd\t$path:${a.pos.startLine + 1}\t" +
                s"${moduleOf(from)}\t${moduleOf(to)}")
            }
          case _ =>
        }
        Patch.empty
      case o: Defn.Object if aliasOf.contains(norm(o.symbol.value)) =>
        val from = norm(o.symbol.value)
        val (targetPackage, targetPath) = split(aliasOf(from))
        if (o.templ.stats.nonEmpty) {
          log.add(s"skipped-body\t$from\t$path:${o.pos.startLine + 1}")
          Patch.empty
        } else if (targetPath.size < 2) {
          log.add(s"skipped-top-level-target\t$from\t$path:${o.pos.startLine + 1}")
          Patch.empty
        } else if (shadowed(o, targetPath.head, s"$targetPackage/${targetPath.head}.")) {
          log.add(s"skipped-shadowed\t$from\t$path:${o.pos.startLine + 1}")
          Patch.empty
        } else {
          val name = o.name.value
          val exported = s"export ${targetPath.mkString(".")}" + (if (targetPath.last != name) s" as $name" else "")
          val imported =
            if (split(from)._1 == targetPackage) Patch.empty
            else Patch.addGlobalImport(Symbol(s"$targetPackage/${targetPath.head}."))
          log.add(s"alias\t$from\t${aliasOf(from)}\t$path:${o.pos.startLine + 1}")
          Patch.replaceTree(o, exported) + imported
        }
      case t: Defn.Type if t.body.syntax == s"${t.name.value}.T" && aliasOf.contains(norm(t.symbol.value.stripSuffix("#") + ".")) &&
            split(aliasOf(norm(t.symbol.value.stripSuffix("#") + ".")))._2.size >= 2 &&
            t.parent.exists(_.children.exists {
              case o: Defn.Object =>
                val (targetPackage, targetPath) = split(aliasOf(norm(t.symbol.value.stripSuffix("#") + ".")))
                o.name.value == t.name.value && o.templ.stats.isEmpty &&
                  !shadowed(o, targetPath.head, s"$targetPackage/${targetPath.head}.")
              case _ => false
            }) =>
        lineRemoval(t)
    }.asPatch
  }
}
