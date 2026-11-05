import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityRewrite extends SemanticRule("PluginCapabilityRewrite") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val output = Paths.get("target/plugin-capability-preview")
  private val rows = scala.collection.mutable.ListBuffer.empty[String]
  private val api = "com.histmersion.plugin."
  private val host = "com.histmersion.pluginhost."
  private val usingFiles = s"(using files: ${api}LocalFiles)"
  private val usingProcesses = s"(using processes: ${api}Processes)"
  private def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def save(path: String, before: Option[String], after: String): Unit = {
    after.parse[Source].get
    val dest = output.resolve(path); Files.createDirectories(dest.getParent); Files.writeString(dest, after)
    rows += List(path, before.fold("new")(hash), hash(after)).mkString("\t")
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(doc.input.syntax).toAbsolutePath.normalize).toString
    val name = Paths.get(path).getFileName.toString
    val main = path.contains("/src/main/")
    val vault = path.startsWith("modules/plugin-vaults/")
    val calibre = path.startsWith("modules/plugins/calibre/")
    val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    def insert(at: Int, text: String): Unit = edits += ((at, at, text))
    def edit(tree: Tree, text: String): Unit = {
      val rendered = text.linesIterator.zipWithIndex.map { case (line, i) => (if(i == 0 || line.isEmpty) "" else " " * tree.pos.startColumn) + line }.mkString("\n")
      edits += ((tree.pos.start, tree.pos.end, rendered))
    }
    def member(t: Template, text: String): Unit = {
      val first = t.body.stats.head
      insert(first.pos.start, text + "\n" + " " * first.pos.startColumn)
    }
    def inject(d: Defn.Def, parameters: String): Unit = insert(d.paramClauseGroups.last.paramClauses.last.pos.end, parameters)
    def prefixed(d: Defn.Def, givens: String): Unit = {
      val first = d.body match { case b: Term.Block => b.stats.head; case other => other }
      insert(first.pos.start, givens + "\n" + " " * first.pos.startColumn)
    }
    val defs = doc.tree.collect { case d: Defn.Def => d }
    val classes = doc.tree.collect { case c: Defn.Class => c }
    if(main && name == "Plugin.scala" && path.startsWith("modules/plugin-api/")) {
      val context = doc.tree.collect { case t: Defn.Trait if t.name.value == "Context" => t }.head
      member(context.templ, "def files: LocalFiles = LocalFiles.Unavailable\n    def processes: Processes = Processes.Unavailable")
    }
    if(main && name == "HostServices.scala") {
      val cls = classes.find(_.name.value == "HostServices").get
      val param = cls.ctor.paramClauses.last.values.last
      insert(param.pos.end, ",\n    files: LocalFiles = LocalFiles.Unavailable,\n    processes: Processes = Processes.Unavailable")
    }
    if(main && name == "PluginHost.scala") {
      member(classes.find(_.name.value == "RegistrationContext").get.templ, "override def files: LocalFiles = services.files\n    override def processes: Processes = services.processes")
    }
    if(main && Set("PluginHarness.scala", "Plugins.scala", "RelinkPlugins.scala")(name)) {
      doc.tree.collect { case call: Term.Apply if call.fun.symbol.value.startsWith("com/histmersion/pluginhost/HostServices.") =>
        val last = call.argClause.values.last
        insert(last.pos.end, s", files = new ${host}DiskLocalFiles, processes = new ${host}SystemProcesses")
      }
    }
    if(main && (vault || calibre)) {
      if(name == "Subprocess.scala") {
        val result = classes.find(_.name.value == "Result").get
        edit(result, s"type Result = ${api}Processes.Result\nval Result = ${api}Processes.Result")
        val session = classes.find(_.name.value == "Session").get
        edit(session, s"type Session = ${api}Processes.Session")
        val run = defs.find(_.name.value == "run").get
        inject(run, usingProcesses); edit(run.body, "processes.run(argv, env, stdin, timeout)")
        val start = defs.find(_.name.value == "start").get
        inject(start, usingProcesses); edit(start.body, "processes.start(argv, env)")
        edit(defs.find(_.name.value == "drain").get, "")
        inject(defs.find(_.name.value == "locate").get, usingFiles)
      }
      if(Set("OnePasswordVault.scala", "BitwardenVault.scala", "KeePassXcVault.scala")(name))
        insert(classes.find(_.name.value == name.stripSuffix(".scala")).get.ctor.pos.end, usingProcesses)
      if(Set("OnePasswordPlugin.scala", "BitwardenPlugin.scala", "KeePassXcPlugin.scala", "CalibrePlugin.scala")(name)) {
        prefixed(defs.find(_.name.value == "register").get, s"given files: ${api}LocalFiles = ctx.files\n    given processes: ${api}Processes = ctx.processes")
        defs.filter(_.name.value == "detector").foreach(d => prefixed(d, s"given files: ${api}LocalFiles = ctx.files"))
      }
      if(name == "Shell.scala") {
        val system = doc.tree.collect { case o: Defn.Object if o.name.value == "System" => o }.head
        edit(system, s"""def system$usingProcesses: Shell = new Shell:
  def run(cmd: List[String]): Either[String, String] =
    processes.capture(cmd).flatMap(result =>
      if result.exit == 0 then Right(result.out)
      else Left(s"$${cmd.head} exited $${result.exit}: $${result.err.trim.take(400)}")
    )
  def isRunning(processMatch: String): Boolean = run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)""")
      }
      if(name == "Calibre.scala") {
        insert(classes.find(_.name.value == "Calibre").get.ctor.pos.end, usingFiles)
        val library = defs.find(_.name.value == "library").get
        library.paramClauseGroups.head.paramClauses.head.values.filter(p => Set("searchPath", "executable")(p.name.value)).foreach { p =>
          edit(p, p.name.syntax + ": " + p.decltpe.get.syntax)
        }
        insert(library.pos.start, s"def library(home: Option[String], searchPath: Option[String] = None)$usingFiles: Library =\n      library(home, searchPath, files.isExecutable)\n\n    ")
      }
      if(name == "CalibreCollection.scala") {
        insert(classes.find(_.name.value == "CalibreCollection").get.ctor.pos.end, usingFiles)
        defs.filter(d => Set("read", "staged", "reach")(d.name.value) && d.paramClauseGroups.exists(_.paramClauses.nonEmpty)).foreach(d => inject(d, usingFiles))
        edit(defs.find(_.name.value == "staged").get.body, "files.staged(s\"$name.${file.extension}\", file.bytes)(f)")
        val reach = defs.find(d => d.name.value == "reach" && d.paramClauseGroups.exists(_.paramClauses.nonEmpty)).get
        reach.body.collect { case v: Defn.Val if v.rhs.syntax == "Files.newInputStream(db)" => v }.foreach { v =>
          val block = v.parent.get.asInstanceOf[Term.Block]
          edit(block, "{ files.readPrefix(db, 16); () }")
        }
      }
      doc.tree.collect {
        case s: Term.Select if s.name.symbol.value.startsWith("java/nio/file/Files#") &&
          !edits.exists(e => e._1 <= s.pos.start && e._2 >= s.pos.end) =>
          val method = s.name.value match { case "isRegularFile" => "isFile"; case "readAllBytes" => "read"; case other => other }
          edit(s, "files." + method)
      }
    }
    if((vault || calibre) && !main) {
      val givens = s"private given ${api}LocalFiles = new ${host}DiskLocalFiles\n  private given ${api}Processes = new ${host}SystemProcesses"
      doc.tree.collect {
        case c: Defn.Class if c.templ.body.stats.nonEmpty && c.parent.exists(p => p.is[Pkg] || p.productPrefix == "Pkg.Body" || p.is[Source]) => member(c.templ, givens)
        case o: Defn.Object if o.templ.body.stats.nonEmpty && o.parent.exists(p => p.is[Pkg] || p.productPrefix == "Pkg.Body" || p.is[Source]) => member(o.templ, givens)
      }
      classes.filter(_.templ.inits.exists(_.tpe.symbol.value == "com/histmersion/plugin/Plugin.Context#")).foreach { c =>
        member(c.templ, s"override def files: ${api}LocalFiles = summon[${api}LocalFiles]\n    override def processes: ${api}Processes = summon[${api}Processes]")
      }
      doc.tree.collect { case n: Term.NewAnonymous if n.templ.inits.exists(_.tpe.symbol.value == "com/histmersion/plugin/Plugin.Context#") =>
        member(n.templ, s"override def files: ${api}LocalFiles = summon[${api}LocalFiles]\n      override def processes: ${api}Processes = summon[${api}Processes]")
      }
    }
    if(vault || calibre) doc.tree.collect { case s: Term.Select if s.symbol.value == "com/histmersion/plugins/calibre/Shell.System." => edit(s, "Shell.system") }
    if(edits.nonEmpty) {
      val sorted = edits.toList.sortBy(e => (e._1, e._2))
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping edits: " + path)
      val result = sorted.reverse.foldLeft(doc.input.text) { case (text, (start,end,replacement)) => text.patch(start,replacement,end-start) }
      save(path, Some(doc.input.text), result)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    List("LocalFiles.scala", "Processes.scala", "DiskLocalFiles.scala", "SystemProcesses.scala").foreach { name =>
      val apiFile = Set("LocalFiles.scala", "Processes.scala")(name)
      val path = s"modules/${if(apiFile) "plugin-api" else "plugin-host"}/src/main/scala/com/histmersion/${if(apiFile) "plugin" else "pluginhost"}/$name"
      require(!Files.exists(Paths.get(path)))
      save(path, None, Files.readString(Paths.get("target/plugin-capability-adapters", name)))
    }
    val report = Paths.get("refactor-audit/plugin-capabilities")
    Files.createDirectories(report)
    Files.write(report.resolve("plan.tsv"), ("path\tbefore-sha256\tafter-sha256" :: rows.toList.sorted).asJava)
    println(s"Plugin capability preview: ${rows.size} files; application unchanged")
  }
}
