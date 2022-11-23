package io.github.coolcrabs.brachyura.ide;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

public class CustomRunConfigBuilder extends IdeModule.RunConfigBuilder {
	private final IdeModule.RunConfigBuilder builder;
	public CustomRunConfigBuilder(IdeModule.RunConfigBuilder builder) {
		this.builder = builder;
	}

	@Override
	IdeModule.RunConfig build(IdeModule project) {
		String name = getPrivateField(builder, "name");
		String mainClass = getPrivateField(builder, "mainClass");
		Path cwd = getPrivateField(builder, "cwd");
		Supplier<List<String>> vmArgs = getPrivateField(builder, "vmArgs");
		Supplier<List<String>> args = getPrivateField(builder, "args");
		Supplier<List<Path>> classpath = getPrivateField(builder, "classpath");
		List<IdeModule> additionalModulesClasspath = getPrivateField(builder, "additionalModulesClasspath");
		List<Path> resourcePaths = getPrivateField(builder, "resourcePaths");

		Objects.requireNonNull(name, "Null name");
		Objects.requireNonNull(mainClass, "Null mainClass");
		//Objects.requireNonNull(cwd, "Null cwd");

		Class<IdeModule.RunConfig> cls = IdeModule.RunConfig.class;
		Constructor<?> constructor = cls.getDeclaredConstructors()[0];
		try {
			return (IdeModule.RunConfig) constructor.newInstance(project,
					name,
					mainClass,
					cwd,
					vmArgs,
					args,
					classpath,
					additionalModulesClasspath,
					resourcePaths
			);
			//return project.new RunConfig(name, mainClass, cwd, vmArgs, args, classpath, additionalModulesClasspath, resourcePaths);
		} catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static <T> T getPrivateField(IdeModule.RunConfigBuilder builder, String name) {
		try {
			Class<?> cls = builder.getClass();
			Field field = cls.getDeclaredField(name);
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			T value = (T) field.get(builder);
			return value;
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
