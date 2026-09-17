use crate::interner::StrI;

/// Position range in source code (test edit)
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RangeL(pub i32, pub i32);

impl RangeL {
  pub fn new(begin: i32, end: i32) -> Self {
    assert!(begin == end || begin <= end);
    RangeL(begin, end)
  }

  pub fn zero() -> Self {
    RangeL(0, 0)
  }

  pub fn begin(&self) -> i32 {
    self.0
  }

  pub fn end(&self) -> i32 {
    self.1
  }
}

/// A file with top-level denizens
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FileL<'p> {
  pub denizens: &'p [IDenizenL<'p>],
  pub comment_ranges: &'p [RangeL],
}

/// Top-level items in a file
#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IDenizenL<'p> {
  TopLevelFunction(FunctionL<'p>),
  TopLevelStruct(StructL<'p>),
  TopLevelInterface(InterfaceL<'p>),
  TopLevelImpl(ImplL<'p>),
  TopLevelExportAs(ExportAsL<'p>),
  TopLevelImport(ImportL<'p>),
}

/// Impl block
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ImplL<'p> {
  pub range: RangeL,
  pub identifying_runes: Option<AngledLE<'p>>,
  pub template_rules: Option<ScrambleLE<'p>>,
  pub struct_: Option<ScrambleLE<'p>>, // Option because we can say `impl MyInterface;` inside a struct
  pub interface: ScrambleLE<'p>,
  pub attributes: &'p [IAttributeL<'p>],
}

/// Export as declaration
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ExportAsL<'p> {
  pub range: RangeL,
  pub contents: ScrambleLE<'p>,
}

/// Import declaration
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ImportL<'p> {
  pub range: RangeL,
  pub module_name: WordLE<'p>,
  pub package_steps: &'p [WordLE<'p>],
  pub importee_name: WordLE<'p>,
}

/// Struct definition
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct StructL<'p> {
  pub range: RangeL,
  pub name: WordLE<'p>,
  pub attributes: &'p [IAttributeL<'p>],
  pub mutability: Option<ScrambleLE<'p>>,
  pub identifying_runes: Option<AngledLE<'p>>,
  pub template_rules: Option<ScrambleLE<'p>>,
  pub contents_range: RangeL,
  pub members: &'p [ScrambleLE<'p>],
  pub methods: &'p [FunctionL<'p>],
}

/// Interface definition
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct InterfaceL<'p> {
  pub range: RangeL,
  pub name: WordLE<'p>,
  pub attributes: &'p [IAttributeL<'p>],
  pub mutability: Option<ScrambleLE<'p>>,
  pub maybe_identifying_runes: Option<AngledLE<'p>>,
  pub template_rules: Option<ScrambleLE<'p>>,
  pub body_range: RangeL,
  pub members: &'p [FunctionL<'p>],
}

/// Attributes on declarations
#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IAttributeL<'p> {
  AbstractAttribute(RangeL),
  ExportAttribute(RangeL),
  PureAttribute(RangeL),
  AdditiveAttribute(RangeL),
  ExternAttribute {
    range: RangeL,
    maybe_custom_name: Option<ParendLE<'p>>,
  },
  LinearAttribute(RangeL),
  WeakableAttribute(RangeL),
  SealedAttribute(RangeL),
  MacroCall {
    range: RangeL,
    inclusion: IMacroInclusionL,
    name: WordLE<'p>,
  },
}

/// Macro inclusion type
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum IMacroInclusionL {
  CallMacro,
  DontCallMacro,
}

/// Function definition
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FunctionL<'p> {
  pub range: RangeL,
  pub header: FunctionHeaderL<'p>,
  pub body: Option<FunctionBodyL<'p>>,
}

/// Function body
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FunctionBodyL<'p> {
  pub body: CurliedLE<'p>,
}

/// Function header
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FunctionHeaderL<'p> {
  pub range: RangeL,
  pub name: WordLE<'p>,
  pub attributes: &'p [IAttributeL<'p>],
  pub maybe_user_specified_identifying_runes: Option<AngledLE<'p>>,
  pub params: ParendLE<'p>,
  /// Includes: where clause, return type, default region for the body
  /// Basically, everything up until the body's { or a ;
  pub trailing_details: ScrambleLE<'p>,
}

/// Node in the lexer tree
pub trait INodeLE {
  fn range(&self) -> RangeL;
}

/// A scramble of lexer nodes (no structure yet)
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ScrambleLE<'p> {
  pub range: RangeL,
  pub elements: &'p [&'p INodeLEEnum<'p>],
}
impl INodeLE for ScrambleLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Enum wrapper for INodeLE to allow storing in vectors
#[derive(Copy, Clone, Debug, PartialEq)]
pub enum INodeLEEnum<'p> {
  Parend(ParendLE<'p>),
  Curlied(CurliedLE<'p>),
  Squared(SquaredLE<'p>),
  Angled(AngledLE<'p>),
  Word(WordLE<'p>),
  Symbol(SymbolLE),
  String(StringLE<'p>),
  ParsedInteger(ParsedIntegerLE),
  ParsedDouble(ParsedDoubleLE),
  Scramble(ScrambleLE<'p>), // For recursive cases
}

impl INodeLE for INodeLEEnum<'_> {
  fn range(&self) -> RangeL {
    match self {
      INodeLEEnum::Parend(x) => x.range,
      INodeLEEnum::Curlied(x) => x.range,
      INodeLEEnum::Squared(x) => x.range,
      INodeLEEnum::Angled(x) => x.range,
      INodeLEEnum::Word(x) => x.range,
      INodeLEEnum::Symbol(x) => x.range(),
      INodeLEEnum::String(x) => x.range,
      INodeLEEnum::ParsedInteger(x) => x.range,
      INodeLEEnum::ParsedDouble(x) => x.range,
      INodeLEEnum::Scramble(x) => x.range,
    }
  }
}

/// Parenthesized expression
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ParendLE<'p> {
  pub range: RangeL,
  pub contents: ScrambleLE<'p>,
}
impl INodeLE for ParendLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Angled brackets (generics)
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AngledLE<'p> {
  pub range: RangeL,
  pub contents: ScrambleLE<'p>,
}
impl INodeLE for AngledLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Squared brackets (arrays)
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct SquaredLE<'p> {
  pub range: RangeL,
  pub contents: ScrambleLE<'p>,
}

impl INodeLE for SquaredLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Curly braces (blocks)
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CurliedLE<'p> {
  pub range: RangeL,
  pub contents: ScrambleLE<'p>,
}

impl INodeLE for CurliedLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Word/identifier
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct WordLE<'p> {
  pub range: RangeL,
  pub str: StrI<'p>,
}
impl INodeLE for WordLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Single character symbol
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct SymbolLE(pub RangeL, pub char);

impl SymbolLE {
  pub fn range(&self) -> RangeL {
    self.0
  }

  pub fn c(&self) -> char {
    self.1
  }
}

impl INodeLE for SymbolLE {
  fn range(&self) -> RangeL {
    self.0
  }
}

/// String literal
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct StringLE<'p> {
  pub range: RangeL,
  pub parts: &'p [StringPart<'p>],
}

impl INodeLE for StringLE<'_> {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Part of a string (literal or interpolated expression)
#[derive(Copy, Clone, Debug, PartialEq)]
pub enum StringPart<'p> {
  Literal { range: RangeL, s: StrI<'p> },
  Expr(ScrambleLE<'p>),
}

/// Parsed integer literal
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ParsedIntegerLE {
  pub range: RangeL,
  pub value: i64,
  pub bits: Option<i64>,
}

impl INodeLE for ParsedIntegerLE {
  fn range(&self) -> RangeL {
    self.range
  }
}

/// Parsed floating-point literal
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ParsedDoubleLE {
  pub range: RangeL,
  pub value: f64,
  pub bits: Option<i64>,
}

impl INodeLE for ParsedDoubleLE {
  fn range(&self) -> RangeL {
    self.range
  }
}

