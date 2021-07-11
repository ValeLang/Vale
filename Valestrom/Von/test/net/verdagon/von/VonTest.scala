package net.verdagon.von

import org.scalatest.{FunSuite, Matchers, _}

class VonTest extends FunSuite with Matchers {

  test("Test 1") {
    val data = VonObject("MyObj", None, Vector(VonMember("mem", VonInt(42))))
    new VonPrinter(VonSyntax(), 30).print(data) shouldEqual
      "MyObj(mem = 42)"
  }

  test("Json escape apostrophe") {
    val data = VonStr("yes'nt")
    new VonPrinter(JsonSyntax, 30).print(data) shouldEqual
      ("yes" + "\\" + "'" + "nt")
  }

  test("Test 2") {
    val data = VonObject("MySuperSuperLongObject", None, Vector(VonMember("member", VonInt(42))))
    new VonPrinter(VonSyntax(), 30).print(data) shouldEqual
      """
        |MySuperSuperLongObject(
        |  member = 42)
      """.stripMargin.trim
  }

  test("Test 3") {
    val data =
      VonObject(
        "MyObj",
        None,
        Vector(
          VonMember(
            "member",
            VonObject(
              "MyObj",
              None,
              Vector(
                VonMember(
                  "member",
                  VonObject(
                    "MyObj",
                    None,
                    Vector(
                      VonMember(
                        "member",
                        VonInt(42))))))))))
    new VonPrinter(VonSyntax(), 30).print(data) shouldEqual
      """
        |MyObj(
        |  member = MyObj(
        |    member = MyObj(member = 42)))
      """.stripMargin.trim
    new VonPrinter(VonSyntax(), 25).print(data) shouldEqual
      """
        |MyObj(
        |  member = MyObj(
        |    member = MyObj(
        |      member = 42)))
      """.stripMargin.trim
  }
}
