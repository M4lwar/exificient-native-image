# exificient-native-image

A GraalVM Native Image shared library that exposes schema-informed XML↔EXI conversion via a C-compatible ABI. Built on top of [EXIficient](https://github.com/EXIficient/exificient) (Siemens EXI codec).

## Prerequisites

- Java 21
- Maven
- **GraalVM JDK 21** — required only for native builds (Docker removes this requirement)
- Docker — for the containerized build and integration test

## Building

### Docker (recommended)

Produces `target/libexificient.so` and the corresponding headers without requiring a local GraalVM installation:

```sh
docker build -f Dockerfile.build -o target/ .
```

### Local (requires GraalVM JDK 21)

The native build is a two-phase process. Phase 1 runs the JUnit tests under the native-image tracing agent to capture all runtime reflection and resource accesses. Phase 2 compiles the shared library using those captured configs.

```sh
# Phase 1: record reflection/resource config
mvn -Pnative test

# Phase 2: build the shared library
mvn -Pnative package -DskipTests
```

Output: `target/libexificient.so`, `target/libexificient.h`, `target/graal_isolate.h`

## Testing

**JUnit tests (JVM, no GraalVM required):**
```sh
mvn test
```

**End-to-end integration test (builds `.so`, compiles and runs `test.cpp`):**
```sh
docker build -f Dockerfile.test -t exi-test .
docker run --rm exi-test
```

**C test harness locally (after building the `.so`):**
```sh
g++ -o test_exi test.cpp -L./target -lexificient -I./target
LD_LIBRARY_PATH=./target ./test_exi [optional-xml-path]
```

## C API

```c
#include "libexificient.h"

// 1. Create GraalVM isolate
graal_isolate_t* isolate = NULL;
graal_isolatethread_t* thread = NULL;
graal_create_isolate(NULL, &isolate, &thread);

// 2. Initialize the EXI processor (loads the XSD schema)
exi_init(thread);

// 3. Encode XML → EXI
int exi_len = 0;
char* exi_bytes = exi_encode(thread, xml_buf, xml_len, &exi_len);

// 4. Decode EXI → XML
int xml_len = 0;
char* xml_bytes = exi_decode(thread, exi_bytes, exi_len, &xml_len);

// 5. Free buffers (required — library allocates unmanaged memory)
exi_free(thread, exi_bytes);
exi_free(thread, xml_bytes);

// 6. Tear down
graal_tear_down_isolate(thread);
```

`exi_encode` and `exi_decode` return `NULL` and write `-1` to `outputLen` on failure.

## Runtime Requirement: schemas

`exi_init` loads an XSD schema from the filesystem at the time it is called. By
default it reads `./schemas/UCI_MessageDefinitions_v2_5_0.xsd` relative to the
**current working directory**, so a `schemas/` directory must be present wherever
the library runs.

### Using a custom schema

Set the `EXIFICIENT_SCHEMA` environment variable (before `exi_init`) to the path
of your own `.xsd` to encode/decode against a different schema — no rebuild
required:

```sh
export EXIFICIENT_SCHEMA=/etc/myapp/MySchema.xsd
./your_app
```

If the variable is unset or empty, the default path above is used. The schema
informs the EXI grammar on both ends, so the encoder and decoder must use the
same schema.

The schema is parsed from disk at runtime (it is **not** compiled into the
`.so`), so pointing at a different schema works without rebuilding — this is
verified with an unrelated schema, not just the bundled one. The one caveat is
GraalVM's closed-world model: the native build only registers the
reflection/resource config exercised while parsing schemas during the
tracing-agent phase. That covers the Xerces parser machinery and common XSD
datatypes, so typical schemas load fine; a schema using an exotic XSD construct
never exercised at build time could fail to load and require re-running Phase 1
(`mvn -Pnative test`) before rebuilding. To guarantee coverage for a specific
schema, parse it during that phase.

## Consuming via Conan

CI publishes a prebuilt Conan 2 package (library + headers; **schemas are not
packaged** — supply your own per the section above). Download the
`conan-exificient-linux-<arch>` artifact from a `build` workflow run, then:

```sh
# 1. Restore the CI-built package into your local Conan cache
conan cache restore conan-exificient-<arch>.tgz

# 2. Require it from your conanfile
#    [requires]
#    exificient/<version>
#
#    [generators]
#    CMakeDeps
#    CMakeToolchain

# 3. In CMakeLists.txt
#    find_package(exificient CONFIG REQUIRED)
#    target_link_libraries(your_app PRIVATE exificient::exificient)
```

The package is keyed on `os`+`arch` only (C ABI), so one binary works across
compilers. A complete worked example lives on the `demo/entity-exi-compression`
branch (`examples/entity_demo`).
