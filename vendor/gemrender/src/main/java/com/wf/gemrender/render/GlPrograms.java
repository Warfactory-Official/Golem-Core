package com.wf.gemrender.render;

import com.wf.gemrender.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20C.*;

public final class GlPrograms {
    private GlPrograms() {
    }

    public static String resource(String namespace, String path) throws IOException {
        ResourceLocation location = Ids.of(namespace, path);
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .open(location)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static int link(String name, String vertexSource, String fragmentSource) {
        int vertex = compile(name, GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(name, GL_FRAGMENT_SHADER, fragmentSource);

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);

        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException(name + " failed to link: " + glGetProgramInfoLog(program));
        }
        return program;
    }

    public static int compile(String name, int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException(name + (type == GL_VERTEX_SHADER ? " (vert)" : " (frag)")
                    + " failed to compile: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
