#include <cstdio>

#include "libexificient.h"

// Minimal consumer: prove the package links against libexificient.so and that
// the GraalVM runtime initializes. We intentionally do NOT exercise encode/decode
// here -- exi_init needs the XSDs at ./schemas relative to CWD, which is a runtime
// deployment concern rather than something the link test should depend on.
int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    if (graal_create_isolate(nullptr, &isolate, &thread) != 0) {
        fprintf(stderr, "FAIL: could not create GraalVM isolate\n");
        return 1;
    }

    // A non-zero return is acceptable here (schemas may be absent); we only
    // require that the symbol resolves and the call returns.
    int rc = exi_init(thread);
    printf("exificient: linked OK, isolate created, exi_init returned %d\n", rc);

    graal_tear_down_isolate(thread);
    return 0;
}
