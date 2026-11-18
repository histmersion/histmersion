import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.meta.internal.{semanticdb => s}
import scalafix.lint.LintSeverity

/** Read-only: every place in main code that builds a problem a user reads (G60, the sentence ledger, speed plan B).
  *
  * A site is the outermost construction of a case of the closed `util.problem.Problem` (`Problem.Invalid.Missing(f)`, a
  * companion helper such as `Missing.here(f)`, a case object such as `Problem.Run.Interrupted`), or of the temporary
  * `Problem.Unclassified`. One row per site:
  *
  *   key, module, path, line, enclosing, site, variants, template
  *
  *  - `enclosing` is the SemanticDB symbol of the nearest named definition around it (a method, a val, an object): it
  *    survives moved lines and moved files, unlike a line number;
  *  - `key` is `<enclosing>#<n>`, the n-th site (from 0) in that definition;
  *  - `site` is the case (`Invalid.Missing`), or `Unclassified`;
  *  - `variants` are the nested vocabulary values written in the construction (`Config.Fault.Empty`), sorted, `;`-joined:
  *    they pick the examples of the case whose sentence is this site's;
  *  - `template` is, for an unclassified site, its text with every splice marked `{expr}` (a literal `{` or `}` doubled);
  *    `-` for a case.
  *
  * SentenceLedgerSuite (server tests) compares the rows with `architecture/sentences.tsv`. The rule never patches; with
  * `emitLints` it reports each row as a lint (`SentenceSites.unclassified` / `SentenceSites.case`), and with `expected` it
  * fails unless the rows are exactly that file's (the fixtures' check).
  */
object SentenceSites {
  case class Config(
    report: String = "target/guidelines/sentence-sites.tsv",
    roots: List[String] = List("modules"),
    mainMarker: String = "/src/main/",
    // the module's own cases, examples and copies (`within`, `inside`) and the renderer's: no site a user's sentence comes from
    exclude: List[String] = List("modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala",
      "modules/problem/src/main/scala/com/histmersion/util/problem/Text.scala"),
    problemRoot: String = "com/histmersion/util/problem/Problem.",
    // `{scalaVersion}` is Scalafix's --scala-version: the build's (target/build-versions.properties)
    problemSemanticdb: List[String] = List("target/out/jvm/scala-{scalaVersion}/histmersion-problem/meta"),
    newtypeParent: String = "com/histmersion/Newtype#",
    emitLints: Boolean = false,
    expected: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  case class Row(key: String, module: String, path: String, line: Int, enclosing: String, site: String, variants: String, template: String) {
    def cells: List[String] = List(key, module, path, line.toString, enclosing, site, variants, template)
    def tsv: String = cells.map(clean).mkString("\t")
  }
  val Header: String = List("key", "module", "path", "line", "enclosing", "site", "variants", "template").mkString("\t")

  /** A cell on one line: a tab or a line break in the text is written `\t`, `\n`. */
  def clean(text: String): String = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")

  /** The symbols of the problem module's own SemanticDB (the cases, the groups, the newtypes), by symbol. */
  private val indexes = new ConcurrentHashMap[List[String], Map[String, s.SymbolInformation]]()
  def index(roots: List[String]): Map[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => buildIndex(r))
  private def buildIndex(roots: List[String]): Map[String, s.SymbolInformation] = {
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList finally stream.close()
    }
    files.flatMap(f => s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.flatMap(_.symbols)).map(i => i.symbol -> i).toMap
  }

  private[SentenceSites] val rows = new ConcurrentLinkedQueue[Row]()
}

class SentenceSites(config: SentenceSites.Config, index: Map[String, s.SymbolInformation]) extends SemanticRule("SentenceSites") {
  import SentenceSites._
  def this() = this(SentenceSites.Config(), Map.empty)

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("SentenceSites")(Config()).map(cfg => new SentenceSites(cfg, SentenceSites.index(cfg.problemSemanticdb.map(_.replace("{scalaVersion}", c.scalaVersion)))))

  override def beforeStart(): Unit = rows.clear()

  override def afterComplete(): Unit = {
    val all = rows.asScala.toList.sortBy(r => (r.path, r.line, r.key))
    val lines = Header :: all.map(_.tsv)
    if (config.report.nonEmpty) {
      val p = Paths.get(config.report)
      Option(p.getParent).foreach(Files.createDirectories(_))
      Files.write(p, lines.asJava, UTF_8)
    }
    if (config.expected.nonEmpty) {
      val want = Files.readAllLines(Paths.get(config.expected), UTF_8).asScala.toList.filter(_.nonEmpty)
      val missing = want.filterNot(lines.contains)
      val extra = lines.filterNot(want.contains)
      require(missing.isEmpty && extra.isEmpty,
        s"rows differ from ${config.expected}\nmissing:\n${missing.mkString("\n")}\nunexpected:\n${extra.mkString("\n")}")
    }
  }

  private val Root = config.problemRoot
  private val ProblemTrait = Root.stripSuffix(".") + "#"

  /** The parents of a class or object of the problem module. */
  private def parents(sym: String): List[String] = index.get(sym).map(_.signature).toList.flatMap {
    case c: s.ClassSignature => c.parents.toList.collect { case r: s.TypeRef => r.symbol }
    case _ => Nil
  }
  /** A group is a sealed trait directly under `Problem` that extends it. */
  private def isGroup(sym: String): Boolean =
    index.get(sym).exists(_.kind == s.SymbolInformation.Kind.TRAIT) && parents(sym).contains(ProblemTrait)
  /** A case is a class or an object that extends `Problem` itself (`Unclassified`) or one of its groups. */
  private def isCase(sym: String): Boolean =
    !isGroup(sym) && parents(sym).exists(p => p == ProblemTrait || isGroup(p))
  private def isNewtype(sym: String): Boolean = parents(sym).contains(config.newtypeParent)

  /** The components of a symbol under `Problem.`, up to the first member that is not a type or object (`apply()`). */
  private def path(sym: String): Option[List[String]] =
    if (!sym.startsWith(Root)) None
    else {
      val parts = sym.stripPrefix(Root).split("[.#]").toList.filter(_.nonEmpty)
      val named = parts.takeWhile(p => p.headOption.exists(_.isUpper) && !p.contains("("))
      if (named.isEmpty) None else Some(named)
    }
  /** The case a symbol builds: the shortest prefix of its path that is a case class or a case object. */
  private def caseOf(sym: String): Option[String] = path(sym).flatMap { parts =>
    (1 to parts.size).iterator.map(n => parts.take(n)).collectFirst {
      case p if isCase(Root + p.mkString(".") + "#") || isCase(Root + p.mkString(".") + ".") => p.mkString(".")
    }
  }
  /** A nested value of the vocabulary written in a construction (`Config.Fault.Empty`): not a newtype, not a group. */
  private def variantOf(sym: String): Option[String] = path(sym).filter { parts =>
    val name = Root + parts.mkString(".")
    !isNewtype(name + ".") && !isGroup(name + "#") && parts.size > 1
  }.map(_.mkString("."))

  private def symbolOf(t: Tree)(implicit doc: SemanticDocument): Option[String] = (t match {
    case n: Term.Name => Some(n.symbol)
    case Term.Select(_, n) => Some(n.symbol)
    case Term.ApplyType.After_4_6_0(f, _) => return symbolOf(f)
    case _ => None
  }).map(_.value).filter(_.nonEmpty)

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))

  /** Is `t` a value written in an expression (not a pattern, a type, an import, a qualifier or a called function)? */
  private def inExpression(t: Tree): Boolean = {
    // a name and a selection are also patterns in Scalameta's trees: a pattern position is told by where it is written
    def within(p: Tree): Boolean = p.pos.start <= t.pos.start && t.pos.end <= p.pos.end
    !ancestors(t).exists {
      case _: Import | _: Type => true
      case c: Case => within(c.pat)
      case v: Defn.Val => v.pats.exists(within)
      case v: Defn.Var => v.pats.exists(within)
      case g: Enumerator.Generator => within(g.pat)
      case g: Enumerator.CaseGenerator => within(g.pat)
      case g: Enumerator.Val => within(g.pat)
      case _ => false
    }
  }

  /** The text of a splice on one line, its braces as parentheses (so a template's `{…}` marks stay unambiguous). */
  private def splice(t: Tree): String = "{" + t.syntax.replaceAll("\\s+", " ").replace('{', '(').replace('}', ')') + "}"
  private def literal(text: String): String = text.replace("{", "{{").replace("}", "}}")

  /** The text an argument of `Legacy.Text(...)` writes, with its splices marked. */
  def template(t: Term): String = t match {
    case Lit.String(v) => literal(v)
    case i: Term.Interpolate if Set("s", "f", "raw")(i.prefix.value) =>
      // a part is source text: `$$` and the escapes an `s` or `f` string processes are read as it reads them
      def read(v: String): String =
        if (i.prefix.value == "raw") v.replace("$$", "$")
        else scala.util.Try(StringContext.processEscapes(v.replace("$$", "$"))).getOrElse(v.replace("$$", "$"))
      val parts = i.parts.map { case Lit.String(v) => literal(read(v)); case o => literal(o.syntax) }
      parts.zipAll(i.args.map(a => a match {
        case Term.Block(List(only: Term)) => splice(only)
        case other => splice(other)
      }), "", "").map { case (p, a) => p + a }.mkString
    case inf: Term.ApplyInfix if inf.op.value == "+" && inf.argClause.values.size == 1 =>
      template(inf.lhs) + template(inf.argClause.values.head)
    case Term.Block(List(only: Term)) => template(only)
    case Term.Select(i: Term.Interpolate, Term.Name("stripMargin")) =>
      // each literal part as `stripMargin` leaves it; a part after a splice does not start a line
      val stripped = i.copy(parts = i.parts.zipWithIndex.map {
        case (Lit.String(v), 0) => Lit.String(v.stripMargin)
        case (Lit.String(v), _) => Lit.String(("x" + v).stripMargin.drop(1))
        case (other, _) => other
      })
      template(stripped)
    case other => splice(other)
  }

  /** The template of an unclassified construction's argument: `Legacy.Text(x)`, `Legacy.Text.of(e)`, anything else. */
  private def unclassifiedTemplate(a: Term.Apply, fnSym: String)(implicit doc: SemanticDocument): String = {
    val arg = a.argClause.values.headOption
    if (fnSym.endsWith("Unclassified.of().")) arg.map(e => splice(Term.Select(e, Term.Name("getMessage")))).getOrElse("-")
    else arg match {
      case Some(inner: Term.Apply) => symbolOf(inner.fun) match {
        case Some(sym) if sym.contains("util/problem/Legacy.Text.of(") =>
          inner.argClause.values.headOption.map(e => splice(Term.Select(e, Term.Name("getMessage")))).getOrElse("-")
        case Some(sym) if sym.contains("util/problem/Legacy.Text.") && inner.argClause.values.size == 1 =>
          template(inner.argClause.values.head)
        case _ => splice(inner)
      }
      case Some(other) => splice(other)
      case None => "-"
    }
  }

  /** The nearest named definition around a tree: its SemanticDB symbol. */
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

  override def fix(implicit doc: SemanticDocument): Patch = {
    val abs = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    val file = config.roots.map(r => abs.indexOf(r + "/")).filter(_ >= 0).sorted.headOption.map(abs.substring).getOrElse(abs)
    val inScope = (config.mainMarker.isEmpty || file.contains(config.mainMarker)) && !config.exclude.exists(file.endsWith)
    if (!inScope) return Patch.empty
    val module = file.stripPrefix("modules/").split("/src/").head

    case class Found(tree: Tree, site: String, template: String)
    val found = scala.collection.mutable.ListBuffer.empty[Found]
    doc.tree.traverse {
      case a: Term.Apply if inExpression(a) => symbolOf(a.fun).flatMap(caseOf).foreach { c =>
        found += Found(a, c, if (c == "Unclassified") unclassifiedTemplate(a, symbolOf(a.fun).get) else "-")
      }
      case n: Term.Name if inExpression(n) && !n.parent.exists {
            case Term.Select(_, name) => name ne n
            case _ => false
          } && !n.parent.exists { case a: Term.Apply => a.fun eq n; case _ => false } &&
          !n.parent.exists { case sel: Term.Select => sel.parent.exists { case a: Term.Apply => a.fun eq sel; case _ => false }; case _ => false } &&
          !n.parent.exists { case _: Term.ApplyType => true; case _ => false } =>
        // a case object written as a value: `Problem.Run.Interrupted`, or `Interrupted` after an import
        val sym = n.symbol.value
        if (sym.endsWith(".") && isCase(sym)) path(sym).foreach(p => found += Found(n.parent.collect { case s: Term.Select if s.name eq n => s }.getOrElse(n), p.mkString("."), "-"))
    }
    // the outermost constructions only: a case inside another's arguments is part of that one's sentence
    val outer = found.filterNot(f => found.exists(o => (o.tree ne f.tree) && o.tree.pos.start <= f.tree.pos.start && f.tree.pos.end <= o.tree.pos.end))
    val numbered = outer.toList.sortBy(_.tree.pos.start).groupBy(f => enclosing(f.tree, file)).toList.flatMap { case (enc, fs) =>
      fs.sortBy(_.tree.pos.start).zipWithIndex.map { case (f, n) =>
        val variants = scala.collection.mutable.SortedSet.empty[String]
        f.tree.traverse {
          // the whole of `Problem.Config.Fault.Empty`, not its qualifier `Problem.Config.Fault`
          case t @ (_: Term.Name | _: Term.Select) if (t ne f.tree) && !t.parent.exists { case Term.Select(q, n) => (q eq t) || (n eq t); case _ => false } =>
            symbolOf(t).flatMap(variantOf).filter(_ != f.site).foreach(variants += _)
        }
        Row(s"$enc#$n", module, file, f.tree.pos.startLine + 1, enc, f.site, variants.mkString(";"), f.template) -> f.tree
      }
    }
    numbered.foreach { case (r, _) => rows.add(r) }
    if (!config.emitLints) Patch.empty
    else Patch.fromIterable(numbered.map { case (r, t) =>
      val id = if (r.site == "Unclassified") "unclassified" else "case"
      Patch.lint(Diagnostic(id, s"${r.key} ${r.site} [${r.variants}] ${r.template}", t.pos, severity = LintSeverity.Info))
    })
  }
}
