package com.histmersion.util.storage

/** A temporary store, removed with everything in it when it closes. */
trait Scratch extends AutoCloseable:
  def store: Store
  def close(): Unit
