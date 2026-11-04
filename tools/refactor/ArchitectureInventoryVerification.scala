import scalafix.v1._
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

class ArchitectureInventoryVerification extends SyntacticRule("ArchitectureInventoryVerification") {
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/architecture/verification.txt")
    Files.createDirectories(report.getParent); Files.writeString(report,"RUNNING\n")
    val logs = Paths.get("target/architecture-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Architecture inventory verification: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == expected,s"$label exited $code, expected $expected; see $logs/$label.log")
    }
    val source = "tools/refactor/architecture-fixture/src/main/scala"
    run("compile",List("scala-cli","compile",source,"--server=false","-S","3.10.0","--scalac-option","-source:future",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString,
      "--dep","io.github.iltotore::iron:3.4.1","--compilation-output","target/architecture-fixture-classes"))
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    val args = List("--config","tools/refactor/architecture-fixture/inspect.conf","--scala-version","3.10.0","--sourceroot",".","--classpath","target/architecture-fixture-classes","--files")
    run("positive",cli ++ args ++ List(source))
    def table(name: String) = Files.readAllLines(Paths.get("target/architecture-fixture-report",name),UTF_8).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val services = table("services.tsv")
    val service = services.find(_.head == "com/histmersion/inventoryfixture/SampleService#").get
    require(service(6).split(";").toSet == Set("com/histmersion/inventoryfixture/SampleService.Implementation#","com/histmersion/inventoryfixture/SampleService.InMemory#"),"Incorrect semantic implementation edges: " + service)
    require(service(7).endsWith("SampleService.Implementation#") && service(8).endsWith("SampleService.InMemory#"))
    require(!service(6).contains("Unrelated"),"Name-only InMemory false positive")
    require(!service(6).contains("Wrapped"),"Parent type argument incorrectly treated as implemented parent")
    val obligations = table("service-obligations.tsv").filter(_.head == "com/histmersion/inventoryfixture/SampleService#").map(r => r(2) -> r(3)).toMap
    require(obligations("service-trait") == "structural-evidence-present")
    require(obligations("companion-Implementation") == "structural-evidence-present")
    require(obligations("companion-InMemory") == "structural-evidence-present")
    require(obligations("private-Implementation") == "required-shape-missing","Public implementation wrongly accepted")
    require(obligations("behavioral-parity").startsWith("unproven"),"Structure wrongly accepted as behavioral parity")
    val good = table("service-obligations.tsv").filter(_.head == "com/histmersion/inventoryfixture/GoodService#").map(r => r(2) -> r(3)).toMap
    require(List("service-trait","companion-Implementation","private-Implementation","companion-InMemory").forall(c => good(c) == "structural-evidence-present"),"Private indirect implementation rejected: " + good)
    require(table("service-obligations.tsv").exists(r => r.head == "com/histmersion/inventoryfixture/QualifiedService#" && r(2) == "private-Implementation" && r(3) == "structural-evidence-present"),"Companion-qualified private implementation rejected")
    val plan = table("object-plan.tsv").map(r => r(2) -> r(7)).toMap
    require(plan("`Retained`").startsWith("original-object-present"),"Original object missed")
    require(plan("`Moved`").startsWith("same-name-candidates"),"Relocated name not retained for review")
    require(plan("`Missing`").startsWith("original-absent"),"Missing original incorrectly completed")
    val declarations = table("declarations.tsv")
    val models = table("model-construction.tsv").map(r => r.head -> r).toMap
    require(models("com/histmersion/inventoryfixture/Record#")(5) == "private","Private constructor access lost")
    require(models("com/histmersion/inventoryfixture/Record#")(8) == "required-invariant; preservation-unproven","Private constructor falsely proves invariant")
    require(models("com/histmersion/inventoryfixture/Unchecked#")(8) == "required-invariant; public-constructor-review","Public invariant constructor missed")
    require(models("com/histmersion/inventoryfixture/Plain#")(8) == "configured-no-aggregate-invariant","Plain record falsely requires smart constructor")
    val sites = table("construction-sites.tsv")
    require(sites.exists(r => r(4) == "new" && r(5).contains("inventoryfixture/Record#")),"Explicit constructor missing")
    require(sites.exists(r => r(4) == "companion-apply-candidates" && r(5).contains("inventoryfixture/Plain#")),"Generated apply missing")
    require(sites.exists(r => r(4) == "copy" && r(5).contains("inventoryfixture/Plain#")),"Generated copy missing")
    require(sites.exists(r => r(3).endsWith("PlainFactory.") && r(5).contains("inventoryfixture/Plain#")),"Companion apply target inferred from name instead of result signature")
    val factories = table("factory-candidates.tsv")
    require(factories.exists(r => r(1).endsWith("Record.from().") && r(6).contains("invariant-unproven")),"Either factory falsely proves validation")
    require(factories.exists(r => r(0).endsWith("Record#") && r(1).endsWith("Construction.delegated().") && r(6).startsWith("returns-model;")),"Alias-returning delegated factory missing")
    val ownership = table("type-ownership.tsv").map(r => r.head -> r).toMap
    def owned(name: String) = ownership("com/histmersion/inventoryfixture/" + name)
    require(owned("Entity.Kind#")(7) == "configured-owner-satisfied","Correct companion property rejected")
    require(owned("EntityPatch#")(7) == "configured-owner-violation","Top-level property accepted")
    require(owned("UnprefixedProperty#")(7) == "configured-owner-violation","Non-prefixed property missed")
    require(owned("EntityService#")(7) == "top-level-owner-review" && owned("EntityService#")(8).contains("Entity."),"Prefix hypothesis silently approved as ownership")
    require(declarations.filter(_(7) == "enum-case").map(_(6)).toSet == Set("Active","Done","Failed"),"Enum cases missing")
    require(declarations.count(_(7) == "given-template") == 1,"Template given missing")
    require(declarations.exists(r => r(6) == "Record" && r(7) == "case-class" && r(12).contains("private")),"Constructor visibility lost")
    require(table("references.tsv").exists(r => r(3) == "com/histmersion/inventoryfixture/SampleService#read()." && r(6) == "project-declaration"),"Call edge unresolved")
    val groups = table("file-groups.tsv")
    require(groups.exists(r => r.head.endsWith("/Outcome.scala") && r(3) == "single-logical-group" && r(5).contains("sealed-parent")),"Sealed family misclassified")
    require(groups.exists(r => r.head.endsWith("/Code.scala") && r(3) == "single-logical-group" && r(5).contains("Iron-constraint")),"Newtype/constraint/companion group lost")
    require(groups.exists(r => r.head.endsWith("/Unrelated.scala") && r(3).startsWith("multiple-logical-groups")),"Unrelated declarations merged")
    val visibility = table("visibility-candidates.tsv").map(r => r.head -> r).toMap
    val crossings = table("dependency-crossings.tsv")
    require(crossings.exists(r => r(5).endsWith("Consumer.read().") && r(6).endsWith("low/Contract#read().") && r(10).startsWith("configured-contract")),"Configured contract member not recognized")
    require(crossings.exists(r => r(5).endsWith("Gateway.back().") && r(10) == "forbidden-direction"),"Public contract incorrectly waived forbidden direction")
    require(crossings.exists(r => r(5).endsWith("Gateway.permitted().") && r(10) == "configured-crossing-permit"),"Exact crossing permit missed")
    require(crossings.exists(r => r(5).endsWith("Consumer.inferred().") && r(6).endsWith("low/Internal#") && r(7) == "declared-or-inferred-signature" && r(10) == "allowed-direction; contract-review"),"Inferred dependency not checked")
    require(crossings.exists(r => r(5).endsWith("Consumer.aliased().") && r(6).endsWith("low/Internal#") && r(7).contains("alias=")),"Alias-hidden crossing missed")
    require(visibility("com/histmersion/inventoryfixture/visibility/HiddenRepo.")(8) == "feature-restricted","Feature privacy missed")
    require(visibility("com/histmersion/inventoryfixture/visibility/BroadRepo.")(8) == "broader-than-feature","Broad package privacy accepted")
    require(visibility("com/histmersion/inventoryfixture/visibility/Envelope.NestedRow#")(8) == "feature-restricted","Enclosing privacy lost")
    require(visibility("com/histmersion/inventoryfixture/visibility/Storage.")(7).contains("configured-owner"),"Non-suffix target missed")
    require(visibility("com/histmersion/inventoryfixture/visibility/ConnectionOwner#")(7).contains("database-dependency"),"Database owner without naming convention missed")
    val consumers = table("visibility-consumers.tsv")
    require(consumers.exists(r => r.head.endsWith("PublicRepo.ResultRow#") && r(5).endsWith("Consumer.inferred().") && r(7) == "declared-or-inferred-signature" && r(8) == "outside-expected-feature"),"Inferred row escape missed")
    require(consumers.exists(r => r.head.endsWith("PublicRepo.ResultRow#") && r(5).endsWith("Consumer.aliased().") && r(7).contains("alias=") && r(8) == "outside-expected-feature"),"Alias-hidden row escape missed")
    require(!consumers.exists(r => r.head.endsWith("PublicRepo.ResultRow#") && r(5).endsWith("Consumer.opaqueValue().")),"Opaque representation leaked into consumer inventory")
    run("omitted-file",cli ++ args ++ List(source + "/Services.scala"),1)
    run("absent-service",cli ++ args.updated(1,"tools/refactor/architecture-fixture/reject-service.conf") ++ List(source),1)
    run("absent-visibility",cli ++ args.updated(1,"tools/refactor/architecture-fixture/reject-visibility.conf") ++ List(source),1)
    run("absent-property",cli ++ args.updated(1,"tools/refactor/architecture-fixture/reject-property.conf") ++ List(source),1)
    run("absent-contract",cli ++ args.updated(1,"tools/refactor/architecture-fixture/reject-contract.conf") ++ List(source),1)
    run("absent-invariant",cli ++ args.updated(1,"tools/refactor/architecture-fixture/reject-invariant.conf") ++ List(source),1)
    Files.write(report,List("PASS","Resolved implementations exclude unrelated same-name InMemory and parent type arguments","Public implementation rejected; private indirect implementation accepted","Behavioral parity remains unproven despite matching structure","Constructor visibility and method-reference edges retained","Every object-plan row reconciled; missing originals never count as completion","Omitted source file and absent configured service rejected").asJava,UTF_8)
  }
}
