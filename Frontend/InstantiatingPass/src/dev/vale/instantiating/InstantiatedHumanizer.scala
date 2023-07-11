package dev.vale.instantiating

import dev.vale._
import dev.vale.instantiating.ast._

object InstantiatedHumanizer {
  def humanizeTemplata[R <: IRegionsModeI](
    codeMap: CodeLocationS => String,
    templata: ITemplataI[R]):
  String = {
    templata match {
      case RuntimeSizedArrayTemplateTemplataI() => "Array"
      case StaticSizedArrayTemplateTemplataI() => "StaticArray"
      case InterfaceDefinitionTemplataI(envId, tyype) => humanizeId(codeMap, envId, None)
      case StructDefinitionTemplataI(envId, tyype) => humanizeId(codeMap, envId, None)
      case VariabilityTemplataI(variability) => {
        variability match {
          case FinalI => "final"
          case VaryingI => "vary"
        }
      }
      case IntegerTemplataI(value) => value.toString
      case MutabilityTemplataI(mutability) => {
        mutability match {
          case MutableI => "mut"
          case ImmutableI => "imm"
        }
      }
      case OwnershipTemplataI(ownership) => {
        ownership match {
          case OwnI => "own"
          case ImmutableBorrowI => "i&"
          case MutableBorrowI => "&"
          case ImmutableShareI => "i*"
          case MutableShareI => "*"
          case WeakI => "weak"
        }
      }
      case PrototypeTemplataI(range, prototype) => {
        humanizeId(codeMap, prototype.id)
      }
      case CoordTemplataI(region, coord) => {
        humanizeCoord(codeMap, coord)
      }
      case KindTemplataI(kind) => {
        humanizeKind(codeMap, kind)
      }
      case CoordListTemplataI(coords) => {
        "(" + coords.map(humanizeCoord(codeMap, _)).mkString(",") + ")"
      }
      case StringTemplataI(value) => "\"" + value + "\""
      case RegionTemplataI(pureHeight) => pureHeight.toString
      case other => vimpl(other)
    }
  }

  private def humanizeCoord[R <: IRegionsModeI](
    codeMap: CodeLocationS => String,
    coord: CoordI[R]
  ): String = {
    val CoordI(ownership, kind) = coord

    val ownershipStr =
      ownership match {
        case OwnI => ""
        case MutableShareI => ""
        case MutableBorrowI => "&"
        case ImmutableShareI => "#"
        case ImmutableBorrowI => "&#"
        case WeakI => "weak&"
      }
    val kindStr = humanizeKind(codeMap, kind)
    ownershipStr + kindStr
  }

  private def humanizeKind[R <: IRegionsModeI](
    codeMap: CodeLocationS => String,
    kind: KindIT[R]
  ) = {
    kind match {
      case IntIT(bits) => "i" + bits
      case BoolIT() => "bool"
      case StrIT() => "str"
      case NeverIT(_) => "never"
      case VoidIT() => "void"
      case FloatIT() => "float"
      case InterfaceIT(name) => humanizeId(codeMap, name)
      case StructIT(name) => humanizeId(codeMap, name)
      case RuntimeSizedArrayIT(name) => humanizeId(codeMap, name)
      case StaticSizedArrayIT(name) => humanizeId(codeMap, name)
    }
  }

  def humanizeId[R <: IRegionsModeI, I <: INameI[R]](
    codeMap: CodeLocationS => String,
    name: IdI[R, I],
    containingRegion: Option[ITemplataI[R]] = None):
  String = {
    (if (name.initSteps.nonEmpty) {
      name.initSteps.map(n => humanizeName(codeMap, n)).mkString(".") + "."
    } else {
      ""
    }) +
      humanizeName(codeMap, name.localName, containingRegion)
  }

  def humanizeName[R <: IRegionsModeI, I <: INameI[R]](
    codeMap: CodeLocationS => String,
    name: INameI[R],
    containingRegion: Option[ITemplataI[R]] = None):
  String = {
    name match {
      case AnonymousSubstructConstructorNameI(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(",") + ">" +
          "(" + parameters.map(humanizeCoord(codeMap, _)).mkString(",") + ")"
      }
      case AnonymousSubstructConstructorTemplateNameI(substruct) => {
        "asc:" + humanizeName(codeMap, substruct)
      }
      case SelfNameI() => "self"
      case IteratorNameI(range) => "it:" + codeMap(range.begin)
      case IterableNameI(range) => "ib:" + codeMap(range.begin)
      case IterationOptionNameI(range) => "io:" + codeMap(range.begin)
      case ForwarderFunctionNameI(_, inner) => humanizeName(codeMap, inner)
      case ForwarderFunctionTemplateNameI(inner, index) => "fwd" + index + ":" + humanizeName(codeMap, inner)
      case MagicParamNameI(codeLoc) => "mp:" + codeMap(codeLoc)
      case ClosureParamNameI(codeLocation) => "λP:" + codeMap(codeLocation)
      case ConstructingMemberNameI(name) => "cm:" + name
      case TypingPassBlockResultVarNameI(life) => "b:" + life
      case TypingPassFunctionResultVarNameI() => "(result)"
      case TypingPassTemporaryVarNameI(life) => "t:" + life
      case FunctionBoundTemplateNameI(humanName, codeLocation) => humanName.str
      case LambdaCallFunctionTemplateNameI(codeLocation, _) => "λF:" + codeMap(codeLocation)
      case LambdaCitizenTemplateNameI(codeLocation) => "λC:" + codeMap(codeLocation)
      case LambdaCallFunctionNameI(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          humanizeGenericArgs(codeMap, templateArgs, None) +
          "(" + parameters.map(humanizeCoord(codeMap, _)).mkString(",") + ")"
      }
      case FunctionBoundNameI(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          humanizeGenericArgs(codeMap, templateArgs, None) +
          "(" + parameters.map(humanizeCoord(codeMap, _)).mkString(",") + ")"
      }
      case CodeVarNameI(name) => name.str
      case LambdaCitizenNameI(template) => humanizeName(codeMap, template) + "<>"
      case FunctionTemplateNameI(humanName, codeLoc) => humanName.str
      case ExternFunctionNameI(humanName, parameters) => humanName.str
      case FunctionNameIX(templateName, templateArgs, parameters) => {
        humanizeName(codeMap, templateName) +
          humanizeGenericArgs(codeMap, templateArgs, containingRegion) +
          (if (parameters.nonEmpty) {
            "(" + parameters.map(humanizeCoord(codeMap, _)).mkString(",") + ")"
          } else {
            ""
          })
      }
      case CitizenNameI(humanName, templateArgs) => {
        humanizeName(codeMap, humanName) +
          humanizeGenericArgs(codeMap, templateArgs, containingRegion)
      }
      case RuntimeSizedArrayNameI(RuntimeSizedArrayTemplateNameI(), RawArrayNameI(mutability, elementType, region)) => {
        ("[]<" +
          (mutability match { case ImmutableI => "i" case MutableI => "m" }) + "," +
          humanizeTemplata(codeMap, region) + ">" +
          humanizeTemplata(codeMap, elementType))
      }
      case StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(), size, variability, RawArrayNameI(mutability, elementType, region)) => {
        ("[]<" +
          humanizeTemplata(codeMap, IntegerTemplataI(size)) + "," +
          humanizeTemplata(codeMap, MutabilityTemplataI(mutability)) + "," +
          humanizeTemplata(codeMap, VariabilityTemplataI(variability)) + "," +
          humanizeTemplata(codeMap, region) + ">" +
          humanizeTemplata(codeMap, elementType))
      }
      case AnonymousSubstructNameI(interface, templateArgs) => {
        humanizeName(codeMap, interface) +
          "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(",") + ">"
      }
      case AnonymousSubstructTemplateNameI(interface) => {
        humanizeName(codeMap, interface) + ".anonymous"
      }
      case StructTemplateNameI(humanName) => humanName.str
      case InterfaceTemplateNameI(humanName) => humanName.str
    }
  }

  private def humanizeGenericArgs[R <: IRegionsModeI](
    codeMap: CodeLocationS => String,
    templateArgs: Vector[ITemplataI[R]],
    containingRegion: Option[ITemplataI[R]]
  ) = {
    (
      if (templateArgs.nonEmpty) {
        "<" +
          (templateArgs.init.map(humanizeTemplata(codeMap, _)) ++
              templateArgs.lastOption.map(region => {
                containingRegion match {
                  case None => humanizeTemplata(codeMap, region)
                  case Some(r) => vassert(r == region); "_"
                }
              })).mkString(",") +
          ">"
      } else {
        ""
      })
  }

  def humanizeSignature[R <: IRegionsModeI](codeMap: CodeLocationS => String, signature: SignatureI[R]): String = {
    humanizeId(codeMap, signature.id)
  }
}
