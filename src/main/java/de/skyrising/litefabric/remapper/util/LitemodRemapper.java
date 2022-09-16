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
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class LitemodRemapper extends Remapper implements IRemapper {
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

    /*

    Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: Callback method
    onRunTick(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V in mixins.tweakeroo.json:MixinMinecraft from
    mod tweakeroo failed injection check, (0/1) succeeded. Scanned 1 target(s). Using refmap mixins.tweakeroo.refmap.json

    @Inject(method = {"runTick"}, slice = {
    @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTickKeyboard()V"))
    },
    at = {@At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;world:Lnet/minecraft/client/multiplayer/WorldClient;", ordinal = 0)})
  private void onRunTick(CallbackInfo ci) {
    MiscTweaks.onTick((class_1600)this);
  }



Caused by: java.lang.NoSuchMethodError: fi.dy.masa.malilib.command.ClientCommandHandler.a(Lnet/minecraft/class_1061;Ljava/lang/String;)I
	at net.minecraft.class_388.handler$zzg000$onSendMessage(class_388.java:591) ~[client-intermediary.jar:?]
	at net.minecraft.class_388.method_9583(class_388.java:327) ~[client-intermediary.jar:?]


public class ClientCommandHandler extends CommandHandler
	@Override
    public int func_71556_a(ICommandSender sender, String message)


public class ClientCommandHandler extends bj
    public int a(bn sender, String message)

	//TODO it doesn't remap methods overwriting methods from super classes correctly (it remaps the method, but not references to it)
     */

    // this method gets called on each class **before** any remapping is done
    public Set<String> addClass(ClassNode node) {
        // TODO: check that this is correct for @Mixin classes
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
                // if it's not the Mixin annotation, continue searching
                if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(classAnnotation.desc))
                    continue;

                // store all fields with the @Shadow annotation in this.shadowFields
                {
                    Set<String> shadowFields = new HashSet<>();
                    for (FieldNode field: node.fields)
                        if (field.visibleAnnotations != null)
                            for (AnnotationNode fieldAnnotation: field.visibleAnnotations)
                                if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(fieldAnnotation.desc))
                                    shadowFields.add(field.name);

                    // and store then (based on the class)
                    this.shadowFields.put(node.name, shadowFields);
                }

                // store all methods with the @Shadow annotation in this.shadowMethods
                {
                    Set<String> shadowMethods = new HashSet<>();
                    for (MethodNode method: node.methods)
                        if (method.visibleAnnotations != null)
                            for (AnnotationNode methodAnnotation: method.visibleAnnotations)
                                if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(methodAnnotation.desc))
                                    shadowMethods.add(method.name);

                    // and store them (based on the class)
                    this.shadowMethods.put(node.name, shadowMethods);
                }

                List<String> targets = new ArrayList<>(1); // most mixins have one target

                int len = classAnnotation.values.size();
                for (int i = 0; i < len; i += 2) { // iterate over all annotations
                    String annotationFieldName = (String) classAnnotation.values.get(i);
                    Object annotationFieldValue = classAnnotation.values.get(i + 1);

                    if ("value".equals(annotationFieldName)) {
                        // match the @Mixin(Class.class) case
                        @SuppressWarnings("unchecked")
                        List<Type> annotationValue = (List<Type>) annotationFieldValue;
                        for (Type superClass: annotationValue) {
                            targets.add(superClass.getInternalName());
                        }
                    }
                    // it's valid to use both ways of specifying targets at the same time
                    if ("targets".equals(annotationFieldName)) {
                        // match the @Mixin(targets = "org/example/class/Class$1") case
                        @SuppressWarnings("unchecked")
                        List<String> annotationValue = (List<String>) annotationFieldValue;
                        targets.addAll(annotationValue);
                    }
                }
                if (targets.size() == 0) {
                    throw new RuntimeException("Didn't find any targets in class " + node.name + ": " + classAnnotation.values);
                }

                // TODO: refactor it to use lists/sets here, shouldn't matter, it could be that there are mixins that specify
                //  two classes, but have methods that are not in the first one/second one
                this.mixinAnnotationSuperClasses.put(node.name, targets.get(0));
            }
        }

        this.superClasses.put(node.name, superClasses);
        return superClasses;
    }

    private static final String LITELOADER_PACKAGE = "com/mumfrey/liteloader/";
    @Override
    public String map(String internalName) {
        if (internalName.startsWith(LITELOADER_PACKAGE)) {
            return "de/skyrising/litefabric/liteloader/" + internalName.substring(LITELOADER_PACKAGE.length());
        }

        ClassDef def = classes.get(internalName);
        if (def != null) {
            return def.getName(targetNamespace);
        }
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
        if (!knownClass && shadowMethods.containsKey(owner)) {
            return mapMethodName0(mixinAnnotationSuperClasses.get(owner), name, descriptor);
        }
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
            // for every super class we try to map the method in it, if it's successful return that mapping
            String superMap = mapMethodName0(superClass, name, descriptor);
            if (superMap != null) {
                return superMap;
            }
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
        //if (mixinAnnotationSuperClasses.containsKey(cls))
        //    return Collections.singleton(mixinAnnotationSuperClasses.get(cls));

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
        //T_ODO: load from the specified jar, then from the class loader?

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

            Set<String> supers = addClass(node); // TODO: here we store the named classes under non named! bug!
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

    /**
     * We need to unmap the refmap content, generated by {@link MemberInfo#toString()}:
     *
     * <pre>{@code
     * public String toString() {
     *     String owner = this.owner != null ? "L" + this.owner + ";" : "";
     *     String name = this.name != null ? this.name : "";
     *     String quantifier = this.matches.toString();
     *     String desc = this.desc != null ? this.desc : "";
     *     String separator = desc.startsWith("(") ? "" : (this.desc != null ? ":" : "");
     *     String tail = this.tail != null ? " " + MemberInfo.ARROW + " " + this.tail : "";
     *     return owner + name + quantifier + separator + desc + tail;
     * }
     * }</pre>
     *
     * @param mixinClass The internal name of the @Mixin annotated class
     * @param old The old refmap target entry.
     * @return The new refmap target entry.
     */
    public String mapRefMapEntry(String mixinClass, String old) {
        // format is:
        // owner + name + quantifier + (desc == null || desc.startsWith("(") ? "" : ":") + desc + (tail != null ? " -> " : "") + tail
        String owner; // can be ""
        String name;
        String quantifier; // can be ""
        String desc; // can be ""
        String tail; // can be ""

        // read the entry
        {
            String rest;
            // get tail
            {
                String arrow = " -> ";
                int arrowPosition = old.indexOf(arrow);
                if (arrowPosition == -1) { // tail == null
                    tail = "";
                    rest = old;
                } else {
                    rest = old.substring(0, arrowPosition);
                    tail = old.substring(arrowPosition + arrow.length());
                }
            }

            // get desc
            {
                int separatorPosition = rest.indexOf(":");
                if (separatorPosition == -1) { // separator == null
                    int parenthesisPosition = rest.indexOf("(");
                    if (parenthesisPosition == -1) {
                        desc = "";
                    } else {
                        // if there's no ':', then there must be a '(' or **the desc is null**
                        desc = rest.substring(parenthesisPosition);
                        rest = rest.substring(0, parenthesisPosition);
                    }
                } else {
                    desc = rest.substring(separatorPosition + 1);
                    rest = rest.substring(0, separatorPosition);
                }
            }

            // get owner
            {
                if (rest.startsWith("L")) { // owner != null
                    int endPosition = rest.indexOf(";");
                    if (endPosition == -1) {
                        throw new RuntimeException(
                                "Cannot parse refmap entry of class " + mixinClass + ": it starts with 'L', and doesn't contain a ';': " + old);
                    } else {
                        owner = rest.substring(1, endPosition);
                        rest = rest.substring(endPosition + 1); // we don't want the ';' here
                    }
                } else {
                    owner = "";
                }
            }

            // get quantifier
            {
                // try to find either '{', '+' or '*'
                int bracesPosition = rest.indexOf("{");
                if (bracesPosition == -1)
                    bracesPosition = rest.indexOf("*");
                if (bracesPosition == -1)
                    bracesPosition = rest.indexOf("+");

                if (bracesPosition == -1) {
                    // try the * and +
                    quantifier = "";
                } else {
                    quantifier = rest.substring(bracesPosition);
                    rest = rest.substring(0, bracesPosition);
                }
            }

            // get name
            {
                name = rest; // only name is left
                if (name.isEmpty()) {
                    throw new RuntimeException("Cannot parse refmap entry of class " + mixinClass +
                            ": the name is \"\", so something went wrong: owner = \"" + owner + "\", name = \"" + name +
                            "\", quantifier = \"" + quantifier + "\", desc = \"" + desc + "\", tail = \"" + tail +
                            "\", old = \"" + old + "\"");
                }
            }
        }

        // for now just stop here, most stuff doesn't use quantifiers or tails
        if (!quantifier.isEmpty())
            throw new RuntimeException("Quantifiers are not yet supported: " + old);
        if (!tail.isEmpty())
            throw new RuntimeException("Tails are not yet supported: " + tail);

        // do the actual mapping

        // it's a class
        if (owner.isEmpty() && desc.isEmpty()) {
            return map(name);
        }

        // it's a method
        if (desc.startsWith("(") && desc.contains(")")) {
            if (owner.isEmpty()) { // it's an @Invoker
                String mixinOwner = mixinAnnotationSuperClasses.get(mixinClass);
                if (mixinOwner == null)
                    throw new RuntimeException("Can't find target class for mixin " + mixinClass);
                return mapMethodName(mixinOwner, name, desc) + mapDesc(desc);
            } else { // just a normal method
                return "L" + map(owner) + ";" + mapMethodName(owner, name, desc) + mapDesc(desc);
            }
        }

        // it's an @Accessor
        if (owner.isEmpty()) {
            String mixinOwner = mixinAnnotationSuperClasses.get(mixinClass);
            if (mixinOwner == null)
                throw new RuntimeException("Can't find target class for mixin " + mixinClass);
            return mapFieldName(mixinOwner, name, desc) + ":" + desc;
        }

        // just a normal field
        return "L" + map(owner) + ";" + mapFieldName(owner, name, desc) + ":" + desc;
    }
}
