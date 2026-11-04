import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TransactionGuardCorrection extends SyntacticRule("TransactionGuardCorrection") {
  override def afterComplete(): Unit = {
    val base = Paths.get("refactor-audit/capability-history")
    val out = base.resolve("corrections/transaction-repair")
    require(!Files.exists(out), "Never overwrite a policy correction")
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val names = List("CapabilityAccess.scala", "capabilities.conf", "capabilities-source.conf", "capability-source-examples.conf")
    val files = names.map(n => n -> Files.readString(Paths.get("tools/refactor", n))).toMap
    val oldRow = """  { path = "modules/db/src/main/scala/com/histmersion/domain/library/search/QueryRepo.scala", line = 39, snippet = "exec(text, timeoutMs, maxRows, maxCellChars)", rule = repo-transaction, count = 1, reason = "Resolved helper chain reaches Connection.setAutoCommit." },"""
    val newRow = """  { path = "modules/db/src/main/scala/com/histmersion/domain/library/search/QueryRepo.scala", line = 34, snippet = "exec(text, maxRows, maxCellChars)", rule = repo-transaction, count = 0, reason = "TX-0001 repaired: execution helper borrows DbCon; QueryService selects the transaction. Positive helper-chain cases remain in transaction-mode-fixture." },"""
    val current = files(names.last)
    require(current.linesIterator.count(_ == newRow) == 1 && !current.contains(oldRow))
    val prior = current.replace(newRow, oldRow)
    def policy(inputs: Map[String, String]): String = hash(names.map(n => n + "\t" + hash(inputs(n))).mkString("\n"))
    val previousContract = Files.readAllLines(base.resolve("adoptions/transaction-round1/contract.txt")).asScala.toList
    require(previousContract.contains("detector-and-policy=" + policy(files.updated(names.last, prior))), "Correction changes more than the reviewed source assertion")
    require(Files.readAllLines(Paths.get("refactor-audit/transaction-repair/identity-verification.txt")).get(0) == "PASS")
    require(Files.readAllLines(Paths.get("refactor-audit/transaction-mode-fixture/round-trip.txt")).get(0) == "PASS")
    Files.createDirectories(out)
    Files.writeString(out.resolve("before-examples.conf"), prior)
    Files.writeString(out.resolve("after-examples.conf"), current)
    Files.write(out.resolve("previous-contract.txt"), previousContract.asJava)
    Files.write(out.resolve("contract.txt"), previousContract.map(s => if(s.startsWith("detector-and-policy=")) "detector-and-policy=" + policy(files) else s).asJava)
    Files.write(out.resolve("event.txt"), List("SOURCE_ASSERTION_CORRECTION_AFTER_VERIFIED_REPAIR", "TX-0001", "Only the repaired QueryRepo helper assertion changes from positive to negative; all other assertions and detector rules remain identical.", "Positive direct and helper-mediated rejection fixtures still pass; no findings or denominators removed from baseline.", "before-examples-sha256=" + hash(prior), "after-examples-sha256=" + hash(current), "baseline-sha256=" + hash(Files.readString(base.resolve("baseline.tsv")))).asJava)
    println("PASS: recorded one source assertion correction; detector unchanged")
  }
}
