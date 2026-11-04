import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Path,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import com.typesafe.config._

class ResourceInventory extends SyntacticRule("ResourceInventory") {
  private implicit val dialect: Dialect = dialects.Scala3.withAllowToplevelTerms(true)
  override def afterComplete(): Unit = {
    val out = Paths.get("refactor-audit/resources"); Files.createDirectories(out); Files.writeString(out.resolve("status.txt"),"RUNNING\n")
    def walk(root: String): List[Path] = {
      val stream = Files.walk(Paths.get(root))
      try stream.iterator.asScala.filter(Files.isRegularFile(_)).toList.sortBy(_.toString) finally stream.close()
    }
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
    def esc(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    val includes = scala.collection.mutable.ListBuffer.empty[List[String]]
    val fields = scala.collection.mutable.ListBuffer.empty[List[String]]
    val failures = scala.collection.mutable.ListBuffer.empty[List[String]]
    def parseConfig(text: String, path: String): Config = {
      val includer = new ConfigIncluder with ConfigIncluderFile with ConfigIncluderURL with ConfigIncluderClasspath {
        def withFallback(fallback: ConfigIncluder): ConfigIncluder = this
        private def record(kind: String, value: String): ConfigObject = {
          includes += List(path,kind,value,"include-target-inventory; not-expanded")
          ConfigFactory.empty().root()
        }
        def include(context: ConfigIncludeContext, what: String): ConfigObject = record("heuristic",what)
        def includeFile(context: ConfigIncludeContext, what: java.io.File): ConfigObject = record("file",what.toString)
        def includeURL(context: ConfigIncludeContext, what: java.net.URL): ConfigObject = record("url",what.toString)
        def includeResources(context: ConfigIncludeContext, what: String): ConfigObject = record("classpath",what)
      }
      ConfigFactory.parseString(text,ConfigParseOptions.defaults().setOriginDescription(path).setIncluder(includer))
    }
    val fixture = parseConfig("include \"other.conf\"\napp { value = ${?ENVIRONMENT_VALUE}, count = 3, nested { enabled = true } }","fixture")
    require(includes.exists(r => r.head == "fixture" && r(2) == "other.conf"),"Include fixture missed")
    require(fixture.getInt("app.count") == 3 && fixture.getBoolean("app.nested.enabled"),"HOCON fixture lost values")
    require(fixture.root().render().contains("ENVIRONMENT_VALUE"),"Environment substitution was resolved")
    require(scala.util.Try(parseConfig("broken = {", "negative-fixture")).isFailure,"Malformed HOCON accepted")
    includes.clear()
    val configs = walk("modules").filter(p => p.toString.endsWith(".conf") && p.toString.contains("/src/") && p.toString.contains("/resources/"))
    def visit(path: String, keys: List[String], value: ConfigValue): Unit = value match {
      case obj: ConfigObject => obj.entrySet().asScala.toList.sortBy(_.getKey).foreach(e => visit(path,keys :+ e.getKey,e.getValue))
      case other =>
        val kind = scala.util.Try(other.valueType().name()).getOrElse("UNRESOLVED")
        fields += List(path,other.origin().lineNumber().toString,ConfigUtil.joinPath(keys.asJava),kind,
          other.render(ConfigRenderOptions.concise().setJson(false)),"declared-value; effective-config-unresolved")
    }
    configs.foreach { p =>
      scala.util.Try(parseConfig(Files.readString(p),p.toString)) match {
        case scala.util.Success(c) => visit(p.toString,Nil,c.root())
        case scala.util.Failure(e) => failures += List(p.toString,e.toString)
      }
    }
    write("config-fields.tsv","path\tline\tkey\tkind\tvalue\tstatus",fields.toList)
    write("config-includes.tsv","path\tkind\ttarget\tstatus",includes.toList)
    write("config-parse-failures.tsv","path\terror",failures.toList)
    val builds = Paths.get("build.sbt") :: walk("project").filter(p => (p.toString.endsWith(".scala") || p.toString.endsWith(".sbt")) && !p.toString.contains("/target/"))
    val buildRows = builds.flatMap { p =>
      val tree = Files.readString(p).parse[Source].get
      tree.collect {
        case n: Term.Name if Set("projectMatrix","description","nativeImageOptions","NativeImage","Sloth","nativeImage","mainClass","dependsOn","Provided","Runtime","Compile","enablePlugins")(n.value) =>
          val parent = n.parent.getOrElse(n)
          List(p.toString,(n.pos.startLine+1).toString,n.value,parent.syntax,parent.structure,"build-AST; evaluated-settings-in-build-inventory")
      }
    }
    write("build-ast.tsv","path\tline\tname\tsource\tast\tstatus",buildRows)
    val ledgers = List("places.tsv","exceptions.tsv","contracts.tsv").map(n => Paths.get("modules/server/src/test/resources/architecture",n))
    val ledgerRows = ledgers.flatMap { p =>
      Files.readAllLines(p,UTF_8).asScala.toList.zipWithIndex.filterNot { case(s,_) => s.trim.isEmpty || s.startsWith("#") }.map { case(s,i) =>
        val cells = s.split("\\\\t|\\t",-1).toList
        val places = p.getFileName.toString == "places.tsv"
        require(cells.size == (if(places) 2 else 4),"Malformed ledger: " + p + ":" + (i+1))
        val encoded = cells(if(places) 0 else 1).split(":",2)
        require(encoded.length == 2 || (!places && cells(1).startsWith("layer ")),"Malformed ledger file identity: " + s)
        val file = if(encoded.length == 2) Some(Paths.get("modules",encoded(0),"src/main/scala/com/histmersion",encoded(1))) else None
        List(p.toString,(i+1).toString,cells.mkString(" | "),file.fold("")(_.toString),
          file.fold("layer-aggregate; reconcile-concept-identities")(f => if(Files.isRegularFile(f)) "source-present; reconcile-policy" else "source-absent; relocation-or-retirement-proof-required"))
      }
    }
    write("ledgers.tsv","ledger\tline\tentry\tsource\tstatus",ledgerRows)
    val manifest = (configs ++ builds ++ ledgers).distinct
    write("sources.tsv","path\tsha256",manifest.map(p => List(p.toString,hash(Files.readString(p)))))
    Files.write(out.resolve("summary.md"),List("# Resource and build AST inventory","",
      s"${configs.size} configuration resources; ${fields.size} declared configuration values; ${includes.size} include directives; ${failures.size} parse failures.",
      s"${builds.size} build source files; ${buildRows.size} relevant build AST occurrences; ${ledgerRows.size} architecture ledger rows.","",
      "HOCON is parsed without resolving environment variables or fetching includes. Includes are inventoried separately, not silently flattened. Configuration ownership and effective include resolution still require policy decisions. Parse failures remain explicit.",
      "Build expressions retain ASTs; actual compile/runtime dependency closures come from BuildInventory. Ledger rows remain obligations when their original file disappears. Live-data protection requires process and integration evidence, not source-scanner approval.").asJava,UTF_8)
    require(!failures.exists(_.head.contains("/src/main/")),"Production configuration parse failures")
    Files.writeString(out.resolve("verification.txt"),"PASS\nNested HOCON, unexpanded include, unresolved environment substitution, malformed HOCON rejection\n")
    Files.writeString(out.resolve("status.txt"),"COMPLETE_RESOURCE_INVENTORY_WITH_REVIEW_CASES\n")
  }
}
