package net.verdagon.vale

object InheritanceSamples {
  val upcasting =
    """
      |interface MyInterface { }
      |struct MyStruct { value int; }
      |impl MyStruct for MyInterface;
      |fn main() {
      |  x MyInterface = MyStruct(9);
      |}
      |""".stripMargin

  val calling =
    """
      |interface Car { }
      |abstract fn doCivicDance(virtual this Car) int;
      |
      |struct Civic {}
      |impl Civic for Car;
      |fn doCivicDance(civic Civic impl Car) int {
      |	= 4;
      |}
      |
      |struct Toyota {}
      |impl Toyota for Car;
      |fn doCivicDance(toyota Toyota impl Car) int {
      |	= 7;
      |}
      |
      |fn main() int {
      |	x Car = Toyota();
      |	= doCivicDance(x);
      |}
    """.stripMargin

  val callingThroughBorrow =
    """
      |interface Car { }
      |abstract fn doCivicDance(virtual this &Car) int;
      |
      |struct Civic {}
      |impl Civic for Car;
      |fn doCivicDance(civic &Civic impl Car) int {
      |	= 4;
      |}
      |
      |struct Toyota {}
      |impl Toyota for Car;
      |fn doCivicDance(toyota &Toyota impl Car) int {
      |	= 7;
      |}
      |
      |fn main() int {
      |	x Car = Toyota();
      | b = &x;
      |	= doCivicDance(b);
      |}
    """.stripMargin

  val callingAbstract =

    """
      |interface MyInterface<T> rules(T Ref) { }
      |abstract fn doThing<T>(virtual x MyInterface<T>) int;
      |
      |struct MyStruct<T> rules(T Ref) { }
      |impl<T> MyStruct<T> for MyInterface<T>;
      |fn doThing<T>(x MyStruct<T> impl MyInterface<T>) int {4}
      |
      |fn main() {
      |  x = MyStruct<int>();
      |  y = MyStruct<str>();
      |  doThing(x);
      |  = doThing(y);
      |}
    """.stripMargin
}
