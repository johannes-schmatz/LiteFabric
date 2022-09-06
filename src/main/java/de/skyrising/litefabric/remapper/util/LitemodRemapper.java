package de.skyrising.litefabric.remapper.util;

import net.fabricmc.mapping.tree.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class LitemodRemapper extends Remapper implements IRemapper {
    private static final String LITELOADER_PACKAGE = "com/mumfrey/liteloader/";
    private static final String SOURCE_NAMESPACE = "official";
    private final Map<String, ClassDef> classes = new HashMap<>();
    private final Map<String, String> classesReverse = new HashMap<>();
    private final Map<String, Set<String>> superClasses = new HashMap<>();
    private final Map<String, Map<String, FieldDef>> fields = new HashMap<>();
    private final Map<String, Map<String, MethodDef>> methods = new HashMap<>();
    private final Map<String, Set<String>> shadowFields = new HashMap<>();
    private final Map<String, Set<String>> shadowMethods = new HashMap<>();
    private final String targetNamespace;
    private final Map<String, String> mixinAnnotationSuperClasses = new HashMap<>();
    //private final Function<String, InputStream> classGetter;

    public LitemodRemapper(TinyTree mappings, String targetNamespace/*, Function<String, InputStream> classGetter*/) {
        this.targetNamespace = targetNamespace;
        for (ClassDef cls : mappings.getClasses()) {
            String clsName = cls.getName(SOURCE_NAMESPACE);
            //String targetName = cls.getName(targetNamespace);

            classes.put(clsName, cls);
            //classesReverse.put(targetName, clsName);

            // reason for doing this is using this as a hack fix for the addClass of a "named" name
            classesReverse.put(cls.getName("named"), clsName);
            classesReverse.put(cls.getName("intermediary"), clsName);
        }
        //this.classGetter = classGetter;
    }

    public Set<String> addClass(ClassNode node) {
        LinkedHashSet<String> superClasses = new LinkedHashSet<>();
        String superName = node.superName;

        // add the super name to the super classes
        if (node.superName != null)
            superClasses.add(classesReverse.getOrDefault(superName, superName));

        // add the interface names to the super classes
        for (String itfName : node.interfaces) {
            superClasses.add(classesReverse.getOrDefault(itfName, itfName));
        }

        // for every annotation on this class
        if (node.invisibleAnnotations != null) {
            for (AnnotationNode classAnnotation: node.invisibleAnnotations) {

                // if it's the Mixin annotation
                if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(classAnnotation.desc)) {

                    {
                        Set<String> shadowFields = new HashSet<>();

                        // collect all fields with the @Shadow annotation
                        for (FieldNode field: node.fields)
                            if (field.visibleAnnotations != null)
                                for (AnnotationNode fieldAnnotation: field.visibleAnnotations)
                                    if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(fieldAnnotation.desc))
                                        shadowFields.add(field.name);

                        // and store then (based on the class)
                        this.shadowFields.put(node.name, shadowFields);
                    }
                    {
                        Set<String> shadowMethods = new HashSet<>();

                        // collect all methods with the @Shadow annotation
                        for (MethodNode method: node.methods)
                            if (method.visibleAnnotations != null)
                                for (AnnotationNode methodAnnotation: method.visibleAnnotations)
                                    if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(methodAnnotation.desc))
                                        shadowMethods.add(method.name);

                        // and store them (based on the class)
                        this.shadowMethods.put(node.name, shadowMethods);
                    }
                    
                    if (classAnnotation.values.size() == 2) {
                        // TODO: handle more than one field set
                        String annotationFieldName = (String) classAnnotation.values.get(0);

                        if ("value".equals(annotationFieldName)) {
                            // match the @Mixin(Class.class) case
                            Object valuesEntry = classAnnotation.values.get(1);

                            @SuppressWarnings("unchecked")
                            Type superClass = ((List<Type>) valuesEntry).get(0);

                            this.mixinAnnotationSuperClasses.put(node.name, superClass.getInternalName());
                        } else if ("targets".equals(annotationFieldName)) {
                            // match the @Mixin(targets = "org.example.class.Class$1") case
                            @SuppressWarnings("unchecked")
                            List<Object> targetsEntry = (List<Object>) classAnnotation.values.get(1);

                            if (targetsEntry.size() != 1) {
                                new RuntimeException("Unexpected multiple targets for class " + node.name + ": " + classAnnotation.values).printStackTrace();
                            }

                            for (Object target: targetsEntry) {
                                String internalName = (String) target;

                                this.mixinAnnotationSuperClasses.put(node.name, internalName);
                            }
                        }
                    } else {
                        throw new RuntimeException("Unknown annotation field for class " + node.name + ": " + classAnnotation.values);
                    }

                    break;
                }
            }
        }

        return addSuperClassMapping(node.name, superClasses);
    }

    @Override
    public String map(String internalName) {
        if (internalName.startsWith(LITELOADER_PACKAGE)) {
            return "de/skyrising/litefabric/liteloader/" + internalName.substring(LITELOADER_PACKAGE.length());
        }

        ClassDef def = classes.get(internalName);
        if (def != null) return def.getName(targetNamespace);
        return internalName;
    }

    private <T extends Descriptored> Map<String, T> computeDescriptored(Collection<T> collection) {
        Map<String, T> map = new HashMap<>();
        for (T def : collection) {
            String key = def.getName(SOURCE_NAMESPACE) + def.getDescriptor(SOURCE_NAMESPACE);
            map.put(key, def);
        }
        return map;
    }

    private Map<String, FieldDef> computeFields(String clsName) {
        ClassDef clsDef = classes.get(clsName);
        if (clsDef == null) return null;
        return computeDescriptored(clsDef.getFields());
    }

    private String mapFieldName0(String owner, String name, String descriptor) {
        if (isUnmappedField(owner)) return null;
        boolean knownClass = classes.containsKey(owner);
        if (!knownClass && classesReverse.containsKey(owner)) {
            knownClass = true;
            owner = unmap(owner);
            descriptor = unmapDesc(descriptor);
        }
        // don't traverse super classes for @Shadow fields
        //if (!knownClass && shadowFields.containsKey(owner)) {
        //    if (shadowFields.get(owner).contains(name)) return null;
        //}

        // map @Shadow fields
        if (!knownClass && shadowFields.containsKey(owner)) {
            return mapFieldName0(mixinAnnotationSuperClasses.get(owner), name, descriptor);
        }

        if (knownClass) {
            Map<String, FieldDef> fieldMap = fields.computeIfAbsent(owner, this::computeFields);
            if (fieldMap != null) {
                FieldDef fieldDef = fieldMap.get(name + descriptor);
                if (fieldDef != null) return fieldDef.getName(targetNamespace);
            }
        }
        return mapFieldNameFromSupers(owner, name, descriptor);
    }

    private boolean isUnmappedField(String owner) {
        return owner == null || owner.isEmpty() || owner.charAt(0) == '[';
    }

    private String mapFieldNameFromSupers(String owner, String name, String descriptor) {
        Set<String> superClassNames = getSuperClasses(owner);
        for (String superClass : superClassNames) {
            String superMap = mapFieldName0(superClass, name, descriptor);
            if (superMap != null) return superMap;
        }
        return null;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String mapped = mapFieldName0(owner, name, descriptor);
        if (mapped != null) return mapped;
        return name;
    }

    private Map<String, MethodDef> computeMethods(String clsName) {
        ClassDef clsDef = classes.get(clsName);
        if (clsDef == null) return null;
        return computeDescriptored(clsDef.getMethods());
    }

    private String mapMethodName0(String owner, String name, String descriptor) {
        if (isUnmappedMethod(owner, name)) return null;
        boolean knownClass = classes.containsKey(owner);
        if (!knownClass && classesReverse.containsKey(owner)) {
            knownClass = true;
            owner = unmap(owner);
            descriptor = unmapDesc(descriptor);
        }

        // map @Shadow methods
        //if (!knownClass && shadowMethods.containsKey(owner)) {
        //    return mapMethodName0(mixinAnnotationSuperClasses.get(owner), name, descriptor);
        //}
        // TODO: fix shadow methods!!!

        if (knownClass) {
            Map<String, MethodDef> methodMap = methods.computeIfAbsent(owner, this::computeMethods);
            if (methodMap != null) {
                MethodDef methodDef = methodMap.get(name + descriptor);
                if (methodDef != null) return methodDef.getName(targetNamespace);
            }
        }
        return mapMethodNameFromSupers(owner, name, descriptor);
    }

    private static boolean isUnmappedMethod(String owner, String name) {
        return name.isEmpty() || name.charAt(0) == '<' || owner == null || owner.isEmpty() || owner.charAt(0) == '[';
    }

    private String mapMethodNameFromSupers(String owner, String name, String descriptor) {
        Set<String> superClassNames = getSuperClasses(owner);
        for (String superClass : superClassNames) {
            String superMap = mapMethodName0(superClass, name, descriptor);
            if (superMap != null) return superMap;
        }
        return null;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        String mapped = mapMethodName0(owner, name, descriptor);
        if (mapped != null) return mapped;
        return name;
    }

    private Set<String> getSuperClasses(String cls) {
        if (cls == null)
            return Collections.emptySet();
        if (superClasses.containsKey(cls))
            return superClasses.get(cls);
        if (mixinAnnotationSuperClasses.containsKey(cls))
            return Collections.singleton(mixinAnnotationSuperClasses.get(cls));

        // get the non mapped class
        //InputStream in = FabricLauncherBase.getLauncher().getResourceAsStream(map(cls) + ".class");
        // T_ODO: remove this somehow, load it from the jar instead

        // T_ODO: this doesn't work in the dev env with intermediary mappings

        // use "named" for getting the super classes, later remap the super classes again to the "official" names
        String clsMappedNamed;
        ClassDef def = classes.get(cls);
        if (def != null) {
            clsMappedNamed = def.getName("named");
        } else {
            clsMappedNamed = cls;
        }

        InputStream in = LitemodRemapper.class.getClassLoader().getResourceAsStream(clsMappedNamed + ".class");
        //TODO: load from the specified jar, then from the class loader?

        //if (in == null)
        //    in = classGetter.apply(map(cls));

        /*if (in == null) {
            in = Main.class.getClassLoader().getResourceAsStream(map(cls) + ".class");
            if (in != null) {
                System.out.println("->      " + cls);
            }
        }*/

        if (in == null) {
            return addSuperClassMapping(cls, Collections.emptySet());
        }

        try {
            ClassReader reader = new ClassReader(in);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

            Set<String> supers = addClass(node);
            return addSuperClassMapping(cls, supers); //pretty hacky, adds stuff twice, with different keys, shouldn't matter
        } catch (IOException e) {
            e.printStackTrace();
        }
        return addSuperClassMapping(cls, Collections.emptySet());
    }

    private Set<String> addSuperClassMapping(String cls, Set<String> supers) {
        this.superClasses.put(cls, supers);
        return supers;
    }

    @Override
    public String unmap(String typeName) {
        if (!classesReverse.containsKey(typeName)) return typeName;
        return classesReverse.get(typeName);
    }

    @Override
    public String unmapDesc(String old) {
        if (old == null) return null;
        int lastL = old.indexOf('L');
        int lastSemi = -1;
        if (lastL < 0) return old;
        int oldLength = old.length();
        StringBuilder builder = new StringBuilder(oldLength + oldLength / 4);
        while (lastL >= 0) {
            if (lastSemi + 1 < lastL) builder.append(old, lastSemi + 1, lastL);

            lastSemi = old.indexOf(';', lastL + 1);
            if (lastSemi == -1) return old;

            builder.append('L').append(unmap(old.substring(lastL + 1, lastSemi))).append(';');
            lastL = old.indexOf('L', lastSemi + 1);
        }

        if (lastSemi + 1 < old.length()) builder.append(old, lastSemi + 1, old.length());

        return builder.toString();
    }

    @Override
    public String mapDesc(String old) {
        if (old == null) return null;
        int lastL = old.indexOf('L');
        int lastSemi = -1;
        if (lastL < 0) return old;
        int oldLength = old.length();
        StringBuilder builder = new StringBuilder(oldLength + oldLength / 4);
        while (lastL >= 0) {
            if (lastSemi + 1 < lastL) builder.append(old, lastSemi + 1, lastL);

            lastSemi = old.indexOf(';', lastL + 1);
            if (lastSemi == -1) return old;

            builder.append('L').append(map(old.substring(lastL + 1, lastSemi))).append(';');
            lastL = old.indexOf('L', lastSemi + 1);
        }

        if (lastSemi + 1 < old.length()) builder.append(old, lastSemi + 1, old.length());

        return builder.toString();
    }

    public String mapRefMapEntry(String mixinClass, String old) {
        String superClass = mixinAnnotationSuperClasses.get(mixinClass);
        if (superClass != null) {
            int separator = old.indexOf(':');
            if (separator == -1) {
                // remap the refmap method
                if (old.startsWith("L")) {
                    // method from other class
                    int otherClassEnd = old.indexOf(";");
                    int descStart = old.indexOf("(");
                    String otherClass = old.substring(1, otherClassEnd);
                    String methodName = old.substring(otherClassEnd + 1, descStart);
                    String descriptor = old.substring(descStart);

                    // if this can't map a method (that isn't in the class, but in an interface for example)
                    // make sure that that class can be loaded from the classpath.

                    return "L" + map(otherClass) + ";" + mapMethodName(otherClass, methodName, descriptor) + mapDesc(descriptor);
                } else {
                    // method from super class
                    int descStart = old.indexOf("(");
                    String methodName = old.substring(0, descStart);
                    String descriptor = old.substring(descStart);

                    return mapMethodName(superClass, methodName, descriptor) + mapDesc(descriptor);
                }
            } else {
                // remap the refmap field
                String fieldName = old.substring(0, separator);
                String fieldDescriptor = old.substring(separator + 1);
                if (fieldName.startsWith("L")) {
                    // an @Inject annotation with value="FIELD"
                    int otherClassEnd = fieldName.indexOf(";");
                    String otherClass = fieldName.substring(1, otherClassEnd);
                    String otherClassFieldName = fieldName.substring(otherClassEnd + 1);
                    return "L" + map(otherClass) + ";" + mapFieldName(otherClass, otherClassFieldName, fieldDescriptor) + ":" + mapDesc(fieldDescriptor);
                } else {
                    // just a normal field
                    return mapFieldName(superClass, fieldName, fieldDescriptor) + ":" + mapDesc(fieldDescriptor);
                }
            }
        }
        throw new RuntimeException("Can't find (super) class in mixin annotation in class " + mixinClass);
    }
}
