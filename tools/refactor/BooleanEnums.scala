import scalafix.v1._
import scala.meta._
import scala.meta.internal.{semanticdb => s}
import metaconfig.{ConfDecoder, Configured, generic}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

object BooleanEnums {
  case class Two(newtype: String = "", yes: String = "", no: String = "", test: String = "")
  object Two {
    implicit val surface: generic.Surface[Two] = generic.deriveSurface[Two]
    implicit val decoder: ConfDecoder[Two] = generic.deriveDecoder(Two()).noTypos
  }
  case class Config(
      semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}"),
      paths: List[String] = Nil,
      enums: List[Two] = Nil,
      report: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val ironValue = "io/github/iltotore/iron/"
  val newtypeBase = "com/histmersion/Newtype#"
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

class BooleanEnums(config: BooleanEnums.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("BooleanEnums") {
  import BooleanEnums._

  def this() = this(BooleanEnums.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("BooleanEnums")(BooleanEnums.Config()).map { cfg =>
      new BooleanEnums(cfg, buildIndex(cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))))
    }

  private val byType: Map[String, Two] = config.enums.map(e => norm(e.newtype) -> e).toMap
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
    def isIronValue(n: Name): Boolean = n.value == "value" && n.symbol.value.startsWith(ironValue)
    def constructed(fun: Term): Option[Two] = fun match {
      case n: Term.Name => byType.get(norm(n.symbol.value))
      case Term.Select(q, n) if n.value == "apply" => constructed(q)
      case sel: Term.Select => byType.get(norm(sel.name.symbol.value))
      case _ => None
    }
    def prefixOf(fun: Term): String = fun match {
      case Term.Select(q, n) if n.value == "apply" => q.syntax
      case other => other.syntax
    }
    def indentOf(t: Tree): String = {
      val text = doc.input.text
      val lineStart = text.lastIndexOf('\n', t.pos.start - 1) + 1
      text.substring(lineStart, t.pos.start).takeWhile(_ == ' ')
    }
    def lineRemoval(t: Tree): Patch = {
      val tokens = doc.tree.tokens
      val first = tokens.indexWhere(_.start == t.pos.start)
      val last = tokens.lastIndexWhere(_.end == t.pos.end)
      val leading = tokens.take(first).reverse.takeWhile(tk => tk.is[Token.Whitespace]).toList
      val newline = tokens.take(first - leading.size).lastOption.filter(_.is[Token.LF]).toList
      Patch.removeTokens(newline ++ leading.reverse ++ tokens.slice(first, last + 1))
    }

    def definition(o: Defn.Object, e: Two): Patch = {
      val ind = indentOf(o)
      val name = o.name.value
      val tokens = o.tokens
      val initEnd = o.templ.inits.last.pos.end
      val header = tokens.filter(_.end <= initEnd)
      val ext = s"extension (${name.head.toLower}${name.tail}: $name) def ${e.test}: Boolean = ${name.head.toLower}${name.tail} == ${e.yes}"
      val enumText = s"enum $name derives CanEqual:\n$ind  case ${e.yes}, ${e.no}\n$ind"
      log.add(s"$path:${o.pos.startLine + 1}\tdefinition\t${e.newtype}")
      def replaced(text: String): Patch = Patch.removeTokens(header) + Patch.addLeft(header.head, text)
      if (o.templ.stats.isEmpty) replaced(s"${enumText}object $name:\n$ind  $ext")
      else {
        val colon = tokens.find(tk => tk.start >= initEnd && tk.is[Token.Colon])
        replaced(s"${enumText}object $name") + colon.fold(Patch.empty)(c => Patch.addRight(c, s"\n$ind  $ext"))
      }
    }

    doc.tree.collect {
      case o: Defn.Object if byType.contains(norm(o.symbol.value)) && o.templ.inits.exists(_.tpe.syntax.startsWith("Newtype[Boolean")) =>
        definition(o, byType(norm(o.symbol.value)))
      case t: Defn.Type if t.body.syntax == s"${t.name.value}.T" && byType.contains(norm(t.symbol.value.stripSuffix("#") + ".")) =>
        lineRemoval(t)
      case sel @ Term.Select(q, v) if isIronValue(v) && termType(q).flatMap(newtypeOf).exists(byType.contains) =>
        val e = byType(termType(q).flatMap(newtypeOf).get)
        log.add(s"$path:${sel.pos.startLine + 1}\tunwrap\t${e.newtype}")
        Patch.replaceTree(v, e.test)
      case a: Term.Apply if a.argClause.values.size == 1 && constructed(a.fun).nonEmpty =>
        val e = constructed(a.fun).get
        val insideOwn = Iterator.iterate(a.parent)(_.flatMap(_.parent)).takeWhile(_.nonEmpty).flatten.exists {
          case o: Defn.Object => norm(o.symbol.value) == norm(e.newtype)
          case _ => false
        }
        val p = prefixOf(a.fun)
        val (yes, no) = if (insideOwn) (e.yes, e.no) else (s"$p.${e.yes}", s"$p.${e.no}")
        log.add(s"$path:${a.pos.startLine + 1}\tconstruct\t${e.newtype}")
        a.argClause.values.head match {
          case Lit.Boolean(true) => Patch.replaceTree(a, yes)
          case Lit.Boolean(false) => Patch.replaceTree(a, no)
          case other => Patch.replaceTree(a, s"(if ${other.syntax} then $yes else $no)")
        }
    }.asPatch
  }
}
