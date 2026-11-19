package com.histmersion
package api

import com.histmersion.other.Schema

object Rendered:
  // P6 REWRITE to json.Schema: the file's own package api has the member json
  def schema: json.Schema[Int] = new json.Schema[Int] {}
  def otherSchema: Schema[Int] = new Schema[Int] {}
