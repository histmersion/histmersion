package fixture

import io.github.iltotore.iron.*

@main def verifyBuffers(): Unit =
  val bytes = Array[Byte](1, 2, 3)
  val packet = Packet.Bytes(bytes)
  assert(packet.value eq bytes)
  bytes(1) = 9
  assert(packet.value(1) == 9)
  val pixels = Array[Int](0xff112233, 0xff445566)
  val image = Packet.Pixels(pixels)
  assert(image.value eq pixels)
  assert(image.value.sameElements(pixels))
  val legacy = Legacy.Bytes(bytes)
  assert(legacy.value eq bytes)
  assert(!scala.compiletime.testing.typeChecks("fixture.Packet(Array[Byte](1), None)"))
  assert(!scala.compiletime.testing.typeChecks("fixture.Packet(fixture.Legacy.Bytes(Array[Byte](1)), None)"))
  println("PASS: buffer identity, contents and nominal separation")
