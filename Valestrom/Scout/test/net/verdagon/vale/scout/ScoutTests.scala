package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ScoutTests extends FunSuite with Matchers {
  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => Scout.scoutProgram(List(program0))
    }
  }

  test("Lookup +") {
    val program1 = compile("fn main() { +(3, 4) }")
    val main = program1.lookupFunction("main")
    
    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(FunctionCallSE(_, FunctionLoadSE(GlobalFunctionFamilyNameS("+")), _))) =>
    }
  }

  test("Struct") {
    val program1 = compile("struct Moo { x int; }")
    val imoo = program1.lookupStruct("Moo")

    val memberRune = MemberRuneS(0)
    imoo.rules match {
      case List(
        EqualsSR(TypedSR(memberRune, CoordTypeSR), TemplexSR(NameST(_, CodeTypeNameS("int")))),
        EqualsSR(TemplexSR(RuneST(ImplicitRuneS(_, _))), TemplexSR(MutabilityST(MutableP)))) =>
    }
    imoo.members shouldEqual List(StructMemberS("x",FinalP,memberRune))
  }

  test("Lambda") {
    val program1 = compile("fn main() { {_ + _}(4, 6) }")

    val CodeBody1(BodySE(_, BlockSE(_, List(expr)))) = program1.lookupFunction("main").body
    val FunctionCallSE(_, FunctionSE(lambda @ FunctionS(_, _, _, _, _, _, _, _,_, _, _, _)), _) = expr
    lambda.identifyingRunes match {
      case List(MagicParamRuneS(mp1), MagicParamRuneS(mp2)) => {
        vassert(mp1 != mp2)
      }
    }
  }

  test("Interface") {
    val program1 = compile("interface IMoo { fn blork(a bool)void; }")
    val imoo = program1.lookupInterface("IMoo")

    imoo.rules match {
      case List(EqualsSR(TemplexSR(RuneST(ImplicitRuneS(_, _))), TemplexSR(MutabilityST(MutableP)))) =>
    }

    val blork = imoo.internalMethods.head
    blork.name match { case FunctionNameS("blork", _) => }

    val (paramRune, retRune) =
      blork.templateRules match {
        case List(
          EqualsSR(
            TypedSR(actualParamRune, CoordTypeSR),
            TemplexSR(NameST(_, CodeTypeNameS("bool")))),
          EqualsSR(
            TypedSR(actualRetRune, CoordTypeSR),
            TemplexSR(NameST(_, CodeTypeNameS("void"))))) => {
          actualParamRune match {
            case ImplicitRuneS(_, 0) =>
          }
          actualRetRune match {
            case ImplicitRuneS(_, 1) =>
          }
          (actualParamRune, actualRetRune)
        }
      }

    RuleSUtils.getDistinctOrderedRunesForRulexes(blork.templateRules) shouldEqual
      List(paramRune, retRune)

    blork.params match {
      case List(
        ParameterS(
          AtomSP(
            CaptureS(CodeVarNameS("a"),FinalP),
            None,
            ImplicitRuneS(_, 0),
            None))) =>
    }

    // Yes, even though the user didnt specify any. See CCAUIR.
    blork.identifyingRunes shouldEqual List()
  }

  test("Impl") {
    val program1 = compile("impl IMoo for Moo;")
    val impl = program1.impls.head
    val structRune =
      impl.structKindRune match {
        case ir0 @ ImplicitRuneS(_, 0) => ir0
      }
    val interfaceRune =
      impl.interfaceKindRune match {
        case ir0 @ ImplicitRuneS(_, 1) => ir0
      }
    impl.rules match {
      case List(
          EqualsSR(TypedSR(a,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("Moo")))),
          EqualsSR(TypedSR(b,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("IMoo"))))) => {
        vassert(a == structRune)
        vassert(b == interfaceRune)
      }
    }
  }

  test("Method call") {
    val program1 = compile("fn main() { x = 4; = x.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(_, FunctionCallSE(_, FunctionLoadSE(GlobalFunctionFamilyNameS("shout")), List(LendSE(LocalLoadSE(name, BorrowP), BorrowP))))) => {
        name match {
          case CodeVarNameS("x") =>
        }
      }
    }
  }

  test("Moving method call") {
    val program1 = compile("fn main() { x = 4; = x^.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(_, FunctionCallSE(_, FunctionLoadSE(GlobalFunctionFamilyNameS("shout")), List(LocalLoadSE(_, OwnP))))) =>
    }
  }

  test("Function with magic lambda and regular lambda") {
    // There was a bug that confused the two, and an underscore would add a magic param to every lambda after it

    val program1 =
      compile(
        """fn main() {
          |  {_};
          |  (a){a};
          |}
        """.stripMargin)
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    val BlockSE(_, FunctionSE(lambda1) :: FunctionSE(lambda2) :: _) = block
    lambda1.params match {
      case List(_, ParameterS(AtomSP(CaptureS(MagicParamNameS(_),FinalP),None,MagicParamRuneS(_),None))) =>
    }
    lambda2.params match {
      case List(_, ParameterS(AtomSP(CaptureS(CodeVarNameS("a"),FinalP),None,ImplicitRuneS(_, _),None))) =>
    }
  }


  test("Constructing members") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = true;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(
        List(
          LocalVariable1(ConstructingMemberNameS("x"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed),
          LocalVariable1(ConstructingMemberNameS("y"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed)),
        List(
          LetSE(
            _,
            _,
            _,
            AtomSP(CaptureS(ConstructingMemberNameS("x"),FinalP),None,_,None),
            IntLiteralSE(4)),
          LetSE(
            _,
            _,
            _,
            AtomSP(CaptureS(ConstructingMemberNameS("y"),FinalP),None,_,None),
            BoolLiteralSE(true)),
          FunctionCallSE(_,
            FunctionLoadSE(GlobalFunctionFamilyNameS("MyStruct")),
            List(
              LocalLoadSE(ConstructingMemberNameS("x"),OwnP),
              LocalLoadSE(ConstructingMemberNameS("y"),OwnP))))) =>
    }
  }

  test("Constructing members, borrowing another member") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = &this.x;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(
        List(
          LocalVariable1(ConstructingMemberNameS("x"),FinalP,Used,Used,NotUsed,NotUsed,NotUsed,NotUsed),
          LocalVariable1(ConstructingMemberNameS("y"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed)),
        List(
          LetSE(_,_,_,
            AtomSP(CaptureS(ConstructingMemberNameS("x"),FinalP),None,_,None),
            IntLiteralSE(4)),
          LetSE(_,_,_,
            AtomSP(CaptureS(ConstructingMemberNameS("y"),FinalP),None,_,None),
            LendSE(LocalLoadSE(ConstructingMemberNameS("x"),BorrowP), BorrowP)),
          FunctionCallSE(_,
            FunctionLoadSE(GlobalFunctionFamilyNameS("MyStruct")),
            List(
              LocalLoadSE(ConstructingMemberNameS("x"),OwnP),
              LocalLoadSE(ConstructingMemberNameS("y"),OwnP))))) =>
    }

  }

  test("this isnt special if was explicit param") {
    val program1 = compile(
      """fn moo(this &MyStruct) {
        |  println(this.x);
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("moo")
    main.body match {
      case CodeBody1(
        BodySE(
          List(),
          BlockSE(
            List(LocalVariable1(CodeVarNameS("this"),FinalP,Used,NotUsed,NotUsed,NotUsed,NotUsed,NotUsed)),
            List(
              FunctionCallSE(_,
                FunctionLoadSE(GlobalFunctionFamilyNameS("println")),
                List(DotSE(LocalLoadSE(CodeVarNameS("this"),BorrowP),"x",true))),
              VoidSE())))) =>
    }
  }
}
