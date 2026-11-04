import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class RepairCopySeparators extends SyntacticRule("RepairCopySeparators") {
  private lazy val generated = Files.readAllLines(Paths.get("refactor-audit/copy-migration/multiple-applied/findings.tsv")).asScala.drop(1).map(_.split("\t",-1)).filter(_(4) == "eligible").map(_(0)).toSet
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => p.toString; case Input.VirtualFile(p,_) => p }
    if(!generated(path)) Patch.empty else {
      val tokens = doc.tokens.toList
      tokens.zipWithIndex.collect {
        case(c: Token.Comma,i) if tokens.drop(i+1).dropWhile(t => t.is[Token.Space] || t.is[Token.Tab]).headOption.exists(_.is[Token.RightParen]) => Patch.removeToken(c)
      }.asPatch
    }
  }
}
