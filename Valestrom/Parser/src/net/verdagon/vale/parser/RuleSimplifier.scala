package net.verdagon.vale.parser

object RuleSimplifier {
//  sealed trait ISimplifyResult
//  case class
//
//  def getTemplex(rule: ITemplataPR): Option[ITemplexPT] = {
//    rule match {
//      case RuneRulePR(_) => None
//      case tcPR @ CoordPR(_, _) => getCoordTemplex(tcPR)
//    }
//  }
//
//  def getKindTemplex(tkindPR: KindPR): Option[ITemplexPT] = {
//    val KindPR(_, KindPR(maybeFilter)) = tkindPR
//    maybeFilter match {
//      case None => None
//      case Some(filter) => getKindFilterTemplex(filter)
//    }
//  }
//
//  def getKindFilterTemplex(filterPR: IKindFilterPR): Option[ITemplexPT] = {
//    filterPR match {
//      case NamedKindPR(name) => Some(NamePT(name))
//      case TemplateCallKindFilterPR(template, args) => CallPT()
//      case OrKindFilterPR(orees) => {
//        orees.map(getKindFilterTemplex).reduce({
//          case (None, None) => None
//          case (None, Some(right)) => Some(right)
//          case (Some(left), None) => Some(left)
//          case (Some(left), Some(right)) => {
//            if (left == right) {
//              Some(left)
//            } else {
//              // If they aren't exactly equal, then abort, can't narrow down to templex.
//              None
//            }
//          }
//        })
//      }
//      case AndKindFilterPR(andees) => {
//        andees.map(getKindFilterTemplex).reduce({
//          case (None, None) => None
//          case (None, Some(right)) => Some(right)
//          case (Some(left), None) => Some(left)
//          case (Some(left), Some(right)) => {
//            if (left == right) {
//              Some(left)
//            } else {
//              (left, right) match {
//                case _ => vfail("impl!")
//              }
//            }
//          }
//        })
//      }
//      case StructKindFilterPR => None
//      case InterfaceKindFilterPR => None
//      case ImmutableKindFilterPR => None
//      case ClosureKindFilterPR =>
//      case CitizenTemplateKindFilterPR =>
//      case OverrideKindFilterPR(interface) =>
//      case CallableKindFilterPR(parameters, returnType) =>
//      case RepeaterSequenceTemplexPR(size, element) =>
//      case ArrayKindFilterPR(element) =>
//      case ManualSequenceTemplexPR(elements) =>
//      case MembersKindFilterPR(parameters) =>
//    }
//  }
//
//  def getCoordTemplex(rule: CoordPR): Option[ITemplexPT] = {
//    val CoordPR(_, CoordPR(maybeOwnership, maybeKindPR)) = rule
//    maybeKindPR match {
//      case None => None
//      case Some(kindPR) => {
//        val maybeKindPT = getKindTemplex(kindPR)
//        maybeKindPT match {
//          case None => None
//          case Some(kindPT) => {
//            maybeOwnership match {
//              case None => Some(kindPT)
//              case Some(OwnershipPR(_, OwnershipPR(maybeFilter))) => {
//                maybeFilter match {
//                  case None => Some(kindPT)
//                  case Some(filter) => {
//                    if (filter.isEmpty) {
//                      // Should be impossible, should be enforced by OwnershipPR's constructor
//                      vfail("wot")
//                    } else if (filter.size == 1) {
//                      filter.head match {
//                        case OwnP => Some(OwnPT(kindPT))
//                        case ShareP => Some(SharePT(kindPT))
//                        case BorrowP => Some(BorrowPT(kindPT))
//                      }
//                    } else {
//                      // If there are multiple possibilities we can't really narrow down to a templex
//                      None
//                    }
//                  }
//                }
//              }
//            }
//          }
//        }
//      }
//    }
//  }
}
