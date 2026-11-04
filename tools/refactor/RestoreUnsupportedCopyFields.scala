import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory

class RestoreUnsupportedCopyFields extends SyntacticRule("RestoreUnsupportedCopyFields") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val reserved = Set("at","index","atOrElse","each","eachWhere","when","eachLeft","eachRight")
  private lazy val deferred = ConfigFactory.parseFile(Paths.get("tools/refactor/copy-to-lens.conf").toFile).resolve().getConfig("CopyToLens.deferredOwners").root().keySet().asScala.toSet
  private def decode(s: String): String = {
    val out = new StringBuilder; var i = 0
    while(i < s.length) {
      if(s(i) == '\\' && i+1 < s.length) { i += 1; out.append(s(i) match { case 'n' => '\n'; case 't' => '\t'; case 'r' => '\r'; case c => c }) }
      else out.append(s(i))
      i += 1
    }
    out.toString
  }
  private lazy val planned = Files.readAllLines(Paths.get("refactor-audit/copy-migration/test-applied/findings.tsv")).asScala.drop(1).flatMap { line =>
    val r = line.split("\t",-1)
    if(r(4) != "eligible") None else {
      val source = decode(r(5))
      source.parse[Term].toOption.collect {
        case Term.Apply(fun,List(Term.Assign(field: Term.Name,value))) if reserved(field.value) || deferred(r(3).stripSuffix("copy().")) =>
          val receiver = fun match { case Term.Select(q,_) => q.syntax; case _ => "this" }
          val generated = s"$receiver.modify(_.${field.syntax}).setTo(${value.syntax})".parse[Term].get.structure
          (r(0),generated) -> source
      }
    }
  }.toMap
  private val restored = java.util.concurrent.ConcurrentHashMap.newKeySet[(String,String)]()
  private val patches = new java.util.concurrent.atomic.AtomicInteger()
  private lazy val originals = planned.map { case(key,source) => (key._1,source.parse[Term].get.structure) -> key }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => p.toString; case Input.VirtualFile(p,_) => p }
    if(!planned.keysIterator.exists(_._1 == path)) Patch.empty else doc.tree.collect {
      case a: Term.Apply if planned.contains(path -> a.structure) =>
        restored.add((path,a.structure)); patches.incrementAndGet()
        Patch.replaceTree(a,planned(path -> a.structure)).atomic
      case a: Term.Apply if originals.contains(path -> a.structure) =>
        restored.add(originals(path -> a.structure)); Patch.empty
    }.asPatch
  }
  override def afterComplete(): Unit = {
    require(planned.keySet == restored.asScala.toSet,"Some generated unsupported-field updates were not restored")
    println(s"Restored ${patches.get} expressions; ${restored.size} unsupported expression shapes accounted for")
  }
}
