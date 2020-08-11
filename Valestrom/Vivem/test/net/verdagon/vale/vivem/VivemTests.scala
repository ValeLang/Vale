package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonArray, VonInt, VonMember, VonObject, VonStr}
import org.scalatest.{FunSuite, Matchers}

class VivemTests extends FunSuite with Matchers {
  test("Return 7") {
    val main =
      FunctionH(
        PrototypeH(FullNameH(List(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),List(),ReferenceH(m.ShareH,InlineH,IntH())),
        false,
        false,
        List(UserFunctionH, ExportH),
        BlockH(ConstantI64H(7)))
    val programH = ProgramH(List(), List(), List(), List(main), Map())
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(7)
  }

  test("Adding") {

    val addPrototype =
      PrototypeH(
        FullNameH(List(VonObject("F",None,Vector(VonMember("humanName",VonStr("__addIntInt")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector(VonObject("Ref",None,Vector(VonMember("ownership",VonObject("Share",None,Vector())), VonMember("location",VonObject("Inline",None,Vector())), VonMember("kind",VonObject("Int",None,Vector())))), VonObject("Ref",None,Vector(VonMember("ownership",VonObject("Share",None,Vector())), VonMember("location",VonObject("Inline",None,Vector())), VonMember("kind",VonObject("Int",None,Vector()))))))))))),
        List(ReferenceH(ShareH,InlineH,IntH()), ReferenceH(ShareH,InlineH,IntH())),
        ReferenceH(ShareH,InlineH,IntH()))
    val main =
      FunctionH(
        PrototypeH(FullNameH(List(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),List(),ReferenceH(m.ShareH,InlineH,IntH())),
        false,
        false,
        List(UserFunctionH, ExportH),
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
        true,
        List(),
        BlockH(ConstantI64H(133337)))
    val programH = ProgramH(List(), List(), List(), List(main, addExtern), Map())
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(159)
  }
}
