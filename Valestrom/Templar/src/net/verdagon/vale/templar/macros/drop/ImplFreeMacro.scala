package net.verdagon.vale.templar.macros.drop

import net.verdagon.vale._
import net.verdagon.vale.astronomer.{FunctionA, ImplA, StructA, VirtualFreeDeclarationNameS}
import net.verdagon.vale.parser.{LendConstraintP, MoveP}
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar.{OverloadTemplar, Templar, Temputs, env}
import net.verdagon.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionCallTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import net.verdagon.vale.templar.env.{FunctionEnvEntry, IEnvEntry}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.macros.{IFunctionBodyMacro, IOnImplDefinedMacro, IOnStructDefinedMacro}
import net.verdagon.vale.templar.names.{FullNameT, INameT, NameTranslator, VirtualFreeNameT}
import net.verdagon.vale.templar.types.{CoordT, ImmutableT, InterfaceTT, MutabilityT, ParamFilter, ReadonlyT, ShareT, StructTT, VoidT}

class ImplFreeMacro(overloadTemplar: OverloadTemplar) extends IOnStructDefinedMacro {
  val macroName: String = "DeriveImplFree"
//  val generatorId = "freeImplGenerator"

  override def getStructSiblingEntries(macroName: String, structName: FullNameT[INameT], structA: StructA): Vector[(FullNameT[INameT], IEnvEntry)] = {
    Vector()
  }

  override def getStructChildEntries(macroName: String, structName: FullNameT[INameT], structA: StructA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
    if (mutability != ImmutableT) {
      return Vector()
    }
    val virtualFreeFunctionA =
      FunctionA(
        structA.range,
        VirtualFreeDeclarationNameS(structA.range.begin),
        Vector(),
        TemplateTemplataType(
          structA.identifyingRunes.map(_.rune).map(structA.runeToType) :+ KindTemplataType,
          FunctionTemplataType),
        structA.identifyingRunes :+ RuneUsage(RangeS.internal(-64002), FreeOverrideInterfaceRuneS()),
        structA.runeToType +
          (ImplDropCoordRuneS() -> CoordTemplataType) +
          (ImplDropVoidRuneS() -> CoordTemplataType) +
          (FreeOverrideStructTemplateRuneS() -> structA.tyype) +
          (FreeOverrideStructRuneS() -> KindTemplataType) +
          (FreeOverrideInterfaceRuneS() -> KindTemplataType),
        Vector(
          ParameterS(
            AtomSP(
              RangeS.internal(-1340),
              Some(CaptureS(CodeVarNameS("this"))),
              Some(OverrideSP(RangeS.internal(-64002), RuneUsage(RangeS.internal(-64002), FreeOverrideInterfaceRuneS()))),
              Some(RuneUsage(RangeS.internal(-64002), ImplDropCoordRuneS())), None))),
        Some(RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS())),
        structA.rules ++
        Vector(
          CoerceToCoordSR(
            RangeS.internal(-167213),
            RuneUsage(RangeS.internal(-167215), ImplDropCoordRuneS()),
            RuneUsage(RangeS.internal(-167214), FreeOverrideStructRuneS())),
          structA.tyype match {
            case KindTemplataType => {
              EqualsSR(
                RangeS.internal(-167213),
                RuneUsage(RangeS.internal(-167219), FreeOverrideStructRuneS()),
                RuneUsage(RangeS.internal(-167219), FreeOverrideStructTemplateRuneS()))
            }
            case TemplateTemplataType(paramTypes, KindTemplataType) => {
              CallSR(
                RangeS.internal(-167213),
                RuneUsage(RangeS.internal(-167219), FreeOverrideStructRuneS()),
                RuneUsage(RangeS.internal(-167219), FreeOverrideStructTemplateRuneS()),
                structA.identifyingRunes.map(_.rune).map(r => RuneUsage(RangeS.internal(-167219), r)).toArray)
            }
          },
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), FreeOverrideStructTemplateRuneS()),structA.name.getImpreciseName),
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS()),CodeNameS("void"))),
//        GeneratedBodyS(generatorId))
        CodeBodyS(
          BodySE(RangeS.internal(-167213),
            Vector(),
            BlockSE(RangeS.internal(-167213),
              Vector(LocalS(CodeVarNameS("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              Vector(
                ReturnSE(RangeS.internal(-167213), VoidSE(RangeS.internal(-167214)))
              )))))
//                FunctionCallSE(RangeS.internal(-167213),
//                  OutsideLoadSE(RangeS.internal(-167213),
//                    Array(),
//                    FreeImpreciseNameS(),
//                    None,
//                    LendConstraintP(None)),
//                  Vector(LocalLoadSE(RangeS.internal(-167213), CodeVarNameS("this"), MoveP))))))))
    Vector((
      structName.addStep(NameTranslator.translateFunctionNameToTemplateName(virtualFreeFunctionA.name)),
      FunctionEnvEntry(virtualFreeFunctionA)))
  }


//  override def generateFunctionBody(
//    fate: env.FunctionEnvironment,
//    temputs: Temputs,
//    generatorId: String,
//    life: LocationInFunctionEnvironment,
//    callRange: RangeS,
//    originFunction: Option[FunctionA],
//    params2: Vector[ParameterT],
//    maybeRetCoord: Option[CoordT]):
//  FunctionHeaderT = {
//    val ret = CoordT(ShareT, ReadonlyT, VoidT())
//    val header = FunctionHeaderT(fate.fullName, Vector.empty, params2, ret, originFunction)
//
//    temputs.declareFunctionReturnType(header.toSignature, header.returnType)
//
//    val coord = vassertOne(params2.map(_.tyype))
//
//    val func =
//      overloadTemplar.findFunction(
//        fate,
//        temputs,
//        callRange,
//        FreeImpreciseNameS(),
//        Vector(),
//        Array(),
//        Vector(ParamFilter(coord, None)),
//        Vector(),
//        true)
//    val expr =
//      FunctionCallTE(func, Vector(ArgLookupTE(0, coord)))
//
//    val function2 = FunctionT(header, BlockTE(Templar.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
//    temputs.addFunction(function2)
//    function2.header
//  }
}
