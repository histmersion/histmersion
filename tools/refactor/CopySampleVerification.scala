import scalafix.v1._
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder,Configured,generic}

object CopySampleVerification {
  case class Policy(sample: String = "tools/refactor/copy-holdout.tsv", report: String = "refactor-audit/copy-migration", role: String = "development-regression-after-round-one")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CopySampleVerification(val policy: CopySampleVerification.Policy) extends SyntacticRule("CopySampleVerification") {
  def this() = this(CopySampleVerification.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("CopySampleVerification")(CopySampleVerification.Policy()).map(new CopySampleVerification(_))
  override def afterComplete(): Unit = {
    def rows(path: String) = Files.readAllLines(Paths.get(path),UTF_8).asScala.drop(1).map(_.split("\t",-1).toList).toList
    require(Files.readString(Paths.get("refactor-audit/operations/status.txt")).startsWith("COMPLETE"),"Operation scan incomplete")
    val samples = rows(policy.sample)
    val findings = rows("refactor-audit/operations/occurrences.tsv").filter(r => r(0) == "copy" && r(1) == "case-class-copy")
    val manifest = rows("refactor-audit/operations/sources.tsv").map(r => r.head -> r(1)).toMap
    val measured = samples.map { s =>
      val path = Paths.get(s.head).toAbsolutePath.normalize
      val source = Files.readString(path)
      val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
      require(manifest.get(path.toString).contains(hash),"Stale sample source: " + path)
      require(source.linesIterator.drop(s(1).toInt-1).next().contains(s(3)),"Sample source guard changed: " + s)
      val actual = findings.count(r => r(2) == path.toString && r(4) == s(1))
      (s,s(2).toInt,actual)
    }
    val tp = measured.map { case(_,expected,actual) => expected.min(actual) }.sum
    val fn = measured.map { case(_,expected,actual) => (expected-actual).max(0) }.sum
    val fp = measured.map { case(_,expected,actual) => (actual-expected).max(0) }.sum
    val tn = measured.count { case(_,expected,actual) => expected == 0 && actual == 0 }
    val precision = tp.toDouble / (tp+fp)
    val recall = tp.toDouble / (tp+fn)
    val out = Paths.get(policy.report); Files.createDirectories(out)
    Files.write(out.resolve("holdout-results.tsv"),("path\tline\texpected\tactual" :: measured.map { case(s,e,a) => List(s.head,s(1),e.toString,a.toString).mkString("\t") }).asJava,UTF_8)
    Files.writeString(out.resolve("quality.txt"),s"role=${policy.role}\ntp=$tp\nfp=$fp\nfn=$fn\ntn=$tn\nprecision=$precision\nrecall=$recall\nIndependent source audit; ${samples.size} grouped locations, not a random population sample. Limited negative diversity. Unclassified dependency-owned Printer.copy excluded explicitly. No population confidence claim.\n")
    require(precision >= 0.95 && recall >= 0.95,"Copy detector misses holdout target; do not change the labels to fit")
  }
}
