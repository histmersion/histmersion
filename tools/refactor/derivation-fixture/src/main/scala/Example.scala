package derivationfixture

import hearth.kindlings.tapirschemaderivation.KindlingsSchema

final case class Child(value: Int) derives KindlingsSchema, Schema
final case class Record(child: Child, note: Option[String] = None, tags: List[String] = Nil)
    derives KindlingsSchema, Schema

@main def verifySchemas(): Unit =
  def fields[A](schema: sttp.tapir.Schema[A]) =
    schema.schemaType.asInstanceOf[sttp.tapir.SchemaType.SProduct[A]].fields.map(f =>
      (f.name, f.schema.name, f.schema.isOptional, f.schema.format, f.schema.description, f.schema.default.map(_._1))
    )
  val legacyChild: sttp.tapir.Schema[Child] = summon[KindlingsSchema[Child]].schema
  val legacyRecord: sttp.tapir.Schema[Record] = summon[KindlingsSchema[Record]].schema
  assert(fields(summon[sttp.tapir.Schema[Child]]) == fields(legacyChild))
  assert(fields(summon[sttp.tapir.Schema[Record]]) == fields(legacyRecord))
  assert(summon[sttp.tapir.Schema[Record]].name == legacyRecord.name)
  val product = summon[sttp.tapir.Schema[Record]].schemaType.asInstanceOf[sttp.tapir.SchemaType.SProduct[Record]]
  assert(product.fields.map(_.name.name) == List("child", "note", "tags"))
  assert(product.fields.find(_.name.name == "note").get.schema.isOptional)
  val customized = legacyChild.description("preserved").format("custom").encodedExample("example")
    .default(Child(7)).deprecated(true).hidden(true).title("custom title")
  val instance = new KindlingsSchema[Child]:
    def schema: sttp.tapir.Schema[Child] = customized
  val adapted = Schema.derived[Child](using instance)
  assert(adapted.productIterator.toList == customized.productIterator.toList)
  assert(summon[sttp.tapir.Schema[Parent]].schemaType.isInstanceOf[sttp.tapir.SchemaType.SProduct[?]])
