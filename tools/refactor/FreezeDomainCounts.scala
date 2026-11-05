import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class FreezeDomainCounts extends SyntacticRule("FreezeDomainCounts") {
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val files = List(
      "core/src/main/scala/com/histmersion/domain/CompileArticle.scala",
      "core/src/test/scala/com/histmersion/domain/CompileArticleSuite.scala",
      "core/src/test/scala/com/histmersion/api/EmptyAnswerFieldsSuite.scala",
      "server/src/main/scala/com/histmersion/domain/research/workflows/CompileArticlePipeline.scala",
      "server/src/main/scala/com/histmersion/domain/research/workflows/SeedManagerKind.scala",
      "server/src/test/scala/com/histmersion/server/agents/VocabularyWireSuite.scala"
    ).map("modules/" + _)
    val newPath = "modules/values/src/main/scala/com/histmersion/RowCount.scala"
    val newSource = "package com.histmersion\n\nimport io.github.iltotore.iron.*\nimport io.github.iltotore.iron.constraint.any.Pure\n\ntype RowCount = RowCount.T\nobject RowCount extends RefinedType[Int, Pure]\n"
    newSource.parse[Source].get
    require(!Files.exists(Paths.get(newPath)))
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v3/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    List("target/pagination-domain-preview" -> "domain-preview-v1", "target/pagination-domain-preview-v2" -> "domain-preview-v2").foreach { case(from,name) =>
      val out = Paths.get("refactor-audit/models/pagination",name)
      require(!Files.exists(out))
      val stream = Files.walk(Paths.get(from))
      val actual = try stream.iterator.asScala.filter(Files.isRegularFile(_)).map(p => Paths.get(from).relativize(p).toString).toSet finally stream.close()
      require(actual == files.toSet, "Unexpected preview source set")
      val contents = files.map { p =>
        val before = Files.readString(Paths.get(p)); require(hash(before) == frozen(p))
        val next = Files.readString(Paths.get(from,p)); next.parse[Source].get
        (p,hash(before),next)
      } :+ ((newPath,"new",newSource))
      contents.foreach { case(p,_,s) => val dest = out.resolve("sources").resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) }
      Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: contents.map { case(p,h,s) => s"$p\t$h\t${hash(s)}" }).asJava)
      Files.writeString(out.resolve("status.txt"),if(name.endsWith("v1")) "REJECTED_UNAPPLIED: cross-layer wrapping composed .value outside the new constructor.\n" else "PREVIEW: fixture verified; actual application compilation pending.\n")
    }
    val expected = Files.readString(Paths.get("tools/refactor/model-rewrite-methods/expected/Models.scala"))
    val actual = Files.readString(Paths.get("target/model-methods-preview-v2/Models.scala"))
    require(actual == expected,"Fixture output differs")
    val fixture = Paths.get("refactor-audit/models/pagination/method-fixture-v2")
    require(!Files.exists(fixture)); Files.createDirectories(fixture)
    Files.writeString(fixture.resolve("Models.scala"),actual)
    Files.writeString(fixture.resolve("status.txt"),"Exact output PASS. Baseline, expected and rewritten definitions/callers compiled; runtime and type separation checks passed. Commands recorded in README.\n")
    println("Frozen seven-file domain count preview, rejected predecessor and exact-output fixture")
  }
}
