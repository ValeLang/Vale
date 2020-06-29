interface MyOption<T> { }

struct MySome<T> {
  value: T;
}
MySome<T> implements MyOption<T>;

struct MyNone<T> { }
MyNone<T> implements MyOption<T>;


struct MyList<T> {
  value: T;
  next: MyOption<MyList<T>>;
}

abstract fn forEach<F, T>(opt: virtual MyOption<MyList<T>>, func: F)Int;
fn forEach<F, T>(override opt: MyNone<MyList<T>>, func: F)Int { 1 }
fn forEach<F, T>(override opt: MySome<MyList<T>>, func: F)Int {
   forEach<F, T>(opt.value, func);
   1
}
fn forEach<F, T>(list: MyList<T>, func: F)Int {
  func(list.value);
  forEach<F, T>(list.next, func);
  1
}

fn main()Int {
  list = MyList<Int>(10, MySome<MyList<Int>>(MyList<Int>(25, MySome<MyList<Int>>(MyList<Int>(30, MyNone<MyList<Int>>())))));
  forEach(list, print);
  0
}
