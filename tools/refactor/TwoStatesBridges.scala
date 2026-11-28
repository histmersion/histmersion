import scalafix.v1._
import scala.meta._

class TwoStatesBridges extends SyntacticRule("TwoStatesBridges") {
  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case o: Defn.Object
          if o.templ.inits.exists(i => Set(s"TwoStates[${o.name.value}]", s"TwoCases[${o.name.value}]")(i.tpe.syntax)) =>
        val name = o.name.value
        val cases = o.parent.toList.flatMap(_.children).collectFirst {
          case e: Defn.Enum if e.name.value == name =>
            e.templ.stats.collect { case r: Defn.RepeatedEnumCase => r.cases.map(_.value) }.flatten
        }
        val predicate = o.templ.stats.collectFirst { case g: Defn.ExtensionGroup => g }
        (cases, predicate) match {
          case (Some(List(yes, no)), Some(g)) =>
            val text = doc.input.text
            val ind = text.substring(text.lastIndexOf('\n', g.pos.start - 1) + 1, g.pos.start)
            Patch.addRight(
              g,
              s"\n${ind}def apply(value: Boolean): $name = if value then $yes else $no" +
                s"\n${ind}extension (x: $name) def value: Boolean = x == $yes"
            )
          case _ => Patch.empty
        }
    }.asPatch
}
