package derivationfixture

import hearth.kindlings.tapirschemaderivation.KindlingsSchema

final case class UndeclaredChild(value: Int)
final case class Parent(child: UndeclaredChild) derives KindlingsSchema, Schema
