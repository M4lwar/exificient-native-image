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
    license = "MIT"
    settings = "os", "arch"
    package_type = "shared-library"

    # The .so embeds the MIT-licensed EXIficient codec, so its notice must ship
    # with the package (THIRD_PARTY_NOTICES.txt). LICENSE is this wrapper's own.
    exports = "THIRD_PARTY_NOTICES.txt", "LICENSE"

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
        # Package the link-time deliverable only: the shared library and headers.
        # The XSDs are deliberately NOT packaged -- the library loads schemas from
        # ./schemas at runtime so a consumer can supply their own (see README), so
        # bundling a specific schema here would couple the package to one schema
        # version and bloat it.
        copy(self, "*.so", prebuilt, os.path.join(self.package_folder, "lib"))
        copy(self, "*.h", prebuilt, os.path.join(self.package_folder, "include"))
        # Ship license notices: the .so embeds MIT-licensed EXIficient.
        licenses = os.path.join(self.package_folder, "licenses")
        copy(self, "THIRD_PARTY_NOTICES.txt", self.recipe_folder, licenses)
        copy(self, "LICENSE", self.recipe_folder, licenses)

    def package_info(self):
        # libexificient.so -> link name "exificient"
        self.cpp_info.libs = ["exificient"]
        self.cpp_info.includedirs = ["include"]
        self.cpp_info.libdirs = ["lib"]
        # No schemas are packaged: at runtime the library reads ./schemas relative
        # to the process CWD, which the consumer supplies (and can swap for their
        # own XSD). See examples/entity_demo/README.md.
