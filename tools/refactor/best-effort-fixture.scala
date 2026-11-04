//> using scala 3.10.0
package com.histmersion.domain.detectorfixtures.besteffort

object Broken:
  def environment = System.getenv("HOME") // expect: environment
  def wrong: Int = "deliberate type error"
  def unresolved(x: MissingType): Unit = x.commit()
  def property = java.lang.System.getProperty("user.home") // expect: properties
