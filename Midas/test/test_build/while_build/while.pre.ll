; ModuleID = 'while'
source_filename = "while"

define i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = alloca i64
  store i64 %0, i64* %2
  %3 = alloca i64
  store i64 %1, i64* %3
  %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22a\22)" = load i64, i64* %2
  %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22b\22)" = load i64, i64* %3
  %4 = call i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64 %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22a\22)", i64 %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22b\22)")
  %5 = alloca i64
  store i64 %4, i64* %5
  %6 = load i64, i64* %3
  %7 = load i64, i64* %2
  %8 = load i64, i64* %5
  ret i64 %8
}

define i1 @"F(\22__lessThanInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = icmp slt i64 %0, %1
  ret i1 %2
}

define i1 @"F(\22<\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = alloca i64
  store i64 %0, i64* %2
  %3 = alloca i64
  store i64 %1, i64* %3
  %"F(\22<\22,[],[R(*,i),R(*,i)]):CodeVarName(\22left\22)" = load i64, i64* %2
  %"F(\22<\22,[],[R(*,i),R(*,i)]):CodeVarName(\22right\22)" = load i64, i64* %3
  %4 = call i1 @"F(\22__lessThanInt\22,[],[R(*,i),R(*,i)])"(i64 %"F(\22<\22,[],[R(*,i),R(*,i)]):CodeVarName(\22left\22)", i64 %"F(\22<\22,[],[R(*,i),R(*,i)]):CodeVarName(\22right\22)")
  %5 = alloca i1
  store i1 %4, i1* %5
  %6 = load i64, i64* %3
  %7 = load i64, i64* %2
  %8 = load i1, i1* %5
  ret i1 %8
}

define i64 @"F(\22main\22)"() {
block1:
  %0 = alloca i64
  store i64 1, i64* %0
  %1 = load i64, i64* %0
  %2 = alloca i64
  store i64 %1, i64* %2
  br label %block2

block2:                                           ; preds = %block6, %block1
  %"F(\22main\22):CodeVarName(\22a\22)" = load i64, i64* %2
  %3 = alloca i64
  store i64 42, i64* %3
  %4 = load i64, i64* %3
  %5 = call i1 @"F(\22<\22,[],[R(*,i),R(*,i)])"(i64 %"F(\22main\22):CodeVarName(\22a\22)", i64 %4)
  br i1 %5, label %block4, label %block5

block3:                                           ; preds = %block6
  %"F(\22main\22):CodeVarName(\22a\22)3" = load i64, i64* %2
  %6 = alloca i64
  store i64 %"F(\22main\22):CodeVarName(\22a\22)3", i64* %6
  %7 = load i64, i64* %2
  %8 = load i64, i64* %6
  ret i64 %8

block4:                                           ; preds = %block2
  %"F(\22main\22):CodeVarName(\22a\22)1" = load i64, i64* %2
  %"F(\22main\22):CodeVarName(\22a\22)2" = load i64, i64* %2
  %9 = alloca i64
  store i64 1, i64* %9
  %10 = load i64, i64* %9
  %11 = call i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64 %"F(\22main\22):CodeVarName(\22a\22)2", i64 %10)
  store i64 %11, i64* %2
  %12 = alloca i1
  store i1 true, i1* %12
  %13 = load i1, i1* %12
  br label %block6

block5:                                           ; preds = %block2
  %14 = alloca i1
  store i1 false, i1* %14
  %15 = load i1, i1* %14
  br label %block6

block6:                                           ; preds = %block5, %block4
  %16 = phi i1 [ %13, %block4 ], [ %15, %block5 ]
  br i1 %16, label %block2, label %block3
}

define i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add = add i64 %0, %1
  ret i64 %add
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  %valeMainCall = call i64 @"F(\22main\22)"()
  ret i64 %valeMainCall
}
