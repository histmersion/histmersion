package com.histmersion.plugin

/** A secret backend (KeePassXC, 1Password, Bitwarden, …), registered by a plugin under its [[name]]: the host hands it
  * the part of a `<name>:<ref>` reference after the colon. `Left` names what went wrong (locked, no such entry, the
  * tool is missing) and NEVER contains the value.
  */
trait SecretVault:
  def name: String
  def resolve(ref: String): Either[String, String]

  /** Several references at once: the host asks for every reference of this vault that the plugins of one config
    * generation (a server start, a `config reload`) hold, in ONE call, before it registers them. The answer has an
    * entry for each reference asked (one missing is reported as unanswered); each `Left`, as in [[resolve]], names what
    * went wrong and never contains a value. The default resolves them one by one; a vault that pays per call — a
    * database to unlock, a hardware key to touch — overrides it to pay once. The values are kept by the host for that
    * generation only.
    */
  def resolveAll(refs: List[String]): Map[String, Either[String, String]] =
    refs.distinct.map(r => r -> resolve(r)).toMap
