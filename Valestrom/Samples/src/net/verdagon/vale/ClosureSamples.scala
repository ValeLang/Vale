package net.verdagon.vale

object ClosureSamples {
  val mutate =
    """
      |fn main() int {
      |  x = 4;
      |  {
      |    mut x = x + 1;
      |  }();
      |  x
      |}
    """.stripMargin
}
