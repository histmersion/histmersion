import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory

object ProjectAssessment {
  case class Invocation(config: String = "", files: List[String] = Nil, semantic: Boolean = false)
  object Invocation {
    implicit val surface: generic.Surface[Invocation] = generic.deriveSurface[Invocation]
    implicit val decoder: ConfDecoder[Invocation] = generic.deriveDecoder(Invocation()).noTypos
  }
  case class Pipeline(mode: String = "plan", report: String = "", scopeReview: String = "",
    prepare: List[Invocation] = Nil, rewrites: List[Invocation] = Nil,
    baselineTests: List[String] = Nil, afterTests: List[String] = Nil,
    compileConfig: String = "", identityChecks: List[Invocation] = Nil, capabilityFixes: List[String] = Nil)
  object Pipeline {
    implicit val surface: generic.Surface[Pipeline] = generic.deriveSurface[Pipeline]
    implicit val decoder: ConfDecoder[Pipeline] = generic.deriveDecoder(Pipeline()).noTypos
  }
  case class Policy(compile: Boolean = true, zero: List[String] = Nil, noRegression: Boolean = false,
    captureBaseline: Boolean = false, pipeline: Option[Pipeline] = None)
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
  // Scalafix at the build's versions, with the build's Scala as --scala-version (target/build-versions.properties)
  private val cli = List("bash", "tools/refactor/scalafix.sh")
  private def migration(p: Pipeline): Unit = {
    require(Set("plan", "apply")(p.mode), "Pipeline mode must be plan or apply")
    require(!policy.captureBaseline, "Migration cannot recapture the aggregate baseline")
    def local(value: String): Path = {
      val path = Paths.get(value)
      require(value.nonEmpty && !path.isAbsolute && path.normalize == path && !path.startsWith(".."), "Expected worktree-relative path: " + value)
      path
    }
    require(Files.isRegularFile(local(p.scopeReview)), "Missing eligibility/exclusions review")
    require(p.rewrites.nonEmpty && (p.identityChecks.nonEmpty || p.capabilityFixes.nonEmpty), "Require rewrites and original-ID reconciliation")
    require(p.capabilityFixes.distinct.size == p.capabilityFixes.size, "Duplicate expected capability IDs")
    require(p.capabilityFixes.forall(_.matches("CAP-[0-9a-f]{64}")), "Expected complete original capability IDs")
    require(p.baselineTests.nonEmpty && p.baselineTests.size == p.afterTests.size, "Require paired behavioral tests")
    def config(path: String) = {
      require(Files.isRegularFile(local(path)), "Missing configuration: " + path)
      ConfigFactory.parseFile(Paths.get(path).toFile).resolve()
    }
    def rule(path: String, name: String) = {
      val c = config(path)
      require(c.getStringList("rules").asScala.toList == List("file:tools/refactor/" + name + ".scala"), "Expected existing " + name + " entry point: " + path)
      c.getConfig(name)
    }
    p.baselineTests.zip(p.afterTests).foreach { case (before, after) =>
      val b = rule(before, "BatchTestVerification"); val a = rule(after, "BatchTestVerification")
      require((!b.hasPath("phase") || b.getString("phase") == "baseline") && a.getString("phase") == "after", "Incorrect test phases")
      List("module", "report", "suites", "artifact", "environment", "knownSkips").foreach { k =>
        def value(c: com.typesafe.config.Config) = if (c.hasPath(k)) Some(c.getValue(k)) else None
        require(value(b) == value(a), "Test scope changed: " + k)
      }
      if (p.mode == "apply") require(!Files.exists(local(b.getString("report")).resolve("baseline.tsv")), "Test observation already exists; use a new observation directory")
    }
    val build = rule(p.compileConfig, "CompileBatch")
    require(build.getStringList("commands").asScala.toList == List("compile", "Test/compile"), "Pipeline requires full production and test compilation")
    if (p.mode == "apply") require(!Files.exists(local(build.getString("report"))), "Build observation already exists")
    val invocations = p.prepare ++ p.rewrites ++ p.identityChecks
    invocations.foreach { i =>
      val c = config(i.config)
      require(!c.getStringList("rules").asScala.exists(_.contains("ProjectAssessment")), "Recursive pipeline stage")
      require(i.files.nonEmpty, "Stage needs explicit input scope")
      i.files.foreach(f => require(Files.exists(local(f)), "Missing stage input: " + f))
    }
    val output = local(p.report)
    require(!Files.exists(output), "Never overwrite a pipeline observation")
    if (p.mode == "plan") {
      println("PIPELINE_CONFIG_VALID; plan checks configuration only, not preview freshness or observation reuse; no stages executed; no repair or verification credit")
      return
    }
    Files.createDirectories(output)
    write(output.resolve("status.txt"), List("RUNNING"))
    def stage(label: String, i: Invocation): Unit = {
      val args = cli ++ List("--config", i.config) ++
        (if (i.semantic) List("--sourceroot", ".", "--auto-classpath", "--auto-classpath-roots", "target/out") else Nil) ++
        i.files.flatMap(f => List("--files", f))
      println("Migration: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(output.resolve(label + ".log").toFile).start().waitFor()
      require(code == 0, label + " failed; inspect " + output.resolve(label + ".log"))
    }
    def stages(label: String, xs: List[Invocation]): Unit = xs.zipWithIndex.foreach { case (i,n) => stage(label + "-" + n, i) }
    def tests(label: String, xs: List[String]): Unit = stages(label, xs.map(c => Invocation(c, List("tools/refactor/BatchTestVerification.scala"))))
    def capabilities(): Map[String,List[String]] = {
      val stream = Files.list(Paths.get("refactor-audit/capability-history/observations"))
      val observation = try stream.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString).last finally stream.close()
      require(lines(observation.resolve("status.txt")).head == "PASS", "Capability identity gate failed")
      require(lines(observation.resolve("sources.txt")) == List(fingerprint(sourceFiles)), "Stale capability observation")
      val rows = lines(observation.resolve("findings.tsv")).tail.map(_.split("\t", -1).toList)
      require(rows.forall(_.size == 9) && rows.map(_.head).distinct.size == rows.size, "Invalid capability identity table")
      rows.map(r => r.head -> r).toMap
    }
    try {
      new ProjectAssessment(policy.copy(compile = true, zero = Nil, pipeline = None)).afterComplete()
      val originalCapabilities = capabilities()
      require(p.capabilityFixes.forall(originalCapabilities.contains), "Expected repair ID is absent from the before observation")
      val before = fingerprint(sourceFiles)
      val rulesBefore = fingerprint(policyInputs)
      val reviewBefore = hash(Files.readString(local(p.scopeReview)))
      tests("baseline-tests", p.baselineTests)
      stages("prepare", p.prepare)
      require(before == fingerprint(sourceFiles), "Preparation/tests changed application sources")
      stages("rewrite", p.rewrites)
      stage("compile", Invocation(p.compileConfig, List("tools/refactor/CompileBatch.scala")))
      val rewritten = fingerprint(sourceFiles)
      tests("after-tests", p.afterTests)
      new ProjectAssessment(policy.copy(compile = false, pipeline = None)).afterComplete()
      if (p.capabilityFixes.nonEmpty) {
        require(capabilities() == originalCapabilities -- p.capabilityFixes,
          "Capability reconciliation differs from exact expected removals; an unchanged aggregate count is insufficient")
      }
      stages("identity", p.identityChecks)
      require(rewritten == fingerprint(sourceFiles), "Verification changed application sources")
      require(rulesBefore == fingerprint(policyInputs), "Policy/configuration changed during migration")
      require(reviewBefore == hash(Files.readString(local(p.scopeReview))), "Eligibility review changed during migration")
      write(output.resolve("status.txt"), List("PASS", "Compilation, selected tests, assessment and configured original-ID checks passed.",
        "verified-capability-ids=" + p.capabilityFixes.size,
        "verified-capability-occurrences=" + p.capabilityFixes.map(id => originalCapabilities(id)(1).toInt).sum,
        "Repair counts remain in the existing family histories; no overall percentage implied."))
    } catch {
      case e: Exception =>
        write(output.resolve("status.txt"), List("FAILED", Option(e.getMessage).getOrElse(e.toString), "Stopped without rollback; inspect the worktree and stage logs before resuming."))
        throw e
    }
  }
  override def afterComplete(): Unit = {
    if (policy.pipeline.nonEmpty) { migration(policy.pipeline.get); return }
    // Preserve the evidence preceding each measurement, including explicit baseline corrections.
    if (Files.exists(current) || Files.exists(root.resolve("baseline.tsv"))) {
      val archive = root.resolve("history").resolve(java.time.Instant.now().toString.replace(':','-'))
      val preserved = (if (Files.exists(current)) walk(current.toString) else Nil) ++
        List(root.resolve("baseline.tsv"),root.resolve("baseline-policy.txt")).filter(Files.exists(_))
      preserved.foreach { source =>
        val destination = archive.resolve(root.relativize(source))
        Files.createDirectories(destination.getParent)
        Files.copy(source,destination)
      }
    }
    Files.createDirectories(current)
    write(current.resolve("status.txt"), List("RUNNING", java.time.Instant.now().toString))
    try {
      val before = fingerprint(sourceFiles)
      val policyHash = fingerprint(policyInputs)
      val resourcesHash = fingerprint(resourceInputs)
      run("example-preflight", cli ++ List("--rules", "file:tools/refactor/SourceExamplePreflight.scala", "--files", "tools/refactor/SourceExamplePreflight.scala"))
      require(policyInputs.contains(Paths.get("tools/refactor/model-fixture/expected-primitives.tsv")),"Golden fixture expectations missing from policy fingerprint")
      if (policy.compile) run("compile", List("sbt", "--server", "-batch", "set every scalafmtOnCompile := false", "set ThisBuild / scalacOptions += \"-Xsemanticdb\"", "compile", "Test/compile"))
      run("schema-fixtures", cli ++ List("--rules", "file:tools/refactor/SchemaVerification.scala", "--files", "tools/refactor/SchemaVerification.scala"))
      run("model-fixtures", cli ++ List("--rules", "file:tools/refactor/ModelInventoryVerification.scala", "--files", "tools/refactor/ModelInventoryVerification.scala"))
      run("operation-fixtures", cli ++ List("--rules", "file:tools/refactor/OperationInventoryVerification.scala", "--files", "tools/refactor/OperationInventoryVerification.scala"))
      run("copy-fixtures", cli ++ List("--rules", "file:tools/refactor/CopyVerification.scala", "--files", "tools/refactor/CopyVerification.scala"))
      run("copy-multiple-fixtures", cli ++ List("--config", "tools/refactor/copy-multiple-verification.conf", "--files", "tools/refactor/CopyVerification.scala"))
      run("architecture-fixtures", cli ++ List("--rules", "file:tools/refactor/ArchitectureInventoryVerification.scala", "--files", "tools/refactor/ArchitectureInventoryVerification.scala"))
      run("transaction-mode-fixtures", cli ++ List("--rules", "file:tools/refactor/TransactionModeVerification.scala", "--files", "tools/refactor/TransactionModeVerification.scala"))
      run("build-inventory", cli ++ List("--config", "tools/refactor/build-inventory.conf", "--files", "tools/refactor/BuildInventory.scala"))
      run("resource-inventory", cli ++ List("--config", "tools/refactor/resource-inventory.conf", "--files", "tools/refactor/ResourceInventory.scala"))
      run("semantic", cli ++ List("--config", "tools/refactor/assessment-semantic.conf", "--sourceroot", ".", "--auto-classpath", "--auto-classpath-roots", "target/out", "--files", "modules"))
      run("fixtures", cli ++ List("--config", "tools/refactor/assessment-fixtures.conf", "--sourceroot", ".", "--files", "modules", "--files", "tools/refactor/source-fixtures.scala", "--files", "tools/refactor/structure-fixtures.scala"))
      require(before == fingerprint(sourceFiles), "Application sources changed during assessment")
      require(policyHash == fingerprint(policyInputs), "Rule/configuration/fixture changed during assessment")
      require(resourcesHash == fingerprint(resourceInputs), "Build/configuration/object-plan/ledger inputs changed during assessment")
      run("inventory-coverage", cli ++ List("--config", "tools/refactor/inventory-coverage.conf", "--files", "tools/refactor/InventoryCoverage.scala"))
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
      run("capability-history", cli ++ List("--config", "tools/refactor/capability-history.conf", "--files", "tools/refactor/CapabilityHistory.scala"))
      run("isolation-history", cli ++ List("--config", "tools/refactor/isolation-history.conf", "--files", "tools/refactor/IsolationHistory.scala"))
      println("Assessment complete: " + current.resolve("summary.md"))
    } catch {
      case e: Exception =>
        write(current.resolve("status.txt"), List("FAILED", Option(e.getMessage).getOrElse(e.toString), "Do not treat prior reports as current passing evidence."))
        throw e
    }
  }
}
