// Code formatting utilities

pub enum IClass {
    W,
    Ab,
    Ext,
    Fn,
    FnName,
    FnTplSep,
    Rune,
}

pub enum IElement {
    Span(Span),
    Text(Text),
}

pub struct Span {
    pub classs: IClass,
    pub elements: Vec<IElement>,
}

pub struct Text {
    pub string: String,
}

