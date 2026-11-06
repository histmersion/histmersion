# Imported Pure repair resolution

The five frozen sources produce ten positive and seven negative E007 diagnostics.
The positive output must exactly match expected/Native.scala and compile/run with
Scala3.10, -source:future and fatal E165. Runtime checks cover String, numeric and
Boolean values, empty/populated lists, Array reference identity, renamed imports
and fully qualified non-rooted names. RootPackage.scala contributes a legitimate
root io package: it must not be mistaken for a local namespace shadow.

Seven cases stay outside the plan: fake RefinedType, a constraint alias called
Pure, a local primitive alias, an unknown wildcard import, inherited aliases,
a local io namespace and a primitive shadow declared in a sibling source file.
Only Native.scala may change. The disabled configuration must produce an empty
plan on the same inputs, showing that import-dependent support is opt-in.

The first preview missed the two renamed-parent cases because the existing shape
index does not discover RefinedType through an import rename. The repair resolver
now locates unique AST objects directly, without changing inventory classifications
or weakening the golden. All seven exclusions remain. The final preview matches
the whole golden and its actual rewritten runtime passes. Older five ordinary,
two owner and five-property outputs remain exact, preserving prior fixture behavior.

Use imported-pure-fixture-index.conf, compile src/main/scala to its build.log, and
run imported-pure-fixture.conf through the existing Scalafix CLI. Compare Native.scala
and run the generated positive source with RootPackage.scala using main class
importedfixture.checkImportedPure. Run imported-pure-disabled-fixture.conf to check
the empty plan. No application-specific type or expression mappings are supplied.
