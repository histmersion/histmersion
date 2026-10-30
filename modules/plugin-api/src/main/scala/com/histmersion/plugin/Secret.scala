package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** A credential in a config: a REFERENCE to the value, parsed when the config is read and resolved by the host when the
  * plugin is registered (through [[PluginContext.secret]]). `toString` shows the reference —
  * `Secret(env:PORTAL_TOKEN)`, `Secret(value:***)` — never the value.
  */
final class Secret private (val ref: SecretRef):
  override def toString: String = s"Secret(${ref.shown})"
  override def equals(o: Any): Boolean = o.isInstanceOf[Secret] && o.asInstanceOf[Secret].ref == ref
  override def hashCode: Int = ref.hashCode

object Secret:
  def apply(ref: SecretRef): Secret = new Secret(ref)

  def parse(text: String): Either[String, Secret] = SecretRef.parse(text).map(Secret(_))

  /** The HOCON reader: a string holding a reference. It parses the reference only — nothing is resolved while the
    * config is read.
    */
  given ConfigReader[Secret] = new ConfigReader[Secret]:
    def from(v: org.ekrich.config.ConfigValue) =
      ConfigReader.stringReader
        .from(v)
        .flatMap(s => parse(s).left.map(e => ConfigDecodingError.CannotConvert(Nil, e, None)))
