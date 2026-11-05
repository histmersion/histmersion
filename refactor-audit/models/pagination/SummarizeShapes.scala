import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class SummarizeShapes extends SyntacticRule("SummarizeShapes") {
  override def afterComplete(): Unit = {
    val root = Paths.get("refactor-audit/models")
    def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val uses = rows(root.resolve("uses.tsv")).map(r => (r.head,r(1),r(2))).toSet
    val shapes = rows(root.resolve("pagination/shapes-1/evidence.tsv"))
    val generated = shapes.filter(r => r(3).contains("#copy().") || r(3).contains(".apply()."))
    val missing = generated.filterNot(r => uses((r(3),r.head,r(1))))
    require(missing.nonEmpty,"Expected generated-member coverage gap needs re-evaluation")
    val declared = rows(root.resolve("positions.tsv")).map(_(7)).toSet ++ rows(root.resolve("types.tsv")).map(_.head)
    require(missing.forall(r => !declared(r(3))),"Missing use has a source declaration; investigate a separate gap")
    val summary = generated.groupBy(r => List(if(r.head.contains("/src/test/")) "test" else "production",
      if(r(3).contains("#copy().")) "copy" else "apply",if(r(8) == "unresolved") "unresolved-signature" else "resolved-signature")).toList.sortBy(_._1.mkString("\t")).map { case(k,rs) =>
      (k ++ List(rs.size.toString,rs.count(r => !uses((r(3),r.head,r(1)))).toString)).mkString("\t")
    }
    val out = root.resolve("pagination/shapes-1")
    Files.writeString(out.resolve("generated-members.tsv"),("scope\tkind\tsignature-status\tnames\tabsent-from-use-inventory" :: summary).mkString("\n") + "\n")
    Files.writeString(out.resolve("generated-member-gap.txt"),missing.size + " generated member/parameter names absent from existing use inventory.\n" +
      "All missing symbols are absent from the source declaration catalogue; ModelInventory filters uses to that catalogue. Some generated symbols additionally lack signatures.\n" +
      "These are not independent violations or independent quality samples. Match copy/apply symbols and constructor metadata without requiring symbol.info; fail closed when argument mapping cannot be established.\n")
    println(summary.mkString("\n"))
  }
}
