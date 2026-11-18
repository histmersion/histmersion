import scalafix.v1._
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._

class OwnerRelocationVerification extends SyntacticRule("OwnerRelocationVerification") {
  override def afterComplete(): Unit = {
    val root = Paths.get("").toAbsolutePath.normalize
    val fixture = Paths.get("tools/refactor/owner-relocation-fixture")
    val out = Paths.get("refactor-audit/shared-title/fixture-2")
    require(!Files.exists(out), "Preserve fixture observations")
    Files.createDirectories(out)
    def files(p: Path): List[Path] = { val s = Files.walk(p); try s.iterator.asScala.filter(Files.isRegularFile(_)).filter(_.toString.endsWith(".scala")).toList finally s.close() }
    val work = out.resolve("work")
    val input = fixture.resolve("input")
    files(input).foreach { p => val dest = work.resolve(input.relativize(p)); Files.createDirectories(dest.getParent); Files.copy(p,dest) }
    val scala = List("--server=false","-S","3.10.0","--dep","io.github.iltotore::iron:3.4.1","--scalac-option","-source:future","--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",root.toString)
    val cli = List("bash","tools/refactor/scalafix.sh") // the build's Scalafix; the fixture names its own --scala-version
    def run(label: String, command: List[String], success: Boolean = true): Unit = {
      println("Relocation fixture: " + label)
      val code = new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(out.resolve(label + ".log").toFile).start().waitFor()
      require((code == 0) == success, s"$label exit=$code; expected success=$success")
    }
    def config(label: String, mode: String, report: Path): String = {
      val p = out.resolve(label + ".conf")
      Files.writeString(p,s"""rules = ["file:tools/refactor/RelocateOwners.scala"]
RelocateOwners {
mode = "$mode"
report = "$report"
roots = ["$work"]
moves = [{ from = "$work/src/main/scala/Title.scala", to = "$work/src/main/scala/kernel/Title.scala", oldPackage = "relocation.library", newPackage = "relocation.kernel", names = [Title], symbols { "relocation/library/Title$$package.Title#" = Title, "relocation/library/Title$$package.Title." = Title } }]
}
""")
      p.toString
    }
    val cp = out.resolve("classes").toString
    def fix(label: String, mode: String, report: Path, success: Boolean = true): Unit = run(label,cli ++ List("--config",config(label,mode,report),"--scala-version","3.10.0","--sourceroot",".","--classpath",cp,"--files",work.toString),success)
    run("input",List("scala-cli","run",work.resolve("src/main/scala").toString) ++ scala ++ List("--main-class","relocation.client.verifyRelocation","--compilation-output",cp))
    run("expected",List("scala-cli","run",fixture.resolve("expected/src/main/scala").toString) ++ scala ++ List("--main-class","relocation.client.verifyRelocation"))
    fix("check-before","check",out.resolve("check-before"),success=false)
    require(Files.readString(out.resolve("check-before.log")).contains("Old owner reference"))
    fix("preview","preview",out.resolve("preview"))
    fix("apply","apply",out.resolve("preview"))
    val expected = fixture.resolve("expected")
    val expectedPaths = files(expected).filterNot(_.toString.contains("/.scala-build/")).map(expected.relativize).toSet
    val actualPaths = files(work).filterNot(_.toString.contains("/.scala-build/")).map(work.relativize).toSet
    require(expectedPaths == actualPaths,"Relocated path set differs")
    expectedPaths.foreach(p => require(Files.readString(expected.resolve(p)) == Files.readString(work.resolve(p)),"Exact output differs: " + p))
    run("output",List("scala-cli","run",work.resolve("src/main/scala").toString) ++ scala ++ List("--main-class","relocation.client.verifyRelocation","--compilation-output",cp))
    fix("check-after","check",out.resolve("check-after"))
    val before = files(work).filterNot(_.toString.contains("/.scala-build/")).map(p => p -> Files.readString(p)).toMap
    fix("apply-repeat","apply",out.resolve("preview"))
    require(before.forall { case(p,s) => Files.readString(p) == s })
    Files.writeString(out.resolve("status.txt"),"PASS\nInput/expected/output compile and run; exact AST patch output; old owner rejected before, absent after; repeated application unchanged.\n")
  }
}
