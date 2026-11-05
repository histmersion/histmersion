import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object AgentStoragePolicyCorrection {
  case class Policy(phase: String = "capture", correction: String = "agent-storage", predecessor: String = "transaction-repair",
    permissionLine: String = "{ id = plugin-generator-exit, owner = \"com/histmersion/server/agents/GeneratePluginMain$package.generatePlugin().\", capabilities = [lifecycle], reason = \"The generator command entry maps a returned generation error to process exit; domain code returns Either.\" },",
    description: String = "Repaired AgentTrees, PluginTree and Prompts guards follow their new source locations; generator exit is permitted only at the resolved command entry.")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class AgentStoragePolicyCorrection(val policy: AgentStoragePolicyCorrection.Policy) extends SyntacticRule("AgentStoragePolicyCorrection") {
  def this() = this(AgentStoragePolicyCorrection.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("AgentStoragePolicyCorrection")(AgentStoragePolicyCorrection.Policy()).map(new AgentStoragePolicyCorrection(_))
  override def afterComplete(): Unit = {
    require(Set("capture", "seal")(policy.phase))
    val base = Paths.get("refactor-audit/capability-history")
    require(List(policy.correction, policy.predecessor).forall(_.matches("[a-z-]+")))
    val out = base.resolve("corrections/" + policy.correction)
    val names = List("CapabilityAccess.scala", "capabilities.conf", "capabilities-source.conf", "capability-source-examples.conf")
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def fingerprint(folder: java.nio.file.Path): String = hash(names.map(n => n + "\t" + hash(Files.readString(folder.resolve(n)))).mkString("\n"))
    val current = Paths.get("tools/refactor")
    val predecessor = base.resolve("corrections/" + policy.predecessor + "/contract.txt")
    if(policy.phase == "capture") {
      require(!Files.exists(out), "Never replace a policy correction")
      require(Files.readAllLines(predecessor).asScala.contains("detector-and-policy=" + fingerprint(current)), "Unexpected predecessor policy")
      Files.createDirectories(out.resolve("before"))
      names.foreach(n => Files.copy(current.resolve(n), out.resolve("before").resolve(n)))
      Files.copy(predecessor, out.resolve("previous-contract.txt"))
      Files.copy(current.resolve(names.last), out.resolve("before-examples.conf"))
    } else {
      require(!Files.exists(out.resolve("event.txt")), "Correction already sealed")
      require(Files.readString(current.resolve(names.head)) == Files.readString(out.resolve("before").resolve(names.head)), "Detector implementation changed")
      require(Files.readString(current.resolve("capabilities-source.conf")) == Files.readString(out.resolve("before/capabilities-source.conf")), "Source policy include changed")
      val permission = "    " + policy.permissionLine + "\n"
      val configured = Files.readString(current.resolve("capabilities.conf"))
      require(configured.contains(permission) && configured.replace(permission, "") == Files.readString(out.resolve("before/capabilities.conf")), "Only the reviewed exact-entry permission may change")
      val contract = Files.readAllLines(out.resolve("previous-contract.txt")).asScala.toList
      require(contract.contains("detector-and-policy=" + fingerprint(out.resolve("before"))), "Predecessor snapshot changed")
      Files.createDirectories(out.resolve("after"))
      names.foreach(n => Files.copy(current.resolve(n), out.resolve("after").resolve(n)))
      Files.copy(current.resolve(names.last), out.resolve("after-examples.conf"))
      Files.write(out.resolve("contract.txt"), contract.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + fingerprint(current) else s).asJava)
      Files.write(out.resolve("event.txt"), List("VERIFIED_REPAIR_ASSERTIONS_AND_EXACT_ENTRY_PERMISSION",
        policy.description,
        "before-examples-sha256=" + hash(Files.readString(out.resolve("before-examples.conf"))),
        "after-examples-sha256=" + hash(Files.readString(out.resolve("after-examples.conf"))),
        "baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    }
    println("Policy correction " + policy.phase + ": original baseline preserved")
  }
}
