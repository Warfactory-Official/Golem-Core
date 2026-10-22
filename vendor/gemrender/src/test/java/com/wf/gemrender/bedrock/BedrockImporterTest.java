package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.Ids;

import net.minecraft.resources.ResourceLocation;

class BedrockImporterTest {

	@Test
	@DisplayName("a model's texture and clips are the files of the same name beside it")
	void siblingsDropTheGeometrySuffix() {
		ResourceLocation model = Ids.of("example", "models/spider/spider.geo.json");

		assertThat(BedrockImporter.sibling(model, ".png"))
				.hasToString("example:models/spider/spider.png");
		assertThat(BedrockImporter.sibling(model, ".animation.json"))
				.hasToString("example:models/spider/spider.animation.json");
	}

	@Test
	@DisplayName("a plain .json geometry gets the same treatment")
	void plainJsonAlsoHasSiblings() {
		ResourceLocation model = Ids.of("example", "models/bedrock/test.json");
		assertThat(BedrockImporter.sibling(model, ".png")).hasToString("example:models/bedrock/test.png");
	}

	@Test
	@DisplayName("the namespace comes with it, so a pack cannot pull another pack's texture")
	void siblingsStayInTheirNamespace() {
		ResourceLocation model = Ids.of("otherpack", "a/b.geo.json");
		assertThat(BedrockImporter.sibling(model, ".png")
				.getNamespace()).isEqualTo("otherpack");
	}
}
