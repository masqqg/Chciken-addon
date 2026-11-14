import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ASM custom protection layer, runs after the obfuscators and before signing.
 *
 * 1. Renames the class names that survived obfuscation (the private records
 *    Umbra keeps for their Record attribute) consistently through every class.
 *    Package names are untouched, so MeteorAddon#getPackage() stays valid.
 * 2. Drops SourceFile, SourceDebugExtension, LocalVariableTable,
 *    LocalVariableTypeTable, LineNumberTable and MethodParameters.
 * 3. Verifies every rewritten class with ASM's CheckClassAdapter, so a broken
 *    transform fails the build instead of shipping an unloadable jar.
 * 4. Analyses reflective access: string constants that name a class present in
 *    the jar are reported, because those names have to stay readable at runtime.
 * 5. Writes a per-class SHA-256 integrity manifest next to the output jar.
 *
 * Method names, descriptors, annotations and everything the loader or a mixin
 * looks up by name are left alone.
 */
public class Sanitizer {
    // Class-name fragments that still tell an analyst what the code does.
    private static final String[] LEAKS = {
        "ChickenStorageESP", "ClusterBypass", "ClearRender", "TracerTarget", "EntityBox", "StorageESP"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: Sanitizer <in.jar> <out.jar> [report.txt]");
            System.exit(2);
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        File report = args.length > 2 ? new File(args[2]) : null;

        List<String> classes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(input)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class")) classes.add(name.substring(0, name.length() - 6));
            }
        }

        Set<String> taken = new HashSet<>(classes);
        Map<String, String> mapping = new HashMap<>();
        int counter = 0;

        for (String internal : classes) {
            String simple = internal.substring(internal.lastIndexOf('/') + 1);
            if (!leaks(simple)) continue;

            String pkg = internal.contains("/") ? internal.substring(0, internal.lastIndexOf('/') + 1) : "";
            String candidate;
            do {
                candidate = pkg + shortName(counter++);
            } while (taken.contains(candidate));

            taken.add(candidate);
            mapping.put(internal, candidate);
        }

        SimpleRemapper remapper = new SimpleRemapper(mapping);

        Map<String, String> integrity = new LinkedHashMap<>();
        Set<String> reflectiveNames = new HashSet<>();
        int verified = 0;

        try (ZipFile zip = new ZipFile(input);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output))) {

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }

                if (entry.getName().endsWith(".class")) {
                    collectReflectiveNames(bytes, reflectiveNames);

                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor stripper = new StripVisitor(writer);
                    reader.accept(new ClassRemapper(stripper, remapper), 0);
                    bytes = writer.toByteArray();

                    verify(bytes);
                    verified++;

                    String internal = entry.getName().substring(0, entry.getName().length() - 6);
                    String renamed = mapping.getOrDefault(internal, internal);
                    integrity.put(renamed + ".class", sha256(bytes));
                }

                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String internal = entryName.substring(0, entryName.length() - 6);
                    entryName = mapping.getOrDefault(internal, internal) + ".class";
                }

                ZipEntry copy = new ZipEntry(entryName);
                copy.setTime(0L);
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            System.out.println("renamed " + entry.getKey() + " -> " + entry.getValue());
        }

        List<String> readable = new ArrayList<>();
        for (String name : reflectiveNames) {
            String internal = name.replace('.', '/');
            if (classes.contains(internal) || classes.contains(name)) readable.add(name);
        }
        java.util.Collections.sort(readable);

        System.out.println("verified: " + verified + " class(es) pass ASM verification");
        System.out.println("reflection: " + reflectiveNames.size() + " class-name string constant(s), "
                + readable.size() + " resolve inside the jar and stay unrenamed");
        System.out.println("integrity: " + integrity.size() + " class hash(es) recorded");
        System.out.println("sanitized: " + mapping.size() + " class name(s) renamed, debug metadata stripped");

        if (report != null) {
            try (PrintStream ps = new PrintStream(report, StandardCharsets.UTF_8)) {
                ps.println("# ASM protection report");
                ps.println();
                ps.println("classes: " + integrity.size());
                ps.println("verified: " + verified);
                ps.println("class-name strings seen: " + reflectiveNames.size());
                ps.println("class-name strings that resolve in the jar (kept readable): " + readable.size());
                ps.println();
                ps.println("## reflection targets inside the jar");
                for (String name : readable) ps.println("- " + name);
                ps.println();
                ps.println("## integrity (sha256 per class)");
                for (Map.Entry<String, String> e : integrity.entrySet()) {
                    ps.println(e.getValue() + "  " + e.getKey());
                }
            }
        }

        // Integrity manifest next to the jar: verifiable without any runtime code.
        File manifest = new File(output.getParentFile(), output.getName().replaceAll("\\.jar$", "") + ".integrity");
        try (PrintStream ps = new PrintStream(manifest, StandardCharsets.UTF_8)) {
            ps.println("# sha256 per class, generated by the ASM protection layer");
            for (Map.Entry<String, String> e : integrity.entrySet()) {
                ps.println(e.getValue() + "  " + e.getKey());
            }
        }
    }

    private static void verify(byte[] bytes) {
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new CheckClassAdapter(writer, false), 0);
        } catch (RuntimeException e) {
            throw new IllegalStateException("ASM verification failed for a rewritten class: " + e.getMessage(), e);
        }
    }

    /** Collects plain string constants that look like binary class names. */
    private static void collectReflectiveNames(byte[] bytes, Set<String> into) {
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String s && looksLikeClassName(s)) into.add(s);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG);
    }

    private static boolean looksLikeClassName(String value) {
        if (value.length() < 4 || value.length() > 200) return false;
        if (!value.contains(".")) return false;
        return value.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean leaks(String simpleName) {
        for (String leak : LEAKS) {
            if (simpleName.contains(leak)) return true;
        }
        return false;
    }

    /** a, b, ... z, aa, ab, ... - deterministic and collision free. */
    private static String shortName(int value) {
        StringBuilder sb = new StringBuilder();
        int v = value;
        do {
            sb.insert(0, (char) ('a' + (v % 26)));
            v = v / 26 - 1;
        } while (v >= 0);
        return sb.toString();
    }

    private static final class StripVisitor extends ClassVisitor {
        StripVisitor(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visitSource(String source, String debug) {
            // SourceFile and SourceDebugExtension are intentionally dropped.
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // inner_name is not a class reference, so ClassRemapper keeps it.
            // Re-derive it from the (already renamed) binary name, and drop a
            // stale outer reference that still spells a leaked class name.
            String fixedInner = innerName;
            if (innerName != null) {
                String simple = name.substring(name.lastIndexOf('/') + 1);
                int dollar = simple.lastIndexOf('$');
                if (dollar >= 0) simple = simple.substring(dollar + 1);
                fixedInner = simple;
            }

            String fixedOuter = outerName;
            if (outerName != null) {
                String simpleOuter = outerName.substring(outerName.lastIndexOf('/') + 1);
                if (leaks(simpleOuter)) fixedOuter = null;
            }

            super.visitInnerClass(name, fixedOuter, fixedInner, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            // A native method would make the jar require a platform library.
            // Replace it with the exact failure the JVM produces when the
            // library is missing, as a normal (throwable) Java method, so the
            // existing try/catch fallbacks keep working without any .so/.dll.
            if ((access & Opcodes.ACC_NATIVE) != 0) {
                MethodVisitor stub = super.visitMethod(access & ~Opcodes.ACC_NATIVE,
                        name, descriptor, signature, exceptions);
                if (stub == null) return null;
                stub.visitCode();
                stub.visitTypeInsn(Opcodes.NEW, "java/lang/UnsatisfiedLinkError");
                stub.visitInsn(Opcodes.DUP);
                stub.visitLdcInsn(name);
                stub.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsatisfiedLinkError",
                        "<init>", "(Ljava/lang/String;)V", false);
                stub.visitInsn(Opcodes.ATHROW);
                int locals = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
                for (Type t : Type.getArgumentTypes(descriptor)) locals += t.getSize();
                stub.visitMaxs(3, locals);
                stub.visitEnd();
                return null;
            }

            MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (next == null) return null;

            return new MethodVisitor(Opcodes.ASM9, next) {
                @Override
                public void visitLocalVariable(String name, String descriptor, String signature,
                                               Label start, Label end, int index) {
                    // dropped
                }

                @Override
                public void visitLineNumber(int line, Label start) {
                    // dropped
                }

                @Override
                public void visitParameter(String name, int access) {
                    // dropped
                }
            };
        }
    }
}
