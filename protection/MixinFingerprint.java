import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prints everything a mixin depends on by name: the class itself, its
 * annotations (@Mixin and friends), every method and field with its descriptor,
 * their annotations, and the annotation values (@At targets, @Shadow members,
 * remap flags).
 *
 * The guard runs this on the pre-protection jar and on the protected jar and
 * compares the output: any difference means a protection layer touched a mixin.
 */
public class MixinFingerprint {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: MixinFingerprint <jar> <class>...");
            System.exit(2);
        }

        File jar = new File(args[0]);
        List<String> classes = new ArrayList<>();
        for (int i = 1; i < args.length; i++) classes.add(args[i]);

        try (ZipFile zip = new ZipFile(jar)) {
            for (String name : new TreeSet<>(classes)) {
                ZipEntry entry = zip.getEntry(name.replace('.', '/') + ".class");
                if (entry == null) {
                    System.out.println("MISSING " + name);
                    continue;
                }

                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }

                final String owner = name;
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        System.out.println("class-ann " + owner + " " + descriptor);
                        return dump(owner + " ann:" + descriptor);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                     String signature, String[] exceptions) {
                        System.out.println("method " + owner + " " + method + descriptor);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                System.out.println("method-ann " + owner + " " + method + descriptor + " " + desc);
                                return dump(owner + " " + method + descriptor + " ann:" + desc);
                            }
                        };
                    }

                    @Override
                    public FieldVisitor visitField(int access, String field, String descriptor,
                                                   String signature, Object value) {
                        System.out.println("field " + owner + " " + field + " " + descriptor);
                        return new FieldVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                System.out.println("field-ann " + owner + " " + field + " " + desc);
                                return dump(owner + " field:" + field + " ann:" + desc);
                            }
                        };
                    }

                    private AnnotationVisitor dump(String prefix) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String key, Object value) {
                                System.out.println("value " + prefix + " " + key + "=" + value);
                            }

                            @Override
                            public void visitEnum(String key, String descriptor, String value) {
                                System.out.println("enum " + prefix + " " + key + "=" + descriptor + "." + value);
                            }

                            @Override
                            public AnnotationVisitor visitArray(String key) {
                                return dump(prefix + " " + key + "[]");
                            }

                            @Override
                            public AnnotationVisitor visitAnnotation(String key, String descriptor) {
                                System.out.println("nested " + prefix + " " + key + " -> " + descriptor);
                                return dump(prefix + " " + key + " ann");
                            }
                        };
                    }
                }, 0);
            }
        }
    }
}
