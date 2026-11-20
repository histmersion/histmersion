import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.meta.internal.{semanticdb => s}

/** X4 / G89: every write transaction holds a write permit, by type.
  *
  * `Transactor#transact` / `#autocommit` (and `Transactor.Session#transact`) open a write without a permit. This rule
  * moves each call in main code to the permit-taking entry point (`write`, `writeAutocommit`) and gives the def around
  * it a `(using WritePermit)` clause, then follows the permit up the call graph, over the whole code base at once:
  *
  *   - a def that calls a def needing a permit needs one too, unless the call sits inside the block of a permit
  *     provider (`WriteGate#guard`, `WritePermit.Gate#permit`, or any def listed in `providers`), which holds one;
  *   - a def needing a permit makes every def it overrides need one, and every other def overriding those (a service
  *     trait's method, its implementation and its `InMemory` twin keep one signature), test sources included;
  *   - a call inside a function literal passed to anything but a call known to run it at once (`synchronous`: the
  *     standard collections, Option, Either, Try) does NOT carry the permit across: a thread, a worker, a stored
  *     callback or a constructor may run it after the block, and a permit lives as long as its block. Such a
  *     site, an endpoint in a `boundaryFiles` file (it must hold the gate itself: `WriteGate#guard`), a def that
  *     overrides a method outside the project, and a call outside any def (a val, a constructor) are not rewritten
  *     past: they are listed as `stop` rows for a hand-written permit source.
  *
  * Test sources: a file that calls a def needing a permit outside a provider block gets `testImport` (a standing test
  * permit), and overrides there get the clause like any other.
  *
  * Re-runnable: a def that already takes a `WritePermit` is left as it is, a call already on `write` is no seed.
  * `apply = false` (the default) changes nothing and writes the listing (`report`): one row per rewritten call, added
  * clause, test import and stop. Global: the plan is computed once from every source under `roots` and its current
  * SemanticDB (a document counts only when its md5 matches the file), then each file gets its share.
  *
  *   sbt 'server/Test/compile'   # current SemanticDB of every module
  *   bash tools/refactor/scalafix.sh --config tools/refactor/write-permit.conf --sourceroot . --files modules   # dry run
  *   bash tools/refactor/scalafix.sh --config tools/refactor/write-permit-apply.conf --sourceroot . --files modules
  */
object WritePermitRewrite {
  case class Config(
      roots: List[String] = List("modules"),
      semanticdbRoots: List[String] = List("target/out/jvm"),
      seeds: Map[String, String] = Map(
        "com/histmersion/util/database/Transactor#transact()." -> "write",
        "com/histmersion/util/database/Transactor#autocommit()." -> "writeAutocommit",
        "com/histmersion/util/database/Transactor.Session#transact()." -> "write",
        "com/histmersion/util/database/Transactor.Session#autocommit()." -> "writeAutocommit"
      ),
      providers: List[String] = List(
        "com/histmersion/util/data/maintenance/WriteGate#guard().",
        "com/histmersion/util/database/WritePermit.Gate#permit()."
      ),
      /** Calls that run a function literal passed to them before they return: a permit is carried into it. Any other
        * call taking a function literal (a thread, a worker, a stored callback, a constructor) defers it: stop.
        */
      synchronous: List[String] = List("scala/util/Either#", "scala/util/Try", "scala/Option#", "scala/collection/",
        "scala/package.List", "scala/util/Using", "scala/util/control/", "java/util/Optional#",
        "com/histmersion/util/data/maintenance/WriteGate#guard().",
        "com/histmersion/util/database/WritePermit.Gate#permit()."),
      deferred: List[String] = List("scala/collection/immutable/LazyList", "scala/collection/Iterator#", "scala/collection/View"),
      /** Calls whose (by-name) argument runs later, on another thread: a permit must never be captured into one. */
      deferredCalls: List[String] = List("com/histmersion/util/jobs/Pinning#carry().",
        "com/histmersion/util/jobs/Pinning#carryWork().", "com/histmersion/util/jobs/WorkCarrier#carryWork()."),
      /** `path:line` of a transaction that only reads (a SELECT opened with `transact`): it becomes `read`. */
      reads: List[String] = Nil,
      /** Files whose calls are left as they are (the transactor itself). */
      exclude: List[String] = List("modules/db/src/main/scala/com/histmersion/util/database/Transactor.scala"),
      boundaryFiles: String =".*(Controllers|Routes|Tools)\\.scala",
      projectPrefix: String = "com/histmersion/",
      writingModules: List[String] = List("modules/db/", "modules/server/"),
      permitType: String = "com/histmersion/util/database/WritePermit#",
      permitImport: String = "com.histmersion.util.database.WritePermit",
      testImport: String = "com.histmersion.util.database.TestPermits.given",
      report: String = "refactor-audit/lane-data/write-permit.tsv",
      apply: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  /** An edit at a character offset of one file: `replace` the token starting there, or `insert` after the token ending
    * there.
    */
  sealed trait Edit { def offset: Int }
  final case class Replace(offset: Int, text: String) extends Edit
  final case class Insert(offset: Int, text: String) extends Edit
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

  /** One source with its current SemanticDB. */
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
    /** Local symbols are per document. */
    def key(sym: String): String = if (sym.startsWith("local")) path + "::" + sym else sym
  }

  /** A def (or an abstract declaration) of the code base. */
  final case class DefSite(unit: Src, tree: Tree, name: Term.Name, symbol: String, groups: List[Member.ParamClauseGroup])

  /** A reference from inside a def's body (or from outside any def, `owner = None`). */
  final case class Ref(unit: Src, name: Term.Name, symbol: String, owner: Option[String], covered: Boolean,
                       async: Option[String], lambda: Option[String])

  def plan(config: Config): Plan = {
    val docs: Map[String, s.TextDocument] = config.semanticdbRoots.flatMap(r => walk(r, ".semanticdb")).flatMap { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.map(d => d.uri -> d)
    }.toMap
    val units: List[Src] =config.roots.flatMap(r => walk(r, ".scala")).flatMap { p =>
      val rel = relative(p)
      val bytes = Files.readAllBytes(p)
      if (!(rel.contains("/src/main/") || rel.contains("/src/test/"))) None
      else docs.get(rel).filter(_.md5 == md5(bytes)).map { d =>
        val text = new String(bytes, UTF_8)
        new Src(rel,text, dialects.Scala3(Input.VirtualFile(rel, text)).parse[Source].get, d)
      }
    }
    val info: Map[String, s.SymbolInformation] = units.flatMap(u => u.doc.symbols.map(i => u.key(i.symbol) -> i)).toMap

    // ---- defs, overrides, references -------------------------------------------------------------------------
    val defs = mutable.Map.empty[String, DefSite]
    val refs = mutable.ListBuffer.empty[Ref]
    val seedCalls = mutable.ListBuffer.empty[(Src, Term.Name, Option[String], Boolean, Option[String])]
    def funSymbol(u: Src, f: Term): Option[String] = f match {
      case Term.Apply.After_4_6_0(g, _) => funSymbol(u, g)
      case Term.ApplyType.After_4_6_0(g, _) => funSymbol(u, g)
      case n: Term.Name => u.symbol(n)
      case Term.Select(_, n) => u.symbol(n)
      case _ => None
    }
    def literal(t: Term): Boolean = t match {
      case _: Term.Function | _: Term.AnonymousFunction | _: Term.PartialFunction | _: Term.ContextFunction => true
      case Term.Block(stats) => stats.lastOption.exists { case x: Term => literal(x); case _ => false }
      case _ => false
    }
    units.foreach { u =>
      def visit(t: Tree, owner: Option[String], covered: Boolean, async: Option[String], lambda: Option[String]): Unit = t match {
        case d: Defn.Def =>
          val sym = u.symbol(d.name).getOrElse(u.path + "::" + d.name.value + "@" + d.pos.start)
          defs(sym) = DefSite(u, d, d.name, sym, d.paramClauseGroups)
          d.paramClauseGroups.foreach(g => visit(g, owner, covered, async, lambda))
          visit(d.body, Some(sym), false, None, None)
        case d: Decl.Def =>
          u.symbol(d.name).foreach(sym => defs(sym) = DefSite(u, d, d.name, sym, d.paramClauseGroups))
        case Term.Apply.After_4_6_0(f, args) =>
          val callee = funSymbol(u, f)
          val inside = covered || callee.exists(config.providers.contains)
          visit(f, owner, covered, async, lambda)
          args.values.foreach { v =>
            val fn = literal(v)
            // a function literal carries the permit only into a call known to run it before it returns
            val deferred = fn && !callee.exists(c => config.synchronous.exists(p => c.startsWith(p)) &&
              !config.deferred.exists(p => c.startsWith(p))) || callee.exists(config.deferredCalls.contains)
            val starts = if (deferred) callee.orElse(Some("?" + f.syntax.takeRight(60))) else async
            visit(v, owner, inside, starts, if (fn) callee.orElse(Some("?" + f.syntax.takeRight(60))) else lambda)
          }
        case n: Term.Name =>
          u.occurrence(n).filter(_.role.isReference).foreach { o =>
            val sym = u.key(o.symbol)
            if (config.seeds.contains(sym)) seedCalls += ((u, n, owner, covered, async))
            else refs += Ref(u, n, sym, owner, covered, async, lambda)
          }
        case _ => t.children.foreach(c => visit(c, owner, covered, async, lambda))
      }
      visit(u.tree, None, false, None, None)
    }
    val overrides: Map[String, List[String]] =
      defs.keys.map(k => k -> info.get(k).toList.flatMap(_.overriddenSymbols.map(_.toString))).toMap
    val overriders: Map[String, List[String]] =
      overrides.toList.flatMap { case (k, os) => os.map(_ -> k) }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }
    // a def the permit may reach: one of the modules that write (the plugin SPI and its plugins never see a permit)
    val project = (sym: String) => sym.startsWith(config.projectPrefix) &&
      defs.get(sym).exists(d => config.writingModules.exists(m => d.unit.path.startsWith(m))) || sym.contains("::local")
    val takesPermit: Set[String] =defs.values.filter { d =>
      d.groups.flatMap(_.paramClauses).exists(c => c.mod.exists(_.is[Mod.Using]) && c.values.exists(p =>
        p.decltpe.exists(t => t.syntax == "WritePermit" || t.syntax.endsWith(".WritePermit"))))
    }.map(_.symbol).toSet
    val boundary = config.boundaryFiles.r
    def endpoint(d: DefSite): Boolean = boundary.pattern.matcher(d.unit.path).matches() && (d.tree match {
      case x: Defn.Def => !x.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])
      case _ => false
    })

    // ---- propagation -----------------------------------------------------------------------------------------
    val needs = mutable.LinkedHashMap.empty[String, String] // def -> why
    val stops = mutable.ListBuffer.empty[String]
    val queue = mutable.Queue.empty[String]
    def need(sym: String, why: String): Unit =
      if (!needs.contains(sym) && defs.contains(sym)) {
        val d = defs(sym)
        val foreign = overrides(sym).filterNot(project)
        if (foreign.nonEmpty) stops += List("stop", d.unit.path, (d.name.pos.startLine + 1).toString,
          s"overrides ${foreign.mkString(",")} (outside the project): needs a permit of its own", why).mkString("\t")
        else if (!d.unit.test && endpoint(d)) stops += List("stop", d.unit.path, (d.name.pos.startLine + 1).toString,
          s"endpoint ${d.name.value} writes outside WriteGate#guard", why).mkString("\t")
        else {
          needs(sym) = why
          queue.enqueue(sym)
        }
      }
    def reached(owner: Option[String], u: Src, n: Term.Name, async: Option[String], what: String): Unit =
      (owner, async) match {
        case (_, Some(starter)) => stops += List("stop", u.path, (n.pos.startLine + 1).toString,
          s"$what inside the block of $starter (asynchronous): needs a permit of its own", "").mkString("\t")
        case (None, _) => stops += List("stop", u.path, (n.pos.startLine + 1).toString,
          s"$what outside any def: needs a permit of its own", "").mkString("\t")
        case (Some(o), _) => need(o, s"calls $what")
      }
    val seedRows = mutable.ListBuffer.empty[String]
    def at(u: Src, n: Term.Name): String = u.path + ":" + (n.pos.startLine + 1)
    /** What the call becomes: `read` for a transaction listed as one that only reads, else its seed's entry point. */
    def target(u: Src, n: Term.Name): String =
      if (config.reads.contains(at(u, n))) "read" else config.seeds(u.key(u.occurrence(n).get.symbol))
    seedCalls.foreach { case (u, n, owner, covered, async) =>
      if (!u.test && !config.exclude.contains(u.path)) {
        val to = target(u, n)
        seedRows += List(if (to == "read") "read" else "write", u.path, (n.pos.startLine + 1).toString, s"${n.value} -> $to", "").mkString("\t")
        if (!covered && to != "read") reached(owner, u, n, async, s"Transactor#${n.value}")
      }
    }
    val refsTo: Map[String, List[Ref]] = refs.toList.groupBy(_.symbol)
    val lambdas = mutable.Map.empty[String, Int]
    while (queue.nonEmpty) {
      val sym = queue.dequeue()
      val d = defs(sym)
      // one signature for the whole override family
      overrides(sym).filter(project).foreach(o => need(o, s"overridden by ${d.name.value}"))
      overriders.getOrElse(sym, Nil).foreach(o => need(o, s"overrides ${d.name.value}"))
      refsTo.getOrElse(sym, Nil).filterNot(r => r.covered || r.unit.test).foreach { r =>
        if (r.async.isEmpty) r.lambda.foreach(l => lambdas(l) = lambdas.getOrElse(l, 0) + 1)
        reached(r.owner, r.unit, r.name, r.async, d.name.value)
      }
    }
    // the calls whose function literal a permit is carried into: each must run its function before it returns
    val lambdaRows = lambdas.toList.sortBy(-_._2).map { case (callee, n) => List("lambda", "", n.toString, callee, "").mkString("\t") }

    // ---- edits -----------------------------------------------------------------------------------------------
    val edits = mutable.Map.empty[String, mutable.ListBuffer[Edit]]
    def add(path: String, e: Edit): Unit = edits.getOrElseUpdate(path, mutable.ListBuffer.empty) += e
    val rows = mutable.ListBuffer.empty[String]
    rows ++= seedRows
    seedCalls.filterNot(c => config.exclude.contains(c._1.path)).foreach { case (u, n, _, covered, _) =>
      add(u.path, Replace(n.pos.start, target(u, n)))
      if (u.test && !covered) add(u.path, ImportLine(config.testImport))
    }
    seedCalls.filter(_._1.test).groupBy(_._1.path).foreach { case (path, cs) =>
      rows += List("test-write", path, (cs.head._2.pos.startLine + 1).toString, s"${cs.size} call(s)", "").mkString("\t")
    }
    needs.foreach { case (sym, why) =>
      if (!takesPermit(sym)) {
        val d = defs(sym)
        val at = d.groups.lastOption match {
          case Some(g) if g.paramClauses.nonEmpty => g.paramClauses.last.pos.end
          case Some(g) if g.tparamClause.values.nonEmpty => g.tparamClause.pos.end
          case _ => d.name.pos.end
        }
        add(d.unit.path, Insert(at, "(using WritePermit)"))
        add(d.unit.path, ImportLine(config.permitImport))
        rows += List("using", d.unit.path, (d.name.pos.startLine + 1).toString, d.name.value, why).mkString("\t")
      }
    }
    val testCallers = refs.filter(r => r.unit.test && !r.covered && needs.contains(r.symbol)).groupBy(_.unit.path)
    testCallers.foreach { case (path, rs) =>
      add(path, ImportLine(config.testImport))
      rows += List("test-import", path, (rs.head.name.pos.startLine + 1).toString, s"${rs.size} call(s), first ${rs.head.name.value}", "").mkString("\t")
    }
    rows ++= stops.distinct
    // Magnum's own `transact` on a DataSource bypasses the transactor: rewritten by hand (`Transactor(ds).write`, or
    // `Transactor(ds).snapshot` for a read-only snapshot), since the call's shape differs
    rows ++= refs.filter(r => !r.unit.test && r.symbol.startsWith("com/augustnagro/magnum/") && r.name.value == "transact")
      .map(r => List("stop", r.unit.path, (r.name.pos.startLine + 1).toString,
        "Magnum transact on a DataSource: Transactor(ds).write (or .snapshot when it only reads)", "").mkString("\t"))
    rows ++= lambdaRows
    Plan(edits.map { case (k, v) => k -> v.toList.distinct }.toMap, rows.toList)
  }
}

class WritePermitRewrite(config: WritePermitRewrite.Config) extends SyntacticRule("WritePermitRewrite") {
  import WritePermitRewrite._

  def this() = this(WritePermitRewrite.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("WritePermitRewrite")(this.config).map(new WritePermitRewrite(_))

  private lazy val computed: Plan = {
    val p = WritePermitRewrite.plan(config)
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
      val token = mine.collect {
        case Replace(at, text) => tokens.find(_.pos.start == at).map(t => Patch.replaceToken(t, text)).getOrElse(Patch.empty)
        case Insert(at, text) => tokens.find(t => t.pos.end == at && !t.is[Token.Whitespace]).map(t => Patch.addRight(t, text)).getOrElse(Patch.empty)
      }
      val present = doc.tree.collect { case i: Importer => i.syntax }.toSet
      // present when imported as it is, or by name inside a braced list of the same package
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
        val lastImport = pkgStats.takeWhile(_.is[Import]).lastOption
        lastImport match {
          case Some(i) => Patch.addRight(i, imports.map("\nimport " + _).mkString)
          case None => pkgStats.headOption.map(s => Patch.addLeft(s, imports.map("import " + _ + "\n").mkString + "\n")).getOrElse(Patch.empty)
        }
      }
      token.asPatch + importPatch
    }
  }
}
