package com.histmersion
package api.model

import com.histmersion.other.Schema

final case class Model(id: Int)

object Model:
  // P5 REWRITE to api.json.Schema: another Schema is imported, and json is no member of api.model
  given modelSchema: api.json.Schema[Model] = new api.json.Schema[Model] {}
  def otherSchema: Schema[Int] = new Schema[Int] {}

// P7 REWRITE to api.json.Schema: a derives clause has no SemanticDB occurrences; the path is read from the index
final case class Derived(id: Int) derives api.json.Schema

// P8 UNCHANGED: a relative path is no finding
final case class Relative(id: Int) derives Schema
