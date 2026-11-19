package com.histmersion
package other

trait Schema[A]

object Schema:
  def derived[A]: Schema[A] = new Schema[A] {}
