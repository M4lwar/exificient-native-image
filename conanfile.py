import os

from conan import ConanFile
from conan.errors import ConanInvalidConfiguration
from conan.tools.files import copy


class ExificientConan(ConanFile):
    name = "exificient"
    description = (
        "Schema-informed XML<->EXI codec: the Siemens EXIficient Java library "
        "compiled to a native C-ABI shared library via GraalVM Native Image."
    )
    homepage = "https://github.com/nolekev1214/exificient-native-image"
    topics = ("exi", "xml", "codec", "graalvm", "native-image")
    settings = "os", "arch"
    package_type = "shared-library"

    # This recipe packages a PREBUILT binary -- it never compiles from source, so
    # consumers never need a JDK or GraalVM. CI points EXIFICIENT_PREBUILT_DIR at
    # the directory holding libexificient.so, the generated headers, and schemas/,
    # then runs `conan export-pkg`.
    def validate(self):
        if self.settings.os != "Linux":
            raise ConanInvalidConfiguration(
                "Only Linux is currently packaged (x86_64, armv8). "
                "Windows/macOS artifacts are not yet produced."
            )

    def package(self):
        prebuilt = os.environ.get("EXIFICIENT_PREBUILT_DIR")
        if not prebuilt:
            raise ConanInvalidConfiguration(
                "EXIFICIENT_PREBUILT_DIR must point at the directory containing "
                "libexificient.so, the *.h headers, and schemas/."
            )
        copy(self, "*.so", prebuilt, os.path.join(self.package_folder, "lib"))
        copy(self, "*.h", prebuilt, os.path.join(self.package_folder, "include"))
        copy(self, "schemas/*", prebuilt,
             os.path.join(self.package_folder, "res", "schemas"))

    def package_info(self):
        # libexificient.so -> link name "exificient"
        self.cpp_info.libs = ["exificient"]
        self.cpp_info.includedirs = ["include"]
        self.cpp_info.libdirs = ["lib"]
        self.cpp_info.resdirs = ["res"]
        # The library currently loads its XSDs from ./schemas relative to the
        # process CWD. We expose the packaged copy here so a consumer can locate
        # them; until the library reads this var, the consumer must still ensure
        # the schemas are reachable at ./schemas in their working directory.
        self.runenv_info.define_path(
            "EXIFICIENT_SCHEMAS",
            os.path.join(self.package_folder, "res", "schemas"),
        )
