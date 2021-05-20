version 1.0

task add {
  input {
    Int a
    Int b
  }

  command <<<
        echo $((~{a} + ~{b}))
  >>>

  output {
    Int result = read_int(stdout())
  }

  meta {
    developer_notes: "Developer notes defined in WDL"
  }
}