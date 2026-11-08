package com.histmersion
package config.edit

class SecretFormSuite extends munit.FunSuite:
  private def key(s: SettingKey.ParseRaw) = SettingKey.parse(s).toOption.get

  test("which keys are credentials, and how each may be written"):
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("auth.api-key"))), SecretRule.FromEnvironment)
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("db.password"))), SecretRule.FromEnvironment)
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("plugins.oa-core.api-key"))), SecretRule.Reference)
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("plugins.bitwarden.session"))), SecretRule.Reference)
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("agents.step.max-tokens"))), SecretRule.NotSecret)
    assertEquals(SecretRule.of(key(SettingKey.ParseRaw("plugins.keepassxc.key-file"))), SecretRule.NotSecret)

  test("a reference's form, never the value"):
    val r = SecretRule.Reference
    assertEquals(SecretForm.of("", r), SecretForm.Unset)
    assertEquals(SecretForm.of("env:HISTMERSION_CORE_KEY", r), SecretForm.Environment)
    assertEquals(SecretForm.of("keepassxc:Histmersion/CORE#password", r), SecretForm.Vault)
    assertEquals(SecretForm.of("value:abc123", r), SecretForm.Literal)
    assertEquals(SecretForm.of("abc123", r), SecretForm.Plain)
    assertEquals(SecretForm.of("abc123", SecretRule.FromEnvironment), SecretForm.Plain)
    assertEquals(SecretForm.shown("value:abc123", r), Some("value:***"))
    assertEquals(SecretForm.shown("env:X", r), Some("env:X"))
    assertEquals(SecretForm.shown("abc123", r), None)
    val bad = SecretForm.reference("abc123")
    assert(bad.isLeft && !bad.left.toOption.get.contains("abc123"), s"the error never repeats the text: $bad")
    assert(SecretForm.reference("env:1BAD").isLeft)
    assert(SecretForm.reference("Weird:x").isLeft)
