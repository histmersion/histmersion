import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

class AgentStorageRewrite extends SyntacticRule("AgentStorageRewrite") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val root = Paths.get("").toAbsolutePath.normalize
  private val output = Paths.get("target/agent-storage-preview")
  private val rows = new ConcurrentLinkedQueue[String]()
  private val storage = "com.histmersion.util.storage."
  private val home = "com.histmersion.util.system.JvmHome"
  private val parameters = s"(using stores: ${storage}Stores, resources: ${storage}Resources, clock: java.time.Clock, jvmHome: $home)"
  private def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def save(path: String, before: Option[String], after: String): Unit = {
    after.parse[Source].get
    val destination = output.resolve(path)
    Files.createDirectories(destination.getParent)
    Files.writeString(destination, after)
    rows.add(List(path, before.fold("new")(hash), hash(after)).mkString("\t"))
  }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val source = doc.input match { case Input.File(p, _) => p.toNIO; case Input.VirtualFile(p, _) => Paths.get(p) }
    val path = root.relativize(source.toAbsolutePath.normalize).toString
    if (!path.startsWith("modules/server/src/")) return Patch.empty
    val name = source.getFileName.toString
    val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    def insert(at: Int, text: String): Unit = edits += ((at, at, text))
    def edit(tree: Tree, text: String): Unit = {
      val rendered = text.linesIterator.toList match { case head :: tail => (head :: tail.map(line => if(line.isEmpty) "" else " " * tree.pos.startColumn + line)).mkString("\n"); case Nil => "" }
      edits += ((tree.pos.start, tree.pos.end, rendered))
    }
    def addMembers(template: Template, text: String): Unit = {
      val first = template.body.stats.head
      insert(first.pos.start, text + "\n" + " " * first.pos.startColumn)
    }
    def inject(method: Defn.Def): Unit = insert(method.paramClauseGroups.last.paramClauses.last.pos.end, parameters)
    val defs = doc.tree.collect { case d: Defn.Def => d }
    val classes = doc.tree.collect { case c: Defn.Class => c }
    def isWriter(fun: Term): Boolean = fun match {
      case Term.Select(Term.Name("AgentTrees"), Term.Name("write")) => true
      case _ => false
    }
    if(path.contains("/src/main/") && Set("Prompts.scala", "PluginTree.scala")(name)) {
      save(path, Some(doc.input.text), Files.readString(Paths.get("tools/refactor/agent-storage", name)))
      return Patch.empty
    }
    if(path.contains("/src/main/") && name == "AgentTrees.scala") {
      val cls = classes.find(_.name.value == "AgentTrees").get
      insert(cls.ctor.pos.end, s"(using stores: ${storage}Stores, clock: java.time.Clock)")
      val writer = defs.find(_.name.value == "write").get
      edit(writer, s"""def write(store: ${storage}Store, tree: Map[String, String], runtimes: List[String], sha: String)(using java.time.Clock): Either[String, GenerateResult] =
  AgentTreeWriter.write(store, tree, runtimes, sha)""")
      val expand = defs.find(_.name.value == "expand").get
      edit(expand, s"def expand(p: String)(using home: $home): String = home.expand(p)")
      doc.tree.collect { case call: Term.Apply if isWriter(call.fun) =>
        edit(call.argClause.values.head, s"stores.at(${storage}Location.AgentHomes, ${call.argClause.values.head.syntax})")
      }
    }
    if(path.contains("/src/main/") && name == "AgentDefinitions.scala") {
      val index = defs.find(_.name.value == "index").get
      edit(index.body, "prompts.bundledText(\"index.txt\").toOption.toList.flatMap(_.linesIterator.map(_.trim).filter(_.nonEmpty))")
    }
    if(path.contains("/src/main/") && name == "CliRuntime.scala") {
      insert(classes.find(_.name.value == "CliRuntime").get.ctor.pos.end, parameters.replace("jvmHome:", "val jvmHome:"))
      inject(defs.find(_.name.value == "apply").get)
    }
    if(path.contains("/src/main/") && name == "AgentDoctor.scala") {
      doc.tree.collect { case select: Term.Select if select.name.value == "codexHome" && select.qual.syntax.startsWith("AgentTrees(app,") =>
        edit(select, "AgentTrees.codexHome(app, paths)")
      }
    }
    if(path.contains("/src/main/") && name == "EngineSite.scala") {
      val traitDef = doc.tree.collect { case t: Defn.Trait if t.name.value == "EngineSite" => t }.head
      addMembers(traitDef.templ, s"def stores: ${storage}Stores\n  def resources: ${storage}Resources\n  def jvmHome: $home")
    }
    if(path.contains("/src/main/") && name == "ServerEngineSite.scala") {
      addMembers(classes.head.templ, s"def stores: ${storage}Stores = deps.stores\n  def resources: ${storage}Resources = deps.resources\n  def jvmHome: $home = deps.jvmHome")
    }
    if(path.contains("/src/main/") && name == "Deps.scala") {
      addMembers(classes.find(_.name.value == "Deps").get.templ,
        s"val stores: ${storage}Stores = ${storage}Stores.disk\n  val resources: ${storage}Resources = ${storage}Resources.classpath(getClass.getClassLoader)\n  val jvmHome: $home = SystemContext.jvmHome()")
    }
    if(path.contains("/src/main/") && name == "AgentsControllers.scala") {
      doc.tree.collect { case call: Term.Apply if call.fun.syntax == "CliRuntime" =>
        insert(call.pos.end, "(using site.stores, site.resources, site.clock, site.jvmHome)")
      }
      doc.tree.collect { case call: Term.Apply if call.fun.syntax == "PlanPools" => insert(call.pos.end, "(using site.jvmHome)") }
    }
    if(path.contains("/src/main/") && name == "SystemContext.scala") {
      val obj = doc.tree.collect { case o: Defn.Object if o.name.value == "SystemContext" => o }.head
      addMembers(obj.templ, s"def jvmHome(): $home = $home(java.nio.file.Path.of(System.getProperty(\"user.home\")))")
    }
    if(path.contains("/src/main/") && name == "ClaudeCode.scala") {
      defs.filter(d => Set("environment", "probeEnvironment")(d.name.value)).foreach(d => insert(d.paramClauseGroups.last.paramClauses.last.pos.end, s"(using $home)"))
    }
    if(path.contains("/src/main/") && name == "ClaudeCodeClient.scala") {
      doc.tree.collect { case call: Term.Apply if call.fun.syntax == "ClaudeCode.environment" => insert(call.pos.end, "(using rt.jvmHome)") }
    }
    if(path.contains("/src/main/") && name == "PlanPools.scala") {
      insert(classes.find(_.name.value == "PlanPools").get.ctor.pos.end, s"(using $home)")
      defs.filter(_.name.value == "claudeEnv").foreach(d => insert(d.paramClauseGroups.last.paramClauses.last.pos.end, s"(using $home)"))
    }
    if(path.contains("/src/main/") && name == "GeneratePluginMain.scala") {
      val main = defs.find(_.name.value == "generatePlugin").get
      edit(main.body, s"""given ${storage}Stores = ${storage}Stores.disk
given ${storage}Resources = ${storage}Resources.classpath(classOf[com.histmersion.domain.automation.agents.Prompts].getClassLoader)
val store = summon[${storage}Stores].at(${storage}Location.AgentHomes, Path.of(root).toAbsolutePath.normalize)
PluginTree.write(store, EngineContributions.roster).fold(message => { scribe.error(s"cannot generate the plugin: $$message"); sys.exit(1) }, identity)""")
    }
    if(path.contains("/src/test/") && doc.tree.collect { case n: Term.Name if Set("Prompts", "CliRuntime", "AgentTrees", "PluginTree", "PlanPools", "ClaudeCode")(n.value) => n }.nonEmpty) {
      val members = s"private given ${storage}Stores = ${storage}Stores.disk\n  private given ${storage}Resources = ${storage}Resources.classpath(getClass.getClassLoader)\n  private given java.time.Clock = java.time.Clock.systemUTC()\n  private given $home = $home(java.nio.file.Path.of(\"target/test-home\").toAbsolutePath)"
      doc.tree.collect {
        case cls: Defn.Class if cls.templ.body.stats.nonEmpty && cls.parent.exists(p => p.is[Pkg] || p.productPrefix == "Pkg.Body" || p.is[Source]) => addMembers(cls.templ, members)
        case obj: Defn.Object if obj.templ.body.stats.nonEmpty && obj.parent.exists(p => p.is[Pkg] || p.productPrefix == "Pkg.Body" || p.is[Source]) => addMembers(obj.templ, members)
      }
      doc.tree.collect { case call: Term.Apply if isWriter(call.fun) =>
        edit(call.argClause.values.head, s"summon[${storage}Stores].at(${storage}Location.AgentHomes, ${call.argClause.values.head.syntax})")
      }
    }
    if(edits.nonEmpty) {
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping edits: " + path)
      val result = sorted.reverse.foldLeft(doc.input.text) { case (text, (start, end, replacement)) => text.patch(start, replacement, end-start) }
      save(path, Some(doc.input.text), result)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    List("Stores.scala", "Resources.scala", "JvmHome.scala", "AgentTreeWriter.scala").foreach { name =>
      val folder = if(name == "AgentTreeWriter.scala") "domain/automation/agents" else if(name == "JvmHome.scala") "util/system" else "util/storage"
      val path = s"modules/server/src/main/scala/com/histmersion/$folder/$name"
      require(!Files.exists(Paths.get(path)), "New capability destination exists: " + path)
      save(path, None, Files.readString(Paths.get("tools/refactor/agent-storage", name)))
    }
    val out = Paths.get("refactor-audit/agent-storage"); Files.createDirectories(out)
    Files.write(out.resolve("plan.tsv"), ("path\tbefore-sha256\tafter-sha256" :: rows.asScala.toList.sorted).asJava)
    println(s"Agent storage preview: ${rows.size} files; application unchanged")
  }
}
