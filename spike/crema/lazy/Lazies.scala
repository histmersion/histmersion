package com.histmersion.spike.cremalazy

import java.util.function.Supplier

/** Lazy vals (instance and object) compiled by whichever Scala the jar is built with: Scala < 3.8 emits the
  * sun.misc.Unsafe scheme, Scala >= 3.8 the VarHandle scheme.
  */
final class Lazies extends Supplier[String]:
  lazy val greeting: String = { Thread.sleep(20); s"hello-${Lazies.counter.incrementAndGet()}" }
  def get(): String = s"$greeting/${Lazies.shared}/${util.Properties.versionNumberString}"

object Lazies:
  val counter = java.util.concurrent.atomic.AtomicInteger()
  lazy val shared: String = { Thread.sleep(20); s"shared-${counter.incrementAndGet()}" }
