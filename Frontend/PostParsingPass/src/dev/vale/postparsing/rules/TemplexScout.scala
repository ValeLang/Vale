package dev.vale.postparsing.rules

import dev.vale.lexing.RangeL
import dev.vale.parsing.ast.{AnonymousRunePT, BoolPT, BorrowP, BorrowPT, CallPT, FunctionPT, ITemplexPT, InlinePT, IntPT, InterpretedPT, LocationPT, MutabilityPT, MutableP, NameOrRunePT, NameP, OwnershipPT, PackPT, PrototypePT, RegionRunePT, RuntimeSizedArrayPT, StaticSizedArrayPT, StringPT, TuplePT, VariabilityPT}
import dev.vale.{Interner, Profiler, RangeS, StrI}
import dev.vale.postparsing.{CodeNameS, CodeRuneS, IEnvironment, IImpreciseNameS, IRuneS, ITemplataType, ImplicitRuneS, LocationInDenizenBuilder, PostParser, rules}
import dev.vale.parsing.ast._
import dev.vale.postparsing._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TemplexScout(
    interner: Interner) {
  val IFUNCTION = interner.intern(StrI("IFunction"))
  val TUP = interner.intern(StrI("Tup"))

  def addLiteralRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    valueSR: ILiteralSL):
  RuneUsage = {
    val runeS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder += LiteralSR(rangeS, runeS, valueSR)
    runeS
  }

  def addRuneParentEnvLookupRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    runeS: IRuneS):
  RuneUsage = {
    val usage = rules.RuneUsage(rangeS, runeS)
    ruleBuilder += RuneParentEnvLookupSR(rangeS, usage)
    usage
  }

  def addLookupRule(
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    rangeS: RangeS,
    nameSN: IImpreciseNameS):
  RuneUsage = {
    val runeS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder += rules.LookupSR(rangeS, runeS, nameSN)
    runeS
  }

  def translateValueTemplex(templex: ITemplexPT): Option[ILiteralSL] = {
    templex match {
      case IntPT(_, value) => Some(IntLiteralSL(value))
      case BoolPT(_, value) => Some(BoolLiteralSL(value))
      case MutabilityPT(_, mutability) => Some(MutabilityLiteralSL(mutability))
      case VariabilityPT(_, variability) => Some(VariabilityLiteralSL(variability))
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
    Profiler.frame(() => {
      val evalRange = (range: RangeL) => PostParser.evalRange(env.file, range)

      translateValueTemplex(templex) match {
        case Some(x) => addLiteralRule(lidb.child(), ruleBuilder, evalRange(templex.range), x)
        case None => {
          templex match {
            case InlinePT(range, inner) => translateTemplex(env, lidb, ruleBuilder, inner)
            case AnonymousRunePT(range) => rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            case RegionRunePT(range, NameP(_, name)) => {
              val isRuneFromLocalEnv = env.localDeclaredRunes().contains(CodeRuneS(name))
              if (isRuneFromLocalEnv) {
                rules.RuneUsage(evalRange(range), CodeRuneS(name))
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
                  rules.RuneUsage(evalRange(range), CodeRuneS(nameOrRune))
                } else {
                  // It's from a parent env
                  addRuneParentEnvLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeRuneS(nameOrRune))
                }
              } else {
                val valueSR = interner.intern(CodeNameS(nameOrRune))
                addLookupRule(lidb.child(), ruleBuilder, evalRange(range), valueSR)
              }
            }
            case InterpretedPT(range, ownership, innerP) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              val innerRuneS = translateTemplex(env, lidb.child(), ruleBuilder, innerP)
              ruleBuilder += rules.AugmentSR(evalRange(range), resultRuneS, ownership, innerRuneS)
              resultRuneS
            }
            case BorrowPT(range, innerP) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              val innerRuneS = translateTemplex(env, lidb.child(), ruleBuilder, innerP)
              ruleBuilder += rules.AugmentSR(evalRange(range), resultRuneS, BorrowP, innerRuneS)
              resultRuneS
            }
            case CallPT(range, template, args) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.CallSR(
                  evalRange(range),
                  resultRuneS,
                  translateTemplex(env, lidb.child(), ruleBuilder, template),
                  args.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
              resultRuneS
            }
            case FunctionPT(range, mutability, paramsPack, returnType) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              val templateNameRuneS = addLookupRule(lidb.child(), ruleBuilder, evalRange(range), interner.intern(CodeNameS(IFUNCTION)))
              val mutabilityRuneS =
                mutability match {
                  case None => addLiteralRule(lidb.child(), ruleBuilder, evalRange(range), rules.MutabilityLiteralSL(MutableP))
                  case Some(m) => translateTemplex(env, lidb.child(), ruleBuilder, m)
                }
              ruleBuilder +=
                rules.CallSR(
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
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
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
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.PackSR(
                  evalRange(range),
                  resultRuneS,
                  members.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
              resultRuneS
            }
            case StaticSizedArrayPT(range, mutability, variability, size, element) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                StaticSizedArraySR(
                  evalRange(range),
                  resultRuneS,
                  translateTemplex(env, lidb.child(), ruleBuilder, mutability),
                  translateTemplex(env, lidb.child(), ruleBuilder, variability),
                  translateTemplex(env, lidb.child(), ruleBuilder, size),
                  translateTemplex(env, lidb.child(), ruleBuilder, element))
              resultRuneS
            }
            case RuntimeSizedArrayPT(range, mutability, element) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                RuntimeSizedArraySR(
                  evalRange(range),
                  resultRuneS,
                  translateTemplex(env, lidb.child(), ruleBuilder, mutability),
                  translateTemplex(env, lidb.child(), ruleBuilder, element))
              resultRuneS
            }
            case TuplePT(range, elements) => {
              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              val templateRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              val packRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.LookupSR(
                  evalRange(range),
                  templateRuneS,
                  interner.intern(CodeNameS(TUP)))
              ruleBuilder +=
                rules.CallSR(
                  evalRange(range),
                  resultRuneS,
                  templateRuneS,
                  Array(packRuneS))
              ruleBuilder +=
                rules.PackSR(
                  evalRange(range),
                  packRuneS,
                  elements.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toArray)
              resultRuneS
            }
          }
        }
      }
    })
  }

  def translateTypeIntoRune(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    typeP: ITemplexPT):
  RuneUsage = {
    typeP match {
      case NameOrRunePT(NameP(range, nameOrRune)) if env.allDeclaredRunes().contains(CodeRuneS(nameOrRune)) => {
        val resultRuneS = rules.RuneUsage(PostParser.evalRange(env.file, range), CodeRuneS(nameOrRune))
        //        ruleBuilder += ValueLeafSR(range, resultRuneS, EnvRuneLookupSR(CodeRuneS(nameOrRune)))
        //        resultRuneS
        resultRuneS
      }
      case nonRuneTemplexP => {
        translateTemplex(env, lidb.child(), ruleBuilder, nonRuneTemplexP)
      }
    }
  }
  def translateMaybeTypeIntoRune(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    range: RangeS,
    ruleBuilder: ArrayBuffer[IRulexSR],
    maybeTypeP: Option[ITemplexPT]):
  RuneUsage = {
    maybeTypeP match {
      case None => {
        val resultRuneS = rules.RuneUsage(range, ImplicitRuneS(lidb.child().consume()))
        resultRuneS
      }
      case Some(typeP) => {
        translateTypeIntoRune(env, lidb, ruleBuilder, typeP)
      }
    }
  }
  def translateMaybeTypeIntoMaybeRune(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    range: RangeS,
    ruleBuilder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.HashMap[IRuneS, ITemplataType],
    maybeTypeP: Option[ITemplexPT]):
  Option[RuneUsage] = {
    if (maybeTypeP.isEmpty) {
      None
    } else {
      Some(
        translateMaybeTypeIntoRune(
          env, lidb.child(), range, ruleBuilder, maybeTypeP))
    }
  }
}
