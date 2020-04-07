version 1.0

task foo {
  input {
    # This is a comment that should be reformatted
    String s
    Int i
    String y = "foo"
  }

  String x = "${s}.txt"
  Int z = (i + i) + i
  Int a = if (i > 1) then 2 else 3

  command <<<
    echo ~{x}
    echo ~{i * i}
    echo ~{if true then "a" else "b"}
  >>>

  output {
    String sout = x
  }

  runtime {
    docker: "debian:stretch-slim"
  }
}