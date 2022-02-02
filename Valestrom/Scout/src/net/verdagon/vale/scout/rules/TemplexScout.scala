package net.verdagon.vale.scout.rules

import net.verdagon.vale.RangeS
import net.verdagon.vale.parser.ast.{AnonymousRunePT, BoolPT, BorrowP, BorrowPT, CallPT, FunctionPT, ITemplexPT, IntPT, InterpretedPT, LocationPT, ManualSequencePT, MutabilityPT, MutableP, NameOrRunePT, NameP, OwnershipPT, PackPT, PermissionPT, PrototypePT, RangeP, ReadonlyP, RegionRune, RepeaterSequencePT, StringPT, VariabilityPT}
import net.verdagon.vale.scout.{CodeNameS, CodeRuneS, IEnvironment, IImpreciseNameS, INameS, IRuneS, ImplicitRuneS, LocationInDenizenBuilder, Scout}

import scala.collection.mutable.ArrayBuffer

object TemplexScout {
  def addLiteralRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    valueSR: ILiteralSL):
  RuneUsage = {
    val runeS = RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder += LiteralSR(rangeS, runeS, valueSR)
    runeS
  }

  def addRuneParentEnvLookupRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    runeS: IRuneS):
  RuneUsage = {
    val usage = RuneUsage(rangeS, runeS)
    ruleBuilder += RuneParentEnvLookupSR(rangeS, usage)
    usage
  }

  def addLookupRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    nameSN: IImpreciseNameS):
  RuneUsage = {
    val runeS = RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder += LookupSR(rangeS, runeS, nameSN)
    runeS
  }

  def translateValueTemplex(templex: ITemplexPT): Option[ILiteralSL] = {
    templex match {
      case IntPT(_, value) => Some(IntLiteralSL(value))
      case BoolPT(_, value) => Some(BoolLiteralSL(value))
      case MutabilityPT(_, mutability) => Some(MutabilityLiteralSL(mutability))
      case VariabilityPT(_, variability) => Some(VariabilityLiteralSL(variability))
      case PermissionPT(_, permission) => Some(PermissionLiteralSL(permission))
      case StringPT(_, value) => Some(StringLiteralSL(value))
      case LocationPT(_, location) => Some(LocationLiteralSL(location))
      case OwnershipPT(_, ownership) => Some(OwnershipLiteralSL(ownership))
      case _ => None
    }
  }

  def translateTemplex(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    templex: ITemplexPT):
  RuneUsage = {
    val evalRange = (range: RangeP) => Scout.evalRange(env.file, range)

    translateValueTemplex(templex) match {
      case Some(x) => addLiteralRule(lidb.child(), ruleBuilder, evalRange(templex.range), x)
      case None => {
        templex match {
          case AnonymousRunePT(range) => RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
          case RegionRune(range, NameP(_, name)) => {
            val isRuneFromLocalEnv = env.localDeclaredRunes().contains(CodeRuneS(name))
            if (isRuneFromLocalEnv) {
              RuneUsage(evalRange(range), CodeRuneS(name))
            } else {
              // It's from a parent env
              addRuneParentEnvLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeRuneS(name))
            }
          }
          case NameOrRunePT(NameP(range, nameOrRune)) => {
            val isRuneFromEnv = env.allDeclaredRunes().contains(CodeRuneS(nameOrRune))
            if (isRuneFromEnv) {
              val isRuneFromLocalEnv = env.localDeclaredRunes().contains(CodeRuneS(nameOrRune))
              if (isRuneFromLocalEnv) {
                RuneUsage(evalRange(range), CodeRuneS(nameOrRune))
              } else {
                // It's from a parent env
                addRuneParentEnvLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeRuneS(nameOrRune))
              }
            } else {
              val valueSR = CodeNameS(nameOrRune)
              addLookupRule(lidb.child(), ruleBuilder, evalRange(range), valueSR)
            }
          }
          case InterpretedPT(range, ownership, permission, innerP) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            val innerRuneS = translateTemplex(env, lidb.child(), ruleBuilder, innerP)
            ruleBuilder += AugmentSR(evalRange(range), resultRuneS, ownership, permission, innerRuneS)
            resultRuneS
          }
          case BorrowPT(range, innerP) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            val innerRuneS = translateTemplex(env, lidb.child(), ruleBuilder, innerP)
            ruleBuilder += AugmentSR(evalRange(range), resultRuneS, BorrowP, ReadonlyP, innerRuneS)
            resultRuneS
          }
          case CallPT(range, template, args) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            ruleBuilder +=
              CallSR(
                evalRange(range),
                resultRuneS,
                translateTemplex(env, lidb.child(), ruleBuilder, template),
                args.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
            resultRuneS
          }
          case FunctionPT(range, mutability, paramsPack, returnType) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            val templateNameRuneS = addLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeNameS("IFunction"))
            val mutabilityRuneS =
              mutability match {
                case None => addLiteralRule(lidb.child(), ruleBuilder, evalRange(range), MutabilityLiteralSL(MutableP))
                case Some(m) => translateTemplex(env, lidb.child(), ruleBuilder, m)
              }
            ruleBuilder +=
              CallSR(
                evalRange(range),
                resultRuneS,
                templateNameRuneS,
                Array(
                  mutabilityRuneS,
                  translateTemplex(env, lidb.child(), ruleBuilder, paramsPack),
                  translateTemplex(env, lidb.child(), ruleBuilder, returnType)))
            resultRuneS
          }
          case PrototypePT(range, NameP(_, name), parameters, returnType) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            ruleBuilder +=
              PrototypeSR(
                evalRange(range),
                resultRuneS,
                name,
                parameters.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray,
                translateTemplex(env, lidb.child(), ruleBuilder, returnType))
            resultRuneS
          }
          case PackPT(range, members) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            ruleBuilder +=
              PackSR(
                evalRange(range),
                resultRuneS,
                members.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
            resultRuneS
          }
          case RepeaterSequencePT(range, mutability, variability, size, element) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            ruleBuilder +=
              RepeaterSequenceSR(
                evalRange(range),
                resultRuneS,
                translateTemplex(env, lidb.child(), ruleBuilder, mutability),
                translateTemplex(env, lidb.child(), ruleBuilder, variability),
                translateTemplex(env, lidb.child(), ruleBuilder, size),
                translateTemplex(env, lidb.child(), ruleBuilder, element))
            resultRuneS
          }
          case ManualSequencePT(range, elements) => {
            val resultRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            val templateRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            val packRuneS = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            ruleBuilder +=
              LookupSR(
                evalRange(range),
                templateRuneS,
                CodeNameS("Tup"))
            ruleBuilder +=
              CallSR(
                evalRange(range),
                resultRuneS,
                templateRuneS,
                Array(packRuneS))
            ruleBuilder +=
              PackSR(
                evalRange(range),
                packRuneS,
                elements.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
            resultRuneS
          }
        }
      }
    }
  }
}
