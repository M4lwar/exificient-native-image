import os

from conan import ConanFile
from conan.tools.cmake import CMake, cmake_layout
from conan.tools.build import can_run


class SmokeConan(ConanFile):
    """Functional smoke test: builds smoke.cpp against the exificient package and
    runs it on a known-good XML, asserting that compression actually works.

    Run with: conan test examples/1_ConanPackage exificient/<version>
    (the schema path is passed to exi_init; the .xsd must exist on disk).
    """

    settings = "os", "arch", "compiler", "build_type"
    # VirtualRunEnv puts the package's shared library on the loader path
    # (LD_LIBRARY_PATH / PATH) so the binary runs on every platform.
    generators = "CMakeToolchain", "CMakeDeps", "VirtualRunEnv"

    def requirements(self):
        self.requires(self.tested_reference_str)

    def layout(self):
        cmake_layout(self)

    def build(self):
        cmake = CMake(self)
        cmake.configure()
        cmake.build()

    def test(self):
        if can_run(self):
            exe = os.path.join(self.cpp.build.bindir, "smoke")
            sample = os.path.join(self.source_folder, "PositionReport.xml")
            # Schema lives at <repo>/schemas; pass its path straight to exi_init.
            schema = os.path.abspath(os.path.join(
                self.source_folder, "..", "..", "schemas",
                "UCI_MessageDefinitions_v2_5_0.xsd"))
            self.run(f'"{exe}" "{sample}" "{schema}"', env="conanrun")
