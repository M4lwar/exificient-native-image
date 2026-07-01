#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <vector>

#include "libexificient.h"

static std::vector<char> read_file(const char* path) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        fprintf(stderr, "Error: could not open file: %s\n", path);
        exit(1);
    }
    std::streamsize size = f.tellg();
    f.seekg(0, std::ios::beg);
    std::vector<char> buf(size);
    f.read(buf.data(), size);
    return buf;
}

int main(int argc, char** argv) {
    const char* xml_path = argc > 1 ? argv[1] : "src/test/resources/PositionReport.xml";
    const char* schema_path = argc > 2 ? argv[2] : "schemas/UCI_MessageDefinitions_v2_5_0.xsd";

    // --- Start GraalVM runtime ---
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;
    if (graal_create_isolate(nullptr, &isolate, &thread) != 0) {
        fprintf(stderr, "Error: failed to create GraalVM isolate\n");
        return 1;
    }

    if (exi_init(thread, schema_path) != 0) {
        fprintf(stderr, "Error: exi_init failed\n");
        graal_tear_down_isolate(thread);
        return 1;
    }

    // --- Read input XML ---
    std::vector<char> xml = read_file(xml_path);

    printf("=== Input XML (%zu bytes) ===\n", xml.size());
    printf("%.*s\n", (int)xml.size(), xml.data());

    // --- Encode XML -> EXI ---
    int exi_len = 0;
    char* exi_bytes = exi_encode(thread, xml.data(), (int)xml.size(), &exi_len);
    if (!exi_bytes || exi_len < 0) {
        fprintf(stderr, "Error: exi_encode failed\n");
        graal_tear_down_isolate(thread);
        return 1;
    }
    printf("=== Encoded EXI (%d bytes) ===\n", exi_len);

    // --- Decode EXI -> XML ---
    int xml_out_len = 0;
    char* xml_out = exi_decode(thread, exi_bytes, exi_len, &xml_out_len);
    if (!xml_out || xml_out_len < 0) {
        fprintf(stderr, "Error: exi_decode failed\n");
        exi_free(thread, exi_bytes);
        graal_tear_down_isolate(thread);
        return 1;
    }

    printf("=== Output XML (%d bytes) ===\n", xml_out_len);
    printf("%.*s\n", xml_out_len, xml_out);

    // --- Cleanup ---
    exi_free(thread, exi_bytes);
    exi_free(thread, xml_out);
    graal_tear_down_isolate(thread);

    return 0;
}
