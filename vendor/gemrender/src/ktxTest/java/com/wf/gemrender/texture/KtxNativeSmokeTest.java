package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KtxNativeSmokeTest {
	@Test
	@DisplayName("libktx links through libffi on this node's LWJGL core")
	void theNativeLoads() {
		assertThat(Libktx.errorString(Libktx.SUCCESS)).isNotBlank();
	}
}
