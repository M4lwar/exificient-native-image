# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Does

This project wraps the [EXIficient](https://github.com/EXIficient/exificient) library (a Siemens EXI codec) and compiles it into a GraalVM Native Image shared library (`libexificient.so`) with a C-compatible ABI. The goal is to make schema-informed XML↔EXI conversion callable from C/C++ (or any language with a C FFI).

## Build Commands

**Java tests only (no GraalVM required):**
```sh
mvn test
```

**Run a single test:**
```sh
mvn test -Dtest=ExiProcessorTest#encodeXmlToExi
```

**Build native shared library (requires GraalVM JDK 21):**
```sh
# Phase 1: run tests under the tracing agent (records reflection/resource config)
mvn -Pnative test

# Phase 2: compile the shared library using the recorded configs
mvn -Pnative package -DskipTests
```
Output: `target/libexificient.so`, `target/libexificient.h`, `target/graal_isolate.h`

**Docker build (preferred — no local GraalVM needed):**
```sh
docker build -f Dockerfile.build -o target/ .
```

**Docker end-to-end integration test (builds .so, then compiles and runs test.cpp):**
```sh
docker build -f Dockerfile.test -t exi-test .
docker run --rm exi-test
```

**Compile and run the C test harness locally (after building the .so):**
```sh
g++ -o test_exi test.cpp -L./target -lexificient -I./target
LD_LIBRARY_PATH=./target ./test_exi [optional-xml-path]
```

## Architecture

### Two-Layer Design

**`ExiProcessor`** (`src/main/java/.../ExiProcessor.java`) — pure Java, no GraalVM dependencies. It wraps EXIficient's SAX-based API:
- Constructed with a schema path (`new ExiProcessor(schemaPath)`); a null/empty path falls back to the default `./schemas/UCI_MessageDefinitions_v2_5_0.xsd` (relative to CWD at runtime)
- `encode(ByteArrayInputStream xml)` → EXI bytes via `EXIResult` + `XMLReader`
- `decode(ByteArrayInputStream exi)` → XML bytes via `EXISource` + `TransformerFactory`

**`ExiLibrary`** (`src/main/java/.../ExiLibrary.java`) — the C ABI bridge. Uses GraalVM `@CEntryPoint` to expose:
- `exi_init(thread, schemaPath)` → initializes the singleton `ExiProcessor` with the XSD at `schemaPath` (NULL → default schema)
- `exi_encode(thread, input, inputLen, outputLen*)` → allocates and returns a `CCharPointer` buffer
- `exi_decode(thread, input, inputLen, outputLen*)` → allocates and returns a `CCharPointer` buffer
- `exi_free(thread, ptr)` → releases buffers allocated by encode/decode

Callers are responsible for calling `exi_free` on every buffer returned by `exi_encode` and `exi_decode`. Memory is managed via `UnmanagedMemory.malloc/free`.

### Native Image Tracing Agent Workflow

The `native` Maven profile uses a two-phase build because GraalVM Native Image performs closed-world analysis and cannot discover reflection/resources at compile time. The workaround:

1. **Phase 1** (`mvn -Pnative test`): runs the JUnit tests under `-agentlib:native-image-agent`, which records all reflective class loads, resource accesses (e.g. Xerces SAX parser, SLF4J providers, XSD schemas), and proxy usage. Output goes to `target/native/agent-output/main/`.

2. **Phase 2** (`mvn -Pnative package -DskipTests`): builds the shared library with `-H:ConfigurationFileDirectories` pointing at the agent output, ensuring all dynamically-loaded classes are included.

If you add new code paths that use reflection or load resources, re-run Phase 1 to regenerate the agent configs before rebuilding the native image.

### Schema Files

`schemas/UCI_MessageDefinitions_v2_5_0.xsd` is the primary schema for schema-informed EXI encoding. `UCI_SecurityMarkings_v2_5_0.xsd` is imported by it. Both must be present at the path `./schemas/` relative to the working directory at runtime — this applies to both the JVM (for tests) and the native library. To use a different schema, pass its path to `exi_init` (the C entry point) or to `new ExiProcessor(schemaPath)`; a null/empty path uses the default above.
