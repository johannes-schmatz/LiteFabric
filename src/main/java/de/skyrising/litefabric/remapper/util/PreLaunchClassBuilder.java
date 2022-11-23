package de.skyrising.litefabric.remapper.util;

import de.skyrising.litefabric.common.EntryPointType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class PreLaunchClassBuilder {
	private final Map<String, Set<String>> entryPoints = new LinkedHashMap<>();
	private final String modId;
	public final String className;
	public PreLaunchClassBuilder(String modId) {
		this.modId = modId;
		this.className = EntryPointType.getPreLaunchEntryPoint(modId, '/');
	}

	public void addEntryPoint(String type, String clazz) {
		entryPoints
				.computeIfAbsent(type, key -> new HashSet<>())
				.add(clazz.replace('/', '.'));
	}

	public byte[] build() {
		// create a java 8 class extending PreLaunchEntrypoint
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		writer.visit(
				V1_8,
				ACC_PUBLIC | ACC_SUPER,
				className,
				null,
				Type.getInternalName(Object.class),
				new String[]{"net/fabricmc/loader/api/entrypoint/PreLaunchEntrypoint"}
		);

		// create the constructor that just calls Object's new
		MethodVisitor init = writer.visitMethod(
				ACC_PUBLIC,
				"<init>",
				Type.getMethodDescriptor(Type.VOID_TYPE),
				null,
				null
		);
		init.visitCode();

		init.visitVarInsn(ALOAD, 0);
		init.visitMethodInsn(
				INVOKESPECIAL,
				Type.getInternalName(Object.class),
				"<init>",
				Type.getMethodDescriptor(Type.VOID_TYPE),
				false
		);

		init.visitInsn(RETURN);
		init.visitMaxs(1, 1);
		init.visitEnd();

		// create the method from the interface
		MethodVisitor onPreLaunch = writer.visitMethod(
				ACC_PUBLIC,
				"onPreLaunch",
				Type.getMethodDescriptor(Type.VOID_TYPE),
				null,
				null
		);

		onPreLaunch.visitCode();

		buildEntryPoints(onPreLaunch);

		onPreLaunch.visitInsn(RETURN);
		onPreLaunch.visitMaxs(2, 1);
		onPreLaunch.visitEnd();

		// build the class
		return writer.toByteArray();
	}

	private void buildEntryPoints(MethodVisitor visitor) {
		for (Map.Entry<String, Set<String>> entry: this.entryPoints.entrySet()) {
			String type = entry.getKey();
			for (String cls: entry.getValue()) {
				buildEntryPoint(visitor, type, cls);
			}
		}
	}

	private void buildEntryPoint(MethodVisitor visitor, String type, String cls) {
		visitor.visitLdcInsn(this.modId);
		visitor.visitLdcInsn(type);
		visitor.visitLdcInsn(cls);

		visitor.visitMethodInsn(INVOKESTATIC, EntryPointType.getCollectorClassName(), "addEntryPoint",
				Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(String.class),
						Type.getType(String.class)), false);
	}
}
