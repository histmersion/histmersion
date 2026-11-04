package com.histmersion.inventoryfixture.low {
  trait Contract:
    def read: Int
  final class Internal
  type Hidden = Internal
  object Gateway:
    def make(): Internal = new Internal
    def back(): com.histmersion.inventoryfixture.high.Contract = ???
    def permitted(): com.histmersion.inventoryfixture.high.Internal = ???
}

package com.histmersion.inventoryfixture.high {
  trait Contract
  final class Internal
  object Consumer:
    def read(contract: com.histmersion.inventoryfixture.low.Contract): Int = contract.read
    def inferred() = com.histmersion.inventoryfixture.low.Gateway.make()
    def aliased(): com.histmersion.inventoryfixture.low.Hidden = com.histmersion.inventoryfixture.low.Gateway.make()
}
