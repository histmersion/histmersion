package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory
import com.histmersion.plugin.PluginConfig.given

final case class WithSecrets(token: Secret, password: Option[Secret], retries: Int, verbose: Boolean)
    derives ConfigReader

final class ExamplePlugin extends Plugin:
  type Config = WithSecrets
  val info = PluginInfo("example", "0.1.0", Spi.Version)
  def register(config: WithSecrets, ctx: PluginContext) = Right(())

class SecretSuite extends munit.FunSuite:

  test("the three reference forms parse"):
    assertEquals(SecretRef.parse("value:hunter2"), Right(SecretRef.Value("hunter2")))
    assertEquals(SecretRef.parse("env:PORTAL_TOKEN"), Right(SecretRef.Env("PORTAL_TOKEN")))
    assertEquals(SecretRef.parse(" keepassxc:Histmersion/Portal#password "),
      Right(SecretRef.Vault("keepassxc", "Histmersion/Portal#password")))
    assertEquals(SecretRef.parse("1password:op://Private/Portal/password"),
      Right(SecretRef.Vault("1password", "op://Private/Portal/password")))
    // a literal keeps its colons and its spaces inside
    assertEquals(SecretRef.parse("value:a:b c"), Right(SecretRef.Value("a:b c")))

  test("a bare value, an empty part or a bad name is refused, and the error never echoes a value"):
    val bare = SecretRef.parse("hunter2")
    assert(bare.left.exists(_.contains("not a bare value")), bare)
    assert(!bare.left.exists(_.contains("hunter2")))
    assert(SecretRef.parse("value:").isLeft)
    assert(SecretRef.parse("env:").isLeft)
    assert(SecretRef.parse("env:NOT-A-NAME").isLeft)
    assert(SecretRef.parse("keepassxc:").isLeft)
    assert(SecretRef.parse("Vault:x").left.exists(_.contains("not a vault name")))
    assert(SecretRef.parse(":x").isLeft)
    assert(SecretRef.parse(null).isLeft)

  test("toString shows the reference, never a literal value"):
    assertEquals(Secret.parse("env:PORTAL_TOKEN").map(_.toString), Right("Secret(env:PORTAL_TOKEN)"))
    assertEquals(Secret.parse("bitwarden:abc#password").map(_.toString), Right("Secret(bitwarden:abc#password)"))
    val literal = Secret.parse("value:hunter2").toOption.get
    assertEquals(literal.toString, "Secret(value:***)")
    assert(!s"$literal ${Some(literal)} ${List(literal)}".contains("hunter2"))
    assertEquals(Secret.parse("env:A"), Secret.parse("env:A"))

  test("the reader parses references inside a derived config; numbers and booleans may be strings"):
    val c = ConfigFactory.parseString(
      "token = \"env:PORTAL_TOKEN\"\npassword = null\nretries = \"3\"\nverbose = \"true\"")
    assertEquals(ConfigReader[WithSecrets].from(c.root),
      Right(WithSecrets(Secret(SecretRef.Env("PORTAL_TOKEN")), None, 3, true)))
    val withPw = c.withValue("password", org.ekrich.config.ConfigValueFactory.fromAnyRef("keepassxc:a#b"))
    assertEquals(ConfigReader[WithSecrets].from(withPw.root).map(_.password),
      Right(Some(Secret(SecretRef.Vault("keepassxc", "a#b")))))

  test("a bare string where a secret belongs is a decoding error that does not repeat the value"):
    val c = ConfigFactory.parseString("token = hunter2\npassword = null\nretries = 1\nverbose = false")
    val r = ConfigReader[WithSecrets].from(c.root)
    assert(r.isLeft, r)
    assert(!r.left.toOption.get.toString.contains("hunter2"), r)

  test("a plugin's config type and reader are path-dependent members"):
    val p: Plugin = ExamplePlugin()
    val c = ConfigFactory.parseString("token = \"env:T\"\npassword = null\nretries = 2\nverbose = true")
    val typed: Either[?, p.Config] = p.configReader.from(c.root)
    assert(typed.isRight, typed)
    assertEquals(p.defaultsResource, "META-INF/histmersion/plugins/example.conf")
