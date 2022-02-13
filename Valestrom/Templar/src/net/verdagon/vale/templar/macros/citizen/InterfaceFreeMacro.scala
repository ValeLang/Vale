package net.verdagon.vale.templar.macros.citizen

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA}
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{LookupSR, RuneUsage}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment, FunctionEnvironmentBox, IEnvironment}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.macros.{IFunctionBodyMacro, IOnInterfaceDefinedMacro}
import net.verdagon.vale.templar.names._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{CompileErrorExceptionT, CouldntFindFunctionToCallT, OverloadTemplar, Templar, Temputs, env}
import net.verdagon.vale.{CodeLocationS, Err, Interner, Ok, RangeS, vassert}

class InterfaceFreeMacro(interner: Interner, overloadTemplar: OverloadTemplar) extends IOnInterfaceDefinedMacro {

  val generatorId = "interfaceFreeGenerator"

  val macroName: String = "DeriveInterfaceFree"

  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(
    interfaceName: FullNameT[INameT],
    interfaceA: InterfaceA,
    mutability: MutabilityT):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    if (mutability == ImmutableT) {
      val freeFunctionNameS = interner.intern(FreeDeclarationNameS(interfaceA.range.begin))
      val freeFunctionA =
        FunctionA(
          interfaceA.name.range,
          freeFunctionNameS,
          Vector(),
          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
          Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
          Vector(
            ParameterS(
              AtomSP(
                RangeS.internal(-1340),
                Some(CaptureS(interner.intern(CodeVarNameS("this")))),
                Some(AbstractSP(RangeS.internal(-64002), true)),
                Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
          Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
          Vector(
            LookupSR(RangeS.internal(-1672155), RuneUsage(RangeS.internal(-64002), CodeRuneS("T")), interner.intern(SelfNameS())),
            LookupSR(RangeS.internal(-1672156), RuneUsage(RangeS.internal(-64002), CodeRuneS("V")), interner.intern(CodeNameS("void")))),
          AbstractBodyS)

//      val virtualFreeFunctionNameS = interner.intern(AbstractVirtualFreeDeclarationNameS(interfaceA.range.begin))
//      val virtualFreeFunctionA =
//        FunctionA(
//          interfaceA.range,
//          virtualFreeFunctionNameS,
//          Vector(),
//          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
//          Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
//          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
//          Vector(
//            ParameterS(
//              AtomSP(
//                RangeS.internal(-1340),
//                Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//                Some(AbstractSP(RangeS.internal(-1340), true)),
//                Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
//          Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
//          Vector(
//            LookupSR(RangeS.internal(-1672157), RuneUsage(RangeS.internal(-64002), CodeRuneS("T")), interner.intern(SelfNameS())),
//            LookupSR(RangeS.internal(-1672158), RuneUsage(RangeS.internal(-64002), CodeRuneS("V")), interner.intern(CodeNameS("void")))),
//          GeneratedBodyS("abstractBody"))

      Vector(
        interfaceName.addStep(interner.intern(FreeTemplateNameT(freeFunctionNameS.codeLocationS))) ->
          FunctionEnvEntry(freeFunctionA))//,
//        interfaceName.addStep(interner.intern(AbstractVirtualFreeTemplateNameT(virtualFreeFunctionNameS.codeLoc))) ->
//          FunctionEnvEntry(virtualFreeFunctionA))
    } else {
      Vector()
    }
  }

//  override def generateFunctionBody(
//    env: FunctionEnvironment,
//    temputs: Temputs,
//    generatorId: String,
//    life: LocationInFunctionEnvironment,
//    callRange: RangeS,
//    originFunction1: Option[FunctionA],
//    params2: Vector[ParameterT],
//    maybeRetCoord: Option[CoordT]):
//  FunctionHeaderT = {
//    val Vector(paramCoord @ CoordT(ShareT, InterfaceTT(_))) = params2.map(_.tyype)
//
//    val ret = CoordT(ShareT, VoidT())
//    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)
//
//    temputs.declareFunctionReturnType(header.toSignature, header.returnType)
//
//    val virtualFreePrototype =
//      overloadTemplar.findFunction(
//        env, temputs, callRange, interner.intern(VirtualFreeImpreciseNameS()), Vector(), Array(),
//        Vector(ParamFilter(paramCoord, None)), Vector(), true) match {
//        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
//        case Ok(x) => x
//      }
//
//    val expr = FunctionCallTE(virtualFreePrototype, Vector(ArgLookupTE(0, paramCoord)))
//
//    val function2 = FunctionT(header, BlockTE(Templar.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
//    temputs.addFunction(function2)
//    function2.header}
}
