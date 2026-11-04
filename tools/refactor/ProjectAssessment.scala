import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

object ProjectAssessment {
  case class Policy(compile: Boolean = true, zero: List[String] = Nil, noRegression: Boolean = false, captureBaseline: Boolean = false)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def walk(root: String): List[Path] = {
    val stream = Files.walk(Paths.get(root))
    try stream.iterator.asScala.filter(Files.isRegularFile(_)).toList finally stream.close()
  }
}

class ProjectAssessment(val policy: ProjectAssessment.Policy) extends SyntacticRule("ProjectAssessment") {
  def this() = this(ProjectAssessment.Policy())
  import ProjectAssessment._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ProjectAssessment")(Policy()).map(new ProjectAssessment(_))
  private val root = Paths.get("refactor-audit/assessment")
  private val current = root.resolve("current")
  private def write(path: Path, lines: List[String]): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, lines.asJava, UTF_8)
  }
  private def lines(path: Path): List[String] = Files.readAllLines(path, UTF_8).asScala.toList
  private def table(name: String): List[List[String]] = lines(current.resolve(name)).drop(1).map(_.split("\t", -1).toList)
  private def module(path: String): String = path.split("/modules/").last.stripPrefix("modules/").split("/src/").head
  private def sourceFiles: List[Path] = walk("modules").filter(p => p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/")))
  private def generated(p: Path): Boolean = p.iterator.asScala.exists(s => Set("target",".scala-build",".bsp")(s.toString))
  private def policyInputs: List[Path] = walk("tools/refactor").filter { p =>
    !generated(p) && (List(".scala",".conf",".tsv").exists(p.toString.endsWith) || p.getFileName.toString == "objects.md")
  }
  private def resourceInputs: List[Path] =
    List(Paths.get("build.sbt"),Paths.get("docs/research-domain-model/objects.md")) ++
      walk("project").filter(p => !generated(p) && List(".scala",".sbt").exists(p.toString.endsWith)) ++
      walk("modules").filter(p => p.toString.contains("/src/") && p.toString.contains("/resources/") &&
        (p.toString.endsWith(".conf") || p.toString.contains("/architecture/") && p.toString.endsWith(".tsv")))
  private def fingerprint(paths: List[Path]): String = hash(paths.sortBy(_.toString).map(p => p.toString + "\t" + hash(Files.readString(p))).mkString("\n"))
  private def run(label: String, args: List[String]): Unit = {
    println("Assessment: " + label)
    val process = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(current.resolve(label + ".log").toFile).start()
    val code = process.waitFor()
    require(code == 0, s"$label failed with exit $code; see ${current.resolve(label + ".log")}")
  }
  private val cli = List("scala-cli", "run", "--server=false", "-q", "-S", "2.13.18", "--dep", "ch.epfl.scala:scalafix-cli_2.13.18:0.14.9", "--main-class", "scalafix.cli.Cli", "--")
  override def afterComplete(): Unit = {
    Files.createDirectories(current)
    write(current.resolve("status.txt"), List("RUNNING", java.time.Instant.now().toString))
    try {
      val before = fingerprint(sourceFiles)
      val policyHash = fingerprint(policyInputs)
      val resourcesHash = fingerprint(resourceInputs)
      require(policyInputs.contains(Paths.get("tools/refactor/model-fixture/expected-primitives.tsv")),"Golden fixture expectations missing from policy fingerprint")
      if (policy.compile) run("compile", List("sbt", "--server", "-batch", "set every scalafmtOnCompile := false", "set ThisBuild / scalacOptions += \"-Xsemanticdb\"", "compile", "Test/compile"))
      run("schema-fixtures", cli ++ List("--rules", "file:tools/refactor/SchemaVerification.scala", "--scala-version", "3.10.0", "--files", "tools/refactor/SchemaVerification.scala"))
      run("model-fixtures", cli ++ List("--rules", "file:tools/refactor/ModelInventoryVerification.scala", "--scala-version", "3.10.0", "--files", "tools/refactor/ModelInventoryVerification.scala"))
      run("operation-fixtures", cli ++ List("--rules", "file:tools/refactor/OperationInventoryVerification.scala", "--scala-version", "3.10.0", "--files", "tools/refactor/OperationInventoryVerification.scala"))
      run("copy-fixtures", cli ++ List("--rules", "file:tools/refactor/CopyVerification.scala", "--scala-version", "3.10.0", "--files", "tools/refactor/CopyVerification.scala"))
      run("architecture-fixtures", cli ++ List("--rules", "file:tools/refactor/ArchitectureInventoryVerification.scala", "--scala-version", "3.10.0", "--files", "tools/refactor/ArchitectureInventoryVerification.scala"))
      run("build-inventory", cli ++ List("--config", "tools/refactor/build-inventory.conf", "--scala-version", "3.10.0", "--files", "tools/refactor/BuildInventory.scala"))
      run("resource-inventory", cli ++ List("--config", "tools/refactor/resource-inventory.conf", "--scala-version", "3.10.0", "--files", "tools/refactor/ResourceInventory.scala"))
      run("semantic", cli ++ List("--config", "tools/refactor/assessment-semantic.conf", "--scala-version", "3.10.0", "--sourceroot", ".", "--auto-classpath", "--auto-classpath-roots", "target/out", "--files", "modules"))
      run("fixtures", cli ++ List("--config", "tools/refactor/assessment-fixtures.conf", "--scala-version", "3.10.0", "--sourceroot", ".", "--files", "modules", "--files", "tools/refactor/source-fixtures.scala", "--files", "tools/refactor/structure-fixtures.scala"))
      require(before == fingerprint(sourceFiles), "Application sources changed during assessment")
      require(policyHash == fingerprint(policyInputs), "Rule/configuration/fixture changed during assessment")
      require(resourcesHash == fingerprint(resourceInputs), "Build/configuration/object-plan/ledger inputs changed during assessment")
      run("inventory-coverage", cli ++ List("--config", "tools/refactor/inventory-coverage.conf", "--scala-version", "3.10.0", "--files", "tools/refactor/InventoryCoverage.scala"))
      val modules = sourceFiles.map(p => module(p.toString)).distinct.sorted
      val scopes = scala.collection.mutable.LinkedHashMap.empty[String, String]
      val hits = scala.collection.mutable.ListBuffer.empty[(String, String)]
      def register(id: String, scope: String): Unit = { require(!scopes.contains(id), "Duplicate measurement: " + id); scopes(id) = scope }
      lines(current.resolve("capabilities/rules.txt")).foreach(id => register("capability/" + id, "production; configured symbol/owner policy"))
      table("capabilities/findings.tsv").foreach(r => hits += (("capability/" + r.head, module(r(1)))))
      lines(current.resolve("structure/rules.txt")).foreach(id => register("structure/" + id, "production; configured structural scope"))
      table("structure/findings.tsv").foreach(r => hits += (("structure/" + r.head, module(r(1)))))
      register("repository/outside-files", "production+tests; twelve configured repository symbols; relationship count")
      table("repositories/outside-files.tsv").foreach(r => hits += (("repository/outside-files", module(r(1)))))
      register("migration/old-owner", "production+tests; configured ownership moves")
      table("owners/references.tsv").foreach(r => hits += (("migration/old-owner", module(r.head))))
      register("migration/controller-name", "production+tests; configured old controller symbols")
      table("controllers.tsv").filter(_.head == "rewrite").foreach(r => hits += (("migration/controller-name", module(r(1)))))
      List("stdout", "stderr").foreach { channel =>
        register("migration/cli-" + channel, "cli production only; includes unsupported matches")
        table(channel + ".tsv").foreach(r => hits += (("migration/cli-" + channel, module(r.head))))
      }
      register("migration/console-injection", "cli production only; dependency-plan nodes")
      table("console.tsv").foreach(r => hits += (("migration/console-injection", module(r(1)))))
      register("derivation/schema-bridge", "production+tests; semantic plain KindlingsSchema bridges, including deferred shapes")
      table("schema-derives/plan.tsv").foreach(r => hits += (("derivation/schema-bridge", module(r(1)))))
      List("production","test").foreach { scope =>
        register("updates/case-class-copy-" + scope,scope + "; resolved case-class copy occurrences, including deferred rewrite shapes")
        lines(Paths.get("refactor-audit/operations/occurrences.tsv")).drop(1).map(_.split("\t",-1)).filter(r => r(0) == "copy" && r(1) == "case-class-copy" && r(3) == scope).foreach(r => hits += (("updates/case-class-copy-" + scope,module(r(2)))))
      }
      require(hits.forall { case (id,m) => scopes.contains(id) && modules.contains(m) }, "Unknown measurement/module")
      val counts = scopes.keys.toList.flatMap(id => ("ALL" :: modules).map(m => (id,m) -> hits.count { case (r,mod) => r == id && (m == "ALL" || mod == m) })).toMap
      val baseline = root.resolve("baseline.tsv")
      val baselinePolicy = root.resolve("baseline-policy.txt")
      def countRows(values: Map[(String,String),Int]): List[String] = "rule\tmodule\tcount" :: values.toList.sortBy(_._1).map { case ((r,m),n) => s"$r\t$m\t$n" }
      if (!Files.exists(baseline) || policy.captureBaseline) {
        require(policy.captureBaseline || !Files.exists(baselinePolicy), "Incomplete baseline; review before replacing")
        write(baseline, countRows(counts))
        write(baselinePolicy, List(policyHash))
      }
      val comparable = lines(baselinePolicy).head == policyHash
      val previous = lines(baseline).drop(1).map(_.split("\t")).map(r => (r(0),r(1)) -> r(2).toInt).toMap
      write(current.resolve("counts.tsv"), countRows(counts))
      write(current.resolve("matrix.tsv"), List("rule\tmodule\tscope\tcurrent\tbaseline\tdelta") ++ counts.toList.sortBy(_._1).map { case ((r,m),n) =>
        val old = if (comparable) previous.get((r,m)) else None
        s"$r\t$m\t${scopes(r)}\t$n\t${old.fold("unknown")(_.toString)}\t${old.fold("incomparable")(v => (n-v).toString)}"
      })
      write(current.resolve("coverage.tsv"), List("module\tproduction-files\ttest-files") ++ modules.map { m =>
        val files = sourceFiles.filter(p => module(p.toString) == m)
        s"$m\t${files.count(_.toString.contains("/src/main/"))}\t${files.count(_.toString.contains("/src/test/"))}"
      })
      write(current.resolve("summary.md"), List("# Project-wide rule measurements", "", s"${modules.size} source modules; ${sourceFiles.size} source/test files. Source and policy fingerprints remained stable. Baseline comparable: $comparable.", "", "Counts are matches within each rule's stated scope, not proof of all 43 architectural goals. Relationship counts and rewrite-plan nodes must not be added to diagnostic counts. CLI-only rows outside cli are not applicable. Zero is never a claim that an unimplemented goal is covered.", "", "| Rule | Current | Baseline | Delta | Scope |", "|---|---:|---:|---:|---|") ++ scopes.toList.map { case (id,scope) =>
        val n = counts((id,"ALL")); val old = if (comparable) previous.get((id,"ALL")) else None
        s"| $id | $n | ${old.fold("unknown")(_.toString)} | ${old.fold("incomparable")(v => (n-v).toString)} | $scope |"
      })
      write(current.resolve("fingerprints.txt"), List("sources=" + before, "policy=" + policyHash, "resources=" + resourcesHash))
      val legacyPath = Paths.get("modules/server/src/test/resources/architecture/contracts.tsv")
      val legacy = lines(legacyPath).filterNot(s => s.startsWith("#") || s.trim.isEmpty).map(_.split("\\\\t|\\t", -1).toList)
      require(legacy.forall(_.size == 4), "Unknown historical contract ledger format")
      val legacyCounts = legacy.groupBy(_.head).toList.map { case (id, rows) => (id, rows.size, rows.map(_(3).toInt).sum) }.sortBy(r => -r._3)
      write(current.resolve("automation-backlog.md"), List("# Bulk rewrite planning baseline", "", s"Historical contracts.tsv: ${legacy.size} grouped rows, ${legacyCounts.map(_._3).sum} occurrences. SHA-256: ${hash(Files.readString(legacyPath))}.", "", "These are recorded exceptions, not a fresh violation count. Do not add them to current detector counts or use them as a verified completion denominator. Reconcile each family with semantic matches and valid cases before rewriting. The target is at least 90% automatic fixes across the agreed violation inventory, with the difficult remainder tracked explicitly.", "", "| Historical family | Rows | Occurrences |", "|---|---:|---:|") ++ legacyCounts.map { case (id, rows, count) => s"| $id | $rows | $count |" } ++ List("", "Work by reusable AST/symbol transformation across all modules. For each family record confirmed violations, legitimate cases, automatic patches, unsupported cases and post-rewrite residuals. Preserve the denominator and policy version; compile definitions and callers and test behavior. A successful single-site rewrite is a fixture, not the migration batch."))
      require(policy.zero.forall(scopes.contains), "Unknown rule requested in zero gate")
      val remaining = policy.zero.filter(id => counts((id,"ALL")) != 0)
      val regressions = if (policy.noRegression) {
        require(comparable && counts.keySet == previous.keySet, "No-regression gate requires a comparable baseline and identical module/rule coverage")
        counts.collect { case (key,n) if n > previous(key) => key }.toList
      } else Nil
      write(current.resolve("status.txt"), List("MEASUREMENT_COMPLETE", s"zero-gate=${remaining.isEmpty}", s"no-regression-gate=${regressions.isEmpty}", "Architectural readiness remains separate; see structure/coverage-matrix.md"))
      require(remaining.isEmpty && regressions.isEmpty, s"Batch gate failed: remaining=$remaining regressions=$regressions")
      println("Assessment complete: " + current.resolve("summary.md"))
    } catch {
      case e: Exception =>
        write(current.resolve("status.txt"), List("FAILED", Option(e.getMessage).getOrElse(e.toString), "Do not treat prior reports as current passing evidence."))
        throw e
    }
  }
}
