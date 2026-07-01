# exi-demo

A C++ example that consumes the prebuilt `libexificient` **Conan** package to
schema-informed **EXI-compress** UCI XML messages. Point it at a single `.xml`
file or a **directory** of captured messages (searched recursively) — it reports
the compression ratio for each — and with `--iterations` it benchmarks the codec,
timing the one-time `exi_init` separately from the per-message encode/decode.

`EntityReport.xml` is a representative, verbose UCI `Entity` message shaped against
`schemas/UCI_MessageDefinitions_v2_5_0.xsd`.

## 1. Get the Conan package

Download the package for your architecture from the
[`v0.3.0-rc` release](../../releases) (or a `build` workflow run):

- `conan-exificient-0.3.0-rc-linux-x86_64.tgz`
- `conan-exificient-0.3.0-rc-linux-arm64.tgz`
- `conan-exificient-0.3.0-rc-windows-x86_64.tgz`

It's a prebuilt binary package — no GraalVM/JDK needed to consume it.

## 2. Restore it into your local Conan cache

```sh
conan cache restore conan-exificient-0.3.0-rc-linux-<arch>.tgz
```

## 3. Build the demo

`conanfile.py` + `CMakeLists.txt` drive the build. `conan install` writes the
generated CMake files (including the toolchain) under **`build/Release/generators/`**,
so CMake must be pointed at the toolchain **explicitly**:

```sh
conan install .
cmake -S . -B build/Release \
      -DCMAKE_TOOLCHAIN_FILE="$(pwd)/build/Release/generators/conan_toolchain.cmake" \
      -DCMAKE_BUILD_TYPE=Release
cmake --build build/Release
```

The binary is written to **`build/Release/exi-demo`**.

> Shortcut: `conan build .` runs those same CMake steps for you (Conan supplies the
> toolchain automatically), if you'd rather not spell them out.

## 4. Provide the schema, set the library path, and run

The package ships **library + headers only — no XSDs**. Provide the schema on disk,
and put the package's shared library on the loader path via the generated `conanrun`
script (this is why a bare `./exi-demo` otherwise fails to find `libexificient.so`):

```sh
cp -r ../../schemas ./schemas                    # the schema exi_init will load
source build/Release/generators/conanrun.sh      # adds the package's lib dir to LD_LIBRARY_PATH

./build/Release/exi-demo -h                       # usage
./build/Release/exi-demo                          # default: EntityReport.xml
./build/Release/exi-demo samples/                 # survey a directory (recursive *.xml)
./build/Release/exi-demo -n 1000 EntityReport.xml # benchmark encode/decode timing
```

## CLI

```
exi-demo [options] [path]

  path                   An .xml file, or a directory searched recursively for
                         *.xml files (default: EntityReport.xml)

  -s, --schema <path>    XSD schema passed to exi_init
                         (default: ./schemas/UCI_MessageDefinitions_v2_5_0.xsd)
  -n, --iterations <N>   Encode+decode each message N times; report min/avg/max
                         timing (default: 1)
  -h, --help             Show this help and exit
```

## Example: compression survey over a directory

`samples/` holds one message of each of six UCI types (EntityMT, PositionReportMT,
NavigationReportMT, TaskMT, TaskCommandMT, PositionReportDetailedMT):

```
  Schema   : ./schemas/UCI_MessageDefinitions_v2_5_0.xsd
  exi_init : 8611.040 ms  (one-time: schema load + grammar build)

  message                                        XML       EXI    saved
  ---------------------------------------------------------------------
  samples/entity.xml                            3466       259    92.5%
  samples/navigation-report.xml                 1168        81    93.1%
  samples/position-report-detailed.xml          2383       129    94.6%
  samples/position-report.xml                   1798       139    92.3%
  samples/task-command.xml                      1460       121    91.7%
  samples/task.xml                              1403        81    94.2%
  ---------------------------------------------------------------------
  6 message(s)                                 11678       810    93.1%
```

Run it with `./build/Release/exi-demo samples/`.

## Example: timing (`-n 1000`)

```
  message                                        XML       EXI    saved  enc avg ms dec avg ms
  --------------------------------------------------------------------------------------------
  EntityReport.xml                              3466       259    92.5%       0.218      0.102
```

`exi_init` takes ~9–10 s (building EXI grammars from the 8 MB UCI schema), while each
`exi_encode`/`exi_decode` is well under a millisecond — the cost is the one-time init;
per-message work is negligible. **Initialize once and reuse the processor for many
messages.**

## Notes

- Schemas are **not** packaged; you supply the `.xsd` at runtime and its path is
  passed to `exi_init`, so you can encode/decode against a different schema without
  rebuilding. The schema file (and anything it imports) must exist on disk.
- Decoded XML is smaller than the original because EXIficient re-serializes without
  the original comments/indentation — semantically identical, just reformatted.
- Every buffer returned by `exi_encode`/`exi_decode` must be released with `exi_free`
  (the demo does this each iteration).
