package com.histmersion.plugin

import java.nio.file.Path

/** Local filesystem operations supplied by the host. Path construction alone performs no I/O.
  * Read failures preserve the underlying IOException so a plugin can explain its own operation.
  */
trait LocalFiles:
  def isFile(path: Path): Boolean
  def isDirectory(path: Path): Boolean
  def exists(path: Path): Boolean
  def isExecutable(path: Path): Boolean
  def read(path: Path): Array[Byte]
  def readPrefix(path: Path, length: Int): Array[Byte]

  /** The named file exists only while use runs. Cleanup also runs when use returns Left or throws.
    * name must be a single filename, never a path. The supplied path must not escape the callback.
    */
  def staged[A](name: String, bytes: Array[Byte])(use: Path => Either[String, A]): Either[String, A]

object LocalFiles:
  object Unavailable extends LocalFiles:
    private def unavailable: Nothing = throw UnsupportedOperationException("this host supplies no local files")
    def isFile(path: Path): Boolean = unavailable
    def isDirectory(path: Path): Boolean = unavailable
    def exists(path: Path): Boolean = unavailable
    def isExecutable(path: Path): Boolean = unavailable
    def read(path: Path): Array[Byte] = unavailable
    def readPrefix(path: Path, length: Int): Array[Byte] = unavailable
    def staged[A](name: String, bytes: Array[Byte])(use: Path => Either[String, A]): Either[String, A] =
      Left("this host supplies no local files")
