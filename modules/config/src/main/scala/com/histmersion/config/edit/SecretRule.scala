package com.histmersion.config.edit

/** How a setting that holds a credential may be written into the config file. */
enum SecretRule:
  /** Not a credential. */
  case NotSecret

  /** The setting holds the credential ITSELF (`auth.api-key`, `db.password`): it is never written into the file from
    * Settings or the command line — the environment variable named for it is the place.
    */
  case FromEnvironment

  /** A plugin's credential, held as a REFERENCE to where the value is kept (`env:NAME`, `<vault>:<entry>`);
    * `value:<literal>` only when the person asks for it.
    */
  case Reference

object SecretRule:
  /** The words a credential's key is called by. */
  type LooksSecretResult = LooksSecretResult.T
  object LooksSecretResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LooksSecretLeaf = LooksSecretLeaf.T
  object LooksSecretLeaf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Names = Set("api-key", "password", "session", "service-account-token", "token", "secret")

  def looksSecret(leaf: com.histmersion.config.edit.SecretRule.LooksSecretLeaf): com.histmersion.config.edit.SecretRule.LooksSecretResult =
    Names(leaf) || leaf.endsWith("-api-key") || leaf.endsWith("-password") || leaf.endsWith("-token")

  /** The rule for `key`: a plugin's credential is a reference; any other is the value itself. */
  def of(key: SettingKey): SecretRule =
    if !looksSecret(key.leaf) then NotSecret
    else if key.within(SettingKey.applyUnsafe("histmersion.plugins")) then Reference
    else FromEnvironment
