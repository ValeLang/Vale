
#ifndef VALE_TYPES_H_
#define VALE_TYPES_H_

#include <string>
#include <vector>

// Defined elsewhere
class Name;
class CodeLocation;

// Defined in this file
class Reference;
class Referend;
class Int;
class Bool;
class Str;
class Void;
class Float;
class Never;
class InterfaceRef;
class StructReferend;
class RawArrayT;
class KnownSizeArrayT;
class UnknownSizeArrayT;

enum class Ownership {
    OWN,
    BORROW,
    SHARE
};

enum class Permission {
    READONLY,
    READWRITE,
    EXCLUSIVE_READWRITE
};

enum class Location {
    INLINE,
    YONDER
};

enum class Mutability {
    IMMUTABLE,
    MUTABLE
};

enum class Variability {
    FINAL,
    VARYING
};

class Reference {
public:
    Ownership ownership;
    Referend* referend;

    Reference(
        Ownership ownership_,
    Referend* referend_) : ownership(ownership_), referend(referend_) {}
};

class Referend {
public:
    virtual ~Referend() {}
};

class Int : public Referend {
public:
};

class Bool : public Referend {
public:
};

class Str : public Referend {
public:
};

class Void : public Referend {
public:
};

class Float : public Referend {
public:
};

class Never : public Referend {
public:
};

class InterfaceReferend : public Referend {
public:
    Name* fullName;
};

class StructReferend : public Referend {
public:
    Name* fullName;

    StructReferend(Name* fullName_) :
        fullName(fullName_) {}
};

class RawArrayT {
    Reference* elementType;
};

class KnownSizeArrayT : Referend {
public:
    int size;
    RawArrayT* rawArray;
};

class UnknownSizeArrayT : public Referend {
    RawArrayT* rawArray;
};


class IContainer {
public:
    std::string humanName;
    CodeLocation* location;
};

#endif