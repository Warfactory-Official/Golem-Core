// On 26.1 GemRender 0.1.21 and older carry their own Flywheel, and 0.1.22 nests upstream Flywheel, whose API differs.
// Core compiles against both at once, so a call only one of them has builds cleanly and fails at runtime on the
// other. `checkGemRenderApi` fails if a Flywheel or GemRender member that dev.golem.core.gemrender calls does not
// resolve in each: deps.gemrender (the CurseForge GemRender, own Flywheel) and deps.gemrender_flywheel (upstream).

import java.io.DataInputStream
import java.util.zip.ZipFile
import org.gradle.api.tasks.SourceSetContainer

class LibClass(val superName: String?, val interfaces: List<String>, val members: Set<String>)

class Ref(val kind: Int, val owner: String, val name: String, val desc: String)

// Class and member references in a class file's constant pool, plus the class's own supertypes and members.
class ClassFile(bytes: ByteArray) {
    val refs = mutableListOf<Ref>()
    val classRefs = mutableSetOf<String>()
    val lib: LibClass
    val name: String

    init {
        val input = DataInputStream(bytes.inputStream())
        input.skipBytes(8)
        val count = input.readUnsignedShort()
        val utf = arrayOfNulls<String>(count)
        val classIndex = IntArray(count)
        val nameAndType = Array(count) { IntArray(2) }
        val memberRefs = mutableListOf<IntArray>()
        var i = 1
        while (i < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf[i] = input.readUTF()
                7 -> classIndex[i] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.skipBytes(2)
                9, 10, 11 -> memberRefs += intArrayOf(tag, input.readUnsignedShort(), input.readUnsignedShort())
                12 -> nameAndType[i] = intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
                3, 4, 17, 18 -> input.skipBytes(4)
                5, 6 -> { input.skipBytes(8); i++ }
                15 -> input.skipBytes(3)
                else -> error("constant pool tag $tag")
            }
            i++
        }
        fun className(index: Int) = utf[classIndex[index]]!!
        for (index in 1 until count) if (classIndex[index] != 0) classRefs += className(index)
        for ((tag, owner, nat) in memberRefs.map { Triple(it[0], it[1], it[2]) }) {
            refs += Ref(tag, className(owner), utf[nameAndType[nat][0]]!!, utf[nameAndType[nat][1]]!!)
        }
        input.skipBytes(2)
        name = className(input.readUnsignedShort())
        val superIndex = input.readUnsignedShort()
        val interfaces = List(input.readUnsignedShort()) { className(input.readUnsignedShort()) }
        val members = mutableSetOf<String>()
        repeat(2) {
            repeat(input.readUnsignedShort()) {
                input.skipBytes(2)
                members += utf[input.readUnsignedShort()] + ":" + utf[input.readUnsignedShort()]
                repeat(input.readUnsignedShort()) {
                    input.skipBytes(2)
                    input.skipBytes(input.readInt())
                }
            }
        }
        lib = LibClass(if (superIndex == 0) null else className(superIndex), interfaces, members)
    }
}

fun readClasses(jar: File, keep: (String) -> Boolean): Map<String, LibClass> = ZipFile(jar).use { zip ->
    zip.entries().asSequence()
        .filter { it.name.endsWith(".class") && keep(it.name) }
        .associate { it.name.removeSuffix(".class") to ClassFile(zip.getInputStream(it).readBytes()).lib }
}

// A member resolves through the owner's superclasses and interfaces; a supertype outside the jars cannot be judged.
val objectMembers = setOf("equals:(Ljava/lang/Object;)Z", "hashCode:()I", "toString:()Ljava/lang/String;",
    "getClass:()Ljava/lang/Class;", "notify:()V", "notifyAll:()V", "wait:()V", "wait:(J)V", "wait:(JI)V")

fun resolves(api: Map<String, LibClass>, owner: String, member: String, seen: MutableSet<String> = mutableSetOf()): Boolean? {
    if (!seen.add(owner)) return false
    if (owner == "java/lang/Object") return member in objectMembers
    val cls = api[owner] ?: return null
    if (member in cls.members) return true
    var unknown = false
    for (next in listOfNotNull(cls.superName) + cls.interfaces) {
        when (resolves(api, next, member, seen)) {
            true -> return true
            null -> unknown = true
            false -> {}
        }
    }
    return if (unknown) null else false
}

val apiPackages = listOf("dev/engine_room/", "com/wf/gemrender/")
val mainOutput = (project.extensions.getByName("sourceSets") as SourceSetContainer)["main"].output
val compileJava = tasks.named("compileJava")

fun detached(notation: String) = configurations.detachedConfiguration(dependencies.create(notation)).apply { isTransitive = false }

val gemrenderId = (findProperty("deps.gemrender") as String?)?.takeIf { it.isNotBlank() }
val upstreamFlywheel = (findProperty("deps.gemrender_flywheel") as String?)?.takeIf { it.isNotBlank() }

// Below 26.1 every GemRender nests the same published Flywheel.
val modern = project.name.substringBefore('.').toInt() >= 26

if (modern && gemrenderId != null && upstreamFlywheel != null) {
    val forkJar = detached("curse.maven:gemrender-1735539:$gemrenderId")
    val upstreamJar = detached(upstreamFlywheel)

    val check = tasks.register("checkGemRenderApi") {
        group = "verification"
        description = "Fails if Core's GemRender code calls a Flywheel member missing from GemRender's own or upstream Flywheel."
        dependsOn(compileJava)
        inputs.files(mainOutput.classesDirs).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(forkJar, upstreamJar)
        val marker = layout.buildDirectory.file("checkGemRenderApi/ok")
        outputs.file(marker)

        doLast {
            val gem = forkJar.singleFile
            val fork = readClasses(gem) { true }
            val upstream = readClasses(upstreamJar.singleFile) { true } +
                readClasses(gem) { it.startsWith("com/wf/gemrender/") }
            val refs = mutableListOf<Pair<String, Ref>>()
            val classRefs = mutableListOf<Pair<String, String>>()
            for (dir in mainOutput.classesDirs.files) {
                dir.resolve("dev/golem/core/gemrender").walkTopDown().filter { it.name.endsWith(".class") }.forEach { file ->
                    val parsed = ClassFile(file.readBytes())
                    parsed.refs.filter { ref -> apiPackages.any { ref.owner.startsWith(it) } }.forEach { refs += parsed.name to it }
                    parsed.classRefs.filter { ref -> apiPackages.any { ref.startsWith(it) } }.forEach { classRefs += parsed.name to it }
                }
            }
            val problems = sortedSetOf<String>()
            for ((label, api) in listOf("GemRender $gemrenderId's own Flywheel" to fork, "upstream $upstreamFlywheel" to upstream)) {
                for ((user, cls) in classRefs) if (cls !in api) problems += "$user: class $cls is missing from $label"
                for ((user, ref) in refs) {
                    if (resolves(api, ref.owner, ref.name + ":" + ref.desc) == false) {
                        problems += "$user: ${ref.owner}.${ref.name}${ref.desc} is missing from $label"
                    }
                }
            }
            if (problems.isNotEmpty()) {
                throw GradleException("checkGemRenderApi: ${problems.size} reference(s) resolve in only one Flywheel; " +
                    "reach them by MethodHandle behind GemRender.FORK:\n  " + problems.joinToString("\n  "))
            }
            logger.lifecycle("checkGemRenderApi: ${refs.size + classRefs.size} reference(s) resolve in both Flywheels")
            marker.get().asFile.apply { parentFile.mkdirs(); writeText("ok") }
        }
    }
    tasks.named("check") { dependsOn(check) }
}
