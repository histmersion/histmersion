package com.histmersion
package api.json

trait Schema[A]

object Schema:
  def derived[A]: Schema[A] = new Schema[A] {}
