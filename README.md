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
docker build -f /examples/0_ManualLinkage/Dockerfile -t exi-test .
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
`.so`), so pointing at a different schema works without rebuilding. XSD parsing
runs through Xerces' fixed, compiled code path — schema *content* does not load
construct-specific classes — so this holds across schemas regardless of the
constructs they use. Verified with both an unrelated schema and a construct-heavy
one (patterns, unions, lists, abstract types, nillable elements, `xs:ID`, and
datatypes from `dateTime` to `base64Binary`), all loading through the shipped
`.so` with no rebuild.

The only thing to be aware of is GraalVM's closed-world model: just the
reflection/resource paths exercised during the tracing-agent phase
(`mvn -Pnative test`) are available at runtime. The XSD parser machinery is
schema-independent and is covered, so standard, valid schemas load. The narrow
residual risk is some unrelated code branch the build never exercised (e.g. an
error-reporting path) — not XSD constructs; if one ever surfaces, add that
scenario to the Phase-1 run and rebuild.

## Consuming via Conan

The `build` workflow publishes a prebuilt **Conan 2** package per architecture
(library + headers — **schemas are not packaged**; you supply your own, see
above). There is no Conan remote: the package is distributed as a CI artifact and
imported into your local cache.

**1. Restore the package into your local Conan cache.** Download the artifact for
your platform from a successful `build` run — `conan-exificient-linux-<arch>`
(x86_64 / arm64) or `conan-exificient-windows-x86_64` — then:

```sh
conan cache restore conan-exificient-<arch>.tgz
conan list "exificient/*"        # shows the version you just restored
```

The version tracks the build: `0.0.0-ci` for branch/PR builds, or the tag (e.g.
`0.1.0`) for tagged releases.

**2. Require it.** In your consumer `conanfile.txt` (use the version from step 1):

```ini
[requires]
exificient/0.0.0-ci

[generators]
CMakeDeps
CMakeToolchain
VirtualRunEnv
```

**3. Link it.** In `CMakeLists.txt`:

```cmake
find_package(exificient CONFIG REQUIRED)
target_link_libraries(your_app PRIVATE exificient::exificient)
```

**4. Build.** `exificient` is a prebuilt binary package, so `conan install` simply
uses the binary you restored in step 1. Don't pass `--build=missing` — the package
cannot be built from source, and that flag would make Conan try (and fail) instead
of reporting a clean "missing binary" if the restore was skipped or the
platform/arch doesn't match.

```sh
conan install . --output-folder=build
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$PWD/build/conan_toolchain.cmake" -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

**5. Run.** `libexificient` is a shared library, so it must be on the loader path
at runtime, and the schemas must be reachable (see *Runtime Requirement* above).
The generated `VirtualRunEnv` script handles the loader path:

```sh
source build/conanrun.sh                 # puts the package's lib dir on LD_LIBRARY_PATH
cp -r /path/to/your/schemas ./schemas    # or: export EXIFICIENT_SCHEMA=/path/to/your.xsd
./build/your_app
```

The package is keyed on `os`+`arch` only (the C ABI is compiler-independent), so
one binary works across compilers and build types. A complete worked example —
using a `conanfile.py` consumer recipe — lives on the
`demo/entity-exi-compression` branch under `examples/entity_demo`.

## License

This project is released under the [MIT License](LICENSE). It compiles
[EXIficient](https://github.com/EXIficient/exificient) (also MIT, © Siemens AG)
into the shared library; that notice is reproduced in
[`THIRD_PARTY_NOTICES.txt`](THIRD_PARTY_NOTICES.txt) and bundled into the Conan
package under `licenses/`, so it travels with any redistribution of the binary.
