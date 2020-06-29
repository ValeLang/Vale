package net.verdagon.vale

object TemplateSamples {
  val stampingViaReturn =
    """
      |interface MyInterface<X> rules(X Ref) { }
      |
      |struct SomeStruct<X> rules(X Ref) { x X; }
      |impl<X> SomeStruct<X> for MyInterface<X>;
      |
      |fn doAThing<T>(t T) {
      |  SomeStruct<T>(t)
      |}
      |
      |fn main() {
      |  doAThing(4);
      |}
    """.stripMargin

  val callingTemplatedConstructor =
    """
      |struct MySome<T> rules(T Ref) { value T; }
      |fn main() {
      |  MySome<*Int>(4).value
      |}
    """.stripMargin
}
