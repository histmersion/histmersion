import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.meta.internal.{semanticdb => s}

object CreatorRewrite {
  case class Config(
      roots: List[String] = List("modules"),
      semanticdbRoots: List[String] = List("target/out/jvm"),
      tables: List[String] = Nil,
      unrecordedFiles: String = ".*BundleRepo\\.scala",
      copySources: List[String] = List("unresolved_edges"),
      endpointGivens: Map[String, String] = Map.empty,
      testFixtures: Boolean = false,
      skipTests: List[String] = Nil,
      testLiteral: String = "jsonb_build_object('kind', 'unknown')",
      boundaryFiles: String = ".*(Controllers|Routes|Tools)\\.scala",
      projectPrefix: String = "com/histmersion/",
      writingModules: List[String] = List("modules/db/", "modules/server/"),
      creatorImport: String = "com.histmersion.domain.Creator",
      columnImports: List[String] = List("com.histmersion.util.database.StoredCreator",
        "com.histmersion.util.database.NewtypeColumns.given"),
      testImport: String = "com.histmersion.util.database.TestCreators.given",
      report: String = "refactor-audit/provenance/creator-rewrite.tsv",
      apply: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  sealed trait Edit { def offset: Int }
  final case class Insert(offset: Int, text: String) extends Edit
  final case class InsertAt(offset: Int, text: String) extends Edit
  final case class ImportLine(text: String) extends Edit { def offset: Int = -1 }

  final case class Plan(edits: Map[String, List[Edit]], rows: List[String])

  private def relative(p: Path): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = p.toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else abs.toString
  }

  private def walk(root: String, suffix: String): List[Path] = {
    val r = Paths.get(root)
    if (!Files.isDirectory(r)) Nil
    else {
      val stream = Files.walk(r)
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(suffix)).toList
      finally stream.close()
    }
  }

  private def md5(bytes: Array[Byte]): String =
    java.security.MessageDigest.getInstance("MD5").digest(bytes).map(b => f"${b & 255}%02X").mkString

  final class Src(val path: String, val text: String, val tree: Source, val doc: s.TextDocument) {
    val test: Boolean = path.contains("/src/test/")
    private val byRange: Map[(Int, Int, Int, Int), s.SymbolOccurrence] = doc.occurrences.flatMap { o =>
      o.range.map(r => (r.startLine, r.startCharacter, r.endLine, r.endCharacter) -> o)
    }.toMap
    def occurrence(t: Tree): Option[s.SymbolOccurrence] = {
      val p = t.pos
      byRange.get((p.startLine, p.startColumn, p.endLine, p.endColumn))
    }
    def symbol(t: Tree): Option[String] = occurrence(t).map(_.symbol).map(key)
    def key(sym: String): String = if (sym.startsWith("local")) path + "::" + sym else sym
  }

  final case class DefSite(unit: Src, tree: Tree, name: Term.Name, symbol: String, groups: List[Member.ParamClauseGroup])

  final case class Ref(unit: Src, name: Term.Name, symbol: String, owner: Option[String])

  private def skipQuoted(text: String, quote: Int): Int = {
    val end = text.indexOf('\'', quote + 1)
    if (end < 0) text.length else end + 1
  }

  private def skipSplice(text: String, brace: Int): Int = {
    var depth = 0
    var i = brace
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '{') depth += 1
      else if (c == '}') { depth -= 1; if (depth == 0) return i + 1 }
      i += 1
    }
    i
  }

  private def closingIn(text: String, open: Int): Option[Int] = {
    var depth = 0
    var i = open
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '$' && i + 1 < text.length && text.charAt(i + 1) == '{') i = skipSplice(text, i + 1)
      else if (c == '\'') i = skipQuoted(text, i)
      else {
        if (c == '(') depth += 1
        else if (c == ')') { depth -= 1; if (depth == 0) return Some(i) }
        else if (text.startsWith("\"\"\"", i)) return None
        i += 1
      }
    }
    None
  }

  def valueRowEnds(text: String, firstEnd: Int): List[Int] = {
    var ends = List(firstEnd)
    var i = firstEnd + 1
    var more = true
    while (more) {
      while (i < text.length && Character.isWhitespace(text.charAt(i))) i += 1
      if (i < text.length && text.charAt(i) == ',') {
        val open = text.indexOf('(', i)
        closingIn(text, open) match {
          case Some(e) => ends = e :: ends; i = e + 1
          case None    => more = false
        }
      } else more = false
    }
    ends.reverse
  }

  def sqlEdits(text: String, start: Int, severalRows: Boolean = false): Either[String, (Int, Int)] = {
    def closing(open: Int): Option[Int] = closingIn(text, open)
    def keywordAtDepth0(from: Int, word: String): Option[Int] = {
      var depth = 0
      var i = from
      while (i < text.length && !text.startsWith("\"\"\"", i)) {
        val c = text.charAt(i)
        if (c == '$' && i + 1 < text.length && text.charAt(i + 1) == '{') i = skipSplice(text, i + 1)
        else if (c == '\'') i = skipQuoted(text, i)
        else {
          if (c == '(') depth += 1
          else if (c == ')') depth -= 1
          else if (depth == 0 && text.regionMatches(true, i, word, 0, word.length) &&
            !Character.isLetterOrDigit(text.charAt(i - 1)) &&
            (i + word.length >= text.length || !Character.isLetterOrDigit(text.charAt(i + word.length)))) return Some(i)
          i += 1
        }
      }
      None
    }
    val open = text.indexOf('(', start)
    val head = text.substring(start, math.max(start, open))
    if (open < 0 || !head.matches("(?s)INSERT INTO\\s+(public\\.)?[a-z_]+\\s*")) Left("no column list")
    else closing(open) match {
      case None => Left("unbalanced column list")
      case Some(columnsEnd) =>
        val rest = columnsEnd + 1
        val after = text.substring(rest, math.min(text.length, rest + 40)).trim
        if (after.toUpperCase.startsWith("VALUES")) {
          val vOpen = text.indexOf('(', rest)
          closing(vOpen) match {
            case None => Left("unbalanced VALUES row")
            case Some(vEnd) =>
              val tail = text.substring(vEnd + 1, math.min(text.length, vEnd + 20)).trim
              if (tail.startsWith(",") && !severalRows) Left("several VALUES rows") else Right((columnsEnd, vEnd))
          }
        } else if (after.toUpperCase.startsWith("SELECT")) {
          val select = text.indexOf("SELECT", rest)
          keywordAtDepth0(select + 6, "FROM") match {
            case None => Left("SELECT without FROM")
            case Some(from) =>
              var end = from
              while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end -= 1
              Right((columnsEnd, end))
          }
        } else Left("neither VALUES nor SELECT")
    }
  }

  def plan(config: Config): Plan = {
    val docs: Map[String, s.TextDocument] = config.semanticdbRoots.flatMap(r => walk(r, ".semanticdb")).flatMap { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.map(d => d.uri -> d)
    }.toMap
    val units: List[Src] = config.roots.flatMap(r => walk(r, ".scala")).flatMap { p =>
      val rel = relative(p)
      val bytes = Files.readAllBytes(p)
      if (!(rel.contains("/src/main/") || rel.contains("/src/test/"))) None
      else docs.get(rel).filter(_.md5 == md5(bytes)).map { d =>
        val text = new String(bytes, UTF_8)
        new Src(rel, text, dialects.Scala3(Input.VirtualFile(rel, text)).parse[Source].get, d)
      }
    }
    val info: Map[String, s.SymbolInformation] = units.flatMap(u => u.doc.symbols.map(i => u.key(i.symbol) -> i)).toMap

    val defs = mutable.Map.empty[String, DefSite]
    val refs = mutable.ListBuffer.empty[Ref]
    units.foreach { u =>
      def visit(t: Tree, owner: Option[String]): Unit = t match {
        case d: Defn.Def =>
          val sym = u.symbol(d.name).getOrElse(u.path + "::" + d.name.value + "@" + d.pos.start)
          defs(sym) = DefSite(u, d, d.name, sym, d.paramClauseGroups)
          d.paramClauseGroups.foreach(g => visit(g, owner))
          visit(d.body, Some(sym))
        case d: Decl.Def =>
          u.symbol(d.name).foreach(sym => defs(sym) = DefSite(u, d, d.name, sym, d.paramClauseGroups))
        case n: Term.Name =>
          u.occurrence(n).filter(_.role.isReference).foreach(o => refs += Ref(u, n, u.key(o.symbol), owner))
        case _ => t.children.foreach(c => visit(c, owner))
      }
      visit(u.tree, None)
    }
    val overrides: Map[String, List[String]] =
      defs.keys.map(k => k -> info.get(k).toList.flatMap(_.overriddenSymbols.map(_.toString))).toMap
    val overriders: Map[String, List[String]] =
      overrides.toList.flatMap { case (k, os) => os.map(_ -> k) }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }
    val project = (sym: String) => sym.startsWith(config.projectPrefix) &&
      defs.get(sym).exists(d => config.writingModules.exists(m => d.unit.path.startsWith(m))) || sym.contains("::local")
    def creatorType(t: Type): Boolean = {
      val x = t.syntax
      x == "Creator" || x == "Creator.New" || x.endsWith(".Creator") || x.endsWith(".Creator.New")
    }
    val takesCreator: Set[String] = defs.values.filter { d =>
      d.groups.flatMap(_.paramClauses).exists(c => c.values.exists(p => p.decltpe.exists(creatorType)))
    }.map(_.symbol).toSet
    val boundary = config.boundaryFiles.r
    val unrecorded = config.unrecordedFiles.r
    def endpoint(d: DefSite): Boolean = boundary.pattern.matcher(d.unit.path).matches() && (d.tree match {
      case x: Defn.Def => !x.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
      case _ => false
    })

    val edits = mutable.Map.empty[String, mutable.ListBuffer[Edit]]
    def add(path: String, e: Edit): Unit = edits.getOrElseUpdate(path, mutable.ListBuffer.empty) += e
    val rows = mutable.ListBuffer.empty[String]
    val stops = mutable.ListBuffer.empty[String]
    def line(u: Src, offset: Int): Int = u.text.substring(0, offset).count(_ == '\n') + 1

    val insert = ("INSERT INTO\\s+(?:public\\.)?(" + config.tables.mkString("|") + ")\\b").r
    val seeds = mutable.LinkedHashMap.empty[String, String]
    if (config.testFixtures) units.filter(u => u.test && !config.skipTests.exists(u.path.endsWith)).foreach { u =>
      insert.findAllMatchIn(u.text).foreach { m =>
        val sqlText = u.text.substring(m.start, math.min(u.text.length, m.start + 2000))
        val written = sqlText.takeWhile(_ != '"').replaceAll("\\s+", " ")
        if (!written.matches("(?i).*\\(([^)]*,)?\\s*creator\\s*[,)].*"))
          sqlEdits(u.text, m.start, severalRows = true) match {
            case Left(why) =>
              stops += List("stop", u.path, line(u, m.start).toString, s"fixture INSERT INTO ${m.group(1)}: $why", "")
                .mkString("\t")
            case Right((columnsEnd, firstEnd)) =>
              add(u.path, InsertAt(columnsEnd, ", creator"))
              valueRowEnds(u.text, firstEnd).foreach(e => add(u.path, InsertAt(e, ", " + config.testLiteral)))
              rows += List("fixture", u.path, line(u, m.start).toString, m.group(1), "").mkString("\t")
          }
      }
    }
    if (!config.testFixtures) units.filterNot(_.test).foreach { u =>
      insert.findAllMatchIn(u.text).foreach { m =>
        val owner = defs.values.filter(d => d.unit eq u).filter(d => d.tree.pos.start <= m.start && m.start < d.tree.pos.end)
          .toList.sortBy(d => d.tree.pos.end - d.tree.pos.start).headOption
        val bundle = unrecorded.pattern.matcher(u.path).matches()
        val value =
          if (bundle) "${StoredCreator.write(Creator.unrecorded)}::jsonb" else "${StoredCreator.column(creator)}::jsonb"
        val sqlText = u.text.substring(m.start, math.min(u.text.length, m.start + 2000))
        val written = sqlText.takeWhile(_ != '"').replaceAll("\\s+", " ")
        if (written.matches("(?i).*\\(([^)]*,)?\\s*creator\\s*[,)].*")) rows += List("written", u.path, line(u, m.start).toString, m.group(1), "").mkString("\t")
        else owner match {
          case None => stops += List("stop", u.path, line(u, m.start).toString, s"INSERT INTO ${m.group(1)} outside any def", "").mkString("\t")
          case Some(d) =>
            sqlEdits(u.text, m.start) match {
              case Left(why) => stops += List("stop", u.path, line(u, m.start).toString, s"INSERT INTO ${m.group(1)}: $why", "").mkString("\t")
              case Right((columnsEnd, valuesEnd)) =>
                val fromText = u.text.substring(valuesEnd, math.min(u.text.length, valuesEnd + 80)).trim
                val copied = config.copySources.find(t => fromText.matches("(?s)FROM\\s+(public\\.)?" + t + "\\b.*"))
                add(u.path, InsertAt(columnsEnd, ", creator"))
                add(u.path, InsertAt(valuesEnd, ", " + copied.fold(value)(_ => "creator")))
                add(u.path, ImportLine(config.creatorImport))
                if (copied.isEmpty) config.columnImports.foreach(i => add(u.path, ImportLine(i)))
                rows += List(if (copied.isDefined) "copy" else "insert", u.path, line(u, m.start).toString, m.group(1),
                  d.name.value).mkString("\t")
                if (copied.isEmpty && !bundle && !takesCreator(d.symbol)) seeds(d.symbol) = s"inserts into ${m.group(1)}"
            }
        }
      }
    }

    val needs = mutable.LinkedHashMap.empty[String, String]
    val bound = mutable.Set.empty[String]
    val queue = mutable.Queue.empty[String]
    def need(sym: String, why: String): Unit =
      if (!needs.contains(sym) && defs.contains(sym) && !takesCreator(sym)) {
        val d = defs(sym)
        val foreign = overrides(sym).filterNot(project)
        if (foreign.nonEmpty) stops += List("stop", d.unit.path, (d.name.pos.startLine + 1).toString,
          s"overrides ${foreign.mkString(",")} (outside the project): needs a creator of its own", why).mkString("\t")
        else if (!d.unit.test && endpoint(d)) {
          val given = config.endpointGivens.collectFirst { case (pattern, expr) if d.unit.path.matches(pattern) => expr }
          (given, d.tree) match {
            case (Some(expr), x: Defn.Def) if x.body.is[Term.Block] && x.body.asInstanceOf[Term.Block].stats.nonEmpty =>
              if (!bound.contains(sym)) {
                bound += sym
                val first = x.body.asInstanceOf[Term.Block].stats.head
                val indent = " " * first.pos.startColumn
                add(d.unit.path, InsertAt(first.pos.start, s"given Creator.New = $expr\n$indent"))
                add(d.unit.path, ImportLine(config.creatorImport))
                rows += List("given", d.unit.path, (d.name.pos.startLine + 1).toString, s"${d.name.value}: $expr", why)
                  .mkString("\t")
              }
            case _ => stops += List("stop", d.unit.path, (d.name.pos.startLine + 1).toString,
              s"endpoint ${d.name.value}: the binding gives the creator", why).mkString("\t")
          }
        }
        else {
          needs(sym) = why
          queue.enqueue(sym)
        }
      }
    seeds.foreach { case (sym, why) => need(sym, why) }
    val refsTo: Map[String, List[Ref]] = refs.toList.groupBy(_.symbol)
    while (queue.nonEmpty) {
      val sym = queue.dequeue()
      val d = defs(sym)
      overrides(sym).filter(project).foreach(o => need(o, s"overridden by ${d.name.value}"))
      overriders.getOrElse(sym, Nil).foreach(o => need(o, s"overrides ${d.name.value}"))
      refsTo.getOrElse(sym, Nil).filterNot(_.unit.test).foreach { r =>
        r.owner match {
          case None => stops += List("stop", r.unit.path, (r.name.pos.startLine + 1).toString,
            s"${d.name.value} outside any def: needs a creator of its own", "").mkString("\t")
          case Some(o) if takesCreator(o) => stops += List("stop", r.unit.path, (r.name.pos.startLine + 1).toString,
            s"${d.name.value} inside a def with an explicit creator: pass it as the given", "").mkString("\t")
          case Some(o) => need(o, s"calls ${d.name.value}")
        }
      }
    }

    needs.foreach { case (sym, why) =>
      val d = defs(sym)
      val at = d.groups.lastOption match {
        case Some(g) if g.paramClauses.nonEmpty => g.paramClauses.last.pos.end
        case Some(g) if g.tparamClause.values.nonEmpty => g.tparamClause.pos.end
        case _ => d.name.pos.end
      }
      val clause = if (seeds.contains(sym)) "(using creator: Creator.New)" else "(using Creator.New)"
      add(d.unit.path, InsertAt(at, clause))
      add(d.unit.path, ImportLine(config.creatorImport))
      rows += List("using", d.unit.path, (d.name.pos.startLine + 1).toString, d.name.value, why).mkString("\t")
    }
    refs.filter(r => r.unit.test && needs.contains(r.symbol)).groupBy(_.unit.path).foreach { case (path, rs) =>
      add(path, ImportLine(config.testImport))
      rows += List("test-import", path, (rs.head.name.pos.startLine + 1).toString,
        s"${rs.size} call(s), first ${rs.head.name.value}", "").mkString("\t")
    }
    rows ++= stops.distinct
    Plan(edits.map { case (k, v) => k -> v.toList.distinct }.toMap, rows.toList)
  }
}

class CreatorRewrite(config: CreatorRewrite.Config) extends SyntacticRule("CreatorRewrite") {
  import CreatorRewrite._

  def this() = this(CreatorRewrite.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("CreatorRewrite")(this.config).map(new CreatorRewrite(_))

  private lazy val computed: Plan = {
    val p = CreatorRewrite.plan(config)
    val out = Paths.get(config.report)
    Option(out.getParent).foreach(Files.createDirectories(_))
    val counts = p.rows.groupBy(_.takeWhile(_ != '\t')).map { case (k, v) => s"# $k\t${v.size}" }.toList.sorted
    Files.write(out, (counts ++ List("kind\tpath\tline\twhat\twhy") ++ p.rows).mkString("", "\n", "\n").getBytes(UTF_8))
    p
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.VirtualFile(p, _) => relative(Paths.get(p))
      case Input.File(p, _) => relative(p.toNIO)
      case other => other.syntax
    }
    val mine = computed.edits.getOrElse(path, Nil)
    if (!config.apply || mine.isEmpty) Patch.empty
    else {
      val tokens = doc.tokens
      val inserts = mine.collect { case InsertAt(at, text) => (at, text) }
      def literal(t: Token): Boolean = t.is[Token.Interpolation.Part] || t.is[Token.Constant.String]
      val (inParts, elsewhere) = inserts.partition { case (at, _) =>
        tokens.exists(t => literal(t) && t.pos.start <= at && at < t.pos.end)
      }
      val partPatches = inParts.groupBy { case (at, _) =>
        tokens.find(t => literal(t) && t.pos.start <= at && at < t.pos.end).get
      }.map { case (t, edits) =>
        val text = edits.sortBy(-_._1).foldLeft(t.text) { case (acc, (at, ins)) =>
          acc.substring(0, at - t.pos.start) + ins + acc.substring(at - t.pos.start)
        }
        Patch.replaceToken(t, text)
      }
      val located = partPatches.toList ++ elsewhere.map { case (at, text) =>
        tokens.find(t => t.pos.start == at && !t.is[Token.Whitespace]).map(t => Patch.addLeft(t, text))
          .orElse(tokens.find(t => t.pos.end == at && !t.is[Token.Whitespace]).map(t => Patch.addRight(t, text)))
          .getOrElse(Patch.empty)
      }
      val present = doc.tree.collect { case i: Importer => i.syntax }.toSet
      def has(text: String): Boolean = {
        val (pkg, name) = (text.split('.').init.mkString("."), text.split('.').last)
        present.contains(text) || present.exists(i => i.startsWith(pkg + ".{") && i.split("[{},\\s]+").contains(name))
      }
      val imports = mine.collect { case ImportLine(text) if !has(text) => text }.distinct
      val importPatch = if (imports.isEmpty) Patch.empty else {
        val pkgStats = {
          def inner(t: Tree): List[Stat] = t match {
            case s: Source => s.stats.collectFirst { case p: Pkg => inner(p) }.getOrElse(s.stats)
            case p: Pkg => p.stats.collectFirst { case q: Pkg => inner(q) }.getOrElse(p.stats)
            case _ => Nil
          }
          inner(doc.tree)
        }
        pkgStats.takeWhile(_.is[Import]).lastOption match {
          case Some(i) => Patch.addRight(i, imports.map("\nimport " + _).mkString)
          case None => pkgStats.headOption.map(s => Patch.addLeft(s, imports.map("import " + _ + "\n").mkString + "\n")).getOrElse(Patch.empty)
        }
      }
      located.asPatch + importPatch
    }
  }
}
