package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The buffer layout is written down twice — once in Java, where particles are packed, and once in GLSL, where
 * they are unpacked — and nothing in a compiler or a screenshot notices when the two stop agreeing. A style
 * count that differs shifts {@code GEMRENDER_PARTICLE_BASE}, so every particle reads someone else's texels
 * and draws something plausible in the wrong place. This is the test that fails instead.
 */
class ParticleLayoutTest {

	private static final String PARTICLE_GLSL = "assets/gemrender/flywheel/particle.glsl";

	@Test
	@DisplayName("the shipped shader unpacks particles at the size Java packs them")
	void particleFloatsAgree() {
		assertThat(constant("GEMRENDER_PARTICLE_FLOATS")).isEqualTo(ParticleBuffer.PARTICLE_FLOATS);
	}

	@Test
	@DisplayName("the shipped shader unpacks styles at the size Java packs them")
	void styleFloatsAgree() {
		assertThat(constant("GEMRENDER_STYLE_FLOATS")).isEqualTo(ParticleStyle.FLOATS);
	}

	@Test
	@DisplayName("the shader finds the first particle where Java put it")
	void styleRegionsAgree() {
		assertThat(constant("GEMRENDER_MAX_STYLES")).isEqualTo(ParticleBuffer.MAX_STYLES);
	}

	@Test
	@DisplayName("a particle and a style are both whole texels, or a slot straddles two")
	void bothAreWholeTexels() {
		assertThat(ParticleBuffer.PARTICLE_FLOATS % 4).isZero();
		assertThat(ParticleStyle.FLOATS % 4).isZero();
	}

	private static int constant(String name) {
		Matcher found = Pattern.compile("const\\s+int\\s+" + name + "\\s*=\\s*(\\d+)\\s*;")
				.matcher(source());
		if (!found.find()) {
			return fail(name + " is not declared in " + PARTICLE_GLSL);
		}
		return Integer.parseInt(found.group(1));
	}

	private static String source() {
		try (InputStream in = ParticleLayoutTest.class.getClassLoader()
				.getResourceAsStream(PARTICLE_GLSL)) {
			if (in == null) {
				return fail(PARTICLE_GLSL + " is not on the test classpath");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return fail("could not read " + PARTICLE_GLSL, e);
		}
	}
}
