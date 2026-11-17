package com.histmersion
package server

import scala.collection.mutable
import scala.quoted.*
import scala.quoted.runtime.impl.QuotesImpl

import dotty.tools.dotc.{CompilationUnit, Compiler, Driver}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Phases.Phase
import dotty.tools.dotc.fromtasty.TASTYCompiler
import dotty.tools.dotc.quoted.QuotesCache
import dotty.tools.dotc.reporting.{Diagnostic, Reporter}

/** What the compiled code of the main modules says about the HTTP routes: every route a tapir endpoint is given its
  * logic in (`<endpoint>.serverLogic…(f)`), its method, and every write its logic can reach — read from the TASTy
  * trees, so a name is the symbol the compiler resolved, never a word in the text.
  *
  * The call graph: a method (or a function-valued val, whose use is a call of its closure) calls every term its body
  * names, nested closures and local definitions included; a constructor runs its class's body; an abstract or
  * overridable method calls every member that overrides it (class-hierarchy analysis). Reading a val, lazy ones
  * included, runs nothing: a lazy val of the wiring (`ServerWiring.instance`) is built once, by whoever asks first, and
  * building it is the wiring's work, not a request's. A route's logic reaches what that graph reaches from the terms
  * the logic names. A write is a call of one of [[Sinks]]: a database statement that changes rows (magnum's `update`
  * and `batchUpdate`, JDBC's `execute…`), a file system change (`java.nio.file.Files`' writers, `java.io.File`'s and
  * the output streams'), or a started process. A read-only transaction (`Transactor.readOnlyRollback`: PostgreSQL
  * refuses every write in it) is not followed into.
  *
  * What it cannot see, and so does not claim: a function value kept in a field or passed as a parameter and called from
  * elsewhere (the closure's body counts for the method that wrote it, not for its caller); a library's own internals
  * (its writes count only where the code calls one of the sinks); routes not given their logic by `serverLogic…` (the
  * static files' and MCP's endpoints).
  */
object RouteWrites:

  /** What a write changes. */
  enum Kind derives CanEqual:
    case Database, File, Process

  /** A route: its endpoint (the val that defines it, or the expression), its method (`GET`, …, `ANY` when it sets
    * none), and the file its logic is given in.
    */
  final case class Route(endpoint: String, method: String, site: String):
    def reads: Boolean = method == "GET" || method == "HEAD" || method == "ANY"

  /** A write `route`'s logic reaches: the sink called, and one shortest chain of calls from the logic to it. */
  final case class Write(route: Route, kind: Kind, sink: String, via: List[String]):
    /** The row of the known-violations list: route, sink, never the chain (it moves with every refactor). */
    def row: String = s"${route.endpoint}\t${route.method}\t${route.site}\t${kind.productPrefix}\t$sink"

  /** A route whose method could not be read from its endpoint: the check cannot vouch for it. */
  final case class Unresolved(site: String, endpoint: String)

  /** What a scan found, and how much it read: `trees` the units read, `unread` those it could not. */
  final case class Found(
      routes: List[Route],
      writes: List[Write],
      unresolved: List[Unresolved],
      unread: List[String],
      trees: Int
  )

  /** The sinks: (owner, member) — the owner's full name, its `$` marks left out — and what a call of it changes. */
  val Sinks: Map[(String, String), Kind] =
    val db = for
      (owner, names) <- List(
        "com.augustnagro.magnum.Frag" -> List("update", "returning"),
        "com.augustnagro.magnum.magnum" -> List("batchUpdate"),
        "com.augustnagro.magnum.package" -> List("batchUpdate"),
        "java.sql.Statement" -> List("execute", "executeUpdate", "executeLargeUpdate", "executeBatch"),
        "java.sql.PreparedStatement" -> List("execute", "executeUpdate", "executeLargeUpdate", "executeBatch")
      )
      name <- names
    yield (owner, name) -> Kind.Database
    val files = List(
      "java.nio.file.Files" -> List(
        "write",
        "writeString",
        "delete",
        "deleteIfExists",
        "move",
        "copy",
        "createDirectory",
        "createDirectories",
        "createFile",
        "createTempFile",
        "createTempDirectory",
        "createLink",
        "createSymbolicLink",
        "newOutputStream",
        "newBufferedWriter",
        "setLastModifiedTime",
        "setPosixFilePermissions",
        "setAttribute"
      ),
      "java.io.File" -> List("delete", "mkdir", "mkdirs", "renameTo", "createNewFile", "deleteOnExit"),
      "java.io.FileOutputStream" -> List("<init>"),
      "java.io.FileWriter" -> List("<init>"),
      "java.io.RandomAccessFile" -> List("<init>")
    ).flatMap((owner, names) => names.map(n => (owner, n) -> Kind.File))
    val processes = List(("java.lang.ProcessBuilder", "start"), ("java.lang.Runtime", "exec")).map(_ -> Kind.Process)
    (db ++ files ++ processes).toMap

  /** Not followed into: a transaction PostgreSQL holds read-only. */
  val Barriers: Set[(String, String)] = Set(
    ("com.histmersion.util.database.Transactor", "readOnlyRollback"),
    ("com.histmersion.util.database.Transactor.Session", "readOnlyRollback")
  )

  /** The start of a statement that changes no row: a setting (`SET TRANSACTION … READ ONLY`) or a query. */
  private val ReadOnlyStatement = "(?s)^(SET|SELECT|SHOW)\\b.*".r

  private val MethodSetters: Map[String, String] = Map(
    "get" -> "GET",
    "post" -> "POST",
    "put" -> "PUT",
    "delete" -> "DELETE",
    "patch" -> "PATCH",
    "head" -> "HEAD",
    "options" -> "OPTIONS"
  )

  /** Every route of the TASTy files and jars given, and what each one's logic writes. `classpath`: what they refer to.
    */
  def scan(tastyFiles: List[String], jars: List[String], classpath: List[String]): Found =
    val scan = Scan()
    val args = List("-from-tasty", "-Yretain-trees", "-classpath", classpath.mkString(java.io.File.pathSeparator)) ++
      tastyFiles ++ jars
    val _ = Reads(Reader(Reading(scan))).process(args.toArray, Silent())
    scan.found

  /** The compiler's own TASTy inspector, but for one thing: it stops before reading when the trees it re-reads fail a
    * check of the compiler's own (a `derives` given of a private row type is reported as leaking it, which the real
    * compile accepted). This one reads them all the same, and reports nothing; the suite checks what it found (the
    * route count, the planted routes).
    */
  final private class Reads(compiler: Compiler) extends Driver:
    override protected def newCompiler(using Context): Compiler = compiler

  final private class Reader(reading: Phase) extends TASTYCompiler:
    override protected def picklerPhases: List[List[Phase]] = List(List(reading))
    override protected def transformPhases: List[List[Phase]] = Nil
    override protected def backendPhases: List[List[Phase]] = Nil

  final private class Silent extends Reporter:
    def doReport(dia: Diagnostic)(using Context): Unit = ()

  final private class Reading(scan: Scan) extends Phase:
    def phaseName: String = "routeWrites"
    override def isRunnable(using Context): Boolean = true
    override def runOn(units: List[CompilationUnit])(using ctx: Context): List[CompilationUnit] =
      val quoting = QuotesCache.init(ctx.fresh)
      val q = QuotesImpl()(using quoting)
      // a unit whose file is no class of the class path is read as nothing (EmptyTree): counted, never walked
      val (read, empty) = units.partition(!_.tpdTree.isEmpty)
      scan.unread = empty.map(u => s"${u.source.file.path}")
      scan.read(using q)(read.map(_.tpdTree.asInstanceOf[q.reflect.Tree]))
      units
    protected def run(using Context): Unit = ()

  /** One pass over every tree builds the graph and finds the routes, then each route is walked. */
  final private class Scan:
    // `var`s: the scan's result and the units it could not read, set once in its one run
    var found: Found = Found(Nil, Nil, Nil, Nil, 0)
    var unread: List[String] = Nil

    def read(using Quotes)(trees: List[quotes.reflect.Tree]): Unit =
      import quotes.reflect.*

      val edges = mutable.HashMap.empty[Symbol, mutable.Set[Symbol]]
      val definitions = mutable.HashMap.empty[Symbol, Term]
      val logics = mutable.ListBuffer.empty[(Term, Term, Position)]

      def edge(from: Symbol, to: Symbol): Unit =
        val _ = edges.getOrElseUpdate(from, mutable.HashSet.empty) += to

      def plain(name: String): String = name.replace("$", "").replace("..", ".")

      def ownerName(s: Symbol): String = plain(s.owner.fullName)

      def isEndpoint(t: Term): Boolean =
        t.tpe.widen.dealias.baseClasses.exists(_.fullName == "sttp.tapir.Endpoint")

      def isFunctionValued(tpe: TypeRepr): Boolean =
        tpe.widen.dealias.baseClasses.exists(c => c.fullName.startsWith("scala.Function") && c.fullName.length <= 16)

      /** The first string literal of a tree: the start of a `sql"…"` statement's text. */
      def firstText(tree: Tree): Option[String] =
        val acc = new TreeAccumulator[Option[String]]:
          def foldTree(x: Option[String], t: Tree)(owner: Symbol): Option[String] =
            if x.isDefined then x
            else
              t match
                case Literal(StringConstant(s)) => Some(s)
                case _                          => foldOverTree(x, t)(owner)
        acc.foldTree(None, tree)(Symbol.noSymbol)

      /** A statement run through magnum's `update` that changes nothing: its text starts with `SET` (a transaction's
        * isolation or `READ ONLY`, a session setting) or `SELECT`.
        */
      def settingOnly(s: Select): Boolean =
        s.name == "update" && ownerName(s.symbol) == "com.augustnagro.magnum.Frag" &&
          firstText(s.qualifier).exists(t => ReadOnlyStatement.matches(t.trim.toUpperCase(java.util.Locale.ROOT)))

      def terms(tree: Tree): Set[Symbol] =
        val acc = new TreeAccumulator[Set[Symbol]]:
          def foldTree(x: Set[Symbol], t: Tree)(owner: Symbol): Set[Symbol] =
            val here = t match
              case i: Ident if i.symbol.exists && i.symbol.isTerm && !i.symbol.isPackageDef => x + i.symbol
              case s: Select if s.symbol.exists && s.symbol.isTerm && !s.symbol.isPackageDef && !settingOnly(s) =>
                x + s.symbol
              case _ => x
            foldOverTree(here, t)(owner)
        acc.foldTree(Set.empty, tree)(Symbol.noSymbol)

      def isLogic(fn: Term): Option[Term] = fn match
        case TypeApply(sel @ Select(qual, name), _) if name.startsWith("serverLogic") && isEndpoint(qual) => Some(qual)
        case sel @ Select(qual, name) if name.startsWith("serverLogic") && isEndpoint(qual)               => Some(qual)
        case _                                                                                            => None

      val walker = new TreeTraverser:
        override def traverseTree(tree: Tree)(owner: Symbol): Unit =
          tree match
            case d: DefDef =>
              d.rhs.foreach { r =>
                definitions(d.symbol) = r
                terms(r).foreach(edge(d.symbol, _))
              }
              d.symbol.allOverriddenSymbols.foreach(o => edge(o, d.symbol))
            case v: ValDef =>
              v.rhs.foreach { r =>
                definitions(v.symbol) = r
                if isFunctionValued(v.tpt.tpe) then terms(r).foreach(edge(v.symbol, _))
              }
            case c: ClassDef =>
              val ctor = c.constructor.symbol
              c.parents.foreach {
                case p: Term => terms(p).foreach(edge(ctor, _))
                case _       => ()
              }
              c.body.foreach {
                case _: DefDef => ()
                case v: ValDef => v.rhs.foreach(r => terms(r).foreach(edge(ctor, _)))
                case t: Term   => terms(t).foreach(edge(ctor, _))
                case _         => ()
              }
            case Apply(fn, List(logic)) =>
              isLogic(fn).foreach(endpoint => logics += ((endpoint, logic, tree.pos)))
            case _ => ()
          super.traverseTree(tree)(owner)

      trees.foreach(t => walker.traverseTree(t)(Symbol.noSymbol))

      /** The method an endpoint expression sets, and the term it starts from: the last setter applied wins. */
      def chain(t: Term): (Option[String], Option[Symbol]) = t match
        case Inlined(_, _, e) => chain(e)
        case Typed(e, _)      => chain(e)
        case TypeApply(fn, _) => chain(fn)
        case Apply(fn, args)  =>
          val (m, root) = chain(fn)
          val named = fn match
            case Select(_, "method") =>
              args.flatMap(terms).map(_.name).find(n => MethodSetters.values.toSet.contains(n))
            case _ => None
          (named.orElse(m), root)
        case Select(q, name) if isEndpoint(q) =>
          val (m, root) = chain(q)
          (MethodSetters.get(name).orElse(m), root)
        case other => (None, Option(other.symbol).filter(_.exists))

      def methodOf(endpoint: Term, depth: Int): Option[String] =
        val (m, root) = chain(endpoint)
        m.orElse(
          if depth > 8 then None
          else root.flatMap(definitions.get).filter(isEndpoint).flatMap(methodOf(_, depth + 1))
        )

      def where(p: Position): String =
        val path = p.sourceFile.path
        val at = path.indexOf("modules/")
        if at >= 0 then path.drop(at) else p.sourceFile.name

      def shown(s: Symbol): String =
        val at = s.pos.map(p => s" (${p.sourceFile.name}:${p.startLine + 1})").getOrElse("")
        s"${plain(s.fullName).stripPrefix("com.histmersion.")}$at"

      def sinkOf(s: Symbol): Option[Kind] = Sinks.get((ownerName(s), s.name))
      def isBarrier(s: Symbol): Boolean = Barriers.contains((ownerName(s), s.name))

      val unresolved = mutable.ListBuffer.empty[Unresolved]
      val routes = logics.toList.flatMap { (endpoint, logic, pos) =>
        val name = chain(endpoint)._2.map(s => plain(s.fullName).stripPrefix("com.histmersion.")).getOrElse("?")
        methodOf(endpoint, 0) match
          case Some(m) => List((Route(name, m, where(pos)), logic))
          case None    =>
            // no method set anywhere along the definition: tapir matches every method, GET included
            val (_, root) = chain(endpoint)
            // tapir's own empty endpoint (`endpoint`, `infallibleEndpoint`) sets none either
            val tapirs = root.exists(s => ownerName(s).startsWith("sttp.tapir") && s.name.endsWith("ndpoint"))
            if root.exists(definitions.contains) || tapirs then List((Route(name, "ANY", where(pos)), logic))
            else
              unresolved += Unresolved(where(pos), name)
              Nil
      }

      val writes = routes.filter(_._1.reads).flatMap { (route, logic) =>
        val parent = mutable.HashMap.empty[Symbol, Symbol]
        val seen = mutable.HashSet.empty[Symbol]
        val queue = mutable.Queue.empty[Symbol]
        val starts = terms(logic)
        starts.foreach { s => seen += s; queue.enqueue(s) }
        val hits = mutable.LinkedHashMap.empty[(Kind, String), List[String]]
        def chainTo(s: Symbol): List[String] =
          Iterator.iterate(Option(s))(_.flatMap(parent.get)).takeWhile(_.isDefined).flatten.toList.reverse.map(shown)
        while queue.nonEmpty do
          val s = queue.dequeue()
          sinkOf(s) match
            case Some(kind) =>
              val key = (kind, s"${ownerName(s)}.${s.name}")
              if !hits.contains(key) then hits(key) = chainTo(s)
            case None if isBarrier(s) => ()
            case None                 =>
              edges
                .get(s)
                .foreach(_.foreach { n =>
                  if !seen.contains(n) then
                    seen += n
                    parent(n) = s
                    queue.enqueue(n)
                })
        hits.toList.map { case ((kind, sink), via) => Write(route, kind, sink, via) }
      }
      found = Found(routes.map(_._1), writes, unresolved.toList, unread, trees.size)
