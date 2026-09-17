use crate::lexing::errors::ParseError;
use crate::utils::source_code_utils;
use std::path::Path;

pub struct ParseErrorHumanizer;

impl ParseErrorHumanizer {
  pub fn humanize(file_path: &Path, source: &str, err: &ParseError) -> String {
    let error_message = Self::get_error_message(err);
    let pos_str = source_code_utils::humanize_pos(file_path, source, err.pos());
    let next_stuff = source_code_utils::next_thing_and_rest_of_line(source, err.pos() as usize);

    format!(
      "{} error {}: {}\n{}\n",
      pos_str,
      err.error_id(),
      error_message,
      next_stuff
    )
  }

  fn get_error_message(err: &ParseError) -> &str {
    match err {
      ParseError::RangedInternalError { .. } => "Internal error",
      ParseError::UnrecognizableExpressionAfterAugment(_) => "Unrecognizable expression",
      ParseError::BadMemberEnd(_) => "Bad member end.",
      ParseError::OnlyRegionRunesCanHaveMutability(_) => {
        "Only region runes, such as 'x, can have ro or rw or mut."
      }
      ParseError::BadRuleCallParam(_) => "Bad rule call param.",
      ParseError::BadFunctionAfterParam(_) => "Bad end of param.",
      ParseError::BadRuneTypeError(_) => "Bad rune type.",
      ParseError::BadTemplateCallParam(_) => "Bad template call param.",
      ParseError::BadDestructureError(_) => "Bad destructure.",
      ParseError::BadTypeExpression(_) => "Bad type expression.",
      ParseError::BadLocalName(_) => "Bad local name.",
      ParseError::LightFunctionMustHaveParamTypes { .. } => "Function parameter must have a type!",
      ParseError::BadStringInterpolationEnd(_) => "Bad string interpolation end.",
      ParseError::CantUseBreakInExpression(_) => "Can't use break inside an expression.",
      ParseError::CantUseReturnInExpression(_) => "Can't use return inside an expression.",
      ParseError::BadInterfaceHeader(_) => "Bad interface header.",
      ParseError::BadStartOfBlock(_) => "Bad start of block.",
      ParseError::DontNeedSemicolon(_) => "Dont need semicolon.",
      ParseError::BadDot(_) => "Bad dot.",
      ParseError::BadImplFor(_) => "Bad impl, expected `for`",
      ParseError::BadForeachInError(_) => "Bad foreach, expected `in`.",
      ParseError::BadAttributeError(_) => "Bad attribute.",
      ParseError::BadStructContentsBegin(_) => "Bad start of struct contents.",
      ParseError::BadInterfaceMember(_) => "Bad interface member.",
      ParseError::BadStringChar { .. } => "Bad string character.",
      ParseError::BadExpressionBegin(_) => "Bad start of expression.",
      ParseError::NeedWhitespaceAroundBinaryOperator(_) => {
        "Need whitespace around binary operator."
      }
      ParseError::UnknownTupleOrSubExpression(_) => {
        "Saw ( but expression is neither tuple nor sub-expression."
      }
      ParseError::NeedSemicolon(_) => "Need semicolon.",
      ParseError::BadStructMember(_) => "Bad struct member.",
      ParseError::BadBinaryFunctionName(_) => "Bad binary function name.",
      ParseError::UnrecognizedDenizenError(_) => {
        "expected func, struct, interface, impl, import, or export, but found:"
      }
      ParseError::BadFunctionBodyError(_) => {
        "expected a function body, or `;` to note there is none. Found:"
      }
      ParseError::BadStartOfStatementError(_) => "expected `}` to end the block, but found:",
      ParseError::BadExpressionEnd(_) => "expected `;` or `}` after expression, but found:",
      ParseError::IfBlocksMustBothOrNeitherReturn(_) => {
        "If blocks should either both ret, or neither return."
      }
      ParseError::ForgotSetKeyword(_) => {
        "Need `set` keyword to mutate a variable that already exists."
      }
      ParseError::FuncBoundWithoutWhere(_) => {
        "Function bound needs `where` keyword before it. e.g.: `func foo<T>() where func bar(&T)void { ... }`"
      }
      ParseError::BadStartOfWhileCondition(_) => "Bad start of while condition, expected (",
      ParseError::BadEndOfWhileCondition(_) => "Bad end of while condition, expected )",
      ParseError::BadStartOfWhileBody(_) => "Bad start of while body, expected {",
      ParseError::BadEndOfWhileBody(_) => "Bad end of while body, expected }",
      ParseError::BadStartOfIfCondition(_) => "Bad start of if condition, expected (",
      ParseError::BadEndOfIfCondition(_) => "Bad end of if condition, expected )",
      ParseError::BadStartOfIfBody(_) => "Bad start of if body, expected {",
      ParseError::BadEndOfIfBody(_) => "Bad end of if body, expected }",
      ParseError::BadStartOfElseBody(_) => "Bad start of else body, expected {",
      ParseError::BadEndOfElseBody(_) => "Bad end of else body, expected }",
      ParseError::BadLetEqualsError(_) => "Expected = after declarations",
      ParseError::BadMutateEqualsError(_) => "Expected = after set destination",
      ParseError::BadLetEndError(_) => "Expected ; after declarations source",
      ParseError::BadArraySizerEnd(_) => "Bad array sizer; expected ]",
      ParseError::BadLetSourceError { .. } => {
        "Parse error somewhere inside this let source expression."
      }
      ParseError::CantUseThatLocalName { .. } => "Can't use that local name.",
      ParseError::EmptyPattern(_) => "Empty pattern.",
      ParseError::EmptyParameter(_) => "Empty parameter.",
      ParseError::BadImportName(_) => "Bad import name.",
      ParseError::BadLambdaBegin(_) => "Bad lambda begin.",
      ParseError::BadLambdaBodyBegin(_) => "Bad lambda body begin.",

      // Additional variants not in original Scala match - use default messages
      ParseError::BadNameBeforeDestructure(_) => "Bad name before destructure.",
      ParseError::BadLocalNameInUnlet(_) => "Bad local name in unlet.",
      ParseError::FoundBothAbstractAndOverride(_) => "Found both abstract and override.",
      ParseError::FoundBothImmutableAndMutabilityInArray(_) => {
        "Found both immutable and mutability in array."
      }
      ParseError::BadStringInTemplex(_) => "Bad string in templex.",
      ParseError::BadPrototypeName(_) => "Bad prototype name.",
      ParseError::BadPrototypeParams(_) => "Bad prototype params.",
      ParseError::BadTupleElement(_) => "Bad tuple element.",
      ParseError::ShareCantBeReadwrite(_) => "Share can't be readwrite.",
      ParseError::BadRuneEnd(_) => "Bad rune end.",
      ParseError::BadRegionName(_) => "Bad region name.",
      ParseError::BadRuneNameError(_) => "Bad rune name error.",
      ParseError::RegionRuneHasType(_) => "Region rune has type.",
      ParseError::BadRule(_) => "Bad rule.",
      ParseError::UnexpectedAttributes(_) => "Unexpected attributes.",
      ParseError::BadFunctionName(_) => "Bad function name.",
      ParseError::BadParamEnd(_) => "Bad param end.",
      ParseError::BadTemplateCallee(_) => "Bad template callee.",
      ParseError::BadRangeOperand(_) => "Bad range operand.",
      ParseError::CantUseWhileInExpression(_) => "Can't use while in expression.",
      ParseError::CantTemplateCallMember(_) => "Can't template call member.",
      ParseError::BadUnicodeChar(_) => "Bad unicode char.",
      ParseError::BadEndOfBlock(_) => "Bad end of block.",
      ParseError::BadVPSTError { .. } => "Bad VPST error.",
      ParseError::BadArraySizer(_) => "Bad array sizer.",
      ParseError::BadStructName(_) => "Bad struct name.",
      ParseError::BadInterfaceName(_) => "Bad interface name.",
      ParseError::BadStructContentsEnd(_) => "Bad struct contents end.",
      ParseError::BadStructMemberType(_) => "Bad struct member type.",
      ParseError::VariadicStructMemberHasName(_) => "Variadic struct member has name.",
      ParseError::BadInterfaceContentsBegin(_) => "Bad interface contents begin.",
      ParseError::BadImpl(_) => "Bad impl.",
      ParseError::BadImplStruct(_) => "Bad impl struct.",
      ParseError::BadImplInterface(_) => "Bad impl interface.",
      ParseError::BadImplEnd(_) => "Bad impl end.",
      ParseError::BadExportAs(_) => "Bad export as.",
      ParseError::BadImportEnd(_) => "Bad import end.",
      ParseError::BadFunctionParamsBegin(_) => "Bad function params begin.",
      ParseError::BadExportName(_) => "Bad export name.",
      ParseError::BadExportEnd(_) => "Bad export end.",
      ParseError::BadForeachIterableError(_) => "Bad foreach iterable error.",
      ParseError::BadArraySpecifier(_) => "Bad array specifier.",
      ParseError::BadThingAfterTypeInPattern(_) => "Bad thing after type in pattern.",
      ParseError::BadExternAttribute(_) => {
        "Bad extern attribute. Expected string literal in parentheses."
      }
    }
  }
}

