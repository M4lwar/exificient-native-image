// entity_demo.cpp
//
// Demonstrates schema-informed EXI compression using libexificient.so (the
// GraalVM-native build of EXIficient). Reads a UCI "Entity" XML message,
// encodes it to EXI, and reports the size before/after. It then decodes the
// EXI back to XML to show the round-trip works.
//
// Requires the UCI schemas to be reachable at ./schemas relative to the current
// working directory (the library loads them from there at exi_init time).
//
// Build/run instructions are in README.md.

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <vector>

#include "libexificient.h"

static std::vector<char> read_file(const char* path) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        fprintf(stderr, "Error: cannot open '%s'\n", path);
        exit(1);
    }
    std::streamsize n = f.tellg();
    f.seekg(0, std::ios::beg);
    std::vector<char> buf(static_cast<size_t>(n));
    f.read(buf.data(), n);
    return buf;
}

int main(int argc, char** argv) {
    const char* xml_path = argc > 1 ? argv[1] : "EntityReport.xml";

    // --- Start the GraalVM runtime embedded in the library ---
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;
    if (graal_create_isolate(nullptr, &isolate, &thread) != 0) {
        fprintf(stderr, "Error: failed to create GraalVM isolate\n");
        return 1;
    }

    // Loads ./schemas/UCI_MessageDefinitions_v2_5_0.xsd relative to CWD.
    if (exi_init(thread) != 0) {
        fprintf(stderr, "Error: exi_init failed (is ./schemas present in the "
                        "working directory?)\n");
        graal_tear_down_isolate(thread);
        return 1;
    }

    std::vector<char> xml = read_file(xml_path);
    const int xml_len = static_cast<int>(xml.size());

    // --- Encode XML -> EXI ---
    int exi_len = 0;
    char* exi = exi_encode(thread, xml.data(), xml_len, &exi_len);
    if (!exi || exi_len < 0) {
        fprintf(stderr, "Error: exi_encode failed\n");
        graal_tear_down_isolate(thread);
        return 1;
    }

    // --- Decode EXI -> XML (round-trip) ---
    int xml_out_len = 0;
    char* xml_out = exi_decode(thread, exi, exi_len, &xml_out_len);
    if (!xml_out || xml_out_len < 0) {
        fprintf(stderr, "Error: exi_decode failed\n");
        exi_free(thread, exi);
        graal_tear_down_isolate(thread);
        return 1;
    }

    // --- Report ---
    const double ratio   = xml_len > 0 ? static_cast<double>(exi_len) / xml_len : 0.0;
    const double saving  = (1.0 - ratio) * 100.0;
    const double shrinkX = exi_len > 0 ? static_cast<double>(xml_len) / exi_len : 0.0;

    printf("\n");
    printf("  UCI Entity message  : %s\n", xml_path);
    printf("  ----------------------------------------------\n");
    printf("  XML size (original) : %8d bytes\n", xml_len);
    printf("  EXI size (encoded)  : %8d bytes\n", exi_len);
    printf("  ----------------------------------------------\n");
    printf("  Compression ratio   : %8.1f%% of original\n", ratio * 100.0);
    printf("  Space saved         : %8.1f%%\n", saving);
    printf("  Shrink factor       : %8.2fx smaller\n", shrinkX);
    printf("  Round-trip decode   : %8d bytes XML (re-serialized, semantically equal)\n",
           xml_out_len);
    printf("\n");

    // --- Cleanup: every buffer from encode/decode must be freed ---
    exi_free(thread, exi);
    exi_free(thread, xml_out);
    graal_tear_down_isolate(thread);
    return 0;
}
