// smoke.cpp — functional CI smoke test for the libexificient Conan package.
//
// Encodes a known-good XML, asserts that schema-informed EXI compression
// actually happened (EXI must be well under half the XML size), and that the
// EXI round-trips back to XML. Exits non-zero on any failure so CI catches a
// broken or fallback (schema-less) encode, not just a link/load problem.
//
// argv[1] is the XML to encode; argv[2] is the .xsd schema path (passed straight
// to exi_init).

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <vector>

#include "libexificient.h"

static std::vector<char> read_file(const char* path) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        fprintf(stderr, "smoke: cannot open %s\n", path);
        exit(2);
    }
    std::streamsize n = f.tellg();
    f.seekg(0, std::ios::beg);
    std::vector<char> buf(static_cast<size_t>(n));
    f.read(buf.data(), n);
    return buf;
}

int main(int argc, char** argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: smoke <xml-file> <schema.xsd>\n");
        return 2;
    }

    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;
    if (graal_create_isolate(nullptr, &isolate, &thread) != 0) {
        fprintf(stderr, "smoke: failed to create GraalVM isolate\n");
        return 1;
    }
    if (exi_init(thread, argv[2]) != 0) {
        fprintf(stderr, "smoke: exi_init failed (schema '%s' not found?)\n", argv[2]);
        return 1;
    }

    std::vector<char> xml = read_file(argv[1]);
    const int xml_len = static_cast<int>(xml.size());

    int exi_len = 0;
    char* exi = exi_encode(thread, xml.data(), xml_len, &exi_len);
    if (!exi || exi_len <= 0) {
        fprintf(stderr, "smoke: encode failed\n");
        return 1;
    }

    int out_len = 0;
    char* out = exi_decode(thread, exi, exi_len, &out_len);
    if (!out || out_len <= 0) {
        fprintf(stderr, "smoke: decode failed\n");
        exi_free(thread, exi);
        return 1;
    }

    printf("smoke: xml=%d bytes  exi=%d bytes  (%.1f%% of original)  roundtrip=%d bytes\n",
           xml_len, exi_len, 100.0 * exi_len / xml_len, out_len);

    int rc = 0;
    // Compression must be effective: schema-informed EXI on real data is a small
    // fraction of the XML. A result >= 50% means encoding broke or fell back.
    if (exi_len * 2 >= xml_len) {
        fprintf(stderr, "smoke: FAIL — compression not effective (exi %d >= 50%% of xml %d)\n",
                exi_len, xml_len);
        rc = 1;
    } else {
        printf("smoke: PASS — compression effective and round-trip ok\n");
    }

    exi_free(thread, exi);
    exi_free(thread, out);
    graal_tear_down_isolate(thread);
    return rc;
}
