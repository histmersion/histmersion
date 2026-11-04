import scalafix.v1._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

object BuildInventory {
  case class Boundary(project: String, forbidden: List[String], scopes: List[String] = List("Compile","Runtime"))
  case class Policy(report: String = "refactor-audit/build", boundaries: List[Boundary] = Nil)
  object Boundary {
    implicit val surface: generic.Surface[Boundary] = generic.deriveSurface[Boundary]
    implicit val decoder: ConfDecoder[Boundary] = generic.deriveDecoder(Boundary("",Nil)).noTypos
  }
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Snapshot(projects: Set[String], values: Map[(String,String,String),List[String]])
  def artifactOwner(path: String, products: Map[String,String]): Option[String] = {
    val unhashed = path.takeWhile(_ != '>').replace("${OUT}", Paths.get("target/out").toAbsolutePath.normalize.toString)
    val product = Paths.get(unhashed).toAbsolutePath.normalize
    val matches = products.toList.collect { case (id, classes)
      if product == Paths.get(classes).toAbsolutePath.normalize ||
        (product.getFileName.toString.endsWith(".jar") && product.getParent == Paths.get(classes).toAbsolutePath.normalize.getParent) => id }
    require(matches.size <= 1, "Ambiguous project product: " + path)
    matches.headOption
  }
  def parse(lines: List[String]): Snapshot = {
    val project = "\\[info\\]\\s+(?:\\*\\s+)?([A-Za-z0-9_-]+)\\s*".r
    val header = "(?:(\\w+) / )?(?:(Compile|Runtime) / )?(classDirectory|fullClasspath|description|name|baseDirectory)".r
    val ids = scala.collection.mutable.Set.empty[String]
    val values = scala.collection.mutable.Map.empty[(String,String,String),List[String]]
    var key = Option.empty[(String,String,String)]
    var listing = false
    lines.foreach { line =>
      if(line.startsWith("[info] In file:")) listing = true
      if(listing) line match {
        case project(id) => ids += id
        case s if s.startsWith("[info]") && !s.startsWith("[info] In file:") => listing = false
        case _ => ()
      }
      if(line.startsWith("[info]")) {
        val text = line.stripPrefix("[info]").trim
        text match {
          case header(p,s,k) =>
            key = if(s == null && Set("Compile","Runtime").contains(p)) Some(("root",p,k))
              else Some((Option(p).getOrElse("root"),Option(s).getOrElse(""),k))
            values.getOrElseUpdate(key.get,Nil)
          case entry if line.startsWith("[info] \t") || entry.startsWith("* Attributed(") => key.foreach { k =>
            val value = if(entry.startsWith("* Attributed(") && entry.endsWith(")")) entry.stripPrefix("* Attributed(").stripSuffix(")") else entry
            values(k) = values(k) :+ value
          }
          case _ => ()
        }
      }
    }
    Snapshot(ids.toSet,values.toMap)
  }
  def validate(s: Snapshot): Unit = {
    require(s.projects.nonEmpty,"No evaluated sbt projects")
    val expected = s.projects.flatMap(p => Set((p,"","name"),(p,"","description"),(p,"","baseDirectory"),
      (p,"Compile","classDirectory"),(p,"Compile","fullClasspath"),(p,"Runtime","fullClasspath")))
    require(expected.subsetOf(s.values.keySet),"Missing evaluated settings: " + (expected -- s.values.keySet))
    s.values.foreach { case((_,_,key),values) =>
      if(key != "fullClasspath") require(values.size == 1,"Expected one evaluated value for " + key + ": " + values)
    }
  }
}

class BuildInventory(val policy: BuildInventory.Policy) extends SyntacticRule("BuildInventory") {
  def this() = this(BuildInventory.Policy())
  import BuildInventory._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("BuildInventory")(Policy()).map(new BuildInventory(_))
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report); Files.createDirectories(out); Files.writeString(out.resolve("status.txt"),"RUNNING\n")
    val fixture = List("[info] In file:/fixture/","[info] \t * root","[info] \t   cli",
      "[info] cli / Compile / fullClasspath","[info] * Attributed(/fixture/cli/classes)",
      "[info] Runtime / fullClasspath","[info] * Attributed(/fixture/root/classes)",
      "[info] cli / description","[info] \tA client")
    val parsed = parse(fixture)
    require(parsed.projects == Set("root","cli") && parsed.values(("cli","Compile","fullClasspath")) == List("/fixture/cli/classes") &&
      parsed.values(("root","Runtime","fullClasspath")) == List("/fixture/root/classes"),"Build parser fixture failed")
    require(scala.util.Try(validate(parsed)).isFailure,"Incomplete build snapshot was accepted")
    val fixtureProducts = Map("api" -> Paths.get("target/out/jvm/api/classes").toAbsolutePath.toString,
      "apiJS" -> Paths.get("target/out/sjs1/api/classes").toAbsolutePath.toString)
    require(artifactOwner("${OUT}/jvm/api/api_3.jar>sha256-abc/123",fixtureProducts).contains("api"))
    require(artifactOwner("${OUT}/sjs1/api/api_sjs1_3.jar>sha256-def/456",fixtureProducts).contains("apiJS"))
    require(artifactOwner("/cache/api_3.jar",fixtureProducts).isEmpty)
    val command = List("sbt","--server","-batch","set every scalafmtOnCompile := false","set ThisBuild / scalacOptions += \"-Xsemanticdb\"",
      "projects","show name","show description","show baseDirectory","show Compile / classDirectory","show Compile / fullClasspath","show Runtime / fullClasspath")
    val code = new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(out.resolve("sbt.log").toFile).start().waitFor()
    require(code == 0,"Build evaluation failed; see " + out.resolve("sbt.log"))
    val snapshot = parse(Files.readAllLines(out.resolve("sbt.log"),UTF_8).asScala.toList)
    validate(snapshot)
    def one(p: String,k: String,s: String = "") = snapshot.values((p,s,k)).head
    val products = snapshot.projects.map(p => p -> one(p,"classDirectory","Compile")).toMap
    val names = snapshot.projects.map(p => p -> one(p,"name")).toMap
    val entries = snapshot.projects.toList.sorted.flatMap(p => List("Compile","Runtime").flatMap(s =>
      snapshot.values((p,s,"fullClasspath")).map(path => List(p,s,artifactOwner(path,products).getOrElse("external"),path))))
    require(entries.exists(r => r.head != r(2) && r(2) != "external"), "No project dependencies resolved: inspect product path encoding")
    require(!entries.exists(r => r(2) == "external" && r(3).startsWith("${OUT}/") && r(3).contains(".jar")),
      "Unresolved local build artifact")
    val edges = entries.filter(r => r.head != r(2) && r(2) != "external")
    policy.boundaries.foreach(b => require(snapshot.projects(b.project) && b.forbidden.forall(snapshot.projects) && b.scopes.forall(Set("Compile","Runtime")),"Unknown dependency policy project/scope: " + b))
    val violations = edges.filter(r => policy.boundaries.exists(b => b.project == r.head && b.scopes.contains(r(1)) && b.forbidden.contains(r(2))))
    val runtimeMacro = edges.filter(r => r(1) == "Runtime" && r(2) == "chimneyIron")
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    write("projects.tsv","project\tname\tdescription\tbase-directory\tclass-directory",snapshot.projects.toList.sorted.map(p => List(p,names(p),one(p,"description"),one(p,"baseDirectory"),one(p,"classDirectory","Compile"))))
    write("classpath.tsv","project\tscope\tproject-dependency\tpath",entries)
    write("dependencies.tsv","project\tscope\tproject-dependency\tpath",edges)
    write("boundary-findings.tsv","project\tscope\tforbidden-project\tpath",violations)
    write("runtime-macro.tsv","project\tscope\tproject-dependency\tpath",runtimeMacro)
    val defaultDescriptions = snapshot.projects.toList.sorted.filter(p => one(p,"description").trim.isEmpty || one(p,"description") == names(p))
    Files.write(out.resolve("summary.md"),List("# Evaluated build inventory","",
      s"${snapshot.projects.size} sbt projects; ${entries.size} compile/runtime classpath entries; ${edges.map(_.take(3)).distinct.size} project dependency relationships.",
      s"${violations.map(_.take(3)).distinct.size} configured forbidden relationships; ${runtimeMacro.map(_.take(3)).distinct.size} runtime relationships to chimney-iron.",
      "Descriptions missing or identical to project name (review explicit descriptions): " + defaultDescriptions.mkString(", "),"",
      "Dependencies are evaluated transitive compile/runtime classpaths, not an import-text approximation. Project products are matched to evaluated product directories, including sbt hashed artifact paths and separate JVM/JS products. External artifacts remain in classpath.tsv for published-plugin-contract review. Test dependencies are a separate policy and are not declared violations here. Build AST/layout and native settings have a separate resource inventory.",
      "Parser fixtures reject missing settings; every listed sbt project must have both classpaths, base directory, name, description and class directory.").asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"COMPLETE_EVALUATED_BUILD_INVENTORY\n")
  }
}
