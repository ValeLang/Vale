package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonArray, VonInt, VonMember, VonObject, VonStr}
import org.scalatest.{FunSuite, Matchers}

class VivemTests extends FunSuite with Matchers {
  test("Return 7") {
    val main =
      FunctionH(
        PrototypeH(
          FullNameH(
            "main",
            0,
            List(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),List(),ReferenceH(m.ShareH,InlineH,ReadonlyH,IntH())),
        true,
        false,
        false,
        List(UserFunctionH),
        BlockH(ConstantI64H(7)))
    val programH = ProgramH(List(), List(), List(), List(main), List(), List(), Map(), Map(), Map(), List())
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(7)
  }

  test("Adding") {
    val intRef =
      VonObject("Ref",None,Vector(VonMember("ownership",VonObject("Share",None,Vector())), VonMember("location",VonObject("Inline",None,Vector())), VonMember("permission",VonObject("Readonly",None,Vector())), VonMember("kind",VonObject("Int",None,Vector()))))

    val addPrototype =
      PrototypeH(
        FullNameH(
          "__addIntInt",
          0,
          List(VonStr(""),VonObject("F",None,Vector(VonMember("humanName",VonStr("__addIntInt")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector(intRef, intRef))))))),
        List(ReferenceH(ShareH,InlineH,ReadonlyH,IntH()), ReferenceH(ShareH,InlineH,ReadonlyH,IntH())),
        ReferenceH(ShareH,InlineH,ReadonlyH,IntH()))
    val main =
      FunctionH(
        PrototypeH(
          FullNameH(
            "main",
            0,
            List(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),List(),ReferenceH(m.ShareH,InlineH,ReadonlyH,IntH())),
        true,
        false,
        false,
        List(UserFunctionH),
        BlockH(
          CallH(
            addPrototype,
            List(
              ConstantI64H(52),
              CallH(
                addPrototype,
                List(
                  ConstantI64H(53),
                  ConstantI64H(54)))))))
    val addExtern =
      FunctionH(
        addPrototype,
        false,
        false,
        true,
        List(),
        BlockH(ConstantI64H(133337)))
    val programH = ProgramH(List(), List(), List(), List(main, addExtern), List(), List(), Map(), Map(), Map(), List())
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(159)
  }
}
