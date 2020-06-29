// expected output begin
// 77
// 10
// expected output end

interface MyOption<T> { }

struct MySome<T> {
  value: T;
}
MySome<T> implements MyOption<T>;

struct MyNone<T> { }
MyNone<T> implements MyOption<T>;


fn doSomething(opt: virtual MyOption<Int>) Int {
  -1
}
fn doSomething(s: virtual MySome<Int>) Int {
  s.value
}
fn doSomething(override n: MyNone<Int>) Int {
  10
}

fn main()Int {
	let x: MyOption<Int> = MySome<Int>(77);
	print(doSomething(x));
	let y: MyOption<Int> = MyNone<Int>();
	print(doSomething(y));
	0
}
