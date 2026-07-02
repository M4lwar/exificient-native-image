package org.example;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.ByteArrayInputStream;

public class ExiLibrary {
    private static ExiProcessor processor;

    // schemaPath: NUL-terminated path to the XSD to load. If NULL/empty, the
    // default schema (./schemas/UCI_MessageDefinitions_v2_5_0.xsd) is used.
    @CEntryPoint(name = "exi_init")
    public static int init(IsolateThread thread, CCharPointer schemaPath) {
        try {
            String path = schemaPath.isNull() ? null : CTypeConversion.toJavaString(schemaPath);
            processor = new ExiProcessor(path);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    // XML bytes -> EXI bytes
    @CEntryPoint(name = "exi_encode")
    public static CCharPointer encode(IsolateThread thread,
                                      CCharPointer input, int inputLen,
                                      CIntPointer outputLen) {
        try {
            byte[] in = new byte[inputLen];
            CTypeConversion.asByteBuffer(input, inputLen).get(in);
            byte[] out = processor.encode(new ByteArrayInputStream(in)).toByteArray();
            outputLen.write(out.length);
            CCharPointer result = UnmanagedMemory.malloc(out.length);
            CTypeConversion.asByteBuffer(result, out.length).put(out);
            return result;
        } catch (Exception e) {
            outputLen.write(-1);
            return (CCharPointer) WordFactory.nullPointer();
        }
    }

    // EXI bytes -> XML bytes
    @CEntryPoint(name = "exi_decode")
    public static CCharPointer decode(IsolateThread thread,
                                      CCharPointer input, int inputLen,
                                      CIntPointer outputLen) {
        try {
            byte[] in = new byte[inputLen];
            CTypeConversion.asByteBuffer(input, inputLen).get(in);
            byte[] out = processor.decode(new ByteArrayInputStream(in)).toByteArray();
            outputLen.write(out.length);
            CCharPointer result = UnmanagedMemory.malloc(out.length);
            CTypeConversion.asByteBuffer(result, out.length).put(out);
            return result;
        } catch (Exception e) {
            outputLen.write(-1);
            return (CCharPointer) WordFactory.nullPointer();
        }
    }

    // Caller must free buffers returned by encode/decode
    @CEntryPoint(name = "exi_free")
    public static void free(IsolateThread thread, CCharPointer ptr) {
        UnmanagedMemory.free(ptr);
    }
}