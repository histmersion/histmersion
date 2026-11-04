import scalafix.v1._
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._

class CapabilityHistoryVerification extends SyntacticRule("CapabilityHistoryVerification") {
  override def afterComplete(): Unit = {
    val root = Paths.get("target/capability-history-verification",java.time.Instant.now().toString.replace(':','-'))
    val report = root.resolve("report")
    val ledger = root.resolve("ledger")
    def write(p: Path, rows: List[String]): Unit = { Files.createDirectories(p.getParent); Files.write(p,rows.asJava) }
    def lines(p: Path): List[String] = Files.readAllLines(p).asScala.toList
    val original = Paths.get("refactor-audit/assessment/current")
    List("status.txt","fingerprints.txt","capabilities/findings.tsv","capabilities/files.txt","capabilities/missing-files.txt","capabilities/rules.txt").foreach { name =>
      val dst = report.resolve(name); Files.createDirectories(dst.getParent); Files.copy(original.resolve(name),dst)
    }
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
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    def run(label: String, mode: String, expected: Int): Unit = {
      val conf = root.resolve(label + ".conf")
      write(conf,List("rules = [\"file:tools/refactor/CapabilityHistory.scala\"]",s"CapabilityHistory { mode = $mode, report = \"$report\", ledger = \"$ledger\" }"))
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
    write(Paths.get("refactor-audit/capability-history/verification.txt"),List("PASS",root.toString,
      "Unchanged and line-shifted observations pass; frozen baseline overwrite is rejected.",
      "Equal-count replacement, duplicate insertion and reappearance fail.",
      "Missing coverage and stale source evidence fail; baseline remains byte-for-byte unchanged.",
      "Synthetic report mutations test history enforcement, not detector precision or recall."))
  }
}
