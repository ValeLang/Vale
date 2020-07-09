package net.verdagon.vale

object OverloadSamples {
  val overloads =
    """
      |fn ~(a int, b int){+(a, b)}
      |fn ~(a str, b str){+(a, b)}
      |fn main(){3 ~ 3}
    """.stripMargin
}
