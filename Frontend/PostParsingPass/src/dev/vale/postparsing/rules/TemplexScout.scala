package dev.vale.postparsing.rules

import dev.vale.lexing.RangeL
import dev.vale.parsing.ast._
import dev.vale.{Interner, Keywords, Profiler, RangeS, StrI, vassert, vassertSome, vimpl}
import dev.vale.postparsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TemplexScout(
    interner: Interner,
  keywords: Keywords) {
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
    contextRegion: IRuneS, // Nearest enclosing region marker, see RADTGCA.
    nameSN: IImpreciseNameS):
  RuneUsage = {
    val runeS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder += rules.MaybeCoercingLookupSR(rangeS, runeS, nameSN)
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

  // Returns:
  // - Rune for this type
  def translateTemplex(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    contextRegion: IRuneS, // Nearest enclosing region marker, see RADTGCA.
    templex: ITemplexPT):
  RuneUsage = {
    Profiler.frame(() => {
      val evalRange = (range: RangeL) => PostParser.evalRange(env.file, range)

      translateValueTemplex(templex) match {
        case Some(x) => {
          val rune = addLiteralRule(lidb.child(), ruleBuilder, evalRange(templex.range), x)
          rune
        }
        case None => {
          templex match {
            case InlinePT(range, inner) => translateTemplex(env, lidb, ruleBuilder, contextRegion, inner)
            case AnonymousRunePT(range) => {
              val rune = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
              rune
            }
            case RegionRunePT(range, None) => {
              vimpl() // isolates
            }
            case RegionRunePT(range, Some(NameP(_, name))) => {
              val isRuneFromLocalEnv = env.localDeclaredRunes().contains(CodeRuneS(name))
              if (isRuneFromLocalEnv) {
                val rune = rules.RuneUsage(evalRange(range), CodeRuneS(name))
                rune
              } else {
                // It's from a parent env
                val rune = addRuneParentEnvLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeRuneS(name))
                rune
              }
            }
            case NameOrRunePT(NameP(range, nameOrRune)) => {
              val isRuneFromEnv = env.allDeclaredRunes().contains(CodeRuneS(nameOrRune))
              if (isRuneFromEnv) {
                val isRuneFromLocalEnv = env.localDeclaredRunes().contains(CodeRuneS(nameOrRune))
                if (isRuneFromLocalEnv) {
                  val rune = rules.RuneUsage(evalRange(range), CodeRuneS(nameOrRune))
                  rune
                } else {
                  // It's from a parent env
                  val rune = addRuneParentEnvLookupRule(lidb.child(), ruleBuilder, evalRange(range), CodeRuneS(nameOrRune))
                  rune
                }
              } else {
                // e.g. "int"
                val name = interner.intern(CodeNameS(nameOrRune))
                val rune = addLookupRule(lidb.child(), ruleBuilder, evalRange(range), contextRegion, name)
                // For lookups like these, we bring them into the current region.
                rune
              }
            }
            case InterpretedPT(range, ownership, maybeRegion, innerP) => {
              val rangeS = evalRange(range)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))

              val maybeRegionRune =
                maybeRegion.map(runeName => {
                  val rune = CodeRuneS(vassertSome(runeName.name).str) // impl isolates
                  if (!env.allDeclaredRunes().contains(rune)) {
                    throw CompileErrorExceptionS(UnknownRegionError(rangeS, rune.name.str))
                  }
                  rules.RuneUsage(evalRange(range), rune)
                })

              // We need to use region as the new context region for everything under us, since
              // region annotations apply deeply.
              val newRegion =
                maybeRegionRune match {
                  case None => contextRegion
                  case Some(rune) => rune.rune
                }

              val innerRuneS =
                translateTemplex(env, lidb.child(), ruleBuilder, newRegion, innerP)

              ruleBuilder += rules.AugmentSR(evalRange(range), resultRuneS, ownership.map(_.ownership), innerRuneS)

              resultRuneS
            }
            case CallPT(rangeP, template, args) => {
              val rangeS = evalRange(rangeP)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.MaybeCoercingCallSR(
                  rangeS,
                  resultRuneS,
                  translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, template),
                  args.map(translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, _)))
              resultRuneS
            }
            case FunctionPT(rangeP, mutability, paramsPack, returnType) => {
              val rangeS = evalRange(rangeP)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              val templateNameRuneS =
                addLookupRule(
                  lidb.child(), ruleBuilder, rangeS, contextRegion, interner.intern(CodeNameS(keywords.IFUNCTION)))
              val mutabilityRuneS =
                mutability match {
                  case None => addLiteralRule(lidb.child(), ruleBuilder, rangeS, rules.MutabilityLiteralSL(MutableP))
                  case Some(m) => translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, m)
                }
              ruleBuilder +=
                rules.MaybeCoercingCallSR(
                  rangeS,
                  resultRuneS,
                  templateNameRuneS,
                  Vector(
                    mutabilityRuneS,
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion,  paramsPack),
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, returnType)))
              resultRuneS
            }
            case FuncPT(range, NameP(nameRange, name), paramsRangeL, paramsP, returnTypeP) => {
              val rangeS = PostParser.evalRange(env.file, range)
              val paramsRangeS = PostParser.evalRange(env.file, paramsRangeL)
              val paramsS =
                paramsP.map(paramP => {
                  translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, paramP)
                })
              val paramListRuneS = rules.RuneUsage(paramsRangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder += PackSR(paramsRangeS, paramListRuneS, paramsS.toVector)

              val returnRuneS = translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, returnTypeP)

              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))

              // Only appears in call site; filtered out when solving definition
              ruleBuilder += CallSiteFuncSR(rangeS, resultRuneS, name, paramListRuneS, returnRuneS)
              // Only appears in definition; filtered out when solving call site
              ruleBuilder += DefinitionFuncSR(rangeS, resultRuneS, name, paramListRuneS, returnRuneS)
              // Only appears in call site; filtered out when solving definition
              ruleBuilder += ResolveSR(rangeS, resultRuneS, name, paramListRuneS, returnRuneS)

              resultRuneS
            }
            case PackPT(rangeP, members) => {
              val rangeS = PostParser.evalRange(env.file, rangeP)

              val templateRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                MaybeCoercingLookupSR(
                  rangeS,
                  templateRuneS,
                  CodeNameS(keywords.tupleHumanName(members.length)))

              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder += MaybeCoercingCallSR(
                rangeS,
                resultRuneS,
                templateRuneS,
                members.map(translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, _)))


              resultRuneS
//              val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
//              ruleBuilder +=
//                rules.PackSR(
//                  evalRange(range),
//                  resultRuneS,
//                  members.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toVector)
//              resultRuneS
            }
            case StaticSizedArrayPT(rangeP, mutability, variability, size, element) => {
              val rangeS = evalRange(rangeP)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              val templateRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.LookupSR(
                  rangeS,
                  templateRuneS,
                  interner.intern(CodeNameS(keywords.StaticArray)))
              ruleBuilder +=
                MaybeCoercingCallSR(
                  rangeS,
                  resultRuneS,
                  templateRuneS,
                  Vector(
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, size),
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, mutability),
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, variability),
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, element)))
              resultRuneS
            }
            case RuntimeSizedArrayPT(rangeP, mutability, element) => {
              val rangeS = evalRange(rangeP)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              val templateRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.LookupSR(
                  rangeS,
                  templateRuneS,
                  interner.intern(CodeNameS(keywords.Array)))
              ruleBuilder +=
                MaybeCoercingCallSR(
                  rangeS,
                  resultRuneS,
                  templateRuneS,
                  Vector(
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, mutability),
                    translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, element)))
              resultRuneS
            }
            case TuplePT(rangeP, elements) => {
              val rangeS = evalRange(rangeP)
              val resultRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              val templateRuneS = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                rules.MaybeCoercingLookupSR(
                  rangeS,
                  templateRuneS,
                  interner.intern(CodeNameS(keywords.tupleHumanName(elements.length))))
              ruleBuilder +=
                rules.MaybeCoercingCallSR(
                  rangeS,
                  resultRuneS,
                  templateRuneS,
                  elements.map(translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, _)))
//              ruleBuilder +=
//                rules.CallSR(
//                  evalRange(range),
//                  resultRuneS,
//                  templateRuneS,
//                  Vector(packRuneS))
//              ruleBuilder +=
//                rules.PackSR(
//                  evalRange(range),
//                  packRuneS,
//                  elements.map(translateTemplex(env, lidb.child(), ruleBuilder, _)).toVector)
              resultRuneS
            }
          }
        }
      }
    })
  }

  // Returns:
  // - Rune for this type
  def translateTypeIntoRune(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    contextRegion: IRuneS, // Nearest enclosing region marker, see RADTGCA.
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
        translateTemplex(env, lidb.child(), ruleBuilder, contextRegion, nonRuneTemplexP)
      }
    }
  }

  // Returns:
  // - Rune for this type
  def translateMaybeTypeIntoRune(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    range: RangeS,
    ruleBuilder: ArrayBuffer[IRulexSR],
    contextRegion: IRuneS, // Nearest enclosing region marker, see RADTGCA.
    maybeTypeP: Option[ITemplexPT]):
  RuneUsage = {
    maybeTypeP match {
      case None => {
        val resultRuneS = rules.RuneUsage(range, ImplicitRuneS(lidb.child().consume()))
        resultRuneS
      }
      case Some(typeP) => {
        translateTypeIntoRune(env, lidb, ruleBuilder, contextRegion, typeP)
      }
    }
  }

  def translateMaybeTypeIntoMaybeRune(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    range: RangeS,
    ruleBuilder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)],
    contextRegion: IRuneS, // Nearest enclosing region marker, see RADTGCA.
    maybeTypeP: Option[ITemplexPT]):
  Option[RuneUsage] = {
    if (maybeTypeP.isEmpty) {
      None
    } else {
      Some(
        translateMaybeTypeIntoRune(
          env, lidb.child(), range, ruleBuilder, contextRegion, maybeTypeP))
    }
  }
}
