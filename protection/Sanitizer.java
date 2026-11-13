import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Post-processing pass that runs after the obfuscators, before signing.
 *
 * 1. Renames the classes whose names survived obfuscation (the private records
 *    Umbra keeps for their Record attribute), consistently through every class
 *    file. Package names are untouched, so MeteorAddon#getPackage() stays valid.
 * 2. Drops SourceFile, SourceDebugExtension, LocalVariableTable,
 *    LocalVariableTypeTable, LineNumberTable and MethodParameters.
 *
 * Method names, descriptors, annotations and everything the loader or a
 * framework looks up by name are left alone.
 */
public class Sanitizer {
    // Class-name fragments that still tell an analyst what the code does.
    private static final String[] LEAKS = {
        "ChickenStorageESP", "ClusterBypass", "ClearRender", "TracerTarget", "EntityBox", "StorageESP"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: Sanitizer <in.jar> <out.jar>");
            System.exit(2);
        }

        File input = new File(args[0]);
        File output = new File(args[1]);

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
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor stripper = new StripVisitor(writer);
                    reader.accept(new ClassRemapper(stripper, remapper), 0);
                    bytes = writer.toByteArray();
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
        System.out.println("sanitized: " + mapping.size() + " class name(s) renamed, debug metadata stripped");
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
