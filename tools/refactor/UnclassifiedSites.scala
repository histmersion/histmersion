import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.meta.internal.{semanticdb => s}
import scalafix.lint.LintSeverity

/** Read-only inventory of what G60 has left to classify (speed plan C, `UnclassifiedSites`): every construction of the
  * temporary `Problem.Unclassified` (`G60fromString`), every read of `legacyText`, and every definition whose failure is
  * a String (`Either[String, _]` as its declared result). One row per site:
  *
  *   kind, scope, module, dir, path, line, enclosing, shape, wrapper, template, splices, extra
  *
  *  - `kind`: `Unclassified`, `legacyText` or `Either[String]`;
  *  - `dir`: the module and the first two packages under `com/histmersion` (`server:domain/library`): the batch (D);
  *  - `shape` (unclassified only), the first that applies: `relay` (splices another problem's `.sentence` or
  *    `.legacyText`), `getMessage` (an exception's message: `getMessage`, `Unclassified.of(e)`, `Legacy.Text.of(e)`),
  *    `exception` (splices a Throwable), `lifted` (the text is a lambda's parameter: `.map(e => Unclassified(...e))`),
  *    `multi-line`, `interpolation`, `literal`, `expression`;
  *  - `wrapper`: the call around the construction (`Left`, `Api.Error`, `Cli.Error`, `.left.map(λ)`, ...);
  *  - `template`: the text, splices marked `{expr}` (as SentenceSites writes it);
  *  - `splices`: each splice with the type SemanticDB records for its name (`p: Path`), `?` when it records none;
  *  - `extra`: for `lifted`, the call the lambda's text comes from (`lifted-from=<symbol>`); for `Either[String]` its type.
  *
  * With `summary`, it also writes the counts by scope, module and directory, by kind and shape. With `emitLints` each row
  * is a lint `UnclassifiedSites.<kind>` (the fixtures' check), and with `expected` the rows must be that file's.
  */
object UnclassifiedSites {
  case class Config(
    report: String = "refactor-audit/g60/unclassified.tsv",
    summary: String = "refactor-audit/g60/unclassified-summary.tsv",
    roots: List[String] = List("modules"),
    testMarker: String = "/src/test/",
    exclude: List[String] = List("modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala"),
    unclassified: String = "com/histmersion/util/problem/Problem.Unclassified.",
    legacyText: String = "com/histmersion/util/problem/Problem.legacyText(",
    legacy: String = "com/histmersion/util/problem/Legacy.Text.",
    semanticdbRoots: List[String] = List("target/out/jvm/scala-3.10.0", "target/out/sjs1/scala-3.10.0"),
    emitLints: Boolean = false,
    expected: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  case class Row(kind: String, scope: String, module: String, dir: String, path: String, line: Int, enclosing: String,
      shape: String, wrapper: String, template: String, splices: String, extra: String) {
    def tsv: String = List(kind, scope, module, dir, path, line.toString, enclosing, shape, wrapper, template, splices, extra)
      .map(clean).mkString("\t")
  }
  val Header: String = "kind\tscope\tmodule\tdir\tpath\tline\tenclosing\tshape\twrapper\ttemplate\tsplices\textra"
  def clean(text: String): String = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")

  /** The counts of the rows by scope, module and directory, then kind and shape: the batches of speed plan D. */
  def summary(rows: List[Row]): List[String] = {
    val head = "scope\tmodule\tdir\tkind\tshape\tcount"
    def group(rs: List[Row], key: Row => (String, String, String, String, String)) =
      rs.groupBy(key).toList.map { case (k, v) => (k, v.size) }.sortBy { case (k, n) => (k._1, k._2, -n, k._3, k._4, k._5) }
    val byModule = group(rows, r => (r.scope, r.module, "*", r.kind, "*"))
    val byDir = group(rows, r => (r.scope, r.module, r.dir, r.kind, "*"))
    val byShape = group(rows, r => (r.scope, r.module, "*", r.kind, r.shape))
    head :: (byModule ++ byDir ++ byShape).map { case ((a, b, c, d, e), n) => s"$a\t$b\t$c\t$d\t$e\t$n" }
  }

  private val indexes = new ConcurrentHashMap[List[String], ConcurrentHashMap[String, s.SymbolInformation]]()
  def index(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
  /** The symbols of every current SemanticDB document under `roots` (a document counts when its md5 is the file's). */
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
  private[UnclassifiedSites] val rows = new ConcurrentLinkedQueue[Row]()
}

class UnclassifiedSites(config: UnclassifiedSites.Config, index: ConcurrentHashMap[String, s.SymbolInformation])
    extends SemanticRule("UnclassifiedSites") {
  import UnclassifiedSites._
  def this() = this(UnclassifiedSites.Config(), new ConcurrentHashMap())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("UnclassifiedSites")(Config()).map(cfg => new UnclassifiedSites(cfg, UnclassifiedSites.index(cfg.semanticdbRoots)))

  override def beforeStart(): Unit = rows.clear()

  override def afterComplete(): Unit = {
    val all = rows.asScala.toList.sortBy(r => (r.path, r.line, r.kind))
    val lines = Header :: all.map(_.tsv)
    def write(file: String, content: List[String]): Unit = if (file.nonEmpty) {
      val p = Paths.get(file)
      Option(p.getParent).foreach(Files.createDirectories(_))
      Files.write(p, content.asJava, UTF_8)
    }
    write(config.report, lines)
    write(config.summary, summary(all))
    if (config.expected.nonEmpty) {
      // empty last cells are trailing tabs, which an editor may strip from the expected file
      def trimmed(l: String) = l.replaceAll("\\s+$", "")
      val want = Files.readAllLines(Paths.get(config.expected), UTF_8).asScala.toList.filter(_.nonEmpty).map(trimmed)
      val got = lines.map(trimmed)
      val missing = want.filterNot(got.contains)
      val extra = got.filterNot(want.contains)
      require(missing.isEmpty && extra.isEmpty,
        s"rows differ from ${config.expected}\nmissing:\n${missing.mkString("\n")}\nunexpected:\n${extra.mkString("\n")}")
    }
  }

  // ---- types as SemanticDB records them ----------------------------------------------------------------------------
  private def display(sym: String): String = {
    val trimmed = sym.stripSuffix("#").stripSuffix(".")
    val last = trimmed.split("[/.#]").lastOption.getOrElse(trimmed)
    if (last == "T" || last == "Type") trimmed.split("[/.#]").dropRight(1).lastOption.getOrElse(last) else last
  }
  private def show(t: s.Type): String = t match {
    case r: s.TypeRef => display(r.symbol) + (if (r.typeArguments.isEmpty) "" else r.typeArguments.map(show).mkString("[", ", ", "]"))
    case st: s.SingleType => display(st.symbol) + ".type"
    case a: s.AnnotatedType => show(a.tpe)
    case b: s.ByNameType => show(b.tpe)
    case w: s.WithType => w.types.map(show).mkString(" & ")
    case u: s.UnionType => u.types.map(show).mkString(" | ")
    case _ => "?"
  }
  /** The file as SemanticDB names it (relative to the source root): `modules/...`. */
  private def relative(doc: SemanticDocument): String = {
    val abs = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    config.roots.map(r => abs.indexOf(r + "/")).filter(_ >= 0).sorted.headOption.map(abs.substring).getOrElse(abs)
  }
  private def info(sym: Symbol)(implicit doc: SemanticDocument): Option[s.SymbolInformation] =
    if (sym.isNone) None
    else if (sym.isLocal) Option(index.get(relative(doc) + "::" + sym.value))
    else Option(index.get(sym.value))
  private def typeOf(sym: Symbol)(implicit doc: SemanticDocument): Option[s.Type] = info(sym).map(_.signature).collect {
    case v: s.ValueSignature => v.tpe
    case m: s.MethodSignature => m.returnType
  }
  private def symbolOf(t: Tree)(implicit doc: SemanticDocument): Symbol = t match {
    case n: Term.Name => n.symbol
    case Term.Select(_, n) => n.symbol
    case a: Term.Apply => symbolOf(a.fun)
    case Term.ApplyType.After_4_6_0(f, _) => symbolOf(f)
    case _ => Symbol.None
  }
  private def termType(t: Term)(implicit doc: SemanticDocument): Option[String] = t match {
    case Term.Block(List(only: Term)) => termType(only)
    case _ => typeOf(symbolOf(t)).map(show).filter(_ != "?")
  }
  private def throwable(t: Term)(implicit doc: SemanticDocument): Boolean =
    termType(t).exists(n => n.endsWith("Throwable") || n.endsWith("Exception") || n.endsWith("Error"))

  // ---- templates (as SentenceSites writes them) --------------------------------------------------------------------
  private def splice(t: Tree): String = "{" + t.syntax.replaceAll("\\s+", " ").replace('{', '(').replace('}', ')') + "}"
  private def literal(text: String): String = text.replace("{", "{{").replace("}", "}}")
  private def spliced(t: Term): List[Term] = t match {
    case i: Term.Interpolate => i.args.map { case Term.Block(List(only: Term)) => only; case o => o }
    case inf: Term.ApplyInfix if inf.op.value == "+" => spliced(inf.lhs) ++ inf.argClause.values.flatMap(spliced)
    case Term.Select(i: Term.Interpolate, Term.Name("stripMargin")) => spliced(i)
    case Term.Block(List(only: Term)) => spliced(only)
    case _: Lit.String => Nil
    case other => List(other)
  }
  def template(t: Term): String = t match {
    case Lit.String(v) => literal(v)
    case i: Term.Interpolate if Set("s", "f", "raw")(i.prefix.value) =>
      def read(v: String): String =
        if (i.prefix.value == "raw") v.replace("$$", "$")
        else scala.util.Try(StringContext.processEscapes(v.replace("$$", "$"))).getOrElse(v.replace("$$", "$"))
      val parts = i.parts.map { case Lit.String(v) => literal(read(v)); case o => literal(o.syntax) }
      parts.zipAll(i.args.map {
        case Term.Block(List(only: Term)) => splice(only)
        case other => splice(other)
      }, "", "").map { case (p, a) => p + a }.mkString
    case inf: Term.ApplyInfix if inf.op.value == "+" && inf.argClause.values.size == 1 =>
      template(inf.lhs) + template(inf.argClause.values.head)
    case Term.Block(List(only: Term)) => template(only)
    case Term.Select(i: Term.Interpolate, Term.Name("stripMargin")) =>
      template(i.copy(parts = i.parts.zipWithIndex.map {
        case (Lit.String(v), 0) => Lit.String(v.stripMargin)
        case (Lit.String(v), _) => Lit.String(("x" + v).stripMargin.drop(1))
        case (other, _) => other
      }))
    case other => splice(other)
  }

  // ---- places ------------------------------------------------------------------------------------------------------
  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def enclosing(t: Tree, file: String)(implicit doc: SemanticDocument): String = {
    def named(a: Tree): Option[Symbol] = a match {
      case d: Defn.Def => Some(d.name.symbol)
      case d: Defn.Val => d.pats.collectFirst { case Pat.Var(n) => n.symbol }
      case d: Defn.Var => d.pats.collectFirst { case Pat.Var(n) => n.symbol }
      case d: Defn.Given => Some(d.name.symbol)
      case d: Defn.GivenAlias => Some(d.name.symbol)
      case d: Defn.Object => Some(d.name.symbol)
      case d: Defn.Class => Some(d.name.symbol)
      case d: Defn.Trait => Some(d.name.symbol)
      case d: Defn.Enum => Some(d.name.symbol)
      case _ => None
    }
    ancestors(t).iterator.flatMap(a => named(a).toList).find(s => !s.isNone && !s.isLocal).map(_.value).getOrElse(file)
  }
  /** The call around a construction: its function's name (`Left`, `Api.Error`), or the method a lambda is given to. */
  private def wrapper(t: Tree): String = {
    def name(fun: Term): String = fun match {
      case Term.Select(q @ Term.Select(_, Term.Name("left")), n) => s".left.${n.value}"
      case Term.Select(Term.Name(o), n) if o.headOption.exists(_.isUpper) => s"$o.${n.value}"
      case Term.Select(_, n) => s".${n.value}"
      case n: Term.Name => n.value
      case Term.ApplyType.After_4_6_0(f, _) => name(f)
      case a: Term.Apply => name(a.fun)
      case other => other.syntax.take(30)
    }
    def up(node: Tree, lambda: Boolean): String = node.parent match {
      case Some(a: Term.ArgClause) => a.parent match {
        case Some(app: Term.Apply) => name(app.fun) + (if (lambda) "(λ)" else "")
        case Some(other) => up(other, lambda)
        case None => "-"
      }
      case Some(f: Term.Function) => up(f, lambda = true)
      case Some(f: Term.AnonymousFunction) => up(f, lambda = true)
      case Some(b: Term.Block) => up(b, lambda)
      case Some(i: Term.If) => up(i, lambda)
      case Some(c: Case) => c.parent.flatMap(_.parent).map(up(_, lambda)).getOrElse("-")
      case Some(m: Term.Match) => up(m, lambda)
      case Some(inf: Term.ApplyInfix) => s"${inf.op.value}"
      case Some(_: Defn.Def) | Some(_: Defn.Val) | Some(_: Defn.Var) => "result"
      case Some(other) => other.productPrefix
      case None => "-"
    }
    up(t, lambda = false)
  }
  /** The lambda whose parameter the text is, and the call the lambda is given to: `.map(e => ...)` on `parse(x)`. */
  private def liftedFrom(text: Term, at: Tree)(implicit doc: SemanticDocument): Option[String] = text match {
    case n: Term.Name if n.symbol.isLocal => ancestors(at).collectFirst {
        case f: Term.Function if f.paramClause.values.exists(p => p.name.value == n.value) => f
      }.flatMap(f => f.parent.flatMap(_.parent)).collect {
        case app: Term.Apply => app.fun match {
          case Term.Select(Term.Select(recv, Term.Name("left")), _) => recv
          case Term.Select(recv, _) => recv
          case other => other
        }
      }.map(recv => symbolOf(recv) match { case s if !s.isNone => s.value; case _ => recv.syntax.replaceAll("\\s+", " ").take(80) })
    case _ => None
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val abs = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    val file = config.roots.map(r => abs.indexOf(r + "/")).filter(_ >= 0).sorted.headOption.map(abs.substring).getOrElse(abs)
    if (config.exclude.exists(file.endsWith)) return Patch.empty
    val scope = if (file.contains(config.testMarker)) "test" else "main"
    val module = file.stripPrefix("modules/").split("/src/").head
    val dir = {
      val pkgs = file.split("/").toList.dropWhile(_ != "histmersion").drop(1).dropRight(1)
      s"$module:${pkgs.take(2).mkString("/")}"
    }
    val found = scala.collection.mutable.ListBuffer.empty[(Row, Tree)]
    def add(kind: String, t: Tree, shape: String, wrap: String, tmpl: String, spl: String, extra: String): Unit =
      found += Row(kind, scope, module, dir, file, t.pos.startLine + 1, enclosing(t, file), shape, wrap, tmpl, spl, extra) -> t

    def isUnclassified(fun: Term): Boolean = symbolOf(fun).value.startsWith(config.unclassified)
    doc.tree.traverse {
      case a: Term.Apply if isUnclassified(a.fun) && !ancestors(a).exists(_.is[Import]) &&
          !ancestors(a).exists { case c: Case => c.pat.pos.start <= a.pos.start && a.pos.end <= c.pat.pos.end; case _ => false } =>
        val viaOf = symbolOf(a.fun).value.endsWith("Unclassified.of().")
        val inner = a.argClause.values.headOption
        val (text, ofThrown) = inner match {
          case Some(i: Term.Apply) if symbolOf(i.fun).value.startsWith(config.legacy + "of(") => (i.argClause.values.headOption, true)
          case Some(i: Term.Apply) if symbolOf(i.fun).value.startsWith(config.legacy) => (i.argClause.values.headOption, false)
          case other => (other, viaOf)
        }
        val tmpl = text.map(t => if (ofThrown) splice(Term.Select(t, Term.Name("getMessage"))) else template(t)).getOrElse("-")
        val splices = text.toList.flatMap(t => if (ofThrown) List(t) else spliced(t))
        val spl = splices.map(t => s"${t.syntax.replaceAll("\\s+", " ")}: ${termType(t).getOrElse("?")}").mkString("; ")
        val lifted = text.filter(_ => !ofThrown).flatMap(liftedFrom(_, a))
        val relay = splices.exists(_.collect { case Term.Name(n) if n == "sentence" || n == "legacyText" || n == "sentences" => n }.nonEmpty)
        val message = ofThrown || splices.exists(_.collect { case Term.Name("getMessage") => () }.nonEmpty)
        val shape =
          if (relay) "relay"
          else if (message) "getMessage"
          else if (splices.exists(throwable)) "exception"
          else if (lifted.nonEmpty) "lifted"
          else if (text.exists(t => t.pos.startLine != t.pos.endLine)) "multi-line"
          else text match {
            case Some(_: Lit.String) => "literal"
            case Some(_: Term.Interpolate) | Some(_: Term.ApplyInfix) => "interpolation"
            case _ => "expression"
          }
        add("Unclassified", a, shape, wrapper(a), tmpl, spl, lifted.map("lifted-from=" + _).getOrElse(""))
      case sel @ Term.Select(_, n) if n.symbol.value.startsWith(config.legacyText) && !ancestors(sel).exists(_.is[Import]) =>
        add("legacyText", sel, "-", wrapper(sel), "-", "", "")
      case d: Defn.Def if d.decltpe.exists(isEitherString) =>
        add("Either[String]", d.name, "-", "-", "-", "", d.decltpe.map(_.syntax).getOrElse(""))
      case d: Defn.Val if d.decltpe.exists(isEitherString) =>
        add("Either[String]", d.pats.head, "-", "-", "-", "", d.decltpe.map(_.syntax).getOrElse(""))
    }
    found.foreach { case (r, _) => rows.add(r) }
    if (!config.emitLints) Patch.empty
    else Patch.fromIterable(found.toList.map { case (r, t) =>
      val id = r.kind match { case "Either[String]" => "string"; case k => k }
      Patch.lint(Diagnostic(id, s"${r.shape} ${r.wrapper} ${r.template} [${r.splices}] ${r.extra}", t.pos, severity = LintSeverity.Info))
    })
  }

  /** `Either[String, A]` as written (the failure a bare String). */
  private def isEitherString(t: Type): Boolean = t match {
    case Type.Apply.After_4_6_0(Type.Name("Either"), args) => args.values.headOption.exists {
        case Type.Name("String") => true
        case _ => false
      }
    case _ => false
  }
}
