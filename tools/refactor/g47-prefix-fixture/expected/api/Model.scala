package com.histmersion
package api.model

import com.histmersion.other.Schema

final case class Model(id: Int)

object Model:
  // P5 REWRITE to api.json.Schema: another Schema is imported, and json is no member of api.model
  given modelSchema: api.json.Schema[Model] = new api.json.Schema[Model] {}
  def otherSchema: Schema[Int] = new Schema[Int] {}
