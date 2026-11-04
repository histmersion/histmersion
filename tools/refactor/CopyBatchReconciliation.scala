import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class CopyBatchReconciliation extends SyntacticRule("CopyBatchReconciliation") {
  override def afterComplete(): Unit = {
    val root = Paths.get("refactor-audit/copy-migration")
    def rows(name: String) = Files.readAllLines(root.resolve(name)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val applied = rows("applied/findings.tsv").filter(_(4) == "eligible")
    val remaining = rows("inspect/findings.tsv").count(_(4) == "eligible")
    require(remaining == 0,"Eligible copies remain after the batch")
    val grouped = applied.groupBy(r => List(r(0),r(1),r(3),r(5),r(6)))
    val duplicates = grouped.toList.filter(_._2.size > 1).sortBy(_._1.head)
    Files.write(root.resolve("same-line-duplicates.tsv"),
      ("path\tline\tpatches\toffsets\tsource" :: duplicates.map { case(key, rs) =>
        List(key(0),key(1),rs.size.toString,rs.map(_(2)).mkString(","),key(3)).mkString("\t")
      }).asJava)
    val result = List("applied-patches=" + applied.size,"distinct-line-expressions=" + grouped.size,
      "remaining-eligible=" + remaining,"duplicate-line-expression-groups=" + duplicates.size)
    Files.write(root.resolve("reconciliation.txt"),result.asJava)
    result.foreach(println)
  }
}
