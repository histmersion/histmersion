//> using dep org.ekrich::sconfig:2.1.0
package com.histmersion.domain.detectorfixtures

object ConfigCases:
  def load = org.ekrich.config.ConfigFactory.load() // expect: config-load
  def environment = org.ekrich.config.ConfigFactory.systemEnvironment() // expect: config-load
  def properties = org.ekrich.config.ConfigFactory.systemProperties() // expect: config-load
  def text = org.ekrich.config.ConfigFactory.parseString("a = 1")
  def empty = org.ekrich.config.ConfigFactory.empty("fixture")
