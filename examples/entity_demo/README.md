# entity_demo

A minimal C++ program that uses the prebuilt `libexificient` shared library to
**schema-informed EXI compress** a representative UCI `Entity` message and report
the size before and after. It then decodes the EXI back to XML to show the
round-trip works. The library is consumed **via Conan**, using the package
produced by the CI pipeline.

`EntityReport.xml` is a representative `EntityMT` message (root `<uci:Entity>`)
shaped against `schemas/UCI_MessageDefinitions_v2_5_0.xsd`. It carries the required
`MessageType` base fields (SecurityInformation, MessageHeader) and the required
`EntityMDT` payload (EntityID, CreationTimestamp, Source, EntityStatus, Identity),
plus a realistic set of optional fields to make it verbose: OperationalStatus,
Mobility, a detailed Identity (Standard / Environment / Platform with confidences),
full Kinematics (Position, Velocity, Orientation), Strength, and Endurance.

## 1. Get the Conan package from CI

The `build` workflow's `conan-package` job archives the package with
`conan cache save` and uploads it as an artifact. Open a successful run and
download the one for your architecture:

- `conan-exificient-linux-x86_64` -> `conan-exificient-x86_64.tgz`
- `conan-exificient-linux-arm64`  -> `conan-exificient-arm64.tgz`

No GraalVM/JDK is needed to consume it — it's a prebuilt binary package.

## 2. Restore it into your local Conan cache

This is the "local Conan repo": the package goes straight into your cache, no
remote server required.

```sh
conan cache restore conan-exificient-<arch>.tgz
```

## 3. Build the demo against it

```sh
conan install . --output-folder=build --build=missing
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$PWD/build/conan_toolchain.cmake" -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

`CMakeLists.txt` resolves the library with `find_package(exificient)` and links
`exificient::exificient`; Conan generated the package config from the recipe's
`cpp_info`.

## 4. Provide schemas and run

The package ships **only the library and headers** — not the XSDs. At runtime the
library loads its schema from `./schemas` relative to the working directory, which
you supply (and can swap for your own XSD). Copy the schemas shipped in this repo
(or your own) next to where you run:

```sh
cp -r ../../schemas ./schemas
source build/conanrun.sh          # puts the package's lib dir on LD_LIBRARY_PATH
./build/entity_demo EntityReport.xml
```

Pass a different XML path as the first argument to compress your own message
(using whatever XSD you placed in `./schemas`).

## Expected output

```
  UCI Entity message  : EntityReport.xml
  ----------------------------------------------
  XML size (original) :     3466 bytes
  EXI size (encoded)  :      259 bytes
  ----------------------------------------------
  Compression ratio   :      7.5% of original
  Space saved         :     92.5%
  Shrink factor       :    13.38x smaller
  Round-trip decode   :     2784 bytes XML (re-serialized, semantically equal)
```

> The decoded XML is smaller than the original because EXIficient re-serializes
> the document without the original comments and indentation — the content is
> semantically identical, just reformatted.

## Notes

- The Conan package contains the library + headers only. **Schemas are not
  packaged** — they are runtime data you provide at `./schemas`, by design, so you
  can encode/decode against a different message schema without rebuilding.
- Every buffer returned by `exi_encode` / `exi_decode` must be released with
  `exi_free` (the demo does this).
