package net.verdagon.vale

object Assert {
  val code =
    """
      |fn assert(cond Bool) Void {
      |  assert(cond, "Assertion failed!");
      |}
      |fn assert(cond Bool, msg Str) Void {
      |  if (cond == false) {
      |    println(msg);
      |    panic();
      |  }
      |}
      |
      |fn assertEq<T>(a T, b T) Void {
      |  assert(a == b, "Assertion failed, not equal!");
      |}
      |
      |fn assertEq<T>(a T, b T, msg Str) Void {
      |  assert(a == b, msg);
      |}
      |
    """.stripMargin
}
