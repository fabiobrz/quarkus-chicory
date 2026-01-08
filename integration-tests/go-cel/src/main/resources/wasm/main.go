package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/google/cel-go/cel"
	"github.com/google/cel-go/checker/decls"
)

// evalPolicy evaluates a CEL expression
// Returns: 1 = policy allows, 0 = policy denies, negative = error
func evalPolicy(policy string, inputJSON []byte) int32 {
	// Step 1: Parse the JSON input
	var input map[string]any
	if err := json.Unmarshal(inputJSON, &input); err != nil {
		return -1 // JSON parse error
	}

	// Step 2: Create CEL environment
	env, err := cel.NewEnv(
		cel.Declarations(
			decls.NewVar("object", decls.NewMapType(decls.String, decls.Dyn)),
		),
	)
	if err != nil {
		return -2 // Environment creation error
	}

	// Step 3: Compile the CEL expression
	ast, iss := env.Compile(policy)
	if iss.Err() != nil {
		return -3 // Compilation error
	}

	// Step 4: Create program
	prg, err := env.Program(ast)
	if err != nil {
		return -4 // Program creation error
	}

	// Step 5: Evaluate the expression
	out, _, err := prg.Eval(map[string]any{
		"object": input,
	})
	if err != nil {
		return -5 // Runtime error
	}

	// Step 6: Check if result is a boolean true
	if b, ok := out.Value().(bool); ok && b {
		return 1 // Policy allows
	}
	return 0 // Policy denies
}

func main() {
	// Expect: program-name policy-string input-json-string
	if len(os.Args) != 3 {
		fmt.Fprintf(os.Stderr, "ERROR: Expected 2 arguments: <policy> <input-json>\n")
		os.Exit(2)
	}

	policy := os.Args[1]
	inputJSON := []byte(os.Args[2])

	result := evalPolicy(policy, inputJSON)

	// Print result as a simple integer to stdout
	fmt.Println(result)

	// Exit with 0 (success) regardless of policy result
	// The policy result is in the stdout output
	os.Exit(0)
}
