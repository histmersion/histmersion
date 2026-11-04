//> using scala 3.10.0
package com.histmersion.domain.detectorfixtures

object ClockCases:
  def bad = java.time.Instant.now() // expect: clock
  def supplied(clock: java.time.Clock) = java.time.Instant.now(clock)
  def wrong(zone: java.time.ZoneId) = java.time.LocalDate.now(zone) // expect: clock
  def factory = java.time.Clock.systemUTC() // expect: clock
  def zoneFactory(zone: java.time.ZoneId) = java.time.Clock.system(zone) // expect: clock
  def ticks = System.nanoTime() // expect: clock

object LifecycleCases:
  def exit = System.exit(1) // expect: lifecycle

object PropertyCases:
  def mutate = System.setProperty("test", "x") // expect: properties
  def clear = System.clearProperty("test") // expect: properties

object MoreAccess:
  def process = new java.lang.ProcessBuilder("test") // expect: process
  def stream = new java.io.FileInputStream("test") // expect: filesystem
