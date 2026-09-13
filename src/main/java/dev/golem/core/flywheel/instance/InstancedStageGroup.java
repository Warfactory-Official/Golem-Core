package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.Mesh;
import dev.golem.core.flywheel.MeshPool;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Stage-local submission cache for the INSTANCING backend. Mirrors the indirect backend's
// stage-group split: flush once per frame, sort active instancers by (material, texture, mesh),
// then submit contiguous ranges that share pipeline + Sampler0.
public final class InstancedStageGroup {

    private static final Comparator<InstancedInstancer> DRAW_ORDER = Comparator
            .comparingInt((InstancedInstancer i) -> i.material().ordinal())
            .thenComparing(i -> i.texture().getNamespace())
            .thenComparing(i -> i.texture().getPath())
            .thenComparingInt(InstancedInstancer::meshHandle);

    private final Material.Stage stage;
    private final List<InstancedInstancer> instancers = new ArrayList<>();
    private final List<InstancedInstancer> activeInstancers = new ArrayList<>();
    private final List<Submission> submissions = new ArrayList<>();

    public InstancedStageGroup(Material.Stage stage) {
        this.stage = stage;
    }

    public Material.Stage stage() {
        return stage;
    }

    void addInstancer(InstancedInstancer instancer) {
        instancers.add(instancer);
    }

    public List<InstancedInstancer> activeInstancers() {
        return activeInstancers;
    }

    public List<Submission> submissions() {
        return submissions;
    }

    public synchronized void flushAll() {
        activeInstancers.clear();
        submissions.clear();

        for (InstancedInstancer instancer : instancers) {
            instancer.flush();
            if (instancer.liveCount() == 0) continue;
            Mesh mesh = MeshPool.mesh(instancer.meshHandle());
            if (mesh.empty() || instancer.gpuBuffer() == null) continue;
            activeInstancers.add(instancer);
        }
        if (activeInstancers.isEmpty()) return;

        activeInstancers.sort(DRAW_ORDER);
        for (int i = 0; i < activeInstancers.size(); i++) {
            appendSubmission(activeInstancers.get(i), i);
        }
    }

    public synchronized void close() {
        instancers.clear();
        activeInstancers.clear();
        submissions.clear();
    }

    private void appendSubmission(InstancedInstancer instancer, int instancerIndex) {
        if (!submissions.isEmpty()) {
            Submission last = submissions.get(submissions.size() - 1);
            if (last.material == instancer.material() && last.texture.equals(instancer.texture())) {
                last.instancerCount++;
                return;
            }
        }
        submissions.add(new Submission(instancer.material(), instancer.texture(), instancerIndex));
    }

    public static final class Submission {
        private final Material material;
        private final Identifier texture;
        private final int firstInstancerIndex;
        private int instancerCount = 1;

        Submission(Material material, Identifier texture, int firstInstancerIndex) {
            this.material = material;
            this.texture = texture;
            this.firstInstancerIndex = firstInstancerIndex;
        }

        public Material material() {
            return material;
        }

        public Identifier texture() {
            return texture;
        }

        public int firstInstancerIndex() {
            return firstInstancerIndex;
        }

        public int instancerCount() {
            return instancerCount;
        }
    }
}
//?}
