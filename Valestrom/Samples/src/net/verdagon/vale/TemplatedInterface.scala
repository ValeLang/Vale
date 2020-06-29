package net.verdagon.vale

object TemplatedInterface {
  val code =
    """
      |interface MyIFunction1<P1, R> rules(P1 Ref, R Ref) {
      |  abstract fn go(virtual this &MyIFunction1<P1, R>, param P1) R;
      |}
      |
      |struct MyFunc { }
      |impl MyFunc for MyIFunction1<Int, Int>;
      |
      |fn go(this &MyFunc impl MyIFunction1<Int, Int>, param Int) Int {
      |  param * 2
      |}
      |
      |fn main() {
      |  m = MyFunc();
      |  i &MyIFunction1<Int, Int> = &m;
      |  println(i.go(4));
      |  println(i.go(6));
      |}
    """.stripMargin
}
