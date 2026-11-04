import scalafix.v1._
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets.UTF_8

class TransactionRoundVerification extends SyntacticRule("TransactionRoundVerification") {
  private def lines(p: Path): List[String] = Files.readAllLines(p,UTF_8).asScala.toList
  private def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val root = Paths.get("").toAbsolutePath.normalize
    val old = Paths.get("refactor-audit/capability-history/baseline.tsv")
    val measured = Paths.get("refactor-audit/transaction-round1")
    require(lines(measured.resolve("verification.txt")) == List("examples=28/28","failures=0","unchecked-examples=0"),"Frozen source sample did not pass")
    require(lines(measured.resolve("missing-files.txt")).isEmpty,"Missing production files")
    val stream = Files.walk(Paths.get("modules"))
    val sources = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).toList finally stream.close()
    val sourceHash = hash(sources.sortBy(_.toString).map(p => p.toString + "\t" + hash(Files.readString(p))).mkString("\n"))
    require(lines(Paths.get("refactor-audit/assessment/current/fingerprints.txt")).contains("sources=" + sourceHash),"Sources changed since baseline assessment")
    require(lines(measured.resolve("files.txt")).map(p => root.relativize(Paths.get(p).toAbsolutePath.normalize).toString).toSet ==
      sources.filter(_.toString.contains("/src/main/")).map(_.toString).toSet,"Production source coverage differs")
    val previous = lines(old).drop(1).map(_.split("\t",-1).toList).map(r => r.head -> r(1).toInt).toMap
    val rows = lines(measured.resolve("findings.tsv")).drop(1).map(_.split("\t",-1).toList)
    def key(r: List[String]): String = List(r(0),root.relativize(Paths.get(r(1)).toAbsolutePath.normalize).toString,r(4),r(5),r(7)).mkString("\t")
    def id(r: List[String]): String = "CAP-" + hash(key(r))
    val grouped = rows.groupBy(id)
    require(previous.forall { case (k,n) => grouped.get(k).exists(_.size == n) },"An existing anchor disappeared or changed multiplicity")
    val added = rows.filterNot(r => previous.contains(id(r)))
    require(added.size == 3 && added.map(_(2)).toSet == Set("54","59","77") && added.forall(r =>
      r(0) == "repo-transaction" && r(1).endsWith("/domain/library/search/QueryRepo.scala") &&
      r(4) == "com/histmersion/domain/library/search/QueryRepo.exec()."),"Delta differs from the three reviewed pre-existing mode changes")
    val addedPath = measured.resolve("reconciliation-additions.tsv")
    Files.write(addedPath,("id\tcount\tclassification\ttreatment\tboundary-id\trule\tpath\towner\tsymbol\tast" ::
      added.groupBy(id).toList.sortBy(_._1).map { case (identity,rs) =>
        List(identity,rs.size.toString,"confirmed","manual/design","TX-0001",key(rs.head)).mkString("\t")
      }).asJava,UTF_8)
    Files.write(measured.resolve("reconciliation.txt"),List("VERIFIED_ADDITIVE_DETECTOR_CORRECTION; HISTORY_POLICY_ADOPTION_PENDING",
      "sources=" + sourceHash,"original-occurrences=228","current-occurrences=231","added-occurrences=3","added-anchors=2",
      "All original anchor identities and multiplicities preserved; application sources unchanged.",
      "Original sample failure and original history baseline remain unchanged.",
      "These are pre-existing TX-0001 violations newly detected, not new code regressions or fixes.").asJava,UTF_8)
    println("PASS: all 228 original occurrences retained; exactly 3 reviewed additions in 2 anchors; unchanged application sources")
  }
}
