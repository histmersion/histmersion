import scalafix.v1._
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._

class CapabilityHistoryVerification extends SyntacticRule("CapabilityHistoryVerification") {
  override def afterComplete(): Unit = {
    val root = Paths.get("target/capability-history-verification",java.time.Instant.now().toString.replace(':','-'))
    val report = root.resolve("report")
    var ledger = root.resolve("ledger")
    def write(p: Path, rows: List[String]): Unit = { Files.createDirectories(p.getParent); Files.write(p,rows.asJava) }
    def lines(p: Path): List[String] = Files.readAllLines(p).asScala.toList
    val original = Paths.get("refactor-audit/assessment/current")
    List("status.txt","fingerprints.txt","capabilities/findings.tsv","capabilities/files.txt","capabilities/missing-files.txt","capabilities/rules.txt").foreach { name =>
      val dst = report.resolve(name); Files.createDirectories(dst.getParent); Files.copy(original.resolve(name),dst)
    }
    List("findings.tsv","files.txt","missing-files.txt","rules.txt","identity-policy.txt").foreach { name =>
      Files.copy(Paths.get("refactor-audit/transaction-round1",name),report.resolve("capabilities").resolve(name),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    val fingerprints = lines(report.resolve("fingerprints.txt"))
    val input = report.resolve("capabilities/findings.tsv")
    val frozen = lines(input)
    val header = frozen.head
    val data = frozen.tail
    val selected = data.indexWhere { line =>
      val r = line.split("\t",-1)
      !r(4).startsWith("local") && data.count { other =>
        val q = other.split("\t",-1); List(0,1,4,5,7).forall(i => r(i) == q(i))
      } == 1
    }
    require(selected >= 0,"Need an independently identifiable fixture observation")
    val cli = List("bash","tools/refactor/scalafix.sh") // the build's Scalafix; the fixture names its own --scala-version
    def run(label: String, mode: String, expected: Int, extra: List[String] = Nil): Unit = {
      val conf = root.resolve(label + ".conf")
      write(conf,List("rules = [\"file:tools/refactor/CapabilityHistory.scala\"]",s"CapabilityHistory { mode = $mode, report = \"$report\", ledger = \"$ledger\" }") ++ extra)
      val log = root.resolve(label + ".log")
      val code = new ProcessBuilder((cli ++ List("--config",conf.toString,"--scala-version","3.10.0","--files","tools/refactor/CapabilityHistory.scala")).asJava).redirectErrorStream(true).redirectOutput(log.toFile).start().waitFor()
      require(code == expected,s"$label: got $code, expected $expected; $log")
      println(label + ": PASS")
    }
    run("capture","capture",0)
    val baseline = Files.readString(ledger.resolve("baseline.tsv"))
    run("overwrite-refused","capture",1)
    run("unchanged","check",0)
    val moved = data.map { line => val r = line.split("\t",-1); r(2) = (r(2).toInt + 100).toString; r.mkString("\t") }
    write(input,header :: moved)
    run("line-movement","check",0)
    val replaced = data(selected).split("\t",-1); replaced(4) = "fixture/Replacement.run()."
    write(input,header :: data.updated(selected,replaced.mkString("\t")))
    run("equal-count-replacement","check",1)
    write(input,header :: (data :+ data(selected)))
    run("duplicate-insertion","check",1)
    write(input,header :: data.patch(selected,Nil,1))
    run("absence-not-verified-fix","check",0)
    write(input,frozen)
    run("reappearance","check",1)
    require(Files.readString(ledger.resolve("baseline.tsv")) == baseline,"Frozen baseline changed")
    write(report.resolve("capabilities/missing-files.txt"),List("fixture/missing.scala"))
    run("missing-coverage","check",1)
    write(report.resolve("capabilities/missing-files.txt"),Nil)
    write(report.resolve("fingerprints.txt"),List("sources=stale"))
    run("stale-evidence","check",1)
    write(report.resolve("fingerprints.txt"),fingerprints)
    val policyStamp = lines(report.resolve("capabilities/identity-policy.txt"))
    write(report.resolve("capabilities/identity-policy.txt"),List("stale"))
    run("stale-detector","check",1)
    write(report.resolve("capabilities/identity-policy.txt"),policyStamp)
    ledger = root.resolve("adoption-ledger")
    write(input,header :: data.patch(selected,Nil,1))
    run("adoption-baseline","capture",0)
    val priorBaseline = Files.readString(ledger.resolve("baseline.tsv"))
    val previousContract = lines(ledger.resolve("contract.txt"))
    write(ledger.resolve("contract.txt"),previousContract.init :+ "detector-and-policy=fixture-previous-version")
    val adoption = root.resolve("adoption")
    val addition = root.resolve("reviewed.tsv")
    val sourceEvidence = root.resolve("source-evidence.txt")
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val r = data(selected).split("\t",-1)
    val key = List(r(0),Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(r(1)).toAbsolutePath.normalize).toString,r(4),r(5),r(7)).mkString("\t")
    write(addition,List("id\tcount\tclassification\ttreatment\tboundary-id\trule\tpath\towner\tsymbol\tast",s"CAP-${hash(key)}\t1\tconfirmed\ttargeted\tFIXTURE\t$key"))
    write(sourceEvidence,fingerprints.filter(_.startsWith("sources=")))
    val adoptionSettings = List(s"CapabilityHistory.adoption = \"$adoption\"",s"CapabilityHistory.additions = \"$addition\"",
      s"CapabilityHistory.sourceEvidence = \"$sourceEvidence\"","CapabilityHistory.reason = \"Synthetic additive detector correction\"")
    write(input,header :: (data :+ replaced.mkString("\t")))
    run("adoption-unreviewed-addition","adopt",1,adoptionSettings)
    require(!Files.exists(adoption),"Rejected adoption wrote an event")
    write(input,frozen)
    run("adoption-reviewed-only","adopt",0,adoptionSettings)
    run("adoption-overwrite-refused","adopt",1,adoptionSettings)
    run("adopted-check","check",0,List(s"CapabilityHistory.adoption = \"$adoption\""))
    write(input,header :: (data :+ replaced.mkString("\t")))
    run("adopted-unrelated-regression","check",1,List(s"CapabilityHistory.adoption = \"$adoption\""))
    require(Files.readString(ledger.resolve("baseline.tsv")) == priorBaseline,"Adoption changed original baseline")
    write(Paths.get("refactor-audit/capability-history/verification.txt"),List("PASS",root.toString,
      "Unchanged and line-shifted observations pass; frozen baseline overwrite is rejected.",
      "Equal-count replacement, duplicate insertion and reappearance fail.",
      "Missing coverage and stale source evidence fail; baseline remains byte-for-byte unchanged.",
      "Stale detector versions fail. Reviewed additive adoption passes; unreviewed additions, event overwrite and unrelated later regressions fail.",
      "Synthetic report mutations test history enforcement, not detector precision or recall."))
  }
}
